package com.ubirch.services.jwt

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.InvalidClaimsException
import com.ubirch.services.formats.JsonConverterService
import org.json4s.JValue
import pdi.jwt.{ Jwt, JwtAlgorithm }

import java.security.PublicKey
import javax.inject.{ Inject, Singleton }

trait TokenVerificationService {
  def decodeAndVerify(jwt: String, publicKey: PublicKey): Option[JValue]
}

@Singleton
class DefaultTokenVerificationService @Inject() (jsonConverterService: JsonConverterService)
  extends TokenVerificationService
  with LazyLogging {

  final val ISSUER = "iss"
  final val SUBJECT = "sub"
  final val AUDIENCE = "aud"
  final val EXPIRATION = "exp"
  final val NOT_BEFORE = "nbf"
  final val ISSUED_AT = "iat"
  final val JWT_ID = "jti"

  def decodeAndVerify(jwt: String, publicKey: PublicKey): Option[JValue] = {
    (for {
      (_, p, _) <- Jwt.decodeRawAll(jwt, publicKey, Seq(JwtAlgorithm.ES256))

      all <- jsonConverterService.toJValue(p).toTry
        .recover { case e: Exception => throw InvalidClaimsException(e.getMessage, jwt) }

    } yield {
      Some(all)
    }).recover {
      case e: InvalidClaimsException =>
        logger.error(s"invalid_claims=${e.getMessage}", e)
        None
      case e: Exception =>
        logger.error(s"invalid_token=${e.getMessage}", e)
        None
    }.getOrElse(None)

  }

}
