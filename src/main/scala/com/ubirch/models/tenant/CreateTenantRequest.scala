package com.ubirch.models.tenant

case class CreateTenantRequest(
  tenantName: TenantName,
  usageType: UsageType,
  deviceCreationToken: PlainDeviceCreationToken,
  sharedAuthCertRequired: Boolean
)
