package com.ubirch.models.tenant
import java.util.UUID

case class UpdateWebIdentIdRequest(
  pocAdminId: UUID,
  webIdentId: String,
  webIdentInitiateId: UUID
)
