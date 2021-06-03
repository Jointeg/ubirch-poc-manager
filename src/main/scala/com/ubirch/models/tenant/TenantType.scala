package com.ubirch.models.tenant

import com.ubirch.services.keycloak.{ CertifyBmgRealm, CertifyUbirchRealm, KeycloakRealm }
import io.getquill.MappedEncoding

sealed trait TenantType extends Product with Serializable
case object BMG extends TenantType
case object UBIRCH extends TenantType

object TenantType {
  val BMG_STRING = "bmg"
  val UBIRCH_STRING = "ubirch"

  def unsafeFromString(value: String): TenantType =
    value match {
      case BMG_STRING    => BMG
      case UBIRCH_STRING => UBIRCH
    }

  def toStringFormat(tenantType: TenantType): String =
    tenantType match {
      case BMG    => BMG_STRING
      case UBIRCH => UBIRCH_STRING
    }

  def getRealm(tenantType: TenantType): KeycloakRealm =
    tenantType match {
      case UBIRCH => CertifyUbirchRealm
      case BMG    => CertifyBmgRealm
    }

  implicit val encodeTenantTypeValue: MappedEncoding[TenantType, String] =
    MappedEncoding[TenantType, String](toStringFormat)

  implicit val decodeTenantTypeValue: MappedEncoding[String, TenantType] =
    MappedEncoding[String, TenantType](unsafeFromString)
}
