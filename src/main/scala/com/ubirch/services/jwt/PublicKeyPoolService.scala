package com.ubirch.services.jwt

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.KeycloakPaths
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak, KeycloakInstance }
import monix.eval.Task

import java.security.Key
import javax.inject._
import scala.collection.concurrent.TrieMap

trait PublicKeyPoolService {
  def getKey(kid: String): Option[Key]

  def getDefaultKey(keycloakInstance: KeycloakInstance): Option[Key]

  def init(keycloakInstances: KeycloakInstance*): Task[List[(String, Key)]]
}

@Singleton
class DefaultPublicKeyPoolService @Inject() (config: Config, publicKeyDiscoveryService: PublicKeyDiscoveryService)
  extends PublicKeyPoolService
  with LazyLogging {

  private def acceptedKids(keycloakInstance: KeycloakInstance): List[String] =
    keycloakInstance match {
      case CertifyKeycloak => List(config.getString(KeycloakPaths.CertifyKeycloak.KID))
      case DeviceKeycloak  => List(config.getString(KeycloakPaths.DeviceKeycloak.KID))
    }

  final private val cache = new TrieMap[String, Key]()

  override def getKey(kid: String): Option[Key] = cache.get(kid)

  override def getDefaultKey(keycloakInstance: KeycloakInstance): Option[Key] =
    acceptedKids(keycloakInstance).headOption.flatMap(x => getKey(x))

  def getKeyFromDiscoveryService(keycloakInstance: KeycloakInstance, kid: String): Task[Option[Key]] =
    publicKeyDiscoveryService.getKey(keycloakInstance, kid)

  override def init(keycloakInstances: KeycloakInstance*): Task[List[(String, Key)]] = {
    Task.sequence {
      keycloakInstances.toList.flatMap(instance => acceptedKids(instance).map(kid => (instance, kid))).map {
        case (instance, kid) =>
          for {
            maybeKey <- getKeyFromDiscoveryService(instance, kid)
          } yield {

            val res = maybeKey match {
              case Some(value) => cache.put(kid, value).map(x => (kid, x))
              case None =>
                logger.warn("kid_not_found={}", kid)
                None
            }
            res.toList

          }
      }
    }
      .map(_.flatten)

  }

}
