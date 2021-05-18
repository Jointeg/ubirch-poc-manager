package com.ubirch.models.poc

import com.ubirch.models.tenant.TenantId
import com.ubirch.util.ServiceConstants.POC_GROUP_PREFIX
import org.joda.time.DateTime

import java.util.UUID

case class Poc(
  id: UUID,
  tenantId: TenantId,
  externalId: String,
  pocName: String,
  pocType: String,
  address: Address,
  phone: String,
  logoUrl: Option[LogoURL],
  clientCertRequired: Boolean,
  extraConfig: Option[JsonConfig],
  manager: PocManager,
  roleName: String,
  deviceGroupId: Option[String] = None,
  certifyGroupId: Option[String] = None,
  deviceId: DeviceId,
  clientCertFolder: Option[String] = None,
  status: Status = Pending,
  lastUpdated: Updated = Updated(DateTime.now()), //updated automatically on storage in DB
  created: Created = Created(DateTime.now())
) {
  def getDeviceId: String = deviceId.value.value.toString
  def isUsingApp: Boolean =
    pocType.split("_").last match {
      case "app" => true
      case _     => false
    }
}

object Poc {

  def apply(
    id: UUID,
    tenantId: TenantId,
    externalId: String,
    pocName: String,
    pocType: String,
    address: Address,
    phone: String,
    logoUrl: Option[LogoURL],
    clientCertRequired: Boolean,
    extraConfig: Option[JsonConfig],
    manager: PocManager,
    status: Status): Poc = {

    val roleName = POC_GROUP_PREFIX + pocName.take(10) + "_" + id
    Poc(
      id = id,
      tenantId = tenantId,
      externalId = externalId,
      pocName = pocName,
      pocType = pocType,
      address = address,
      phone = phone,
      logoUrl = logoUrl,
      clientCertRequired = clientCertRequired,
      extraConfig = extraConfig,
      manager = manager,
      roleName = roleName,
      deviceId = DeviceId(tenantId, externalId),
      status = status
    )
  }

}
