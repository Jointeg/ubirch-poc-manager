package com.ubirch.models.tenant
import com.ubirch.models.auth.Base64String
import io.getquill.MappedEncoding

case class ClientCert(value: Base64String)

object ClientCert {
  implicit val encodeClientCert: MappedEncoding[ClientCert, String] =
    MappedEncoding[ClientCert, String](_.value.value)
  implicit val decodeClientCert: MappedEncoding[String, ClientCert] =
    MappedEncoding[String, ClientCert](value => ClientCert(Base64String(value)))
}
