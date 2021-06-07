package com.ubirch.models.tenant

import com.ubirch.models.poc.{ Created, Updated }
import com.ubirch.services.keycloak.{ CertifyBmgRealm, CertifyUbirchRealm, KeycloakRealm }
import org.joda.time.DateTime

import java.util.UUID

case class Tenant(
  id: TenantId,
  tenantName: TenantName,
  usageType: UsageType,
  tenantType: TenantType,
  deviceCreationToken: Option[EncryptedDeviceCreationToken],
  certifyGroupId: TenantCertifyGroupId,
  deviceGroupId: TenantDeviceGroupId,
  tenantTypeGroupId: Option[TenantTypeGroupId],
  orgId: OrgId,
  orgUnitId: OrgUnitId,
  groupId: GroupId,
  sharedAuthCert: Option[SharedAuthCert] = None,
  lastUpdated: Updated = Updated(DateTime.now()), //updated automatically on storage in DB
  created: Created = Created(DateTime.now())
) {
  def getOrgId: UUID = orgId.value.value.asJava()

  def getRealm: KeycloakRealm = tenantType match {
    case UBIRCH => CertifyUbirchRealm
    case BMG    => CertifyBmgRealm
  }
}

object Tenant {

  def apply(
    id: TenantId,
    tenantName: TenantName,
    usageType: UsageType,
    tenantType: TenantType,
    deviceCreationToken: Option[EncryptedDeviceCreationToken],
    certifyGroupId: TenantCertifyGroupId,
    deviceGroupId: TenantDeviceGroupId,
    tenantSpecificGroupId: Option[TenantTypeGroupId],
    orgId: OrgId): Tenant =
    Tenant(
      id,
      tenantName,
      usageType,
      tenantType,
      deviceCreationToken,
      certifyGroupId,
      deviceGroupId,
      tenantSpecificGroupId,
      orgId,
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
