package com.ubirch.services.poc

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ServicesConfPaths.POC_CREATION_INTERVAL
import monix.reactive.Observable

import javax.inject.Inject
import scala.concurrent.duration.DurationInt

trait PocAdminCreationLoop {
  def startPocAdminCreationLoop[T](operation: PocAdminCreationResult => Observable[T]): Observable[T]
}

class PocAdminCreationLoopImpl @Inject() (conf: Config, pocAdminCreator: PocAdminCreator)
  extends PocAdminCreationLoop
  with LazyLogging {
  private val pocAdminCreatorInterval = conf.getInt(POC_CREATION_INTERVAL)

  private val startPocAdminCreation: Observable[PocAdminCreationResult] = {
    for {
      _ <- Observable.intervalWithFixedDelay(pocAdminCreatorInterval.seconds)
      result <- Observable.fromTask(pocAdminCreator.createPocAdmins())
    } yield result
  }

  private def retryWithDelay[A](source: Observable[A]): Observable[A] = {
    source.onErrorHandleWith { ex =>
      logger.error("some unexpected error occurred during poc admin creation", ex)
      retryWithDelay(source).delayExecution(pocAdminCreatorInterval.seconds)
    }
  }

  override def startPocAdminCreationLoop[T](operation: PocAdminCreationResult => Observable[T]): Observable[T] =
    retryWithDelay(startPocAdminCreation.flatMap(operation))
}
