package com.ubirch.models.csv
import scala.util.Try

case class PocEmployeeRow(
  firstName: String,
  lastName: String,
  email: String
)

object PocEmployeeRow {
  def fromCsv(columns: Array[String]): Try[PocEmployeeRow] = Try {
    PocEmployeeRow(
      columns(0),
      columns(1),
      columns(2)
    )
  }
}
