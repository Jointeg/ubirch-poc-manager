package com.ubirch.services.poc

import com.google.inject.Inject
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.keycloak.group.{ GroupCreationError, GroupId, GroupName }
import com.ubirch.models.keycloak.roles.{ CreateKeycloakRole, RoleAlreadyExists, RoleCreationException, RoleName }
import com.ubirch.models.poc.{ Poc, PocStatus }
import com.ubirch.models.tenant.Tenant
import com.ubirch.services.keycloak.KeycloakRealm
import com.ubirch.services.keycloak.groups.KeycloakGroupService
import com.ubirch.services.keycloak.roles.KeycloakRolesService
import com.ubirch.services.poc.PocCreator.throwError
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak, KeycloakInstance }
import com.ubirch.util.ServiceConstants.{ POC_ADMIN, POC_EMPLOYEE }
import monix.eval.Task
import com.ubirch.util.KeycloakRealmsHelper._
import javax.inject.Singleton

trait KeycloakHelper {
  def createDeviceRole(pocAndStatus: PocAndStatus): Task[PocAndStatus]

  def createDeviceGroup(pocAndStatus: PocAndStatus, tenant: Tenant): Task[PocAndStatus]

  def assignDeviceRealmRoleToGroup(pocAndStatus: PocAndStatus, tenant: Tenant): Task[PocAndStatus]

  def createCertifyRole(pocAndStatus: PocAndStatus): Task[PocAndStatus]

  def createCertifyGroup(pocAndStatus: PocAndStatus, tenant: Tenant): Task[PocAndStatus]

  def createAdminGroup(pocAndStatus: PocAndStatus): Task[PocAndStatus]

  def assignAdminRole(pocAndStatus: PocAndStatus): Task[PocAndStatus]

  def createPocTypeRole(pocAndStatus: PocAndStatus): Task[PocAndStatus]

  def createPocTenantTypeGroup(pocAndStatus: PocAndStatus, tenant: Tenant): Task[PocAndStatus]

  def assignPocTypeRoleToGroup(pocAndStatus: PocAndStatus): Task[PocAndStatus]

  def createEmployeeGroup(pocAndStatus: PocAndStatus): Task[PocAndStatus]

  def assignEmployeeRole(pocAndStatus: PocAndStatus): Task[PocAndStatus]

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
      roles.createNewRole(
        CertifyKeycloak.defaultRealm,
        CreateKeycloakRole(RoleName(pocAndStatus.poc.roleName)),
        CertifyKeycloak)
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
      addSubGroup(
        tenant.certifyGroupId.value,
        pocAndStatus.poc.roleName,
        pocAndStatus,
        CertifyKeycloak.defaultRealm,
        CertifyKeycloak)
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

    if (pocAndStatus.status.certifyGroupRoleAssigned) Task(pocAndStatus)
    else {
      val certifyGroupId = poc.certifyGroupId.getOrElse(throwError(
        pocAndStatus,
        s"groupId for poc with id ${poc.id} is missing, though poc status says it was already created"))

      findRoleAndAddToGroup(
        poc,
        poc.roleName,
        certifyGroupId,
        pocAndStatus.status,
        CertifyKeycloak.defaultRealm,
        CertifyKeycloak)
        .map {
          case Right(_)       => pocAndStatus.copy(status = pocAndStatus.status.copy(certifyGroupRoleAssigned = true))
          case Left(errorMsg) => throwError(pocAndStatus, errorMsg)
        }
    }
  }

  @throws[PocCreationError]
  override def createAdminGroup(pocAndStatus: PocAndStatus): Task[PocAndStatus] = {
    if (pocAndStatus.status.adminGroupCreated.isEmpty || pocAndStatus.status.adminGroupCreated.contains(true))
      Task(pocAndStatus)
    else {
      addSubGroup(
        getCertifyGroupId(pocAndStatus),
        POC_ADMIN,
        pocAndStatus,
        CertifyKeycloak.defaultRealm,
        CertifyKeycloak)
        .map { groupId =>
          val updatedPoc = pocAndStatus.poc.copy(adminGroupId = Some(groupId.value))
          val updatedStatus = pocAndStatus.status.copy(adminGroupCreated = Some(true))
          pocAndStatus.copy(poc = updatedPoc, status = updatedStatus)
        }
    }
  }

  @throws[PocCreationError]
  override def assignAdminRole(pocAndStatus: PocAndStatus): Task[PocAndStatus] = {
    val poc = pocAndStatus.poc
    if (pocAndStatus.status.adminRoleAssigned.isEmpty || pocAndStatus.status.adminRoleAssigned.contains(true))
      Task(pocAndStatus)
    else {
      val adminGroupId =
        poc.adminGroupId.getOrElse(throwError(pocAndStatus, s"adminGroupId for poc ${poc.id} is missing"))
      findRoleAndAddToGroup(
        poc,
        POC_ADMIN,
        adminGroupId,
        pocAndStatus.status,
        CertifyKeycloak.defaultRealm,
        CertifyKeycloak)
        .map {
          case Right(_)       => pocAndStatus.copy(status = pocAndStatus.status.copy(adminRoleAssigned = Some(true)))
          case Left(errorMsg) => throwError(pocAndStatus, errorMsg)
        }
    }
  }

  @throws[PocCreationError]
  override def createPocTypeRole(pocAndStatus: PocAndStatus): Task[PocAndStatus] = {
    if (pocAndStatus.status.pocTypeRoleCreated.isEmpty || pocAndStatus.status.pocTypeRoleCreated.contains(true))
      Task(pocAndStatus)
    else {
      roles.createNewRole(
        pocAndStatus.poc.getRealm,
        CreateKeycloakRole(RoleName(pocAndStatus.poc.roleName)),
        CertifyKeycloak)
        .map {
          case Right(_) => pocAndStatus.copy(status = pocAndStatus.status.copy(pocTypeRoleCreated = Some(true)))
          case Left(_: RoleAlreadyExists) =>
            pocAndStatus.copy(status = pocAndStatus.status.copy(pocTypeRoleCreated = Some(true)))
          case Left(l: RoleCreationException) =>
            throwError(pocAndStatus, s"pocTypeRole couldn't be created :${l.roleName}")
        }
    }
  }

  @throws[PocCreationError]
  override def createPocTenantTypeGroup(pocAndStatus: PocAndStatus, tenant: Tenant): Task[PocAndStatus] = {
    if (pocAndStatus.status.pocTypeGroupCreated.isEmpty || pocAndStatus.status.pocTypeGroupCreated.contains(
        true))
      Task(pocAndStatus)
    else {
      val tenantTypeGroupId =
        tenant.tenantTypeGroupId.getOrElse(throwError(pocAndStatus, "tenant is missing tenantTypeGroupId"))
      addSubGroup(
        tenantTypeGroupId.value,
        pocAndStatus.poc.roleName,
        pocAndStatus,
        tenant.getRealm,
        CertifyKeycloak)
        .map { groupId =>
          val updatedPoc = pocAndStatus.poc.copy(pocTypeGroupId = Some(groupId.value))
          val updatedStatus = pocAndStatus.status.copy(pocTypeGroupCreated = Some(true))
          pocAndStatus.copy(poc = updatedPoc, status = updatedStatus)
        }
    }
  }

  @throws[PocCreationError]
  override def assignPocTypeRoleToGroup(pocAndStatus: PocAndStatus): Task[PocAndStatus] = {
    val poc = pocAndStatus.poc

    if (pocAndStatus.status.pocTypeGroupRoleAssigned.isEmpty || pocAndStatus.status.pocTypeGroupRoleAssigned.contains(
        true)) Task(pocAndStatus)
    else {
      val pocTypeGroupId = poc.pocTypeGroupId.getOrElse(throwError(
        pocAndStatus,
        s"pocTypeGroupId for poc with id ${poc.id} is missing, though poc status says it was already created"))

      findRoleAndAddToGroup(
        poc,
        poc.roleName,
        pocTypeGroupId,
        pocAndStatus.status,
        poc.getRealm,
        CertifyKeycloak)
        .map {
          case Right(_)       => pocAndStatus.copy(status = pocAndStatus.status.copy(pocTypeGroupRoleAssigned = Some(true)))
          case Left(errorMsg) => throwError(pocAndStatus, errorMsg)
        }
    }
  }

  @throws[PocCreationError]
  override def createEmployeeGroup(pocAndStatus: PocAndStatus): Task[PocAndStatus] = {
    if (pocAndStatus.status.employeeGroupCreated.isEmpty || pocAndStatus.status.employeeGroupCreated.contains(true))
      Task(pocAndStatus)
    else {
      val pocTenantTypeGroupId =
        pocAndStatus.poc.pocTypeGroupId.getOrElse(throwError(pocAndStatus, "poc is missing pocTenantTypeGroupId"))
      addSubGroup(pocTenantTypeGroupId, POC_EMPLOYEE, pocAndStatus, pocAndStatus.poc.getRealm, CertifyKeycloak)
        .map { groupId =>
          val updatedPoc = pocAndStatus.poc.copy(employeeGroupId = Some(groupId.value))
          val updatedStatus = pocAndStatus.status.copy(employeeGroupCreated = Some(true))
          pocAndStatus.copy(poc = updatedPoc, status = updatedStatus)
        }
    }
  }

  @throws[PocCreationError]
  override def assignEmployeeRole(pocAndStatus: PocAndStatus): Task[PocAndStatus] = {
    val poc = pocAndStatus.poc
    if (pocAndStatus.status.employeeRoleAssigned.isEmpty || pocAndStatus.status.employeeRoleAssigned.contains(true))
      Task(pocAndStatus)
    else {
      val employeeGroupId =
        poc.employeeGroupId.getOrElse(throwError(pocAndStatus, s"employeeGroupId for poc ${poc.id} is missing"))

      findRoleAndAddToGroup(poc, POC_EMPLOYEE, employeeGroupId, pocAndStatus.status, poc.getRealm, CertifyKeycloak)
        .map {
          case Right(_)       => pocAndStatus.copy(status = pocAndStatus.status.copy(employeeRoleAssigned = Some(true)))
          case Left(errorMsg) => throwError(pocAndStatus, errorMsg)
        }
    }
  }

  @throws[PocCreationError]
  private def getCertifyGroupId(pocAndStatus: PocAndStatus): String = {
    pocAndStatus.poc.certifyGroupId.getOrElse(throwError(pocAndStatus, "poc is missing certifyGroupId"))
  }

  @throws[PocCreationError]
  override def createDeviceRole(pocAndStatus: PocAndStatus): Task[PocAndStatus] = {
    if (pocAndStatus.status.deviceRoleCreated) Task(pocAndStatus)
    else {
      roles.createNewRole(
        DeviceKeycloak.defaultRealm,
        CreateKeycloakRole(RoleName(pocAndStatus.poc.roleName)),
        DeviceKeycloak)
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
      addSubGroup(
        tenant.deviceGroupId.value,
        pocAndStatus.poc.roleName,
        pocAndStatus,
        DeviceKeycloak.defaultRealm,
        DeviceKeycloak)
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
      findRoleAndAddToGroup(
        poc,
        poc.roleName,
        poc.deviceGroupId.get,
        status,
        DeviceKeycloak.defaultRealm,
        DeviceKeycloak)
        .map {
          case Right(_)       => pocAndStatus.copy(status = pocAndStatus.status.copy(deviceGroupRoleAssigned = true))
          case Left(errorMsg) => throwError(pocAndStatus, errorMsg)
        }
    }
  }

  @throws[PocCreationError]
  private def addSubGroup(
    parentGroupId: String,
    subGroupName: String,
    pocAndStatus: PocAndStatus,
    realm: KeycloakRealm,
    keycloak: KeycloakInstance): Task[GroupId] =
    groups
      .addSubGroup(realm, GroupId(parentGroupId), GroupName(subGroupName), keycloak)
      .map {
        case Right(groupId)               => groupId
        case Left(ex: GroupCreationError) => throwError(pocAndStatus, ex.errorMsg)
      }

  @throws[PocCreationError]
  private def findRoleAndAddToGroup(
    poc: Poc,
    roleName: String,
    groupId: String,
    status: PocStatus,
    realm: KeycloakRealm,
    instance: KeycloakInstance): Task[Either[String, Unit]] = {
    roles
      .findRoleRepresentation(realm, RoleName(roleName), instance)
      .flatMap {
        case Some(role) => groups.assignRoleToGroup(realm, GroupId(groupId), role, instance)
        case None =>
          throwError(
            PocAndStatus(poc, status),
            s"adding role $roleName to $groupId failed as role doesn't exist")
      }
  }

}
