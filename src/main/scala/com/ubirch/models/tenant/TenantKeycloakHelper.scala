package com.ubirch.models.tenant

import com.google.inject.Inject
import com.ubirch.models.keycloak.group
import com.ubirch.models.keycloak.group.{ CreateKeycloakGroup, GroupCreationError, GroupName }
import com.ubirch.models.keycloak.roles.{ CreateKeycloakRole, RoleCreationException, RoleName }
import com.ubirch.services.keycloak.KeycloakRealm
import com.ubirch.services.keycloak.groups.KeycloakGroupService
import com.ubirch.services.keycloak.roles.KeycloakRolesService
import com.ubirch.services.superadmin.TenantCreationException
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak, KeycloakInstance }
import com.ubirch.util.ServiceConstants.TENANT_GROUP_PREFIX
import monix.eval.Task

trait TenantKeycloakHelper {

  def doKeycloakRelatedTasks(tenantRequest: CreateTenantRequest): Task[DeviceAndCertifyGroups]
}

class TenantKeycloakHelperImpl @Inject() (roles: KeycloakRolesService, groups: KeycloakGroupService)
  extends TenantKeycloakHelper {

  override def doKeycloakRelatedTasks(tenantRequest: CreateTenantRequest): Task[DeviceAndCertifyGroups] = {
    val keycloakName = TENANT_GROUP_PREFIX + tenantRequest.tenantName.value
    for {
      _ <- createRoles(keycloakName, tenantRequest)
      deviceAndTenantGroupId <- createGroups(keycloakName, tenantRequest)
      _ <- assignRolesToGroups(deviceAndTenantGroupId, keycloakName)
    } yield deviceAndTenantGroupId
  }

  private def createRoles(tenantRoleName: String, tenantRequest: CreateTenantRequest): Task[Unit] = {
    val tenantRole = CreateKeycloakRole(RoleName(tenantRoleName))

    def createRole(realm: KeycloakRealm, instance: KeycloakInstance): Task[Unit] =
      roles.createNewRole(realm, tenantRole, instance).map {
        case Left(_: RoleCreationException) =>
          throw TenantCreationException(s"failed to create role in ${instance.name} realm with name $tenantRoleName ")
        case _ => ()
      }

    createRole(CertifyKeycloak.defaultRealm, CertifyKeycloak)
      .flatMap(_ => createRole(DeviceKeycloak.defaultRealm, DeviceKeycloak))
      .flatMap { _ =>
        if (tenantRequest.usageType == API) Task(())
        else createRole(TenantType.getRealm(tenantRequest.tenantType), CertifyKeycloak)
      }
  }

  private def createGroups(
    tenantGroupName: String,
    tenantRequest: CreateTenantRequest): Task[DeviceAndCertifyGroups] = {
    val tenantGroup = CreateKeycloakGroup(GroupName(tenantGroupName))

    def createGroup(realm: KeycloakRealm, instance: KeycloakInstance): Task[group.GroupId] =
      groups.createGroup(realm, tenantGroup, instance).map {
        case Right(groupId) => groupId
        case Left(error: GroupCreationError) =>
          throw TenantCreationException(s"failed to create group for tenant $tenantGroupName ${error.errorMsg}")
      }
    for {
      certifyGroup <- createGroup(CertifyKeycloak.defaultRealm, CertifyKeycloak)
      deviceGroup <- createGroup(DeviceKeycloak.defaultRealm, DeviceKeycloak)
      employeeGroup <-
        if (tenantRequest.usageType == API) Task(None)
        else createGroup(TenantType.getRealm(tenantRequest.tenantType), CertifyKeycloak).map(Some(_))
    } yield DeviceAndCertifyGroups(deviceGroup, certifyGroup, employeeGroup)
  }

  private def assignRolesToGroups(deviceAndCertifyGroup: DeviceAndCertifyGroups, keycloakName: String): Task[Unit] = {

    def assignRoleToGroup(realm: KeycloakRealm, groupId: group.GroupId, instance: KeycloakInstance): Task[Unit] = {
      roles.findRoleRepresentation(instance.defaultRealm, RoleName(keycloakName), instance).flatMap {
        case Some(role) =>
          groups.assignRoleToGroup(realm, groupId, role, instance).map {
            case Right(_) =>
            case Left(errorMsg) =>
              TenantCreationException(
                s"failed to assign tenantRole to group with id $groupId; $errorMsg")
          }
        case None =>
          Task.raiseError(TenantCreationException(s"failed to find tenantRole with name $keycloakName"))
      }
    }

    assignRoleToGroup(CertifyKeycloak.defaultRealm, deviceAndCertifyGroup.certifyGroup, CertifyKeycloak)
      .flatMap(_ => assignRoleToGroup(DeviceKeycloak.defaultRealm, deviceAndCertifyGroup.deviceGroup, DeviceKeycloak))

  }

}

case class DeviceAndCertifyGroups(
  deviceGroup: group.GroupId,
  certifyGroup: group.GroupId,
  employeeGroup: Option[group.GroupId])
