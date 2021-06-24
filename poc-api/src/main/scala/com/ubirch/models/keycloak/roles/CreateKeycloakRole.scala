package com.ubirch.models.keycloak.roles

import org.keycloak.representations.idm.RoleRepresentation

case class CreateKeycloakRole(roleName: RoleName) {
  def toKeycloakRepresentation: RoleRepresentation = {
    val keycloakRepresentation = new RoleRepresentation()
    keycloakRepresentation.setName(roleName.value)
    keycloakRepresentation.setId(RoleId.asUUID().value)
    keycloakRepresentation
  }
}
