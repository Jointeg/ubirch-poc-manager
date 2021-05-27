package com.ubirch.services.poc

import com.ubirch.services.CertifyKeycloak
import com.ubirch.services.keycloak.users.KeycloakUserService
import monix.eval.Task
import cats.syntax.either._
import com.ubirch.services.poc.CertifyUserService.HasCertifyUserId

import java.util.UUID
import javax.inject.{ Inject, Singleton }

@Singleton
class CertifyUserService @Inject() (keycloakUserService: KeycloakUserService) {

  def remove2FAToken(
    id: UUID,
    getEntity: UUID => Task[Option[HasCertifyUserId]]): Task[Either[Remove2faTokenError, Unit]] =
    for {
      maybeEmployee <- getEntity(id)
      result <- maybeEmployee match {
        case Some(pe) => pe.certifyUserId match {
            case Some(certifyUserId) =>
              keycloakUserService.remove2faToken(certifyUserId, CertifyKeycloak).map(_ => ().asRight)
            case None => Task.pure(Remove2faTokenError.MissingCertifyUserId(id).asLeft)
          }
        case None => Task.pure(Remove2faTokenError.CertifyUserNotFound(id).asLeft)
      }
    } yield result
}

object CertifyUserService {
  trait HasCertifyUserId {
    def certifyUserId: Option[UUID]
  }
}

sealed trait Remove2faTokenError
object Remove2faTokenError {
  case class CertifyUserNotFound(id: UUID) extends Remove2faTokenError
  case class MissingCertifyUserId(id: UUID) extends Remove2faTokenError
}
