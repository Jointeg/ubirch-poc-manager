package com.ubirch.services.keycloak.users

import cats.data.OptionT
import com.google.inject.{ Inject, Singleton }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.keycloak.user.{ CreateKeycloakUser, UserAlreadyExists, UserCreationError, UserException }
import com.ubirch.models.user.{ UserId, UserName }
import com.ubirch.services.{ KeycloakConnector, KeycloakInstance, UsersKeycloak }
import monix.eval.Task
import org.keycloak.representations.idm.{ GroupRepresentation, UserRepresentation }

import java.util.UUID
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.Status
import scala.collection.JavaConverters.{
  iterableAsScalaIterableConverter,
  mapAsJavaMapConverter,
  seqAsJavaListConverter
}

trait KeycloakUserService {
  def createUser(
    createKeycloakUser: CreateKeycloakUser,
    keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Either[UserException, UserId]]

  def addGroupToUser(
    id: String,
    groupRepresentation: GroupRepresentation,
    keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Either[String, Unit]]

  def deleteUser(username: UserName, keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Unit]

  def getUser(username: UserName, keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Option[UserRepresentation]]
}

@Singleton
class DefaultKeycloakUserService @Inject() (keycloakConnector: KeycloakConnector)
  extends KeycloakUserService
  with LazyLogging {

  override def createUser(
    createKeycloakUser: CreateKeycloakUser,
    keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Either[UserException, UserId]] = {
    val keycloakUser = createKeycloakUser.toKeycloakRepresentation
    keycloakUser.setEnabled(true)
    keycloakUser.setAttributes(Map("confirmation_mail_sent" -> List("false").asJava).asJava)
    logger.debug(s"Creating keycloak user ${keycloakUser.getUsername}")
    Task {
      val resp =
        keycloakConnector
          .getKeycloak(keycloakInstance)
          .realm(keycloakConnector.getKeycloakRealm(keycloakInstance))
          .users()
          .create(keycloakUser)
      processCreationResponse(resp, keycloakUser.getUsername)
    }.onErrorHandle { ex =>
      val errorMsg = s"failed to create user ${createKeycloakUser.userName.value}"
      logger.error(errorMsg, ex)
      Left(UserCreationError(errorMsg))
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

  override def addGroupToUser(
    id: String,
    group: GroupRepresentation,
    keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Either[String, Unit]] = {
    Task {
      val user = keycloakConnector
        .getKeycloak(keycloakInstance)
        .realm(keycloakConnector.getKeycloakRealm(keycloakInstance))
        .users()
        .get(id)

      if (user.groups().add(group)) {
        Right()
      } else {
        Left("adding group to user failed")
      }
    }.onErrorHandle { ex =>
      logger.error(s"failed to add group ${group.getName} to user $id", ex)
      Left(s"failed to add group ${group.getName} to user $id")
    }
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

  private def processCreationResponse(response: Response, userName: String): Either[UserException, UserId] = {

    if (response.getStatusInfo.equals(Status.CREATED)) {
      Right(getIdFromPath(response))
    } else if (response.getStatusInfo.equals(Status.CONFLICT)) {
      logger.info(s"user with name $userName already existed")
      Left(UserAlreadyExists(UserName(userName)))
    } else {
      logger.error(s"failed to create user $userName; response has status ${response.getStatus}")
      Left(UserCreationError(s"failed to create user $userName; response has status ${response.getStatus}"))
    }
  }

  private def getIdFromPath(response: Response) = {
    val path = response.getLocation.getPath
    UserId(UUID.fromString(path.substring(path.lastIndexOf('/') + 1)))
  }

}
