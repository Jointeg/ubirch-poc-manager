package com.ubirch.services.jwt

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.services.keycloak.{ KeycloakCertifyConfig, KeycloakDeviceConfig }
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
class DefaultPublicKeyPoolService @Inject() (
  keycloakCertifyConfig: KeycloakCertifyConfig,
  keycloakDeviceConfig: KeycloakDeviceConfig,
  publicKeyDiscoveryService: PublicKeyDiscoveryService)
  extends PublicKeyPoolService
  with LazyLogging {

  protected def acceptedKids(keycloakInstance: KeycloakInstance): List[String] =
    keycloakInstance match {
      case CertifyKeycloak => List(keycloakCertifyConfig.acceptedKid)
      case DeviceKeycloak  => List(keycloakDeviceConfig.acceptedKid)
    }

  final private val cache = new TrieMap[String, Key]()

  override def getKey(kid: String): Option[Key] = cache.get(kid)

  override def getDefaultKey(keycloakInstance: KeycloakInstance): Option[Key] = {
    val kids = acceptedKids(keycloakInstance)
    acceptedKids(keycloakInstance).find(kid => getKey(kid).isDefined).flatMap(x => getKey(x))
  }

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
