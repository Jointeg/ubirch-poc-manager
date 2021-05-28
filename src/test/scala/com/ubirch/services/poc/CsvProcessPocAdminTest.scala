package com.ubirch.services.poc

import com.ubirch.ModelCreationHelper.createTenant
import com.ubirch.UnitTestBase
import com.ubirch.controllers.TenantAdminContext
import com.ubirch.db.tables.{ PocAdminRepository, PocAdminStatusRepository, PocRepository, PocStatusRepository }
import com.ubirch.testutils.CentralCsvProvider.{
  invalidHeaderPocAdminCsv,
  validHeaderButBadRowsPocAdminCsv,
  validPocAdminCsv
}

import java.util.UUID

class CsvProcessPocAdminTest extends UnitTestBase {

  private val pocId = UUID.randomUUID()

  private val tenant = createTenant()
  private val tenantContext = TenantAdminContext(UUID.randomUUID(), tenant.id.value.asJava())

  "ProcessPoc" should {
    "create poc, admin and status" in {
      withInjector { injector =>
        val processPocAdmin = injector.get[CsvProcessPocAdmin]
        val pocRepository = injector.get[PocRepository]
        val pocStatusRepository = injector.get[PocStatusRepository]
        val pocAdminRepository = injector.get[PocAdminRepository]
        val pocAdminStatusRepository = injector.get[PocAdminStatusRepository]

        (for {
          result <- processPocAdmin.createListOfPoCsAndAdmin(validPocAdminCsv(pocId), tenant, tenantContext)
          pocs <- pocRepository.getAllPocsByTenantId(tenant.id)
          poc = pocs.head
          pocStatusOpt <- pocStatusRepository.getPocStatus(poc.id)
          pocAdmins <- pocAdminRepository.getAllPocAdminsByTenantId(tenant.id)
          pocAdmin = pocAdmins.head
          pocAdminStatusOpt <- pocAdminStatusRepository.getStatus(pocAdmin.id)
        } yield {
          assert(result.isRight)
          assert(pocs.length == 1)
          assert(pocStatusOpt.isDefined)
          assert(pocStatusOpt.get.pocId == poc.id)
          assert(pocAdmins.length == 1)
          assert(pocAdminStatusOpt.isDefined)
          assert(pocAdminStatusOpt.get.pocAdminId == pocAdmin.id)
        }).onErrorHandle { e =>
          fail(e)
        }.runSyncUnsafe()
      }
    }

    "fail to create create poc, admin and status when the header is invalid" in {
      withInjector { injector =>
        val processPocAdmin = injector.get[CsvProcessPocAdmin]
        val pocRepository = injector.get[PocRepository]
        val pocAdminRepository = injector.get[PocAdminRepository]

        (for {
          result <- processPocAdmin.createListOfPoCsAndAdmin(invalidHeaderPocAdminCsv, tenant, tenantContext)
          pocs <- pocRepository.getAllPocsByTenantId(tenant.id)
          pocAdmins <- pocAdminRepository.getAllPocAdminsByTenantId(tenant.id)
        } yield {
          result.left.get.shouldBe("technician_surname didn't equal expected header technician_surname*; the right header order would be: external_id*,poc_type*,poc_name*,street*,street_number*,additional_address,zipcode*,city*,county,federal_state,country*,phone*,certify_app*,logo_url,client_cert*,data_schema_id*,manager_surname*,manager_name*,manager_email*,manager_mobile_phone*,extra_config")
          assert(pocs.isEmpty)
          assert(pocAdmins.isEmpty)
        }).onErrorHandle(e => fail(e)).runSyncUnsafe()
      }
    }

    "success to create poc, admin and status for only valid rows" in {
      withInjector { injector =>
        val processPocAdmin = injector.get[CsvProcessPocAdmin]
        val pocRepository = injector.get[PocRepository]
        val pocAdminRepository = injector.get[PocAdminRepository]

        (for {
          result <-
            processPocAdmin.createListOfPoCsAndAdmin(validHeaderButBadRowsPocAdminCsv(pocId), tenant, tenantContext)
          pocs <- pocRepository.getAllPocsByTenantId(tenant.id)
          pocAdmins <- pocAdminRepository.getAllPocAdminsByTenantId(tenant.id)
        } yield {

          println(result)
          assert(result.isLeft)
          assert(pocs.length == 1)
          assert(pocAdmins.length == 1)
        }).onErrorHandle(e => fail(e)).runSyncUnsafe()
      }
    }
  }
}
