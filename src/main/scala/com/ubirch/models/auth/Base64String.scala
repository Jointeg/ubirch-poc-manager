package com.ubirch.models.auth
import java.util.Base64

case class Base64String(value: String) extends AnyVal {

  def decode: Array[Byte] = Base64.getDecoder.decode(value)

}
