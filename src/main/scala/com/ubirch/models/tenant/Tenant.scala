package com.ubirch.models.tenant

case class Tenant(
  id: TenantId,
  tenantName: TenantName,
  usageType: UsageType,
  deviceCreationToken: EncryptedDeviceCreationToken,
  idGardIdentifier: IdGardIdentifier,
  certifyGroupId: TenantCertifyGroupId,
  deviceGroupId: TenantDeviceGroupId,
  clientCert: Option[ClientCert]
)
