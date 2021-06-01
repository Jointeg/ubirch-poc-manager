package com.ubirch.services.poc

import com.ubirch.ModelCreationHelper.{ createPoc, createPocStatus, createTenant }
import com.ubirch.e2e.E2ETestBase
import com.ubirch.models.keycloak.group.{ CreateKeycloakGroup, GroupId, GroupName }
import com.ubirch.models.keycloak.roles.{ CreateKeycloakRole, RoleName }
import com.ubirch.models.poc.{ Poc, PocStatus }
import com.ubirch.models.tenant.TenantCertifyGroupId
import com.ubirch.services.CertifyKeycloak
import com.ubirch.services.keycloak.groups.DefaultKeycloakGroupService
import com.ubirch.services.keycloak.roles.DefaultKeycloakRolesService
import com.ubirch.util.ServiceConstants.{ POC_ADMIN, POC_EMPLOYEE, TENANT_GROUP_PREFIX }

import scala.concurrent.duration.DurationInt

class KeycloakHelperIntegrationTest extends E2ETestBase {

  private val tenant = createTenant()
  private val poc: Poc = createPoc(tenantName = tenant.tenantName)
  private val pocStatus: PocStatus = createPocStatus(poc.id, Some(false), Some(false), Some(false), Some(false))

  "KeycloakHelper" should {
    "do certify realm related tasks" in {
      withInjector { injector =>
        val groups = injector.get[DefaultKeycloakGroupService]
        val roles = injector.get[DefaultKeycloakRolesService]
        val tenantRole = TENANT_GROUP_PREFIX + tenant.tenantName.value
        val realm = CertifyKeycloak.defaultRealm

        roles.createNewRole(realm, CreateKeycloakRole(RoleName(tenantRole)), CertifyKeycloak).runSyncUnsafe()
        roles.createNewRole(realm, CreateKeycloakRole(RoleName(POC_ADMIN)), CertifyKeycloak).runSyncUnsafe()
        roles.createNewRole(realm, CreateKeycloakRole(RoleName(POC_EMPLOYEE)), CertifyKeycloak).runSyncUnsafe()
        val groupId =
          groups.createGroup(realm, CreateKeycloakGroup(GroupName(tenantRole)), CertifyKeycloak).runSyncUnsafe()
        val role = roles.findRoleRepresentation(realm, RoleName(tenantRole), CertifyKeycloak).runSyncUnsafe()
        groups.assignRoleToGroup(realm, groupId.right.get, role.get, CertifyKeycloak).runSyncUnsafe()
        val updatedTenant = tenant.copy(certifyGroupId = TenantCertifyGroupId(groupId.right.get.value))
        val helper: KeycloakHelper = injector.get[KeycloakHelper]
        import helper._

        val r = for {
          pocAndStatus1 <- createCertifyRole(PocAndStatus(poc, pocStatus))
          pocAndStatus2 <- createCertifyGroup(pocAndStatus1, updatedTenant)
          pocAndStatus3 <- assignCertifyRoleToGroup(pocAndStatus2, updatedTenant)
          pocAndStatus4 <- createAdminGroup(pocAndStatus3)
          pocAndStatus5 <- assignAdminRole(pocAndStatus4)
          pocAndStatus6 <- createEmployeeGroup(pocAndStatus5)
          pocAndStatusFinal <- assignEmployeeRole(pocAndStatus6)
        } yield pocAndStatusFinal
        val pocAndStatus = r.runSyncUnsafe()
        val certifyGroup =
          groups.findGroupById(realm, GroupId(pocAndStatus.poc.certifyGroupId.get), CertifyKeycloak).runSyncUnsafe(
            2.seconds)
        val adminGroup =
          groups.findGroupById(realm, GroupId(pocAndStatus.poc.adminGroupId.get), CertifyKeycloak).runSyncUnsafe(
            2.seconds)
        val employeeGroup =
          groups.findGroupById(realm, GroupId(pocAndStatus.poc.employeeGroupId.get), CertifyKeycloak).runSyncUnsafe(
            2.seconds)
        certifyGroup.isRight shouldBe true
        adminGroup.isRight shouldBe true
        employeeGroup.isRight shouldBe true
        adminGroup match {
          case Right(group) =>
            group.getName shouldBe POC_ADMIN
            group.getId shouldBe pocAndStatus.poc.adminGroupId.get
            group.getRealmRoles.contains(POC_ADMIN) shouldBe true
          case Left(_) => assert(false)
        }
        employeeGroup match {
          case Right(group) =>
            group.getName shouldBe POC_EMPLOYEE
            group.getId shouldBe pocAndStatus.poc.employeeGroupId.get
            group.getRealmRoles.contains(POC_EMPLOYEE) shouldBe true
          case Left(_) => assert(false)
        }
      }
    }
  }
}
