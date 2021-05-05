package com.ubirch.services.formats
import com.ubirch.models.auth.Base64String
import com.ubirch.models.tenant._
import org.apache.commons.codec.binary.Base64
import org.json4s.JsonAST.JString
import org.json4s.{ CustomSerializer, MappingException }

object DomainObjectFormats {

  private val usageTypeFormat: CustomSerializer[UsageType] =
    createStringFormat(UsageType.unsafeFromString, _.nonEmpty)(UsageType.toStringFormat)
  private val clientCertFormat: CustomSerializer[ClientCert] =
    createStringFormat(string => ClientCert(Base64String(string)), string => Base64.isBase64(string))(_.value.value)

  val all = List(usageTypeFormat, clientCertFormat)

  private def createStringFormat[A: Manifest](
    decode: String => A,
    validation: String => Boolean)(encode: A => String) = {
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
}
