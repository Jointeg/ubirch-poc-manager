package com.ubirch.services.keycloak.groups

import com.ubirch.models.keycloak.group._
import com.ubirch.services.KeycloakInstance
import com.ubirch.services.keycloak.KeycloakRealm
import monix.eval.Task
import org.keycloak.representations.idm.{ GroupRepresentation, RoleRepresentation, UserRepresentation }

trait KeycloakGroupService {

  def createGroup(
    realm: KeycloakRealm,
    createKeycloakGroup: CreateKeycloakGroup,
    instance: KeycloakInstance): Task[Either[GroupCreationError, GroupId]]

  def findGroupById(
    realm: KeycloakRealm,
    groupId: GroupId,
    instance: KeycloakInstance): Task[Either[String, GroupRepresentation]]

  def findGroupByName(
    realm: KeycloakRealm,
    groupName: GroupName,
    instance: KeycloakInstance): Task[Either[GroupNotFound, GroupRepresentation]]

  def addSubGroup(
    realm: KeycloakRealm,
    parentGroupId: GroupId,
    childGroupName: GroupName,
    instance: KeycloakInstance): Task[Either[GroupCreationError, GroupId]]

  def assignRoleToGroup(
    realm: KeycloakRealm,
    groupId: GroupId,
    role: RoleRepresentation,
    instance: KeycloakInstance): Task[Either[String, Unit]]

  def addMemberToGroup(
    realm: KeycloakRealm,
    groupId: GroupId,
    user: UserRepresentation,
    instance: KeycloakInstance): Task[Either[String, Boolean]]

  def deleteGroup(realm: KeycloakRealm, groupName: GroupName, instance: KeycloakInstance): Task[Unit]

}
