package com.ubirch.services.formats

import com.ubirch.models.auth.Base16String
import com.ubirch.models.auth.cert.{ Passphrase, SharedAuthCertificateResponse }
import com.ubirch.models.poc._
import org.json4s.JsonDSL._
import org.json4s.{ CustomSerializer, Formats, JObject }

import java.util.UUID

object PocFormats extends FormatHelperMethods {
  private val pocStatusFormat: CustomSerializer[Status] =
    createStringFormat(Status.unsafeFromString, _ => true)(Status.toFormattedString)
  private val pocManagerFormat: CustomSerializer[PocManager] = new CustomSerializer[PocManager](format =>
    (
      {
        case jsonObj: JObject =>
          implicit val formats: Formats = format
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
  private val sharedAuthCertificateResponseFormat: CustomSerializer[SharedAuthCertificateResponse] =
    new CustomSerializer[SharedAuthCertificateResponse](format =>
      (
        {
          case jsonObj: JObject =>
            implicit val formats: Formats = format
            val certUuid = (jsonObj \ "cert_uuid").extract[UUID]
            val passphrase = (jsonObj \ "passphrase").extract[Passphrase]
            val pkcs12 = (jsonObj \ "pkcs12").extract[Base16String]
            SharedAuthCertificateResponse(certUuid, passphrase, pkcs12)
        },
        {
          case sharedAuthCertificateResponse: SharedAuthCertificateResponse =>
            ("cert_uuid" -> sharedAuthCertificateResponse.certUuid.toString) ~
              ("passphrase" -> sharedAuthCertificateResponse.passphrase.value) ~
              ("pkcs12" -> sharedAuthCertificateResponse.pkcs12.value)
        }
      ))
  private val createdFormat: CustomSerializer[Created] = createDateTimeFormat(Created(_))(_.dateTime)
  private val updatedFormat: CustomSerializer[Updated] = createDateTimeFormat(Updated(_))(_.dateTime)
  private val jsonConfigFormat: CustomSerializer[JsonConfig] = new CustomSerializer[JsonConfig](format =>
    (
      {
        case jsonObj: JObject => JsonConfig(jsonObj)
      },
      {
        case jsonConfig: JsonConfig => jsonConfig.jvalue
      }
    ))

  val all = List(
    pocStatusFormat,
    pocManagerFormat,
    createdFormat,
    updatedFormat,
    jsonConfigFormat,
    sharedAuthCertificateResponseFormat)
}
