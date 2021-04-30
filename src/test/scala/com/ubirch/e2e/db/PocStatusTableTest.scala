package com.ubirch.e2e.db

import com.ubirch.db.tables.PocStatusRepository
import com.ubirch.e2e.E2ETestBase
import com.ubirch.models.poc.PocStatus
import org.postgresql.util.PSQLException

import java.util.UUID
import scala.concurrent.duration.DurationInt

class PocStatusTableTest extends E2ETestBase {

  private def createPocStatus(id: UUID = UUID.randomUUID()): PocStatus = PocStatus(
    id,
    validDataSchemaGroup = true,
    clientCertRequired = false,
    clientCertDownloaded = None,
    clientCertProvided = None,
    logoRequired = false,
    logoReceived = None,
    logoStored = None
  )

  "PocStatusTable" should {
    "be able to store and retrieve data in DB" in {
      withInjector { injector =>
        val repo = injector.get[PocStatusRepository]
        val pocStatus = createPocStatus()
        val res1 = for {
          _ <- repo.createPocStatus(pocStatus)
          data <- repo.getPocStatus(pocStatus.pocId)
        } yield {
          data
        }
        val storedStatus = await(res1, 5.seconds).get
        storedStatus shouldBe pocStatus.copy(lastUpdated = storedStatus.lastUpdated)
      }
    }

    "fail when same PocStatus is tried to be stored twice, when primary key is the same" in {
      withInjector { injector =>
        val repo = injector.get[PocStatusRepository]
        val pocStatus = createPocStatus()
        val res = for {
          _ <- repo.createPocStatus(pocStatus)
          _ <- repo.createPocStatus(pocStatus)
          data <- repo.getPocStatus(pocStatus.pocId)
        } yield {
          data
        }
        assertThrows[PSQLException](await(res, 5.seconds))
      }
    }

    "be able to store and update data in DB" in {
      withInjector { injector =>
        val repo = injector.get[PocStatusRepository]
        val pocStatus = createPocStatus()
        val updatedStatus = pocStatus.copy(userRealmRoleCreated = true)

        val res1 = for {
          _ <- repo.createPocStatus(pocStatus)
          _ <- repo.updatePocStatus(updatedStatus)
          data <- repo.getPocStatus(pocStatus.pocId)
        } yield {
          data
        }
        val storedStatus = await(res1, 5.seconds).get
        storedStatus shouldBe updatedStatus.copy(lastUpdated = storedStatus.lastUpdated)
      }
    }

    "be able to store and delete data in DB" in {
      withInjector { injector =>
        val repo = injector.get[PocStatusRepository]
        val pocStatus = createPocStatus()

        val res1 = for {
          _ <- repo.createPocStatus(pocStatus)
          data <- repo.getPocStatus(pocStatus.pocId)
        } yield {
          data
        }
        val storedStatus = await(res1, 5.seconds).get
        storedStatus shouldBe pocStatus.copy(lastUpdated = storedStatus.lastUpdated)
        val res2 = for {
          _ <- repo.deletePocStatus(pocStatus.pocId)
          data <- repo.getPocStatus(pocStatus.pocId)
        } yield {
          data
        }
        await(res2, 5.seconds) shouldBe None
      }
    }

  }

}
