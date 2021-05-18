package com.ubirch.models.tenant

import io.getquill.MappedEncoding

import java.util.UUID

case class GroupId(value: UUID)
object GroupId {
  implicit val encodeOrgCertId: MappedEncoding[GroupId, UUID] =
    MappedEncoding[GroupId, UUID](_.value)
  implicit val decodeOrgCertId: MappedEncoding[UUID, GroupId] =
    MappedEncoding[UUID, GroupId](GroupId.apply)
}
