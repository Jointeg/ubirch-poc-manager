package com.ubirch.models

import cats.Applicative
import cats.syntax.all._

object common {
  case class Page(index: Int, size: Int)
  case class Sort(field: String, order: Order)

  sealed class Order
  case object ASC extends Order
  case object DESC extends Order
}
