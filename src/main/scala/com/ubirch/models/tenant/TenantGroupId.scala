package com.ubirch.models.tenant
import io.getquill.MappedEncoding

final case class TenantGroupId(value: String) extends AnyVal

object TenantGroupId {
  implicit val encodeTenantGroupId: MappedEncoding[TenantGroupId, String] =
    MappedEncoding[TenantGroupId, String](_.value)
  implicit val decodeTenantGroupId: MappedEncoding[String, TenantGroupId] =
    MappedEncoding[String, TenantGroupId](TenantGroupId(_))
}
