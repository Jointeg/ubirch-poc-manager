package com.ubirch.services.poc

import cats.implicits.toTraverseOps
import com.ubirch.{ PocConfig, UnitTestBase }
import com.ubirch.db.tables._
import com.ubirch.models.poc.Completed
import com.ubirch.services.poc.PocAdminTestHelper.{
  addPocAndStatusToRepository,
  createPocAdminAndStatus,
  createPocAdminStatusAllTrue
}
import com.ubirch.services.poc.PocTestHelper.{ addPocTripleToRepository, createPocTriple }
import com.ubirch.services.teamdrive.model.SpaceName
import com.ubirch.test.FakeTeamDriveClient

import java.util.UUID

class PocAdminCreatorTest extends UnitTestBase {
  "PocAdminCreator" should {
    "create pending poc admin successfully - web ident is false" in {
      withInjector { injector =>
        val pocConfig = injector.get[PocConfig]
        val webIdentRequired = false
        val creator = injector.get[PocAdminCreator]
        val tenantTable = injector.get[TenantRepositoryMock]
        val pocTable = injector.get[PocRepositoryMock]
        val pocStatusTable = injector.get[PocStatusRepositoryMock]
        val pocAdminTable = injector.get[PocAdminRepositoryMock]
        val pocAdminStatusTable = injector.get[PocAdminStatusRepositoryMock]
        val teamDriveClient = injector.get[FakeTeamDriveClient]

        val (poc, pocStatus, tenant) = createPocTriple(status = Completed)
        val (pocAdmin, pocAdminStatus) = createPocAdminAndStatus(poc, tenant, webIdentRequired)
        val spaceName = SpaceName.ofPoc("local", tenant, poc)
        val updatedPoc =
          poc.copy(certifyGroupId = Some(UUID.randomUUID().toString), adminGroupId = Some(UUID.randomUUID().toString))
        addPocTripleToRepository(tenantTable, pocTable, pocStatusTable, updatedPoc, pocStatus, tenant)
        addPocAndStatusToRepository(pocAdminTable, pocAdminStatusTable, pocAdmin, pocAdminStatus)
        teamDriveClient.createSpace(spaceName, spaceName.v).runSyncUnsafe()
        // Create static spaces
        pocConfig.pocTypeStaticSpaceNameMap.values.toList.traverse { spaceName =>
          teamDriveClient.createSpace(SpaceName.of(pocConfig.teamDriveStage, spaceName), spaceName)
        }.runSyncUnsafe()

        val result = creator.createPocAdmins().runSyncUnsafe()

        result match {
          case NoWaitingPocAdmin => fail("one poc admin should be found")
          case PocAdminCreationMaybeSuccess(list) =>
            assert(list.nonEmpty)
            assert(list.head.isRight)
            val result = list.head.right.get
            val allTrue = createPocAdminStatusAllTrue(webIdentRequired)
            val expected =
              allTrue.copy(pocAdminId = pocAdmin.id, lastUpdated = result.lastUpdated, created = result.created)

            result shouldBe expected

        }

        val newPocAdmin = pocAdminTable.getPocAdmin(pocAdmin.id).runSyncUnsafe()
        assert(newPocAdmin.isDefined)
        assert(newPocAdmin.get.status == Completed)
      }
    }

    "create pending poc admin successfully - web ident is true and successful" in {
      withInjector { injector =>
        val pocConfig = injector.get[PocConfig]
        val webIdentRequired = true
        val creator = injector.get[PocAdminCreator]
        val tenantTable = injector.get[TenantRepositoryMock]
        val pocTable = injector.get[PocRepositoryMock]
        val pocStatusTable = injector.get[PocStatusRepositoryMock]
        val pocAdminTable = injector.get[PocAdminRepositoryMock]
        val pocAdminStatusTable = injector.get[PocAdminStatusRepositoryMock]
        val teamDriveClient = injector.get[FakeTeamDriveClient]

        val (poc, pocStatus, tenant) = createPocTriple(status = Completed)
        val (pocAdmin, pocAdminStatus) = createPocAdminAndStatus(poc, tenant, webIdentRequired)
        val spaceName = SpaceName.ofPoc("local", tenant, poc)
        val updatedPoc =
          poc.copy(certifyGroupId = Some(UUID.randomUUID().toString), adminGroupId = Some(UUID.randomUUID().toString))
        addPocTripleToRepository(tenantTable, pocTable, pocStatusTable, updatedPoc, pocStatus, tenant)
        addPocAndStatusToRepository(pocAdminTable, pocAdminStatusTable, pocAdmin, pocAdminStatus)
        teamDriveClient.createSpace(spaceName, spaceName.v).runSyncUnsafe()
        // Create static spaces
        pocConfig.pocTypeStaticSpaceNameMap.values.toList.traverse { spaceName =>
          teamDriveClient.createSpace(SpaceName.of(pocConfig.teamDriveStage, spaceName), spaceName)
        }.runSyncUnsafe()

        // start process
        val result = creator.createPocAdmins().runSyncUnsafe()

        result match {
          case NoWaitingPocAdmin                  => fail("one poc admin should be found")
          case PocAdminCreationMaybeSuccess(list) => assert(list.head.isRight)
        }

        // No change
        val expectedPocAdminStatus = pocAdminStatusTable.getStatus(pocAdmin.id).runSyncUnsafe()
        assert(expectedPocAdminStatus.isDefined)
        pocAdminStatus shouldBe expectedPocAdminStatus.get

        pocAdminStatusTable.updateStatus(pocAdminStatus.copy(
          webIdentInitiated = Some(true),
          webIdentSuccess = Some(true))).runSyncUnsafe()

        // restart process
        val secondResult = creator.createPocAdmins().runSyncUnsafe()

        secondResult match {
          case NoWaitingPocAdmin => fail("one poc admin should be found")
          case PocAdminCreationMaybeSuccess(list) =>
            assert(list.nonEmpty)
            assert(list.head.isRight)
            val result = list.head.right.get
            val allTrue = createPocAdminStatusAllTrue(webIdentRequired)
            val expected =
              allTrue.copy(pocAdminId = pocAdmin.id, lastUpdated = result.lastUpdated, created = result.created)

            result shouldBe expected
        }

        val newPocAdmin = pocAdminTable.getPocAdmin(pocAdmin.id).runSyncUnsafe()
        assert(newPocAdmin.isDefined)
        assert(newPocAdmin.get.status == Completed)
      }
    }

    "create pending poc admin - after certifyGroupId is created" in {
      withInjector { injector =>
        val pocConfig = injector.get[PocConfig]
        val webIdentRequired = true
        val creator = injector.get[PocAdminCreator]
        val tenantTable = injector.get[TenantRepositoryMock]
        val pocTable = injector.get[PocRepositoryMock]
        val pocStatusTable = injector.get[PocStatusRepositoryMock]
        val pocAdminTable = injector.get[PocAdminRepositoryMock]
        val pocAdminStatusTable = injector.get[PocAdminStatusRepositoryMock]
        val teamDriveClient = injector.get[FakeTeamDriveClient]

        val (poc, pocStatus, tenant) = createPocTriple(status = Completed)
        val (pocAdmin, pocAdminStatus) = createPocAdminAndStatus(poc, tenant, webIdentRequired)
        val spaceName = SpaceName.ofPoc("local", tenant, poc)
        val webIdentSuccessPocAdminStatus =
          pocAdminStatus.copy(webIdentInitiated = Some(true), webIdentSuccess = Some(true))
        addPocTripleToRepository(tenantTable, pocTable, pocStatusTable, poc, pocStatus, tenant)
        addPocAndStatusToRepository(pocAdminTable, pocAdminStatusTable, pocAdmin, webIdentSuccessPocAdminStatus)
        teamDriveClient.createSpace(spaceName, spaceName.v).runSyncUnsafe()
        // Create static spaces
        pocConfig.pocTypeStaticSpaceNameMap.values.toList.traverse { spaceName =>
          teamDriveClient.createSpace(SpaceName.of(pocConfig.teamDriveStage, spaceName), spaceName)
        }.runSyncUnsafe()

        // start process
        val result = creator.createPocAdmins().runSyncUnsafe()

        result match {
          case NoWaitingPocAdmin                  => fail("one poc admin should be found")
          case PocAdminCreationMaybeSuccess(list) => assert(list.head.isLeft)
        }

        val updatedPocAdminStatus = pocAdminStatusTable.getStatus(pocAdmin.id).runSyncUnsafe()
        val expected = webIdentSuccessPocAdminStatus.copy(
          certifyUserCreated = true,
          keycloakEmailSent = false,
          pocAdminGroupAssigned = false,
          invitedToTeamDrive = Some(true),
          invitedToStaticTeamDrive = Some(true),
          errorMessage = Some(s"adminGroupId is missing in poc ${poc.id}")
        )
        assert(updatedPocAdminStatus.isDefined)
        updatedPocAdminStatus.get shouldBe expected

        pocTable.updatePoc(poc.copy(
          certifyGroupId = Some(UUID.randomUUID().toString),
          adminGroupId = Some(UUID.randomUUID().toString))).runSyncUnsafe()

        // restart process
        val secondResult = creator.createPocAdmins().runSyncUnsafe()

        secondResult match {
          case NoWaitingPocAdmin => fail("one poc admin should be found")
          case PocAdminCreationMaybeSuccess(list) =>
            assert(list.nonEmpty)
            assert(list.head.isRight)
            val result = list.head.right.get
            val allTrue = createPocAdminStatusAllTrue(webIdentRequired)
            val expectedFinal =
              allTrue.copy(
                pocAdminId = pocAdmin.id,
                lastUpdated = result.lastUpdated,
                created = result.created
              )
            result shouldBe expectedFinal
        }

        val newPocAdmin = pocAdminTable.getPocAdmin(pocAdmin.id).runSyncUnsafe()
        assert(newPocAdmin.isDefined)
        assert(newPocAdmin.get.status == Completed)
      }
    }
  }
}
