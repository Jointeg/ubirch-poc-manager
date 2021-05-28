package com.ubirch.e2e.controllers
import com.ubirch.FakeTokenCreator
import com.ubirch.ModelCreationHelper.createPocEmployee
import com.ubirch.controllers.PocAdminController
import com.ubirch.db.tables.PocEmployeeTable
import com.ubirch.e2e.E2ETestBase
import com.ubirch.models.{ Paginated_OUT, ValidationErrorsResponse }
import com.ubirch.models.poc.{ Completed, Created, Pending, Processing, Updated }
import com.ubirch.models.pocEmployee.PocEmployee
import com.ubirch.services.formats.{ CustomFormats, JodaDateTimeFormats }
import com.ubirch.services.jwt.PublicKeyPoolService
import com.ubirch.services.poc.util.CsvConstants.pocEmployeeHeaderLine
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak }
import io.prometheus.client.CollectorRegistry
import org.joda.time.DateTime
import org.json4s.ext.{ JavaTypesSerializers, JodaTimeSerializers }
import org.json4s.{ DefaultFormats, Formats }
import org.scalatest.BeforeAndAfterEach
import org.json4s.native.Serialization.read

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.DurationInt
import PocAdminControllerSpec._
import org.scalatest.prop.TableDrivenPropertyChecks

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
        } yield (employees)
        val employees = await(r, 5.seconds).map(_.datesToIsoFormat)
        employees.size shouldBe 3
        get(EndPoint, headers = Map("authorization" -> token.pocAdmin(pocAdmin1.certifyUserId.value).prepare)) {
          status should equal(200)
          val employeeOut = read[Paginated_OUT[PocEmployee]](body)
          employeeOut.total shouldBe 2
          employeeOut.records shouldBe employees.filter(_.pocId == poc1.id)
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
          val employeeOut = read[Paginated_OUT[PocEmployee]](body)
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
        } yield (employees)
        val employees = await(r, 5.seconds).map(_.datesToIsoFormat)
        employees.size shouldBe 5
        get(
          EndPoint,
          params = Map("pageIndex" -> "1", "pageSize" -> "2"),
          headers =
            Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare)) {
          status should equal(200)
          val employeeOut = read[Paginated_OUT[PocEmployee]](body)
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
        } yield (employees)
        val employees = await(r, 5.seconds).map(_.datesToIsoFormat)
        employees.size shouldBe 3
        get(
          EndPoint,
          params = Map("search" -> "employee 1"),
          headers =
            Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare)) {
          status should equal(200)
          val employeeOut = read[Paginated_OUT[PocEmployee]](body)
          employeeOut.total shouldBe 2
          employeeOut.records shouldBe employees.filter(_.name.startsWith("employee 1"))
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
        } yield (employees)
        val employees = await(r, 5.seconds).map(_.datesToIsoFormat)
        employees.size shouldBe 3
        get(
          EndPoint,
          params = Map("search" -> "employee1"),
          headers =
            Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare)) {
          status should equal(200)
          val employeeOut = read[Paginated_OUT[PocEmployee]](body)
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
        } yield (employees)
        val employees = await(r, 5.seconds).sortBy(_.name).map(_.datesToIsoFormat)
        employees.size shouldBe 3
        get(
          EndPoint,
          params = Map("sortColumn" -> "name", "sortOrder" -> "asc"),
          headers =
            Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare)) {
          status should equal(200)
          val employeeOut = read[Paginated_OUT[PocEmployee]](body)
          employeeOut.total shouldBe 3
          employeeOut.records shouldBe employees
        }
      }
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
        } yield (employees)
        val employees = await(r, 5.seconds).sortBy(_.name).reverse.map(_.datesToIsoFormat)
        employees.size shouldBe 3
        get(
          EndPoint,
          params = Map("sortColumn" -> "name", "sortOrder" -> "desc"),
          headers =
            Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare)) {
          status should equal(200)
          val employeeOut = read[Paginated_OUT[PocEmployee]](body)
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
        } yield (employees)
        val employees =
          await(r, 5.seconds).filter(p => Seq(Pending, Processing).contains(p.status)).map(_.datesToIsoFormat)
        employees.size shouldBe 2
        get(
          EndPoint,
          params = Map("filterColumn[status]" -> "pending,processing"),
          headers =
            Map("authorization" -> token.pocAdmin(pocAdmin.certifyUserId.value).prepare)) {
          status should equal(200)
          val employeeOut = read[Paginated_OUT[PocEmployee]](body)
          employeeOut.total shouldBe 2
          employeeOut.records shouldBe employees
        }
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
  }
}
