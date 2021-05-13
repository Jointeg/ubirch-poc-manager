package com.ubirch.services.keycloak.users

import com.google.inject.{ Inject, Singleton }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.keycloak.user.KeycloakUser
import com.ubirch.services.execution.SttpResources
import com.ubirch.services.keycloak.KeycloakUsersConfig
import com.ubirch.services.keycloak.auth.AuthClient
import monix.eval.Task
import monix.reactive.Observable
import org.json4s.Formats
import org.json4s.native.Serialization
import sttp.client._
import sttp.client.json4s._

import scala.concurrent.duration.DurationInt

trait UserPollingService {
  def via[T](operation: Either[Exception, List[KeycloakUser]] => Observable[T]): Observable[T]
}

@Singleton
class KeycloakUserPollingService @Inject() (authClient: AuthClient, keycloakUsersConfig: KeycloakUsersConfig)(implicit
formats: Formats)
  extends UserPollingService
  with LazyLogging {

  implicit private val serialization: Serialization.type = org.json4s.native.Serialization

  private val newlyRegisteredUsers: Observable[Either[Exception, List[KeycloakUser]]] = {
    for {
      _ <- Observable.intervalWithFixedDelay(keycloakUsersConfig.userPollingInterval.seconds)
      token <-
        Observable
          .fromTask(
            authClient
              .obtainAccessToken(keycloakUsersConfig.clientAdminUsername, keycloakUsersConfig.clientAdminPassword))
          .doOnError(logObtainAccessTokenError)
      users <-
        Observable
          .fromTask(getUsersWithoutConfirmationMail(token))
          .doOnError(logObtainUsersError)
      _ <- Observable.fromTask(logUsersResponse(users))
    } yield users.body
  }

  private def retryWithDelay[A](source: Observable[A]): Observable[A] = {
    source.onErrorHandleWith { _ =>
      retryWithDelay(source).delayExecution(30.seconds)
    }
  }

  private def getUsersWithoutConfirmationMail(token: String)
    : Task[Response[Either[ResponseError[Exception], List[KeycloakUser]]]] =
    Task.deferFuture {
      val request = basicRequest
        .get(
          uri"${keycloakUsersConfig.serverUrl}/realms/${keycloakUsersConfig.realm}/user-search/users-without-confirmation-mail")
        .auth
        .bearer(token)
        .response(asJson[List[KeycloakUser]])
      SttpResources.backend
        .send(request)
    }

  private def logUsersResponse(usersResponse: Response[Either[ResponseError[Exception], List[KeycloakUser]]])
    : Task[Unit] = {
    usersResponse.body match {
      case Left(exception) =>
        Task(
          logger.error(s"Could not retrieve users from Keycloak because error has occurred: ${exception.getMessage}"))
      case Right(users) => Task(logger.info(s"Retrieved ${users.size} users without confirmation mail sent"))
    }
  }

  private def logObtainAccessTokenError(exception: Throwable): Task[Unit] = {
    Task(logger.error(s"Could not obtain Access Token because ${exception.getMessage}"))
  }

  private def logObtainUsersError(exception: Throwable): Task[Unit] = {
    Task(logger.error(s"Could not retrieve users without confirmation mail sent because ${exception.getMessage}"))
  }

  def via[T](operation: Either[Exception, List[KeycloakUser]] => Observable[T]): Observable[T] =
    retryWithDelay(newlyRegisteredUsers.flatMap(operation))

}
