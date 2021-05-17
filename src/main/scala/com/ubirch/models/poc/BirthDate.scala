package com.ubirch.models.poc

import io.getquill.{ Embedded, MappedEncoding }
import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat

case class BirthDate(date: LocalDate) extends Embedded

object BirthDate {
  private val formatter = DateTimeFormat.forPattern("dd.MM.yyyy")
  implicit val encodeBirthDate: MappedEncoding[BirthDate, String] =
    MappedEncoding[BirthDate, String](birthDate => formatter.print(birthDate.date))
  implicit val decodeBirthDate: MappedEncoding[String, BirthDate] =
    MappedEncoding[String, BirthDate](str => BirthDate(LocalDate.parse(str, formatter)))
}
