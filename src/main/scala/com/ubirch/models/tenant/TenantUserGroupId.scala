package com.ubirch.models.tenant
import io.getquill.MappedEncoding

final case class TenantUserGroupId(value: String) extends AnyVal

object TenantUserGroupId {
  implicit val encodeTenantGroupId: MappedEncoding[TenantUserGroupId, String] =
    MappedEncoding[TenantUserGroupId, String](_.value)
  implicit val decodeTenantGroupId: MappedEncoding[String, TenantUserGroupId] =
    MappedEncoding[String, TenantUserGroupId](TenantUserGroupId(_))
}
