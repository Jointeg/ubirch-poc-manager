package com.ubirch.e2e.controllers

import cats.syntax.option._
import com.ubirch.ModelCreationHelper._
import com.ubirch.controllers.PocAdminController
import com.ubirch.controllers.model.PocAdminControllerJsonModel.PocEmployee_OUT
import com.ubirch.data.KeycloakTestData
import com.ubirch.db.tables.{ PocEmployeeRepository, PocEmployeeTable }
import com.ubirch.e2e.E2ETestBase
import com.ubirch.e2e.controllers.PocAdminControllerSpec._
import com.ubirch.e2e.controllers.assertions.PocEmployeesJsonAssertion._
import com.ubirch.e2e.controllers.assertions.PocEmployeeJsonAssertion._
import com.ubirch.models.keycloak.user.UserRequiredAction
import com.ubirch.models.poc.{ Completed, Pending, Poc, PocAdmin, _ }
import com.ubirch.models.pocEmployee.PocEmployee
import com.ubirch.models.tenant.Tenant
import com.ubirch.models.user.UserId
import com.ubirch.models.{ FieldError, ValidationErrorsResponse }
import com.ubirch.services.CertifyKeycloak
import com.ubirch.services.formats.CustomFormats
import com.ubirch.services.keycloak.users.KeycloakUserService
import com.ubirch.services.poc.util.CsvConstants.pocEmployeeHeaderLine
import com.ubirch.test.TestData
import com.ubirch.util.KeycloakRealmsHelper._
import com.ubirch.{ FakeTokenCreator, FakeX509Certs, InjectorHelper }
import io.prometheus.client.CollectorRegistry
import monix.eval.Task
import org.joda.time.{ DateTime, DateTimeZone }
import org.json4s.ext.{ JavaTypesSerializers, JodaTimeSerializers }
import org.json4s.jackson.JsonMethods.{ parse, pretty, render }
import org.json4s.native.Serialization.read
import org.json4s.{ DefaultFormats, Formats }
import org.scalatest.{ AppendedClues, BeforeAndAfterEach }
import org.scalatest.prop.TableDrivenPropertyChecks

import java.time.{ Clock, Instant }
import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._
import scala.language.postfixOps

class PocAdminControllerSpec
  extends E2ETestBase
  with BeforeAndAfterEach
  with TableDrivenPropertyChecks
  with ControllerSpecHelper
  with X509CertTests
  with AppendedClues {

  implicit private val formats: Formats =
    DefaultFormats.lossless ++ new CustomFormats().formats ++ JavaTypesSerializers.all ++ JodaTimeSerializers.all

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

  "Endpoint POST /employees/create" should {
    "create employees from provided CSV" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val (tenant, _, pocAdmin) = addTenantWithPocAndPocAdminToTable(injector)

        post(
          "/employees/create",
          body = goodCsv.getBytes(),
          headers =
            Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
        ) {
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
        val (tenant, _, pocAdmin) = addTenantWithPocAndPocAdminToTable(injector)

        post(
          "/employees/create",
          body = semiCorrectCsv.getBytes(),
          headers =
            Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
        ) {
          status shouldBe 200
          body shouldBe
            """first_name;last_name;email
              |firstName2,lastName2,valid2@email.com;the number of column 1 is invalid. should be 3.
              |firstName2;lastName2;valid2@email.com;error on persisting objects; email already exists.
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

    x509ForbiddenWhenHeaderIsInvalid(
      method = POST,
      "/employees/create",
      requestBody = goodCsv,
      createToken = _.pocAdmin(TestData.PocAdmin.certifyUserId),
      before = injector =>
        addTenantWithPocAndPocAdminToTable(injector, adminCertifyUserId = Some(TestData.PocAdmin.certifyUserId))
    )

    x509SuccessWhenNonBlockingIssuesWithCert[UUID](
      method = POST,
      "/employees/create",
      createToken = _.pocAdmin(TestData.PocAdmin.certifyUserId),
      before = (injector, certifyUserId) =>
        addTenantWithPocAndPocAdminToTable(injector, adminCertifyUserId = Some(certifyUserId)),
      requestBody = _ => goodCsv,
      responseAssertion = (body, _) => body shouldBe empty,
      payload = TestData.PocAdmin.certifyUserId
    )
  }

  "Endpoint GET /employees" must {
    val Endpoint = "/employees"
    "return only pocs of the poc admin" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val employeeTable = injector.get[PocEmployeeTable]
        val (tenant, poc1, pocAdmin1) = createTenantWithPocAndPocAdmin(injector)
        val poc2 = addPocToDb(tenant, injector)
        addPocAdminToDB(poc2, tenant, injector)
        val employee1 =
          createPocEmployee(pocId = poc1.id, tenantId = tenant.id, webAuthnDisconnected = Some(DateTime.now()))
        val employee2 = createPocEmployee(pocId = poc1.id, tenantId = tenant.id)
        val employee3 = createPocEmployee(pocId = poc2.id, tenantId = tenant.id)
        val r = for {
          _ <- employeeTable.createPocEmployee(employee1)
          _ <- employeeTable.createPocEmployee(employee2)
          _ <- employeeTable.createPocEmployee(employee3)
          employees <- employeeTable.getPocEmployeesByTenantId(tenant.id)
        } yield employees
        await(r)

        get(
          Endpoint,
          headers = Map(
            "authorization" -> token.pocAdmin(pocAdmin1.certifyUserId.value).prepare,
            FakeX509Certs.validX509Header)) {
          status should equal(200)
          assertPocEmployeesJson(body)
            .hasTotal(2)
            .hasEmployeeCount(2)
            .hasEmployeeAtIndex(0, employee1)
            .hasEmployeeAtIndex(1, employee2)
        }
      }
    }

    "return Bad Request when poc admin doesn't exist" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        get(
          Endpoint,
          headers = Map("authorization" -> token.pocAdmin(UUID.randomUUID()).prepare, FakeX509Certs.validX509Header)) {
          status should equal(404)
        }
      }
    }

    "return Success also when list of Employees is empty" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val (_, _, pocAdmin) = createTenantWithPocAndPocAdmin(injector)
        get(
          Endpoint,
          headers = Map(
            "authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare,
            FakeX509Certs.validX509Header)) {
          status should equal(200)
          assertPocEmployeesJson(body)
            .hasTotal(0)
            .hasEmployeeCount(0)
        }
      }
    }

    "return Employees page for given index and size" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val employeeTable = injector.get[PocEmployeeTable]
        val (tenant, poc, pocAdmin) = createTenantWithPocAndPocAdmin(injector)
        val employee1 = createPocEmployee(pocId = poc.id, tenantId = tenant.id)
        val employee2 = createPocEmployee(pocId = poc.id, tenantId = tenant.id)
        val employee3 = createPocEmployee(pocId = poc.id, tenantId = tenant.id)
        val employee4 = createPocEmployee(pocId = poc.id, tenantId = tenant.id)
        val employee5 = createPocEmployee(pocId = poc.id, tenantId = tenant.id)
        val r = for {
          _ <- employeeTable.createPocEmployee(employee1)
          _ <- employeeTable.createPocEmployee(employee2)
          _ <- employeeTable.createPocEmployee(employee3)
          _ <- employeeTable.createPocEmployee(employee4)
          _ <- employeeTable.createPocEmployee(employee5)
          employees <- employeeTable.getPocEmployeesByTenantId(tenant.id)
        } yield employees
        val employees = await(r)
        get(
          Endpoint,
          params = Map("pageIndex" -> "1", "pageSize" -> "2"),
          headers =
            Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
        ) {
          val expectedEmployees = employees.slice(2, 4)
          status should equal(200)
          assertPocEmployeesJson(body)
            .hasTotal(5)
            .hasEmployeeCount(2)
            .hasEmployees(expectedEmployees)
        }
      }
    }

    "return Employees for passed search by name" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val employeeTable = injector.get[PocEmployeeTable]
        val (tenant, poc, pocAdmin) = createTenantWithPocAndPocAdmin(injector)
        val employee1 = createPocEmployee(pocId = poc.id, tenantId = tenant.id, name = "employee 1")
        val employee2 = createPocEmployee(pocId = poc.id, tenantId = tenant.id, name = "employee 11")
        val employee3 = createPocEmployee(pocId = poc.id, tenantId = tenant.id, name = "employee 2")
        val r = for {
          _ <- employeeTable.createPocEmployee(employee1)
          _ <- employeeTable.createPocEmployee(employee2)
          _ <- employeeTable.createPocEmployee(employee3)
          employees <- employeeTable.getPocEmployeesByTenantId(tenant.id)
        } yield employees
        val employees = await(r)
        get(
          Endpoint,
          params = Map("search" -> "employee 1"),
          headers =
            Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
        ) {
          status should equal(200)
          assertPocEmployeesJson(body)
            .hasTotal(2)
            .hasEmployeeCount(2)
            .hasEmployees(employees.filter(_.name.startsWith("employee 1")))
        }
      }
    }

    "return Employees for passed search by surname" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val employeeTable = injector.get[PocEmployeeTable]
        val (tenant, poc, pocAdmin) = createTenantWithPocAndPocAdmin(injector)
        val employee1 = createPocEmployee(pocId = poc.id, tenantId = tenant.id, surname = "employee 1")
        val employee2 = createPocEmployee(pocId = poc.id, tenantId = tenant.id, surname = "employee 11")
        val employee3 = createPocEmployee(pocId = poc.id, tenantId = tenant.id, surname = "employee 2")
        val r = for {
          _ <- employeeTable.createPocEmployee(employee1)
          _ <- employeeTable.createPocEmployee(employee2)
          _ <- employeeTable.createPocEmployee(employee3)
          employees <- employeeTable.getPocEmployeesByTenantId(tenant.id)
        } yield employees
        val employees = await(r)
        get(
          Endpoint,
          params = Map("search" -> "employee 1"),
          headers =
            Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
        ) {
          status should equal(200)
          assertPocEmployeesJson(body)
            .hasTotal(2)
            .hasEmployeeCount(2)
            .hasEmployees(employees.filter(_.surname.startsWith("employee 1")))
        }
      }
    }

    "return Employees for passed search by email" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val employeeTable = injector.get[PocEmployeeTable]
        val (tenant, poc, pocAdmin) = createTenantWithPocAndPocAdmin(injector)
        val employee1 = createPocEmployee(pocId = poc.id, tenantId = tenant.id, email = "employee1@test.de")
        val employee2 = createPocEmployee(pocId = poc.id, tenantId = tenant.id, email = "employee11@test.de")
        val employee3 = createPocEmployee(pocId = poc.id, tenantId = tenant.id, email = "employee2@test.de")
        val r = for {
          _ <- employeeTable.createPocEmployee(employee1)
          _ <- employeeTable.createPocEmployee(employee2)
          _ <- employeeTable.createPocEmployee(employee3)
          employees <- employeeTable.getPocEmployeesByTenantId(tenant.id)
        } yield employees
        val employees = await(r)
        get(
          Endpoint,
          params = Map("search" -> "employee1"),
          headers =
            Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
        ) {
          status should equal(200)
          assertPocEmployeesJson(body)
            .hasTotal(2)
            .hasEmployeeCount(2)
            .hasEmployees(employees.filter(_.email.startsWith("employee 1")))
        }
      }
    }

    "return Employees ordered asc by field" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val employeeTable = injector.get[PocEmployeeTable]
        val (tenant, poc, pocAdmin) = createTenantWithPocAndPocAdmin(injector)
        val employee1 = createPocEmployee(pocId = poc.id, tenantId = tenant.id, name = "employee 1")
        val employee2 = createPocEmployee(pocId = poc.id, tenantId = tenant.id, name = "employee 11")
        val employee3 = createPocEmployee(pocId = poc.id, tenantId = tenant.id, name = "employee 2")
        val r = for {
          _ <- employeeTable.createPocEmployee(employee1)
          _ <- employeeTable.createPocEmployee(employee2)
          _ <- employeeTable.createPocEmployee(employee3)
          employees <- employeeTable.getPocEmployeesByTenantId(tenant.id)
        } yield employees
        val employees = await(r).sortBy(_.name)
        get(
          Endpoint,
          params = Map("sortColumn" -> "firstName", "sortOrder" -> "asc"),
          headers =
            Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
        ) {
          status should equal(200)
          assertPocEmployeesJson(body)
            .hasTotal(3)
            .hasEmployeeCount(3)
            .hasEmployees(employees)
        }
      }
    }

    "return Employees ordered desc by field" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val employeeTable = injector.get[PocEmployeeTable]
        val (tenant, poc, pocAdmin) = createTenantWithPocAndPocAdmin(injector)
        val employee1 = createPocEmployee(pocId = poc.id, tenantId = tenant.id, name = "employee A")
        val employee2 = createPocEmployee(pocId = poc.id, tenantId = tenant.id, name = "employee B")
        val employee3 = createPocEmployee(pocId = poc.id, tenantId = tenant.id, name = "employee C")
        val r = for {
          _ <- employeeTable.createPocEmployee(employee1)
          _ <- employeeTable.createPocEmployee(employee2)
          _ <- employeeTable.createPocEmployee(employee3)
          employees <- employeeTable.getPocEmployeesByTenantId(tenant.id)
        } yield employees
        val employees = await(r).filter(_.name.startsWith("employee 1")).sortBy(_.name).reverse
        get(
          Endpoint,
          params = Map("sortColumn" -> "firstName", "sortOrder" -> "desc"),
          headers =
            Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
        ) {
          status should equal(200)
          assertPocEmployeesJson(body)
            .hasTotal(3)
            .hasEmployeeCount(3)
            .hasEmployeeAtIndex(0)(_.hasFirstName("employee C"))
            .hasEmployeeAtIndex(1)(_.hasFirstName("employee B"))
            .hasEmployeeAtIndex(2)(_.hasFirstName("employee A"))
        }
      }
    }

    "return only employees with matching status" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val employeeTable = injector.get[PocEmployeeTable]
        val (tenant, poc, pocAdmin) = createTenantWithPocAndPocAdmin(injector)
        val employee1 =
          createPocEmployee(pocId = poc.id, tenantId = tenant.id, name = "employee 1").copy(status = Pending)
        val employee2 =
          createPocEmployee(pocId = poc.id, tenantId = tenant.id, name = "employee 11").copy(status = Processing)
        val employee3 =
          createPocEmployee(pocId = poc.id, tenantId = tenant.id, name = "employee 2").copy(status = Completed)
        val r = for {
          _ <- employeeTable.createPocEmployee(employee1)
          _ <- employeeTable.createPocEmployee(employee2)
          _ <- employeeTable.createPocEmployee(employee3)
          employees <- employeeTable.getPocEmployeesByTenantId(tenant.id)
        } yield employees
        val employees = await(r).filter(p => Seq(Pending, Processing).contains(p.status))
        get(
          Endpoint,
          params = Map("filterColumn[status]" -> "pending,processing"),
          headers =
            Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
        ) {
          status should equal(200)
          assertPocEmployeesJson(body)
            .hasTotal(2)
            .hasEmployeeCount(2)
            .hasEmployees(employees)
        }
      }
    }

    x509ForbiddenWhenHeaderIsInvalid(
      method = GET,
      path = Endpoint,
      createToken = _.pocAdmin(TestData.PocAdmin.certifyUserId),
      before = injector =>
        addTenantWithPocAndPocAdminToTable(injector, adminCertifyUserId = Some(TestData.PocAdmin.certifyUserId))
    )

    x509SuccessWhenNonBlockingIssuesWithCert[PocEmployee](
      method = GET,
      path = Endpoint,
      createToken = _.pocAdmin(TestData.PocAdmin.certifyUserId),
      payload = createPocEmployee(),
      before = (injector, employee) => {
        val (tenant, poc, _) =
          addTenantWithPocAndPocAdminToTable(injector, adminCertifyUserId = Some(TestData.PocAdmin.certifyUserId))
        await(injector.get[PocEmployeeRepository].createPocEmployee(employee.copy(
          tenantId = tenant.id,
          pocId = poc.id)))
      },
      responseAssertion = (body, employee) =>
        assertPocEmployeesJson(body)
          .hasTotal(1)
          .hasEmployeeCount(1)
          .hasEmployeeAtIndex(0)(_.hasId(employee.id))
    )
  }

  "Endpoint DELETE /employees/:id/2fa-token" should {

    "delete 2FA token for poc employee" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val clock = i.get[Clock]
      val keycloakUserService = i.get[KeycloakUserService]
      val instance = CertifyKeycloak
      val (tenant, poc, pocAdmin) = createTenantWithPocAndPocAdmin(i)
      val repository = i.get[PocEmployeeTable]
      val certifyUserId = await(keycloakUserService.createUserWithoutUserName(
        tenant.getRealm,
        KeycloakTestData.createNewCertifyKeycloakUser(),
        instance,
        List(UserRequiredAction.UPDATE_PASSWORD, UserRequiredAction.WEBAUTHN_REGISTER)
      )).fold(ue => fail(ue.getClass.getSimpleName), ui => ui)
      val employee =
        createPocEmployee(pocId = poc.id).copy(certifyUserId = certifyUserId.value.some, status = Completed)
      val id = await(repository.createPocEmployee(employee))
      val getPocEmployee = repository.getPocEmployee(id)

      val requiredAction = for {
        requiredAction <- keycloakUserService.getUserById(tenant.getRealm, certifyUserId, instance).flatMap {
          case Some(ur) => Task.pure(ur.getRequiredActions)
          case None     => Task.raiseError(new RuntimeException("User not found"))
        }
      } yield requiredAction

      delete(
        s"/employees/${employee.id}/2fa-token",
        headers =
          Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(200)
        body shouldBe empty
        await(requiredAction) should contain theSameElementsAs List("webauthn-register", "UPDATE_PASSWORD")
        await(getPocEmployee).value.webAuthnDisconnected shouldBe Some(new DateTime(
          clock.instant().toString,
          DateTimeZone.forID(clock.getZone.getId)))
      }

      get(
        uri = s"/employees/${employee.id}",
        headers =
          Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(200) withClue s"Error response: $body"
        assertPocEmployeeJson(body).hasRevokeTime(DateTime.parse(clock.instant().toString))
      }
    }

    x509ForbiddenWhenHeaderIsInvalid(
      method = DELETE,
      path = s"/employees/${TestData.PocEmployee.id}/2fa-token",
      createToken = _.pocAdmin(TestData.PocAdmin.certifyUserId),
      before = injector =>
        addTenantWithPocAndPocAdminToTable(injector, adminCertifyUserId = Some(TestData.PocAdmin.certifyUserId))
    )

    x509SuccessWhenNonBlockingIssuesWithCert[PocEmployee](
      method = DELETE,
      path = s"/employees/${TestData.PocEmployee.id}/2fa-token",
      createToken = _.pocAdmin(TestData.PocAdmin.certifyUserId),
      payload = createPocEmployee(employeeId = TestData.PocEmployee.id),
      before = (injector, employee) => {
        val (tenant, poc, _) =
          addTenantWithPocAndPocAdminToTable(injector, adminCertifyUserId = Some(TestData.PocAdmin.certifyUserId))
        val keycloakUserService = injector.get[KeycloakUserService]
        val certifyUserId = await(keycloakUserService.createUserWithoutUserName(
          tenant.getRealm,
          KeycloakTestData.createNewCertifyKeycloakUser(),
          CertifyKeycloak,
          List(UserRequiredAction.UPDATE_PASSWORD, UserRequiredAction.WEBAUTHN_REGISTER)
        )).fold(ue => fail(ue.getClass.getSimpleName), ui => ui)
        await(injector.get[PocEmployeeRepository].createPocEmployee(employee.copy(
          tenantId = tenant.id,
          status = Completed,
          pocId = poc.id,
          certifyUserId = Some(certifyUserId.value))))
      },
      responseAssertion = (body, _) => body shouldBe empty
    )

    "return 404 when poc-employee does not exist" in withInjector { injector =>
      val token = injector.get[FakeTokenCreator]
      val invalidId = UUID.randomUUID()
      val (_, _, pocAdmin) = createTenantWithPocAndPocAdmin(injector)

      delete(
        s"/employees/$invalidId/2fa-token",
        headers =
          Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(404)
        assert(body.contains(s"Poc employee with id '$invalidId' not found"))
      }
    }

    "return 404 when employees is not owned by poc-admin" in withInjector { injector =>
      val token = injector.get[FakeTokenCreator]
      val repository = injector.get[PocEmployeeTable]
      val (_, _, pocAdmin) = createTenantWithPocAndPocAdmin(injector)

      val (_, poc, _) = createTenantWithPocAndPocAdmin(injector, "secondTenant")
      val id = await(repository.createPocEmployee(createPocEmployee(pocId = poc.id)))

      delete(
        s"/employees/$id/2fa-token",
        headers =
          Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(401)
        assert(body.contains(s"Poc employee with id '$id' not found"))
      }
    }

    "return 409 when employees does not have certifyUserId" in withInjector { injector =>
      val token = injector.get[FakeTokenCreator]
      val (_, poc, pocAdmin) = createTenantWithPocAndPocAdmin(injector)
      val employee = createPocEmployee(pocId = poc.id, status = Completed)
      await(injector.get[PocEmployeeTable].createPocEmployee(employee))

      delete(
        s"/employees/${employee.id}/2fa-token",
        headers =
          Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(409)
        assert(body.contains(s"Poc employee '${employee.id}' does not have certifyUserId"))
      }
    }

    "return 409 when employees is not in completed status" in withInjector { injector =>
      val token = injector.get[FakeTokenCreator]
      val repository = injector.get[PocEmployeeTable]
      val (_, poc, pocAdmin) = createTenantWithPocAndPocAdmin(injector)
      val employee = createPocEmployee(pocId = poc.id).copy(status = Pending)
      val id = await(repository.createPocEmployee(employee))

      delete(
        s"/employees/$id/2fa-token",
        headers =
          Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(409)
        assert(body.contains(s"Poc employee '$id' is in wrong status: 'Pending', required: 'Completed'"))
      }
    }

  }

  "Endpoint PUT /employees/:id/active/:isActive" should {
    "deactivate, and activate user" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val repository = i.get[PocEmployeeTable]
      val keycloakUserService = i.get[KeycloakUserService]
      val (tenant, poc, admin) = addTenantWithPocAndPocAdminToTable(i)

      val certifyUserId = await(keycloakUserService.createUserWithoutUserName(
        tenant.getRealm,
        KeycloakTestData.createNewCertifyKeycloakUser(),
        CertifyKeycloak)).fold(ue => fail(ue.getClass.getSimpleName), ui => ui)
      val pocEmployee =
        createPocEmployee(tenantId = tenant.id, pocId = poc.id).copy(
          certifyUserId = Some(certifyUserId.value),
          status = Completed)
      val id = await(repository.createPocEmployee(pocEmployee))

      put(
        s"/employees/$id/active/0",
        headers =
          Map("authorization" -> token.pocAdmin(admin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(200)
        body shouldBe empty
        await(repository.getPocEmployee(id)).value.active shouldBe false
        await(
          keycloakUserService.getUserById(
            tenant.getRealm,
            UserId(certifyUserId.value),
            CertifyKeycloak)).value.isEnabled shouldBe false
      }

      put(
        s"/employees/$id/active/1",
        headers =
          Map("authorization" -> token.pocAdmin(admin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(200)
        body shouldBe empty
        await(repository.getPocEmployee(id)).value.active shouldBe true
        await(
          keycloakUserService.getUserById(
            tenant.getRealm,
            UserId(certifyUserId.value),
            CertifyKeycloak)).value.isEnabled shouldBe true
      }
    }

    x509ForbiddenWhenHeaderIsInvalid(
      method = PUT,
      path = s"/employees/${TestData.PocEmployee.id}/active/0",
      createToken = _.pocAdmin(TestData.PocAdmin.certifyUserId),
      before = injector =>
        addTenantWithPocAndPocAdminToTable(injector, adminCertifyUserId = Some(TestData.PocAdmin.certifyUserId))
    )

    x509SuccessWhenNonBlockingIssuesWithCert[PocEmployee](
      method = PUT,
      path = s"/employees/${TestData.PocEmployee.id}/active/0",
      createToken = _.pocAdmin(TestData.PocAdmin.certifyUserId),
      payload = createPocEmployee(employeeId = TestData.PocEmployee.id, status = Completed),
      before = (injector, employee) => {
        val (tenant, poc, _) =
          addTenantWithPocAndPocAdminToTable(injector, adminCertifyUserId = Some(TestData.PocAdmin.certifyUserId))
        val keycloakUserService = injector.get[KeycloakUserService]
        val certifyUserId = await(keycloakUserService.createUserWithoutUserName(
          tenant.getRealm,
          KeycloakTestData.createNewCertifyKeycloakUser(),
          CertifyKeycloak)).fold(ue => fail(ue.getClass.getSimpleName), ui => ui)
        await(injector.get[PocEmployeeRepository].createPocEmployee(employee.copy(
          tenantId = tenant.id,
          pocId = poc.id,
          certifyUserId = Some(certifyUserId.value))))
      },
      responseAssertion = (body, _) => body shouldBe empty
    )

    "fail deactivating employee, when employee not completed" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val repository = i.get[PocEmployeeTable]
      val keycloakUserService = i.get[KeycloakUserService]
      val (tenant, poc, admin) = addTenantWithPocAndPocAdminToTable(i)

      val certifyUserId = await(keycloakUserService.createUserWithoutUserName(
        tenant.getRealm,
        KeycloakTestData.createNewCertifyKeycloakUser(),
        CertifyKeycloak))
        .fold(ue => fail(ue.getClass.getSimpleName), ui => ui)
      val pocEmployee =
        createPocEmployee(tenantId = tenant.id, pocId = poc.id).copy(certifyUserId = Some(certifyUserId.value))
      val id = await(repository.createPocEmployee(pocEmployee))

      put(
        s"/employees/$id/active/0",
        headers =
          Map("authorization" -> token.pocAdmin(admin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(409)
        body.contains(s"Poc employee with id '$id' cannot be de/-activated before status is Completed.") shouldBe true
        await(repository.getPocEmployee(id)).value.active shouldBe true
        await(
          keycloakUserService.getUserById(
            tenant.getRealm,
            UserId(certifyUserId.value),
            CertifyKeycloak)).value.isEnabled shouldBe true
      }
    }

    "return 404 when poc-admin does not exist" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val (_, _, admin) = addTenantWithPocAndPocAdminToTable(i)
      val invalidPocEmployeeId = UUID.randomUUID()

      put(
        s"/employees/$invalidPocEmployeeId/active/0",
        headers =
          Map("authorization" -> token.pocAdmin(admin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(404)
        assert(body.contains(s"Poc employee with id '$invalidPocEmployeeId' or related tenant was not found"))
      }
    }

    "return 404 when tenant does not exist" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val nonExistingTenant = createTenant("non-existing-tenant")
      val (_, admin) = addPocAndPocAdminToTable(i, nonExistingTenant)
      val invalidPocEmployeeId = UUID.randomUUID()

      put(
        s"/employees/$invalidPocEmployeeId/active/0",
        headers =
          Map("authorization" -> token.pocAdmin(admin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(404)
        assert(
          body.contains(s"Poc employee with id '${nonExistingTenant.id.value.value}' or related tenant was not found"))
      }
    }

    "return 400 when isActive is invalid value" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val (_, _, admin) = addTenantWithPocAndPocAdminToTable(i)
      val invalidPocEmployeeId = UUID.randomUUID()

      put(
        s"/employees/$invalidPocEmployeeId/active/2",
        headers =
          Map("authorization" -> token.pocAdmin(admin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(400)
        assert(body.contains("Illegal value for ActivateSwitch: 2. Expected 0 or 1"))
      }
    }

    "return 409 when poc-admin does not have certifyUserId" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val repository = i.get[PocEmployeeTable]
      val (tenant, poc, admin) = addTenantWithPocAndPocAdminToTable(i)

      val pocEmployee =
        createPocEmployee(tenantId = tenant.id, pocId = poc.id, status = Completed)
      val id = await(repository.createPocEmployee(pocEmployee))

      put(
        s"/employees/$id/active/0",
        headers =
          Map("authorization" -> token.pocAdmin(admin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(409)
        assert(body.contains(s"Poc employee '$id' does not have certifyUserId yet"))
      }
    }

    "return 401 when poc of poc-employee and admin don't belong to same tenant " in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val repository = i.get[PocEmployeeTable]
      val (tenant, poc, _) = addTenantWithPocAndPocAdminToTable(i)

      val pocEmployee =
        createPocEmployee(tenantId = tenant.id, pocId = poc.id)
      val id = await(repository.createPocEmployee(pocEmployee))

      val (_, _, unrelatedAdmin) = addTenantWithPocAndPocAdminToTable(i, "unrelated tenantName")

      put(
        s"/employees/$id/active/0",
        headers = Map(
          "authorization" -> token.pocAdmin(unrelatedAdmin.certifyUserId.value).prepare,
          FakeX509Certs.validX509Header)
      ) {
        status should equal(401)
        assert(body.contains(s"Poc employee with id '$id' doesn't belong to poc of requesting poc admin."))
      }
    }
  }

  "Endpoint GET /employees/:id" should {
    "return PocEmployee by id" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val repository = i.get[PocEmployeeTable]
      val (tenant, poc, admin) = addTenantWithPocAndPocAdminToTable(i)

      val employee =
        createPocEmployee(tenantId = tenant.id, pocId = poc.id, webAuthnDisconnected = Some(DateTime.now()))
      val id = await(repository.createPocEmployee(employee))

      get(
        uri = s"/employees/$id",
        headers =
          Map("authorization" -> token.pocAdmin(admin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(200)
        assertPocEmployeeJson(body)
          .hasId(employee.id)
          .hasFirstName(employee.name)
          .hasLastName(employee.surname)
          .hasEmail(employee.email)
          .hasActive(employee.active)
          .hasStatus(employee.status.toString.toUpperCase)
          .hasCreatedAt(employee.created.dateTime)
          .hasRevokeTime(employee.webAuthnDisconnected.value)
      }
    }

    x509ForbiddenWhenHeaderIsInvalid(
      method = GET,
      path = s"/employees/${TestData.PocEmployee.id}",
      createToken = _.pocAdmin(TestData.PocAdmin.certifyUserId),
      before = injector =>
        addTenantWithPocAndPocAdminToTable(injector, adminCertifyUserId = Some(TestData.PocAdmin.certifyUserId))
    )

    x509SuccessWhenNonBlockingIssuesWithCert[PocEmployee](
      method = GET,
      path = s"/employees/${TestData.PocEmployee.id}",
      createToken = _.pocAdmin(TestData.PocAdmin.certifyUserId),
      payload = createPocEmployee(employeeId = TestData.PocEmployee.id),
      before = (injector, employee) => {
        val (tenant, poc, _) =
          addTenantWithPocAndPocAdminToTable(injector, adminCertifyUserId = Some(TestData.PocAdmin.certifyUserId))
        await(injector.get[PocEmployeeRepository].createPocEmployee(employee.copy(
          tenantId = tenant.id,
          pocId = poc.id)))
      },
      responseAssertion = (body, employee) => assertPocEmployeeJson(body).hasId(employee.id)
    )

    "return 404 when PocEmployee does not exists" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val (_, _, admin) = addTenantWithPocAndPocAdminToTable(i)
      val id = UUID.randomUUID()

      get(
        uri = s"/employees/$id",
        headers =
          Map("authorization" -> token.pocAdmin(admin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(404)
        assert(body.contains(s"Poc employee with id '$id' does not exist"))
      }
    }

    "return 401 when PocEmployee does not belong to requesting PocAdmin" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val repository = i.get[PocEmployeeTable]
      val (tenant, poc, _) = addTenantWithPocAndPocAdminToTable(i)
      val (_, _, otherAdmin) = addTenantWithPocAndPocAdminToTable(injector = i, tenantName = "OtherTenant")

      val pocEmployee = createPocEmployee(tenantId = tenant.id, pocId = poc.id)
      val _ = await(repository.createPocEmployee(pocEmployee))

      get(
        uri = s"/employees/${pocEmployee.id}",
        headers =
          Map("authorization" -> token.pocAdmin(otherAdmin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(401)
        assert(body.contains(s"Unauthorized"))
      }
    }

    "return 403 when requesting user is not a poc admin" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val repository = i.get[PocEmployeeTable]
      val (tenant, poc, _) = addTenantWithPocAndPocAdminToTable(i)
      val pocEmployee = createPocEmployee(tenantId = tenant.id, pocId = poc.id)
      val _ = await(repository.createPocEmployee(pocEmployee))

      get(
        uri = s"/employees/${pocEmployee.id}",
        headers = Map("authorization" -> token.superAdmin.prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(403)
        assert(body.contains(s"Forbidden"))
      }
    }

    "return 400 when passed id is not a valid UUID" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val repository = i.get[PocEmployeeTable]
      val (tenant, poc, admin) = addTenantWithPocAndPocAdminToTable(i)
      val pocEmployee = createPocEmployee(tenantId = tenant.id, pocId = poc.id)
      val _ = await(repository.createPocEmployee(pocEmployee))

      get(
        uri = s"/employees/${pocEmployee.id}-i",
        headers =
          Map("authorization" -> token.pocAdmin(admin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(400)
        assert(body.contains(s"Invalid PocEmployee id '${pocEmployee.id}-i'"))
      }
    }
  }

  "Endpoint PUT /employees/:id" should {
    "update PocEmployee by id" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val repository = i.get[PocEmployeeTable]
      val keycloakUserService = i.get[KeycloakUserService]
      val (tenant, poc, admin) = addTenantWithPocAndPocAdminToTable(i)

      val certifyUserId = await(createKeycloakUserForPocEmployee(keycloakUserService, poc))
        .fold(ue => fail(ue.getClass.getSimpleName), ui => ui.value)

      val pocEmployee =
        createPocEmployee(tenantId = tenant.id, pocId = poc.id, certifyUserId = Some(certifyUserId), status = Completed)
      val id = await(repository.createPocEmployee(pocEmployee))
      val newEmail = (getRandomString + "@test.de").toLowerCase
      val updatedPocEmployee =
        pocEmployee.copy(name = "new name", surname = "new surname", email = newEmail)

      put(
        uri = s"/employees/$id",
        body = updatedPocEmployee.toPocEmployeeInJson.getBytes,
        headers =
          Map("authorization" -> token.pocAdmin(admin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(200)
      }

      get(
        uri = s"/employees/$id",
        headers =
          Map("authorization" -> token.pocAdmin(admin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(200)
        assertPocEmployeeJson(body)
          .hasId(updatedPocEmployee.id)
          .hasFirstName(updatedPocEmployee.name)
          .hasLastName(updatedPocEmployee.surname)
          .hasEmail(updatedPocEmployee.email)
          .hasActive(updatedPocEmployee.active)
          .hasStatus(updatedPocEmployee.status.toString.toUpperCase)
          .hasCreatedAt(updatedPocEmployee.created.dateTime)
          .doesNotHaveRevokeTime()
      }

      val ur = await(keycloakUserService.getUserById(poc.getRealm, UserId(certifyUserId), CertifyKeycloak)).value
      ur.getFirstName shouldBe updatedPocEmployee.name
      ur.getLastName shouldBe updatedPocEmployee.surname
      ur.getEmail shouldBe updatedPocEmployee.email
      ur.getRequiredActions.asScala shouldBe Seq(UserRequiredAction.VERIFY_EMAIL.toString)
    }

    x509ForbiddenWhenHeaderIsInvalid(
      method = PUT,
      path = s"/employees/${TestData.PocEmployee.id}",
      createToken = _.pocAdmin(TestData.PocAdmin.certifyUserId),
      before = injector =>
        addTenantWithPocAndPocAdminToTable(injector, adminCertifyUserId = Some(TestData.PocAdmin.certifyUserId))
    )

    x509SuccessWhenNonBlockingIssuesWithCert[PocEmployee](
      method = PUT,
      pathFromPayload = e => s"/employees/${e.id}",
      createToken = _.pocAdmin(TestData.PocAdmin.certifyUserId),
      payload = createPocEmployee(status = Completed),
      requestBody = e => e.copy(name = "Bruce Wayne", email = s"${getRandomString}@ubirch.de").toPocEmployeeInJson,
      before = (injector, employee) => {
        val (tenant, poc, _) =
          addTenantWithPocAndPocAdminToTable(injector, adminCertifyUserId = Some(TestData.PocAdmin.certifyUserId))
        val keycloakUserService = injector.get[KeycloakUserService]
        val certifyUserId = await(keycloakUserService.createUserWithoutUserName(
          tenant.getRealm,
          KeycloakTestData.createNewCertifyKeycloakUser(),
          CertifyKeycloak)).fold(ue => fail(ue.getClass.getSimpleName), ui => ui)
        await(injector.get[PocEmployeeRepository].createPocEmployee(employee.copy(
          tenantId = tenant.id,
          pocId = poc.id,
          name = "name",
          certifyUserId = Some(certifyUserId.value))))
      },
      responseAssertion = (body, _) => body shouldBe empty,
      assertion = (injector, employee) => {
        val savedEmployee = await(injector.get[PocEmployeeRepository].getPocEmployee(employee.id))
        savedEmployee.value.name shouldBe s"Bruce Wayne"
      }
    )

    "return validation errors" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val repository = i.get[PocEmployeeTable]
      val keycloakUserService = i.get[KeycloakUserService]
      val (tenant, poc, admin) = addTenantWithPocAndPocAdminToTable(i)

      val certifyUserId = await(createKeycloakUserForPocEmployee(keycloakUserService, poc))
        .fold(ue => fail(ue.getClass.getSimpleName), ui => ui.value)

      val pocEmployee =
        createPocEmployee(tenantId = tenant.id, pocId = poc.id, certifyUserId = Some(certifyUserId), status = Completed)
      val id = await(repository.createPocEmployee(pocEmployee))
      val updatedPocEmployee =
        pocEmployee.copy(name = "new name", surname = "new surname", email = "newEmail@.com")

      put(
        uri = s"/employees/$id",
        body = updatedPocEmployee.toPocEmployeeInJson.getBytes,
        headers =
          Map("authorization" -> token.pocAdmin(admin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(400)
        val errorResponse = read[ValidationErrorsResponse](body)
        errorResponse.validationErrors should contain theSameElementsAs Seq(
          FieldError("email", "Invalid email address 'newEmail@.com'")
        )
      }
    }

    "return 404 when PocEmployee does not exists" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val (tenant, poc, admin) = addTenantWithPocAndPocAdminToTable(i)
      val id = UUID.randomUUID()
      val pocEmployee = createPocEmployee(tenantId = tenant.id, pocId = poc.id)

      put(
        uri = s"/employees/$id",
        body = pocEmployee.toPocEmployeeInJson.getBytes,
        headers =
          Map("authorization" -> token.pocAdmin(admin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(404)
      }
    }

    "return 401 when PocEmployee does not belong to requesting PocAdmin" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val repository = i.get[PocEmployeeTable]
      val (tenant, poc, _) = addTenantWithPocAndPocAdminToTable(i)
      val (_, _, otherAdmin) = addTenantWithPocAndPocAdminToTable(injector = i, tenantName = "OtherTenant")

      val pocEmployee = createPocEmployee(tenantId = tenant.id, pocId = poc.id)
      val _ = await(repository.createPocEmployee(pocEmployee))

      put(
        uri = s"/employees/${pocEmployee.id}",
        body = pocEmployee.toPocEmployeeInJson.getBytes,
        headers =
          Map("authorization" -> token.pocAdmin(otherAdmin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(401)
      }
    }

    "return 409 when PocEmployee is not in Completed state" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val repository = i.get[PocEmployeeTable]
      val (tenant, poc, admin) = addTenantWithPocAndPocAdminToTable(i)

      val pocEmployee = createPocEmployee(tenantId = tenant.id, pocId = poc.id, status = Processing)
      val _ = await(repository.createPocEmployee(pocEmployee))

      put(
        uri = s"/employees/${pocEmployee.id}",
        body = pocEmployee.toPocEmployeeInJson.getBytes,
        headers =
          Map("authorization" -> token.pocAdmin(admin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(409)
        assert(
          body.contains(s"Poc employee '${pocEmployee.id}' is in wrong status: '$Processing', required: '$Completed'"))
      }
    }

    "return 403 when requesting user is not a poc admin" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val repository = i.get[PocEmployeeTable]
      val (tenant, poc, _) = addTenantWithPocAndPocAdminToTable(i)
      val pocEmployee = createPocEmployee(tenantId = tenant.id, pocId = poc.id)
      val _ = await(repository.createPocEmployee(pocEmployee))

      put(
        uri = s"/employees/${pocEmployee.id}",
        body = pocEmployee.toPocEmployeeInJson.getBytes,
        headers = Map("authorization" -> token.superAdmin.prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(403)
        assert(body.contains(s"Forbidden"))
      }
    }

    "return 400 when passed id is not a valid UUID" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val repository = i.get[PocEmployeeTable]
      val (tenant, poc, admin) = addTenantWithPocAndPocAdminToTable(i)
      val pocEmployee = createPocEmployee(tenantId = tenant.id, pocId = poc.id)
      val _ = await(repository.createPocEmployee(pocEmployee))

      put(
        uri = s"/employees/${pocEmployee.id}-i",
        body = pocEmployee.toPocEmployeeInJson.getBytes,
        headers =
          Map("authorization" -> token.pocAdmin(admin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(400)
        assert(body.contains(s"Invalid PocEmployee id '${pocEmployee.id}-i'"))
      }
    }

    "return 500 when PocEmployee is not assigned to keycloak user" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val repository = i.get[PocEmployeeTable]
      val (tenant, poc, admin) = addTenantWithPocAndPocAdminToTable(i)

      val pocEmployee =
        createPocEmployee(tenantId = tenant.id, pocId = poc.id, status = Completed, certifyUserId = None)
      val _ = await(repository.createPocEmployee(pocEmployee))

      put(
        uri = s"/employees/${pocEmployee.id}",
        body = pocEmployee.toPocEmployeeInJson.getBytes,
        headers =
          Map("authorization" -> token.pocAdmin(admin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(500)
        assert(body.contains(s"Poc employee '${pocEmployee.id}' is not assigned to certify user"))
      }
    }

    "return 500 when certify user assigned to the PocEmployee is not found" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val repository = i.get[PocEmployeeTable]
      val (tenant, poc, admin) = addTenantWithPocAndPocAdminToTable(i)

      val pocEmployee = createPocEmployee(
        tenantId = tenant.id,
        pocId = poc.id,
        status = Completed,
        certifyUserId = Some(UUID.randomUUID()))
      val _ = await(repository.createPocEmployee(pocEmployee))

      put(
        uri = s"/employees/${pocEmployee.id}",
        body = pocEmployee.toPocEmployeeInJson.getBytes,
        headers =
          Map("authorization" -> token.pocAdmin(admin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(500)
        assert(body.contains(s"Poc employee '${pocEmployee.id}' is assigned to not existing certify user"))
      }
    }
  }

  private val invalidParameter =
    Table(
      ("param", "value"),
      ("filterColumn[status]", "invalid"),
      ("sortColumn", "invalid"),
      ("sortColumn", ""),
      ("sortOrder", "invalid"),
      ("sortOrder", ""),
      ("pageIndex", "invalid"),
      ("pageIndex", "-1"),
      ("pageIndex", ""),
      ("pageSize", "invalid"),
      ("pageSize", "-1"),
      ("pageSize", "")
    )

  forAll(invalidParameter) { (param, value) =>
    s"Endpoint GET /employees must respond with a bad request when provided an invalid value '$value' for '$param'" in {
      withInjector {
        injector =>
          val token = injector.get[FakeTokenCreator]
          val (_, _, pocAdmin) = addTenantWithPocAndPocAdminToTable(injector)
          get(
            "/employees",
            params = Map(param -> value),
            Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare, FakeX509Certs.validX509Header)
          ) {
            status should equal(400)
            val errorResponse = read[ValidationErrorsResponse](body)
            errorResponse.validationErrors should have size 1
            errorResponse.validationErrors.filter(_.name == param) should have size 1
          }
      }
    }
  }

  def createTenantWithPocAndPocAdmin(
    injector: InjectorHelper,
    tenantName: String = globalTenantName): (Tenant, Poc, PocAdmin) = {
    val tenant = addTenantToDB(injector, tenantName)
    val poc = addPocToDb(tenant, injector)
    val pocAdmin = addPocAdminToDB(poc, tenant, injector)
    (tenant, poc, pocAdmin)
  }

  override protected def beforeEach(): Unit = {
    CollectorRegistry.defaultRegistry.clear()
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    withInjector { injector =>
      lazy val superAdminController = injector.get[PocAdminController]
      addServlet(superAdminController, "/*")
    }
  }
}

object PocAdminControllerSpec {
  implicit val pocEmployeeOrdering: Ordering[PocEmployee] =
    (x: PocEmployee, y: PocEmployee) => x.name.compareTo(y.name)

  implicit class EmployeeOps(employee: PocEmployee) {
    def datesToIsoFormat: PocEmployee = {
      employee.copy(
        created = Created(DateTime.parse(Instant.ofEpochMilli(employee.created.dateTime.getMillis).toString)),
        lastUpdated = Updated(DateTime.parse(Instant.ofEpochMilli(employee.lastUpdated.dateTime.getMillis).toString))
      )
    }

    def toPocEmployeeOut: PocEmployee_OUT = PocEmployee_OUT.fromPocEmployee(employee)
  }

  implicit class PocEmployeeOps(pe: PocEmployee) {
    def toPocEmployeeInJson: String = {
      val json =
        s"""{
           | "firstName": "${pe.name}",
           | "lastName": "${pe.surname}",
           | "email": "${pe.email}"
           |}""".stripMargin
      pretty(render(parse(json)))
    }
  }
}
