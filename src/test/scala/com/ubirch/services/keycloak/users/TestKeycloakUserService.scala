package com.ubirch.services.keycloak.users
import com.ubirch.models.keycloak.user.{CreateKeycloakUser, UserAlreadyExists}
import com.ubirch.models.user.UserName
import monix.eval.Task
import org.keycloak.representations.idm.UserRepresentation

import javax.inject.Singleton
import scala.collection.mutable

@Singleton
class TestKeycloakUserService() extends KeycloakUserService {
  private val keycloakUserDatastore = mutable.ListBuffer[UserName]()

  override def createUser(createKeycloakUser: CreateKeycloakUser): Task[Either[UserAlreadyExists, Unit]] =
    Task {
      keycloakUserDatastore.find(_ == createKeycloakUser.userName) match {
        case Some(_) => Left(UserAlreadyExists(createKeycloakUser.userName))
        case None =>
          keycloakUserDatastore += createKeycloakUser.userName
          Right(())
      }
    }
  override def deleteUser(username: UserName): Task[Unit] =
    Task {
      keycloakUserDatastore -= username
      ()
    }
  override def getUser(username: UserName): Task[Option[UserRepresentation]] =
    Task {
      keycloakUserDatastore
        .find(_ == username)
        .map(userName => {
          val userRepresentation = new UserRepresentation()
          userRepresentation.setUsername(userName.value)
          userRepresentation
        })
    }
}
