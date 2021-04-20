package com.ubirch.models.user

import io.getquill.MappedEncoding

import java.util.UUID

case class UserId(value: UUID) extends AnyVal

object UserId {
  implicit val encodeUserId: MappedEncoding[UserId, UUID] = MappedEncoding[UserId, UUID](_.value)
  implicit val decodeUserId: MappedEncoding[UUID, UserId] = MappedEncoding[UUID, UserId](UserId(_))
}
