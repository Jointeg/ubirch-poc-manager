package com.ubirch.services.poc.util

import cats.syntax.either._
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.auth.Base16String
import com.ubirch.models.auth.cert.Passphrase

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.security.KeyStore

object PKCS12Operations extends LazyLogging {

  def recreateFromBase16String(
    stringPKCS12: Base16String,
    passphrase: Passphrase): Either[PKCS12RecreationError.type, KeyStore] = {
    for {
      iso8859PKCS12 <- Base16String.toISO8859String(stringPKCS12).leftMap(parsingError => {
        logger.error(s"Could not parse Base16 String to ISO-8859-1 format because: ${parsingError.msg}")
        PKCS12RecreationError
      })
      store <- createKeystoreFromString(iso8859PKCS12, passphrase)
    } yield store
  }

  private def createKeystoreFromString(
    pkcs12: String,
    passphrase: Passphrase): Either[PKCS12RecreationError.type, KeyStore] = {
    try {
      val pkcs12Bytes = new ByteArrayInputStream(pkcs12.getBytes(StandardCharsets.ISO_8859_1))
      val store = KeyStore.getInstance("pkcs12")
      store.load(pkcs12Bytes, passphrase.value.toCharArray)
      Right(store)
    } catch {
      case ex: Exception =>
        logger.error(s"Could not create PKCS12 because of ${ex.getMessage}")
        Left(PKCS12RecreationError)
    }
  }

}

case object PKCS12RecreationError
