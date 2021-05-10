package com.ubirch.services.formats

import com.ubirch.models.poc._
import org.json4s.JsonDSL._
import org.json4s.{CustomSerializer, Formats, JObject}

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

  val all = List(pocStatusFormat, pocManagerFormat, createdFormat, updatedFormat, jsonConfigFormat)
}
