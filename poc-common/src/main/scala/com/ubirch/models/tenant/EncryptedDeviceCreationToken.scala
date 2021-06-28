package com.ubirch.models.tenant

import com.ubirch.models.auth.{ Base64String, EncryptedData }
import io.getquill.MappedEncoding

final case class EncryptedDeviceCreationToken(value: EncryptedData) extends AnyVal

object EncryptedDeviceCreationToken {
  implicit val encodeEncryptedDeviceCreationToken: MappedEncoding[EncryptedDeviceCreationToken, String] =
    MappedEncoding[EncryptedDeviceCreationToken, String] {
      case EncryptedDeviceCreationToken(EncryptedData(Base64String(value))) => value
    }
  implicit val decodeEncryptedDeviceCreationToken: MappedEncoding[String, EncryptedDeviceCreationToken] =
    MappedEncoding[String, EncryptedDeviceCreationToken](base64String =>
      EncryptedDeviceCreationToken(EncryptedData(Base64String(base64String))))
}
