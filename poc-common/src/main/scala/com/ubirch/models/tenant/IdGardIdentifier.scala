package com.ubirch.models.tenant

import io.getquill.MappedEncoding

final case class IdGardIdentifier(value: String) extends AnyVal

object IdGardIdentifier {
  implicit val encodeIdGardIdentifier: MappedEncoding[IdGardIdentifier, String] =
    MappedEncoding[IdGardIdentifier, String](_.value)
  implicit val decodeIdGardIdentifier: MappedEncoding[String, IdGardIdentifier] =
    MappedEncoding[String, IdGardIdentifier](IdGardIdentifier(_))
}
