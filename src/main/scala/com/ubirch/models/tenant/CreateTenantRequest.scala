package com.ubirch.models.tenant

case class CreateTenantRequest(
  tenantName: TenantName,
  usageType: UsageType,
  deviceCreationToken: PlainDeviceCreationToken,
  certificationCreationToken: PlainCertificationCreationToken,
  idGardIdentifier: IdGardIdentifier,
  userGroupId: TenantCertifyGroupId,
  deviceGroupId: TenantDeviceGroupId,
  clientCert: Option[ClientCert]
)
