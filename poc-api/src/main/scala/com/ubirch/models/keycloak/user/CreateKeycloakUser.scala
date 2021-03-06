package com.ubirch.models.keycloak.user

import com.ubirch.models.user.{ Email, FirstName, LastName, UserName }
import org.keycloak.representations.idm.UserRepresentation

sealed trait CreateKeycloakUser {
  def toKeycloakRepresentation: UserRepresentation
}

case class CreateBasicKeycloakUser(
  firstName: FirstName,
  lastName: LastName,
  userName: UserName,
  email: Email
) extends CreateKeycloakUser {
  def toKeycloakRepresentation: UserRepresentation = {
    val userRepresentation = new UserRepresentation()
    userRepresentation.setFirstName(firstName.value)
    userRepresentation.setLastName(lastName.value)
    userRepresentation.setUsername(userName.value)
    userRepresentation.setEmail(email.value)
    userRepresentation
  }
}

case class CreateKeycloakUserWithoutUserName(
  firstName: FirstName,
  lastName: LastName,
  email: Email
) extends CreateKeycloakUser {
  def toKeycloakRepresentation: UserRepresentation = {
    val userRepresentation = new UserRepresentation()
    userRepresentation.setFirstName(firstName.value)
    userRepresentation.setLastName(lastName.value)
    userRepresentation.setUsername(email.value)
    userRepresentation.setEmail(email.value)
    userRepresentation
  }
}
