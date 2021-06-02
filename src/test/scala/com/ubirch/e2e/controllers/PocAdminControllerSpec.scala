package com.ubirch.e2e.controllers

import cats.syntax.either._
import cats.syntax.option._
import com.ubirch.{ FakeTokenCreator, InjectorHelper }
import com.ubirch.ModelCreationHelper.{ createPoc, createPocAdmin, createPocEmployee, createTenant, globalTenantName }
import com.ubirch.FakeTokenCreator
import com.ubirch.controllers.PocAdminController
import com.ubirch.data.KeycloakTestData
import com.ubirch.db.tables.{ PocAdminRepository, PocAdminTable, PocEmployeeTable, PocTable, TenantTable }
import com.ubirch.controllers.PocAdminController.PocEmployee_OUT
import com.ubirch.data.KeycloakTestData
import com.ubirch.db.tables.PocEmployeeTable
import com.ubirch.e2e.E2ETestBase
import com.ubirch.models.keycloak.user.UserRequiredAction
import com.ubirch.models.poc.{ Completed, Pending, Poc, PocAdmin }
import com.ubirch.models.tenant.Tenant
import com.ubirch.e2e.controllers.PocAdminControllerSpec._
import com.ubirch.models.poc._
import com.ubirch.models.pocEmployee.PocEmployee
import com.ubirch.models.user.UserId
import com.ubirch.models.{ Paginated_OUT, ValidationErrorsResponse }
import com.ubirch.services.formats.{ CustomFormats, JodaDateTimeFormats }
import com.ubirch.services.jwt.PublicKeyPoolService
import com.ubirch.services.keycloak.users.KeycloakUserService
import com.ubirch.services.poc.util.CsvConstants.pocEmployeeHeaderLine
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak }
import io.prometheus.client.CollectorRegistry
import monix.eval.Task
import org.joda.time.{ DateTime, DateTimeZone }
import org.json4s.ext.{ JavaTypesSerializers, JodaTimeSerializers }
import org.json4s.native.Serialization.read
import org.json4s.{ DefaultFormats, Formats }
import org.scalatest.BeforeAndAfterEach
import org.scalatest.prop.TableDrivenPropertyChecks

import java.time.{ Clock, Instant }
import java.util.UUID
import scala.concurrent.duration.DurationInt

class PocAdminControllerSpec
  extends E2ETestBase
  with BeforeAndAfterEach
  with TableDrivenPropertyChecks
  with ControllerSpecHelper {

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

  "Endpoint POST /employees/create" should {
    "create employees from provided CSV" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val (tenant, _, pocAdmin) = createTenantWithPocAndPocAdmin(injector)

        post(
          "/employees/create",
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
          "/employees/create",
          body = semiCorrectCsv.getBytes(),
          headers = Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare)) {
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
  }

  "Endpoint GET /employees" must {
    val EndPoint = "/employees"
    "return only pocs of the poc admin" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val employeeTable = injector.get[PocEmployeeTable]
        val (tenant, poc1, pocAdmin1) = createTenantWithPocAndPocAdmin(injector)
        val poc2 = addPocToDb(tenant, injector)
        val _ = addPocAdminToDB(poc2, tenant, injector)
        val employee1 = createPocEmployee(pocId = poc1.id, tenantId = tenant.id)
        val employee2 = createPocEmployee(pocId = poc1.id, tenantId = tenant.id)
        val employee3 = createPocEmployee(pocId = poc2.id, tenantId = tenant.id)
        val r = for {
          _ <- employeeTable.createPocEmployee(employee1)
          _ <- employeeTable.createPocEmployee(employee2)
          _ <- employeeTable.createPocEmployee(employee3)
          employees <- employeeTable.getPocEmployeesByTenantId(tenant.id)
        } yield employees
        val employees = await(r, 5.seconds)
        employees.size shouldBe 3
        val expectedEmployees = employees.filter(_.pocId == poc1.id).map(_.toPocEmployeeOut)
        get(EndPoint, headers = Map("authorization" -> token.pocAdmin(pocAdmin1.certifyUserId.value).prepare)) {
          status should equal(200)
          val employeeOut = read[Paginated_OUT[PocEmployee_OUT]](body)
          employeeOut.total shouldBe 2
          employeeOut.records shouldBe expectedEmployees
        }
      }
    }

    "return Bad Request when poc admin doesn't exist" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        get(EndPoint, headers = Map("authorization" -> token.pocAdmin(UUID.randomUUID()).prepare)) {
          status should equal(404)
        }
      }
    }

    "return Success also when list of Employees is empty" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val (_, _, pocAdmin) = createTenantWithPocAndPocAdmin(injector)
        get(EndPoint, headers = Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare)) {
          status should equal(200)
          val employeeOut = read[Paginated_OUT[PocEmployee_OUT]](body)
          employeeOut.total shouldBe 0
          employeeOut.records should have size 0
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
        val employees = await(r, 5.seconds).map(_.toPocEmployeeOut)
        employees.size shouldBe 5
        get(
          EndPoint,
          params = Map("pageIndex" -> "1", "pageSize" -> "2"),
          headers =
            Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare)) {
          status should equal(200)
          val employeeOut = read[Paginated_OUT[PocEmployee_OUT]](body)
          employeeOut.total shouldBe 5
          employeeOut.records shouldBe employees.slice(2, 4)
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
        val employees = await(r, 5.seconds).map(_.toPocEmployeeOut)
        employees.size shouldBe 3
        get(
          EndPoint,
          params = Map("search" -> "employee 1"),
          headers =
            Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare)) {
          status should equal(200)
          val employeeOut = read[Paginated_OUT[PocEmployee_OUT]](body)
          employeeOut.total shouldBe 2
          employeeOut.records shouldBe employees.filter(_.firstName.startsWith("employee 1"))
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
        val employees = await(r, 5.seconds).map(_.toPocEmployeeOut)
        employees.size shouldBe 3
        get(
          EndPoint,
          params = Map("search" -> "employee 1"),
          headers =
            Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare)) {
          status should equal(200)
          val employeeOut = read[Paginated_OUT[PocEmployee_OUT]](body)
          employeeOut.total shouldBe 2
          employeeOut.records shouldBe employees.filter(_.lastName.startsWith("employee 1"))
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
        val employees = await(r, 5.seconds).map(_.toPocEmployeeOut)
        employees.size shouldBe 3
        get(
          EndPoint,
          params = Map("search" -> "employee1"),
          headers =
            Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare)) {
          status should equal(200)
          val employeeOut = read[Paginated_OUT[PocEmployee_OUT]](body)
          employeeOut.total shouldBe 2
          employeeOut.records shouldBe employees.filter(_.email.startsWith("employee1"))
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
        val employees = await(r, 5.seconds).sortBy(_.name).map(_.toPocEmployeeOut)
        employees.size shouldBe 3
        get(
          EndPoint,
          params = Map("sortColumn" -> "firstName", "sortOrder" -> "asc"),
          headers =
            Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare)) {
          status should equal(200)
          val employeeOut = read[Paginated_OUT[PocEmployee_OUT]](body)
          employeeOut.total shouldBe 3
          employeeOut.records shouldBe employees
        }
      }
    }
    "Endpoint DELETE /poc-employee/:id/2fa-token" should {
      "delete 2FA token for poc employee" in withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val clock = injector.get[Clock]
        val keycloakUserService = injector.get[KeycloakUserService]
        val instance = CertifyKeycloak
        val repository = injector.get[PocEmployeeTable]
        val certifyUserId = await(keycloakUserService.createUserWithoutUserName(
          KeycloakTestData.createNewCertifyKeycloakUser(),
          instance,
          List(UserRequiredAction.UPDATE_PASSWORD, UserRequiredAction.WEBAUTHN_REGISTER)))
          .fold(ue => fail(ue.getClass.getSimpleName), ui => ui)
        val (_, poc, pocAdmin) = createTenantWithPocAndPocAdmin(injector)
        val employee = createPocEmployee(pocId = poc.id).copy(certifyUserId = certifyUserId.value.some)
        val id = await(repository.createPocEmployee(employee))
        val getPocEmployee = repository.getPocEmployee(id)

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
          await(getPocEmployee).value.webAuthnDisconnected shouldBe Some(new DateTime(
            clock.instant().toString,
            DateTimeZone.forID(clock.getZone.getId)))
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

      "return 404 when poc-employee is not owned by poc-admin" in withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val repository = injector.get[PocEmployeeTable]
        val (_, _, pocAdmin) = createTenantWithPocAndPocAdmin(injector)

        val (_, poc, _) = createTenantWithPocAndPocAdmin(injector, "secondTenant")
        val id = await(repository.createPocEmployee(createPocEmployee(pocId = poc.id)))

        delete(
          s"/poc-employee/$id/2fa-token",
          headers = Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare)
        ) {
          status should equal(404)
          assert(body.contains(s"Poc employee with id '$id' not found"))
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

    def createTenantWithPocAndPocAdmin(
      injector: InjectorHelper,
      tenantName: String = globalTenantName): (Tenant, Poc, PocAdmin) = {
      val tenant = addTenantToDB(injector, tenantName)
      val poc = addPocToDb(tenant, injector)
      val pocAdmin = addPocAdminToDB(poc, tenant, injector)
      (tenant, poc, pocAdmin)
    }

    "return Employees ordered desc by field" in {
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
        val employees = await(r, 5.seconds).sortBy(_.name).reverse.map(_.toPocEmployeeOut)
        employees.size shouldBe 3
        get(
          EndPoint,
          params = Map("sortColumn" -> "firstName", "sortOrder" -> "desc"),
          headers =
            Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare)) {
          status should equal(200)
          val employeeOut = read[Paginated_OUT[PocEmployee_OUT]](body)
          employeeOut.total shouldBe 3
          employeeOut.records shouldBe employees
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
        val employees =
          await(r, 5.seconds).filter(p => Seq(Pending, Processing).contains(p.status)).map(_.toPocEmployeeOut)
        employees.size shouldBe 2
        get(
          EndPoint,
          params = Map("filterColumn[status]" -> "pending,processing"),
          headers =
            Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare)) {
          status should equal(200)
          val employeeOut = read[Paginated_OUT[PocEmployee_OUT]](body)
          employeeOut.total shouldBe 2
          employeeOut.records shouldBe employees
        }
      }
    }
  }

  "Endpoint PUT /poc-employee/:id/active/:isActive" should {
    "deactivate, and activate user" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val repository = i.get[PocEmployeeTable]
      val keycloakUserService = i.get[KeycloakUserService]
      val (tenant, poc, admin) = createTenantWithPocAndPocAdmin(i)

      val certifyUserId = await(keycloakUserService.createUserWithoutUserName(
        KeycloakTestData.createNewCertifyKeycloakUser(),
        CertifyKeycloak))
        .fold(ue => fail(ue.getClass.getSimpleName), ui => ui)
      val pocEmployee =
        createPocEmployee(tenantId = tenant.id, pocId = poc.id).copy(
          certifyUserId = Some(certifyUserId.value),
          status = Completed)
      val id = await(repository.createPocEmployee(pocEmployee))

      put(
        s"/poc-employee/$id/active/0",
        headers = Map("authorization" -> token.pocAdmin(admin.certifyUserId.value).prepare)
      ) {
        status should equal(200)
        body shouldBe empty
        await(repository.getPocEmployee(id)).value.active shouldBe false
        await(
          keycloakUserService.getUserById(UserId(certifyUserId.value), CertifyKeycloak)).value.isEnabled shouldBe false
      }

      put(
        s"/poc-employee/$id/active/1",
        headers = Map("authorization" -> token.pocAdmin(admin.certifyUserId.value).prepare)
      ) {
        status should equal(200)
        body shouldBe empty
        await(repository.getPocEmployee(id)).value.active shouldBe true
        await(
          keycloakUserService.getUserById(UserId(certifyUserId.value), CertifyKeycloak)).value.isEnabled shouldBe true
      }
    }

    "fail deactivating employee, when employee not completed" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val repository = i.get[PocEmployeeTable]
      val keycloakUserService = i.get[KeycloakUserService]
      val (tenant, poc, admin) = createTenantWithPocAndPocAdmin(i)

      val certifyUserId = await(keycloakUserService.createUserWithoutUserName(
        KeycloakTestData.createNewCertifyKeycloakUser(),
        CertifyKeycloak))
        .fold(ue => fail(ue.getClass.getSimpleName), ui => ui)
      val pocEmployee =
        createPocEmployee(tenantId = tenant.id, pocId = poc.id).copy(certifyUserId = Some(certifyUserId.value))
      val id = await(repository.createPocEmployee(pocEmployee))

      put(
        s"/poc-employee/$id/active/0",
        headers = Map("authorization" -> token.pocAdmin(admin.certifyUserId.value).prepare)
      ) {
        status should equal(409)
        body.contains(s"Poc employee with id '$id' cannot be de/-activated before status is Completed.") shouldBe true
        await(repository.getPocEmployee(id)).value.active shouldBe true
        await(
          keycloakUserService.getUserById(UserId(certifyUserId.value), CertifyKeycloak)).value.isEnabled shouldBe true
      }
    }

    "return 404 when poc-admin does not exist" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val (_, _, admin) = createTenantWithPocAndPocAdmin(i)
      val invalidPocEmployeeId = UUID.randomUUID()

      put(
        s"/poc-employee/$invalidPocEmployeeId/active/0",
        headers = Map("authorization" -> token.pocAdmin(admin.certifyUserId.value).prepare)
      ) {
        status should equal(404)
        assert(body.contains(s"Poc employee with id '$invalidPocEmployeeId' not found"))
      }
    }

    "return 400 when isActive is invalid value" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val (_, _, admin) = createTenantWithPocAndPocAdmin(i)
      val invalidPocEmployeeId = UUID.randomUUID()

      put(
        s"/poc-employee/$invalidPocEmployeeId/active/2",
        headers = Map("authorization" -> token.pocAdmin(admin.certifyUserId.value).prepare)
      ) {
        status should equal(400)
        assert(body.contains("Illegal value for ActivateSwitch: 2. Expected 0 or 1"))
      }
    }

    "return 409 when poc-admin does not have certifyUserId" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val repository = i.get[PocEmployeeTable]
      val (tenant, poc, admin) = createTenantWithPocAndPocAdmin(i)

      val pocEmployee =
        createPocEmployee(tenantId = tenant.id, pocId = poc.id, status = Completed)
      val id = await(repository.createPocEmployee(pocEmployee))

      put(
        s"/poc-employee/$id/active/0",
        headers = Map("authorization" -> token.pocAdmin(admin.certifyUserId.value).prepare)
      ) {
        status should equal(409)
        assert(body.contains(s"Poc employee '$id' does not have certifyUserId yet"))
      }
    }

    "return 401 when poc of poc-employee and admin don't belong to same tenant " in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val repository = i.get[PocEmployeeTable]
      val (tenant, poc, _) = createTenantWithPocAndPocAdmin(i)

      val pocEmployee =
        createPocEmployee(tenantId = tenant.id, pocId = poc.id)
      val id = await(repository.createPocEmployee(pocEmployee))

      val (_, _, unrelatedAdmin) = createTenantWithPocAndPocAdmin(i, "unrelated tenantName")

      put(
        s"/poc-employee/$id/active/0",
        headers = Map("authorization" -> token.pocAdmin(unrelatedAdmin.certifyUserId.value).prepare)
      ) {
        status should equal(401)
        assert(body.contains(s"Poc employee with id '$id' doesn't belong to poc of requesting poc admin."))
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
          val (_, _, pocAdmin) = createTenantWithPocAndPocAdmin(injector)
          get(
            "/employees",
            params = Map(param -> value),
            Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare)
          ) {
            status should equal(400)
            val errorResponse = read[ValidationErrorsResponse](body)
            errorResponse.validationErrors should have size 1
            errorResponse.validationErrors.filter(_.name == param) should have size 1
          }
      }
    }
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

object PocAdminControllerSpec {
  implicit class EmployeeOps(employee: PocEmployee) {
    def datesToIsoFormat: PocEmployee = {
      employee.copy(
        created = Created(DateTime.parse(Instant.ofEpochMilli(employee.created.dateTime.getMillis).toString)),
        lastUpdated = Updated(DateTime.parse(Instant.ofEpochMilli(employee.lastUpdated.dateTime.getMillis).toString))
      )
    }

    def toPocEmployeeOut: PocEmployee_OUT = PocEmployee_OUT.fromPocEmployee(employee)
  }
}
