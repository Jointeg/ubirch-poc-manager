package com.ubirch.models.tenant
import io.getquill.MappedEncoding

final case class TenantDeviceGroupId(value: String) extends AnyVal

object TenantDeviceGroupId {
  implicit val encodeTenantGroupId: MappedEncoding[TenantDeviceGroupId, String] =
    MappedEncoding[TenantDeviceGroupId, String](_.value)
  implicit val decodeTenantGroupId: MappedEncoding[String, TenantDeviceGroupId] =
    MappedEncoding[String, TenantDeviceGroupId](TenantDeviceGroupId(_))
}

