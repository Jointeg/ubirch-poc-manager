package com.ubirch.services.poc

import com.ubirch.ModelCreationHelper.{ createPoc, createPocStatus, createTenant }
import com.ubirch.UnitTestBase
import com.ubirch.models.keycloak.group.{ CreateKeycloakGroup, GroupId, GroupName }
import com.ubirch.models.keycloak.roles.{ CreateKeycloakRole, RoleName }
import com.ubirch.models.poc.{ Poc, PocStatus }
import com.ubirch.models.tenant.{ TenantCertifyGroupId, TenantDeviceGroupId, TenantTypeGroupId }
import com.ubirch.services.keycloak.KeycloakRealm
import com.ubirch.services.keycloak.groups.TestKeycloakGroupsService
import com.ubirch.services.keycloak.roles.{ KeycloakRolesService, TestKeycloakRolesService }
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak, KeycloakInstance }
import com.ubirch.util.KeycloakRealmsHelper._
import com.ubirch.util.ServiceConstants.{ POC_ADMIN, POC_EMPLOYEE }

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

        roles.findRoleRepresentation(CertifyKeycloak.defaultRealm, RoleName(poc.roleName), CertifyKeycloak).map {
          role =>
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

        roles.findRoleRepresentation(CertifyKeycloak.defaultRealm, RoleName(poc.roleName), CertifyKeycloak).map {
          role =>
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

        val tenantWithRightGroupId =
          tenant.copy(deviceGroupId =
            TenantDeviceGroupId(createTenantGroup(DeviceKeycloak.defaultRealm, groups, DeviceKeycloak)))

        val pocAndStatus =
          helper.createDeviceGroup(PocAndStatus(poc, pocStatus), tenantWithRightGroupId).runSyncUnsafe()

        pocAndStatus.status.deviceGroupCreated shouldBe true
        pocAndStatus.status.certifyGroupCreated shouldBe false

        groups.findGroupById(
          DeviceKeycloak.defaultRealm,
          GroupId(pocAndStatus.poc.deviceGroupId.value),
          DeviceKeycloak).map {
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

        val tenantWithRightGroupId =
          tenant.copy(certifyGroupId =
            TenantCertifyGroupId(createTenantGroup(CertifyKeycloak.defaultRealm, groups, CertifyKeycloak)))

        val pocAndStatus =
          helper.createCertifyGroup(PocAndStatus(poc, pocStatus), tenantWithRightGroupId).runSyncUnsafe()
        //assert
        pocAndStatus.status.deviceGroupCreated shouldBe false
        pocAndStatus.status.certifyGroupCreated shouldBe true

        //assert
        groups
          .findGroupById(CertifyKeycloak.defaultRealm, GroupId(pocAndStatus.poc.certifyGroupId.value), CertifyKeycloak)
          .map {
            case Right(group) =>
              group.getName shouldBe poc.roleName
              group.getId shouldBe poc.certifyGroupId.get
            case Left(_) => assert(false)
          }
      }
    }

    "create poc admin group and assign role in certify realm" in {
      withInjector { injector =>
        val roles = injector.get[KeycloakRolesService]
        roles.createNewRole(
          CertifyKeycloak.defaultRealm,
          CreateKeycloakRole(RoleName(POC_ADMIN)),
          CertifyKeycloak).runSyncUnsafe(
          3.seconds).isRight shouldBe true

        val helper: KeycloakHelper = injector.get[KeycloakHelper]
        val groups = injector.get[TestKeycloakGroupsService]
        val status1 = pocStatus.copy(adminGroupCreated = Some(false), adminRoleAssigned = Some(false))
        val tenantWithRightGroupId =
          tenant.copy(certifyGroupId =
            TenantCertifyGroupId(createTenantGroup(CertifyKeycloak.defaultRealm, groups, CertifyKeycloak)))
        val r = for {
          pocAndStatus1 <- helper.createCertifyGroup(PocAndStatus(poc, status1), tenantWithRightGroupId)
          pocAndStatus2 <- helper.createAdminGroup(pocAndStatus1)
          pocAndStatus3 <- helper.assignAdminRole(pocAndStatus2)
        } yield pocAndStatus3

        val pocAndStatus = r.runSyncUnsafe()
        //assert
        pocAndStatus.status.adminGroupCreated shouldBe Some(true)
        pocAndStatus.status.certifyGroupCreated shouldBe true
        pocAndStatus.status.adminRoleAssigned shouldBe Some(true)

        //assert
        val groupEither = groups
          .findGroupById(
            CertifyKeycloak.defaultRealm,
            GroupId(pocAndStatus.poc.adminGroupId.value),
            CertifyKeycloak).runSyncUnsafe()
        groupEither match {
          case Right(group) =>
            group.getName shouldBe POC_ADMIN
            group.getId shouldBe pocAndStatus.poc.adminGroupId.get
            group.getRealmRoles.contains(POC_ADMIN) shouldBe true
          case Left(_) => assert(false)
        }
      }
    }

    "create poc employee group and assign role in certify realm" in {
      withInjector { injector =>
        val helper: KeycloakHelper = injector.get[KeycloakHelper]
        val groups = injector.get[TestKeycloakGroupsService]
        val status1 = pocStatus.copy(
          employeeGroupCreated = Some(false),
          employeeRoleAssigned = Some(false),
          pocTypeRoleCreated = Some(false),
          pocTypeGroupCreated = Some(false),
          pocTypeGroupRoleAssigned = Some(false)
        )
        val certifyGroupId =
          TenantCertifyGroupId(createTenantGroup(CertifyKeycloak.defaultRealm, groups, CertifyKeycloak))
        // use different group name as TestKeycloakGroupsService doesn't adjust three realms
        val pocTenantTypeGroupId = TenantTypeGroupId(createTenantGroup(
          tenant.getRealm,
          groups,
          CertifyKeycloak,
          "tenant_type_" + tenant.tenantName.value))
        val tenantWithRightGroupId =
          tenant.copy(
            certifyGroupId =
              certifyGroupId,
            tenantTypeGroupId = Some(pocTenantTypeGroupId))

        val roles = injector.get[KeycloakRolesService]
        roles.createNewRole(
          tenant.getRealm,
          CreateKeycloakRole(RoleName(POC_EMPLOYEE)),
          CertifyKeycloak).runSyncUnsafe(3.seconds)

        val r = for {
          pocAndStatus1 <- helper.createCertifyGroup(PocAndStatus(poc, status1), tenantWithRightGroupId)
          pocAndStatus2 <- helper.createPocTypeRole(pocAndStatus1)
          pocAndStatus3 <- helper.createPocTenantTypeGroup(pocAndStatus2, tenantWithRightGroupId)
          pocAndStatus4 <- helper.assignPocTypeRoleToGroup(pocAndStatus3)
          pocAndStatus5 <- helper.createEmployeeGroup(pocAndStatus4)
          pocAndStatus6 <- helper.assignEmployeeRole(pocAndStatus5)
        } yield pocAndStatus6

        val pocAndStatus = r.runSyncUnsafe()
        //assert
        pocAndStatus.status.employeeGroupCreated shouldBe Some(true)
        pocAndStatus.status.certifyGroupCreated shouldBe true
        pocAndStatus.status.pocTypeRoleCreated shouldBe Some(true)
        pocAndStatus.status.pocTypeGroupCreated shouldBe Some(true)
        pocAndStatus.status.pocTypeGroupRoleAssigned shouldBe Some(true)
        pocAndStatus.status.employeeRoleAssigned shouldBe Some(true)

        //assert
        val groupEither = groups
          .findGroupById(
            tenant.getRealm,
            GroupId(pocAndStatus.poc.employeeGroupId.value),
            CertifyKeycloak).runSyncUnsafe()
        groupEither match {
          case Right(group) =>
            group.getName shouldBe POC_EMPLOYEE
            group.getId shouldBe pocAndStatus.poc.employeeGroupId.get
            group.getRealmRoles.contains(POC_EMPLOYEE) shouldBe true
          case Left(_) => assert(false)
        }
      }
    }

    "assign certify realm role to poc group" in {
      withInjector { injector =>
        val helper: KeycloakHelper = injector.get[KeycloakHelper]
        val groups = injector.get[TestKeycloakGroupsService]

        val tenantWithRightGroupId =
          tenant.copy(certifyGroupId =
            TenantCertifyGroupId(createTenantGroup(CertifyKeycloak.defaultRealm, groups, CertifyKeycloak)))
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
          tenant.copy(deviceGroupId =
            TenantDeviceGroupId(createTenantGroup(DeviceKeycloak.defaultRealm, groups, DeviceKeycloak)))
        helper.createDeviceRole(PocAndStatus(poc, pocStatus)).runSyncUnsafe()
        val pocAndStatus =
          helper.createDeviceGroup(PocAndStatus(poc, pocStatus), tenantWithRightGroupId).runSyncUnsafe()
        val updatedStatus = helper.assignDeviceRealmRoleToGroup(pocAndStatus, tenantWithRightGroupId).runSyncUnsafe()
        //assert
        updatedStatus.status.deviceGroupRoleAssigned shouldBe true
      }
    }
  }

  private def createTenantGroup(
    realm: KeycloakRealm,
    groups: TestKeycloakGroupsService,
    instance: KeycloakInstance,
    groupName: String = tenant.tenantName.value): String = {
    val createGroup = CreateKeycloakGroup(GroupName(groupName))
    val res = groups.createGroup(realm, createGroup, instance)
    val tenantGroup = await(res, 1.seconds)
    tenantGroup.right.get.value

  }
}
