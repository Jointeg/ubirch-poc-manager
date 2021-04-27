package com.ubirch.models.tenant
import io.getquill.MappedEncoding

final case class TenantOrganisationalUnitGroupId(value: String) extends AnyVal

object TenantOrganisationalUnitGroupId {

  implicit val encodeTenantOrganisationalUnitGroupId: MappedEncoding[TenantOrganisationalUnitGroupId, String] =
    MappedEncoding[TenantOrganisationalUnitGroupId, String](_.value)
  implicit val decodeTenantOrganisationalUnitGroupId: MappedEncoding[String, TenantOrganisationalUnitGroupId] =
    MappedEncoding[String, TenantOrganisationalUnitGroupId](TenantOrganisationalUnitGroupId(_))

}
