package com.ubirch.services.superadmin

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.db.tables.TenantRepository
import com.ubirch.models.auth.CertIdentifier
import com.ubirch.models.auth.cert.SharedAuthCertificateResponse
import com.ubirch.models.tenant._
import com.ubirch.services.auth.AESEncryption
import com.ubirch.services.poc.{ CertHandler, CertificateCreationError }
import monix.eval.Task

import java.util.UUID
import javax.inject.Inject

trait SuperAdminService {
  def createTenant(createTenantRequest: CreateTenantRequest): Task[Either[CreateTenantErrors, TenantId]]
}

class DefaultSuperAdminService @Inject() (
  aesEncryption: AESEncryption,
  tenantRepository: TenantRepository,
  certHandler: CertHandler)
  extends SuperAdminService
  with LazyLogging {

  override def createTenant(createTenantRequest: CreateTenantRequest): Task[Either[CreateTenantErrors, TenantId]] = {
    for {
      encryptedDeviceCreationToken <-
        aesEncryption.encrypt(createTenantRequest.deviceCreationToken.value)(EncryptedDeviceCreationToken(_))
      tenant = convertToTenant(encryptedDeviceCreationToken, createTenantRequest)

      _ <- createOrgCert(tenant)
      orgUnitID <- createOrgUnitCert(tenant)
      response <- createSharedAuthCert(tenant, orgUnitID)
      cert <- getCert(tenant, response)

      updatedTenant = updateTenant(tenant, orgUnitID, response, cert)
      tenantId <- persistTenant(updatedTenant)
    } yield tenantId
  }

  private def persistTenant(updatedTenant: Tenant) = {
    tenantRepository.createTenant(updatedTenant).map(Right.apply).onErrorHandle(ex => {
      logger.error(s"Could not create Tenant in DB because: ${ex.getMessage}")
      Left(DBError(updatedTenant.id))
    })
  }

  private def createOrgCert(tenant: Tenant): Task[Unit] = {
    val orgCertIdentifier = CertIdentifier.tenantOrgCert(tenant.tenantName)
    certHandler
      .createOrganisationalCertificate(tenant.getOrgCertId, orgCertIdentifier)
      .map {
        case Right(_)                            =>
        case Left(CertificateCreationError(msg)) => throw TenantCreationException(msg)
      }
  }

  private def createOrgUnitCert(tenant: Tenant): Task[Option[OrgUnitId]] = {
    if (!tenant.sharedAuthCertRequired)
      Task(None)
    else {
      val orgUnitCertId = UUID.randomUUID()
      val identifier = CertIdentifier.tenantOrgUnitCert(tenant.tenantName)
      certHandler
        .createOrganisationalUnitCertificate(tenant.getOrgCertId, orgUnitCertId, identifier)
        .map {
          case Right(_)                            => Some(OrgUnitId(orgUnitCertId))
          case Left(CertificateCreationError(msg)) => throw TenantCreationException(msg)
        }
    }
  }

  //Todo: provide sharedAuthCert to Teamdrive
  private def createSharedAuthCert(
    tenant: Tenant,
    orgUnitCertId: Option[OrgUnitId]): Task[Option[SharedAuthResult]] = {
    if (!tenant.sharedAuthCertRequired)
      Task(None)
    else {
      val groupId = UUID.randomUUID()
      val identifier = CertIdentifier.tenantClientCert(tenant.tenantName)
      certHandler
        .createSharedAuthCertificate(orgUnitCertId.get.value, groupId, identifier)
        .map {
          case Right(SharedAuthCertificateResponse(certUuid, _, _)) => Some(SharedAuthResult(groupId, certUuid))
          case Left(CertificateCreationError(msg))                  => throw TenantCreationException(msg)
        }
    }
  }

  private def getCert(tenant: Tenant, sharedAuthResult: Option[SharedAuthResult]): Task[Option[String]] = {
    if (!tenant.sharedAuthCertRequired)
      Task(None)
    else if (sharedAuthResult.isEmpty)
      throw TenantCreationException(s"getCert failed as sharedAuthCertId was empty for tenant ${tenant.tenantName}")
    else {
      certHandler
        .getCert(sharedAuthResult.get.sharedAuthCertId)
        .map {
          case Right(cert: String)                 => Some(cert)
          case Left(CertificateCreationError(msg)) => throw TenantCreationException(msg)
        }
    }
  }

  private def updateTenant(
    tenant: Tenant,
    orgUnitId: Option[OrgUnitId],
    sharedAuthResult: Option[SharedAuthResult],
    sharedAuthCert: Option[String]) = {

    if (!tenant.sharedAuthCertRequired) {
      tenant
    } else {
      (orgUnitId, sharedAuthResult, sharedAuthCert) match {

        case (Some(_), Some(sharedAuthResult), Some(cert)) =>
          tenant
            .copy(
              orgUnitId = orgUnitId,
              groupId = Some(GroupId(sharedAuthResult.groupId)),
              sharedAuthCert = Some(SharedAuthCert(cert)))

        case _ =>
          throw TenantCreationException(
            s"tenant creation failed; orgId $orgUnitId, shareAuthResult $sharedAuthResult and sharedAuthCert $sharedAuthCert should be defined")
      }
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
      sharedAuthCertRequired = createTenantRequest.sharedAuthCertRequired,
      orgUnitId = None
    )
  }

}

sealed trait CreateTenantErrors
case class DBError(tenantId: TenantId) extends CreateTenantErrors

case class TenantCreationException(msg: String) extends Throwable
case class SharedAuthResult(groupId: UUID, sharedAuthCertId: UUID)
