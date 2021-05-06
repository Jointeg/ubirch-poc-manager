package com.ubirch.models.tenant
import io.getquill.MappedEncoding
import memeid4s.digest.Digestible

import java.nio.charset.StandardCharsets

final case class TenantName(value: String) extends AnyVal

object TenantName {
  implicit val encodeTenantName: MappedEncoding[TenantName, String] = MappedEncoding[TenantName, String](_.value)
  implicit val decodeTenantName: MappedEncoding[String, TenantName] = MappedEncoding[String, TenantName](TenantName(_))
  implicit val digestibleTenantName: Digestible[TenantName] =
    (tenantName: TenantName) => tenantName.value.getBytes(StandardCharsets.UTF_8)
}
