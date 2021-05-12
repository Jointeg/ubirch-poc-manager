package com.ubirch.models.tenant

case class Tenant(
  id: TenantId,
  tenantName: TenantName,
  usageType: UsageType,
  deviceCreationToken: EncryptedDeviceCreationToken,
  idGardIdentifier: IdGardIdentifier,
  userGroupId: TenantUserGroupId,
  deviceGroupId: TenantDeviceGroupId,
  orgCertId: OrgCertId,
  orgUnitCertId: Option[OrgUnitCertId],
  clientCert: Option[ClientCert]
)
