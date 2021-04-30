package com.ubirch.models.poc

import io.getquill.{ Embedded, MappedEncoding }

sealed trait Status extends Embedded

case object Pending extends Status

case object Processing extends Status

case object Completed extends Status

object Status {
  implicit val encodeStatus: MappedEncoding[Status, String] = MappedEncoding[Status, String] {
    case Pending    => "PENDING"
    case Processing => "PROCESSING"
    case Completed  => "COMPLETED"
  }
  implicit val decodeStatus: MappedEncoding[String, Status] = MappedEncoding[String, Status] {
    case "PENDING"    => Pending
    case "PROCESSING" => Processing
    case "COMPLETED"  => Completed
  }
}
