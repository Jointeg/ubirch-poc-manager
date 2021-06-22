package com.ubirch.models.tenant

import com.ubirch.models.auth.{ Base64String, EncryptedData }
import io.getquill.MappedEncoding

final case class EncryptedCertificationCreationToken(value: EncryptedData) extends AnyVal

object EncryptedCertificationCreationToken {
  implicit val encodeEncryptedCertificationCreationToken: MappedEncoding[EncryptedCertificationCreationToken, String] =
    MappedEncoding[EncryptedCertificationCreationToken, String] {
      case EncryptedCertificationCreationToken(EncryptedData(Base64String(value))) => value
    }
  implicit val decodeEncryptedCertificationCreationToken: MappedEncoding[String, EncryptedCertificationCreationToken] =
    MappedEncoding[String, EncryptedCertificationCreationToken](base64String =>
      EncryptedCertificationCreationToken(EncryptedData(Base64String(base64String))))
}
