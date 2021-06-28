package com.ubirch.services.superadmin

import com.ubirch.ModelCreationHelper.{ createTenant, createTenantRequest }
import com.ubirch.controllers.SuperAdminContext
import com.ubirch.db.tables.TenantRepository
import com.ubirch.models.auth.CertIdentifier
import com.ubirch.models.keycloak.group.GroupId
import com.ubirch.models.tenant.{ DeviceAndCertifyGroups, OrgId, TenantId }
import com.ubirch.services.poc.{ CertHandler, CertificateCreationError }
import com.ubirch.services.teamdrive.TeamDriveService
import com.ubirch.{ PocConfig, UnitTestBase }
import monix.eval.Task
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar.mock

import java.util.UUID

class SuperAdminServiceSpec extends UnitTestBase {

  private val tenantRequest = createTenantRequest()
  private val superAdminContext = SuperAdminContext(UUID.randomUUID())
  "SuperAdminService" should {

    "create tenant with shared cert required successfully" in {
      withInjector { injector =>
        val superAdminSvc = injector.get[SuperAdminService]
        val tenantRepo = injector.get[TenantRepository]
        val r = superAdminSvc
          .createTenant(tenantRequest, superAdminContext)
          .runSyncUnsafe()
        r.isRight shouldBe true
        val t = tenantRepo.getTenant(r.right.get).runSyncUnsafe()
        t.isDefined shouldBe true
        t.get.sharedAuthCert.isDefined shouldBe true
      }
    }

    "should throw exception on group creation failure" in {
      withInjector { injector =>
        val repo = injector.get[TenantRepository]
        val keycloakHelperMock = mock[TenantKeycloakHelper]
        when(keycloakHelperMock.doKeycloakRelatedTasks(tenantRequest))
          .thenReturn(Task.raiseError(TenantCreationException("")))

        val superAdminSvc =
          new DefaultSuperAdminService(
            repo,
            mock[CertHandler],
            mock[TeamDriveService],
            mock[PocConfig],
            keycloakHelperMock)
        assertThrows[TenantCreationException](superAdminSvc.createTenant(
          tenantRequest,
          superAdminContext).runSyncUnsafe())
      }
    }

    "should throw exception on org cert creation failure" in {
      withInjector { injector =>
        val repo = injector.get[TenantRepository]
        val certHandlerMock = mock[CertHandler]

        val orgIdentifier = CertIdentifier.tenantOrgCert(tenantRequest.tenantName)
        val orgId = OrgId(TenantId(tenantRequest.tenantName).value)
        when(certHandlerMock.createOrganisationalCertificate(orgId.value.asJava(), orgIdentifier))
          .thenReturn(Task(Left(CertificateCreationError("error"))))

        val keycloakHelperMock = mock[TenantKeycloakHelper]
        when(keycloakHelperMock.doKeycloakRelatedTasks(tenantRequest))
          .thenReturn(Task(DeviceAndCertifyGroups(GroupId("id"), GroupId("id"), None)))

        val superAdminSvc =
          new DefaultSuperAdminService(
            repo,
            certHandlerMock,
            mock[TeamDriveService],
            mock[PocConfig],
            keycloakHelperMock)
        assertThrows[TenantCreationException](superAdminSvc.createTenant(
          tenantRequest,
          superAdminContext).runSyncUnsafe())
      }
    }

    "should throw exception on org unit cert creation failure" in {
      withInjector { injector =>
        val repo = injector.get[TenantRepository]
        val certHandlerMock = mock[CertHandler]
        val teamDriveServiceMock = mock[TeamDriveService]
        val pocConfigMock = mock[PocConfig]
        val tenant = createTenant()

        when(certHandlerMock.createOrganisationalUnitCertificate(any[UUID], any[UUID], any[CertIdentifier]))
          .thenReturn(Task(Left(CertificateCreationError("error"))))

        val superAdminSvc =
          new DefaultSuperAdminService(
            repo,
            certHandlerMock,
            teamDriveServiceMock,
            pocConfigMock,
            mock[TenantKeycloakHelper])
        assertThrows[TenantCreationException](superAdminSvc.createOrgUnitCert(tenant).runSyncUnsafe())
      }
    }

    "should throw exception on shared auth cert creation failure" in {
      withInjector { injector =>
        val repo = injector.get[TenantRepository]
        val certHandlerMock = mock[CertHandler]
        val teamDriveServiceMock = mock[TeamDriveService]
        val pocConfigMock = mock[PocConfig]

        val tenant = createTenant()

        when(certHandlerMock.createSharedAuthCertificate(any[UUID], any[UUID], any[CertIdentifier]))
          .thenReturn(Task(Left(CertificateCreationError("error"))))

        val superAdminSvc =
          new DefaultSuperAdminService(
            repo,
            certHandlerMock,
            teamDriveServiceMock,
            pocConfigMock,
            mock[TenantKeycloakHelper])

        assertThrows[TenantCreationException](superAdminSvc.createSharedAuthCert(tenant).runSyncUnsafe())
      }
    }
  }
}
