package com.ubirch.models.tenant
import io.getquill.MappedEncoding

final case class TenantName(value: String) extends AnyVal

object TenantName {
  implicit val encodeTenantName: MappedEncoding[TenantName, String] = MappedEncoding[TenantName, String](_.value)
  implicit val decodeTenantName: MappedEncoding[String, TenantName] = MappedEncoding[String, TenantName](TenantName(_))
}
