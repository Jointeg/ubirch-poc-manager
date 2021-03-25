package com.ubirch.models.keycloak.user

import com.ubirch.models.generic.user.{Email, FirstName, LastName}
import org.keycloak.representations.idm.UserRepresentation

case class CreateKeycloakUser(
                               firstName: FirstName,
                               lastName: LastName,
                               email: Email
) {
  def toKeycloakRepresentation: UserRepresentation = {
    val userRepresentation = new UserRepresentation()
    userRepresentation.setFirstName(firstName.value)
    userRepresentation.setLastName(lastName.value)
    userRepresentation.setUsername(email.value)
    userRepresentation.setEmail(email.value)
    userRepresentation
  }
}
