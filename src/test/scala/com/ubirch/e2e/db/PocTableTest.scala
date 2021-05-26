package com.ubirch.e2e.db

import com.ubirch.ModelCreationHelper.{ createPoc, createPocStatus }
import com.ubirch.db.tables.{ PocRepository, PocStatusRepository }
import com.ubirch.e2e.E2ETestBase
import com.ubirch.models.poc._
import com.ubirch.models.tenant.{ TenantId, TenantName }
import org.postgresql.util.PSQLException

import java.util.UUID
import scala.concurrent.duration.DurationInt

class PocTableTest extends E2ETestBase {

  "PocTable" should {
    "be able to store and retrieve data in DB" in {
      withInjector { injector =>
        val repo = injector.get[PocRepository]
        val poc = createPoc(tenantName = TenantName("tenant"))
        val res = for {
          _ <- repo.createPoc(poc)
          data <- repo.getPoc(poc.id)
        } yield data
        val result = await(res, 5.seconds).get
        result shouldBe poc.copy(lastUpdated = result.lastUpdated)
      }
    }

    "fail when same Poc is tried to be stored twice, when unique constraint is violated" in {
      withInjector { injector =>
        val repo = injector.get[PocRepository]
        val poc = createPoc(tenantName = TenantName("tenant"))
        val res = for {
          _ <- repo.createPoc(poc)
          _ <- repo.createPoc(poc.copy(UUID.randomUUID()))
          data <- repo.getPoc(poc.id)
        } yield {
          data
        }
        assertThrows[PSQLException](await(res, 5.seconds))
      }
    }

    "fail when same Poc is tried to be stored twice, when only primary key is the same" in {
      withInjector { injector =>
        val repo = injector.get[PocRepository]
        val poc = createPoc(tenantName = TenantName("tenant"))
        val res = for {
          _ <- repo.createPoc(poc)
          _ <- repo.createPoc(poc.copy(pocName = "x"))
          data <- repo.getPoc(poc.id)
        } yield {
          data
        }
        assertThrows[PSQLException](await(res, 5.seconds))
      }
    }

    "be able to store and update data in DB" in {
      withInjector { injector =>
        val repo = injector.get[PocRepository]
        val poc = createPoc(tenantName = TenantName("tenant"))
        val updatedPoc = poc.copy(pocName = "xxx")

        val res1 = for {
          _ <- repo.createPoc(poc)
          _ <- repo.updatePoc(updatedPoc)
          data <- repo.getPoc(poc.id)
        } yield {
          data
        }
        val storedPoc = await(res1, 5.seconds).get
        storedPoc shouldBe updatedPoc.copy(lastUpdated = storedPoc.lastUpdated)
      }
    }

    "be able to store and delete data in DB" in {
      withInjector { injector =>
        val repo = injector.get[PocRepository]
        val poc = createPoc(tenantName = TenantName("tenant"))
        val res1 = for {
          _ <- repo.createPoc(poc)
          data <- repo.getPoc(poc.id)
        } yield {
          data
        }
        val storedPoc = await(res1, 5.seconds).get
        storedPoc shouldBe poc.copy(lastUpdated = storedPoc.lastUpdated)
        val res2 = for {
          _ <- repo.deletePoc(poc.id)
          data <- repo.getPoc(poc.id)
        } yield {
          data
        }
        await(res2, 5.seconds) shouldBe None
      }
    }

    "store Poc and Status at once" in {
      withInjector { injector =>
        val pocRepo = injector.get[PocRepository]
        val statusRepo = injector.get[PocStatusRepository]
        val poc = createPoc(tenantName = TenantName("tenant"))
        val pocStatus = createPocStatus(poc.id)

        val poc1 = poc.copy(UUID.randomUUID(), pocName = "new name")
        val pocStatus1 = pocStatus.copy(poc1.id)
        val res = for {
          _ <- pocRepo.createPocAndStatus(poc1, pocStatus1)
          data <- pocRepo.getPoc(poc1.id)
          status <- statusRepo.getPocStatus(poc1.id)
        } yield {
          (status, data)
        }
        res.runSyncUnsafe(5.seconds) match {
          case (Some(pocStatusOpt: PocStatus), Some(pocOpt: Poc)) =>
            pocOpt.copy(lastUpdated = poc1.lastUpdated) shouldBe poc1
            pocStatusOpt.copy(lastUpdated = pocStatus1.lastUpdated) shouldBe pocStatus1
          case _ => assert(false)
        }
      }
    }

    //Todo: it works but have to create a proper test for this
    //    "store Poc and Status and revoke transaction on exception thrown" in {
    //      withInjector { injector =>
    //
    //        val pocRepo: PocRepository = injector.get[PocRepository]
    //
    //        val statusRepo = injector.get[PocStatusRepository]
    //        val res = for {
    //          r <- pocRepo.createPocAndStatus(poc, pocStatus)
    //        } yield r
    //
    //        assertThrows[Exception](await(res, 5.seconds))
    //        val res1 = for {
    //          data <- pocRepo.getPoc(poc.id)
    //          pocStatus <- statusRepo.getPocStatus(poc.id)
    //        } yield (pocStatus, data)
    //
    //        await(res1, 5.seconds) shouldBe (None, None)
    //      }
    //    }

    "get all by tenantId" in {
      withInjector { injector =>
        val pocRepo = injector.get[PocRepository]
        val poc1 = createPoc()
        val poc2 = createPoc()
        val pocByDiffTenant = createPoc().copy(tenantId = TenantId(TenantName("different tenant")))

        val res = for {
          _ <- pocRepo.createPoc(poc1)
          _ <- pocRepo.createPoc(poc2)
          _ <- pocRepo.createPoc(pocByDiffTenant)
          data <- pocRepo.getAllPocsByTenantId(poc1.tenantId)
        } yield {
          data
        }
        val allEmployeesByTenant = await(res, 5.seconds)
        allEmployeesByTenant.size shouldBe 2
        allEmployeesByTenant.exists(_.id == poc1.id) shouldBe true
        allEmployeesByTenant.exists(_.id == poc2.id) shouldBe true
      }
    }

    "get all uncompleted" in {
      withInjector { injector =>
        val pocRepo = injector.get[PocRepository]
        val poc1 = createPoc()
        val poc2 = createPoc()
        val pocByDiffTenant = createPoc().copy(status = Completed)

        val res = for {
          _ <- pocRepo.createPoc(poc1)
          _ <- pocRepo.createPoc(poc2)
          _ <- pocRepo.createPoc(pocByDiffTenant)
          data <- pocRepo.getAllUncompletedPocs()
        } yield {
          data
        }
        val allEmployeesByTenant = await(res, 5.seconds)
        allEmployeesByTenant.size shouldBe 2
        allEmployeesByTenant.exists(_.id == poc1.id) shouldBe true
        allEmployeesByTenant.exists(_.id == poc2.id) shouldBe true
      }
    }
  }

}
