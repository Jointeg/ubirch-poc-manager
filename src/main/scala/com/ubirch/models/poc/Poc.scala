package com.ubirch.models.poc

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
  deviceRealmGroupId: Option[String] = None,
  userRealmGroupId: Option[String] = None,
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
