package com.ubirch.e2e.controllers

import cats.syntax.either._
import cats.syntax.option._
import com.ubirch.{ FakeTokenCreator, InjectorHelper }
import com.ubirch.ModelCreationHelper.{ createPoc, createPocAdmin, createPocEmployee, createTenant }
import com.ubirch.controllers.PocAdminController
import com.ubirch.data.KeycloakTestData
import com.ubirch.db.tables.{ PocAdminRepository, PocAdminTable, PocEmployeeTable, PocTable, TenantTable }
import com.ubirch.e2e.E2ETestBase
import com.ubirch.models.keycloak.user.UserRequiredAction
import com.ubirch.models.poc.{ Completed, Pending, Poc, PocAdmin }
import com.ubirch.models.tenant.Tenant
import com.ubirch.services.formats.{ CustomFormats, JodaDateTimeFormats }
import com.ubirch.services.jwt.PublicKeyPoolService
import com.ubirch.services.keycloak.users.KeycloakUserService
import com.ubirch.services.poc.util.CsvConstants.pocEmployeeHeaderLine
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak }
import io.prometheus.client.CollectorRegistry
import monix.eval.Task
import org.json4s.ext.{ JavaTypesSerializers, JodaTimeSerializers }
import org.json4s.{ DefaultFormats, Formats }
import org.scalatest.BeforeAndAfterEach

import java.util.UUID
import scala.concurrent.duration.DurationInt

class PocAdminControllerSpec extends E2ETestBase with BeforeAndAfterEach {

  implicit private val formats: Formats =
    DefaultFormats.lossless ++ CustomFormats.all ++ JavaTypesSerializers.all ++ JodaTimeSerializers.all ++ JodaDateTimeFormats.all

  private val goodCsv =
    s"""$pocEmployeeHeaderLine
       |firstName1;lastName1;valid1@email.com
       |firstName2;lastName2;valid2@email.com
       |""".stripMargin

  private val semiCorrectCsv =
    s"""$pocEmployeeHeaderLine
       |firstName1;lastName1;valid1@email.com
       |firstName2,lastName2,valid2@email.com
       |firstName2;lastName2;valid2@email.com
       |firstName2;lastName2;valid2@email.com
       |firstName3;lastName3;valid3@email.com
       |firstName3;lastName3;
       |""".stripMargin

  "Endpoint POST /employee/create" should {
    "create employees from provided CSV" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val (tenant, _, pocAdmin) = createTenantWithPocAndPocAdmin(injector)

        post(
          "/employee/create",
          body = goodCsv.getBytes(),
          headers = Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare)) {
          status shouldBe 200
          body shouldBe empty
        }

        val pocEmployeeTable = injector.get[PocEmployeeTable]
        val employees = await(pocEmployeeTable.getPocEmployeesByTenantId(tenant.id), 5.seconds)
        employees should have size 2
        employees.foreach(employee => employee.status shouldBe Pending)
        employees.exists(employee =>
          employee.name == "firstName1" && employee.surname == "lastName1" && employee.email == "valid1@email.com") shouldBe true
        employees.exists(employee =>
          employee.name == "firstName2" && employee.surname == "lastName2" && employee.email == "valid2@email.com") shouldBe true
      }
    }

    "return errors in body for each incorrect line and insert correct ones" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val (tenant, _, pocAdmin) = createTenantWithPocAndPocAdmin(injector)

        post(
          "/employee/create",
          body = semiCorrectCsv.getBytes(),
          headers = Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare)) {
          status shouldBe 200
          body shouldBe
            """first_name;last_name;email
              |firstName2,lastName2,valid2@email.com;the number of column 1 is invalid. should be 3.
              |firstName2;lastName2;valid2@email.com;error on persisting objects; maybe duplicated key error
              |firstName3;lastName3;;the number of column 2 is invalid. should be 3.""".stripMargin
        }

        val pocEmployeeTable = injector.get[PocEmployeeTable]
        val employees = await(pocEmployeeTable.getPocEmployeesByTenantId(tenant.id), 5.seconds)
        employees should have size 3
        employees.foreach(employee => employee.status shouldBe Pending)
        employees.exists(employee =>
          employee.name == "firstName1" && employee.surname == "lastName1" && employee.email == "valid1@email.com") shouldBe true
        employees.exists(employee =>
          employee.name == "firstName2" && employee.surname == "lastName2" && employee.email == "valid2@email.com") shouldBe true
        employees.exists(employee =>
          employee.name == "firstName3" && employee.surname == "lastName3" && employee.email == "valid3@email.com") shouldBe true
      }
    }
  }

  "Endpoint DELETE /poc-employee/:id/2fa-token" should {
    "delete 2FA token for poc employee" in withInjector { injector =>
      val token = injector.get[FakeTokenCreator]
      val keycloakUserService = injector.get[KeycloakUserService]
      val instance = CertifyKeycloak
      val certifyUserId = await(keycloakUserService.createUserWithoutUserName(
        KeycloakTestData.createNewCertifyKeycloakUser(),
        instance,
        List(UserRequiredAction.UPDATE_PASSWORD, UserRequiredAction.WEBAUTHN_REGISTER)))
        .fold(ue => fail(ue.getClass.getSimpleName), ui => ui)
      val (_, poc, pocAdmin) = createTenantWithPocAndPocAdmin(injector)
      val employee = createPocEmployee(pocId = poc.id).copy(certifyUserId = certifyUserId.value.some)
      await(injector.get[PocEmployeeTable].createPocEmployee(employee))

      val requiredAction = for {
        requiredAction <- keycloakUserService.getUserById(certifyUserId, instance).flatMap {
          case Some(ur) => Task.pure(ur.getRequiredActions)
          case None     => Task.raiseError(new RuntimeException("User not found"))
        }
      } yield requiredAction

      delete(
        s"/poc-employee/${employee.id}/2fa-token",
        headers = Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare)
      ) {
        status should equal(200)
        body shouldBe empty
        await(requiredAction) should contain theSameElementsAs List("UPDATE_PASSWORD")
      }
    }

    "return 404 when poc-employee does not exist" in withInjector { injector =>
      val token = injector.get[FakeTokenCreator]
      val invalidId = UUID.randomUUID()
      val (_, _, pocAdmin) = createTenantWithPocAndPocAdmin(injector)

      delete(
        s"/poc-employee/$invalidId/2fa-token",
        headers = Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare)
      ) {
        status should equal(404)
        assert(body.contains(s"Poc employee with id '$invalidId' not found"))
      }
    }

    "return 409 when poc-employee does not have certifyUserId" in withInjector { injector =>
      val token = injector.get[FakeTokenCreator]
      val (_, poc, pocAdmin) = createTenantWithPocAndPocAdmin(injector)
      val employee = createPocEmployee(pocId = poc.id)
      await(injector.get[PocEmployeeTable].createPocEmployee(employee))

      delete(
        s"/poc-employee/${employee.id}/2fa-token",
        headers = Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare)
      ) {
        status should equal(409)
        assert(body.contains(s"Poc employee '${employee.id}' does not have certifyUserId"))
      }
    }
  }

  def createTenantWithPocAndPocAdmin(injector: InjectorHelper): (Tenant, Poc, PocAdmin) = {
    val tenant = addTenantToDB(injector)
    val poc = addPocToDb(tenant, injector)
    val pocAdmin = addPocAdminToDB(poc, tenant, injector)
    (tenant, poc, pocAdmin)
  }

  private def addPocAdminToDB(poc: Poc, tenant: Tenant, injector: InjectorHelper): PocAdmin = {
    val pocAdminTable = injector.get[PocAdminTable]
    val pocAdmin =
      createPocAdmin(pocId = poc.id, tenantId = tenant.id, certifyUserId = Some(UUID.randomUUID()), status = Completed)
    await(pocAdminTable.createPocAdmin(pocAdmin), 5.seconds)
    pocAdmin
  }

  private def addTenantToDB(injector: InjectorHelper): Tenant = {
    val tenantTable = injector.get[TenantTable]
    val tenant = createTenant()
    await(tenantTable.createTenant(tenant), 5.seconds)
    tenant
  }

  private def addPocToDb(tenant: Tenant, injector: InjectorHelper): Poc = {
    val pocTable = injector.get[PocTable]
    val poc = createPoc(tenantName = tenant.tenantName, status = Pending)
    await(pocTable.createPoc(poc), 5.seconds)
    poc
  }

  override protected def beforeEach(): Unit = {
    CollectorRegistry.defaultRegistry.clear()
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    withInjector { injector =>
      lazy val pool = injector.get[PublicKeyPoolService]
      await(pool.init(DeviceKeycloak, CertifyKeycloak), 2.seconds)

      lazy val superAdminController = injector.get[PocAdminController]
      addServlet(superAdminController, "/*")
    }
  }

}
