package com.ubirch.services.keycloak.users
import com.ubirch.models.keycloak.user.{
  CreateBasicKeycloakUser,
  CreateKeycloakUserWithoutUserName,
  UserAlreadyExists,
  UserException,
  UserRequiredAction
}
import com.ubirch.models.user.{ UserId, UserName }
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak, KeycloakInstance }
import monix.eval.Task
import org.keycloak.representations.idm.UserRepresentation

import java.util.UUID
import javax.inject.Singleton
import scala.collection.mutable

@Singleton
class TestKeycloakUserService() extends KeycloakUserService {
  private val keycloakCertifyDatastore = mutable.Map[UserId, CreateKeycloakUserWithoutUserName]()
  private val keycloakDeviceDatastore = mutable.Map[UserId, CreateBasicKeycloakUser]()

  override def createUser(
    createBasicKeycloakUser: CreateBasicKeycloakUser,
    instance: KeycloakInstance,
    userRequiredActions: List[UserRequiredAction.Value] = Nil): Task[Either[UserException, UserId]] = {
    keycloakDeviceDatastore.find(_._2.userName == createBasicKeycloakUser.userName) match {
      case Some(_) => Task(Left(UserAlreadyExists(createBasicKeycloakUser.userName.value)))
      case None =>
        val userId = UUID.randomUUID()
        keycloakDeviceDatastore += (UserId(userId) -> createBasicKeycloakUser)
        Task(Right(UserId(userId)))
    }
  }

  override def createUserWithoutUserName(
    createKeycloakUserWithoutUserName: CreateKeycloakUserWithoutUserName,
    instance: KeycloakInstance,
    userRequiredActions: List[UserRequiredAction.Value] = Nil): Task[Either[UserException, UserId]] = {
    keycloakCertifyDatastore.find(_._2.email == createKeycloakUserWithoutUserName.email) match {
      case Some(_) => Task(Left(UserAlreadyExists(createKeycloakUserWithoutUserName.email.value)))
      case None =>
        val userId = UUID.randomUUID()
        keycloakCertifyDatastore += (UserId(userId) -> createKeycloakUserWithoutUserName)
        Task(Right(UserId(userId)))
    }
  }

  override def deleteUserByUserName(
    username: UserName,
    keycloakInstance: KeycloakInstance = DeviceKeycloak): Task[Unit] =
    keycloakInstance match {
      case CertifyKeycloak => Task(())
      case DeviceKeycloak =>
        Task {
          keycloakDeviceDatastore.filter(_._2.userName == username).foreach {
            case (userId, user) =>
              keycloakDeviceDatastore -= userId
          }
          ()
        }
    }

  override def getUserById(
    userId: UserId,
    instance: KeycloakInstance): Task[Option[UserRepresentation]] = Task {
    val datastore = instance match {
      case CertifyKeycloak => keycloakCertifyDatastore
      case DeviceKeycloak  => keycloakDeviceDatastore
    }
    datastore.find(_._1 == userId).map(_._2.toKeycloakRepresentation)
  }

  override def getUserByUserName(
    username: UserName,
    keycloakInstance: KeycloakInstance = DeviceKeycloak): Task[Option[UserRepresentation]] =
    keycloakInstance match {
      case CertifyKeycloak => Task(None) // certify keycloak doesn't have user name field
      case DeviceKeycloak => Task {
          keycloakDeviceDatastore
            .find(_._2.userName == username)
            .map(_._2.toKeycloakRepresentation)
        }
    }

  override def addGroupToUserByName(
    userName: String,
    groupId: String,
    keycloakInstance: KeycloakInstance): Task[Either[String, Unit]] =
    Task { Right(()) }

  override def addGroupToUserById(
    userId: UserId,
    groupId: String,
    keycloakInstance: KeycloakInstance): Task[Either[String, Unit]] =
    Task { Right(()) }

  override def sendRequiredActionsEmail(
    userId: UserId,
    instance: KeycloakInstance): Task[Either[String, Unit]] =
    Task(Right(()))

  override def activate(id: UUID, instance: KeycloakInstance): Task[Either[String, Unit]] = Task.pure(Right(()))

  override def deactivate(id: UUID, instance: KeycloakInstance): Task[Either[String, Unit]] = Task.pure(Right(()))

  override def remove2faToken(id: UUID, instance: KeycloakInstance): Task[Either[Remove2faTokenKeycloakError, Unit]] = Task.pure(Right(()))
}
