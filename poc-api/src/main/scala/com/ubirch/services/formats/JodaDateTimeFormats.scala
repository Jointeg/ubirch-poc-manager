package com.ubirch.services.formats

import org.joda.time.DateTime
import org.joda.time.format.{ DateTimeFormatter, DateTimeFormatterBuilder }
import org.json4s.CustomSerializer
import org.json4s.JsonAST.{ JNull, JString }

object JodaDateTimeFormats {
  val all: Seq[CustomSerializer[_]] = Seq(DateTimeSerializer)

  private val formatter: DateTimeFormatter =
    new DateTimeFormatterBuilder().appendPattern("yyyy-MM-dd'T'HH:mm:ss.SSS").toFormatter

  case object DateTimeSerializer
    extends CustomSerializer[DateTime](_ =>
      (
        {
          case JString(s) => DateTime.parse(s, formatter)
          case JNull      => null
        },
        {
          case d: DateTime => JString(d.toString(formatter))
        }
      ))
}
