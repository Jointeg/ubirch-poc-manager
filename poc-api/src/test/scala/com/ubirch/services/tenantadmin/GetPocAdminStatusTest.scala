package com.ubirch.services.tenantadmin

import com.ubirch.ModelCreationHelper.{ createPoc, createPocAdmin, createPocAdminStatus, createTenant }
import com.ubirch.UnitTestBase
import com.ubirch.db.tables.{ PocAdminRepository, PocAdminStatusRepository, PocRepository, TenantRepository }
import com.ubirch.models.tenant.GetPocAdminStatusResponse
import com.ubirch.services.tenantadmin.GetPocAdminStatusErrors.{
  PocAdminAssignedToDifferentTenant,
  PocAdminNotFound,
  PocAdminStatusNotFound
}

import java.util.UUID
import scala.concurrent.duration.DurationInt

class GetPocAdminStatusTest extends UnitTestBase {

  "GetPocAdminStatus functionality" should {
    "retrieve status for given PoC Admin" in {
      withInjector { injector =>
        val pocId = UUID.randomUUID()
        val pocTable = injector.get[PocRepository]
        val pocAdminTable = injector.get[PocAdminRepository]
        val pocAdminStatusTable = injector.get[PocAdminStatusRepository]
        val tenantTable = injector.get[TenantRepository]
        val tenant = createTenant()
        val poc = createPoc(pocId, tenant.tenantName)
        val pocAdmin = createPocAdmin(pocId = poc.id, tenantId = tenant.id)
        val pocAdminStatus = createPocAdminStatus(pocAdmin, poc)
        val r = for {
          _ <- tenantTable.createTenant(tenant)
          _ <- pocTable.createPoc(poc)
          _ <- pocAdminTable.createPocAdmin(pocAdmin)
          _ <- pocAdminStatusTable.createStatus(pocAdminStatus)
        } yield ()
        await(r, 5.seconds)

        val tenantAdminService = injector.get[TenantAdminService]
        val maybePocAdminStatus = await(tenantAdminService.getPocAdminStatus(tenant, pocAdmin.id), 5.seconds)
        maybePocAdminStatus shouldBe Right(GetPocAdminStatusResponse(
          pocAdminStatus.webIdentRequired,
          pocAdminStatus.webIdentInitiated,
          pocAdminStatus.webIdentSuccess,
          pocAdminStatus.certifyUserCreated,
          pocAdminStatus.pocAdminGroupAssigned,
          pocAdminStatus.keycloakEmailSent,
          pocAdminStatus.errorMessage,
          pocAdminStatus.lastUpdated,
          pocAdminStatus.created
        ))
      }
    }

    "fail to retrieve PoC Admin Status if provided PoC Admin can't be found" in {
      withInjector { injector =>
        val pocId = UUID.randomUUID()
        val randomPocAdminId = UUID.randomUUID()
        val pocTable = injector.get[PocRepository]
        val pocAdminTable = injector.get[PocAdminRepository]
        val pocAdminStatusTable = injector.get[PocAdminStatusRepository]
        val tenantTable = injector.get[TenantRepository]
        val tenant = createTenant()
        val poc = createPoc(pocId, tenant.tenantName)
        val pocAdmin = createPocAdmin(pocId = poc.id, tenantId = tenant.id)
        val pocAdminStatus = createPocAdminStatus(pocAdmin, poc)
        val r = for {
          _ <- tenantTable.createTenant(tenant)
          _ <- pocTable.createPoc(poc)
          _ <- pocAdminTable.createPocAdmin(pocAdmin)
          _ <- pocAdminStatusTable.createStatus(pocAdminStatus)
        } yield ()
        await(r, 5.seconds)

        val tenantAdminService = injector.get[TenantAdminService]
        val maybePocAdminStatus = await(tenantAdminService.getPocAdminStatus(tenant, randomPocAdminId), 5.seconds)
        maybePocAdminStatus shouldBe Left(PocAdminNotFound(randomPocAdminId))
      }
    }

    "fail to retrieve PoC Admin Status is assigned to different Tenant" in {
      withInjector { injector =>
        val pocId = UUID.randomUUID()
        val pocTable = injector.get[PocRepository]
        val pocAdminTable = injector.get[PocAdminRepository]
        val pocAdminStatusTable = injector.get[PocAdminStatusRepository]
        val tenantTable = injector.get[TenantRepository]
        val tenant1 = createTenant("tenant1")
        val tenant2 = createTenant("tenant2")
        val poc = createPoc(pocId, tenant1.tenantName)
        val pocAdmin = createPocAdmin(pocId = poc.id, tenantId = tenant1.id)
        val pocAdminStatus = createPocAdminStatus(pocAdmin, poc)
        val r = for {
          _ <- tenantTable.createTenant(tenant1)
          _ <- tenantTable.createTenant(tenant2)
          _ <- pocTable.createPoc(poc)
          _ <- pocAdminTable.createPocAdmin(pocAdmin)
          _ <- pocAdminStatusTable.createStatus(pocAdminStatus)
        } yield ()
        await(r, 5.seconds)

        val tenantAdminService = injector.get[TenantAdminService]
        val maybePocAdminStatus = await(tenantAdminService.getPocAdminStatus(tenant2, pocAdmin.id), 5.seconds)
        maybePocAdminStatus shouldBe Left(PocAdminAssignedToDifferentTenant(tenant2.id, pocAdmin.id))
      }
    }

    "fail to retrieve PoC Admin Status if it does not exists" in {
      withInjector { injector =>
        val pocId = UUID.randomUUID()
        val pocTable = injector.get[PocRepository]
        val pocAdminTable = injector.get[PocAdminRepository]
        val tenantTable = injector.get[TenantRepository]
        val tenant = createTenant()
        val poc = createPoc(pocId, tenant.tenantName)
        val pocAdmin = createPocAdmin(pocId = poc.id, tenantId = tenant.id)
        val r = for {
          _ <- tenantTable.createTenant(tenant)
          _ <- pocTable.createPoc(poc)
          _ <- pocAdminTable.createPocAdmin(pocAdmin)
        } yield ()
        await(r, 5.seconds)

        val tenantAdminService = injector.get[TenantAdminService]
        val maybePocAdminStatus = await(tenantAdminService.getPocAdminStatus(tenant, pocAdmin.id), 5.seconds)
        maybePocAdminStatus shouldBe Left(PocAdminStatusNotFound(pocAdmin.id))
      }
    }
  }

}
