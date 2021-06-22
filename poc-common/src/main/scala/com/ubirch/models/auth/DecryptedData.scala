package com.ubirch.models.auth

import java.nio.charset.StandardCharsets

case class DecryptedData(value: String) extends AnyVal

object DecryptedData {

  def fromByteArray(data: Array[Byte]): DecryptedData = DecryptedData(new String(data, StandardCharsets.UTF_8))

}
