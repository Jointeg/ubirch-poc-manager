package com.ubirch.data

import com.ubirch.models.generic.user.{Email, FirstName, LastName}
import com.ubirch.models.keycloak.roles.{CreateKeycloakRole, RoleName}
import com.ubirch.models.keycloak.user.CreateKeycloakUser

import scala.util.Random

object KeycloakTestData {

  def createNewKeycloakUser(): CreateKeycloakUser =
    CreateKeycloakUser(
      FirstName(Random.alphanumeric.take(10).mkString("")),
      LastName(Random.alphanumeric.take(10).mkString("")),
      Email(s"${Random.alphanumeric.take(10).mkString("")}@email.com")
    )

  def createNewKeycloakRole(): CreateKeycloakRole =
    CreateKeycloakRole(RoleName(Random.alphanumeric.take(10).mkString("")))

}
