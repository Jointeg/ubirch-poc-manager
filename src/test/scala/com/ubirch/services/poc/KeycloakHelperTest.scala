package com.ubirch.services.poc
import com.ubirch.ModelCreationHelper.{ createPoc, createPocStatus, createTenant }
import com.ubirch.UnitTestBase
import com.ubirch.db.tables.PocRepository
import com.ubirch.models.keycloak.group.{ CreateKeycloakGroup, GroupId, GroupName }
import com.ubirch.models.keycloak.roles.{ CreateKeycloakRole, RoleName }
import com.ubirch.models.poc.{ Poc, PocStatus }
import com.ubirch.models.tenant.{ TenantCertifyGroupId, TenantDeviceGroupId }
import com.ubirch.services.keycloak.groups.TestKeycloakGroupsService
import com.ubirch.services.keycloak.roles.{ KeycloakRolesService, TestKeycloakRolesService }
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak, KeycloakInstance }
import com.ubirch.util.ServiceConstants.TENANT_GROUP_PREFIX

import scala.concurrent.duration.DurationInt

class KeycloakHelperTest extends UnitTestBase {

  private val tenant = createTenant()
  private val poc: Poc = createPoc(tenantName = tenant.tenantName)
  private val pocStatus: PocStatus = createPocStatus(poc.id)

  "KeycloakHelper" should {
    "create poc role in certify realm" in {
      withInjector { injector =>
        val helper: KeycloakHelper = injector.get[KeycloakHelper]
        val roles = injector.get[TestKeycloakRolesService]

        val pocAndStatus = helper.createCertifyRole(PocAndStatus(poc, pocStatus)).runSyncUnsafe()

        pocAndStatus.status.deviceRoleCreated shouldBe false
        pocAndStatus.status.certifyRoleCreated shouldBe true

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

        val pocAndStatus = helper.createDeviceRole(PocAndStatus(poc, pocStatus)).runSyncUnsafe()
        pocAndStatus.status.deviceRoleCreated shouldBe true
        pocAndStatus.status.certifyRoleCreated shouldBe false

        roles.findRoleRepresentation(RoleName(poc.roleName)).map { role =>
          role.isDefined shouldBe true
          role.get.getName shouldBe poc.roleName
          role.get.getId shouldBe poc.deviceGroupId.get
        }
      }
    }

    "set poc certify role created to true, if role already exists" in {
      withInjector { injector =>
        val helper: KeycloakHelper = injector.get[KeycloakHelper]

        helper.createDeviceRole(PocAndStatus(poc, pocStatus)).runSyncUnsafe()
        val pocAndStatus = helper.createCertifyRole(PocAndStatus(poc, pocStatus)).runSyncUnsafe()

        pocAndStatus.status.certifyRoleCreated shouldBe true
      }
    }

    "set poc device role created to true, if role already exists" in {
      withInjector { injector =>
        val helper: KeycloakHelper = injector.get[KeycloakHelper]

        helper.createDeviceRole(PocAndStatus(poc, pocStatus)).runSyncUnsafe()
        val pocAndStatus = helper.createDeviceRole(PocAndStatus(poc, pocStatus)).runSyncUnsafe()
        pocAndStatus.status.deviceRoleCreated shouldBe true
      }
    }

    "create poc group in device realm" in {
      withInjector { injector =>
        val helper: KeycloakHelper = injector.get[KeycloakHelper]
        val groups = injector.get[TestKeycloakGroupsService]
        val pocTable = injector.get[PocRepository]

        val tenantWithRightGroupId =
          tenant.copy(deviceGroupId = TenantDeviceGroupId(createTenantGroup(groups, DeviceKeycloak)))

        val pocAndStatus =
          helper.createDeviceGroup(PocAndStatus(poc, pocStatus), tenantWithRightGroupId).runSyncUnsafe()

        pocAndStatus.status.deviceGroupCreated shouldBe true
        pocAndStatus.status.certifyGroupCreated shouldBe false

        //val updatedPoc = await(pocTable.getPoc(poc.id), 5.seconds)
        //val groupId = updatedPoc.value.deviceGroupId.value

        groups.findGroupById(GroupId(pocAndStatus.poc.deviceGroupId.value), DeviceKeycloak).map {
          case Right(group) =>
            group.getName shouldBe poc.roleName
            group.getId shouldBe poc.deviceGroupId.get
          case Left(_) => assert(false)
        }
      }
    }

    "create poc group in certify realm" in {
      withInjector { injector =>
        val helper: KeycloakHelper = injector.get[KeycloakHelper]
        val groups = injector.get[TestKeycloakGroupsService]
        val pocTable = injector.get[PocRepository]

        val tenantWithRightGroupId =
          tenant.copy(certifyGroupId = TenantCertifyGroupId(createTenantGroup(groups, CertifyKeycloak)))

        val pocAndStatus =
          helper.createCertifyGroup(PocAndStatus(poc, pocStatus), tenantWithRightGroupId).runSyncUnsafe()
        //assert
        pocAndStatus.status.deviceGroupCreated shouldBe false
        pocAndStatus.status.certifyGroupCreated shouldBe true

        //val updatedPoc = await(pocTable.getPoc(poc.id), 5.seconds)
        //val groupId = updatedPoc.value.certifyGroupId.value
        //assert
        groups
          .findGroupById(GroupId(pocAndStatus.poc.certifyGroupId.value))
          .map {
            case Right(group) =>
              group.getName shouldBe poc.roleName
              group.getId shouldBe poc.certifyGroupId.get
            case Left(_) => assert(false)
          }
      }
    }

    "assign certify realm role to poc group" in {
      withInjector { injector =>
        val helper: KeycloakHelper = injector.get[KeycloakHelper]
        val groups = injector.get[TestKeycloakGroupsService]

        val tenantWithRightGroupId =
          tenant.copy(certifyGroupId = TenantCertifyGroupId(createTenantGroup(groups, CertifyKeycloak)))
        helper.createCertifyRole(PocAndStatus(poc, pocStatus)).runSyncUnsafe()
        val pocAndStatus =
          helper.createCertifyGroup(PocAndStatus(poc, pocStatus), tenantWithRightGroupId).runSyncUnsafe()
        val updatedStatus = helper.assignCertifyRoleToGroup(pocAndStatus, tenantWithRightGroupId).runSyncUnsafe()
        //assert
        updatedStatus.status.certifyGroupRoleAssigned shouldBe true
      }
    }

    "assign device realm role to poc group" in {
      withInjector { injector =>
        val helper: KeycloakHelper = injector.get[KeycloakHelper]
        val groups = injector.get[TestKeycloakGroupsService]

        val tenantWithRightGroupId =
          tenant.copy(deviceGroupId = TenantDeviceGroupId(createTenantGroup(groups, DeviceKeycloak)))
        helper.createDeviceRole(PocAndStatus(poc, pocStatus)).runSyncUnsafe()
        val pocAndStatus =
          helper.createDeviceGroup(PocAndStatus(poc, pocStatus), tenantWithRightGroupId).runSyncUnsafe()
        val updatedStatus = helper.assignDeviceRealmRoleToGroup(pocAndStatus, tenantWithRightGroupId).runSyncUnsafe()
        //assert
        updatedStatus.status.deviceGroupRoleAssigned shouldBe true
      }
    }

    "assign tenant realm role to device poc group" in {
      withInjector { injector =>
        val helper: KeycloakHelper = injector.get[KeycloakHelper]
        val groups = injector.get[TestKeycloakGroupsService]
        val keyCloakRoleService = injector.get[KeycloakRolesService]

        keyCloakRoleService.createNewRole(
          CreateKeycloakRole(RoleName(TENANT_GROUP_PREFIX + tenant.tenantName.value)),
          DeviceKeycloak).runSyncUnsafe()

        val tenantWithRightGroupId =
          tenant.copy(deviceGroupId = TenantDeviceGroupId(createTenantGroup(groups, DeviceKeycloak)))
        helper.createDeviceRole(PocAndStatus(poc, pocStatus)).runSyncUnsafe()
        val pocAndStatus =
          helper.createDeviceGroup(PocAndStatus(poc, pocStatus), tenantWithRightGroupId).runSyncUnsafe()
        val updatedStatus = helper.assignTenantRoleToDeviceGroup(pocAndStatus, tenantWithRightGroupId).runSyncUnsafe()
        //assert
        updatedStatus.status.deviceGroupTenantRoleAssigned shouldBe true
      }
    }

    "assign tenant realm role to certify poc group" in {
      withInjector { injector =>
        val helper: KeycloakHelper = injector.get[KeycloakHelper]
        val groups = injector.get[TestKeycloakGroupsService]
        val keyCloakRoleService = injector.get[KeycloakRolesService]

        keyCloakRoleService.createNewRole(
          CreateKeycloakRole(RoleName(TENANT_GROUP_PREFIX + tenant.tenantName.value)),
          CertifyKeycloak).runSyncUnsafe()

        val tenantWithRightGroupId =
          tenant.copy(certifyGroupId = TenantCertifyGroupId(createTenantGroup(groups, CertifyKeycloak)))
        helper.createCertifyRole(PocAndStatus(poc, pocStatus)).runSyncUnsafe()
        val pocAndStatus =
          helper.createCertifyGroup(PocAndStatus(poc, pocStatus), tenantWithRightGroupId).runSyncUnsafe()
        val updatedStatus = helper.assignTenantRoleToCertifyGroup(pocAndStatus, tenantWithRightGroupId).runSyncUnsafe()

        //assert
        updatedStatus.status.certifyGroupTenantRoleAssigned shouldBe true
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
