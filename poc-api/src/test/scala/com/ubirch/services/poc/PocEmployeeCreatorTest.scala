package com.ubirch.services.poc

import com.ubirch.ModelCreationHelper.{
  addEmployeeTripleToRepository,
  createEmployeeTriple,
  createPocEmployeeStatusAllTrue
}
import com.ubirch.UnitTestBase
import com.ubirch.db.tables.{ PocEmployeeRepository, PocEmployeeStatusRepository, PocRepository }
import com.ubirch.models.poc.Completed

import java.util.UUID

class PocEmployeeCreatorTest extends UnitTestBase {

  "PocEmployeeCreator" should {
    "create pending poc employee successfully" in {
      withInjector { injector =>
        val creator = injector.get[PocEmployeeCreator]
        val pocTable = injector.get[PocRepository]
        val employeeTable = injector.get[PocEmployeeRepository]
        val statusTable = injector.get[PocEmployeeStatusRepository]

        val (poc, employee, status) = createEmployeeTriple
        val updatedPoc = poc.copy(employeeGroupId = Some(UUID.randomUUID().toString))

        addEmployeeTripleToRepository(pocTable, employeeTable, statusTable, updatedPoc, employee, status)
        employeeTable.getPocEmployee(poc.id).runSyncUnsafe().isDefined shouldBe false
        creator.createPocEmployees().runSyncUnsafe()

        val updatedStatus = statusTable.getStatus(employee.id).runSyncUnsafe().value
        val allTrue = createPocEmployeeStatusAllTrue
        val expected =
          allTrue.copy(
            pocEmployeeId = employee.id,
            lastUpdated = updatedStatus.lastUpdated,
            created = updatedStatus.created)
        updatedStatus shouldBe expected

        val newPocEmployee = employeeTable.getPocEmployee(employee.id).runSyncUnsafe()
        assert(newPocEmployee.isDefined)
        assert(newPocEmployee.get.status == Completed)
      }
    }

    "create pending poc employee - after certifyGroupId is created" in {
      withInjector { injector =>
        val creator = injector.get[PocEmployeeCreator]
        val pocTable = injector.get[PocRepository]
        val employeeTable = injector.get[PocEmployeeRepository]
        val statusTable = injector.get[PocEmployeeStatusRepository]

        val (poc, employee, status) = createEmployeeTriple
        addEmployeeTripleToRepository(pocTable, employeeTable, statusTable, poc, employee, status)
        employeeTable.getPocEmployee(poc.id).runSyncUnsafe().isDefined shouldBe false

        // start process
        creator.createPocEmployees().runSyncUnsafe()

        val updatedStatus = statusTable.getStatus(employee.id).runSyncUnsafe()
        val expected = status.copy(
          certifyUserCreated = true,
          errorMessage = Some(s"employeeGroupId is missing in poc ${poc.id}")
        )
        assert(updatedStatus.isDefined)
        updatedStatus.get shouldBe expected

        pocTable.updatePoc(poc.copy(employeeGroupId = Some(UUID.randomUUID().toString))).runSyncUnsafe()

        // restart process
        creator.createPocEmployees().runSyncUnsafe()

        val updatedStatus2 = statusTable.getStatus(employee.id).runSyncUnsafe().value
        val allTrue = createPocEmployeeStatusAllTrue
        val expectedFinal =
          allTrue.copy(
            pocEmployeeId = employee.id,
            lastUpdated = updatedStatus2.lastUpdated,
            created = updatedStatus2.created
          )
        updatedStatus2 shouldBe expectedFinal

        val newPocEmployee = employeeTable.getPocEmployee(employee.id).runSyncUnsafe()
        assert(newPocEmployee.isDefined)
        assert(newPocEmployee.get.status == Completed)
      }
    }
  }

}
