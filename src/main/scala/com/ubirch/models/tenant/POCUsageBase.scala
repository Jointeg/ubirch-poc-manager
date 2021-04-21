package com.ubirch.models.tenant

sealed trait POCUsageBase extends Product with Serializable
case object APIUsage extends POCUsageBase
case object UIUsage extends POCUsageBase
case object AllChannelsUsage extends POCUsageBase

object POCUsageBase {
  private val APIUsageString = "APIUsage"
  private val UIUsageString = "UIUsage"
  private val AllChannelsUsageString = "AllChannelsUsage"

  def unsafeFromString(value: String): POCUsageBase =
    value match {
      case APIUsageString => APIUsage
      case UIUsageString => UIUsage
      case AllChannelsUsageString => AllChannelsUsage
    }

  def toStringFormat(POCUsageBase: POCUsageBase): String =
    POCUsageBase match {
      case APIUsage => APIUsageString
      case UIUsage => UIUsageString
      case AllChannelsUsage => AllChannelsUsageString
    }
}
