package com.ubirch.services.keycloak

import cats.data.OptionT
import com.google.inject.{Inject, Singleton}
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.keycloak.user.CreateKeycloakUser
import monix.eval.Task
import org.keycloak.representations.idm.UserRepresentation

import scala.collection.JavaConverters.iterableAsScalaIterableConverter

trait KeycloakUserService {
  def createUser(createKeycloakUser: CreateKeycloakUser): Task[Unit]
  def deleteUser(username: String): Task[Unit]
  def getUser(username: String): Task[Option[UserRepresentation]]
}

@Singleton
class KeycloakUserServiceImpl @Inject() (keycloakConnector: KeycloakConnector)
  extends KeycloakUserService
  with LazyLogging {
  override def createUser(createKeycloakUser: CreateKeycloakUser): Task[Unit] = {
    val keycloakUser = createKeycloakUser.toKeycloakRepresentation
    keycloakUser.setEnabled(true)
    logger.debug(s"Creating keycloak user ${keycloakUser.getUsername}")
    Task {
      keycloakConnector.keycloak
        .realm("test-realm")
        .users()
        .create(keycloakUser)
    }
  }

  override def getUser(username: String): Task[Option[UserRepresentation]] = {
    logger.debug(s"Retrieving keycloak user $username")
    Task(
      keycloakConnector.keycloak
        .realm("test-realm")
        .users()
        .search(username)
        .asScala
        .headOption
    )
  }

  override def deleteUser(username: String): Task[Unit] = {
    (for {
      user <- OptionT(getUser(username))
      _ <- OptionT.liftF(Task(keycloakConnector.keycloak.realm("test-realm").users().delete(user.getId)))
      _ = logger.debug(s"Successfully deleted $username user")
    } yield ()).value.void
  }
}
