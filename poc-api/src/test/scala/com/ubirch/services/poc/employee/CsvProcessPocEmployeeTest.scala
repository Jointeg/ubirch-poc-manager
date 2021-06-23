package com.ubirch.services.poc.employee

import com.ubirch.ModelCreationHelper.{ createPocAdmin, createTenant }
import com.ubirch.db.tables.{ PocAdminTable, TenantRepository, TenantTable }
import com.ubirch.{ InjectorHelper, UnitTestBase }
import com.ubirch.models.poc._
import com.ubirch.models.tenant.{ Tenant, TenantId }
import com.ubirch.services.poc.util.CsvConstants.pocEmployeeHeaderLine

import java.util.UUID
import scala.concurrent.duration.DurationInt

class CsvProcessPocEmployeeTest extends UnitTestBase {

  "CsvProcessPocEmployee" should {
    for (state <- List(Pending, Processing)) {
      s"fail if the PoC admin is in $state state" in {
        withInjector { injector =>
          val csvProcessPocEmployee = injector.get[CsvProcessPocEmployee]
          val pocAdmin =
            createPocAdmin(
              pocId = UUID.randomUUID(),
              tenantId = TenantId.unsafeApply(UUID.randomUUID().toString),
              certifyUserId = Some(UUID.randomUUID()),
              status = state)

          val result = await(csvProcessPocEmployee.createListOfPocEmployees("", pocAdmin), 5.seconds)
          result shouldBe Left(PocAdminNotInCompletedStatus(pocAdmin.id))
        }
      }
    }

    "fail if the tenant assigned to PoC Admin can't be found" in {
      withInjector { injector =>
        val csvProcessPocEmployee = injector.get[CsvProcessPocEmployee]
        val unknownTenantId = TenantId.unsafeApply(UUID.randomUUID().toString)
        val pocAdmin =
          createPocAdmin(
            pocId = UUID.randomUUID(),
            tenantId = unknownTenantId,
            certifyUserId = Some(UUID.randomUUID()),
            status = Completed)

        val result = await(csvProcessPocEmployee.createListOfPocEmployees("", pocAdmin), 5.seconds)
        result shouldBe Left(UnknownTenant(unknownTenantId))
      }
    }

    "fail if the CSV is empty and return the error" in {
      withInjector { injector =>
        val csvProcessPocEmployee = injector.get[CsvProcessPocEmployee]
        val tenantRepository = injector.get[TenantRepository]
        val tenant = createTenant()
        val pocAdmin =
          createPocAdmin(
            pocId = UUID.randomUUID(),
            tenantId = tenant.id,
            certifyUserId = Some(UUID.randomUUID()),
            status = Completed)
        await(tenantRepository.createTenant(tenant), 5.seconds)
        val result = await(csvProcessPocEmployee.createListOfPocEmployees("", pocAdmin), 5.seconds)
        result shouldBe Left(CsvContainedErrors(
          """first_name;last_name;email
            |something unexpected went wrong parsing the csv""".stripMargin))
      }
    }

    "report all errors in the CSV file" in {
      val badCSV =
        s"""$pocEmployeeHeaderLine
           |firstName2,lastName2,valid2@email.com
           |firstName3;lastName3;
           |""".stripMargin

      withInjector { injector =>
        val csvProcessPocEmployee = injector.get[CsvProcessPocEmployee]
        val tenantRepository = injector.get[TenantRepository]
        val tenant = createTenant()
        val pocAdmin =
          createPocAdmin(
            pocId = UUID.randomUUID(),
            tenantId = tenant.id,
            certifyUserId = Some(UUID.randomUUID()),
            status = Completed)
        await(tenantRepository.createTenant(tenant), 5.seconds)
        val result = await(csvProcessPocEmployee.createListOfPocEmployees(badCSV, pocAdmin), 5.seconds)
        result shouldBe Left(CsvContainedErrors(
          """|first_name;last_name;email
             |firstName2,lastName2,valid2@email.com;the number of column 1 is invalid. should be 3.
             |firstName3;lastName3;;the number of column 2 is invalid. should be 3.""".stripMargin
        ))
      }
    }

    "fail if the header in CSV is missing" in {
      val badCSV =
        s"""|firstName2,lastName2,valid2@email.com
            |firstName3;lastName3;
            |""".stripMargin

      withInjector { injector =>
        val csvProcessPocEmployee = injector.get[CsvProcessPocEmployee]
        val tenantRepository = injector.get[TenantRepository]
        val tenant = createTenant()
        val pocAdmin =
          createPocAdmin(
            pocId = UUID.randomUUID(),
            tenantId = tenant.id,
            certifyUserId = Some(UUID.randomUUID()),
            status = Completed)
        await(tenantRepository.createTenant(tenant), 5.seconds)
        val result = await(csvProcessPocEmployee.createListOfPocEmployees(badCSV, pocAdmin), 5.seconds)
        result shouldBe Left(HeaderParsingError("the number of headers 1 is invalid. should be 3."))
      }
    }
  }
}
