package com.ubirch.services.superadmin

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.PocConfig
import com.ubirch.db.tables.TenantRepository
import com.ubirch.models.auth.cert.{ Passphrase, SharedAuthCertificateResponse }
import com.ubirch.models.auth.{ Base16String, CertIdentifier }
import com.ubirch.models.tenant._
import com.ubirch.services.auth.AESEncryption
import com.ubirch.services.poc.{ CertHandler, CertificateCreationError }
import com.ubirch.services.teamdrive.TeamDriveService
import com.ubirch.services.teamdrive.TeamDriveService.SharedCertificate
import com.ubirch.util.TaskHelpers
import monix.eval.Task

import java.util.UUID
import javax.inject.Inject

trait SuperAdminService {
  def createTenant(createTenantRequest: CreateTenantRequest): Task[Either[CreateTenantErrors, TenantId]]
}

class DefaultSuperAdminService @Inject() (
  aesEncryption: AESEncryption,
  tenantRepository: TenantRepository,
  certHandler: CertHandler,
  teamDriveService: TeamDriveService,
  pocConfig: PocConfig)
  extends SuperAdminService
  with LazyLogging
  with TaskHelpers {

  override def createTenant(createTenantRequest: CreateTenantRequest): Task[Either[CreateTenantErrors, TenantId]] = {
    for {
      encryptedDeviceCreationToken <-
        aesEncryption.encrypt(createTenantRequest.deviceCreationToken.value)(EncryptedDeviceCreationToken(_))
      tenant = convertToTenant(encryptedDeviceCreationToken, createTenantRequest)

      _ <- createOrgCert(tenant)
      tenantId <-
        if (tenant.sharedAuthCertRequired) {
          for {
            orgUnitID <- createOrgUnitCert(tenant)
            response <- createSharedAuthCert(tenant)
            _ <- createShareCertIntoTD(tenant, response)
            cert <- getCert(tenant, response)
            updated = tenant.copy(sharedAuthCert = Some(SharedAuthCert(cert)))
            tenantId <- persistTenant(updated)
          } yield {
            tenantId
          }
        } else {
          persistTenant(tenant)
        }
    } yield tenantId
  }

  private def createShareCertIntoTD(tenant: Tenant, sharedAuthResult: SharedAuthResult): Task[SharedCertificate] = {
    teamDriveService.shareCert(
      s"${pocConfig.teamDriveStage}_${tenant.tenantName.value}",
      pocConfig.teamDriveAdminEmails,
      sharedAuthResult.passphrase,
      sharedAuthResult.pkcs12).onErrorHandleWith {
      case ex: Exception =>
        val msg = s"Could not persist shared cert in TenantDrive because: ${ex.getMessage}"
        logger.error(msg)
        Task.raiseError(TenantCreationException(msg))
    }
  }

  private def persistTenant(updatedTenant: Tenant): Task[Either[DBError, TenantId]] = {
    tenantRepository.createTenant(updatedTenant).map(Right.apply).onErrorHandle(ex => {
      logger.error(s"Could not create Tenant in DB because: ${ex.getMessage}")
      Left(DBError(updatedTenant.id))
    })
  }

  private def createOrgCert(tenant: Tenant): Task[Unit] = {
    val orgCertIdentifier = CertIdentifier.tenantOrgCert(tenant.tenantName)
    certHandler
      .createOrganisationalCertificate(tenant.getOrgId, orgCertIdentifier)
      .map {
        case Right(_) =>
          logger.debug(s"successfully created org cert ${tenant.getOrgId} for tenant ${tenant.tenantName}")
        case Left(CertificateCreationError(msg)) => throw TenantCreationException(msg)
      }
  }

  private[superadmin] def createOrgUnitCert(tenant: Tenant): Task[Unit] = {
    val identifier = CertIdentifier.tenantOrgUnitCert(tenant.tenantName)
    certHandler
      .createOrganisationalUnitCertificate(tenant.getOrgId, tenant.orgUnitId.value, identifier)
      .map {
        case Right(_) =>
          logger.debug(s"successfully created org unit cert ${tenant.orgUnitId.value} for tenant ${tenant.tenantName}")
        case Left(CertificateCreationError(msg)) => throw TenantCreationException(msg)
      }
  }

  private[superadmin] def createSharedAuthCert(tenant: Tenant): Task[SharedAuthResult] = {

    val identifier = CertIdentifier.tenantClientCert(tenant.tenantName)
    certHandler
      .createSharedAuthCertificate(tenant.orgUnitId.value, tenant.groupId.value, identifier)
      .map {
        case Right(SharedAuthCertificateResponse(certUuid, passphrase, pkcs12)) =>
          logger.debug(s"successfully created shared auth cert ${tenant.groupId.value} for tenant ${tenant.tenantName}")
          SharedAuthResult(certUuid, passphrase, pkcs12)
        case Left(CertificateCreationError(msg)) => throw TenantCreationException(msg)
      }
  }

  private[superadmin] def getCert(tenant: Tenant, sharedAuthResult: SharedAuthResult): Task[String] = {
    certHandler
      .getCert(sharedAuthResult.sharedAuthCertId)
      .map {
        case Right(cert: String) =>
          logger.debug(
            s"successfully retrieved cert ${sharedAuthResult.sharedAuthCertId} for tenant ${tenant.tenantName}")
          cert
        case Left(CertificateCreationError(msg)) => throw TenantCreationException(msg)
      }
  }

  private def convertToTenant(
    encryptedDeviceCreationToken: EncryptedDeviceCreationToken,
    createTenantRequest: CreateTenantRequest): Tenant = {
    val tenantId = TenantId(createTenantRequest.tenantName)
    Tenant(
      tenantId,
      createTenantRequest.tenantName,
      createTenantRequest.usageType,
      encryptedDeviceCreationToken,
      createTenantRequest.idGardIdentifier,
      createTenantRequest.certifyGroupId,
      createTenantRequest.deviceGroupId,
      orgId = OrgId(tenantId.value),
      sharedAuthCertRequired = createTenantRequest.sharedAuthCertRequired
    )
  }

}

sealed trait CreateTenantErrors
case class DBError(tenantId: TenantId) extends CreateTenantErrors

case class TenantCreationException(msg: String) extends Throwable
case class SharedAuthResult(sharedAuthCertId: UUID, passphrase: Passphrase, pkcs12: Base16String)
