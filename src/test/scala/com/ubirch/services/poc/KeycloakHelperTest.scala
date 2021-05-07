package com.ubirch.services.poc
import com.ubirch.ModelCreationHelper.{ createPoc, createPocStatus, createTenant }
import com.ubirch.UnitTestBase
import com.ubirch.db.tables.PocRepository
import com.ubirch.models.keycloak.group.{ CreateKeycloakGroup, GroupId, GroupName }
import com.ubirch.models.keycloak.roles.RoleName
import com.ubirch.models.poc.{ Poc, PocStatus }
import com.ubirch.models.tenant.{ Tenant, TenantDeviceGroupId, TenantUserGroupId }
import com.ubirch.services.keycloak.groups.TestKeycloakGroupsService
import com.ubirch.services.keycloak.roles.TestKeycloakRolesService
import com.ubirch.services.{ DeviceKeycloak, KeycloakInstance, UsersKeycloak }
import monix.eval.Task

import scala.concurrent.duration.DurationInt

class KeycloakHelperTest extends UnitTestBase {

  private val tenant = createTenant()
  private val poc: Poc = createPoc(tenantName = tenant.tenantName)
  private val pocStatus: PocStatus = createPocStatus(poc.id)

  "KeycloakHelper" should {
    "create poc role in user realm" in {
      withInjector { injector =>
        val helper: KeycloakHelper = injector.get[KeycloakHelper]
        val roles = injector.get[TestKeycloakRolesService]

        val res = helper.createUserRealmRole(poc, pocStatus)

        val status = await(res, 1.seconds)
        status.deviceRealmRoleCreated shouldBe false
        status.userRealmRoleCreated shouldBe true

        roles.findRoleRepresentation(RoleName(poc.roleName)).map { role =>
          role.isDefined shouldBe true
          role.get.getName shouldBe poc.roleName
          role.get.getId shouldBe poc.deviceGroupId.get
        }
      }
    }

    "create poc role in device realm" in {
      withInjector { injector =>
        val helper: KeycloakHelper = injector.get[KeycloakHelper]
        val roles = injector.get[TestKeycloakRolesService]

        val res = helper.createDeviceRealmRole(poc, pocStatus)
        val status = await(res, 1.seconds)
        status.deviceRealmRoleCreated shouldBe true
        status.userRealmRoleCreated shouldBe false

        roles.findRoleRepresentation(RoleName(poc.roleName)).map { role =>
          role.isDefined shouldBe true
          role.get.getName shouldBe poc.roleName
          role.get.getId shouldBe poc.deviceGroupId.get
        }
      }
    }

    "create poc group in device realm" in {
      withInjector { injector =>
        val helper: KeycloakHelper = injector.get[KeycloakHelper]
        val groups = injector.get[TestKeycloakGroupsService]
        val pocTable = injector.get[PocRepository]

        val tenantWithRightGroupId =
          tenant.copy(deviceGroupId = TenantDeviceGroupId(createTenantGroup(groups, DeviceKeycloak)))

        val res = helper.createDeviceRealmGroup(poc, pocStatus, tenantWithRightGroupId)
        val pocAndStatus = await(res, 1.seconds)

        pocAndStatus.status.deviceRealmGroupCreated shouldBe true
        pocAndStatus.status.userRealmGroupCreated shouldBe false

        val updatedPoc = await(pocTable.getPoc(poc.id), 5.seconds)
        val groupId = updatedPoc.value.deviceGroupId.value

        groups.findGroupById(GroupId(groupId), DeviceKeycloak).map {
          case Right(group) =>
            group.getName shouldBe poc.roleName
            group.getId shouldBe poc.deviceGroupId.get
          case Left(_) => assert(false)
        }
      }
    }

    "create poc group in user realm" in {
      withInjector { injector =>
        val helper: KeycloakHelper = injector.get[KeycloakHelper]
        val groups = injector.get[TestKeycloakGroupsService]
        val pocTable = injector.get[PocRepository]

        val tenantWithRightGroupId =
          tenant.copy(userGroupId = TenantUserGroupId(createTenantGroup(groups, UsersKeycloak)))

        val res = helper.createUserRealmGroup(poc, pocStatus, tenantWithRightGroupId)
        val pocAndStatus = await(res, 1.seconds)
        //assert
        pocAndStatus.status.deviceRealmGroupCreated shouldBe false
        pocAndStatus.status.userRealmGroupCreated shouldBe true

        val updatedPoc = await(pocTable.getPoc(poc.id), 5.seconds)
        val groupId = updatedPoc.value.userGroupId.value
        //assert
        groups
          .findGroupById(GroupId(groupId))
          .map {
            case Right(group) =>
              group.getName shouldBe poc.roleName
              group.getId shouldBe poc.userGroupId.get
            case Left(_) => assert(false)
          }
      }
    }

    "assign user realm role to poc group" in {
      withInjector { injector =>
        val helper: KeycloakHelper = injector.get[KeycloakHelper]
        val groups = injector.get[TestKeycloakGroupsService]
        val pocTable = injector.get[PocRepository]

        val tenantWithRightGroupId =
          tenant.copy(userGroupId = TenantUserGroupId(createTenantGroup(groups, UsersKeycloak)))
        await(helper.createUserRealmRole(poc, pocStatus), 1.seconds)
        val pocAndStatus = await(helper.createUserRealmGroup(poc, pocStatus, tenantWithRightGroupId), 1.seconds)
        val updatedPoc = await(pocTable.getPoc(poc.id), 2.seconds).value
        val res = helper.assignUserRealmRoleToGroup(pocAndStatus, tenantWithRightGroupId)
        val updatedStatus = await(res, 2.seconds)
        //assert
        updatedStatus.userRealmGroupRoleAssigned shouldBe true
      }
    }

    "assign device realm role to poc group" in {
      withInjector { injector =>
        val helper: KeycloakHelper = injector.get[KeycloakHelper]
        val groups = injector.get[TestKeycloakGroupsService]
        val pocTable = injector.get[PocRepository]

        val tenantWithRightGroupId =
          tenant.copy(deviceGroupId = TenantDeviceGroupId(createTenantGroup(groups, DeviceKeycloak)))
        await(helper.createDeviceRealmRole(poc, pocStatus), 1.seconds)
        val pocAndStatus = await(helper.createDeviceRealmGroup(poc, pocStatus, tenantWithRightGroupId), 1.seconds)
//        val updatedPoc = await(pocTable.getPoc(poc.id), 2.seconds).value
        val res: Task[PocStatus] = helper.assignDeviceRealmRoleToGroup(pocAndStatus, tenantWithRightGroupId)
        val updatedStatus = await(res, 2.seconds)
        //assert
        updatedStatus.deviceRealmGroupRoleAssigned shouldBe true
      }
    }
  }

  private def createTenantGroup(groups: TestKeycloakGroupsService, instance: KeycloakInstance): String = {
    val createGroup = CreateKeycloakGroup(GroupName(tenant.tenantName.value))
    val res = groups.createGroup(createGroup, instance)
    val tenantGroup = await(res, 1.seconds)
    tenantGroup.right.get.value

  }
}
