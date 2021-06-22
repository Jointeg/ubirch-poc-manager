package com.ubirch.models.pocEmployee

import com.ubirch.models.poc.{ Created, Updated }
import org.joda.time.DateTime

import java.util.UUID

case class PocEmployeeStatus(
  pocEmployeeId: UUID,
  certifyUserCreated: Boolean = false,
  employeeGroupAssigned: Boolean = false,
  keycloakEmailSent: Boolean = false,
  errorMessage: Option[String] = None,
  lastUpdated: Updated = Updated(DateTime.now()),
  created: Created = Created(DateTime.now())
)
