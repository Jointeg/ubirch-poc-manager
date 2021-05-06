package com.ubirch.models.tenant

case class CreateTenantRequest(
  tenantName: TenantName,
  usageType: UsageType,
  deviceCreationToken: PlainDeviceCreationToken,
  certificationCreationToken: PlainCertificationCreationToken,
  idGardIdentifier: IdGardIdentifier,
  userGroupId: TenantGroupId,
  deviceGroupId: TenantGroupId,
  clientCert: Option[ClientCert]
)
