package com.ubirch.services.poc

import com.ubirch.ModelCreationHelper.createTenant
import com.ubirch.UnitTestBase
import com.ubirch.controllers.TenantAdminContext
import com.ubirch.models.tenant.Both
import com.ubirch.testutils.CentralCsvProvider

import java.util.UUID

class PocBatchHandlerSpec extends UnitTestBase {

  private val pocId = UUID.randomUUID()

  private val tenant = createTenant()
  private val tenantContext = TenantAdminContext(UUID.randomUUID(), tenant.id.value.asJava())

  "Process csv batch" should {
    "reject if it's a poc admin csv, but the tenant has usageType API" in {
      withInjector { i =>
        val batchHandler = i.get[PocBatchHandlerImpl]
        val r = batchHandler
          .createListOfPoCs(CentralCsvProvider.validPocAdminCsv(pocId), tenant, tenantContext)
          .runSyncUnsafe()
        assert(r.isLeft)
        r.left.get == "cannot parse admin creation for a tenant with usageType API"
      }
    }
    "create poc with admin" in {
      withInjector { i =>
        val batchHandler = i.get[PocBatchHandlerImpl]
        val tenantBoth = tenant.copy(usageType = Both)
        val r = batchHandler
          .createListOfPoCs(CentralCsvProvider.validPocAdminCsv(pocId), tenantBoth, tenantContext)
          .runSyncUnsafe()
        assert(r.isRight)
      }
    }

    "create poc without admin" in {
      withInjector { i =>
        val batchHandler = i.get[PocBatchHandlerImpl]
        val tenantBoth = tenant.copy(usageType = Both)
        val r = batchHandler
          .createListOfPoCs(CentralCsvProvider.validPocOnlyCsv(pocId), tenantBoth, tenantContext)
          .runSyncUnsafe()
        assert(r.isRight)
      }
    }

    "fail, if header columns are missing" in {
      withInjector { i =>
        val batchHandler = i.get[PocBatchHandlerImpl]
        val r = batchHandler
          .createListOfPoCs(CentralCsvProvider.toShortHeaderCsv(pocId), tenant, tenantContext)
          .runSyncUnsafe()
        assert(r.isLeft)
        r.left.get shouldBe "The number of header columns 16 is not enough."
      }
    }

    "fail, if csv is empty" in {
      withInjector { i =>
        val batchHandler = i.get[PocBatchHandlerImpl]
        val r = batchHandler
          .createListOfPoCs("", tenant, tenantContext)
          .runSyncUnsafe()
        assert(r.isLeft)
        r.left.get shouldBe "The provided csv is empty."
      }
    }

  }
}
