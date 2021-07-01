package com.ubirch.models.poc

import com.ubirch.models.common.POC_GROUP_PREFIX
import com.ubirch.models.tenant.{ ClientCert, OrgUnitId, TenantId }
import org.joda.time.DateTime

import java.util.UUID

case class Poc(
  id: UUID,
  tenantId: TenantId,
  externalId: String,
  pocType: String,
  pocName: String,
  address: Address,
  phone: String,
  logoUrl: Option[LogoURL],
  orgUnitId: Option[OrgUnitId] = None,
  clientCert: Option[ClientCert] = None,
  extraConfig: Option[JsonConfig],
  manager: PocManager,
  roleName: String,
  deviceGroupId: Option[String] = None,
  pocTypeGroupId: Option[String] = None,
  certifyGroupId: Option[String] = None,
  adminGroupId: Option[String] = None,
  employeeGroupId: Option[String] = None,
  deviceId: DeviceId,
  clientCertFolder: Option[String] = None,
  status: Status = Pending,
  sharedAuthCertId: Option[UUID] = None,
  creationAttempts: Int = 0,
  lastUpdated: Updated = Updated(DateTime.now()), //updated automatically on storage in DB
  created: Created = Created(DateTime.now())
) {
  def typeIsApp: Boolean = pocType.endsWith("_app")

  def getDeviceId: String = deviceId.value.value.toString

}

object Poc {

  def apply(
    id: UUID,
    tenantId: TenantId,
    externalId: String,
    pocType: String,
    pocName: String,
    address: Address,
    phone: String,
    logoUrl: Option[LogoURL],
    extraConfig: Option[JsonConfig],
    manager: PocManager,
    status: Status
  ): Poc = {

    val roleName = POC_GROUP_PREFIX + pocName.take(10) + "_" + id
    Poc(
      id = id,
      tenantId = tenantId,
      externalId = externalId,
      pocType = pocType,
      pocName = pocName,
      address = address,
      phone = phone,
      logoUrl = logoUrl,
      extraConfig = extraConfig,
      manager = manager,
      roleName = roleName,
      deviceId = DeviceId(tenantId, externalId),
      status = status
    )
  }

}
