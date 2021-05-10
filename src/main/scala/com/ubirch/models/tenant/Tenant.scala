package com.ubirch.models.tenant

case class Tenant(
  id: TenantId,
  tenantName: TenantName,
  usageType: UsageType,
  deviceCreationToken: EncryptedDeviceCreationToken,
  certificationCreationToken: EncryptedCertificationCreationToken,
  idGardIdentifier: IdGardIdentifier,
  userGroupId: TenantUserGroupId,
  deviceGroupId: TenantDeviceGroupId,
  clientCert: Option[ClientCert]
)
