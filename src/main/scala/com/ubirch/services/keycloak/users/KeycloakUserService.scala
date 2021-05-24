package com.ubirch.services.keycloak.users

import cats.data.OptionT
import com.google.inject.{ Inject, Singleton }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.keycloak.user.{
  CreateBasicKeycloakUser,
  CreateKeycloakUser,
  CreateKeycloakUserWithoutUserName,
  UserAlreadyExists,
  UserCreationError,
  UserException,
  UserRequiredAction
}
import com.ubirch.models.user.{ UserId, UserName }
import com.ubirch.services.{ DeviceKeycloak, KeycloakConnector, KeycloakInstance }
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
    * A keycloak user is created with setting the actions of UserRequiredActions parameter
    */
  def createUser(
    createBasicKeycloakUser: CreateBasicKeycloakUser,
    instance: KeycloakInstance,
    userRequiredActions: List[UserRequiredAction.Value] = Nil): Task[Either[UserException, UserId]]

  /**
    * A keycloak user is created with setting the actions of UserRequiredActions parameter
    */
  def createUserWithoutUserName(
    createKeycloakUserWithoutUserName: CreateKeycloakUserWithoutUserName,
    instance: KeycloakInstance,
    userRequiredActions: List[UserRequiredAction.Value] = Nil): Task[Either[UserException, UserId]]

  /**
    * @Important This method doesn't work for Certify Keycloak instance because username is not used for this instance
    */
  def addGroupToUserByName(
    userName: String,
    groupId: String,
    instance: KeycloakInstance): Task[Either[String, Unit]]

  def addGroupToUserById(
    userId: UserId,
    groupId: String,
    instance: KeycloakInstance): Task[Either[String, Unit]]

  /**
    * @Important This method doesn't work for Certify Keycloak instance because username is not used for this instance
    */
  def deleteUserByUserName(username: UserName, instance: KeycloakInstance): Task[Unit]

  def getUserById(
    userId: UserId,
    instance: KeycloakInstance): Task[Option[UserRepresentation]]

  /**
    * @Important This method doesn't work for Certify Keycloak instance because username is not used for this instance
    */
  def getUserByUserName(
    username: UserName,
    instance: KeycloakInstance = DeviceKeycloak): Task[Option[UserRepresentation]]

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
    createBasicKeycloakUser: CreateBasicKeycloakUser,
    instance: KeycloakInstance,
    userRequiredActions: List[UserRequiredAction.Value] = Nil): Task[Either[UserException, UserId]] = {
    _createUser(createBasicKeycloakUser, instance, userRequiredActions)
  }

  override def createUserWithoutUserName(
    createKeycloakUserWithoutUserName: CreateKeycloakUserWithoutUserName,
    instance: KeycloakInstance,
    userRequiredActions: List[UserRequiredAction.Value]): Task[Either[UserException, UserId]] = {
    _createUser(createKeycloakUserWithoutUserName, instance, userRequiredActions)
  }

  private def _createUser(
    createKeycloakUser: CreateKeycloakUser,
    instance: KeycloakInstance,
    userRequiredActions: List[UserRequiredAction.Value]): Task[Either[UserException, UserId]] = {
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
      val errorMsg = s"failed to create user $createKeycloakUser"
      logger.error(errorMsg, ex)
      Left(UserCreationError(errorMsg))
    }
  }

  override def getUserById(
    userId: UserId,
    instance: KeycloakInstance): Task[Option[UserRepresentation]] = {
    Task(
      Option(keycloakConnector
        .getKeycloak(instance)
        .realm(keycloakConnector.getKeycloakRealm(instance))
        .users()
        .get(userId.value.toString)
        .toRepresentation)
    )
  }

  override def getUserByUserName(
    username: UserName,
    instance: KeycloakInstance = DeviceKeycloak): Task[Option[UserRepresentation]] = {
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

  override def addGroupToUserByName(
    userName: String,
    groupId: String,
    instance: KeycloakInstance): Task[Either[String, Unit]] = {

    getUserByUserName(UserName(userName), instance).map {
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

  override def addGroupToUserById(
    userId: UserId,
    groupId: String,
    instance: KeycloakInstance): Task[Either[String, Unit]] = {

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
        Left(s"user with name $userId wasn't found")
    }.onErrorHandle { ex =>
      logger.error(s"failed to add group $groupId to user $userId", ex)
      Left(s"failed to add group $groupId to user $userId")
    }
  }

  override def deleteUserByUserName(username: UserName, instance: KeycloakInstance = DeviceKeycloak): Task[Unit] = {
    (for {
      user <- OptionT(getUserByUserName(username, instance))
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
