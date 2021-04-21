package com.ubirch.models.tenant

case class CreateTenantRequest(
  tenantName: TenantName,
  pocUsageBase: POCUsageBase,
  deviceCreationToken: DeviceCreationToken,
  certificationCreationToken: CertificationCreationToken,
  idGardIdentifier: IdGardIdentifier,
  tenantGroupId: TenantGroupId,
  tenantOrganisationalUnitGroupId: TenantOrganisationalUnitGroupId
)
