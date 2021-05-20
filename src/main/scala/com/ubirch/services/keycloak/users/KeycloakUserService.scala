package com.ubirch.services.keycloak.users

import cats.data.OptionT
import com.google.inject.{ Inject, Singleton }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.keycloak.user.{
  CreateCertifyKeycloakUser,
  CreateDeviceKeycloakUser,
  CreateKeycloakUser,
  UserAlreadyExists,
  UserCreationError,
  UserException,
  UserRequiredAction
}
import com.ubirch.models.user.{ UserId, UserName }
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak, KeycloakConnector, KeycloakInstance }
import monix.eval.Task
import org.keycloak.representations.idm.UserRepresentation

import java.util.UUID
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.Status
import scala.collection.JavaConverters.{
  iterableAsScalaIterableConverter,
  mapAsJavaMapConverter,
  seqAsJavaListConverter
}

trait KeycloakUserService {
  /**
    * when the isVerifyEmail parameter is true, a keycloak user is created with setting the actions of UserRequiredActions parameter
    */
  def createUser(
    createKeycloakUser: CreateKeycloakUser,
    instance: KeycloakInstance = CertifyKeycloak,
    userRequiredActions: List[UserRequiredAction.Value] = Nil): Task[Either[UserException, UserId]]

  def addGroupToUser(
    userName: String,
    groupId: String,
    instance: KeycloakInstance = CertifyKeycloak): Task[Either[String, Unit]]

  def addGroupToUserByUserId(
    userId: UserId,
    groupId: String,
    instance: KeycloakInstance = CertifyKeycloak): Task[Either[String, Unit]]

  def deleteUser(username: UserName, instance: KeycloakInstance = CertifyKeycloak): Task[Unit]

  def getUserById(
    userId: UserId,
    instance: KeycloakInstance = CertifyKeycloak): Task[Option[UserRepresentation]]

  def getUser(
    username: UserName,
    instance: KeycloakInstance = CertifyKeycloak): Task[Option[UserRepresentation]]

  /**
    * Send an email to user with actions set to the user
    */
  def sendRequiredActionsEmail(userId: UserId, instance: KeycloakInstance): Task[Either[String, Unit]]
}

@Singleton
class DefaultKeycloakUserService @Inject() (keycloakConnector: KeycloakConnector)
  extends KeycloakUserService
  with LazyLogging {

  override def createUser(
    createKeycloakUser: CreateKeycloakUser,
    instance: KeycloakInstance = CertifyKeycloak,
    userRequiredActions: List[UserRequiredAction.Value] = Nil): Task[Either[UserException, UserId]] = {
    (createKeycloakUser, instance) match {
      case (_: CreateDeviceKeycloakUser, CertifyKeycloak) =>
        Task(
          Left(UserCreationError(s"user and instance don't match. deviceKeycloak user and certify keycloak instance")))
      case (_: CreateCertifyKeycloakUser, DeviceKeycloak) =>
        Task(
          Left(UserCreationError(s"user and instance don't match. certifyKeycloak user and device keycloak instance")))
      case (_, _) => {
        val keycloakUser = createKeycloakUser.toKeycloakRepresentation
        keycloakUser.setEnabled(true)
        keycloakUser.setAttributes(Map("confirmation_mail_sent" -> List("false").asJava).asJava)
        keycloakUser.setRequiredActions(userRequiredActions.map(_.toString).asJava)
        logger.debug(s"Creating keycloak user ${keycloakUser.getUsername}")
        Task {
          val resp =
            keycloakConnector
              .getKeycloak(instance)
              .realm(keycloakConnector.getKeycloakRealm(instance))
              .users()
              .create(keycloakUser)
          processCreationResponse(resp, keycloakUser.getUsername)
        }.onErrorHandle { ex =>
          val errorMsg = s"failed to create user ${createKeycloakUser}"
          logger.error(errorMsg, ex)
          Left(UserCreationError(errorMsg))
        }
      }
    }
  }

  override def getUserById(
    userId: UserId,
    instance: KeycloakInstance = CertifyKeycloak): Task[Option[UserRepresentation]] = {
    logger.debug(s"Retrieving keycloak user id: ${userId.value}")
    Task(
      Option(keycloakConnector
        .getKeycloak(instance)
        .realm(keycloakConnector.getKeycloakRealm(instance))
        .users()
        .get(userId.value.toString)
        .toRepresentation)
    )
  }

  override def getUser(
    username: UserName,
    instance: KeycloakInstance = CertifyKeycloak): Task[Option[UserRepresentation]] = {
    logger.debug(s"Retrieving keycloak user $username")
    Task(
      keycloakConnector
        .getKeycloak(instance)
        .realm(keycloakConnector.getKeycloakRealm(instance))
        .users()
        .search(username.value)
        .asScala
        .headOption
    )
  }

  override def addGroupToUser(
    userName: String,
    groupId: String,
    instance: KeycloakInstance = CertifyKeycloak): Task[Either[String, Unit]] = {

    getUser(UserName(userName), instance).map {
      case Some(userRepresentation: UserRepresentation) =>
        Right(
          keycloakConnector
            .getKeycloak(instance)
            .realm(keycloakConnector.getKeycloakRealm(instance))
            .users()
            .get(userRepresentation.getId)
            .joinGroup(groupId))
      case None =>
        Left(s"user with name $userName wasn't found")
    }.onErrorHandle { ex =>
      logger.error(s"failed to add group $groupId to user $userName", ex)
      Left(s"failed to add group $groupId to user $userName")
    }
  }

  override def addGroupToUserByUserId(
    userId: UserId,
    groupId: String,
    instance: KeycloakInstance = CertifyKeycloak): Task[Either[String, Unit]] = {
    getUserById(userId, instance).map {
      case Some(userRepresentation: UserRepresentation) =>
        Right(
          keycloakConnector
            .getKeycloak(instance)
            .realm(keycloakConnector.getKeycloakRealm(instance))
            .users()
            .get(userRepresentation.getId)
            .joinGroup(groupId))
      case None =>
        Left(s"user with id ${userId.value} wasn't found")
    }.onErrorHandle { ex =>
      logger.error(s"failed to add group $groupId to user ${userId.value}", ex)
      Left(s"failed to add group $groupId to user ${userId.value}")
    }
  }

  override def deleteUser(username: UserName, instance: KeycloakInstance = CertifyKeycloak): Task[Unit] = {
    (for {
      user <- OptionT(getUser(username))
      _ <- OptionT.liftF(
        Task(
          keycloakConnector
            .getKeycloak(instance)
            .realm(keycloakConnector.getKeycloakRealm(instance))
            .users()
            .delete(user.getId)))
      _ = logger.debug(s"Successfully deleted $username user")
    } yield ()).value.void
  }

  override def sendRequiredActionsEmail(userId: UserId, instance: KeycloakInstance): Task[Either[String, Unit]] = {
    getUserById(userId, instance).map {
      case Some(userRepresentation: UserRepresentation) =>
        val actions = userRepresentation.getRequiredActions
        if (!actions.isEmpty) {
          Right(keycloakConnector.getKeycloak(instance)
            .realm(keycloakConnector.getKeycloakRealm(instance)).users()
            .get(userRepresentation.getId)
            .executeActionsEmail(actions))
        } else {
          Left(s"no action is setup for user: ${userId.value}.")
        }
      case None =>
        Left(s"user with name ${userId.value} wasn't found")
    }.onErrorHandle { ex =>
      logger.error(s"failed to execute required actions to user ${userId.value}", ex)
      Left(s"failed to execute required actions to user ${userId.value}")
    }
  }

  private def processCreationResponse(response: Response, userName: String): Either[UserException, UserId] = {

    if (response.getStatusInfo.equals(Status.CREATED)) {
      Right(getIdFromPath(response))
    } else if (response.getStatusInfo.equals(Status.CONFLICT)) {
      logger.info(s"user with name $userName already existed")
      Left(UserAlreadyExists(userName))
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
