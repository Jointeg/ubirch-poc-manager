package com.ubirch.models.poc

import io.getquill.{ Embedded, MappedEncoding }
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormatterBuilder

/**
  * This case class is supposed to represent time and date of the last update of
  * an object. It updates the timestamp automatically when the object is being
  * stored in the database, as the encoding mapper always uses DateTime.now().
  */
case class Updated(dateTime: DateTime) extends Embedded

object Updated {
  private val formatter = new DateTimeFormatterBuilder().appendPattern("yyyy-MM-dd'T'HH:mm:ss.SSS").toFormatter

  implicit val encodeUpdated: MappedEncoding[Updated, String] =
    MappedEncoding[Updated, String](_ => DateTime.now().toString(formatter))

  implicit val decodeUpdated: MappedEncoding[String, Updated] =
    MappedEncoding[String, Updated](str => Updated(DateTime.parse(str, formatter)))
}
