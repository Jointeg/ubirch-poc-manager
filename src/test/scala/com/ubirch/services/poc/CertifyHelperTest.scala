package com.ubirch.services.poc

import com.ubirch.ModelCreationHelper.{ createPoc, createPocAdmin, createPocAdminStatus, createTenant }
import com.ubirch.UnitTestBase

class CertifyHelperTest extends UnitTestBase {
  private val tenant = createTenant()
  private val poc = createPoc(tenantName = tenant.tenantName)
  private val pocAdmin = createPocAdmin(pocId = poc.id, tenantName = tenant.tenantName)
  private val pocAdminStatus = createPocAdminStatus(pocAdmin.id)
  "CertifyHelper" should {
    "success - addGroupsToCertifyUser" in {
      withInjector { injector =>
        val pocWithCertifyGroupId = poc.copy(certifyGroupId = Some("test"))
        val certifyHelper = injector.get[CertifyHelper]
        val newStatus = certifyHelper.addGroupsToCertifyUser(
          PocAdminAndStatus(pocAdmin, pocAdminStatus),
          pocWithCertifyGroupId,
          tenant).runSyncUnsafe()

        assert(newStatus.status.pocAdminGroupAssigned)
        assert(newStatus.status.pocTenantGroupAssigned)
        assert(newStatus.status.pocCertifyGroupAssigned)
      }
    }

    "fail - addGroupsToCertifyUser when certifyGroupId is None" in {
      withInjector { injector =>
        val certifyHelper = injector.get[CertifyHelper]
        assertThrows[PocAdminCreationError](certifyHelper.addGroupsToCertifyUser(
          PocAdminAndStatus(pocAdmin, pocAdminStatus),
          poc,
          tenant).runSyncUnsafe())
      }
    }
  }
}
