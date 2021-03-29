package com.ubirch.services.keycloak.users

import cats.data.OptionT
import com.google.inject.{Inject, Singleton}
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.keycloak.user.{CreateKeycloakUser, UserAlreadyExists, UserName}
import com.ubirch.services.keycloak.{KeycloakConfig, KeycloakConnector}
import monix.eval.Task
import org.keycloak.representations.idm.UserRepresentation

import scala.collection.JavaConverters.iterableAsScalaIterableConverter

trait KeycloakUserService {
  def createUser(createKeycloakUser: CreateKeycloakUser): Task[Either[UserAlreadyExists, Unit]]
  def deleteUser(username: UserName): Task[Unit]
  def getUser(username: UserName): Task[Option[UserRepresentation]]
}

@Singleton
class KeycloakUserServiceImpl @Inject() (keycloakConnector: KeycloakConnector, keycloakConfig: KeycloakConfig)
  extends KeycloakUserService
  with LazyLogging {
  override def createUser(createKeycloakUser: CreateKeycloakUser): Task[Either[UserAlreadyExists, Unit]] = {
    val keycloakUser = createKeycloakUser.toKeycloakRepresentation
    keycloakUser.setEnabled(true)
    logger.debug(s"Creating keycloak user ${keycloakUser.getUsername}")
    Task {
      val resp = keycloakConnector.keycloak
        .realm(keycloakConfig.usersRealm)
        .users()
        .create(keycloakUser)
      if (resp.getStatus == 409) {
        logger.error(s"Tried to create user with ${keycloakUser.getUsername} but it already exists")
        Left(UserAlreadyExists(UserName(keycloakUser.getUsername)))
      } else {
        Right(())
      }
    }
  }

  override def getUser(username: UserName): Task[Option[UserRepresentation]] = {
    logger.debug(s"Retrieving keycloak user $username")
    Task(
      keycloakConnector.keycloak
        .realm(keycloakConfig.usersRealm)
        .users()
        .search(username.value)
        .asScala
        .headOption
    )
  }

  override def deleteUser(username: UserName): Task[Unit] = {
    (for {
      user <- OptionT(getUser(username))
      _ <- OptionT.liftF(Task(keycloakConnector.keycloak.realm(keycloakConfig.usersRealm).users().delete(user.getId)))
      _ = logger.debug(s"Successfully deleted $username user")
    } yield ()).value.void
  }
}
