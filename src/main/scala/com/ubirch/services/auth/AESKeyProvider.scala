package com.ubirch.services.auth

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.AESEncryptionPaths
import com.ubirch.models.auth.Base64String
import monix.eval.Task

import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

trait AESKeyProvider {
  def getAESKey: Task[SecretKey]
}

class ConfigKeyProvider @Inject() (config: Config) extends AESKeyProvider with LazyLogging {
  private val key = Base64String(config.getString(AESEncryptionPaths.SECRET_KEY))

  override def getAESKey: Task[SecretKey] =
    Task(new SecretKeySpec(key.decode, "AES"))
}
