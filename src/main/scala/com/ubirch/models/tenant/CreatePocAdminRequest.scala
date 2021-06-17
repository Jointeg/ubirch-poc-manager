package com.ubirch.models.tenant

import java.util.UUID

case class CreatePocAdminRequest(
  pocId: UUID,
  firstName: String,
  lastName: String,
  email: String,
  phone: String,
  dateOfBirth: String,
  webIdentRequired: Boolean)
