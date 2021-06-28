package com.ubirch.data

import com.ubirch.models.keycloak.group.{ CreateKeycloakGroup, GroupName }
import com.ubirch.models.keycloak.roles.{ CreateKeycloakRole, RoleName }
import com.ubirch.models.keycloak.user.{ CreateBasicKeycloakUser, CreateKeycloakUserWithoutUserName }
import com.ubirch.models.user.{ Email, FirstName, LastName, UserName }

import scala.util.Random

object KeycloakTestData {

  def createNewDeviceKeycloakUser(): CreateBasicKeycloakUser = {
    val email = s"$getRandomString@email.com"
    CreateBasicKeycloakUser(
      FirstName(getRandomString),
      LastName(getRandomString),
      UserName(email),
      Email(email)
    )
  }

  def createNewCertifyKeycloakUser(): CreateKeycloakUserWithoutUserName = {
    val email = s"$getRandomString@email.com"
    CreateKeycloakUserWithoutUserName(
      FirstName(getRandomString),
      LastName(getRandomString),
      Email(email)
    )
  }

  def createNewKeycloakRole(): CreateKeycloakRole = CreateKeycloakRole(RoleName(getRandomString))

  def createNewKeycloakGroup(): CreateKeycloakGroup = CreateKeycloakGroup(GroupName(getRandomString))

  private def getRandomString: String = Random.alphanumeric.take(10).mkString("")
}
