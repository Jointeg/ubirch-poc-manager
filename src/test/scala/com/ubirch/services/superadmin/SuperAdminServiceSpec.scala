package com.ubirch.services.superadmin

import com.ubirch.ModelCreationHelper.{ createTenant, createTenantRequest }
import com.ubirch.{ PocConfig, UnitTestBase }
import com.ubirch.db.tables.TenantRepository
import com.ubirch.models.auth.CertIdentifier
import com.ubirch.models.tenant.{ OrgId, OrgUnitId, TenantId }
import com.ubirch.services.auth.AESEncryption
import com.ubirch.services.poc.{ CertHandler, CertificateCreationError }
import com.ubirch.services.teamdrive.{ model, TeamDriveService }
import com.ubirch.services.teamdrive.model.TeamDriveClient
import com.ubirch.test.FakeTeamDriveClient
import monix.eval.Task
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar.mock

import java.util.UUID

class SuperAdminServiceSpec extends UnitTestBase {

  private val tenantRequest = createTenantRequest()

  "SuperAdminService" should {

    "create tenant with shared cert required successfully" in {
      withInjector { injector =>
        val superAdminSvc = injector.get[SuperAdminService]
        val tenantRepo = injector.get[TenantRepository]
        val r = superAdminSvc
          .createTenant(tenantRequest)
          .runSyncUnsafe()
        r.isRight shouldBe true
        val t = tenantRepo.getTenant(r.right.get).runSyncUnsafe()
        t.isDefined shouldBe true
        t.get.orgUnitId.isDefined shouldBe true
        t.get.sharedAuthCert.isDefined shouldBe true
      }
    }

    "create tenant without sharedCert required successfully" in {
      withInjector { injector =>
        val superAdminSvc = injector.get[SuperAdminService]
        val tenantRepo = injector.get[TenantRepository]
        val request = tenantRequest.copy(sharedAuthCertRequired = false)
        val r = superAdminSvc
          .createTenant(request)
          .runSyncUnsafe()
        r.isRight shouldBe true
        val t = tenantRepo.getTenant(r.right.get).runSyncUnsafe()
        t.isDefined shouldBe true
        t.get.orgUnitId.isEmpty shouldBe true
        t.get.sharedAuthCert.isEmpty shouldBe true
      }
    }

    "should throw exception on org cert creation failure" in {
      withInjector { injector =>
        val aesEncryption = injector.get[AESEncryption]
        val repo = injector.get[TenantRepository]
        val certHandlerMock = mock[CertHandler]
        val teamDriveServiceMock = mock[TeamDriveService]
        val pocConfigMock = mock[PocConfig]
        val orgIdentifier = CertIdentifier.tenantOrgCert(tenantRequest.tenantName)
        val orgId = OrgId(TenantId(tenantRequest.tenantName).value)
        when(certHandlerMock.createOrganisationalCertificate(orgId.value.asJava(), orgIdentifier))
          .thenReturn(Task(Left(CertificateCreationError("error"))))
        val superAdminSvc =
          new DefaultSuperAdminService(aesEncryption, repo, certHandlerMock, teamDriveServiceMock, pocConfigMock)
        assertThrows[TenantCreationException](superAdminSvc.createTenant(tenantRequest).runSyncUnsafe())
      }
    }

    "should throw exception on org unit cert creation failure" in {
      withInjector { injector =>
        val aesEncryption = injector.get[AESEncryption]
        val repo = injector.get[TenantRepository]
        val certHandlerMock = mock[CertHandler]
        val teamDriveServiceMock = mock[TeamDriveService]
        val pocConfigMock = mock[PocConfig]
        val tenant = createTenant()

        when(certHandlerMock.createOrganisationalUnitCertificate(any[UUID], any[UUID], any[CertIdentifier]))
          .thenReturn(Task(Left(CertificateCreationError("error"))))

        val superAdminSvc =
          new DefaultSuperAdminService(aesEncryption, repo, certHandlerMock, teamDriveServiceMock, pocConfigMock)
        assertThrows[TenantCreationException](superAdminSvc.createOrgUnitCert(tenant).runSyncUnsafe())
      }
    }

    "should throw exception on shared auth cert creation failure" in {
      withInjector { injector =>
        val aesEncryption = injector.get[AESEncryption]
        val repo = injector.get[TenantRepository]
        val certHandlerMock = mock[CertHandler]
        val teamDriveServiceMock = mock[TeamDriveService]
        val pocConfigMock = mock[PocConfig]

        val tenant = createTenant()
        val orgUnitCertId = UUID.randomUUID()

        when(certHandlerMock.createSharedAuthCertificate(any[UUID], any[UUID], any[CertIdentifier]))
          .thenReturn(Task(Left(CertificateCreationError("error"))))

        val superAdminSvc =
          new DefaultSuperAdminService(aesEncryption, repo, certHandlerMock, teamDriveServiceMock, pocConfigMock)

        assertThrows[TenantCreationException](superAdminSvc
          .createSharedAuthCert(tenant, OrgUnitId(orgUnitCertId)).runSyncUnsafe())
      }
    }

  }
}
