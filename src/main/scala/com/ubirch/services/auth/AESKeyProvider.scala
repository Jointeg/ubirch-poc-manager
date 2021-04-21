package com.ubirch.services.auth

import com.typesafe.scalalogging.LazyLogging
import monix.eval.Task

import java.nio.charset.StandardCharsets
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

trait AESKeyProvider {
  def getAESKey: Task[SecretKey]
}

class StaticKeyProvider extends AESKeyProvider with LazyLogging {
  private val key = "4t7w9z$C&F)J@NcRfUjXn2r5u8x/A%D*".getBytes(StandardCharsets.UTF_8)

  override def getAESKey: Task[SecretKey] =
    Task {
      logger.warn("WARNING! Key is provided by StaticKeyProvider which must not be used on production.")
      new SecretKeySpec(key, "AES")
    }
}
