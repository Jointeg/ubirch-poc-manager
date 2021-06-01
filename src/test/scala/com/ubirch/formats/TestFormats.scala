package com.ubirch.formats
import com.ubirch.data.KeycloakToken
import com.ubirch.e2e.{ KeyResponse, KeycloakKidResponse }
import org.json4s.JsonDSL._
import org.json4s.{ CustomSerializer, Formats, JObject }

object TestFormats {

  val keycloakTokenFormat: CustomSerializer[KeycloakToken] = new CustomSerializer[KeycloakToken](format =>
    (
      {
        case jsonObj: JObject =>
          implicit val formats: Formats = format
          val accessToken = (jsonObj \ "access_token").extract[String]
          KeycloakToken(accessToken)
      },
      {
        case keycloakToken: KeycloakToken =>
          "access_token" -> keycloakToken.accessToken
      }
    ))

  val keycloakKidResponse: CustomSerializer[KeycloakKidResponse] = new CustomSerializer[KeycloakKidResponse](format =>
    (
      {
        case jsonObj: JObject =>
          implicit val formats: Formats = format
          val keys = (jsonObj \ "keys").extract[List[KeyResponse]]
          KeycloakKidResponse(keys)
      },
      {
        case _: KeycloakKidResponse =>
          ("keys" -> "")
      }
    ))

  val keycloakKeyResponse: CustomSerializer[KeyResponse] = new CustomSerializer[KeyResponse](format =>
    (
      {
        case jsonObj: JObject =>
          implicit val formats: Formats = format
          val kid = (jsonObj \ "kid").extract[String]
          val alg = (jsonObj \ "alg").extract[String]
          KeyResponse(kid, alg)
      },
      {
        case keycloakKidResponse: KeyResponse =>
          ("kid" -> keycloakKidResponse.kid) ~
            ("alg" -> keycloakKidResponse.alg)
      }
    ))

  val all = List(keycloakTokenFormat, keycloakKeyResponse, keycloakKidResponse)
}
