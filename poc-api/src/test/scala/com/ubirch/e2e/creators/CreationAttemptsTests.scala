package com.ubirch.e2e.creators

import cats.implicits.toTraverseOps
import com.ubirch.ModelCreationHelper.{ addEmployeeTripleToRepository, createTenantPocEmployeeAndStatus }
import com.ubirch.PocConfig
import com.ubirch.db.tables._
import com.ubirch.e2e.E2ETestBase
import com.ubirch.models.poc._
import com.ubirch.models.tenant.{ TenantCertifyGroupId, TenantDeviceGroupId }
import com.ubirch.services.poc.PocAdminTestHelper.{ addPocAndStatusToRepository, createPocAdminAndStatus }
import com.ubirch.services.poc.PocTestHelper._
import com.ubirch.services.poc.{ PocAdminCreationLoop, PocCreationLoop, PocEmployeeCreationLoop }
import com.ubirch.services.teamdrive.model.SpaceName
import com.ubirch.test.FakeTeamDriveClient

import java.net.URL
import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.util.Random

class CreationAttemptsTests extends E2ETestBase {

  "PocCreator" should {
    "move PoC to Aborted state after 10 unsuccessful processing" in {
      withInjector { injector =>
        val loop = injector.get[PocCreationLoop]
        val tenantTable = injector.get[TenantRepository]
        val pocTable = injector.get[PocRepository]
        val pocStatusTable = injector.get[PocStatusRepository]

        val pocs = (1 to 15).map { _ =>
          val (poc, pocStatus, tenant) = createPocTriple(Random.alphanumeric.take(10).mkString)
          val updatedTenant = tenant.copy(
            deviceGroupId = TenantDeviceGroupId("wrong device group"),
            certifyGroupId = TenantCertifyGroupId("wrong certify group")
          )

          val updatedPoc = poc.copy(logoUrl = Some(LogoURL(
            new URL("https://www.scala-lang.org/resources/img/frontpage/scala-spiral.png"))))
          val updatedStatus = pocStatus.copy(logoRequired = true, logoStored = Some(true))
          addPocTripleToRepository(tenantTable, pocTable, pocStatusTable, updatedPoc, updatedStatus, updatedTenant)

          (poc, pocStatus, updatedTenant)
        }

        awaitForTicks(loop.startPocCreationLoop, 15, 30.seconds)

        // if the connection would not be released properly, than the test would fail here with only 10 PoC being transitioned to Processing state
        pocs.foreach {
          case (poc, _, _) =>
            val updatedPoc = await(pocTable.getPoc(poc.id)).value
            updatedPoc.status shouldBe Aborted
            updatedPoc.creationAttempts shouldBe 10
        }
      }
    }
  }

  "PoCAdminCreator" should {
    "move PoC Admin to Aborted state after 10 unsuccessful processing" in {
      withInjector { injector =>
        val pocConfig = injector.get[PocConfig]
        val webIdentRequired = false
        val loop = injector.get[PocAdminCreationLoop]
        val tenantTable = injector.get[TenantRepository]
        val pocTable = injector.get[PocRepository]
        val pocStatusTable = injector.get[PocStatusRepository]
        val pocAdminTable = injector.get[PocAdminRepository]
        val pocAdminStatusTable = injector.get[PocAdminStatusRepository]
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

        awaitForTicks(loop.startPocAdminCreationLoop, 15, 30.seconds)

        val newPocAdmin = pocAdminTable.getPocAdmin(pocAdmin.id).runSyncUnsafe()
        assert(newPocAdmin.value.status == Aborted)
        assert(newPocAdmin.value.creationAttempts == 10)
      }
    }

    "move PoC Admin directly to Aborted state if PoC is in Aborted status" in {
      withInjector { injector =>
        val pocConfig = injector.get[PocConfig]
        val webIdentRequired = false
        val loop = injector.get[PocAdminCreationLoop]
        val tenantTable = injector.get[TenantRepository]
        val pocTable = injector.get[PocRepository]
        val pocStatusTable = injector.get[PocStatusRepository]
        val pocAdminTable = injector.get[PocAdminRepository]
        val pocAdminStatusTable = injector.get[PocAdminStatusRepository]
        val teamDriveClient = injector.get[FakeTeamDriveClient]

        val (poc, pocStatus, tenant) = createPocTriple(status = Completed)
        val (pocAdmin, pocAdminStatus) = createPocAdminAndStatus(poc, tenant, webIdentRequired)
        val spaceName = SpaceName.ofPoc("local", tenant, poc)
        val updatedPoc =
          poc.copy(
            certifyGroupId = Some(UUID.randomUUID().toString),
            adminGroupId = Some(UUID.randomUUID().toString),
            status = Aborted)
        addPocTripleToRepository(tenantTable, pocTable, pocStatusTable, updatedPoc, pocStatus, tenant)
        addPocAndStatusToRepository(pocAdminTable, pocAdminStatusTable, pocAdmin, pocAdminStatus)
        teamDriveClient.createSpace(spaceName, spaceName.v).runSyncUnsafe()
        // Create static spaces
        pocConfig.pocTypeStaticSpaceNameMap.values.toList.traverse { spaceName =>
          teamDriveClient.createSpace(SpaceName.of(pocConfig.teamDriveStage, spaceName), spaceName)
        }.runSyncUnsafe()

        awaitForTwoTicks(loop.startPocAdminCreationLoop, 10.seconds)

        val newPocAdmin = pocAdminTable.getPocAdmin(pocAdmin.id).runSyncUnsafe()
        assert(newPocAdmin.value.status == Aborted)
        assert(newPocAdmin.value.creationAttempts == 0)
      }
    }

    "not increase counter if PoC is not completed" in {
      withInjector { injector =>
        val pocConfig = injector.get[PocConfig]
        val webIdentRequired = false
        val loop = injector.get[PocAdminCreationLoop]
        val tenantTable = injector.get[TenantRepository]
        val pocTable = injector.get[PocRepository]
        val pocStatusTable = injector.get[PocStatusRepository]
        val pocAdminTable = injector.get[PocAdminRepository]
        val pocAdminStatusTable = injector.get[PocAdminStatusRepository]
        val teamDriveClient = injector.get[FakeTeamDriveClient]

        val (poc, pocStatus, tenant) = createPocTriple(status = Completed)
        val (pocAdmin, pocAdminStatus) = createPocAdminAndStatus(poc, tenant, webIdentRequired)
        val spaceName = SpaceName.ofPoc("local", tenant, poc)
        val updatedPoc =
          poc.copy(
            certifyGroupId = Some(UUID.randomUUID().toString),
            adminGroupId = Some(UUID.randomUUID().toString),
            status = Processing)
        addPocTripleToRepository(tenantTable, pocTable, pocStatusTable, updatedPoc, pocStatus, tenant)
        addPocAndStatusToRepository(pocAdminTable, pocAdminStatusTable, pocAdmin, pocAdminStatus)
        teamDriveClient.createSpace(spaceName, spaceName.v).runSyncUnsafe()
        // Create static spaces
        pocConfig.pocTypeStaticSpaceNameMap.values.toList.traverse { spaceName =>
          teamDriveClient.createSpace(SpaceName.of(pocConfig.teamDriveStage, spaceName), spaceName)
        }.runSyncUnsafe()

        awaitForTwoTicks(loop.startPocAdminCreationLoop, 10.seconds)

        val newPocAdmin = pocAdminTable.getPocAdmin(pocAdmin.id).runSyncUnsafe()
        assert(newPocAdmin.value.status == Pending)
        assert(newPocAdmin.value.creationAttempts == 0)
      }
    }
  }

  "PoCEmployeeCreator" should {
    "move PoC Employee to Aborted state after 10 unsuccessful processing" in {
      withInjector { injector =>
        val loop = injector.get[PocEmployeeCreationLoop]
        val pocTable = injector.get[PocRepository]
        val tenantTable = injector.get[TenantRepository]
        val employeeTable = injector.get[PocEmployeeRepository]
        val statusTable = injector.get[PocEmployeeStatusRepository]

        val (tenant, poc, employee, status) = createTenantPocEmployeeAndStatus
        val updatedPoc = poc.copy(employeeGroupId = Some(UUID.randomUUID().toString))

        tenantTable.createTenant(tenant).runSyncUnsafe()
        addEmployeeTripleToRepository(pocTable, employeeTable, statusTable, updatedPoc, employee, status)
        employeeTable.getPocEmployee(poc.id).runSyncUnsafe().isDefined shouldBe false

        awaitForTicks(loop.startPocEmployeeCreationLoop, 15, 30.seconds)

        val newPocEmployee = employeeTable.getPocEmployee(employee.id).runSyncUnsafe()
        assert(newPocEmployee.value.status == Aborted)
        assert(newPocEmployee.value.creationAttempts == 10)
      }
    }
  }

}
