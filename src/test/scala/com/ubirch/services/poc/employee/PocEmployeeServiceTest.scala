package com.ubirch.services.poc.employee

import com.ubirch.ModelCreationHelper.{ createPoc, createPocAdmin, createPocEmployee, createTenant, pocTypeValue }
import com.ubirch.UnitTestBase
import com.ubirch.db.tables.{ PocAdminRepository, PocEmployeeRepository, PocRepository, TenantRepository }
import com.ubirch.models.poc.Completed
import com.ubirch.services.poc.employee.GetCertifyConfigError.InvalidDataPocType

import java.util.UUID
import scala.concurrent.duration.DurationInt

class PocEmployeeServiceTest extends UnitTestBase {
  "getCertifyConfig" should {
    "successfully get GetCertifyConfigDTO" in {
      withInjector { injector =>
        val pocTable = injector.get[PocRepository]
        val pocAdminTable = injector.get[PocAdminRepository]
        val employeeTable = injector.get[PocEmployeeRepository]
        val tenantTable = injector.get[TenantRepository]
        val tenant = createTenant()
        val pocId = UUID.randomUUID()
        val poc = createPoc(pocId, tenant.tenantName)
        val pocAdmin = createPocAdmin(pocId = poc.id, tenantId = tenant.id).copy(status = Completed)
        val employee = createPocEmployee(pocId = poc.id, tenantId = tenant.id).copy(status = Completed)
        val r = for {
          _ <- tenantTable.createTenant(tenant)
          _ <- pocTable.createPoc(poc)
          _ <- pocAdminTable.createPocAdmin(pocAdmin)
          _ <- employeeTable.createPocEmployee(employee)
        } yield ()
        await(r, 5.seconds)

        val pocEmployeeService = injector.get[PocEmployeeService]

        val result = pocEmployeeService.getCertifyConfig(employee).runSyncUnsafe(5.seconds)

        assert(result.isRight)
      }
    }

    "fail to GetCertifyConfigDTO when pocType is unknown" in {
      withInjector { injector =>
        val pocTable = injector.get[PocRepository]
        val pocAdminTable = injector.get[PocAdminRepository]
        val employeeTable = injector.get[PocEmployeeRepository]
        val tenantTable = injector.get[TenantRepository]
        val tenant = createTenant()
        val pocId = UUID.randomUUID()
        val poc = createPoc(pocId, tenant.tenantName).copy(pocType = "ub_cust_app1")
        val pocAdmin = createPocAdmin(pocId = poc.id, tenantId = tenant.id).copy(status = Completed)
        val employee = createPocEmployee(pocId = poc.id, tenantId = tenant.id).copy(status = Completed)
        val r = for {
          _ <- tenantTable.createTenant(tenant)
          _ <- pocTable.createPoc(poc)
          _ <- pocAdminTable.createPocAdmin(pocAdmin)
          _ <- employeeTable.createPocEmployee(employee)
        } yield ()
        await(r, 5.seconds)

        val pocEmployeeService = injector.get[PocEmployeeService]

        val result = pocEmployeeService.getCertifyConfig(employee).runSyncUnsafe(5.seconds)

        assert(result.isLeft)
        assert(result.left.get.isInstanceOf[InvalidDataPocType])
      }
    }
  }
}
