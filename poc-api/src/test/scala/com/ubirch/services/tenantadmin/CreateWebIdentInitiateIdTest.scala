package com.ubirch.services.tenantadmin

import com.ubirch.ModelCreationHelper.{
  createPoc,
  createPocAdmin,
  createPocAdminStatus,
  createTenant,
  getTenantAdminContext
}
import com.ubirch.db.tables.{ PocAdminRepository, PocAdminStatusRepository, PocRepository, TenantRepository }
import com.ubirch.models.tenant.CreateWebIdentInitiateIdRequest
import com.ubirch.{ InjectorHelper, UnitTestBase }

import java.util.UUID
import scala.concurrent.duration.DurationInt

class CreateWebIdentInitiateIdTest extends UnitTestBase {

  "CreateWebIdentInitiateId" should {
    "successfully create WebIdentInitiateId" in {
      withInjector { injector =>
        val pocId = UUID.randomUUID()
        val pocTable = injector.get[PocRepository]
        val pocAdminTable = injector.get[PocAdminRepository]
        val pocAdminStatusTable = injector.get[PocAdminStatusRepository]
        val tenant = addTenantToDB(injector)
        val poc = createPoc(pocId, tenant.tenantName)
        val pocAdmin = createPocAdmin(pocId = poc.id, tenantId = tenant.id)
        val pocAdminStatus = createPocAdminStatus(pocAdmin, poc)
        val r = for {
          _ <- pocTable.createPoc(poc)
          _ <- pocAdminTable.createPocAdmin(pocAdmin)
          _ <- pocAdminStatusTable.createStatus(pocAdminStatus)
        } yield ()
        await(r, 5.seconds)

        val tenantAdminService = injector.get[TenantAdminService]

        val result = await(
          tenantAdminService.createWebIdentInitiateId(
            tenant,
            getTenantAdminContext(tenant),
            CreateWebIdentInitiateIdRequest(pocAdmin.id)),
          5.seconds)
        val pocAdminAfterUpdate = await(pocAdminTable.getPocAdmin(pocAdmin.id), 5.seconds)
        val statusAfterUpdate = await(pocAdminStatusTable.getStatus(pocAdmin.id), 5.seconds)

        result.right.value shouldBe pocAdminAfterUpdate.value.webIdentInitiateId.value
        statusAfterUpdate.value.webIdentInitiated shouldBe Some(true)
      }
    }

    "fail to create WebIdentInitiateId if provided PocAdmin can't be found" in {
      withInjector { injector =>
        val pocId = UUID.randomUUID()
        val pocTable = injector.get[PocRepository]
        val pocAdminTable = injector.get[PocAdminRepository]
        val pocAdminStatusTable = injector.get[PocAdminStatusRepository]
        val tenant = addTenantToDB(injector)
        val poc = createPoc(pocId, tenant.tenantName)
        val pocAdmin = createPocAdmin(pocId = poc.id, tenantId = tenant.id)
        val pocAdminStatus = createPocAdminStatus(pocAdmin, poc)
        val r = for {
          _ <- pocTable.createPoc(poc)
          _ <- pocAdminTable.createPocAdmin(pocAdmin)
          _ <- pocAdminStatusTable.createStatus(pocAdminStatus)
        } yield ()
        await(r, 5.seconds)

        val tenantAdminService = injector.get[TenantAdminService]
        val randomPocAdminId = UUID.randomUUID()

        val result = await(
          tenantAdminService.createWebIdentInitiateId(
            tenant,
            getTenantAdminContext(tenant),
            CreateWebIdentInitiateIdRequest(randomPocAdminId)),
          5.seconds)

        result shouldBe Left(CreateWebIdentInitiateIdErrors.PocAdminNotFound(randomPocAdminId))
        val pocAdminAfterFailedUpdate = await(pocAdminTable.getPocAdmin(pocAdmin.id), 5.seconds)
        pocAdminAfterFailedUpdate.value.webIdentInitiateId shouldBe None
      }
    }

    "fail to create WebIdentInitiateId if provided PocAdmin is assigned to different Tenant" in {
      withInjector { injector =>
        val pocId = UUID.randomUUID()
        val pocTable = injector.get[PocRepository]
        val pocAdminTable = injector.get[PocAdminRepository]
        val pocAdminStatusTable = injector.get[PocAdminStatusRepository]
        val tenant1 = addTenantToDB(injector, "tenant1")
        val tenant2 = addTenantToDB(injector, "tenant2")
        val poc = createPoc(pocId, tenant1.tenantName)
        val pocAdmin1 = createPocAdmin(pocId = poc.id, tenantId = tenant1.id)
        val pocAdmin2 = createPocAdmin(pocId = poc.id, tenantId = tenant2.id)
        val pocAdminStatus1 = createPocAdminStatus(pocAdmin1, poc)
        val pocAdminStatus2 = createPocAdminStatus(pocAdmin2, poc)
        val r = for {
          _ <- pocTable.createPoc(poc)
          _ <- pocAdminTable.createPocAdmin(pocAdmin1)
          _ <- pocAdminTable.createPocAdmin(pocAdmin2)
          _ <- pocAdminStatusTable.createStatus(pocAdminStatus1)
          _ <- pocAdminStatusTable.createStatus(pocAdminStatus2)
        } yield ()
        await(r, 5.seconds)

        val tenantAdminService = injector.get[TenantAdminService]

        val result = await(
          tenantAdminService.createWebIdentInitiateId(
            tenant1,
            getTenantAdminContext(tenant1),
            CreateWebIdentInitiateIdRequest(pocAdmin2.id)),
          5.seconds)

        result shouldBe Left(CreateWebIdentInitiateIdErrors.PocAdminAssignedToDifferentTenant(tenant1.id, pocAdmin2.id))
        val pocAdminAfterFailedUpdate1 = await(pocAdminTable.getPocAdmin(pocAdmin1.id), 5.seconds)
        pocAdminAfterFailedUpdate1.value.webIdentInitiateId shouldBe None
        val pocAdminAfterFailedUpdate2 = await(pocAdminTable.getPocAdmin(pocAdmin2.id), 5.seconds)
        pocAdminAfterFailedUpdate2.value.webIdentInitiateId shouldBe None
      }
    }

    "fail to create WebIdentInitiateId if WebIdentRequired is set to false" in {
      withInjector { injector =>
        val pocId = UUID.randomUUID()
        val pocTable = injector.get[PocRepository]
        val pocAdminTable = injector.get[PocAdminRepository]
        val tenant = addTenantToDB(injector)
        val poc = createPoc(pocId, tenant.tenantName)
        val pocAdmin = createPocAdmin(pocId = poc.id, tenantId = tenant.id, webIdentRequired = false)
        val r = for {
          _ <- pocTable.createPoc(poc)
          _ <- pocAdminTable.createPocAdmin(pocAdmin)
        } yield ()
        await(r, 5.seconds)

        val tenantAdminService = injector.get[TenantAdminService]

        val result = await(
          tenantAdminService.createWebIdentInitiateId(
            tenant,
            getTenantAdminContext(tenant),
            CreateWebIdentInitiateIdRequest(pocAdmin.id)),
          5.seconds)

        result shouldBe Left(CreateWebIdentInitiateIdErrors.WebIdentNotRequired(tenant.id, pocAdmin.id))
      }
    }
  }

  private def addTenantToDB(injector: InjectorHelper, name: String = "tenant") = {
    val tenantTable = injector.get[TenantRepository]
    val tenant = createTenant(name = name)
    await(tenantTable.createTenant(tenant), 5.seconds)
    tenant
  }

}
