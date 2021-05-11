package com.ubirch.models

object common {
  sealed trait Order

  case class Page(index: Int, size: Int)

  case class Sort(field: Option[String], order: Order)

  case object ASC extends Order

  case object DESC extends Order

  object Order {
    def fromString(order: String): Order =
      order.toLowerCase match {
        case "asc"  => ASC
        case "desc" => DESC
      }
  }
}
