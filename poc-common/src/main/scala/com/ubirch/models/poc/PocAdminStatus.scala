package com.ubirch.models.poc

import org.joda.time.DateTime

import java.util.UUID

case class PocAdminStatus(
  pocAdminId: UUID,
  webIdentRequired: Boolean,
  webIdentInitiated: Option[Boolean],
  webIdentSuccess: Option[Boolean],
  certifyUserCreated: Boolean = false,
  pocAdminGroupAssigned: Boolean = false,
  keycloakEmailSent: Boolean = false,
  invitedToTeamDrive: Option[Boolean],
  invitedToStaticTeamDrive: Option[Boolean],
  errorMessage: Option[String] = None,
  lastUpdated: Updated = Updated(DateTime.now()),
  created: Created = Created(DateTime.now())
)

object PocAdminStatus {
  def init(podAdmin: PocAdmin, poc: Poc, pocTypeStaticSpaceNameMap: Map[String, String]): PocAdminStatus = {
    val webIdentInitiated = if (podAdmin.webIdentRequired) Some(false) else None
    val webIdentSuccess = if (podAdmin.webIdentRequired) Some(false) else None
    val invitedToTeamDrive = if (poc.typeIsApp) Some(false) else None
    val invitedToStaticTeamDrive = if (pocTypeStaticSpaceNameMap.contains(poc.pocType)) Some(false) else None
    PocAdminStatus(
      podAdmin.id,
      podAdmin.webIdentRequired,
      webIdentInitiated,
      webIdentSuccess,
      invitedToTeamDrive = invitedToTeamDrive,
      invitedToStaticTeamDrive = invitedToStaticTeamDrive
    )
  }
}
