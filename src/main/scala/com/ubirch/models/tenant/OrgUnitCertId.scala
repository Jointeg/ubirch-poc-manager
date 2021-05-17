package com.ubirch.models.tenant

import io.getquill.MappedEncoding

import java.util.UUID

case class OrgUnitCertId(value: UUID)
object OrgUnitCertId {
  implicit val encodeOrgCertId: MappedEncoding[OrgUnitCertId, UUID] =
    MappedEncoding[OrgUnitCertId, UUID](_.value)
  implicit val decodeOrgCertId: MappedEncoding[UUID, OrgUnitCertId] =
    MappedEncoding[UUID, OrgUnitCertId](OrgUnitCertId.apply)
}
