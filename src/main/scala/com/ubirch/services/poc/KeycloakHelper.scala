package com.ubirch.services.poc

import com.google.inject.Inject
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.keycloak.group.{ GroupCreationError, GroupId, GroupName }
import com.ubirch.models.keycloak.roles.{ CreateKeycloakRole, RoleAlreadyExists, RoleCreationException, RoleName }
import com.ubirch.models.poc.{ Poc, PocStatus }
import com.ubirch.models.tenant.Tenant
import com.ubirch.services.keycloak.groups.KeycloakGroupService
import com.ubirch.services.keycloak.roles.KeycloakRolesService
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak, KeycloakInstance }
import monix.eval.Task
import PocCreator.throwError
import com.ubirch.util.ServiceConstants.TENANT_GROUP_PREFIX

import javax.inject.Singleton

trait KeycloakHelper {
  def createDeviceRole(pocAndStatus: PocAndStatus): Task[PocAndStatus]

  def createDeviceGroup(pocAndStatus: PocAndStatus, tenant: Tenant): Task[PocAndStatus]

  def assignDeviceRealmRoleToGroup(pocAndStatus: PocAndStatus, tenant: Tenant): Task[PocAndStatus]

  def assignTenantRoleToDeviceGroup(pocAndStatus: PocAndStatus, tenant: Tenant): Task[PocAndStatus]

  def createCertifyRole(pocAndStatus: PocAndStatus): Task[PocAndStatus]

  def createCertifyGroup(pocAndStatus: PocAndStatus, tenant: Tenant): Task[PocAndStatus]

  def assignTenantRoleToCertifyGroup(pocAndStatus: PocAndStatus, tenant: Tenant): Task[PocAndStatus]

  def assignCertifyRoleToGroup(pocAndStatus: PocAndStatus, tenant: Tenant): Task[PocAndStatus]

}

case class PocAndStatus(poc: Poc, status: PocStatus) {
  def updateStatus(update: PocStatus => PocStatus): PocAndStatus = this.copy(status = update(this.status))
  def updatePoc(update: Poc => Poc): PocAndStatus = this.copy(poc = update(this.poc))
}

@Singleton
class KeycloakHelperImpl @Inject() (
  roles: KeycloakRolesService,
  groups: KeycloakGroupService)
  extends KeycloakHelper
  with LazyLogging {

  @throws[PocCreationError]
  override def createCertifyRole(pocAndStatus: PocAndStatus): Task[PocAndStatus] = {
    if (pocAndStatus.status.certifyRoleCreated) Task(pocAndStatus)
    else {
      roles.createNewRole(CreateKeycloakRole(RoleName(pocAndStatus.poc.roleName)), CertifyKeycloak)
        .map {
          case Right(_) => pocAndStatus.copy(status = pocAndStatus.status.copy(certifyRoleCreated = true))
          case Left(_: RoleAlreadyExists) =>
            pocAndStatus.copy(status = pocAndStatus.status.copy(certifyRoleCreated = true))
          case Left(l: RoleCreationException) =>
            throwError(pocAndStatus, s"certifyRealmRole couldn't be created :${l.roleName}")
        }
    }
  }

  @throws[PocCreationError]
  override def createCertifyGroup(pocAndStatus: PocAndStatus, tenant: Tenant): Task[PocAndStatus] = {
    if (pocAndStatus.status.certifyGroupCreated) Task(pocAndStatus)
    else {
      addSubGroup(pocAndStatus, tenant.certifyGroupId.value, CertifyKeycloak)
        .map { groupId =>
          val updatedPoc = pocAndStatus.poc.copy(certifyGroupId = Some(groupId.value))
          val updatedStatus = pocAndStatus.status.copy(certifyGroupCreated = true)
          pocAndStatus.copy(poc = updatedPoc, status = updatedStatus)
        }
    }
  }

  @throws[PocCreationError]
  override def assignCertifyRoleToGroup(pocAndStatus: PocAndStatus, tenant: Tenant): Task[PocAndStatus] = {
    val poc = pocAndStatus.poc
    val status = pocAndStatus.status

    if (status.certifyGroupRoleAssigned) Task(pocAndStatus)
    else if (poc.certifyGroupId.isEmpty)
      throwError(
        pocAndStatus,
        s"groupId for poc with id ${poc.id} is missing, though poc status says it was already created")
    else {
      findRoleAndAddToGroup(poc, poc.certifyGroupId.get, status, CertifyKeycloak)
        .map {
          case Right(_)       => pocAndStatus.copy(status = pocAndStatus.status.copy(certifyGroupRoleAssigned = true))
          case Left(errorMsg) => throwError(pocAndStatus, errorMsg)
        }
    }
  }

  @throws[PocCreationError]
  override def assignTenantRoleToCertifyGroup(pocAndStatus: PocAndStatus, tenant: Tenant): Task[PocAndStatus] = {
    val poc = pocAndStatus.poc
    val status = pocAndStatus.status

    if (status.certifyGroupTenantRoleAssigned) Task(pocAndStatus)
    else if (poc.certifyGroupId.isEmpty)
      throwError(
        pocAndStatus,
        s"groupId for poc with id ${poc.id} is missing though poc status says it was already created")
    else {
      findTenantRoleAndAddToGroup(poc, tenant, poc.certifyGroupId.get, status, CertifyKeycloak)
        .map {
          case Right(_)       => pocAndStatus.copy(status = pocAndStatus.status.copy(certifyGroupTenantRoleAssigned = true))
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
  override def assignTenantRoleToDeviceGroup(pocAndStatus: PocAndStatus, tenant: Tenant): Task[PocAndStatus] = {
    val poc = pocAndStatus.poc
    val status = pocAndStatus.status

    if (status.deviceGroupTenantRoleAssigned) Task(pocAndStatus)
    else if (poc.deviceGroupId.isEmpty)
      throwError(
        pocAndStatus,
        s"groupId for poc with id ${poc.id} is missing though poc status says it was already created")
    else {
      findTenantRoleAndAddToGroup(poc, tenant, poc.deviceGroupId.get, status, DeviceKeycloak)
        .map {
          case Right(_)       => pocAndStatus.copy(status = pocAndStatus.status.copy(deviceGroupTenantRoleAssigned = true))
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
        case Some(role) => groups.assignRoleToGroup(GroupId(groupId), role, instance)
        case None =>
          throwError(
            PocAndStatus(poc, status),
            s"adding role ${poc.roleName} to ${poc.deviceGroupId} failed as role doesn't exist")
      }
  }

  @throws[PocCreationError]
  private def findTenantRoleAndAddToGroup(
    poc: Poc,
    tenant: Tenant,
    groupId: String,
    status: PocStatus,
    instance: KeycloakInstance): Task[Either[String, Unit]] = {
    val tenantRole = TENANT_GROUP_PREFIX + tenant.tenantName.value
    roles
      .findRoleRepresentation(RoleName(tenantRole), instance)
      .flatMap {
        case Some(role) => groups.assignRoleToGroup(GroupId(groupId), role, instance)
        case None =>
          throwError(
            PocAndStatus(poc, status),
            s"adding role $tenantRole to $groupId failed as role doesn't exist")
      }
  }
}
