package com.ubirch.services.poc

import com.ubirch.ModelCreationHelper.createTenant
import com.ubirch.UnitTestBase
import com.ubirch.controllers.TenantAdminContext
import com.ubirch.db.tables.{ PocRepository, PocStatusRepository }
import com.ubirch.testutils.CentralCsvProvider.{
  invalidHeaderPocOnlyCsv,
  validHeaderButBadRowsPocOnlyCsv,
  validPocOnlyCsv
}

import java.util.UUID

class CsvProcessPocTest extends UnitTestBase {

  private val pocId = UUID.randomUUID()

  private val tenant = createTenant()
  private val tenantContext = TenantAdminContext(UUID.randomUUID(), tenant.id.value.asJava())

  "ProcessPoc" should {
    "create poc and pocStatus" in {
      withInjector { injector =>
        val processPoc = injector.get[CsvProcessPoc]
        val pocRepository = injector.get[PocRepository]
        val pocStatusRepository = injector.get[PocStatusRepository]

        (for {
          result <- processPoc.createListOfPoCs(validPocOnlyCsv(pocId), tenant, tenantContext)
          pocs <- pocRepository.getAllPocsByTenantId(tenant.id)
          poc = pocs.head
          pocStatusOpt <- pocStatusRepository.getPocStatus(poc.id)
        } yield {
          assert(result.isRight)
          assert(pocs.length == 1)
          assert(pocStatusOpt.isDefined)
          assert(pocStatusOpt.get.pocId == poc.id)
        }).onErrorHandle(e => fail(e)).runSyncUnsafe()
      }
    }

    "fail to create poc and pocStatus when the header is invalid" in {
      withInjector { injector =>
        val processPoc = injector.get[CsvProcessPoc]
        val pocRepository = injector.get[PocRepository]

        (for {
          result <- processPoc.createListOfPoCs(invalidHeaderPocOnlyCsv, tenant, tenantContext)
          pocs <- pocRepository.getAllPocsByTenantId(tenant.id)
        } yield {
          result.left.get.shouldBe("poc_id* didn't equal expected header external_id*; the right header order would be: external_id*,poc_type*,poc_name*,street*,street_number*,additional_address,zipcode*,city*,county,federal_state,country*,phone*,logo_url,manager_surname*,manager_name*,manager_email*,manager_mobile_phone*,extra_config")
          assert(pocs.isEmpty)
        }).runSyncUnsafe()
      }
    }

    "success to create poc and pocStatus for only valid rows" in {
      withInjector { injector =>
        val processPoc = injector.get[CsvProcessPoc]
        val pocRepository = injector.get[PocRepository]

        (for {
          result <- processPoc.createListOfPoCs(validHeaderButBadRowsPocOnlyCsv(pocId), tenant, tenantContext)
          pocs <- pocRepository.getAllPocsByTenantId(tenant.id)
        } yield {
          assert(result.isLeft)
          assert(pocs.length == 2)
        }).onErrorHandle(e => fail(e)).runSyncUnsafe()
      }
    }
  }
}
