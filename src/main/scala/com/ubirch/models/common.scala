package com.ubirch.models

import io.getquill.Ord

object common {
  sealed trait Order

  case class Page(index: Int, size: Int)

  case class Sort(field: Option[String], order: Order) {
    def ord[T]: Ord[T] = order match {
      case ASC  => Ord.asc[T]
      case DESC => Ord.desc[T]
    }
  }

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
