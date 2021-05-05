package com.ubirch.models.auth
import io.getquill.MappedEncoding

import java.security.SecureRandom

case class Salt(value: Array[Byte])

object Salt {

  private val SALT_LENGTH = 128

  def newSalt: Salt = {
    val ivBytes = new Array[Byte](SALT_LENGTH)
    val random: SecureRandom = new SecureRandom()
    random.nextBytes(ivBytes)
    new Salt(ivBytes)
  }

  implicit val encodeBase64String: MappedEncoding[Salt, Array[Byte]] = MappedEncoding[Salt, Array[Byte]](_.value)
  implicit val decodeBase64String: MappedEncoding[Array[Byte], Salt] =
    MappedEncoding[Array[Byte], Salt](Salt.apply)

}
