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
    source.onErrorHandleWith {
      case NonFatal(ex) =>
        logger.error("some unexpected error occurred during poc admin creation", ex)
        retryWithDelay(source).delayExecution(pocAdminCreatorInterval.seconds)
    }
  }

  override def startPocAdminCreationLoop[T](operation: PocAdminCreationResult => Observable[T]): Observable[T] =
    retryWithDelay(startPocAdminCreation.flatMap(operation)).guaranteeCase {
      case ExitCase.Canceled  => Task(PocAdminCreationLoop.loopState.set(Cancelled))
      case ExitCase.Error(_)  => Task(PocAdminCreationLoop.loopState.set(ErrorTerminated(DateTime.now())))
      case ExitCase.Completed => Task(PocAdminCreationLoop.loopState.set(Completed))
    }
}

object PocAdminCreationLoop {
  val loopState: AtomicAny[LoopState] = AtomicAny(Starting(DateTime.now()))
}
