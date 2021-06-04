package com.ubirch.services.poc

import com.ubirch.ModelCreationHelper.{ addEmployeeTripleToRepository, createEmployeeTriple }
import com.ubirch.UnitTestBase
import com.ubirch.db.tables.{ PocEmployeeRepositoryMock, PocEmployeeStatusRepositoryMock, PocRepositoryMock }
import com.ubirch.models.pocEmployee.PocEmployeeStatus
import monix.reactive.Observable
import org.scalatest.Assertion

import java.util.UUID
import scala.concurrent.duration.DurationInt

class PocEmployeeCreatorLoopTest extends UnitTestBase {

  "Poc Employee Creation Loop" should {
    "create pending poc employee first after adding it to database and web-ident successful" in {
      withInjector { injector =>
        val loop = injector.get[PocEmployeeCreationLoop]
        val pocTable = injector.get[PocRepositoryMock]
        val employeeTable = injector.get[PocEmployeeRepositoryMock]
        val statusTable = injector.get[PocEmployeeStatusRepositoryMock]
        val (poc, employee, status) = createEmployeeTriple
        employeeTable.createPocEmployee(employee)

        //start process
        val creationLoop = loop.startPocEmployeeCreationLoop(resp => Observable(resp))
        awaitForTwoTicks(creationLoop, 5.seconds)

        // not process because the not poc is stored/found in database yet
        statusTable.getStatus(status.pocEmployeeId).runSyncUnsafe() shouldBe None

        // store objects in database
        addEmployeeTripleToRepository(pocTable, employeeTable, statusTable, poc, employee, status)
        awaitForTwoTicks(creationLoop, 5.seconds)
        val updatedStatus1 = statusTable.getStatus(status.pocEmployeeId).runSyncUnsafe().get
        updatedStatus1.certifyUserCreated shouldBe true

        // store objects in database
        val updatedPoc = poc.copy(employeeGroupId = Some(UUID.randomUUID().toString))
        pocTable.updatePoc(updatedPoc).runSyncUnsafe(1.seconds)

        awaitForTwoTicks(creationLoop, 5.seconds)
        // not process because employee group doesn't exist yet
        val updatedStatus2 = statusTable.getStatus(status.pocEmployeeId).runSyncUnsafe().get
        assertStatusAllTrue(updatedStatus2)
      }
    }
  }

  private def assertStatusAllTrue(status: PocEmployeeStatus): Assertion = {
    assert(status.certifyUserCreated)
    assert(status.keycloakEmailSent)
    assert(status.employeeGroupAssigned)
  }
}
