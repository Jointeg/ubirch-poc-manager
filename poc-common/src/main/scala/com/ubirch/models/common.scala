package com.ubirch.models

import io.getquill.Ord
import org.joda.time.DateTime

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

  sealed trait LoopState
  case class Starting(dateTime: DateTime) extends LoopState
  case object Cancelled extends LoopState
  case class ErrorTerminated(dateTime: DateTime) extends LoopState
  case class ProcessingElements(dateTime: DateTime, elementName: String, elementId: String) extends LoopState
  case class WaitingForNewElements(dateTime: DateTime, elementName: String) extends LoopState
  case object Completed extends LoopState

  val TENANT_GROUP_PREFIX: String = "TEN_"
  val POC_GROUP_PREFIX: String = "POC_"

  val POC_EMPLOYEE = "poc-employee"
  val POC_ADMIN = "poc-admin"
}
