package com.ubirch.data

import com.ubirch.models.keycloak.group.{ CreateKeycloakGroup, GroupName }
import com.ubirch.models.keycloak.roles.{ CreateKeycloakRole, RoleName }
import com.ubirch.models.keycloak.user.{ CreateDeviceKeycloakUser }
import com.ubirch.models.user.{ Email, FirstName, LastName, UserName }

import scala.util.Random

object KeycloakTestData {

  def createNewKeycloakUser(): CreateDeviceKeycloakUser = {
    val email = s"${Random.alphanumeric.take(10).mkString("")}@email.com"
    CreateDeviceKeycloakUser(
      FirstName(Random.alphanumeric.take(10).mkString("")),
      LastName(Random.alphanumeric.take(10).mkString("")),
      UserName(email),
      Email(email)
    )
  }

  def createNewKeycloakRole(): CreateKeycloakRole =
    CreateKeycloakRole(RoleName(Random.alphanumeric.take(10).mkString("")))

  def createNewKeycloakGroup(): CreateKeycloakGroup =
    CreateKeycloakGroup(GroupName(Random.alphanumeric.take(10).mkString("")))

}
