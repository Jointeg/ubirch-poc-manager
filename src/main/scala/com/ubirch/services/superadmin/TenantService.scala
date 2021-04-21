package com.ubirch.services.superadmin
import com.ubirch.db.tables.TenantRepository
import com.ubirch.models.tenant._
import com.ubirch.services.auth.AESEncryption
import monix.eval.Task

trait TenantService {
  def createTenant(createTenantRequest: CreateTenantRequest): Task[Unit]
}

class TenantServiceImpl(aesEncryption: AESEncryption, tenantRepository: TenantRepository) extends TenantService {

  override def createTenant(createTenantRequest: CreateTenantRequest): Task[Unit] = {
    for {
      encryptedDeviceCreationToken <-
        aesEncryption.encrypt(createTenantRequest.deviceCreationToken.value)(EncryptedDeviceCreationToken(_))
      encryptedCertificationCreationToken <- aesEncryption.encrypt(
        createTenantRequest.certificationCreationToken.value)(EncryptedCertificationCreationToken(_))
      tenant = convertToTenant(encryptedDeviceCreationToken, encryptedCertificationCreationToken, createTenantRequest)
      _ <- tenantRepository.createTenant(tenant)
    } yield ()
  }

  private def convertToTenant(
    encryptedDeviceCreationToken: EncryptedDeviceCreationToken,
    encryptedCertificationCreationToken: EncryptedCertificationCreationToken,
    createTenantRequest: CreateTenantRequest): Tenant =
    Tenant(
      TenantId.random,
      createTenantRequest.tenantName,
      createTenantRequest.pocUsageBase,
      encryptedDeviceCreationToken,
      encryptedCertificationCreationToken,
      createTenantRequest.idGardIdentifier,
      createTenantRequest.tenantGroupId,
      createTenantRequest.tenantOrganisationalUnitGroupId
    )

}
