package com.ubirch.services.poc

import com.ubirch.UnitTestBase
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
        val webIdentRequired = false
        val creator = injector.get[PocAdminCreator]
        val tenantTable = injector.get[TenantRepositoryMock]
        val pocTable = injector.get[PocRepositoryMock]
        val pocStatusTable = injector.get[PocStatusRepositoryMock]
        val pocAdminTable = injector.get[PocAdminRepositoryMock]
        val pocAdminStatusTable = injector.get[PocAdminStatusRepositoryMock]
        val teamDriveClient = injector.get[FakeTeamDriveClient]

        val (poc, pocStatus, tenant) = createPocTriple(clientCertRequired = true)
        val (pocAdmin, pocAdminStatus) = createPocAdminAndStatus(poc, tenant, webIdentRequired)
        val spaceName = s"local_${tenant.tenantName.value}"
        val updatedPoc =
          poc.copy(certifyGroupId = Some(UUID.randomUUID().toString), adminGroupId = Some(UUID.randomUUID().toString))
        addPocTripleToRepository(tenantTable, pocTable, pocStatusTable, updatedPoc, pocStatus, tenant)
        addPocAndStatusToRepository(pocAdminTable, pocAdminStatusTable, pocAdmin, pocAdminStatus)
        teamDriveClient.createSpace(SpaceName(spaceName), spaceName).runSyncUnsafe()

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

            expected shouldBe result
        }

        val newPocAdmin = pocAdminTable.getPocAdmin(pocAdmin.id).runSyncUnsafe()
        assert(newPocAdmin.isDefined)
        assert(newPocAdmin.get.status == Completed)
      }
    }

    "create pending poc admin successfully - web ident is true and successful" in {
      withInjector { injector =>
        val webIdentRequired = true
        val creator = injector.get[PocAdminCreator]
        val tenantTable = injector.get[TenantRepositoryMock]
        val pocTable = injector.get[PocRepositoryMock]
        val pocStatusTable = injector.get[PocStatusRepositoryMock]
        val pocAdminTable = injector.get[PocAdminRepositoryMock]
        val pocAdminStatusTable = injector.get[PocAdminStatusRepositoryMock]
        val teamDriveClient = injector.get[FakeTeamDriveClient]

        val (poc, pocStatus, tenant) = createPocTriple(clientCertRequired = true)
        val (pocAdmin, pocAdminStatus) = createPocAdminAndStatus(poc, tenant, webIdentRequired)
        val spaceName = s"local_${tenant.tenantName.value}"
        val updatedPoc =
          poc.copy(certifyGroupId = Some(UUID.randomUUID().toString), adminGroupId = Some(UUID.randomUUID().toString))
        addPocTripleToRepository(tenantTable, pocTable, pocStatusTable, updatedPoc, pocStatus, tenant)
        addPocAndStatusToRepository(pocAdminTable, pocAdminStatusTable, pocAdmin, pocAdminStatus)
        teamDriveClient.createSpace(SpaceName(spaceName), spaceName).runSyncUnsafe()

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

            expected shouldBe result
        }

        val newPocAdmin = pocAdminTable.getPocAdmin(pocAdmin.id).runSyncUnsafe()
        assert(newPocAdmin.isDefined)
        assert(newPocAdmin.get.status == Completed)
      }
    }

    "create pending poc admin - after certifyGroupId is created" in {
      withInjector { injector =>
        val webIdentRequired = true
        val creator = injector.get[PocAdminCreator]
        val tenantTable = injector.get[TenantRepositoryMock]
        val pocTable = injector.get[PocRepositoryMock]
        val pocStatusTable = injector.get[PocStatusRepositoryMock]
        val pocAdminTable = injector.get[PocAdminRepositoryMock]
        val pocAdminStatusTable = injector.get[PocAdminStatusRepositoryMock]
        val teamDriveClient = injector.get[FakeTeamDriveClient]

        val (poc, pocStatus, tenant) = createPocTriple(clientCertRequired = true)
        val (pocAdmin, pocAdminStatus) = createPocAdminAndStatus(poc, tenant, webIdentRequired)
        val spaceName = s"local_${tenant.tenantName.value}"
        val webIdentSuccessPocAdminStatus =
          pocAdminStatus.copy(webIdentInitiated = Some(true), webIdentSuccess = Some(true))
        addPocTripleToRepository(tenantTable, pocTable, pocStatusTable, poc, pocStatus, tenant)
        addPocAndStatusToRepository(pocAdminTable, pocAdminStatusTable, pocAdmin, webIdentSuccessPocAdminStatus)
        teamDriveClient.createSpace(SpaceName(spaceName), spaceName).runSyncUnsafe()

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
                created = result.created,
                errorMessage = Some(s"adminGroupId is missing in poc ${poc.id}")
              )
            expectedFinal shouldBe result
        }

        val newPocAdmin = pocAdminTable.getPocAdmin(pocAdmin.id).runSyncUnsafe()
        assert(newPocAdmin.isDefined)
        assert(newPocAdmin.get.status == Completed)
      }
    }
  }
}
