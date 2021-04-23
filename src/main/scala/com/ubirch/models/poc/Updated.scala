package com.ubirch.models.poc

import io.getquill.MappedEncoding
import org.joda.time.DateTime

case class Updated(dateTime: DateTime)

object Updated {
  //  private val dateTimeFormatter = ISODateTimeFormat.dateHourMinuteSecondMillis

  implicit val encodeUpdated: MappedEncoding[Updated, String] =
    MappedEncoding[Updated, String](_ => DateTime.now().toString())

  implicit val decodeUpdated: MappedEncoding[String, Updated] =
    MappedEncoding[String, Updated](str => Updated(DateTime.parse(str)))
}
