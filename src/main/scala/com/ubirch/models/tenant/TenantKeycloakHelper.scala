package com.ubirch.models.tenant

import com.google.inject.Inject
import com.ubirch.models.keycloak.group
import com.ubirch.models.keycloak.group.{ CreateKeycloakGroup, GroupCreationError, GroupName }
import com.ubirch.models.keycloak.roles.{ CreateKeycloakRole, RoleCreationException, RoleName }
import com.ubirch.services.keycloak.groups.KeycloakGroupService
import com.ubirch.services.keycloak.roles.KeycloakRolesService
import com.ubirch.services.superadmin.TenantCreationException
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak, KeycloakInstance }
import com.ubirch.util.ServiceConstants.TENANT_GROUP_PREFIX
import monix.eval.Task

trait TenantKeycloakHelper {

  def doKeycloakRelatedTasks(tenantName: TenantName): Task[DeviceAndCertifyGroups]
}

class TenantKeycloakHelperImpl @Inject() (roles: KeycloakRolesService, groups: KeycloakGroupService)
  extends TenantKeycloakHelper {

  override def doKeycloakRelatedTasks(tenantName: TenantName): Task[DeviceAndCertifyGroups] = {
    val keycloakName = TENANT_GROUP_PREFIX + tenantName.value
    for {
      _ <- createRoles(keycloakName)
      deviceAndTenantGroupId <- createGroups(keycloakName)
      _ <- assignRolesToGroups(deviceAndTenantGroupId, keycloakName)
    } yield deviceAndTenantGroupId
  }

  private def createRoles(tenantRoleName: String): Task[Unit] = {
    val tenantRole = CreateKeycloakRole(RoleName(tenantRoleName))

    def createRole(instance: KeycloakInstance) =
      roles.createNewRole(tenantRole, instance).map {
        case Left(_: RoleCreationException) =>
          throw TenantCreationException(s"failed to creat role in ${instance.name} realm with name $tenantRoleName ")
        case _ =>
      }

    createRole(CertifyKeycloak)
      .flatMap(_ => createRole(DeviceKeycloak))

  }

  private def createGroups(tenantGroupName: String): Task[DeviceAndCertifyGroups] = {
    val tenantGroup = CreateKeycloakGroup(GroupName(tenantGroupName))

    def createGroup(instance: KeycloakInstance): Task[group.GroupId] =
      groups.createGroup(tenantGroup, instance).map {
        case Right(groupId) => groupId
        case Left(error: GroupCreationError) =>
          throw TenantCreationException(s"failed to create group for tenant $tenantGroupName ${error.errorMsg}")
      }

    createGroup(CertifyKeycloak)
      .flatMap(certifyGroup =>
        createGroup(DeviceKeycloak)
          .map(deviceGroup => DeviceAndCertifyGroups(deviceGroup, certifyGroup)))
  }

  private def assignRolesToGroups(deviceAndCertifyGroup: DeviceAndCertifyGroups, keycloakName: String): Task[Unit] = {

    def assignRoleToGroup(groupId: group.GroupId, instance: KeycloakInstance): Task[Unit] = {
      roles.findRoleRepresentation(RoleName(keycloakName), instance).flatMap {
        case Some(role) =>
          groups.assignRoleToGroup(groupId, role, instance).map {
            case Right(_) =>
            case Left(errorMsg) =>
              TenantCreationException(
                s"failed to assign tenantRole to group with id $groupId; $errorMsg")
          }
        case None =>
          Task.raiseError(TenantCreationException(s"failed to find tenantRole with name $keycloakName"))
      }
    }

    assignRoleToGroup(deviceAndCertifyGroup.certifyGroup, CertifyKeycloak)
      .flatMap(_ => assignRoleToGroup(deviceAndCertifyGroup.deviceGroup, DeviceKeycloak))

  }

}

case class DeviceAndCertifyGroups(deviceGroup: group.GroupId, certifyGroup: group.GroupId)
