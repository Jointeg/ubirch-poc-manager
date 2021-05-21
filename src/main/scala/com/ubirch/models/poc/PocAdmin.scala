package com.ubirch.models.poc

import com.ubirch.models.tenant.TenantId
import org.joda.time.{ DateTime, LocalDate }

import java.util.UUID

case class PocAdmin(
  id: UUID,
  pocId: UUID,
  tenantId: TenantId,
  name: String,
  surname: String,
  email: String,
  mobilePhone: String,
  webIdentRequired: Boolean,
  webIdentId: Option[Boolean],
  webIdentInitiateId: Option[UUID],
  certifierUserId: UUID,
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
    certifierUserId: UUID,
    dateOfBirth: LocalDate): PocAdmin = {
    new PocAdmin(
      id = id,
      pocId = pocId,
      tenantId = tenantId,
      name = name,
      surname = surName,
      email = email,
      mobilePhone = mobilePhone,
      webIdentRequired = webIdentRequired,
      webIdentId = None,
      webIdentInitiateId = None,
      certifierUserId = certifierUserId,
      dateOfBirth = BirthDate(dateOfBirth)
    )
  }

}
