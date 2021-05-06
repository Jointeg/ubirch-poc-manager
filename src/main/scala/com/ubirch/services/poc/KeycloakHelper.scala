package com.ubirch.services.poc

import com.google.inject.Inject
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.db.tables.PocRepository
import com.ubirch.models.keycloak.group.{ GroupCreationError, GroupId, GroupName }
import com.ubirch.models.keycloak.roles.{ CreateKeycloakRole, RoleAlreadyExists, RoleName }
import com.ubirch.models.poc.{ Poc, PocStatus }
import com.ubirch.models.tenant.{ Tenant, TenantGroupId }
import com.ubirch.services.keycloak.groups.KeycloakGroupService
import com.ubirch.services.keycloak.roles.KeycloakRolesService
import com.ubirch.services.{ DeviceKeycloak, KeycloakInstance, UsersKeycloak }
import monix.eval.Task

trait KeycloakHelper {

  def createDeviceRealmRole(poc: Poc, status: PocStatus): Task[PocStatus]

  def createDeviceRealmGroup(poc: Poc, status: PocStatus, tenant: Tenant): Task[PocAndStatus]

  def assignDeviceRealmRoleToGroup(pocAndStatus: PocAndStatus, tenant: Tenant): Task[PocStatus]

  def createUserRealmRole(poc: Poc, status: PocStatus): Task[PocStatus]

  def createUserRealmGroup(poc: Poc, status: PocStatus, tenant: Tenant): Task[PocAndStatus]

  def assignUserRealmRoleToGroup(pocAndStatus: PocAndStatus, tenant: Tenant): Task[PocStatus]

}

case class PocAndStatus(poc: Poc, status: PocStatus)

class KeycloakHelperImpl @Inject() (
  roles: KeycloakRolesService,
  groups: KeycloakGroupService,
  pocRepository: PocRepository)
  extends KeycloakHelper
  with LazyLogging {

  override def createDeviceRealmRole(poc: Poc, status: PocStatus): Task[PocStatus] = {
    if (status.deviceRealmRoleCreated)
      Task(status)
    else {
      roles
        .createNewRole(CreateKeycloakRole(RoleName(poc.roleName)), DeviceKeycloak)
        .map {
          case Right(_) =>
            status.copy(deviceRealmRoleCreated = true)
          case Left(l: RoleAlreadyExists) =>
            throwError(status, s"deviceRealmRole already exists :${l.roleName}")
        }
    }
  }

  override def createUserRealmRole(poc: Poc, status: PocStatus): Task[PocStatus] = {
    if (status.userRealmRoleCreated) {
      Task(status)
    } else {
      roles
        .createNewRole(CreateKeycloakRole(RoleName(poc.roleName)), UsersKeycloak)
        .map {
          case Right(_) =>
            status.copy(userRealmRoleCreated = true)
          case Left(l: RoleAlreadyExists) =>
            throwError(status, s"userRealmRole already exists :${l.roleName}")
        }
    }
  }

  override def createDeviceRealmGroup(poc: Poc, status: PocStatus, tenant: Tenant): Task[PocAndStatus] = {
    if (status.deviceRealmGroupCreated)
      Task(PocAndStatus(poc, status))
    else {
      addSubGroup(poc, status, tenant.deviceGroupId, DeviceKeycloak)
        .flatMap { groupId =>
          val updatedPoc = poc.copy(deviceGroupId = Some(groupId.value))
          updatePoc(updatedPoc, status, status.copy(deviceRealmGroupCreated = true), groupId)
        }
    }
  }

  override def createUserRealmGroup(poc: Poc, status: PocStatus, tenant: Tenant): Task[PocAndStatus] = {
    if (status.userRealmGroupCreated) {
      Task(PocAndStatus(poc, status))
    } else {
      addSubGroup(poc, status, tenant.userGroupId, UsersKeycloak)
        .flatMap { groupId =>
          val updatedPoc = poc.copy(userGroupId = Some(groupId.value))
          updatePoc(updatedPoc, status, status.copy(userRealmGroupCreated = true), groupId)
        }
    }
  }

  override def assignDeviceRealmRoleToGroup(pocAndStatus: PocAndStatus, tenant: Tenant): Task[PocStatus] = {
    val poc = pocAndStatus.poc
    val status = pocAndStatus.status
    if (status.deviceRealmGroupRoleAssigned) {
      Task(status)
    } else if (poc.deviceGroupId.isEmpty) {
      val errorMsg = s"groupId for poc with id ${poc.id} is missing though poc status says it was already created"
      throwError(status, errorMsg)
    } else {
      findRoleAndAddToGroup(poc, poc.deviceGroupId.get, status, DeviceKeycloak)
        .map {
          case Right(_)       => status.copy(deviceRealmGroupRoleAssigned = true)
          case Left(errorMsg) => throwError(status, errorMsg)
        }
    }
  }

  override def assignUserRealmRoleToGroup(pocAndStatus: PocAndStatus, tenant: Tenant): Task[PocStatus] = {
    val poc = pocAndStatus.poc
    val status = pocAndStatus.status
    if (status.userRealmGroupRoleAssigned) {
      Task(status)
    } else if (poc.userGroupId.isEmpty) {
      val errorMsg = s"groupId for poc with id ${poc.id} is missing, though poc status says it was already created"
      throwError(status, errorMsg)
    } else {
      findRoleAndAddToGroup(poc, poc.userGroupId.get, status, UsersKeycloak)
        .map {
          case Right(_)       => status.copy(userRealmGroupRoleAssigned = true)
          case Left(errorMsg) => throwError(status, errorMsg)
        }
    }
  }

  private def updatePoc(
    poc: Poc,
    failedStatus: PocStatus,
    successStatus: PocStatus,
    groupId: GroupId): Task[PocAndStatus] = {

    pocRepository
      .updatePoc(poc)
      .map(_ => PocAndStatus(poc, successStatus))
      .onErrorHandle { ex =>
        val errorMsg = s"couldn't store newly created device realm groupId ${groupId.value} for poc with id ${poc.id}"
        throwAndLogError(failedStatus, errorMsg, ex)
      }
  }

  private def addSubGroup(
    poc: Poc,
    status: PocStatus,
    tenantId: TenantGroupId,
    keycloak: KeycloakInstance): Task[GroupId] =
    groups
      .addSubGroup(GroupId(tenantId.value), GroupName(poc.roleName), keycloak)
      .map {
        case Left(ex: GroupCreationError) =>
          val errorMsg =
            s"creation of subgroup ${poc.roleName} for group of ${tenantId.value} in realm $keycloak failed; ${ex.errorMsg}"
          throwError(status, errorMsg)
        case Right(groupId) =>
          groupId
      }

  private def throwAndLogError(status: PocStatus, msg: String, ex: Throwable): Nothing = {
    logger.error(msg, ex)
    throwError(status, msg)
  }

  def throwError(status: PocStatus, msg: String) =
    throw PocCreationError(status.copy(errorMessages = Some(msg)))

  private def findRoleAndAddToGroup(
    poc: Poc,
    groupId: String,
    status: PocStatus,
    instance: KeycloakInstance): Task[Either[String, Unit]] = {
    roles
      .findRoleRepresentation(RoleName(poc.roleName), instance)
      .flatMap {
        case Some(role) =>
          groups
            .addRoleToGroup(GroupId(groupId), role, instance)
        case None =>
          throwError(status, s"adding role ${poc.roleName} to ${poc.deviceGroupId} failed as role doesn't exist")
      }
  }

}
