package com.ubirch.models.auth

import java.util.Base64

case class EncryptedData(value: Base64String)

object EncryptedData {

  def fromIVAndDataBytes(iv: Array[Byte], data: Array[Byte]): EncryptedData =
    EncryptedData(Base64String(Base64.getEncoder.encodeToString(iv ++ data)))

}
