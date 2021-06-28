package com.ubirch.models.auth

import io.getquill.MappedEncoding

case class Hash(value: Base64String)

object Hash {
  implicit val encodeHash: MappedEncoding[Hash, String] = MappedEncoding[Hash, String] {
    case Hash(Base64String(value)) => value
  }
  implicit val decodeHash: MappedEncoding[String, Hash] =
    MappedEncoding[String, Hash](value => Hash(Base64String(value)))
}
