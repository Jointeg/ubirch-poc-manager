package com.ubirch

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.db.FlywayProvider
import com.ubirch.models.auth.Base64String
import com.ubirch.services.jwt.PublicKeyPoolService
import com.ubirch.services.keyhash.KeyHashVerifierService
import com.ubirch.services.poc.PocCreationLoop
import com.ubirch.services.rest.RestService
import com.ubirch.services.{ DeviceKeycloak, UsersKeycloak }
import monix.eval.Task
import monix.execution.Scheduler
import monix.reactive.Observable
import org.flywaydb.core.api.FlywayException

import java.util.concurrent.CountDownLatch
import javax.inject.{ Inject, Singleton }
import scala.concurrent.duration.DurationInt
/**
  * Represents a bootable service object that starts the system
  */
@Singleton
class Service @Inject() (
  restService: RestService,
  publicKeyPoolService: PublicKeyPoolService,
  flywayProvider: FlywayProvider,
  config: Config,
  keyHashVerifierService: KeyHashVerifierService,
  pocCreationLoop: PocCreationLoop
  /*keycloakUserPollingService: UserPollingService*/ )(implicit scheduler: Scheduler)
  extends LazyLogging {

  def start(): Unit = {

    publicKeyPoolService
      .init(UsersKeycloak, DeviceKeycloak)
      .doOnFinish {
        case Some(e) =>
          Task.delay(logger.error("error_loading_keys", e))
        case None =>
          Task.delay {
            restService.start()
          }
      }
      .runToFuture

    try {
      flywayProvider.getFlyway.migrate()
    } catch {
      case ex: FlywayException =>
        logger.error(s"something went wrong on database migration: ${ex.getMessage}", ex)
        throw ex
      case ex: Throwable =>
        logger.error(s"something went wrong on database migration; unknown exceptionType: ${ex.getMessage}", ex)
        throw ex
    }

    keyHashVerifierService
      .verifyHash(Base64String(config.getString(ConfPaths.AESEncryptionPaths.SECRET_KEY)))
      .runSyncUnsafe(15.seconds)

    //    val pollingService = keycloakUserPollingService.via(resp => Observable(resp)).subscribe()
    //
    //    sys.addShutdownHook {
    //      logger.info("Shutdown of polling service")
    //      pollingService.cancel()
    //    }

    val pocCreation = pocCreationLoop.startPocCreationLoop(resp => Observable(resp)).subscribe()

    sys.addShutdownHook {
      logger.info("Shutdown of poc creation loop service")
      pocCreation.cancel()
    }

    val cd = new CountDownLatch(1)
    cd.await()
  }

}

object Service extends Boot(List(new Binder)) {
  def main(args: Array[String]): Unit =
    * {
      get[Service].start()
    }
}
