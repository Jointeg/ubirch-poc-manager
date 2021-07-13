package com.ubirch.models.poc

import io.getquill.{ Embedded, MappedEncoding }

sealed trait Status extends Embedded

case object Pending extends Status

case object Processing extends Status

case object Completed extends Status

case object Aborted extends Status

object Status {

  def unsafeFromString(value: String): Status = value match {
    case "PENDING"    => Pending
    case "PROCESSING" => Processing
    case "COMPLETED"  => Completed
    case "ABORTED"    => Aborted
  }

  def toFormattedString(status: Status): String = status match {
    case Pending    => "PENDING"
    case Processing => "PROCESSING"
    case Completed  => "COMPLETED"
    case Aborted    => "ABORTED"
  }

  implicit val encodeStatus: MappedEncoding[Status, String] = MappedEncoding[Status, String](toFormattedString)
  implicit val decodeStatus: MappedEncoding[String, Status] = MappedEncoding[String, Status](unsafeFromString)
}
