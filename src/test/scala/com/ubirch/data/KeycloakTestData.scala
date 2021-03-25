package com.ubirch.data

import com.ubirch.models.generic.user.{Email, FirstName, LastName}
import com.ubirch.models.keycloak.roles.{CreateKeycloakRole, RoleName}
import com.ubirch.models.keycloak.user.{CreateKeycloakUser, UserName}

import scala.util.Random

object KeycloakTestData {

  def createNewKeycloakUser(): CreateKeycloakUser = {
    val email = s"${Random.alphanumeric.take(10).mkString("")}@email.com"
    CreateKeycloakUser(
      FirstName(Random.alphanumeric.take(10).mkString("")),
      LastName(Random.alphanumeric.take(10).mkString("")),
      UserName(email),
      Email(email)
    )
  }

  def createNewKeycloakRole(): CreateKeycloakRole =
    CreateKeycloakRole(RoleName(Random.alphanumeric.take(10).mkString("")))

}
