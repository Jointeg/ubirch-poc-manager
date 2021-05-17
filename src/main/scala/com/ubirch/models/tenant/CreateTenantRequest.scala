package com.ubirch.models.tenant

case class CreateTenantRequest(
  tenantName: TenantName,
  usageType: UsageType,
  deviceCreationToken: PlainDeviceCreationToken,
  idGardIdentifier: IdGardIdentifier,
  certifyGroupId: TenantCertifyGroupId,
  deviceGroupId: TenantDeviceGroupId,
  clientCert: Option[ClientCert]
)
