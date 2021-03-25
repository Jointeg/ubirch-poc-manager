package com.ubirch.data

import com.ubirch.models.generic.user.{Email, FirstName, LastName}
import com.ubirch.models.keycloak.user.CreateKeycloakUser

import scala.util.Random

object KeycloakUserTestData {

  def createNewKeycloakUser(): CreateKeycloakUser =
    CreateKeycloakUser(
      FirstName(Random.alphanumeric.take(10).mkString("")),
      LastName(Random.alphanumeric.take(10).mkString("")),
      Email(s"${Random.alphanumeric.take(10).mkString("")}@email.com")
    )

}
