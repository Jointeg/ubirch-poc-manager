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
  address: Address,
  phone: String,
  certifyApp: Boolean,
  logoUrl: Option[LogoURL],
  clientCertRequired: Boolean,
  dataSchemaId: String,
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
}

object Poc {

  def apply(
    id: UUID,
    tenantId: TenantId,
    externalId: String,
    pocName: String,
    address: Address,
    phone: String,
    certifyApp: Boolean,
    logoUrl: Option[LogoURL],
    clientCertRequired: Boolean,
    dataSchemaId: String,
    extraConfig: Option[JsonConfig],
    manager: PocManager,
    status: Status): Poc = {

    val roleName = POC_GROUP_PREFIX + pocName.take(10) + "_" + id
    Poc(
      id = id,
      tenantId = tenantId,
      externalId = externalId,
      pocName = pocName,
      address = address,
      phone = phone,
      certifyApp = certifyApp,
      logoUrl = logoUrl,
      clientCertRequired = clientCertRequired,
      dataSchemaId = dataSchemaId,
      extraConfig = extraConfig,
      manager = manager,
      roleName = roleName,
      deviceId = DeviceId(tenantId, externalId),
      status = status
    )
  }

}
