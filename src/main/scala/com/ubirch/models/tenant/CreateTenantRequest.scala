package com.ubirch.models.tenant

case class CreateTenantRequest(
  tenantName: TenantName,
  usageType: UsageType,
  sharedAuthCertRequired: Boolean
)
