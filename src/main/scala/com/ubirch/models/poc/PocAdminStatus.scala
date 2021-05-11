package com.ubirch.models.poc

import org.joda.time.DateTime

import java.util.UUID

case class PocAdminStatus(
  pocAdminId: UUID,
  webIdentRequired: Boolean,
  webIdentIdentified: Option[Boolean],
  keycloakUserCreated: Boolean = false,
  emailActionRequired: Boolean = false,
  verifyEmailSet: Boolean = false,
  updatePasswordSet: Boolean = false,
  twoFactorAuthSet: Boolean = false,
  pocGroupAdded: Boolean = false,
  errorMessage: Option[String] = None,
  lastUpdated: Updated = Updated(DateTime.now()),
  created: Created = Created(DateTime.now())
)

object PocAdminStatus {
  def init(podAdmin: PocAdmin): PocAdminStatus = {
    val webIdentIdentifier = if (podAdmin.webIdentRequired) Some(false) else None
    PocAdminStatus(
      podAdmin.id,
      podAdmin.webIdentRequired,
      webIdentIdentifier
    )
  }
}
