package com.ubirch.models.tenant
import io.getquill.MappedEncoding

sealed trait UsageType extends Product with Serializable
case object API extends UsageType
case object APP extends UsageType
case object Both extends UsageType

object UsageType {
  private val APIString = "API"
  private val APPString = "APP"
  private val BothString = "BOTH"

  def unsafeFromString(value: String): UsageType =
    value match {
      case APIString  => API
      case APPString  => APP
      case BothString => Both
    }

  def toStringFormat(POCUsageBase: UsageType): String =
    POCUsageBase match {
      case API  => APIString
      case APP  => APPString
      case Both => BothString
    }

  implicit val encodeUsageType: MappedEncoding[UsageType, String] =
    MappedEncoding[UsageType, String](toStringFormat)
  implicit val decodeUsageType: MappedEncoding[String, UsageType] =
    MappedEncoding[String, UsageType](unsafeFromString)
}
