package com.ubirch.services.jwt

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.crypto.PrivKey
import com.ubirch.util.TaskHelpers
import pdi.jwt.{ Jwt, JwtAlgorithm, JwtClaim }

import java.time.Clock
import java.util.UUID
import javax.inject._
import scala.util.Try

trait TokenCreationService {
  def create[T <: Any](
    id: UUID,
    by: String,
    to: String,
    about: String,
    expiresIn: Option[Long],
    notBefore: Option[Long],
    fields: (Symbol, T)*
  ): Try[JwtClaim]
  def encode(header: String, jwtClaim: String, privKey: PrivKey): Try[String]
}

@Singleton
class DefaultTokenCreationService extends TokenCreationService with TaskHelpers with LazyLogging {

  implicit private val clock: Clock = Clock.systemUTC

  override def create[T <: Any](
    id: UUID,
    by: String,
    to: String,
    about: String,
    expiresIn: Option[Long],
    notBefore: Option[Long],
    fields: (Symbol, T)*
  ): Try[JwtClaim] = {

    for {
      jwtClaim <- Try {
        JwtClaim()
          .by(by)
          .to(to)
          .about(about)
          .issuedNow
          .withId(id.toString)
      }
        .map { x => expiresIn.map(x.expiresIn(_)).getOrElse(x) }
        .map { x => notBefore.map(x.startsIn(_)).getOrElse(x) }

      jwtClaimWithFields = jwtClaim ++ (fields.map(x => (x._1.name, x._2)): _*)

    } yield {
      jwtClaimWithFields
    }

  }

  override def encode(header: String, jwtClaim: String, privKey: PrivKey): Try[String] = Try {
    Jwt.encode(
      header,
      jwtClaim,
      privKey.getPrivateKey,
      JwtAlgorithm.ES256
    )
  }

}
