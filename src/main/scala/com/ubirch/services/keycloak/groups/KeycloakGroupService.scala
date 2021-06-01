package com.ubirch.services.keycloak.groups

import com.ubirch.models.keycloak.group._
import com.ubirch.services.KeycloakInstance
import monix.eval.Task
import org.keycloak.representations.idm.{ GroupRepresentation, RoleRepresentation, UserRepresentation }

trait KeycloakGroupService {

  def createGroup(
    createKeycloakGroup: CreateKeycloakGroup,
    instance: KeycloakInstance): Task[Either[GroupCreationError, GroupId]]

  def findGroupById(
    groupId: GroupId,
    instance: KeycloakInstance): Task[Either[String, GroupRepresentation]]

  def findGroupByName(
    groupName: GroupName,
    instance: KeycloakInstance): Task[Either[GroupNotFound, GroupRepresentation]]

  def addSubGroup(
    parentGroupId: GroupId,
    childGroupName: GroupName,
    instance: KeycloakInstance): Task[Either[GroupCreationError, GroupId]]

  def assignRoleToGroup(
    groupId: GroupId,
    role: RoleRepresentation,
    instance: KeycloakInstance): Task[Either[String, Unit]]

  def addMemberToGroup(
    groupId: GroupId,
    user: UserRepresentation,
    instance: KeycloakInstance): Task[Either[String, Boolean]]

  def deleteGroup(groupName: GroupName, instance: KeycloakInstance): Task[Unit]

}
