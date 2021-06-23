package com.ubirch.services.poc

import com.ubirch.ModelCreationHelper.{ createPoc, createPocAdmin, createPocAdminStatus, createTenant }
import com.ubirch.UnitTestBase

import java.util.UUID

class AdminCertifyHelperTest extends UnitTestBase {
  private val tenant = createTenant()
  private val poc = createPoc(tenantName = tenant.tenantName)
  private val pocAdmin = createPocAdmin(pocId = poc.id, tenantId = tenant.id)
  private val pocAdminStatus = createPocAdminStatus(pocAdmin, poc)
  "CertifyHelper" should {
    "success - addGroupsToCertifyUser" in {
      withInjector { injector =>
        val pocWithCertifyGroupId =
          poc.copy(certifyGroupId = Some("test"), adminGroupId = Some(UUID.randomUUID().toString))
        val certifyHelper = injector.get[AdminCertifyHelper]
        val newStatus = certifyHelper.addGroupsToCertifyUser(
          PocAdminAndStatus(pocAdmin.copy(certifyUserId = Some(UUID.randomUUID())), pocAdminStatus),
          pocWithCertifyGroupId).runSyncUnsafe()

        assert(newStatus.status.pocAdminGroupAssigned)
      }
    }

    "fail - addGroupsToCertifyUser when certifyUserId is None" in {
      withInjector { injector =>
        val pocWithCertifyGroupId = poc.copy(certifyGroupId = Some("test"))
        val certifyHelper = injector.get[AdminCertifyHelper]
        assertThrows[PocAdminCreationError](certifyHelper.addGroupsToCertifyUser(
          PocAdminAndStatus(pocAdmin, pocAdminStatus),
          pocWithCertifyGroupId).runSyncUnsafe())
      }
    }

    "fail - addGroupsToCertifyUser when certifyGroupId is None" in {
      withInjector { injector =>
        val certifyHelper = injector.get[AdminCertifyHelper]
        assertThrows[PocAdminCreationError](certifyHelper.addGroupsToCertifyUser(
          PocAdminAndStatus(pocAdmin, pocAdminStatus),
          poc).runSyncUnsafe())
      }
    }
  }
}
