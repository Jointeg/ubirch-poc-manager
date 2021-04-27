package com.ubirch.services.formats
import com.ubirch.models.tenant._
import org.json4s.JsonAST.JString
import org.json4s.{CustomSerializer, MappingException}

object DomainObjectFormats {
  val all = List(
    createStringFormat(UsageType.unsafeFromString)(UsageType.toStringFormat)
  )

  private def createStringFormat[A: Manifest](decode: String => A)(encode: A => String) = {
    val Class = implicitly[Manifest[A]].runtimeClass
    new CustomSerializer[A](_ =>
      (
        {
          case JString(value) if value.nonEmpty => decode(value)
          case JString(_) => throw new MappingException("Can't convert empty string to " + Class)
        },
        {
          case a: A => JString(encode(a))
        }
      ))
  }
}
