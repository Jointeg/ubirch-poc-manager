package com.ubirch.e2e.flows

import com.ubirch.ModelCreationHelper.pocTypeValue
import cats.implicits.toTraverseOps
import com.ubirch.{ FakeX509Certs, InjectorHelper, PocConfig }
import com.ubirch.controllers.{ PocAdminController, SuperAdminController, TenantAdminController }
import com.ubirch.db.tables._
import com.ubirch.e2e.{ DiscoveryServiceType, E2ETestBase, KeycloakOperations, RealDiscoverService, TenantAdmin }
import com.ubirch.formats.TestFormats
import com.ubirch.models.keycloak.group.GroupName
import com.ubirch.models.keycloak.roles.RoleName
import com.ubirch.models.poc.{ Completed, Pending, Poc, PocAdmin }
import com.ubirch.models.pocEmployee.PocEmployee
import com.ubirch.models.tenant.{ Tenant, TenantName, TenantType }
import com.ubirch.models.user.UserName
import com.ubirch.services.formats.CustomFormats
import com.ubirch.services.keycloak.groups.KeycloakGroupService
import com.ubirch.services.keycloak.roles.KeycloakRolesService
import com.ubirch.services.keycloak.users.KeycloakUserService
import com.ubirch.services.keycloak.{ CertifyKeycloakConnector, CertifyUbirchRealm, KeycloakCertifyConfig }
import com.ubirch.services.poc.PocTestHelper.createNeededDeviceUser
import com.ubirch.services.poc.util.CsvConstants.{ pocAdminHeaderLine, pocEmployeeHeaderLine }
import com.ubirch.services.poc.{ PocAdminCreationLoop, PocCreationLoop, PocEmployeeCreationLoop }
import com.ubirch.services.teamdrive.model.SpaceName
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak }
import com.ubirch.test.FakeTeamDriveClient
import io.prometheus.client.CollectorRegistry
import monix.reactive.Observable
import org.json4s.ext.{ JavaTypesSerializers, JodaTimeSerializers }
import org.json4s.{ DefaultFormats, Formats }
import org.scalatest.{ Assertion, BeforeAndAfterEach }

import java.util.UUID
import scala.concurrent.duration.DurationInt

class PocEmployeeCreationFlowTest extends E2ETestBase with BeforeAndAfterEach with KeycloakOperations {

  override protected val useMockKeyDiscoveryService: DiscoveryServiceType = RealDiscoverService

  implicit private val formats: Formats =
    DefaultFormats.lossless ++ new CustomFormats().formats ++ JavaTypesSerializers.all ++ JodaTimeSerializers.all ++ TestFormats.all

  val poc1Id: UUID = UUID.randomUUID()

  private def createTenantJson(tenantName: String): String = {
    s"""
       |{
       |    "tenantName": "$tenantName",
       |    "usageType": "APP",
       |    "tenantType": "${TenantType.UBIRCH_STRING}"
       |    "sharedAuthCertRequired": true
       |}
       |""".stripMargin
  }

  private val createPocWithPocAdminCSV =
    s"""$pocAdminHeaderLine
       |${poc1Id.toString};$pocTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;+591 74339296;https://www.scala-lang.org/resources/img/frontpage/scala-spiral.png;Musterfrau;Frau;frau.musterfrau@mail.de;+591 74339296;{"vaccines":["vaccine1: vaccine2"]};Mustermann;Herr;herr.mustermann@mail.de;+591 74339296;01.01.1971;FALSE""".stripMargin

  private val addDeviceCreationToken: String =
    s"""
       |{
       |    "token" : "1234567890"
       |}
       |""".stripMargin

  private val pocEmployeeCsv =
    s"""$pocEmployeeHeaderLine
       |firstName1;lastName1;valid1@email.com
       |firstName2;lastName2;valid2@email.com
       |""".stripMargin

  "It should be possible to create Tenant, PoC, Poc Admin and in the end PoC employees" in {
    withInjector { injector =>
      performKeycloakCleanup(injector)
      val certifyKeycloakConnector = injector.get[CertifyKeycloakConnector]
      val certifyConfig = injector.get[KeycloakCertifyConfig]
      val users = injector.get[KeycloakUserService]
      val pocConfig = injector.get[PocConfig]
      val teamDriveClient = injector.get[FakeTeamDriveClient]
      // Create static spaces
      pocConfig.pocTypeStaticSpaceNameMap.values.toList.traverse { spaceName =>
        teamDriveClient.createSpace(SpaceName.of(pocConfig.teamDriveStage, spaceName), spaceName)
      }.runSyncUnsafe()
      createRole("poc-admin", CertifyKeycloak.defaultRealm)(certifyKeycloakConnector)
      createRole("poc-employee", CertifyUbirchRealm)(certifyKeycloakConnector)

      info("Super Admin needs to be created manually")
      await(createKeycloakSuperAdminUser(injector.superAdmin)(certifyKeycloakConnector), 5.seconds)
      val superAdminToken =
        getAuthToken(injector.superAdmin.email, injector.superAdmin.password, certifyConfig.serverUrl)

      info("Super Admin is able to create a new tenant")
      val tenant = superAdminCreatesTenant("tenant1", superAdminToken, injector)

      info("Creation of a tenant should also invoke creation of correct group and role in Keycloak")
      correctRoleAndGroupIsCreatedForTenantAdmin(tenant, injector)

      info("Tenant should be also created in Keycloak")
      info("Right now it is manual process, once it will be automatic one, change the implementation")
      await(
        createKeycloakTenantAdminUser(TenantAdmin(UserName(tenant.tenantName.value), "tenant@email.com", "tenantPass"))(
          certifyKeycloakConnector),
        5.seconds)

      val tenantAdminToken = getAuthToken("tenant@email.com", "tenantPass", certifyConfig.serverUrl)
      info("Tenant adds DeviceCreationToken")
      addDeviceCreationTokenRequest(tenantAdminToken)

      info("Newly created tenant is able to create a new PoC")
      val poc = tenantAdminCreatesPoC(tenant, tenantAdminToken, injector)
      info("PoC should be created in Pending status")
      poc.status shouldBe Pending

      info("Together with PoC, PoC admin is being created")
      val pocAdmin = getPocAdmin(tenant, injector)
      info("PoC admin status shouldBe Pending")
      pocAdmin.status shouldBe Pending

      info("There exists correct device user for PoC")
      createNeededDeviceUser(users, poc)
      info("Newly created PoC should be processed by PocCreationLoop running in the background")
      val pocAfterCreationLoop = processPocByCreationLoop(poc, injector)
      info("PoC status should be changed to Completed")
      pocAfterCreationLoop.status shouldBe Completed
      info("All necessary statuses of PoC should be set to true")
      necessaryPocStatusFieldsShouldBeSetToTrue(poc, injector)

      info("Newly created PoC admin should be processed by PocAdminCreationLoop running in the background")
      val admin = processPocAdminByCreationLoop(pocAdmin, injector)
      info("PoC Admin status should be changed to completed")
      admin.status shouldBe Completed
      info("PoCAdminStatus should be changed accordingly")
      pocAdminStatusFiledsShouldBeSetAccordingly(pocAdmin, injector)

      info(
        "Normally password would be updated by Keycloak RequiredActions email but in tests we are skipping this step")
      updatePassword(admin.certifyUserId.value.toString, "pocAdminPass")(certifyKeycloakConnector)
      resetRequiredActions(admin.certifyUserId.value.toString)(certifyKeycloakConnector)

      info("PoC Admin should be able to create PoC employees")
      val pocAdminToken = getAuthToken(admin.email, "pocAdminPass", certifyConfig.serverUrl)
      val employees = createPoCEmployees(pocAdminToken, tenant, injector)
      info("Employees should be assigned to correct PoC, tenant and should not be created in Certify keycloak yet")
      verifyEmployeesJustAfterCreation(employees, poc, tenant)
      info("PocEmployeeCreationLoop runs in the background")
      runPocEmployeeCreationLoop(tenant, injector)
      info("Employees found by PocEmployeeCreationLoop have status changed to Completed and assigned certifyUserId")
      verifyEmployeesAfterPocEmployeeCreationLoop(tenant, injector)
    }
  }

  private def verifyEmployeesAfterPocEmployeeCreationLoop(tenant: Tenant, injector: InjectorHelper): Unit = {
    val employeesAfterLoop = await(injector.get[PocEmployeeTable].getPocEmployeesByTenantId(tenant.id), 5.seconds)
    employeesAfterLoop.foreach { employee =>
      employee.status shouldBe Completed
      employee.certifyUserId shouldBe defined
    }
  }

  private def runPocEmployeeCreationLoop(tenant: Tenant, injector: InjectorHelper): Unit = {
    val pocEmployeeCreationLoop = injector.get[PocEmployeeCreationLoop]
    val pocEmployeeTable = injector.get[PocEmployeeTable]

    awaitUntil(
      pocEmployeeCreationLoop.startPocEmployeeCreationLoop,
      pocEmployeeTable.getPocEmployeesByTenantId(tenant.id).map(_.forall(_.status == Completed)),
      5.seconds
    )
  }

  private def verifyEmployeesJustAfterCreation(employees: List[PocEmployee], poc: Poc, tenant: Tenant): Unit = {
    employees.foreach { employee =>
      employee.pocId shouldBe poc.id
      employee.tenantId shouldBe tenant.id
      employee.certifyUserId shouldBe None
      employee.active shouldBe true
      employee.status shouldBe Pending
    }
  }

  private def createPoCEmployees(pocAdminToken: String, tenant: Tenant, injector: InjectorHelper): List[PocEmployee] = {
    post(
      "/poc-admin/employees/create",
      body = pocEmployeeCsv.getBytes(),
      headers = Map("authorization" -> pocAdminToken, FakeX509Certs.validX509Header)) {
      status shouldBe 200
      body shouldBe empty
    }
    val pocEmployeeTable = injector.get[PocEmployeeTable]
    await(pocEmployeeTable.getPocEmployeesByTenantId(tenant.id), 5.seconds)
  }

  private def pocAdminStatusFiledsShouldBeSetAccordingly(pocAdmin: PocAdmin, injector: InjectorHelper): Assertion = {
    val pocAdminStatus = await(injector.get[PocAdminStatusRepository].getStatus(pocAdmin.id), 5.seconds).value
    pocAdminStatus.webIdentRequired shouldBe false
    pocAdminStatus.webIdentInitiated shouldBe None
    pocAdminStatus.webIdentSuccess shouldBe None
    pocAdminStatus.certifyUserCreated shouldBe true
    pocAdminStatus.keycloakEmailSent shouldBe true
    pocAdminStatus.pocAdminGroupAssigned shouldBe true
    pocAdminStatus.invitedToTeamDrive shouldBe Some(true)
    pocAdminStatus.invitedToStaticTeamDrive shouldBe Some(true)
    pocAdminStatus.errorMessage shouldBe None
  }

  private def getPocAdmin(tenant: Tenant, injector: InjectorHelper) = {
    val pocAdminRepository = injector.get[PocAdminRepository]
    await(pocAdminRepository.getAllPocAdminsByTenantId(tenant.id), 5.seconds).head
  }

  private def necessaryPocStatusFieldsShouldBeSetToTrue(poc: Poc, injector: InjectorHelper): Assertion = {
    val pocStatusAfterLoop = await(injector.get[PocStatusRepository].getPocStatus(poc.id), 5.seconds).value
    pocStatusAfterLoop.deviceRoleCreated shouldBe true
    pocStatusAfterLoop.deviceGroupCreated shouldBe true
    pocStatusAfterLoop.deviceGroupRoleAssigned shouldBe true

    pocStatusAfterLoop.pocTypeRoleCreated shouldBe Some(true)
    pocStatusAfterLoop.pocTypeGroupCreated shouldBe Some(true)
    pocStatusAfterLoop.pocTypeGroupRoleAssigned shouldBe Some(true)
    pocStatusAfterLoop.certifyRoleCreated shouldBe true
    pocStatusAfterLoop.certifyGroupCreated shouldBe true
    pocStatusAfterLoop.certifyGroupRoleAssigned shouldBe true
    pocStatusAfterLoop.deviceCreated shouldBe true
    pocStatusAfterLoop.goClientProvided shouldBe true
    pocStatusAfterLoop.certifyApiProvided shouldBe true
  }

  private def processPocAdminByCreationLoop(pocAdmin: PocAdmin, injector: InjectorHelper): PocAdmin = {
    val pocAdminCreationLoop = injector.get[PocAdminCreationLoop]
    val pocAdminRepository = injector.get[PocAdminRepository]

    awaitUntil(
      pocAdminCreationLoop.startPocAdminCreationLoop,
      pocAdminRepository.getPocAdmin(pocAdmin.id).map(_.value.status == Completed),
      5.seconds
    )

    await(pocAdminRepository.getPocAdmin(pocAdmin.id).map(_.value), 5.seconds)
  }

  private def processPocByCreationLoop(poc: Poc, injector: InjectorHelper): Poc = {
    val pocCreationLoop = injector.get[PocCreationLoop]
    val pocRepository = injector.get[PocRepository]
    awaitUntil(
      pocCreationLoop.startPocCreationLoop,
      pocRepository.getPoc(poc.id).map {
        case None      => false
        case Some(poc) => if (poc.status == Completed) true else false
      },
      5.seconds
    )
    await(pocRepository.getPoc(poc.id), 5.seconds).value
  }

  private def addDeviceCreationTokenRequest(tenantAdminToken: String): Assertion = {
    post(
      "/tenant-admin/deviceToken",
      body = addDeviceCreationToken.getBytes(),
      headers = Map("authorization" -> tenantAdminToken, FakeX509Certs.validX509Header)
    ) {
      status should equal(200)
      assert(body == "")
    }
  }

  private def tenantAdminCreatesPoC(tenant: Tenant, tenantAdminToken: String, injector: InjectorHelper): Poc = {
    post(
      "/tenant-admin/pocs/create",
      body = createPocWithPocAdminCSV.getBytes(),
      headers = Map("authorization" -> tenantAdminToken, FakeX509Certs.validX509Header)) {
      status should equal(200)
      assert(body.isEmpty)
    }
    val pocRepository = injector.get[PocRepository]
    await(pocRepository.getAllPocsByTenantId(tenant.id), 5.seconds).headOption.value
  }

  private def correctRoleAndGroupIsCreatedForTenantAdmin(tenant: Tenant, injector: InjectorHelper): Unit = {
    val keycloakRolesService = injector.get[KeycloakRolesService]
    await(
      keycloakRolesService.findRole(
        CertifyKeycloak.defaultRealm,
        RoleName(s"TEN_${tenant.tenantName.value}"),
        CertifyKeycloak),
      5.seconds).value
    val keycloakGroupsService = injector.get[KeycloakGroupService]
    await(
      keycloakGroupsService.findGroupByName(
        CertifyKeycloak.defaultRealm,
        GroupName(s"TEN_${tenant.tenantName.value}"),
        CertifyKeycloak),
      5.seconds).right.value
    ()
  }

  private def superAdminCreatesTenant(
    tenantAdminName: String,
    superAdminToken: String,
    injector: InjectorHelper): Tenant = {
    post(
      "/super-admin/tenants/create",
      body = createTenantJson(tenantAdminName).getBytes(),
      headers = Map("authorization" -> superAdminToken, FakeX509Certs.validX509Header)
    ) {
      status should equal(200)
      assert(body == "")
    }

    val tenantRepository = injector.get[TenantRepository]
    await(tenantRepository.getTenantByName(TenantName(tenantAdminName)), 5.seconds).value
  }

  override protected def beforeEach(): Unit = {
    CollectorRegistry.defaultRegistry.clear()
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    withInjector { injector =>
      lazy val superAdminController = injector.get[SuperAdminController]
      addServlet(superAdminController, "/super-admin")
      lazy val tenantAdminController = injector.get[TenantAdminController]
      addServlet(tenantAdminController, "/tenant-admin")
      lazy val pocAdminController = injector.get[PocAdminController]
      addServlet(pocAdminController, "/poc-admin")
    }
  }

}
