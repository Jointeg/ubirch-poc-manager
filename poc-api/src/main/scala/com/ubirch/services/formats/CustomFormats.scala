package com.ubirch.services.formats

import org.joda.time.{ DateTime, DateTimeZone }
import org.json4s.ext.DateParser
import org.json4s.{ CustomSerializer, JString, MappingException, Serializer }

import javax.inject.Singleton

@Singleton
class CustomFormats extends CustomJsonFormats {
  val formats: Iterable[Serializer[_]] = PocFormats.all ++ CommonFormats.all
}

trait FormatHelperMethods {
  def createStringFormat[A: Manifest](
    decode: String => A,
    validation: String => Boolean)(encode: A => String): CustomSerializer[A] = {
    val Class = implicitly[Manifest[A]].runtimeClass
    new CustomSerializer[A](_ =>
      (
        {
          case JString(value) if validation(value) => decode(value)
          case JString(_)                          => throw new MappingException("Can't convert value to " + Class)
        },
        {
          case a: A => JString(encode(a))
        }
      ))
  }

  def createDateTimeFormat[A: Manifest](
    decode: DateTime => A)(encode: A => DateTime): CustomSerializer[A] = {
    new CustomSerializer[A](formats =>
      (
        {
          case JString(s) =>
            val zonedInstant = DateParser.parse(s, formats)
            decode(new DateTime(zonedInstant.instant, DateTimeZone.forTimeZone(zonedInstant.timezone)))
        },
        {
          case d: A => JString(formats.dateFormat.format(encode(d).toDate))
        }))
  }
}
