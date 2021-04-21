package com.ubirch.models.tenant

case class CreateTenantRequest(
  tenantName: TenantName,
  pocUsageBase: POCUsageBase,
  deviceCreationToken: PlainDeviceCreationToken,
  certificationCreationToken: PlainCertificationCreationToken,
  idGardIdentifier: IdGardIdentifier,
  tenantGroupId: TenantGroupId,
  tenantOrganisationalUnitGroupId: TenantOrganisationalUnitGroupId
)
