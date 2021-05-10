package com.ubirch.models.poc

import com.ubirch.models.tenant.TenantId
import org.joda.time.{ DateTime, LocalDate }

import java.util.UUID

case class PocAdmin(
  id: UUID,
  pocId: UUID,
  tenantId: TenantId,
  name: String,
  surName: String,
  email: String,
  mobilePhone: String,
  webIdentRequired: Boolean,
  webIdentIdentifier: Option[String],
  keyCloakUserId: UUID,
  dateOfBirth: BirthDate,
  status: Status = Pending,
  lastUpdated: Updated = Updated(DateTime.now()),
  created: Created = Created(DateTime.now())
)

object PocAdmin {

  def apply(
    id: UUID,
    pocId: UUID,
    tenantId: TenantId,
    name: String,
    surName: String,
    email: String,
    mobilePhone: String,
    webIdentRequired: Boolean,
    keyCloakUserId: UUID,
    dateOfBirth: LocalDate): PocAdmin = {
    PocAdmin(
      id,
      pocId,
      tenantId,
      name,
      surName,
      email,
      mobilePhone,
      webIdentRequired,
      None,
      keyCloakUserId,
      BirthDate(dateOfBirth)
    )
  }

}
