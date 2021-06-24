package com.ubirch.models.keycloak.group

import org.keycloak.representations.idm.GroupRepresentation

case class CreateKeycloakGroup(groupName: GroupName) {
  def toKeycloakRepresentation: GroupRepresentation = {
    val groupRepresentation = new GroupRepresentation()
    groupRepresentation.setName(groupName.value)
    groupRepresentation
  }
}
