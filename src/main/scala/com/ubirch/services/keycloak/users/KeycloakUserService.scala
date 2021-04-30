package com.ubirch.services.keycloak.users

import cats.data.OptionT
import com.google.inject.{Inject, Singleton}
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.keycloak.user.{CreateKeycloakUser, UserAlreadyExists}
import com.ubirch.models.user.UserName
import com.ubirch.services.{KeycloakConnector, KeycloakInstance, UsersKeycloak}
import monix.eval.Task
import org.keycloak.representations.idm.UserRepresentation

import scala.collection.JavaConverters.{iterableAsScalaIterableConverter, mapAsJavaMapConverter, seqAsJavaListConverter}

trait KeycloakUserService {
  def createUser(
                  createKeycloakUser: CreateKeycloakUser,
                  keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Either[UserAlreadyExists, Unit]]

  def deleteUser(username: UserName, keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Unit]

  def getUser(username: UserName, keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Option[UserRepresentation]]
}

@Singleton
class KeycloakUserServiceImpl @Inject()(keycloakConnector: KeycloakConnector)
  extends KeycloakUserService
    with LazyLogging {

  override def createUser(
                           createKeycloakUser: CreateKeycloakUser,
                           keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Either[UserAlreadyExists, Unit]] = {
    val keycloakUser = createKeycloakUser.toKeycloakRepresentation
    keycloakUser.setEnabled(true)
    keycloakUser.setAttributes(Map("confirmation_mail_sent" -> List("false").asJava).asJava)
    logger.debug(s"Creating keycloak user ${keycloakUser.getUsername}")
    Task {
      val resp = keycloakConnector
        .getKeycloak(keycloakInstance)
        .realm(keycloakConnector.getKeycloakRealm(keycloakInstance))
        .users()
        .create(keycloakUser)
      if (resp.getStatus == 409) {
        logger.error(s"Tried to create user with ${keycloakUser.getUsername} username but it already exists")
        Left(UserAlreadyExists(UserName(keycloakUser.getUsername)))
      } else {
        Right(())
      }
    }
  }

  override def getUser(
                        username: UserName,
                        keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Option[UserRepresentation]] = {
    logger.debug(s"Retrieving keycloak user $username")
    Task(
      keycloakConnector
        .getKeycloak(keycloakInstance)
        .realm(keycloakConnector.getKeycloakRealm(keycloakInstance))
        .users()
        .search(username.value)
        .asScala
        .headOption
    )
  }

  override def deleteUser(username: UserName, keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Unit] = {
    (for {
      user <- OptionT(getUser(username))
      _ <- OptionT.liftF(
        Task(
          keycloakConnector
            .getKeycloak(keycloakInstance)
            .realm(keycloakConnector.getKeycloakRealm(keycloakInstance))
            .users()
            .delete(user.getId)))
      _ = logger.debug(s"Successfully deleted $username user")
    } yield ()).value.void
  }
}
