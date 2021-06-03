package com.ubirch.models.tenant

import io.getquill.MappedEncoding

final case class TenantTypeGroupId(value: String) extends AnyVal

object TenantTypeGroupId {
  implicit val encodeTenantGroupId: MappedEncoding[TenantTypeGroupId, String] =
    MappedEncoding[TenantTypeGroupId, String](_.value)
  implicit val decodeTenantGroupId: MappedEncoding[String, TenantTypeGroupId] =
    MappedEncoding[String, TenantTypeGroupId](TenantTypeGroupId(_))
}


