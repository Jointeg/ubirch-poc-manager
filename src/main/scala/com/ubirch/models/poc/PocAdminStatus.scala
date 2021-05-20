package com.ubirch.models.poc

import org.joda.time.DateTime

import java.util.UUID

case class PocAdminStatus(
  pocAdminId: UUID,
  webIdentRequired: Boolean,
  webIdentTriggered: Option[Boolean],
  webIdentIdentifierSuccess: Option[Boolean],
  certifierUserCreated: Boolean = false,
  keycloakEmailSent: Boolean = false,
  pocAdminGroupAssigned: Boolean = false,
  pocCertifyGroupAssigned: Boolean = false,
  pocTenantGroupAssigned: Boolean = false,
  invitedToTeamDrive: Boolean = false,
  errorMessage: Option[String] = None,
  lastUpdated: Updated = Updated(DateTime.now()),
  created: Created = Created(DateTime.now())
)

object PocAdminStatus {
  def init(podAdmin: PocAdmin): PocAdminStatus = {
    val webIdentTriggered = if (podAdmin.webIdentRequired) Some(false) else None
    val webIdentIdentifierSuccess = if (podAdmin.webIdentRequired) Some(false) else None
    PocAdminStatus(
      podAdmin.id,
      podAdmin.webIdentRequired,
      webIdentTriggered,
      webIdentIdentifierSuccess
    )
  }
}
