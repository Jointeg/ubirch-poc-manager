package com.ubirch.services.tenantadmin

import com.ubirch.ModelCreationHelper.{ addTenantToDB, createPoc, createPocAdmin, getTenantAdminContext }
import com.ubirch.UnitTestBase
import com.ubirch.db.tables.{ PocAdminRepository, PocAdminStatusRepository, PocRepository }
import com.ubirch.models.tenant.CreatePocAdminRequest
import com.ubirch.services.tenantadmin.CreatePocAdminError.{ InvalidDataError, NotFound }
import org.joda.time.LocalDate

import java.util.UUID
import scala.concurrent.duration.DurationInt

class CreatePocAdminTest extends UnitTestBase {

  "CreatePocAdmin functionality" should {
    "successfully create poc admin" in {
      withInjector { injector =>
        val pocTable = injector.get[PocRepository]
        val pocAdminTable = injector.get[PocAdminRepository]
        val pocAdminStatusTable = injector.get[PocAdminStatusRepository]
        val tenantAdminService = injector.get[TenantAdminService]
        val pocId = UUID.randomUUID()
        val tenant = addTenantToDB(injector)
        val poc = createPoc(pocId, tenant.tenantName)
        pocTable.createPoc(poc).runSyncUnsafe(5.seconds)

        val createPocAdminRequest = CreatePocAdminRequest(
          pocId,
          "first",
          "last",
          "aaa@ubirch.com",
          "+4911111111",
          LocalDate.now().minusYears(30),
          true
        )

        val result = tenantAdminService.createPocAdmin(
          tenant,
          getTenantAdminContext(tenant),
          createPocAdminRequest).runSyncUnsafe(5.seconds)

        assert(result.isRight)

        val admins = pocAdminTable.getByPocId(pocId).runSyncUnsafe(5.seconds)
        admins.size shouldBe 1
        val admin = admins.head
        val adminStatus = pocAdminStatusTable.getStatus(admin.id).runSyncUnsafe(5.seconds)
        admin.name shouldBe createPocAdminRequest.firstName
        admin.surname shouldBe createPocAdminRequest.lastName
        admin.email shouldBe createPocAdminRequest.email
        admin.mobilePhone shouldBe createPocAdminRequest.phone
        admin.dateOfBirth.date shouldBe createPocAdminRequest.dateOfBirth
        admin.webIdentRequired shouldBe createPocAdminRequest.webIdentRequired
        assert(adminStatus.isDefined)
      }
    }

    "failed to create PocAdmin if provided PocAdmin is assigned to different Tenant" in {
      withInjector { injector =>
        val pocTable = injector.get[PocRepository]
        val tenantAdminService = injector.get[TenantAdminService]
        val pocId1 = UUID.randomUUID()
        val pocId2 = UUID.randomUUID()
        val tenant1 = addTenantToDB(injector, "tenant1")
        val tenant2 = addTenantToDB(injector, "tenant2")
        val poc1 = createPoc(pocId1, tenant1.tenantName)
        val poc2 = createPoc(pocId2, tenant2.tenantName)
        (for {
          _ <- pocTable.createPoc(poc1)
          _ <- pocTable.createPoc(poc2)
        } yield ()).runSyncUnsafe(5.seconds)

        val createPocAdminRequest = CreatePocAdminRequest(
          pocId1,
          "first",
          "last",
          "aaa@ubirch.com",
          "+4911111111",
          LocalDate.now().minusYears(30),
          true
        )

        val result = tenantAdminService.createPocAdmin(
          tenant2,
          getTenantAdminContext(tenant2),
          createPocAdminRequest).runSyncUnsafe(5.seconds)

        assert(result.left.get.isInstanceOf[NotFound])
      }
    }

    "failed to create PocAdmin if the input data is invalid" in {
      withInjector { injector =>
        val pocTable = injector.get[PocRepository]
        val tenantAdminService = injector.get[TenantAdminService]
        val pocId = UUID.randomUUID()
        val tenant = addTenantToDB(injector)
        val poc = createPoc(pocId, tenant.tenantName)
        pocTable.createPoc(poc).runSyncUnsafe(5.seconds)

        val createPocAdminRequest = CreatePocAdminRequest(
          pocId,
          "",
          "",
          "aaa",
          "11111111",
          LocalDate.now().minusYears(30),
          true
        )

        val result = tenantAdminService.createPocAdmin(
          tenant,
          getTenantAdminContext(tenant),
          createPocAdminRequest).runSyncUnsafe(5.seconds)

        result.left.get shouldBe InvalidDataError(
          "the input data is invalid. name is invalid; surName is invalid; email is invalid; phone number is invalid")
      }
    }
  }
}
