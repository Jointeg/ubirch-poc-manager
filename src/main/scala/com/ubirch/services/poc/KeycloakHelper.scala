package com.ubirch.services.poc

import com.google.inject.Inject
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.db.tables.PocRepository
import com.ubirch.models.keycloak.group.{ GroupCreationError, GroupId, GroupName }
import com.ubirch.models.keycloak.roles.{ CreateKeycloakRole, RoleAlreadyExists, RoleCreationException, RoleName }
import com.ubirch.models.poc.{ Poc, PocStatus }
import com.ubirch.models.tenant.Tenant
import com.ubirch.services.keycloak.groups.KeycloakGroupService
import com.ubirch.services.keycloak.roles.KeycloakRolesService
import com.ubirch.services.{ DeviceKeycloak, KeycloakInstance, UsersKeycloak }
import monix.eval.Task

trait KeycloakHelper {

  def createDeviceRole(poc: Poc, status: PocStatus): Task[PocStatus]

  def createDeviceGroup(poc: Poc, status: PocStatus, tenant: Tenant): Task[PocAndStatus]

  def assignDeviceRealmRoleToGroup(pocAndStatus: PocAndStatus, tenant: Tenant): Task[PocStatus]

  def createUserRole(poc: Poc, status: PocStatus): Task[PocStatus]

  def createUserGroup(poc: Poc, status: PocStatus, tenant: Tenant): Task[PocAndStatus]

  def assignUserRoleToGroup(pocAndStatus: PocAndStatus, tenant: Tenant): Task[PocStatus]

}

case class PocAndStatus(poc: Poc, status: PocStatus)

class KeycloakHelperImpl @Inject() (
  roles: KeycloakRolesService,
  groups: KeycloakGroupService,
  pocRepository: PocRepository)
  extends KeycloakHelper
  with LazyLogging {

  @throws[PocCreationError]
  override def createUserRole(poc: Poc, status: PocStatus): Task[PocStatus] = {
    if (status.userRoleCreated) Task(status)
    else {
      roles.createNewRole(CreateKeycloakRole(RoleName(poc.roleName)), UsersKeycloak)
        .map {
          case Right(_)                       => status.copy(userRoleCreated = true)
          case Left(_: RoleAlreadyExists)     => status.copy(userRoleCreated = true)
          case Left(l: RoleCreationException) => throwError(status, s"userRealmRole already exists :${l.roleName}")
        }
    }
  }

  @throws[PocCreationError]
  override def createUserGroup(poc: Poc, status: PocStatus, tenant: Tenant): Task[PocAndStatus] = {
    if (status.userGroupCreated) Task(PocAndStatus(poc, status))
    else {
      addSubGroup(poc, status, tenant.userGroupId.value, UsersKeycloak)
        .flatMap { groupId =>
          val updatedPoc = poc.copy(userGroupId = Some(groupId.value))
          updatePoc(updatedPoc, status, status.copy(userGroupCreated = true), groupId)
        }
    }
  }

  @throws[PocCreationError]
  override def assignUserRoleToGroup(pocAndStatus: PocAndStatus, tenant: Tenant): Task[PocStatus] = {
    val poc = pocAndStatus.poc
    val status = pocAndStatus.status

    if (status.userGroupRoleAssigned) Task(status)
    else if (poc.userGroupId.isEmpty)
      throwError(status, s"groupId for poc with id ${poc.id} is missing, though poc status says it was already created")
    else {
      findRoleAndAddToGroup(poc, poc.userGroupId.get, status, UsersKeycloak)
        .map {
          case Right(_)       => status.copy(userGroupRoleAssigned = true)
          case Left(errorMsg) => throwError(status, errorMsg)
        }
    }
  }

  @throws[PocCreationError]
  override def createDeviceRole(poc: Poc, status: PocStatus): Task[PocStatus] = {
    if (status.deviceRoleCreated) Task(status)
    else {
      roles.createNewRole(CreateKeycloakRole(RoleName(poc.roleName)), DeviceKeycloak)
        .map {
          case Right(_)                       => status.copy(deviceRoleCreated = true)
          case Left(_: RoleAlreadyExists)     => status.copy(deviceRoleCreated = true)
          case Left(l: RoleCreationException) => throwError(status, s"deviceRealmRole already exists :${l.roleName}")
        }
    }
  }

  @throws[PocCreationError]
  override def createDeviceGroup(poc: Poc, status: PocStatus, tenant: Tenant): Task[PocAndStatus] = {
    if (status.deviceGroupCreated) Task(PocAndStatus(poc, status))
    else {
      addSubGroup(poc, status, tenant.deviceGroupId.value, DeviceKeycloak)
        .flatMap { groupId =>
          val updatedPoc = poc.copy(deviceGroupId = Some(groupId.value))
          updatePoc(updatedPoc, status, status.copy(deviceGroupCreated = true), groupId)
        }
    }
  }

  @throws[PocCreationError]
  override def assignDeviceRealmRoleToGroup(pocAndStatus: PocAndStatus, tenant: Tenant): Task[PocStatus] = {
    val poc = pocAndStatus.poc
    val status = pocAndStatus.status

    if (status.deviceGroupRoleAssigned) Task(status)
    else if (poc.deviceGroupId.isEmpty)
      throwError(status, s"groupId for poc with id ${poc.id} is missing though poc status says it was already created")
    else {
      findRoleAndAddToGroup(poc, poc.deviceGroupId.get, status, DeviceKeycloak)
        .map {
          case Right(_)       => status.copy(deviceGroupRoleAssigned = true)
          case Left(errorMsg) => throwError(status, errorMsg)
        }
    }
  }

  @throws[PocCreationError]
  private def updatePoc(poc: Poc, failure: PocStatus, success: PocStatus, groupId: GroupId): Task[PocAndStatus] =
    pocRepository
      .updatePoc(poc)
      .map(_ => PocAndStatus(poc, success))
      .onErrorHandle { ex =>
        val errorMsg = s"couldn't store newly created device realm groupId ${groupId.value} for poc with id ${poc.id}; "
        throwAndLogError(failure, errorMsg, ex)
      }

  @throws[PocCreationError]
  private def addSubGroup(
    poc: Poc,
    status: PocStatus,
    tenantId: String,
    keycloak: KeycloakInstance): Task[GroupId] =
    groups
      .addSubGroup(GroupId(tenantId), GroupName(poc.roleName), keycloak)
      .map {
        case Right(groupId)               => groupId
        case Left(ex: GroupCreationError) => throwError(status, ex.errorMsg)
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
          throwError(status, s"adding role ${poc.roleName} to ${poc.deviceGroupId} failed as role doesn't exist")
      }
  }

  @throws[PocCreationError]
  private def throwAndLogError(status: PocStatus, msg: String, ex: Throwable): Nothing = {
    logger.error(msg, ex)
    throwError(status, msg)
  }

  @throws[PocCreationError]
  def throwError(status: PocStatus, msg: String) =
    throw PocCreationError(status.copy(errorMessage = Some(msg)))

}
