package com.ubirch.services.poc

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ServicesConfPaths.POC_CREATION_INTERVAL
import monix.reactive.Observable

import javax.inject.Inject
import scala.concurrent.duration.DurationInt

trait PocEmployeeCreationLoop {
  def startPocEmployeeCreationLoop[T](operation: PocEmployeeCreationResult => Observable[T]): Observable[T]
}

class PocEmployeeCreationLoopImpl @Inject() (conf: Config, employeeCreator: PocEmployeeCreator)
  extends PocEmployeeCreationLoop
  with LazyLogging {
  private val pocEmployeeCreatorInterval = conf.getInt(POC_CREATION_INTERVAL)

  private val startPocEmployeeCreation: Observable[PocEmployeeCreationResult] = {
    for {
      _ <- Observable.intervalWithFixedDelay(pocEmployeeCreatorInterval.seconds)
      result <- Observable.fromTask(employeeCreator.createPocEmployees())
    } yield result
  }

  private def retryWithDelay[A](source: Observable[A]): Observable[A] = {
    source.onErrorHandleWith { ex =>
      logger.error("some unexpected error occurred during poc employee creation", ex)
      retryWithDelay(source).delayExecution(pocEmployeeCreatorInterval.seconds)
    }
  }

  override def startPocEmployeeCreationLoop[T](operation: PocEmployeeCreationResult => Observable[T]): Observable[T] =
    retryWithDelay(startPocEmployeeCreation.flatMap(operation))
}
