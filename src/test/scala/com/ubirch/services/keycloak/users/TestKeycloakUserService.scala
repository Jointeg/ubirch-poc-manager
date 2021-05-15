package com.ubirch.services.keycloak.users
import com.ubirch.models.keycloak.user.{ CreateKeycloakUser, UserAlreadyExists, UserCreationError, UserException }
import com.ubirch.models.user.{ UserId, UserName }
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak, KeycloakInstance }
import monix.eval.Task
import org.keycloak.representations.idm.{ GroupRepresentation, UserRepresentation }

import java.util.UUID
import javax.inject.Singleton
import scala.collection.mutable

@Singleton
class TestKeycloakUserService() extends KeycloakUserService {
  private val keycloakCertifyDatastore = mutable.ListBuffer[UserName]()
  private val keycloakDeviceDatastore = mutable.ListBuffer[UserName]()

  override def createUser(
    createKeycloakUser: CreateKeycloakUser,
    instance: KeycloakInstance = CertifyKeycloak): Task[Either[UserException, UserId]] =
    instance match {
      case CertifyKeycloak => createIfNotExists(keycloakCertifyDatastore, createKeycloakUser)
      case DeviceKeycloak  => createIfNotExists(keycloakDeviceDatastore, createKeycloakUser)
    }

  private def createIfNotExists(datastore: mutable.ListBuffer[UserName], createKeycloakUser: CreateKeycloakUser) = {
    Task {
      datastore.find(_ == createKeycloakUser.userName) match {
        case Some(_) => Left(UserAlreadyExists(createKeycloakUser.userName))
        case None =>
          datastore += createKeycloakUser.userName
          Right(UserId(UUID.randomUUID()))
      }
    }
  }

  override def deleteUser(username: UserName, keycloakInstance: KeycloakInstance = CertifyKeycloak): Task[Unit] =
    keycloakInstance match {
      case CertifyKeycloak =>
        Task {
          keycloakCertifyDatastore -= username
          ()
        }
      case DeviceKeycloak =>
        Task {
          keycloakDeviceDatastore -= username
          ()
        }
    }

  override def getUser(
    username: UserName,
    keycloakInstance: KeycloakInstance = CertifyKeycloak): Task[Option[UserRepresentation]] =
    keycloakInstance match {
      case CertifyKeycloak => findInDatastore(keycloakCertifyDatastore, username)
      case DeviceKeycloak  => findInDatastore(keycloakDeviceDatastore, username)
    }

  private def findInDatastore(datastore: mutable.ListBuffer[UserName], username: UserName) =
    Task {
      datastore
        .find(_ == username)
        .map(userName => {
          val userRepresentation = new UserRepresentation()
          userRepresentation.setUsername(userName.value)
          userRepresentation
        })
    }

  override def addGroupToUser(
    userName: String,
    groupId: String,
    keycloakInstance: KeycloakInstance): Task[Either[String, Unit]] =
    Task { Right(()) }

}
