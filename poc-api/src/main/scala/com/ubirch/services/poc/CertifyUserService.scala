package com.ubirch.services.poc

import cats.syntax.either._
import com.ubirch.models.poc.HasCertifyUserId
import com.ubirch.services.CertifyKeycloak
import com.ubirch.services.keycloak.KeycloakRealm
import com.ubirch.services.keycloak.users.{ KeycloakUserService, Remove2faTokenKeycloakError }
import monix.eval.Task

import java.util.UUID
import javax.inject.{ Inject, Singleton }

@Singleton
class CertifyUserService @Inject() (keycloakUserService: KeycloakUserService) {

  def remove2FAToken(realm: KeycloakRealm, certifyUser: HasCertifyUserId): Task[Either[Remove2faTokenFromCertifyUserError, Unit]] =
    for {
      result <- certifyUser.certifyUserId match {
        case Some(certifyUserId) =>
          keycloakUserService.remove2faToken(realm, certifyUserId, CertifyKeycloak).flatMap {
            case Left(error) => Task.pure(Remove2faTokenFromCertifyUserError.KeycloakError(certifyUser.id, error).asLeft)
            case Right(_)    => Task.pure(().asRight)
          }
        case None => Task.pure(Remove2faTokenFromCertifyUserError.MissingCertifyUserId(certifyUser.id).asLeft)
      }
    } yield result
}

sealed trait Remove2faTokenFromCertifyUserError
object Remove2faTokenFromCertifyUserError {
  case class KeycloakError(id: UUID, message: Remove2faTokenKeycloakError) extends Remove2faTokenFromCertifyUserError
  case class MissingCertifyUserId(id: UUID) extends Remove2faTokenFromCertifyUserError
}
