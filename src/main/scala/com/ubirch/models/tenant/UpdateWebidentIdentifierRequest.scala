package com.ubirch.models.tenant
import java.util.UUID

case class UpdateWebidentIdentifierRequest(
  pocAdminId: UUID,
  webidentIdentifier: UUID
)
