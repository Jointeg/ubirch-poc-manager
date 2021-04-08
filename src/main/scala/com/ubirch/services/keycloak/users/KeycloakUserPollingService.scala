package com.ubirch.services.keycloak.users

import com.google.inject.{Inject, Singleton}
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.keycloak.user.KeycloakUser
import com.ubirch.services.keycloak.KeycloakConfig
import com.ubirch.services.keycloak.auth.AuthClient
import monix.eval.Task
import monix.execution.Cancelable
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
import org.json4s.Formats
import org.json4s.native.Serialization
import org.keycloak.representations.AccessTokenResponse
import sttp.client._
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.json4s._

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

trait UserPollingService {
  def subscribe[T](operation: Either[Exception, List[KeycloakUser]] => Observable[T]): Cancelable
}

@Singleton
class KeycloakUserPollingService @Inject() (authClient: AuthClient, keycloakConfig: KeycloakConfig)(implicit
  formats: Formats)
  extends UserPollingService
  with LazyLogging {

  implicit private val backend: SttpBackend[Future, Nothing, WebSocketHandler] = AsyncHttpClientFutureBackend()
  implicit private val serialization: Serialization.type = org.json4s.native.Serialization

  private val newlyRegisteredUsers: Observable[Either[Exception, List[KeycloakUser]]] = {
    for {
      _ <- Observable.intervalWithFixedDelay(keycloakConfig.userPollingInterval.seconds)
      token <-
        Observable
          .fromTask(Task(authClient.client.obtainAccessToken(keycloakConfig.username, keycloakConfig.password)))
          .doOnError(logObtainAccessTokenError)
      users <-
        Observable
          .fromTask(getUsersWithoutConfirmationMail(token))
          .doOnError(logObtainUsersError)
      _ <-
        Observable
          .fromTask(
            Task(logger.info(s"Retrieved ${users.body.map(_.size).getOrElse(0)} users without confirmation mail sent"))
          )
    } yield users.body
  }

  private def retryWithDelay[A](source: Observable[A]): Observable[A] = {
    source.onErrorHandleWith { _ =>
      retryWithDelay(source).delayExecution(30.seconds)
    }
  }

  private def getUsersWithoutConfirmationMail(token: AccessTokenResponse) = {
    Task.fromFuture(
      basicRequest
        .get(
          uri"${keycloakConfig.serverUrl}/realms/${keycloakConfig.usersRealm}/user-search/users-without-confirmation-mail")
        .auth
        .bearer(token.getToken)
        .response(asJson[List[KeycloakUser]])
        .send()
    )
  }

  private def logObtainAccessTokenError(exception: Throwable) = {
    Task(logger.error(s"Could not obtain Access Token because ${exception.getMessage}"))
  }

  private def logObtainUsersError(exception: Throwable) = {
    Task(logger.error(s"Could not retrieve users without confirmation mail sent because ${exception.getMessage}"))
  }

  def subscribe[T](operation: Either[Exception, List[KeycloakUser]] => Observable[T]): Cancelable =
    retryWithDelay(newlyRegisteredUsers.flatMap(operation)).subscribe()

}
