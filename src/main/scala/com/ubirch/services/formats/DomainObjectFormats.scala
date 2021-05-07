package com.ubirch.services.formats
import com.ubirch.models.NamespacedUUID
import com.ubirch.models.auth.Base64String
import com.ubirch.models.poc.{ Created, PocManager, Updated }
import com.ubirch.models.tenant._
import org.apache.commons.codec.binary.Base64
import org.joda.time.{ DateTime, DateTimeZone }
import org.json4s.JsonDSL._
import org.json4s.ext.DateParser
import org.json4s.{ CustomSerializer, JObject, JString, MappingException }

import java.util.UUID

object DomainObjectFormats {

  private val usageTypeFormat: CustomSerializer[UsageType] =
    createStringFormat(UsageType.unsafeFromString, _.nonEmpty)(UsageType.toStringFormat)
  private val clientCertFormat: CustomSerializer[ClientCert] =
    createStringFormat(string => ClientCert(Base64String(string)), string => Base64.isBase64(string))(_.value.value)
  private val tenantIdFormat: CustomSerializer[NamespacedUUID] =
    createStringFormat(
      string => NamespacedUUID.fromJavaUUID(UUID.fromString(string)),
      string => Base64.isBase64(string))(
      _.value.toString)
  private val pocManagerFormat: CustomSerializer[PocManager] = new CustomSerializer[PocManager](format =>
    (
      {
        case jsonObj: JObject =>
          implicit val formats = format
          val firstName = (jsonObj \ "firstName").extract[String]
          val lastName = (jsonObj \ "lastName").extract[String]
          val email = (jsonObj \ "email").extract[String]
          val mobilePhone = (jsonObj \ "mobilePhone").extract[String]
          PocManager(lastName, firstName, email, mobilePhone)
      },
      {
        case pocManager: PocManager =>
          ("lastName" -> pocManager.managerSurname) ~
            ("firstName" -> pocManager.managerName) ~
            ("email" -> pocManager.managerEmail) ~
            ("mobilePhone" -> pocManager.managerMobilePhone)
      }
    ))
  private val createdFormat: CustomSerializer[Created] = createDateTimeFormat(Created(_))(_.dateTime)
  private val updatedFormat: CustomSerializer[Updated] = createDateTimeFormat(Updated(_))(_.dateTime)

  val all = List(usageTypeFormat, clientCertFormat, tenantIdFormat, pocManagerFormat, createdFormat, updatedFormat)

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

  private def createDateTimeFormat[A: Manifest](
    decode: DateTime => A)(encode: A => DateTime) = {
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
