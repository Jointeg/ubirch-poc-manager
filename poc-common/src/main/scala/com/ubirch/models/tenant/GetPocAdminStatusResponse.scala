package com.ubirch.models.tenant

import com.ubirch.models.poc.{ Created, PocAdminStatus, Updated }

case class GetPocAdminStatusResponse(
  webIdentRequired: Boolean,
  webIdentInitiated: Option[Boolean],
  webIdentSuccess: Option[Boolean],
  certifyUserCreated: Boolean,
  pocAdminGroupAssigned: Boolean,
  keycloakEmailSent: Boolean,
  errorMessage: Option[String],
  lastUpdated: Updated,
  created: Created
)

object GetPocAdminStatusResponse {
  def fromPocAdminStatus(pocAdminStatus: PocAdminStatus): GetPocAdminStatusResponse = GetPocAdminStatusResponse(
    pocAdminStatus.webIdentRequired,
    pocAdminStatus.webIdentInitiated,
    pocAdminStatus.webIdentSuccess,
    pocAdminStatus.certifyUserCreated,
    pocAdminStatus.pocAdminGroupAssigned,
    pocAdminStatus.keycloakEmailSent,
    pocAdminStatus.errorMessage,
    pocAdminStatus.lastUpdated,
    pocAdminStatus.created
  )
}
