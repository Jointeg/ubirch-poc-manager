package com.ubirch.models.tenant

import io.getquill.MappedEncoding

object TenantType extends Enumeration {
  val bmg: Value = Value("bmg")
  val ubirch: Value = Value("ubirch")

  implicit val encodeTenantTypeValue: MappedEncoding[TenantType.Value, String] =
    MappedEncoding[TenantType.Value, String](_.toString)

  implicit val decodeTenantTypeValue: MappedEncoding[String, TenantType.Value] =
    MappedEncoding[String, TenantType.Value](t => TenantType.withName(t))
}