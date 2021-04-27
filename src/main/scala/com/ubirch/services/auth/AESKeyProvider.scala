package com.ubirch.services.auth

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.AESEncryptionPaths
import monix.eval.Task

import java.nio.charset.StandardCharsets
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

trait AESKeyProvider {
  def getAESKey: Task[SecretKey]
}

class ConfigKeyProvider @Inject() (config: Config) extends AESKeyProvider with LazyLogging {
  private val key = config.getString(AESEncryptionPaths.SECRET_KEY).getBytes(StandardCharsets.UTF_8)

  override def getAESKey: Task[SecretKey] =
    Task(new SecretKeySpec(key, "AES"))
}
