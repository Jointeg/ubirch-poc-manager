package com.ubirch.models.tenant

import org.joda.time.LocalDate

import java.util.UUID

case class CreatePocAdminRequest(
  pocId: UUID,
  firstName: String,
  lastName: String,
  email: String,
  phone: String,
  dateOfBirth: LocalDate,
  webIdentRequired: Boolean)
