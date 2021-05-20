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
  orgUnitId: OrgUnitId,
  groupId: GroupId,
  sharedAuthCert: Option[SharedAuthCert] = None,
  lastUpdated: Updated = Updated(DateTime.now()), //updated automatically on storage in DB
  created: Created = Created(DateTime.now())
) {
  def getOrgId: UUID = orgId.value.value.asJava()
}

object Tenant {

  def apply(
    id: TenantId,
    tenantName: TenantName,
    usageType: UsageType,
    deviceCreationToken: EncryptedDeviceCreationToken,
    idGardIdentifier: IdGardIdentifier,
    certifyGroupId: TenantCertifyGroupId,
    deviceGroupId: TenantDeviceGroupId,
    orgId: OrgId,
    sharedAuthCertRequired: Boolean): Tenant =
    Tenant(
      id,
      tenantName,
      usageType,
      deviceCreationToken,
      idGardIdentifier,
      certifyGroupId,
      deviceGroupId,
      orgId,
      sharedAuthCertRequired,
      getNamespacedOrgUnitId(id),
      getNamespacedGroupId(id)
    )

  private def getNamespacedOrgUnitId(tenantId: TenantId) = {
    OrgUnitId(memeid4s.UUID.V5.apply(tenantId.value.value, "orgUnitId").asJava())
  }

  private def getNamespacedGroupId(tenantId: TenantId) = {
    GroupId(memeid4s.UUID.V5.apply(tenantId.value.value, "groupId").asJava())
  }

}
