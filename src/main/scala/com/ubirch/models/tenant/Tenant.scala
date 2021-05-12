package com.ubirch.models.tenant

case class Tenant(
  id: TenantId,
  tenantName: TenantName,
  usageType: UsageType,
  deviceCreationToken: EncryptedDeviceCreationToken,
  idGardIdentifier: IdGardIdentifier,
  userGroupId: TenantUserGroupId,
  deviceGroupId: TenantDeviceGroupId,
  clientCert: Option[ClientCert]
)
