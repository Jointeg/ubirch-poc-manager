package com.ubirch.models.auth
import java.nio.charset.StandardCharsets
import java.util.Base64

case class Base64String(value: String) extends AnyVal {

  def decode: Array[Byte] = Base64.getDecoder.decode(value.getBytes(StandardCharsets.UTF_8))

}

object Base64String {

  def toBase64String(value: String): Base64String =
    Base64String(Base64.getEncoder.encodeToString(value.getBytes(StandardCharsets.UTF_8)))

  def toBase64String(data: Array[Byte]): Base64String = Base64String(Base64.getEncoder.encodeToString(data))
}
