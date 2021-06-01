package com.ubirch.services.poc
import com.ubirch.ModelCreationHelper.{ createPoc, createPocStatus, createTenant }
import com.ubirch.UnitTestBase
import com.ubirch.models.keycloak.group.{ CreateKeycloakGroup, GroupId, GroupName }
import com.ubirch.models.keycloak.roles.{ CreateKeycloakRole, RoleName, RolesException }
import com.ubirch.models.poc.{ Poc, PocStatus }
import com.ubirch.models.tenant.{ TenantCertifyGroupId, TenantDeviceGroupId }
import com.ubirch.services.keycloak.groups.TestKeycloakGroupsService
import com.ubirch.services.keycloak.roles.{ KeycloakRolesService, TestKeycloakRolesService }
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak, KeycloakInstance }
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

        roles.findRoleRepresentation(RoleName(poc.roleName), CertifyKeycloak).map { role =>
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

        roles.findRoleRepresentation(RoleName(poc.roleName), CertifyKeycloak).map { role =>
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
          tenant.copy(deviceGroupId = TenantDeviceGroupId(createTenantGroup(groups, DeviceKeycloak)))

        val pocAndStatus =
          helper.createDeviceGroup(PocAndStatus(poc, pocStatus), tenantWithRightGroupId).runSyncUnsafe()

        pocAndStatus.status.deviceGroupCreated shouldBe true
        pocAndStatus.status.certifyGroupCreated shouldBe false

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

        val tenantWithRightGroupId =
          tenant.copy(certifyGroupId = TenantCertifyGroupId(createTenantGroup(groups, CertifyKeycloak)))

        val pocAndStatus =
          helper.createCertifyGroup(PocAndStatus(poc, pocStatus), tenantWithRightGroupId).runSyncUnsafe()
        //assert
        pocAndStatus.status.deviceGroupCreated shouldBe false
        pocAndStatus.status.certifyGroupCreated shouldBe true

        //assert
        groups
          .findGroupById(GroupId(pocAndStatus.poc.certifyGroupId.value), CertifyKeycloak)
          .map {
            case Right(group) =>
              group.getName shouldBe poc.roleName
              group.getId shouldBe poc.certifyGroupId.get
            case Left(_) => assert( false)
          }
      }
    }

    "create poc admin group and assign role in certify realm" in {
      withInjector { injector =>
        val roles = injector.get[KeycloakRolesService]
        roles.createNewRole(CreateKeycloakRole(RoleName(POC_ADMIN)), CertifyKeycloak).runSyncUnsafe(
          3.seconds).isRight shouldBe true

        val helper: KeycloakHelper = injector.get[KeycloakHelper]
        val groups = injector.get[TestKeycloakGroupsService]
        val status1 = pocStatus.copy(adminGroupCreated = Some(false), adminRoleAssigned = Some(false))
        val tenantWithRightGroupId =
          tenant.copy(certifyGroupId = TenantCertifyGroupId(createTenantGroup(groups, CertifyKeycloak)))
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
          .findGroupById(GroupId(pocAndStatus.poc.adminGroupId.value), CertifyKeycloak).runSyncUnsafe()
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
        val roles = injector.get[KeycloakRolesService]
        roles.createNewRole(CreateKeycloakRole(RoleName(POC_EMPLOYEE)), CertifyKeycloak).runSyncUnsafe(3.seconds)

        val helper: KeycloakHelper = injector.get[KeycloakHelper]
        val groups = injector.get[TestKeycloakGroupsService]
        val status1 = pocStatus.copy(employeeGroupCreated = Some(false), employeeRoleAssigned = Some(false))
        val tenantWithRightGroupId =
          tenant.copy(certifyGroupId = TenantCertifyGroupId(createTenantGroup(groups, CertifyKeycloak)))
        val r = for {
          pocAndStatus1 <- helper.createCertifyGroup(PocAndStatus(poc, status1), tenantWithRightGroupId)
          pocAndStatus2 <- helper.createEmployeeGroup(pocAndStatus1)
          pocAndStatus3 <- helper.assignEmployeeRole(pocAndStatus2)
        } yield pocAndStatus3

        val pocAndStatus = r.runSyncUnsafe()
        //assert
        pocAndStatus.status.employeeGroupCreated shouldBe Some(true)
        pocAndStatus.status.certifyGroupCreated shouldBe true
        pocAndStatus.status.employeeRoleAssigned shouldBe Some(true)

        //assert
        val groupEither = groups
          .findGroupById(GroupId(pocAndStatus.poc.employeeGroupId.value), CertifyKeycloak).runSyncUnsafe()
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
  }

  private def createTenantGroup(groups: TestKeycloakGroupsService, instance: KeycloakInstance): String = {
    val createGroup = CreateKeycloakGroup(GroupName(tenant.tenantName.value))
    val res = groups.createGroup(createGroup, instance)
    val tenantGroup = await(res, 1.seconds)
    tenantGroup.right.get.value

  }
}
