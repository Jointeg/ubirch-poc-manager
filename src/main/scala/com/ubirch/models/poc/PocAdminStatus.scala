package com.ubirch.models.poc

import org.joda.time.DateTime

import java.util.UUID

case class PocAdminStatus(
  pocAdminId: UUID,
  webIdentRequired: Boolean,
  webIdentIdentifier: Option[Boolean],
  userRealmCreated: Boolean = false,
  emailActionRequired: Boolean = false,
  emailVerified: Boolean = false,
  passwordUpdated: Boolean = false,
  twoFactorAuthCreated: Boolean = false,
  pocGroupRoleAdded: Boolean = false,
  pocAdminRoleAdded: Boolean = false,
  errorMessages: Option[String] = None,
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
