package com.ubirch.models.poc

import io.getquill.{ Embedded, MappedEncoding }
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormatterBuilder

case class Updated(dateTime: DateTime) extends Embedded

object Updated {
  private val formatter = new DateTimeFormatterBuilder().appendPattern("yyyy-MM-dd'T'HH:mm:ss.SSS").toFormatter
  implicit val encodeUpdated: MappedEncoding[Updated, String] =
    MappedEncoding[Updated, String](_ => DateTime.now().toString(formatter))

  implicit val decodeUpdated: MappedEncoding[String, Updated] =
    MappedEncoding[String, Updated](str => Updated(DateTime.parse(str, formatter)))
}
