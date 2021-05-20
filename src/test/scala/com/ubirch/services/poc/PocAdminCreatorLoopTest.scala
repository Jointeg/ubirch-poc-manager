package com.ubirch.services.poc

import com.ubirch.UnitTestBase
import com.ubirch.db.tables.{
  PocAdminRepositoryMock,
  PocAdminStatusRepositoryMock,
  PocRepositoryMock,
  PocStatusRepositoryMock,
  TenantRepositoryMock
}
import com.ubirch.models.poc.PocAdminStatus
import com.ubirch.services.poc.PocAdminTestHelper.{ addPocAndStatusToRepository, createPocAdminAndStatus }
import com.ubirch.services.poc.PocTestHelper.{ addPocTripleToRepository, createPocTriple }
import com.ubirch.test.FakeTeamDriveClient
import monix.reactive.Observable
import org.scalatest.Assertion

import java.util.UUID

class PocAdminCreatorLoopTest extends UnitTestBase {

  "Poc Admin Creation Loop" should {
    "create pending poc admin first after adding it to database and web-ident successful" in {
      withInjector { injector =>
        val loop = injector.get[PocAdminCreationLoop]
        val webIdentRequired = true
        val tenantTable = injector.get[TenantRepositoryMock]
        val pocTable = injector.get[PocRepositoryMock]
        val pocStatusTable = injector.get[PocStatusRepositoryMock]
        val pocAdminTable = injector.get[PocAdminRepositoryMock]
        val pocAdminStatusTable = injector.get[PocAdminStatusRepositoryMock]
        val teamDriveClient = injector.get[FakeTeamDriveClient]

        val (poc, pocStatus, tenant) = createPocTriple()
        val (pocAdmin, pocAdminStatus) = createPocAdminAndStatus(poc, tenant, webIdentRequired)
        val spaceName = s"local_${tenant.tenantName.value}"
        //start process
        val pocAdminCreation = loop.startPocAdminCreationLoop(resp => Observable(resp)).subscribe()
        Thread.sleep(4000)

        // not process because the data is not in database
        pocAdminStatusTable.getStatus(pocAdminStatus.pocAdminId).runSyncUnsafe() shouldBe None
        // store objects in database
        val updatedPoc = poc.copy(certifyGroupId = Some(UUID.randomUUID().toString))
        addPocTripleToRepository(tenantTable, pocTable, pocStatusTable, updatedPoc, pocStatus, tenant)
        addPocAndStatusToRepository(pocAdminTable, pocAdminStatusTable, pocAdmin, pocAdminStatus)
        teamDriveClient.createSpace(spaceName, spaceName).runSyncUnsafe()

        Thread.sleep(4000)
        // not process because web ident is not successful yet
        val adminStatusPoc = pocAdminStatusTable.getStatus(pocAdmin.id).runSyncUnsafe()
        adminStatusPoc.get shouldBe pocAdminStatus
        pocAdminStatusTable.updateStatus(pocAdminStatus.copy(
          webIdentTriggered = Some(true),
          webIdentIdentifierSuccess = Some(true))).runSyncUnsafe()

        Thread.sleep(3000)
        val status = pocAdminStatusTable.getStatus(pocAdminStatus.pocAdminId).runSyncUnsafe().get
        assertStatusAllTrue(status)
        pocAdminCreation.cancel()
      }
    }
  }

  private def assertStatusAllTrue(status: PocAdminStatus): Assertion = {
    assert(status.certifierUserCreated)
    assert(status.keycloakEmailSent)
    assert(status.pocAdminGroupAssigned)
    assert(status.pocCertifyGroupAssigned)
    assert(status.pocTenantGroupAssigned)
    assert(status.invitedToTeamDrive)
  }
}
