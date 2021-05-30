package com.ubirch.services.pocadmin

import com.ubirch.ModelCreationHelper.{ createPoc, createPocAdmin, createPocEmployee, createTenant }
import com.ubirch.UnitTestBase
import com.ubirch.db.tables.model.{ AdminCriteria, StatusFilter }
import com.ubirch.db.tables.{ PocAdminRepository, PocEmployeeRepository, PocRepository, TenantRepository }
import com.ubirch.models.common.{ ASC, Page, Sort }
import com.ubirch.models.poc.Completed

import java.util.UUID
import scala.concurrent.duration.DurationInt

class PocAdminServiceTest extends UnitTestBase {
  "Get Employees" should {
    "successfully get employees" in {
      withInjector { injector =>
        val pocTable = injector.get[PocRepository]
        val pocAdminTable = injector.get[PocAdminRepository]
        val employeeTable = injector.get[PocEmployeeRepository]
        val tenantTable = injector.get[TenantRepository]
        val tenant = createTenant()
        val pocId1 = UUID.randomUUID()
        val poc1 = createPoc(pocId1, tenant.tenantName)
        val pocAdmin1 = createPocAdmin(pocId = poc1.id, tenantId = tenant.id).copy(status = Completed)
        val employee1 = createPocEmployee(pocId = poc1.id, tenantId = tenant.id).copy(status = Completed)
        val employee2 = createPocEmployee(pocId = poc1.id, tenantId = tenant.id)
        val pocId2 = UUID.randomUUID()
        val poc2 = createPoc(pocId2, tenant.tenantName)
        val pocAdmin2 = createPocAdmin(pocId = poc2.id, tenantId = tenant.id).copy(status = Completed)
        val employee3 = createPocEmployee(pocId = poc2.id, tenantId = tenant.id)
        (for {
          _ <- tenantTable.createTenant(tenant)
          _ <- pocTable.createPoc(poc1)
          _ <- pocTable.createPoc(poc2)
          _ <- pocAdminTable.createPocAdmin(pocAdmin1)
          _ <- pocAdminTable.createPocAdmin(pocAdmin2)
          _ <- employeeTable.createPocEmployee(employee1)
          _ <- employeeTable.createPocEmployee(employee2)
          _ <- employeeTable.createPocEmployee(employee3)
        } yield ()).runSyncUnsafe(5.seconds)

        val pocAdminService = injector.get[PocAdminService]

        val criteria = AdminCriteria(pocAdmin1.id, Page(0, 20), Sort(None, ASC), None, StatusFilter(Seq(Completed)))

        val result = pocAdminService.getEmployees(pocAdmin1, criteria).runSyncUnsafe(5.seconds)

        assert(result.isRight)
        assert(result.right.get.total == 1)
        assert(result.right.get.records.head.id == employee1.id)
      }
    }
  }
}
