package com.ubirch.services.poc

import cats.effect.ExitCase
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ServicesConfPaths.POC_CREATION_INTERVAL
import com.ubirch.models.common._
import monix.eval.Task
import monix.execution.atomic.AtomicAny
import monix.reactive.Observable
import org.joda.time.DateTime

import javax.inject.Inject
import scala.concurrent.duration.DurationInt
import scala.util.control.NonFatal

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
    source.onErrorHandleWith {
      case NonFatal(ex) =>
        logger.error("some unexpected error occurred during poc employee creation", ex)
        retryWithDelay(source).delayExecution(pocEmployeeCreatorInterval.seconds)
    }
  }

  override def startPocEmployeeCreationLoop[T](operation: PocEmployeeCreationResult => Observable[T]): Observable[T] =
    retryWithDelay(startPocEmployeeCreation.flatMap(operation)).guaranteeCase {
      case ExitCase.Canceled  => Task(PocEmployeeCreationLoop.loopState.set(Cancelled))
      case ExitCase.Error(_)  => Task(PocEmployeeCreationLoop.loopState.set(ErrorTerminated(DateTime.now())))
      case ExitCase.Completed => Task(PocEmployeeCreationLoop.loopState.set(Completed))
    }
}

object PocEmployeeCreationLoop {
  val loopState: AtomicAny[LoopState] = AtomicAny(Starting(DateTime.now()))
}
