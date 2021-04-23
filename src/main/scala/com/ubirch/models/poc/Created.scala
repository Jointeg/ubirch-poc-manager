package com.ubirch.models.poc

import io.getquill.MappedEncoding
import org.joda.time.DateTime

case class Created(dateTime: DateTime)
object Created {
  implicit val encodeCreated: MappedEncoding[Created, String] = MappedEncoding[Created, String](_.dateTime.toString())

  implicit val decodeCreated: MappedEncoding[String, Created] =
    MappedEncoding[String, Created](str => Created(DateTime.parse(str)))
}
