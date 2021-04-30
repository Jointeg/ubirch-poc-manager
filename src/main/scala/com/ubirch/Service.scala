package com.ubirch

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.db.FlywayProvider
import com.ubirch.services.jwt.PublicKeyPoolService
import com.ubirch.services.rest.RestService
import com.ubirch.services.{DeviceKeycloak, UsersKeycloak}
import monix.eval.Task
import monix.execution.Scheduler

import java.util.concurrent.CountDownLatch
import javax.inject.{Inject, Singleton}

/**
  * Represents a bootable service object that starts the system
  */
@Singleton
class Service @Inject()(
                         restService: RestService,
                         publicKeyPoolService: PublicKeyPoolService,
                         flywayProvider: FlywayProvider
                         /*keycloakUserPollingService: UserPollingService*/)(implicit scheduler: Scheduler)
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

    flywayProvider.getFlyway.migrate()

    //    val pollingService = keycloakUserPollingService.via(resp => Observable(resp)).subscribe()
    //
    //    sys.addShutdownHook {
    //      logger.info("Shutdown of polling service")
    //      pollingService.cancel()
    //    }

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
