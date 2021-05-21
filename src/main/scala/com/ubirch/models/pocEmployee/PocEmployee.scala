package com.ubirch.models.pocEmployee

import com.ubirch.models.poc.{ Created, Pending, Status, Updated }
import com.ubirch.models.tenant.TenantId
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
  active: Boolean = false,
  lastUpdated: Updated = Updated(DateTime.now()),
  created: Created = Created(DateTime.now())
)
