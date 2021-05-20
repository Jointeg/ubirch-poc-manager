package com.ubirch.models.tenant
import java.util.UUID

case class UpdateWebIdentIdRequest(
  pocAdminId: UUID,
  webIdentId: UUID,
  webIdentInitiateId: UUID
)
