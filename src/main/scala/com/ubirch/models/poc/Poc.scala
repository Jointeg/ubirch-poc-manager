package com.ubirch.models.poc

import com.ubirch.util.ServiceConstants.POC_GROUP_PREFIX
import org.joda.time.DateTime

import java.util.UUID

case class Poc(
  id: UUID,
  tenantId: UUID,
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
  userGroupId: Option[String] = None,
  deviceId: UUID = UUID.randomUUID(), //Todo: generate name spaced
  clientCertFolder: Option[String] = None,
  status: Status = Pending,
  lastUpdated: Updated = Updated(DateTime.now()),
  created: Created = Created(DateTime.now())
)

object Poc {

  def apply(
    id: UUID,
    tenantId: UUID,
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
    val roleName = POC_GROUP_PREFIX + pocName.take(10) + "_" + id
    Poc(
      id,
      tenantId = tenantId,
      externalId,
      pocName = pocName,
      address = address,
      phone = phone,
      certifyApp = certifyApp,
      logoUrl = logoUrl,
      clientCertRequired = clientCertRequired,
      dataSchemaId = dataSchemaId,
      extraConfig = extraConfig,
      manager = manager,
      roleName
    )
  }

}
