package com.ubirch.e2e.db

import com.ubirch.ModelCreationHelper.{ createPocEmployeeStatus, createTenantPocEmployeeAndStatus }
import com.ubirch.db.tables.{ PocEmployeeRepository, PocEmployeeStatusRepository, PocRepository, TenantRepository }
import com.ubirch.e2e.{ E2EInjectorHelperImpl, E2ETestBase }
import org.postgresql.util.PSQLException

import scala.concurrent.duration.DurationInt

class PocEmployeeStatusTableTest extends E2ETestBase {

  val (tenant, poc, employee, employeeStatus) = createTenantPocEmployeeAndStatus
  def beforeEach(injector: E2EInjectorHelperImpl): Unit = {
    val tenantRepo = injector.get[TenantRepository]
    val pocRepo = injector.get[PocRepository]
    val employeeRepo = injector.get[PocEmployeeRepository]
    val r = for {
      _ <- tenantRepo.createTenant(tenant)
      _ <- pocRepo.createPoc(poc)
      result <- employeeRepo.createPocEmployee(employee)
    } yield result
    await(r, 2.seconds)
  }

  "PocEmployeeStatusTable" should {
    "be able to store and retrieve data in DB" in {
      withInjector { injector =>
        beforeEach(injector)
        val repo = injector.get[PocEmployeeStatusRepository]
        val res1 = for {
          _ <- repo.createStatus(employeeStatus)
          data <- repo.getStatus(employeeStatus.pocEmployeeId)
        } yield {
          data
        }
        val storedStatus = await(res1, 5.seconds).get
        storedStatus shouldBe employeeStatus.copy(lastUpdated = storedStatus.lastUpdated)
      }
    }

    "fail to store when poc employee doesn't exist yet" in {
      withInjector { injector =>
        val repo = injector.get[PocEmployeeStatusRepository]
        val employeeStatus = createPocEmployeeStatus()
        assertThrows[PSQLException](await(repo.createStatus(employeeStatus), 5.seconds))
      }
    }

    "fail when same PocEmployeeStatus is tried to be stored twice, when primary key is the same" in {
      withInjector { injector =>
        beforeEach(injector)
        val repo = injector.get[PocEmployeeStatusRepository]
        val res = for {
          _ <- repo.createStatus(employeeStatus)
          data <- repo.createStatus(employeeStatus)
        } yield {
          data
        }
        assertThrows[PSQLException](await(res, 5.seconds))
      }
    }

    "be able to store and update data in DB" in {
      withInjector { injector =>
        beforeEach(injector)
        val repo = injector.get[PocEmployeeStatusRepository]
        val updatedStatus = employeeStatus.copy(certifyUserCreated = true)
        val res1 = for {
          _ <- repo.createStatus(employeeStatus)
          _ <- repo.updateStatus(updatedStatus)
          data <- repo.getStatus(employeeStatus.pocEmployeeId)
        } yield {
          data
        }
        val storedStatus = await(res1, 5.seconds).get
        storedStatus shouldBe updatedStatus.copy(lastUpdated = storedStatus.lastUpdated)
      }
    }

    "be able to store and delete data in DB" in {
      withInjector { injector =>
        beforeEach(injector)
        val repo = injector.get[PocEmployeeStatusRepository]
        val res1 = for {
          _ <- repo.createStatus(employeeStatus)
          data <- repo.getStatus(employeeStatus.pocEmployeeId)
        } yield {
          data
        }
        val storedStatus = await(res1, 5.seconds).get
        storedStatus shouldBe employeeStatus.copy(lastUpdated = storedStatus.lastUpdated)
        val res2 = for {
          _ <- repo.deleteStatus(employeeStatus.pocEmployeeId)
          data <- repo.getStatus(employeeStatus.pocEmployeeId)
        } yield {
          data
        }
        await(res2, 5.seconds) shouldBe None
      }
    }
  }

}
