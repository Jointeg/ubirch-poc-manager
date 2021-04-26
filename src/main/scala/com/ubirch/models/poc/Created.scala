package com.ubirch.models.poc

import io.getquill.MappedEncoding
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormatterBuilder

case class Created(dateTime: DateTime)
object Created {

  private val formatter = new DateTimeFormatterBuilder().appendPattern("yyyy-MM-dd'T'HH:mm:ss.SSS").toFormatter
  implicit val encodeCreated: MappedEncoding[Created, String] =
    MappedEncoding[Created, String](_.dateTime.toString(formatter))

  implicit val decodeCreated: MappedEncoding[String, Created] =
    MappedEncoding[String, Created](str => Created(DateTime.parse(str, formatter)))
}
