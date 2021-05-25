package com.ubirch.services.poc
import com.google.inject.Inject
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ServicesConfPaths.POC_CREATION_INTERVAL
import monix.reactive.Observable

import javax.inject.Singleton
import scala.concurrent.duration.DurationInt

trait PocCreationLoop {
  def startPocCreationLoop[T](operation: PocCreationResult => Observable[T]): Observable[T]
}

@Singleton
class PocCreationLoopImpl @Inject() (conf: Config, pocCreator: PocCreator) extends PocCreationLoop with LazyLogging {

  private val pocCreatorInterval = conf.getInt(POC_CREATION_INTERVAL)

  private val startPocCreation: Observable[PocCreationResult] = {
    for {
      _ <- Observable.intervalWithFixedDelay(pocCreatorInterval.seconds)
      result <- Observable.fromTask(pocCreator.createPocs())
    } yield result
  }

  private def retryWithDelay[A](source: Observable[A]): Observable[A] = {
    source.onErrorHandleWith { ex =>
      logger.error("some unexpected error occurred during poc creation", ex)
      retryWithDelay(source).delayExecution(pocCreatorInterval.seconds)
    }
  }

  override def startPocCreationLoop[T](operation: PocCreationResult => Observable[T]): Observable[T] =
    retryWithDelay(startPocCreation.flatMap(operation))

}
