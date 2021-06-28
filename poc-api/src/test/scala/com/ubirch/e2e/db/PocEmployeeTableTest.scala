package com.ubirch.e2e.db

import com.ubirch.ModelCreationHelper.{ createPocEmployee, createTenantPocAndEmployee }
import com.ubirch.db.tables.{ PocEmployeeRepository, PocRepository, TenantRepository }
import com.ubirch.e2e.{ E2EInjectorHelperImpl, E2ETestBase }
import com.ubirch.models.poc.Completed
import com.ubirch.models.tenant.{ TenantId, TenantName }
import org.postgresql.util.PSQLException

import java.util.UUID
import scala.concurrent.duration.DurationInt

class PocEmployeeTableTest extends E2ETestBase {

  val (tenant, poc, employee) = createTenantPocAndEmployee

  def beforeEach(injector: E2EInjectorHelperImpl): Unit = {
    val pocRepo = injector.get[PocRepository]
    val tenantRepo = injector.get[TenantRepository]
    await(tenantRepo.createTenant(tenant), 2.seconds)
    await(pocRepo.createPoc(poc), 2.seconds)
  }

  "PocTable" should {
    "be able to store and retrieve data in DB" in {
      withInjector { injector: E2EInjectorHelperImpl =>
        beforeEach(injector)
        val repo = injector.get[PocEmployeeRepository]
        val res = for {
          _ <- repo.createPocEmployee(employee)
          data <- repo.getPocEmployee(employee.id)
        } yield data
        val result = await(res, 5.seconds).get
        result shouldBe employee.copy(lastUpdated = result.lastUpdated)
      }
    }

    "fail to store when poc doesn't exist yet" in {
      withInjector { injector =>
        val repo = injector.get[PocEmployeeRepository]
        val employee = createPocEmployee()
        assertThrows[PSQLException](await(repo.createPocEmployee(employee), 5.seconds))
      }
    }

    "fail when same PocEmployee is tried to be stored twice, when unique email constraint is violated" in {
      withInjector { injector =>
        beforeEach(injector)
        val repo = injector.get[PocEmployeeRepository]
        val res = for {
          _ <- repo.createPocEmployee(employee)
          data <- repo.createPocEmployee(employee.copy(UUID.randomUUID()))
        } yield {
          data
        }
        assertThrows[PSQLException](await(res, 5.seconds))
      }
    }

    "fail when same PocEmployee is tried to be stored twice, when only primary key is the same" in {
      withInjector { injector =>
        beforeEach(injector)
        val repo = injector.get[PocEmployeeRepository]
        val res = for {
          _ <- repo.createPocEmployee(employee)
          data <- repo.createPocEmployee(employee.copy(name = "Svenja"))
        } yield {
          data
        }
        assertThrows[PSQLException](await(res, 5.seconds))
      }
    }

    "be able to store and update data in DB" in {
      withInjector { injector =>
        beforeEach(injector)
        val repo = injector.get[PocEmployeeRepository]
        val updatedPocEmployee = employee.copy(name = "Lisa")
        val res = for {
          _ <- repo.createPocEmployee(employee)
          _ <- repo.updatePocEmployee(updatedPocEmployee)
          data <- repo.getPocEmployee(employee.id)
        } yield {
          data
        }
        val storedPocEmployee = await(res, 5.seconds).get
        storedPocEmployee shouldBe updatedPocEmployee.copy(lastUpdated = storedPocEmployee.lastUpdated)
      }
    }

    "be able to store and delete data in DB" in {
      withInjector { injector =>
        beforeEach(injector)
        val repo = injector.get[PocEmployeeRepository]
        val res1 = for {
          _ <- repo.createPocEmployee(employee)
          data <- repo.getPocEmployee(employee.id)
        } yield {
          data
        }
        val storedPocEmployee = await(res1, 5.seconds).get
        storedPocEmployee shouldBe employee.copy(lastUpdated = storedPocEmployee.lastUpdated)
        val res2 = for {
          _ <- repo.deletePocEmployee(employee.id)
          data <- repo.getPocEmployee(employee.id)
        } yield {
          data
        }
        await(res2, 5.seconds) shouldBe None
      }
    }

    "get all by tenantId" in {
      withInjector { injector =>
        beforeEach(injector)
        val repo = injector.get[PocEmployeeRepository]
        val tenantRepo = injector.get[TenantRepository]
        val tenantName2 = TenantName("tenant2")
        val tenant2 = tenant.copy(TenantId(tenantName2), tenantName2)
        val employee2 = createPocEmployee(pocId = poc.id)
        val employeeDiffTenant = createPocEmployee(pocId = poc.id).copy(tenantId = tenant2.id)

        val res = for {
          _ <- tenantRepo.createTenant(tenant2)
          _ <- repo.createPocEmployee(employee)
          _ <- repo.createPocEmployee(employee2)
          _ <- repo.createPocEmployee(employeeDiffTenant)
          data <- repo.getPocEmployeesByTenantId(employee.tenantId)
        } yield {
          data
        }
        val allEmployeesByTenant = await(res, 5.seconds)
        allEmployeesByTenant.size shouldBe 2
        allEmployeesByTenant.exists(_.id == employee.id) shouldBe true
        allEmployeesByTenant.exists(_.id == employee2.id) shouldBe true
      }
    }

    "get all uncompleted" in {
      withInjector { injector =>
        beforeEach(injector)
        val repo = injector.get[PocEmployeeRepository]
        val employee2 = createPocEmployee(pocId = poc.id)
        val employeeCompleted = createPocEmployee(pocId = poc.id).copy(status = Completed)

        val res = for {
          _ <- repo.createPocEmployee(employee)
          _ <- repo.createPocEmployee(employee2)
          _ <- repo.createPocEmployee(employeeCompleted)
          ids <- repo.getUncompletedPocEmployeesIds()
        } yield {
          ids
        }
        val allEmployeesByTenant = await(res, 5.seconds)
        allEmployeesByTenant.size shouldBe 2
        allEmployeesByTenant.contains(employee.id) shouldBe true
        allEmployeesByTenant.contains(employee2.id) shouldBe true
      }
    }
  }

}
