package com.ubirch.services.poc.employee

import com.ubirch.ModelCreationHelper.{ createPoc, createTenant }
import com.ubirch.UnitTestBase
import com.ubirch.db.tables.{ PocRepository, TenantRepository }
import com.ubirch.models.poc.Completed
import com.ubirch.services.poc.employee.GetCertifyConfigError.{ InvalidDataPocType, PocIsNotCompleted }

import java.util.UUID
import scala.concurrent.duration.DurationInt

class PocEmployeeServiceTest extends UnitTestBase {
  "getCertifyConfig" should {
    "successfully get GetCertifyConfigDTO" in {
      withInjector { injector =>
        val pocTable = injector.get[PocRepository]
        val tenantTable = injector.get[TenantRepository]
        val tenant = createTenant()
        val pocId = UUID.randomUUID()
        val poc = createPoc(pocId, tenant.tenantName).copy(status = Completed)
        val r = for {
          _ <- tenantTable.createTenant(tenant)
          _ <- pocTable.createPoc(poc)
        } yield ()
        await(r, 5.seconds)

        val pocEmployeeService = injector.get[PocEmployeeService]

        val result =
          pocEmployeeService.getCertifyConfig(PocCertifyConfigRequest(poc.id)).runSyncUnsafe(5.seconds)

        assert(result.isRight)
      }
    }

    "fail to GetCertifyConfigDTO when pocType is unknown" in {
      withInjector { injector =>
        val pocTable = injector.get[PocRepository]
        val tenantTable = injector.get[TenantRepository]
        val tenant = createTenant()
        val pocId = UUID.randomUUID()
        val poc = createPoc(pocId, tenant.tenantName).copy(pocType = "ub_cust_app1", status = Completed)
        val r = for {
          _ <- tenantTable.createTenant(tenant)
          _ <- pocTable.createPoc(poc)
        } yield ()
        await(r, 5.seconds)

        val pocEmployeeService = injector.get[PocEmployeeService]

        val result =
          pocEmployeeService.getCertifyConfig(PocCertifyConfigRequest(poc.id)).runSyncUnsafe(5.seconds)

        assert(result.isLeft)
        assert(result.left.get.isInstanceOf[InvalidDataPocType])
      }
    }

    "fail to GetCertifyConfigDTO when pocType is not completed" in {
      withInjector { injector =>
        val pocTable = injector.get[PocRepository]
        val tenantTable = injector.get[TenantRepository]
        val tenant = createTenant()
        val pocId = UUID.randomUUID()
        val poc = createPoc(pocId, tenant.tenantName)
        val r = for {
          _ <- tenantTable.createTenant(tenant)
          _ <- pocTable.createPoc(poc)
        } yield ()
        await(r, 5.seconds)

        val pocEmployeeService = injector.get[PocEmployeeService]

        val result =
          pocEmployeeService.getCertifyConfig(PocCertifyConfigRequest(poc.id)).runSyncUnsafe(5.seconds)

        assert(result.isLeft)
        assert(result.left.get.isInstanceOf[PocIsNotCompleted])
      }
    }

    "fail to GetCertifyConfigDTO when role doesn't exist" in {
      withInjector { injector =>
        val pocTable = injector.get[PocRepository]
        val tenantTable = injector.get[TenantRepository]
        val tenant = createTenant()
        val pocId = UUID.randomUUID()
        val poc = createPoc(pocId, tenant.tenantName)
        val r = for {
          _ <- tenantTable.createTenant(tenant)
          _ <- pocTable.createPoc(poc)
        } yield ()
        await(r, 5.seconds)

        val pocEmployeeService = injector.get[PocEmployeeService]

        val result =
          pocEmployeeService.getCertifyConfig(PocCertifyConfigRequest(poc.id)).runSyncUnsafe(5.seconds)

        assert(result.isLeft)
      }
    }
  }
}
