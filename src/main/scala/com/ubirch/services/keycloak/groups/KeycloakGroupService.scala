package com.ubirch.services.keycloak.groups

import com.ubirch.models.keycloak.group._
import com.ubirch.services.{ KeycloakInstance, UsersKeycloak }
import monix.eval.Task
import org.keycloak.representations.idm.{ GroupRepresentation, RoleRepresentation }

trait KeycloakGroupService {

  def createGroup(
    createKeycloakGroup: CreateKeycloakGroup,
    keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Either[GroupException, GroupId]]

  def findGroupById(
    groupId: GroupId,
    keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Either[String, GroupRepresentation]]

  def findGroupByName(
    groupName: GroupName,
    keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Either[GroupNotFound, GroupRepresentation]]

  def addSubGroup(
    parentGroupId: GroupId,
    childGroupName: GroupName,
    keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Either[GroupException, GroupId]]

  def addRoleToGroup(
    groupId: GroupId,
    role: RoleRepresentation,
    keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Either[String, Unit]]

  def deleteGroup(groupName: GroupName, keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Unit]

}
