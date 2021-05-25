package com.ubirch.services.poc

import com.ubirch.ModelCreationHelper.{
  addEmployeeTripleToRepository,
  createEmployeeTriple,
  createPocEmployeeStatusAllTrue
}
import com.ubirch.UnitTestBase
import com.ubirch.db.tables.{ PocEmployeeRepositoryMock, PocEmployeeStatusRepositoryMock, PocRepositoryMock }
import com.ubirch.models.poc.Completed

import java.util.UUID

class PocEmployeeCreatorTest extends UnitTestBase {

  "PocEmployeeCreator" should {
    "create pending poc employee successfully" in {
      withInjector { injector =>
        val creator = injector.get[PocEmployeeCreator]
        val pocTable = injector.get[PocRepositoryMock]
        val employeeTable = injector.get[PocEmployeeRepositoryMock]
        val statusTable = injector.get[PocEmployeeStatusRepositoryMock]

        val (poc, employee, status) = createEmployeeTriple
        val updatedPoc = poc.copy(employeeGroupId = Some(UUID.randomUUID().toString))

        addEmployeeTripleToRepository(pocTable, employeeTable, statusTable, updatedPoc, employee, status)
        employeeTable.getPocEmployee(poc.id).runSyncUnsafe().isDefined shouldBe false
        val result = creator.createPocEmployees().runSyncUnsafe()

        result match {
          case NoWaitingPocEmployee => fail("one poc employee should be found")
          case PocEmployeeCreationMaybeSuccess(list) =>
            assert(list.nonEmpty)
            assert(list.head.isRight)
            val result = list.head.right.get
            val allTrue = createPocEmployeeStatusAllTrue
            val expected =
              allTrue.copy(pocEmployeeId = employee.id, lastUpdated = result.lastUpdated, created = result.created)

            result shouldBe expected
        }

        val newPocEmployee = employeeTable.getPocEmployee(employee.id).runSyncUnsafe()
        assert(newPocEmployee.isDefined)
        assert(newPocEmployee.get.status == Completed)
      }
    }

    "create pending poc employee - after certifyGroupId is created" in {
      withInjector { injector =>
        val creator = injector.get[PocEmployeeCreator]
        val pocTable = injector.get[PocRepositoryMock]
        val employeeTable = injector.get[PocEmployeeRepositoryMock]
        val statusTable = injector.get[PocEmployeeStatusRepositoryMock]

        val (poc, employee, status) = createEmployeeTriple
        addEmployeeTripleToRepository(pocTable, employeeTable, statusTable, poc, employee, status)
        employeeTable.getPocEmployee(poc.id).runSyncUnsafe().isDefined shouldBe false

        // start process
        val result = creator.createPocEmployees().runSyncUnsafe()

        result match {
          case NoWaitingPocEmployee                  => fail("one poc employee should be found")
          case PocEmployeeCreationMaybeSuccess(list) => assert(list.head.isLeft)
        }

        val updatedStatus = statusTable.getStatus(employee.id).runSyncUnsafe()
        val expected = status.copy(
          certifyUserCreated = true,
          errorMessage = Some(s"employeeGroupId is missing in poc ${poc.id}")
        )
        assert(updatedStatus.isDefined)
        updatedStatus.get shouldBe expected

        pocTable.updatePoc(poc.copy(employeeGroupId = Some(UUID.randomUUID().toString))).runSyncUnsafe()

        // restart process
        val secondResult = creator.createPocEmployees().runSyncUnsafe()

        secondResult match {
          case NoWaitingPocEmployee => fail("one poc employee should be found")
          case PocEmployeeCreationMaybeSuccess(list) =>
            assert(list.nonEmpty)
            assert(list.head.isRight)
            val result = list.head.right.get
            val allTrue = createPocEmployeeStatusAllTrue
            val expectedFinal =
              allTrue.copy(
                pocEmployeeId = employee.id,
                lastUpdated = result.lastUpdated,
                created = result.created,
                errorMessage = Some(s"employeeGroupId is missing in poc ${poc.id}")
              )
            result shouldBe expectedFinal
        }

        val newPocEmployee = employeeTable.getPocEmployee(employee.id).runSyncUnsafe()
        assert(newPocEmployee.isDefined)
        assert(newPocEmployee.get.status == Completed)
      }
    }
  }

}
