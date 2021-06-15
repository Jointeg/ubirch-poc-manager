package com.ubirch.services.poc

import cats.implicits.toTraverseOps
import com.ubirch.{ PocConfig, UnitTestBase }
import com.ubirch.db.tables.{
  PocAdminRepositoryMock,
  PocAdminStatusRepositoryMock,
  PocRepositoryMock,
  PocStatusRepositoryMock,
  TenantRepositoryMock
}
import com.ubirch.models.poc.{ Completed, PocAdminStatus }
import com.ubirch.services.poc.PocAdminTestHelper.{ addPocAndStatusToRepository, createPocAdminAndStatus }
import com.ubirch.services.poc.PocTestHelper.{ addPocTripleToRepository, createPocStatusAllTrue, createPocTriple }
import com.ubirch.services.teamdrive.model.SpaceName
import com.ubirch.test.FakeTeamDriveClient
import monix.reactive.Observable
import org.scalatest.Assertion

import java.util.UUID
import scala.concurrent.duration.DurationInt

class PocAdminCreatorLoopTest extends UnitTestBase {

  "Poc Admin Creation Loop" should {
    "create pending poc admin first after adding it to database and web-ident successful" in {
      withInjector { injector =>
        val pocConfig = injector.get[PocConfig]
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
        val spaceName = SpaceName.ofPoc("local", tenant, poc)
        //start process
        val pocAdminCreation = loop.startPocAdminCreationLoop(resp => Observable(resp))
        awaitForTwoTicks(pocAdminCreation)

        // not process because the data is not in database
        pocAdminStatusTable.getStatus(pocAdminStatus.pocAdminId).runSyncUnsafe() shouldBe None
        // store objects in database
        val updatedPoc =
          poc.copy(certifyGroupId = Some(UUID.randomUUID().toString), adminGroupId = Some(UUID.randomUUID().toString))
        addPocTripleToRepository(tenantTable, pocTable, pocStatusTable, updatedPoc, pocStatus, tenant)
        addPocAndStatusToRepository(pocAdminTable, pocAdminStatusTable, pocAdmin, pocAdminStatus)
        teamDriveClient.createSpace(spaceName, spaceName.v).runSyncUnsafe()
        // Create static spaces
        pocConfig.pocTypeStaticSpaceNameMap.values.toList.traverse { spaceName =>
          teamDriveClient.createSpace(SpaceName.of(pocConfig.teamDriveStage, spaceName), spaceName)
        }.runSyncUnsafe()

        awaitForTwoTicks(pocAdminCreation)
        // not processed yet because poc is not completed yet
        pocTable.updatePoc(updatedPoc.copy(status = Completed)).runSyncUnsafe()

        awaitForTwoTicks(pocAdminCreation)
        // not process because web ident is not successful yet
        val adminStatusPoc = pocAdminStatusTable.getStatus(pocAdmin.id).runSyncUnsafe()
        adminStatusPoc.get shouldBe pocAdminStatus
        pocAdminStatusTable.updateStatus(pocAdminStatus.copy(
          webIdentInitiated = Some(true),
          webIdentSuccess = Some(true))).runSyncUnsafe()

        awaitForTwoTicks(pocAdminCreation)
        val status = pocAdminStatusTable.getStatus(pocAdminStatus.pocAdminId).runSyncUnsafe().get
        assertStatusAllTrue(status)
      }
    }
  }

  private def assertStatusAllTrue(status: PocAdminStatus): Assertion = {
    assert(status.certifyUserCreated)
    assert(status.keycloakEmailSent)
    assert(status.pocAdminGroupAssigned)
    assert(status.invitedToTeamDrive.get)
    assert(status.invitedToStaticTeamDrive.get)
  }
}
