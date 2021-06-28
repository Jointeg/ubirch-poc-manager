package com.ubirch.e2e.db

import com.ubirch.ModelCreationHelper.createPoc
import com.ubirch.db.tables.{ PocLogoRepository, PocRepository }
import com.ubirch.e2e.E2ETestBase
import com.ubirch.models.poc.PocLogo
import com.ubirch.models.tenant.TenantName
import monix.eval.Task

import java.io.File
import java.nio.file.Files

class PocLogoTableTest extends E2ETestBase {
  "PocLogoTable" should {
    "be able to store and retrieve data in DB" in {
      withInjector { injector =>
        val pocRepo = injector.get[PocRepository]
        val pocLogoRepo = injector.get[PocLogoRepository]
        val file = new File("src/test/resources/img/1024_1024.jpg")
        val imgBytes = Files.readAllBytes(file.toPath)
        val poc = createPoc(tenantName = TenantName("tenant"))
        val res = (for {
          _ <- pocRepo.createPoc(poc)
          pocLogo <- Task(PocLogo.create(poc.id, imgBytes)).map(_.right.value)
          _ <- pocLogoRepo.createPocLogo(pocLogo)
          pocLogoFromDB <- pocLogoRepo.getPocLogoById(poc.id)
        } yield pocLogoFromDB).runSyncUnsafe()

        assert(res.isDefined)
        assert(res.get.pocId == poc.id)
      }
    }
  }
}
