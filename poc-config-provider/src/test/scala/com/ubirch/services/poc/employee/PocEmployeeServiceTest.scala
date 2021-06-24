package com.ubirch.services.poc.employee

import com.ubirch.UnitTestBase
import com.ubirch.db.tables.PocRepositoryMock
import com.ubirch.models.poc._
import com.ubirch.models.tenant.{ TenantId, TenantName }
import com.ubirch.services.poc.employee.GetCertifyConfigError.{ InvalidDataPocType, PocIsNotCompleted }
import org.json4s.native.JsonMethods.parse

import java.util.UUID
import scala.concurrent.duration.DurationInt

class PocEmployeeServiceTest extends UnitTestBase {
  "getCertifyConfig" should {
    "successfully get GetCertifyConfigDTO" in {
      withInjector { injector =>
        val pocTable = injector.get[PocRepositoryMock]
        val pocId = UUID.randomUUID()
        val poc = createPoc(pocId, TenantName("tenantName")).copy(status = Completed)
        val r = pocTable.createPoc(poc)
        await(r, 5.seconds)

        val pocEmployeeService = injector.get[PocEmployeeService]

        val result =
          pocEmployeeService.getCertifyConfig(PocCertifyConfigRequest(poc.id)).runSyncUnsafe(5.seconds)

        assert(result.isRight)
      }
    }

    "fail to GetCertifyConfigDTO when pocType is unknown" in {
      withInjector { injector =>
        val pocTable = injector.get[PocRepositoryMock]
        val pocId = UUID.randomUUID()
        val poc = createPoc(pocId, TenantName("tenantName")).copy(pocType = "ub_cust_app1", status = Completed)
        val r = pocTable.createPoc(poc)
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
        val pocTable = injector.get[PocRepositoryMock]
        val pocId = UUID.randomUUID()
        val poc = createPoc(pocId, TenantName("tenantName"))
        val r = pocTable.createPoc(poc)
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
        val pocTable = injector.get[PocRepositoryMock]
        val pocId = UUID.randomUUID()
        val poc = createPoc(pocId, TenantName("tenantName"))
        val r = pocTable.createPoc(poc)
        await(r, 5.seconds)

        val pocEmployeeService = injector.get[PocEmployeeService]

        val result =
          pocEmployeeService.getCertifyConfig(PocCertifyConfigRequest(poc.id)).runSyncUnsafe(5.seconds)

        assert(result.isLeft)
      }
    }
  }

  def createPoc(
    id: UUID = UUID.randomUUID(),
    tenantName: TenantName,
    externalId: String = UUID.randomUUID().toString,
    name: String = "pocName",
    status: Status = Pending,
    city: String = "Paris"
  ): Poc =
    Poc(
      id = id,
      tenantId = TenantId(tenantName),
      externalId = externalId,
      pocType = "ub_vac_app",
      pocName = name,
      address = Address("An der Heide", "101", None, 67832, city, None, None, "France"),
      phone = "pocPhone",
      logoUrl = None,
      extraConfig = Some(JsonConfig(parse("""{"test":"hello"}"""))),
      manager = PocManager("surname", "", "", "08023-782137"),
      status = status
    )
}
