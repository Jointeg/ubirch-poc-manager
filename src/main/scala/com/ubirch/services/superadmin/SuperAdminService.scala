package com.ubirch.services.superadmin

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.PocConfig
import com.ubirch.db.tables.TenantRepository
import com.ubirch.models.auth.{ Base16String, CertIdentifier }
import com.ubirch.models.auth.cert.{ Passphrase, SharedAuthCertificateResponse }
import com.ubirch.models.tenant._
import com.ubirch.services.auth.AESEncryption
import com.ubirch.services.poc.{ CertHandler, CertificateCreationError }
import com.ubirch.services.teamdrive.TeamDriveService
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

      tenantId <-
        if (tenant.sharedAuthCertRequired) {
          for {
            _ <- createOrgCert(tenant)
            orgUnitID <- createOrgUnitCert(tenant)
            response <- createSharedAuthCert(tenant, orgUnitID)
            _ <- teamDriveService.shareCert(
              s"${pocConfig.teamDriveStage}_${tenant.tenantName.value}",
              pocConfig.teamDriveAdminEmails,
              response.passphrase,
              response.pkcs12)
            cert <- getCert(tenant, response)
            updated = updateTenant(tenant, orgUnitID, response, cert)
            tenantId <- persistTenant(updated)
          } yield {
            tenantId
          }
        } else {
          persistTenant(tenant)
        }
    } yield tenantId
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

  private[superadmin] def createOrgUnitCert(tenant: Tenant): Task[OrgUnitId] = {
    val orgUnitCertId = UUID.randomUUID()
    val identifier = CertIdentifier.tenantOrgUnitCert(tenant.tenantName)
    certHandler
      .createOrganisationalUnitCertificate(tenant.getOrgId, orgUnitCertId, identifier)
      .map {
        case Right(_) =>
          logger.debug(s"successfully created org unit cert $orgUnitCertId for tenant ${tenant.tenantName}")
          OrgUnitId(orgUnitCertId)
        case Left(CertificateCreationError(msg)) => throw TenantCreationException(msg)
      }
  }

  private[superadmin] def createSharedAuthCert(
    tenant: Tenant,
    orgUnitId: OrgUnitId): Task[SharedAuthResult] = {
    val groupId = UUID.randomUUID()
    val identifier = CertIdentifier.tenantClientCert(tenant.tenantName)
    certHandler
      .createSharedAuthCertificate(orgUnitId.value, groupId, identifier)
      .map {
        case Right(SharedAuthCertificateResponse(certUuid, passphrase, pkcs12)) =>
          logger.debug(s"successfully created shared auth cert $groupId for tenant ${tenant.tenantName}")
          SharedAuthResult(groupId, certUuid, passphrase, pkcs12)
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

  private[superadmin] def updateTenant(
    tenant: Tenant,
    orgUnitId: OrgUnitId,
    sharedAuthResult: SharedAuthResult,
    sharedAuthCert: String) = {
    tenant
      .copy(
        orgUnitId = Some(orgUnitId),
        groupId = Some(GroupId(sharedAuthResult.groupId)),
        sharedAuthCert = Some(SharedAuthCert(sharedAuthCert)))
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
      sharedAuthCertRequired = createTenantRequest.sharedAuthCertRequired,
      orgUnitId = None
    )
  }

}

sealed trait CreateTenantErrors
case class DBError(tenantId: TenantId) extends CreateTenantErrors

case class TenantCreationException(msg: String) extends Throwable
case class SharedAuthResult(groupId: UUID, sharedAuthCertId: UUID, passphrase: Passphrase, pkcs12: Base16String)
