package com.ubirch.models.tenant
import io.getquill.MappedEncoding

final case class TenantCertifyGroupId(value: String) extends AnyVal

object TenantCertifyGroupId {
  implicit val encodeTenantGroupId: MappedEncoding[TenantCertifyGroupId, String] =
    MappedEncoding[TenantCertifyGroupId, String](_.value)
  implicit val decodeTenantGroupId: MappedEncoding[String, TenantCertifyGroupId] =
    MappedEncoding[String, TenantCertifyGroupId](TenantCertifyGroupId(_))
}
