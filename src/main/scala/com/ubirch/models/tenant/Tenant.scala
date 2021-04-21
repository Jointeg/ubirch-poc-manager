package com.ubirch.models.tenant

case class Tenant(
  id: TenantId,
  tenantName: TenantName,
  pocUsageBase: POCUsageBase,
  deviceCreationToken: EncryptedDeviceCreationToken,
  certificationCreationToken: EncryptedCertificationCreationToken,
  idGardIdentifier: IdGardIdentifier,
  groupId: TenantGroupId,
  organisationalUnitGroupId: TenantOrganisationalUnitGroupId
)
