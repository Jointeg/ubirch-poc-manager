package com.ubirch.e2e.db

import com.ubirch.db.tables.PocStatusRepository
import com.ubirch.e2e.E2ETestBase
import com.ubirch.models.poc.PocStatus
import org.postgresql.util.PSQLException

import java.util.UUID
import scala.concurrent.duration.DurationInt

class PocStatusTableTest extends E2ETestBase {

  private val pocStatus: PocStatus = PocStatus(
    UUID.fromString("7ff87ef4-3b6a-4ad3-a415-31fa10b6fbea"),
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

        val res1 = for {
          _ <- repo.createPocStatus(pocStatus)
          data <- repo.getPocStatus(pocStatus.pocId)
        } yield {
          data
        }
        val storedStatus = await(res1, 5.seconds).get
        storedStatus shouldBe pocStatus.copy(lastUpdated = storedStatus.lastUpdated, created = storedStatus.created)
      }
    }

    "fail when same PocStatus is tried to be stored twice, when primary key is the same" in {
      withInjector { injector =>
        val repo = injector.get[PocStatusRepository]
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
  }

}
