package com.ubirch.models.pocEmployee

import com.ubirch.models.poc._
import com.ubirch.models.tenant.TenantId
import com.ubirch.services.poc.CertifyUserService.HasCertifyUserId
import org.joda.time.DateTime

import java.util.UUID

case class PocEmployee(
  id: UUID,
  pocId: UUID,
  tenantId: TenantId,
  name: String,
  surname: String,
  email: String,
  certifyUserId: Option[UUID] = None,
  status: Status = Pending,
  active: Boolean = true,
  lastUpdated: Updated = Updated(DateTime.now()),
  created: Created = Created(DateTime.now()),
  webAuthnDisconnected: Option[DateTime] = None
) extends HasCertifyUserId
