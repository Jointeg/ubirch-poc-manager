package com.ubirch.models.tenant
import io.getquill.MappedEncoding

import java.util.UUID

final case class TenantId(value: UUID) extends AnyVal

object TenantId {
  def random: TenantId = TenantId(UUID.randomUUID())

  implicit val encodeTenantId: MappedEncoding[TenantId, UUID] = MappedEncoding[TenantId, UUID](_.value)
  implicit val decodeTenantId: MappedEncoding[UUID, TenantId] = MappedEncoding[UUID, TenantId](TenantId(_))
}
