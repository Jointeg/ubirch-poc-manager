package com.ubirch.models.tenant

import io.getquill.MappedEncoding

case class SharedAuthCert(value: String)

object SharedAuthCert {
  implicit val encodeSharedAuthCert: MappedEncoding[SharedAuthCert, String] =
    MappedEncoding[SharedAuthCert, String](_.value)
  implicit val decodeSharedAuthCert: MappedEncoding[String, SharedAuthCert] =
    MappedEncoding[String, SharedAuthCert](value => SharedAuthCert(value))
}
