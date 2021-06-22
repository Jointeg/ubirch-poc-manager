package com.ubirch.models.tenant

import io.getquill.MappedEncoding

import java.util.UUID

case class OrgUnitId(value: UUID)
object OrgUnitId {
  implicit val encodeOrgCertId: MappedEncoding[OrgUnitId, UUID] =
    MappedEncoding[OrgUnitId, UUID](_.value)
  implicit val decodeOrgCertId: MappedEncoding[UUID, OrgUnitId] =
    MappedEncoding[UUID, OrgUnitId](OrgUnitId.apply)
}
