package com.ubirch.services.keycloak.users

import cats.data.OptionT
import cats.syntax.either._
import com.google.inject.{ Inject, Singleton }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.keycloak.user._
import com.ubirch.models.pocEmployee.PocEmployee
import com.ubirch.models.user._
import com.ubirch.services.keycloak.KeycloakRealm
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak, KeycloakConnector, KeycloakInstance }
import monix.eval.Task
import org.keycloak.representations.idm.UserRepresentation

import java.util.UUID
import javax.ws.rs.NotFoundException
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.Status
import scala.collection.JavaConverters.{ mapAsJavaMapConverter, seqAsJavaListConverter }
import scala.jdk.CollectionConverters._

trait KeycloakUserService {
  /**
    * A keycloak user is created with setting the actions of UserRequiredActions parameter
    */
  def createUser(
    realm: KeycloakRealm,
    createBasicKeycloakUser: CreateBasicKeycloakUser,
    instance: KeycloakInstance,
    userRequiredActions: List[UserRequiredAction.Value] = Nil): Task[Either[UserException, UserId]]

  /**
    * A keycloak user is created with setting the actions of UserRequiredActions parameter
    */
  def createUserWithoutUserName(
    realm: KeycloakRealm,
    createKeycloakUserWithoutUserName: CreateKeycloakUserWithoutUserName,
    instance: KeycloakInstance,
    userRequiredActions: List[UserRequiredAction.Value] = Nil): Task[Either[UserException, UserId]]

  /**
    * @note This method doesn't work for Certify Keycloak instance because username is not used for this instance
    */
  def addGroupToUserByName(
    realm: KeycloakRealm,
    userName: String,
    groupId: String,
    instance: KeycloakInstance): Task[Either[String, Unit]]

  def addGroupToUserById(
    realm: KeycloakRealm,
    userId: UserId,
    groupId: String,
    instance: KeycloakInstance): Task[Either[String, Unit]]

  /**
    * @note This method doesn't work for Certify Keycloak instance because username is not used for this instance
    */
  def deleteUserByUserName(realm: KeycloakRealm, username: UserName, instance: KeycloakInstance): Task[Unit]

  def getUserById(realm: KeycloakRealm, userId: UserId, instance: KeycloakInstance): Task[Option[UserRepresentation]]

  /**
    * @note This method doesn't work for Certify Keycloak instance because username is not used for this instance
    */
  def getUserByUserName(
    realm: KeycloakRealm,
    username: UserName,
    instance: KeycloakInstance = DeviceKeycloak): Task[Option[UserRepresentation]]

  /**
    * Send an email to user with actions set to the user
    */
  def sendRequiredActionsEmail(
    realm: KeycloakRealm,
    userId: UserId,
    instance: KeycloakInstance): Task[Either[String, Unit]]

  def activate(realm: KeycloakRealm, id: UUID, instance: KeycloakInstance): Task[Either[String, Unit]]

  def deactivate(realm: KeycloakRealm, id: UUID, instance: KeycloakInstance): Task[Either[String, Unit]]

  def remove2faToken(
    realm: KeycloakRealm,
    id: UUID,
    instance: KeycloakInstance): Task[Either[Remove2faTokenKeycloakError, Unit]]

  def updateEmployee(
    realm: KeycloakRealm,
    pocEmployee: PocEmployee,
    firstName: FirstName,
    lastName: LastName,
    email: Email): Task[Either[UpdateEmployeeKeycloakError, Unit]]
}

@Singleton
class DefaultKeycloakUserService @Inject() (keycloakConnector: KeycloakConnector)
  extends KeycloakUserService
  with LazyLogging {

  override def createUser(
    realm: KeycloakRealm,
    createBasicKeycloakUser: CreateBasicKeycloakUser,
    instance: KeycloakInstance,
    userRequiredActions: List[UserRequiredAction.Value] = Nil): Task[Either[UserException, UserId]] = {
    _createUser(realm, createBasicKeycloakUser, instance, userRequiredActions)
  }

  override def createUserWithoutUserName(
    realm: KeycloakRealm,
    createKeycloakUserWithoutUserName: CreateKeycloakUserWithoutUserName,
    instance: KeycloakInstance,
    userRequiredActions: List[UserRequiredAction.Value]): Task[Either[UserException, UserId]] = {
    _createUser(realm, createKeycloakUserWithoutUserName, instance, userRequiredActions)
  }

  private def _createUser(
    realm: KeycloakRealm,
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
          .realm(realm.name)
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
    realm: KeycloakRealm,
    userId: UserId,
    instance: KeycloakInstance): Task[Option[UserRepresentation]] = {
    Task(
      Option(keycloakConnector
        .getKeycloak(instance)
        .realm(realm.name)
        .users()
        .get(userId.value.toString)
        .toRepresentation)
    ).onErrorRecover {
      case _: NotFoundException => None
    }
  }

  override def getUserByUserName(
    realm: KeycloakRealm,
    username: UserName,
    instance: KeycloakInstance = DeviceKeycloak): Task[Option[UserRepresentation]] = {
    Task(
      keycloakConnector
        .getKeycloak(instance)
        .realm(realm.name)
        .users()
        .search(username.value)
        .asScala
        .headOption
    )
  }

  override def addGroupToUserByName(
    realm: KeycloakRealm,
    userName: String,
    groupId: String,
    instance: KeycloakInstance): Task[Either[String, Unit]] = {

    getUserByUserName(realm, UserName(userName), instance).map {
      case Some(userRepresentation: UserRepresentation) =>
        Right(
          keycloakConnector
            .getKeycloak(instance)
            .realm(realm.name)
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
    realm: KeycloakRealm,
    userId: UserId,
    groupId: String,
    instance: KeycloakInstance): Task[Either[String, Unit]] = {

    getUserById(realm, userId, instance).map {
      case Some(userRepresentation: UserRepresentation) =>
        Right(
          keycloakConnector
            .getKeycloak(instance)
            .realm(realm.name)
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

  override def deleteUserByUserName(
    realm: KeycloakRealm,
    username: UserName,
    instance: KeycloakInstance = DeviceKeycloak): Task[Unit] = {
    (for {
      user <- OptionT(getUserByUserName(realm, username, instance))
      _ <- OptionT.liftF(
        Task(
          keycloakConnector
            .getKeycloak(instance)
            .realm(realm.name)
            .users()
            .delete(user.getId)))
      _ = logger.debug(s"Successfully deleted $username user")
    } yield ()).value.void
  }

  override def sendRequiredActionsEmail(
    realm: KeycloakRealm,
    userId: UserId,
    instance: KeycloakInstance): Task[Either[String, Unit]] = {
    getUserById(realm, userId, instance).map {
      case Some(userRepresentation: UserRepresentation) =>
        val actions = userRepresentation.getRequiredActions
        if (!actions.isEmpty) {
          Right(keycloakConnector.getKeycloak(instance)
            .realm(realm.name).users()
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

  override def activate(realm: KeycloakRealm, id: UUID, instance: KeycloakInstance): Task[Either[String, Unit]] =
    switchActive(
      realm,
      id,
      instance,
      (id, ex) => s"Could not activate user with id $id. Reason: ${ex.getMessage}",
      enabled = true)

  override def deactivate(realm: KeycloakRealm, id: UUID, instance: KeycloakInstance): Task[Either[String, Unit]] =
    switchActive(
      realm,
      id,
      instance,
      (id, ex) => s"Could not deactivate user with id $id. Reason: ${ex.getMessage}",
      enabled = false)

  private def switchActive(
    realm: KeycloakRealm,
    id: UUID,
    instance: KeycloakInstance,
    errorMessage: (UUID, Throwable) => String,
    enabled: Boolean): Task[Either[String, Unit]] =
    getUserById(realm, UserId(id), instance)
      .flatMap {
        case Some(ur) =>
          ur.setEnabled(enabled)
          update(realm, id, ur, instance)
            .map(_ => ().asRight)
        case None => Task.pure(s"user with name $id wasn't found".asLeft)
      }.onErrorHandle { ex =>
        val message = errorMessage(id, ex)
        logger.error(message, ex)
        message.asLeft
      }

  override def remove2faToken(
    realm: KeycloakRealm,
    id: UUID,
    instance: KeycloakInstance): Task[Either[Remove2faTokenKeycloakError, Unit]] =
    getUserById(realm, UserId(id), instance).flatMap {
      case Some(ur) => update(
          realm,
          id, {
            ur.setRequiredActions(
              ur.getRequiredActions.asScala.filterNot(_ == UserRequiredAction.WEBAUTHN_REGISTER.toString).asJava)
            ur
          },
          instance).map(_ => ().asRight)
      case None => Task.pure(Remove2faTokenKeycloakError.UserNotFound(s"user with id $id wasn't found").asLeft)
    }.onErrorHandle { ex =>
      val message = s"Could not remove 2FA token: ${ex.getMessage}"
      logger.error(message, ex)
      Remove2faTokenKeycloakError.KeycloakError(message).asLeft
    }

  override def updateEmployee(
    realm: KeycloakRealm,
    pocEmployee: PocEmployee,
    firstName: FirstName,
    lastName: LastName,
    email: Email): Task[Either[UpdateEmployeeKeycloakError, Unit]] =
    pocEmployee.certifyUserId match {
      case None => Task.pure(UpdateEmployeeKeycloakError.MissingCertifyUserId(pocEmployee.id).asLeft)
      case Some(certifyUserId) =>
        val instance = CertifyKeycloak
        getUserById(realm, UserId(certifyUserId), instance).flatMap {
          case None =>
            Task.pure(UpdateEmployeeKeycloakError.UserNotFound(s"user with id $certifyUserId wasn't found").asLeft)
          case Some(ur) => update(
              realm,
              certifyUserId, {
                ur.setFirstName(firstName.value)
                ur.setLastName(lastName.value)
                if (ur.getEmail != email.value) {
                  ur.setEmail(email.value)
                  ur.setRequiredActions(
                    (ur.getRequiredActions.asScala :+ UserRequiredAction.VERIFY_EMAIL.toString).distinct.asJava)
                }
                ur
              },
              instance
            ).map(_ => ().asRight)
        }.onErrorHandle { ex =>
          val message = s"Could not update the employee: ${ex.getMessage}"
          logger.error(message, ex)
          UpdateEmployeeKeycloakError.KeycloakError(message).asLeft
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

  private def update(
    realm: KeycloakRealm,
    id: UUID,
    userRepresentation: UserRepresentation,
    instance: KeycloakInstance): Task[Unit] = {
    Task(
      keycloakConnector
        .getKeycloak(instance)
        .realm(realm.name)
        .users()
        .get(id.toString)
        .update(userRepresentation)
    )
  }
}

sealed trait Remove2faTokenKeycloakError
object Remove2faTokenKeycloakError {
  case class UserNotFound(error: String) extends Remove2faTokenKeycloakError
  case class KeycloakError(error: String) extends Remove2faTokenKeycloakError
}

sealed trait UpdateEmployeeKeycloakError
object UpdateEmployeeKeycloakError {
  case class UserNotFound(error: String) extends UpdateEmployeeKeycloakError
  case class KeycloakError(error: String) extends UpdateEmployeeKeycloakError
  case class MissingCertifyUserId(userId: UUID) extends UpdateEmployeeKeycloakError
}
