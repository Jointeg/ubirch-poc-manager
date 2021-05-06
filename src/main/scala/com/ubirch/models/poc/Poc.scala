package com.ubirch.models.poc

import com.ubirch.models.tenant.TenantId
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
  roleAndGroupName: String,
  groupPath: String,
  deviceId: DeviceId,
  clientCertFolder: Option[String] = None,
  status: Status = Pending,
  lastUpdated: Updated = Updated(DateTime.now()),
  created: Created = Created(DateTime.now())
)

object Poc {

  def apply(
    id: UUID,
    tenantId: TenantId,
    tenantGroupName: String,
    externalId: String,
    pocName: String,
    address: Address,
    phone: String,
    certifyApp: Boolean,
    logoUrl: Option[LogoURL],
    clientCertRequired: Boolean,
    dataSchemaId: String,
    extraConfig: Option[JsonConfig],
    manager: PocManager): Poc = {
    val roleName = s"P_${pocName.take(10)}_$id"
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
      roleAndGroupName = roleName,
      groupPath = tenantGroupName + "/" + roleName,
      deviceId = DeviceId(tenantId, externalId)
    )
  }

}
