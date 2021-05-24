package com.ubirch.services.poc

import com.ubirch.models.poc.{ Poc, PocAdmin, PocAdminStatus }
import com.ubirch.ModelCreationHelper.{ createPocAdmin, createPocAdminStatus }
import com.ubirch.db.tables.{ PocAdminRepositoryMock, PocAdminStatusRepositoryMock }
import com.ubirch.models.tenant.Tenant
import monix.execution.Scheduler.Implicits.global

import java.util.UUID

object PocAdminTestHelper {
  def createPocAdminAndStatus(poc: Poc, tenant: Tenant, webIdentRequired: Boolean): (PocAdmin, PocAdminStatus) = {
    val pocAdmin = createPocAdmin(pocId = poc.id, tenantId = tenant.id, webIdentRequired = webIdentRequired)
    val pocAdminStatus = createPocAdminStatus(pocAdmin, poc)
    (pocAdmin, pocAdminStatus)
  }

  def addPocAndStatusToRepository(
    pocAdminTable: PocAdminRepositoryMock,
    pocAdminStatusTable: PocAdminStatusRepositoryMock,
    pocAdmin: PocAdmin,
    pocAdminStatus: PocAdminStatus
  ): Unit = {
    (for {
      _ <- pocAdminTable.createPocAdmin(pocAdmin)
      _ <- pocAdminStatusTable.createStatus(pocAdminStatus)
    } yield ()).runSyncUnsafe()
  }

  def createPocAdminStatusAllTrue(webIdentRequired: Boolean): PocAdminStatus = {
    val webIdentInitiated = if (webIdentRequired) Some(true) else None
    val webIdentSuccess = if (webIdentRequired) Some(true) else None
    PocAdminStatus(
      pocAdminId = UUID.randomUUID(),
      webIdentRequired = webIdentRequired,
      webIdentInitiated = webIdentInitiated,
      webIdentSuccess = webIdentSuccess,
      certifyUserCreated = true,
      keycloakEmailSent = true,
      pocAdminGroupAssigned = true,
      invitedToTeamDrive = Some(true),
      errorMessage = None
    )
  }
}
