package com.ubirch.services.jwt

import cats.effect.{ExitCode, Resource}
import com.typesafe.config.Config
import com.ubirch.ConfPaths.KeycloakPaths
import com.ubirch.services.{DeviceKeycloak, KeycloakInstance, UsersKeycloak}
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.formats.{DefaultJsonConverterService, JsonConverterService, JsonFormatsProvider}
import monix.eval.{Task, TaskApp}
import monix.reactive.Observable
import org.jose4j.jwk.PublicJsonWebKey
import org.json4s.JsonAST.JArray
import org.json4s.{Formats, JString, JValue, JsonAST}

import java.net.URL
import java.security.Key
import javax.inject._
import scala.io.{BufferedSource, Source}

trait PublicKeyDiscoveryService {
  def getKey(keycloakInstance: KeycloakInstance, kid: String): Task[Option[Key]]
}

@Singleton
class DefaultPublicKeyDiscoveryService @Inject() (config: Config, jsonConverterService: JsonConverterService)
  extends PublicKeyDiscoveryService {

  def getConfigUrlString(keycloakInstance: KeycloakInstance) =
    keycloakInstance match {
      case UsersKeycloak => config.getString(KeycloakPaths.UsersKeycloak.CONFIG_URL)
      case DeviceKeycloak => config.getString(KeycloakPaths.DeviceKeycloak.CONFIG_URL)
    }

  final val JWKS_URI = "jwks_uri"
  final val KEYS = "keys"
  final val KID = "kid"

  private def readURLResource(url: URL): Resource[Task, BufferedSource] = {
    Resource.make { Task(Source.fromURL(url)) } { in => Task(in.close()) }
  }

  private def readURL(url: URL): Task[String] = {
    Observable
      .fromResource(readURLResource(url))
      .flatMap { s =>
        Observable.fromLinesReader(Task(s.bufferedReader()))
      }
      .toListL
      .map(_.mkString)
  }

  private def readUrlAsJValue(url: String): Task[JValue] = {
    for {
      urlConfig <- Task(new URL(url))
      conf <- readURL(urlConfig)
      res <- Task.fromEither(jsonConverterService.toJValue(conf))
    } yield res
  }

  def getKey(keycloakInstance: KeycloakInstance, kid: String): Task[Option[Key]] = {

    for {

      configRes <- readUrlAsJValue(getConfigUrlString(keycloakInstance))

      keysUrl <- Task(configRes.findField(_._1 == JWKS_URI))
        .flatMap {
          _.map(Task.apply(_)).getOrElse(Task.raiseError(new Exception("No jwks_uri found")))
        }
        .flatMap {
          case (_, v) =>
            v match {
              case JsonAST.JString(s) => Task(s)
              case _ => Task.raiseError(new Exception("No jwks_uri found"))
            }
        }

      keysRes <- readUrlAsJValue(keysUrl)
        .map { _ \\ KEYS }
        .map {
          case JArray(keys) => keys
          case _ => Nil
        }

      maybeJWK <- Task {
        keysRes
          .find {
            _.findField {
              case (k, JString(v)) => k == KID && v == kid
              case _ => false
            }.isDefined
          }
          .map {
            jsonConverterService.toString
          }
      }

      maybeKey <- Task {
        maybeJWK.map(jwk => PublicJsonWebKey.Factory.newPublicJwk(jwk).getKey)
      }

    } yield {
      maybeKey
    }
  }

}

object DefaultPublicKeyDiscoveryService extends TaskApp {

  override def run(args: List[String]): Task[ExitCode] = {

    implicit val formats: Formats = new JsonFormatsProvider().get()
    val config = new ConfigProvider().get()
    val jsonConverterService: JsonConverterService = new DefaultJsonConverterService()
    val pk: PublicKeyDiscoveryService = new DefaultPublicKeyDiscoveryService(config, jsonConverterService)

    for {
      key <- pk.getKey(DeviceKeycloak, "F3gD13bFD2vOQjtHYESGoZUUK7XMIqwE-juSNIeLui0")
      _ <- Task(println(key))
    } yield {
      ExitCode.Success
    }

  }
}
