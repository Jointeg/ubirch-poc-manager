package com.ubirch.e2e.db

import com.ubirch.ModelCreationHelper.{ createPocAndEmployeeAndStatus, createPocEmployeeStatus }
import com.ubirch.db.tables.{ PocEmployeeRepository, PocEmployeeStatusRepository, PocRepository }
import com.ubirch.e2e.E2ETestBase
import org.postgresql.util.PSQLException

import scala.concurrent.duration.DurationInt

class PocEmployeeStatusTableTest extends E2ETestBase {

  "PocEmployeeStatusTable" should {
    "be able to store and retrieve data in DB" in {
      withInjector { injector =>
        val repo = injector.get[PocEmployeeStatusRepository]
        val employeeRepo = injector.get[PocEmployeeRepository]
        val pocRepo = injector.get[PocRepository]
        val (poc, employee, status) = createPocAndEmployeeAndStatus
        val res1 = for {
          _ <- pocRepo.createPoc(poc)
          _ <- employeeRepo.createPocEmployee(employee)
          _ <- repo.createStatus(status)
          data <- repo.getStatus(status.pocEmployeeId)
        } yield {
          data
        }
        val storedStatus = await(res1, 5.seconds).get
        storedStatus shouldBe status.copy(lastUpdated = storedStatus.lastUpdated)
      }
    }

    "fail to store when poc employee doesn't exist yet" in {
      withInjector { injector =>
        val repo = injector.get[PocEmployeeStatusRepository]
        val status = createPocEmployeeStatus()
        assertThrows[PSQLException](await(repo.createStatus(status), 5.seconds))
      }
    }

    "fail when same PocEmployeeStatus is tried to be stored twice, when primary key is the same" in {
      withInjector { injector =>
        val repo = injector.get[PocEmployeeStatusRepository]
        val employeeRepo = injector.get[PocEmployeeRepository]
        val pocRepo = injector.get[PocRepository]
        val (poc, employee, status) = createPocAndEmployeeAndStatus
        val res = for {
          _ <- pocRepo.createPoc(poc)
          _ <- employeeRepo.createPocEmployee(employee)
          _ <- repo.createStatus(status)
          data <- repo.createStatus(status)
        } yield {
          data
        }
        assertThrows[PSQLException](await(res, 5.seconds))
      }
    }

    "be able to store and update data in DB" in {
      withInjector { injector =>
        val repo = injector.get[PocEmployeeStatusRepository]
        val employeeRepo = injector.get[PocEmployeeRepository]
        val pocRepo = injector.get[PocRepository]
        val (poc, employee, status) = createPocAndEmployeeAndStatus
        val updatedStatus = status.copy(certifyUserCreated = true)
        val res1 = for {
          _ <- pocRepo.createPoc(poc)
          _ <- employeeRepo.createPocEmployee(employee)
          _ <- repo.createStatus(status)
          _ <- repo.updateStatus(updatedStatus)
          data <- repo.getStatus(status.pocEmployeeId)
        } yield {
          data
        }
        val storedStatus = await(res1, 5.seconds).get
        storedStatus shouldBe updatedStatus.copy(lastUpdated = storedStatus.lastUpdated)
      }
    }

    "be able to store and delete data in DB" in {
      withInjector { injector =>
        val repo = injector.get[PocEmployeeStatusRepository]
        val employeeRepo = injector.get[PocEmployeeRepository]
        val pocRepo = injector.get[PocRepository]
        val (poc, employee, status) = createPocAndEmployeeAndStatus
        val res1 = for {
          _ <- pocRepo.createPoc(poc)
          _ <- employeeRepo.createPocEmployee(employee)
          _ <- repo.createStatus(status)
          data <- repo.getStatus(status.pocEmployeeId)
        } yield {
          data
        }
        val storedStatus = await(res1, 5.seconds).get
        storedStatus shouldBe status.copy(lastUpdated = storedStatus.lastUpdated)
        val res2 = for {
          _ <- repo.deleteStatus(status.pocEmployeeId)
          data <- repo.getStatus(status.pocEmployeeId)
        } yield {
          data
        }
        await(res2, 5.seconds) shouldBe None
      }
    }

  }

}
