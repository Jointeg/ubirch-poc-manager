package com.ubirch.models.poc

import org.joda.time.DateTime

import java.util.UUID

case class Poc(
  id: UUID = UUID.randomUUID(),
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
  pocAddons: PocAddons = PocAddons(),
  status: Status = Pending,
  lastUpdated: Updated = Updated(DateTime.now()),
  created: Created = Created(DateTime.now())
)
