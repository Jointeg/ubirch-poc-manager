package com.ubirch.services.keycloak.users
import com.ubirch.models.keycloak.user.{CreateKeycloakUser, UserAlreadyExists}
import com.ubirch.models.user.UserName
import com.ubirch.services.{DeviceKeycloak, KeycloakInstance, UsersKeycloak}
import monix.eval.Task
import org.keycloak.representations.idm.UserRepresentation

import javax.inject.Singleton
import scala.collection.mutable

@Singleton
class TestKeycloakUserService() extends KeycloakUserService {
  private val keycloakUserDatastore = mutable.ListBuffer[UserName]()
  private val keycloakDeviceDatastore = mutable.ListBuffer[UserName]()

  override def createUser(
    createKeycloakUser: CreateKeycloakUser,
    keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Either[UserAlreadyExists, Unit]] =
    keycloakInstance match {
      case UsersKeycloak => createIfNotExists(keycloakUserDatastore, createKeycloakUser)
      case DeviceKeycloak => createIfNotExists(keycloakDeviceDatastore, createKeycloakUser)
    }

  private def createIfNotExists(datastore: mutable.ListBuffer[UserName], createKeycloakUser: CreateKeycloakUser) = {
    Task {
      datastore.find(_ == createKeycloakUser.userName) match {
        case Some(_) => Left(UserAlreadyExists(createKeycloakUser.userName))
        case None =>
          datastore += createKeycloakUser.userName
          Right(())
      }
    }
  }

  override def deleteUser(username: UserName, keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Unit] =
    keycloakInstance match {
      case UsersKeycloak =>
        Task {
          keycloakUserDatastore -= username
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
    keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Option[UserRepresentation]] =
    keycloakInstance match {
      case UsersKeycloak => findInDatastore(keycloakUserDatastore, username)
      case DeviceKeycloak => findInDatastore(keycloakDeviceDatastore, username)
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
}
