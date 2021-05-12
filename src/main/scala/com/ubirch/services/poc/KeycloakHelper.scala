package com.ubirch.services.poc

import com.google.inject.Inject
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.keycloak.group.{ GroupCreationError, GroupId, GroupName }
import com.ubirch.models.keycloak.roles.{ CreateKeycloakRole, RoleAlreadyExists, RoleCreationException, RoleName }
import com.ubirch.models.poc.{ Poc, PocStatus }
import com.ubirch.models.tenant.Tenant
import com.ubirch.services.keycloak.groups.KeycloakGroupService
import com.ubirch.services.keycloak.roles.KeycloakRolesService
import com.ubirch.services.{ DeviceKeycloak, KeycloakInstance, UsersKeycloak }
import monix.eval.Task
import PocCreator.throwError

trait KeycloakHelper {

  def createDeviceRole(pocAndStatus: PocAndStatus): Task[PocAndStatus]

  def createDeviceGroup(pocAndStatus: PocAndStatus, tenant: Tenant): Task[PocAndStatus]

  def assignDeviceRealmRoleToGroup(pocAndStatus: PocAndStatus, tenant: Tenant): Task[PocAndStatus]

  def createUserRole(pocAndStatus: PocAndStatus): Task[PocAndStatus]

  def createUserGroup(pocAndStatus: PocAndStatus, tenant: Tenant): Task[PocAndStatus]

  def assignUserRoleToGroup(pocAndStatus: PocAndStatus, tenant: Tenant): Task[PocAndStatus]

}

case class PocAndStatus(poc: Poc, status: PocStatus)

class KeycloakHelperImpl @Inject() (
  roles: KeycloakRolesService,
  groups: KeycloakGroupService)
  extends KeycloakHelper
  with LazyLogging {

  @throws[PocCreationError]
  override def createUserRole(pocAndStatus: PocAndStatus): Task[PocAndStatus] = {
    if (pocAndStatus.status.userRoleCreated) Task(pocAndStatus)
    else {
      roles.createNewRole(CreateKeycloakRole(RoleName(pocAndStatus.poc.roleName)), UsersKeycloak)
        .map {
          case Right(_) => pocAndStatus.copy(status = pocAndStatus.status.copy(userRoleCreated = true))
          case Left(_: RoleAlreadyExists) =>
            pocAndStatus.copy(status = pocAndStatus.status.copy(userRoleCreated = true))
          case Left(l: RoleCreationException) =>
            throwError(pocAndStatus, s"userRealmRole already exists :${l.roleName}")
        }
    }
  }

  @throws[PocCreationError]
  override def createUserGroup(pocAndStatus: PocAndStatus, tenant: Tenant): Task[PocAndStatus] = {
    if (pocAndStatus.status.userGroupCreated) Task(pocAndStatus)
    else {
      addSubGroup(pocAndStatus, tenant.userGroupId.value, UsersKeycloak)
        .map { groupId =>
          val updatedPoc = pocAndStatus.poc.copy(userGroupId = Some(groupId.value))
          val updatedStatus = pocAndStatus.status.copy(userGroupCreated = true)
          pocAndStatus.copy(poc = updatedPoc, status = updatedStatus)
        }
    }
  }

  @throws[PocCreationError]
  override def assignUserRoleToGroup(pocAndStatus: PocAndStatus, tenant: Tenant): Task[PocAndStatus] = {
    val poc = pocAndStatus.poc
    val status = pocAndStatus.status

    if (status.userGroupRoleAssigned) Task(pocAndStatus)
    else if (poc.userGroupId.isEmpty)
      throwError(
        pocAndStatus,
        s"groupId for poc with id ${poc.id} is missing, though poc status says it was already created")
    else {
      findRoleAndAddToGroup(poc, poc.userGroupId.get, status, UsersKeycloak)
        .map {
          case Right(_)       => pocAndStatus.copy(status = pocAndStatus.status.copy(userGroupRoleAssigned = true))
          case Left(errorMsg) => throwError(pocAndStatus, errorMsg)
        }
    }
  }

  @throws[PocCreationError]
  override def createDeviceRole(pocAndStatus: PocAndStatus): Task[PocAndStatus] = {
    if (pocAndStatus.status.deviceRoleCreated) Task(pocAndStatus)
    else {
      roles.createNewRole(CreateKeycloakRole(RoleName(pocAndStatus.poc.roleName)), DeviceKeycloak)
        .map {
          case Right(_) => pocAndStatus.copy(status = pocAndStatus.status.copy(deviceRoleCreated = true))
          case Left(_: RoleAlreadyExists) =>
            pocAndStatus.copy(status = pocAndStatus.status.copy(deviceRoleCreated = true))
          case Left(l: RoleCreationException) =>
            throwError(pocAndStatus, s"deviceRealmRole already exists :${l.roleName}")
        }
    }
  }

  @throws[PocCreationError]
  override def createDeviceGroup(pocAndStatus: PocAndStatus, tenant: Tenant): Task[PocAndStatus] = {
    if (pocAndStatus.status.deviceGroupCreated) Task(pocAndStatus)
    else {
      addSubGroup(pocAndStatus, tenant.deviceGroupId.value, DeviceKeycloak)
        .map { groupId =>
          val updatedPoc = pocAndStatus.poc.copy(deviceGroupId = Some(groupId.value))
          val updatedStatus = pocAndStatus.status.copy(deviceGroupCreated = true)
          pocAndStatus.copy(poc = updatedPoc, status = updatedStatus)
        }
    }
  }

  @throws[PocCreationError]
  override def assignDeviceRealmRoleToGroup(pocAndStatus: PocAndStatus, tenant: Tenant): Task[PocAndStatus] = {
    val poc = pocAndStatus.poc
    val status = pocAndStatus.status

    if (status.deviceGroupRoleAssigned) Task(pocAndStatus)
    else if (poc.deviceGroupId.isEmpty)
      throwError(
        pocAndStatus,
        s"groupId for poc with id ${poc.id} is missing though poc status says it was already created")
    else {
      findRoleAndAddToGroup(poc, poc.deviceGroupId.get, status, DeviceKeycloak)
        .map {
          case Right(_)       => pocAndStatus.copy(status = pocAndStatus.status.copy(deviceGroupRoleAssigned = true))
          case Left(errorMsg) => throwError(pocAndStatus, errorMsg)
        }
    }
  }

  @throws[PocCreationError]
  private def addSubGroup(
    pocAndStatus: PocAndStatus,
    tenantId: String,
    keycloak: KeycloakInstance): Task[GroupId] =
    groups
      .addSubGroup(GroupId(tenantId), GroupName(pocAndStatus.poc.roleName), keycloak)
      .map {
        case Right(groupId)               => groupId
        case Left(ex: GroupCreationError) => throwError(pocAndStatus, ex.errorMsg)
      }

  @throws[PocCreationError]
  private def findRoleAndAddToGroup(
    poc: Poc,
    groupId: String,
    status: PocStatus,
    instance: KeycloakInstance): Task[Either[String, Unit]] = {
    roles
      .findRoleRepresentation(RoleName(poc.roleName), instance)
      .flatMap {
        case Some(role) => groups.addRoleToGroup(GroupId(groupId), role, instance)
        case None =>
          throwError(
            PocAndStatus(poc, status),
            s"adding role ${poc.roleName} to ${poc.deviceGroupId} failed as role doesn't exist")
      }
  }
}
