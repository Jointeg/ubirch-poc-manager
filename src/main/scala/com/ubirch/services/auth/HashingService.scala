package com.ubirch.services.auth

import com.ubirch.models.auth.{ Base64String, Hash, HashedData, Salt }

import java.security.MessageDigest

trait HashingService {
  def sha256(data: Base64String): HashedData
  def sha256(data: Base64String, salt: Salt): HashedData
}

class DefaultHashingService extends HashingService {
  override def sha256(data: Base64String): HashedData = {
    val salt = Salt.newSalt
    hashWithSaltedSHA256(data, salt)
  }

  override def sha256(data: Base64String, salt: Salt): HashedData = {
    hashWithSaltedSHA256(data, salt)
  }

  private def hashWithSaltedSHA256(data: Base64String, salt: Salt): HashedData = {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(salt.value)
    val hash = digest.digest(data.decode)
    HashedData(Hash(Base64String.toBase64String(hash)), salt)
  }
}
