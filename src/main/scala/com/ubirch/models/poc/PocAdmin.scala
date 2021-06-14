package com.ubirch.models.poc

import com.ubirch.models.tenant.TenantId
import com.ubirch.services.poc.CertifyUserService.HasCertifyUserId
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
  webIdentInitiateId: Option[UUID],
  webIdentId: Option[String],
  certifyUserId: Option[UUID],
  dateOfBirth: BirthDate,
  status: Status = Pending,
  active: Boolean = true,
  webAuthnDisconnected: Option[DateTime] = None,
  lastUpdated: Updated = Updated(DateTime.now()),
  created: Created = Created(DateTime.now())
) extends HasCertifyUserId

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
    dateOfBirth: LocalDate): PocAdmin = {
    PocAdmin(
      id = id,
      pocId = pocId,
      tenantId = tenantId,
      name = name,
      surname = surName,
      email = email,
      mobilePhone = mobilePhone,
      webIdentRequired = webIdentRequired,
      webIdentInitiateId = None,
      webIdentId = None,
      certifyUserId = None,
      dateOfBirth = BirthDate(dateOfBirth)
    )
  }

}
