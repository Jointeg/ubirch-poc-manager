package com.ubirch.services.tenantadmin

import com.ubirch.ModelCreationHelper.{
  createPoc,
  createPocAdmin,
  createPocAdminStatus,
  createTenant,
  getTenantAdminContext
}
import com.ubirch.controllers.TenantAdminContext
import com.ubirch.db.tables.{ PocAdminRepository, PocAdminStatusRepository, PocRepository, TenantRepository }
import com.ubirch.models.tenant.{ Tenant, UpdateWebIdentIdRequest }
import com.ubirch.services.tenantadmin.UpdateWebIdentIdError.{
  DifferentWebIdentInitialId,
  NotExistingPocAdminStatus,
  PocAdminIsNotAssignedToRequestingTenant,
  UnknownPocAdmin,
  WebIdentAlreadyExist
}
import com.ubirch.{ InjectorHelper, UnitTestBase }

import java.util.UUID
import scala.concurrent.duration.DurationInt

class UpdateWebIdentIdTest extends UnitTestBase {

  "UpdateWebIdentId" should {

    "successfully update WebIdentId" in {
      withInjector { injector =>
        val pocId = UUID.randomUUID()
        val webIdentInitiateId = UUID.randomUUID()
        val webIdentId = UUID.randomUUID().toString
        val tenantAdminService = injector.get[TenantAdminService]
        val pocTable = injector.get[PocRepository]
        val pocAdminTable = injector.get[PocAdminRepository]
        val pocAdminStatusTable = injector.get[PocAdminStatusRepository]
        val tenant = addTenantToDB(injector)
        val poc = createPoc(pocId, tenant.tenantName)
        val pocAdmin =
          createPocAdmin(pocId = poc.id, tenantId = tenant.id).copy(webIdentInitiateId = Some(webIdentInitiateId))
        val pocAdminStatus = createPocAdminStatus(pocAdmin, poc)

        val r = for {
          _ <- pocTable.createPoc(poc)
          _ <- pocAdminTable.createPocAdmin(pocAdmin)
          _ <- pocAdminStatusTable.createStatus(pocAdminStatus)
        } yield ()
        await(r, 5.seconds)

        val result = await(
          tenantAdminService.updateWebIdentId(
            tenant,
            getTenantAdminContext(tenant),
            UpdateWebIdentIdRequest(pocAdmin.id, webIdentId, webIdentInitiateId)),
          5.seconds
        )
        result shouldBe Right(())

        val pocAdminAfterUpdate = await(pocAdminTable.getPocAdmin(pocAdmin.id), 5.seconds)
        pocAdminAfterUpdate.value.webIdentId.value shouldBe webIdentId
        val statusAfterUpdate = await(pocAdminStatusTable.getStatus(pocAdmin.id), 5.seconds)
        statusAfterUpdate.value.webIdentSuccess shouldBe Some(true)
      }
    }

    "fail to update WebIdentId if provided PocAdmin does not exists" in {
      withInjector { injector =>
        val pocId = UUID.randomUUID()
        val webIdentInitiateId = UUID.randomUUID()
        val webIdentId = UUID.randomUUID().toString
        val tenantAdminService = injector.get[TenantAdminService]
        val pocTable = injector.get[PocRepository]
        val pocAdminTable = injector.get[PocAdminRepository]
        val pocAdminStatusTable = injector.get[PocAdminStatusRepository]
        val tenant = addTenantToDB(injector)
        val poc = createPoc(pocId, tenant.tenantName)
        val pocAdmin =
          createPocAdmin(pocId = poc.id, tenantId = tenant.id).copy(webIdentInitiateId = Some(webIdentInitiateId))
        val pocAdminStatus = createPocAdminStatus(pocAdmin, poc)
        val r = for {
          _ <- pocTable.createPoc(poc)
          _ <- pocAdminTable.createPocAdmin(pocAdmin)
          _ <- pocAdminStatusTable.createStatus(pocAdminStatus)
        } yield ()
        await(r, 5.seconds)

        val unknownPocAdminId = UUID.randomUUID()

        val result = await(
          tenantAdminService.updateWebIdentId(
            tenant,
            getTenantAdminContext(tenant),
            UpdateWebIdentIdRequest(unknownPocAdminId, webIdentId, webIdentInitiateId)),
          5.seconds
        )

        result shouldBe Left(UnknownPocAdmin(unknownPocAdminId))
        val pocAdminAfterFailedUpdate = await(pocAdminTable.getPocAdmin(pocAdmin.id), 5.seconds)
        pocAdminAfterFailedUpdate.value.webIdentId shouldBe None
      }
    }

    "fail to update WebIdentId if provided PocAdmin does belong to another tenant" in {
      withInjector { injector =>
        val pocId = UUID.randomUUID()
        val webIdentInitiateId = UUID.randomUUID()
        val webIdentId = UUID.randomUUID().toString
        val tenantAdminService = injector.get[TenantAdminService]
        val pocTable = injector.get[PocRepository]
        val pocAdminTable = injector.get[PocAdminRepository]
        val pocAdminStatusTable = injector.get[PocAdminStatusRepository]
        val tenant1 = addTenantToDB(injector, "tenant1")
        val tenant2 = addTenantToDB(injector, "tenant2")
        val poc = createPoc(pocId, tenant1.tenantName)
        val pocAdmin1 =
          createPocAdmin(pocId = poc.id, tenantId = tenant1.id).copy(webIdentInitiateId = Some(webIdentInitiateId))
        val pocAdmin2 =
          createPocAdmin(pocId = poc.id, tenantId = tenant2.id).copy(webIdentInitiateId = Some(webIdentInitiateId))
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

        val result = await(
          tenantAdminService.updateWebIdentId(
            tenant1,
            getTenantAdminContext(tenant1),
            UpdateWebIdentIdRequest(pocAdmin2.id, webIdentId, webIdentInitiateId)),
          5.seconds
        )

        result shouldBe Left(PocAdminIsNotAssignedToRequestingTenant(pocAdmin2.tenantId, tenant1.id))
        val pocAdminAfterFailedUpdate1 = await(pocAdminTable.getPocAdmin(pocAdmin1.id), 5.seconds)
        pocAdminAfterFailedUpdate1.value.webIdentId shouldBe None
        val pocAdminAfterFailedUpdate2 = await(pocAdminTable.getPocAdmin(pocAdmin2.id), 5.seconds)
        pocAdminAfterFailedUpdate2.value.webIdentId shouldBe None
      }
    }

    "fail to update WebIdentId because WebIdentInitialId is different" in {
      withInjector { injector =>
        val pocId = UUID.randomUUID()
        val webIdentInitiateId = UUID.randomUUID()
        val webIdentId = UUID.randomUUID().toString
        val tenantAdminService = injector.get[TenantAdminService]
        val pocTable = injector.get[PocRepository]
        val pocAdminTable = injector.get[PocAdminRepository]
        val pocAdminStatusTable = injector.get[PocAdminStatusRepository]
        val tenant = addTenantToDB(injector)
        val poc = createPoc(pocId, tenant.tenantName)
        val pocAdmin =
          createPocAdmin(pocId = poc.id, tenantId = tenant.id).copy(webIdentInitiateId = Some(webIdentInitiateId))
        val pocAdminStatus = createPocAdminStatus(pocAdmin, poc)
        val r = for {
          _ <- pocTable.createPoc(poc)
          _ <- pocAdminTable.createPocAdmin(pocAdmin)
          _ <- pocAdminStatusTable.createStatus(pocAdminStatus)
        } yield ()
        await(r, 5.seconds)

        val unknownWebIdentInitialId = UUID.randomUUID()

        val result = await(
          tenantAdminService.updateWebIdentId(
            tenant,
            getTenantAdminContext(tenant),
            UpdateWebIdentIdRequest(pocAdmin.id, webIdentId, unknownWebIdentInitialId)),
          5.seconds
        )

        result shouldBe Left(DifferentWebIdentInitialId(unknownWebIdentInitialId, tenant, pocAdmin))
        val pocAdminAfterFailedUpdate = await(pocAdminTable.getPocAdmin(pocAdmin.id), 5.seconds)
        pocAdminAfterFailedUpdate.value.webIdentId shouldBe None
      }
    }

    "fail to update WebIdentId because PocAdminStatus does not exists" in {
      withInjector { injector =>
        val pocId = UUID.randomUUID()
        val webIdentInitiateId = UUID.randomUUID()
        val webIdentId = UUID.randomUUID().toString
        val tenantAdminService = injector.get[TenantAdminService]
        val pocTable = injector.get[PocRepository]
        val pocAdminTable = injector.get[PocAdminRepository]
        val tenant = addTenantToDB(injector)
        val poc = createPoc(pocId, tenant.tenantName)
        val pocAdmin =
          createPocAdmin(pocId = poc.id, tenantId = tenant.id).copy(webIdentInitiateId = Some(webIdentInitiateId))
        val r = for {
          _ <- pocTable.createPoc(poc)
          _ <- pocAdminTable.createPocAdmin(pocAdmin)
        } yield ()
        await(r, 5.seconds)

        val result = await(
          tenantAdminService.updateWebIdentId(
            tenant,
            getTenantAdminContext(tenant),
            UpdateWebIdentIdRequest(pocAdmin.id, webIdentId, webIdentInitiateId)),
          5.seconds
        )

        result shouldBe Left(NotExistingPocAdminStatus(pocAdmin.id))
        val pocAdminAfterFailedUpdate = await(pocAdminTable.getPocAdmin(pocAdmin.id), 5.seconds)
        pocAdminAfterFailedUpdate.value.webIdentId shouldBe None
      }
    }

    "fail to update WebIdentId because WebIdentInitialId already exist" in {
      withInjector { injector =>
        val pocId = UUID.randomUUID()
        val webIdentInitiateId = UUID.randomUUID()
        val webIdentId = UUID.randomUUID().toString
        val tenantAdminService = injector.get[TenantAdminService]
        val pocTable = injector.get[PocRepository]
        val pocAdminTable = injector.get[PocAdminRepository]
        val pocAdminStatusTable = injector.get[PocAdminStatusRepository]
        val tenant = addTenantToDB(injector)
        val poc = createPoc(pocId, tenant.tenantName)
        val pocAdmin =
          createPocAdmin(pocId = poc.id, tenantId = tenant.id).copy(
            webIdentInitiateId = Some(webIdentInitiateId),
            webIdentId = Some(webIdentId))
        val pocAdminStatus = createPocAdminStatus(pocAdmin, poc)
        val r = for {
          _ <- pocTable.createPoc(poc)
          _ <- pocAdminTable.createPocAdmin(pocAdmin)
          _ <- pocAdminStatusTable.createStatus(pocAdminStatus)
        } yield ()
        await(r, 5.seconds)

        val result = await(
          tenantAdminService.updateWebIdentId(
            tenant,
            getTenantAdminContext(tenant),
            UpdateWebIdentIdRequest(pocAdmin.id, webIdentId, webIdentInitiateId)),
          5.seconds
        )

        result shouldBe Left(WebIdentAlreadyExist(pocAdmin.id))
        val pocAdminAfterFailedUpdate = await(pocAdminTable.getPocAdmin(pocAdmin.id), 5.seconds)
        pocAdminAfterFailedUpdate.value.webIdentId shouldBe Some(webIdentId)
      }
    }
  }

  private def addTenantToDB(injector: InjectorHelper, name: String = "tenant"): Tenant = {
    val tenantTable = injector.get[TenantRepository]
    val tenant = createTenant(name)
    await(tenantTable.createTenant(tenant), 5.seconds)
    tenant
  }

}
