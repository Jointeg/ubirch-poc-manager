package com.ubirch.models.auth
import java.nio.charset.StandardCharsets
import java.util.Base64

case class Base64String(value: String) extends AnyVal {

  def decode: Array[Byte] = Base64.getDecoder.decode(value)

}

object Base64String {

  def toBase64String(value: String): Base64String =
    Base64String(Base64.getEncoder.encodeToString(value.getBytes(StandardCharsets.UTF_8)))

}
