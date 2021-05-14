package com.ubirch.services.keycloak.groups

import com.ubirch.models.keycloak.group._
import com.ubirch.services.{ CertifyKeycloak, KeycloakInstance }
import monix.eval.Task
import org.keycloak.representations.idm.{ GroupRepresentation, RoleRepresentation, UserRepresentation }

trait KeycloakGroupService {

  def createGroup(
    createKeycloakGroup: CreateKeycloakGroup,
    keycloakInstance: KeycloakInstance = CertifyKeycloak): Task[Either[GroupCreationError, GroupId]]

  def findGroupById(
    groupId: GroupId,
    keycloakInstance: KeycloakInstance = CertifyKeycloak): Task[Either[String, GroupRepresentation]]

  def findGroupByName(
    groupName: GroupName,
    keycloakInstance: KeycloakInstance = CertifyKeycloak): Task[Either[GroupNotFound, GroupRepresentation]]

  def addSubGroup(
    parentGroupId: GroupId,
    childGroupName: GroupName,
    keycloakInstance: KeycloakInstance = CertifyKeycloak): Task[Either[GroupCreationError, GroupId]]

  def addRoleToGroup(
    groupId: GroupId,
    role: RoleRepresentation,
    keycloakInstance: KeycloakInstance = CertifyKeycloak): Task[Either[String, Unit]]

  def addMemberToGroup(
    groupId: GroupId,
    user: UserRepresentation,
    keycloakInstance: KeycloakInstance = CertifyKeycloak): Task[Either[String, Boolean]]

  def deleteGroup(groupName: GroupName, keycloakInstance: KeycloakInstance = CertifyKeycloak): Task[Unit]

}
