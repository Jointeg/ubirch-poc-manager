package com.ubirch.models.tenant

import com.ubirch.models.poc.{ Created, Updated }
import org.joda.time.DateTime

import java.util.UUID

case class Tenant(
  id: TenantId,
  tenantName: TenantName,
  usageType: UsageType,
  deviceCreationToken: EncryptedDeviceCreationToken,
  idGardIdentifier: IdGardIdentifier,
  certifyGroupId: TenantCertifyGroupId,
  deviceGroupId: TenantDeviceGroupId,
  orgId: OrgId,
  sharedAuthCertRequired: Boolean,
  orgUnitId: Option[OrgUnitId] = None,
  groupId: Option[GroupId] = None,
  sharedAuthCert: Option[SharedAuthCert] = None,
  lastUpdated: Updated = Updated(DateTime.now()), //updated automatically on storage in DB
  created: Created = Created(DateTime.now())
) {
  def getOrgId: UUID = orgId.value.value.asJava()
}
