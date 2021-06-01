package com.ubirch.services.poc

import cats.syntax.either._
import com.ubirch.services.CertifyKeycloak
import com.ubirch.services.keycloak.users.{ KeycloakUserService, Remove2faTokenKeycloakError }
import com.ubirch.services.poc.CertifyUserService.HasCertifyUserId
import monix.eval.Task

import java.util.UUID
import javax.inject.{ Inject, Singleton }

@Singleton
class CertifyUserService @Inject() (keycloakUserService: KeycloakUserService) {

  def remove2FAToken(certifyUser: HasCertifyUserId): Task[Either[Remove2faTokenError, Unit]] =
    for {
      result <- certifyUser.certifyUserId match {
        case Some(certifyUserId) =>
          keycloakUserService.remove2faToken(CertifyKeycloak.defaultRealm, certifyUserId, CertifyKeycloak).flatMap {
            case Left(error) => Task.pure(Remove2faTokenError.KeycloakError(certifyUser.id, error).asLeft)
            case Right(_)    => Task.pure(().asRight)
          }
        case None => Task.pure(Remove2faTokenError.MissingCertifyUserId(certifyUser.id).asLeft)
      }
    } yield result
}

object CertifyUserService {
  trait HasCertifyUserId {
    def id: UUID
    def certifyUserId: Option[UUID]
  }
}

sealed trait Remove2faTokenError
object Remove2faTokenError {
  case class KeycloakError(id: UUID, message: Remove2faTokenKeycloakError) extends Remove2faTokenError
  case class MissingCertifyUserId(id: UUID) extends Remove2faTokenError
}
