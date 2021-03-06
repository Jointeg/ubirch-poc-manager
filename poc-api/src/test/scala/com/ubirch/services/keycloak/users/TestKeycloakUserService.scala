package com.ubirch.services.keycloak.users

import com.ubirch.models.keycloak.user.{
  CreateBasicKeycloakUser,
  CreateKeycloakUserWithoutUserName,
  UserAlreadyExists,
  UserException,
  UserRequiredAction
}
import com.ubirch.models.pocEmployee.PocEmployee
import com.ubirch.models.user.{ Email, FirstName, LastName, UserId, UserName }
import com.ubirch.services.keycloak.KeycloakRealm
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak, KeycloakInstance }
import monix.eval.Task
import org.keycloak.representations.idm.UserRepresentation
import cats.syntax.either._

import java.util.UUID
import javax.inject.Singleton
import scala.collection.mutable

@Singleton
class TestKeycloakUserService() extends KeycloakUserService {
  private val keycloakCertifyDatastore = mutable.Map[UserId, CreateKeycloakUserWithoutUserName]()
  private val keycloakDeviceDatastore = mutable.Map[UserId, CreateBasicKeycloakUser]()

  override def createUser(
    realm: KeycloakRealm,
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
    realm: KeycloakRealm,
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
    realm: KeycloakRealm,
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
    realm: KeycloakRealm,
    userId: UserId,
    instance: KeycloakInstance): Task[Option[UserRepresentation]] = Task {
    val datastore = instance match {
      case CertifyKeycloak => keycloakCertifyDatastore
      case DeviceKeycloak  => keycloakDeviceDatastore
    }
    datastore.find(_._1 == userId).map(_._2.toKeycloakRepresentation)
  }

  override def getUserByUserName(
    realm: KeycloakRealm,
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
    realm: KeycloakRealm,
    userName: String,
    groupId: String,
    keycloakInstance: KeycloakInstance): Task[Either[String, Unit]] =
    Task { Right(()) }

  override def addGroupToUserById(
    realm: KeycloakRealm,
    userId: UserId,
    groupId: String,
    keycloakInstance: KeycloakInstance): Task[Either[String, Unit]] =
    Task { Right(()) }

  override def sendRequiredActionsEmail(
    realm: KeycloakRealm,
    userId: UserId,
    instance: KeycloakInstance): Task[Either[String, Unit]] =
    Task(Right(()))

  override def activate(realm: KeycloakRealm, id: UUID, instance: KeycloakInstance): Task[Either[String, Unit]] =
    Task.pure(Right(()))

  override def deactivate(realm: KeycloakRealm, id: UUID, instance: KeycloakInstance): Task[Either[String, Unit]] =
    Task.pure(Right(()))

  override def remove2faToken(
    realm: KeycloakRealm,
    id: UUID,
    instance: KeycloakInstance): Task[Either[Remove2faTokenKeycloakError, Unit]] =
    Task.pure(Right(()))

  override def updateEmployee(
    realm: KeycloakRealm,
    pocEmployee: PocEmployee,
    firstName: FirstName,
    lastName: LastName,
    email: Email): Task[Either[UpdateEmployeeKeycloakError, Unit]] =
    pocEmployee.certifyUserId match {
      case None => Task.pure(UpdateEmployeeKeycloakError.MissingCertifyUserId(pocEmployee.id).asLeft)
      case Some(certifyUserId) => Task {
          val userId = UserId(certifyUserId)
          keycloakCertifyDatastore.get(userId) match {
            case None => UpdateEmployeeKeycloakError.UserNotFound(s"user with id $certifyUserId wasn't found").asLeft
            case Some(user) =>
              keycloakCertifyDatastore.put(userId, user.copy(firstName = firstName, lastName = lastName, email = email))
              ().asRight
          }
        }
    }

}
