package com.ubirch.services.superadmin
import com.ubirch.db.tables.TenantRepository
import com.ubirch.models.tenant._
import com.ubirch.services.auth.AESEncryption
import monix.eval.Task

import javax.inject.Inject

trait TenantService {
  def createTenant(createTenantRequest: CreateTenantRequest): Task[TenantId]
}

class DefaultTenantService @Inject() (aesEncryption: AESEncryption, tenantRepository: TenantRepository)
  extends TenantService {

  override def createTenant(createTenantRequest: CreateTenantRequest): Task[TenantId] = {
    for {
      encryptedDeviceCreationToken <-
        aesEncryption.encrypt(createTenantRequest.deviceCreationToken.value)(EncryptedDeviceCreationToken(_))
      encryptedCertificationCreationToken <- aesEncryption.encrypt(
        createTenantRequest.certificationCreationToken.value)(EncryptedCertificationCreationToken(_))
      tenant = convertToTenant(encryptedDeviceCreationToken, encryptedCertificationCreationToken, createTenantRequest)
      tenantId <- tenantRepository.createTenant(tenant)
    } yield tenantId
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
