package com.ubirch.services.keycloak.users
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
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak, KeycloakInstance }
import monix.eval.Task
import org.keycloak.representations.idm.UserRepresentation

import java.util.UUID
import javax.inject.Singleton
import scala.collection.mutable

@Singleton
class TestKeycloakUserService() extends KeycloakUserService {
  private val keycloakCertifyDatastore = mutable.Map[UserId, CreateCertifyKeycloakUser]()
  private val keycloakDeviceDatastore = mutable.Map[UserId, CreateDeviceKeycloakUser]()

  override def createUser(
    createKeycloakUser: CreateKeycloakUser,
    instance: KeycloakInstance = CertifyKeycloak,
    userRequiredActions: List[UserRequiredAction.Value] = Nil): Task[Either[UserException, UserId]] = {
    (createKeycloakUser, instance) match {
      case (_: CreateDeviceKeycloakUser, CertifyKeycloak) =>
        Task(Left(UserCreationError("user and instance don't match. device user and certify keycloak")))
      case (_: CreateCertifyKeycloakUser, DeviceKeycloak) =>
        Task(Left(UserCreationError("user and instance don't match. certify user and instance keycloak")))
      case (user: CreateCertifyKeycloakUser, CertifyKeycloak) =>
        keycloakCertifyDatastore.find(_._2.email == user.email) match {
          case Some(_) => Task(Left(UserAlreadyExists(user.email.value)))
          case None =>
            val userId = UUID.randomUUID()
            keycloakCertifyDatastore += (UserId(userId) -> user)
            Task(Right(UserId(userId)))
        }
      case (user: CreateDeviceKeycloakUser, DeviceKeycloak) =>
        keycloakDeviceDatastore.find(_._2.userName == user.userName) match {
          case Some(_) => Task(Left(UserAlreadyExists(user.userName.value)))
          case None =>
            val userId = UUID.randomUUID()
            keycloakDeviceDatastore += (UserId(userId) -> user)
            Task(Right(UserId(userId)))
        }
    }
  }

  override def deleteUser(username: UserName, keycloakInstance: KeycloakInstance = CertifyKeycloak): Task[Unit] =
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
    instance: KeycloakInstance = CertifyKeycloak): Task[Option[UserRepresentation]] = Task {
    val datastore = instance match {
      case CertifyKeycloak => keycloakCertifyDatastore
      case DeviceKeycloak  => keycloakDeviceDatastore
    }
    datastore.find(_._1 == userId).map(_._2.toKeycloakRepresentation)
  }

  override def getUser(
    username: UserName,
    keycloakInstance: KeycloakInstance = CertifyKeycloak): Task[Option[UserRepresentation]] =
    keycloakInstance match {
      case CertifyKeycloak => Task(None) // certify keycloak doesn't have user name field
      case DeviceKeycloak => Task {
          keycloakDeviceDatastore
            .find(_._2.userName == username)
            .map(_._2.toKeycloakRepresentation)
        }
    }

  override def addGroupToUserByUserId(
    userId: UserId,
    groupId: String,
    keycloakInstance: KeycloakInstance): Task[Either[String, Unit]] =
    Task { Right(()) }

  override def addGroupToUser(
    userName: String,
    groupId: String,
    keycloakInstance: KeycloakInstance): Task[Either[String, Unit]] =
    Task { Right(()) }

  override def sendRequiredActionsEmail(
    userId: UserId,
    instance: KeycloakInstance): Task[Either[String, Unit]] =
    Task(Right(()))
}
