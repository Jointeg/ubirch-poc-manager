package com.ubirch.services.superadmin

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.db.tables.TenantRepository
import com.ubirch.models.tenant._
import com.ubirch.services.auth.AESEncryption
import monix.eval.Task

import javax.inject.Inject

trait SuperAdminService {
  def createTenant(createTenantRequest: CreateTenantRequest): Task[Either[CreateTenantErrors, TenantId]]
}

class DefaultSuperAdminService @Inject() (aesEncryption: AESEncryption, tenantRepository: TenantRepository)
  extends SuperAdminService
  with LazyLogging {

  override def createTenant(createTenantRequest: CreateTenantRequest): Task[Either[CreateTenantErrors, TenantId]] = {
    for {
      encryptedDeviceCreationToken <-
        aesEncryption.encrypt(createTenantRequest.deviceCreationToken.value)(EncryptedDeviceCreationToken(_))
      encryptedCertificationCreationToken <- aesEncryption.encrypt(
        createTenantRequest.certificationCreationToken.value)(EncryptedCertificationCreationToken(_))
      tenant = convertToTenant(encryptedDeviceCreationToken, encryptedCertificationCreationToken, createTenantRequest)
      tenantId <- tenantRepository.createTenant(tenant).map(Right.apply).onErrorHandle(ex => {
        logger.error(s"Could not create Tenant in DB because: ${ex.getMessage}")
        Left(DBError(tenant.id))
      })
    } yield tenantId
  }

  private def convertToTenant(
    encryptedDeviceCreationToken: EncryptedDeviceCreationToken,
    encryptedCertificationCreationToken: EncryptedCertificationCreationToken,
    createTenantRequest: CreateTenantRequest): Tenant =
    Tenant(
      TenantId(createTenantRequest.tenantName),
      createTenantRequest.tenantName,
      createTenantRequest.usageType,
      encryptedDeviceCreationToken,
      encryptedCertificationCreationToken,
      createTenantRequest.idGardIdentifier,
      createTenantRequest.userGroupId,
      createTenantRequest.deviceGroupId,
      createTenantRequest.clientCert
    )

}

sealed trait CreateTenantErrors
case class DBError(tenantId: TenantId) extends CreateTenantErrors
