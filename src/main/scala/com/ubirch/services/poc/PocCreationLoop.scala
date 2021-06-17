package com.ubirch.services.poc
import cats.effect.ExitCase
import com.google.inject.Inject
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ServicesConfPaths.POC_CREATION_INTERVAL
import com.ubirch.models.common._
import monix.eval.Task
import monix.execution.atomic.AtomicAny
import monix.reactive.Observable
import org.joda.time.DateTime

import javax.inject.Singleton
import scala.concurrent.duration.DurationInt
import scala.util.control.NonFatal

trait PocCreationLoop {
  def startPocCreationLoop: Observable[Unit]
}

@Singleton
class PocCreationLoopImpl @Inject() (conf: Config, pocCreator: PocCreator) extends PocCreationLoop with LazyLogging {

  private val pocCreatorInterval = conf.getInt(POC_CREATION_INTERVAL)

  private val startPocCreation: Observable[Unit] = {
    for {
      _ <- Observable.intervalWithFixedDelay(pocCreatorInterval.seconds)
      result <- Observable.fromTask(pocCreator.createPocs())
    } yield result
  }

  private def retryWithDelay[A](source: Observable[A]): Observable[A] = {
    source.onErrorHandleWith {
      case NonFatal(ex) =>
        logger.error("some unexpected error occurred during poc creation", ex)
        retryWithDelay(source).delayExecution(pocCreatorInterval.seconds)
    }
  }

  override def startPocCreationLoop: Observable[Unit] =
    retryWithDelay(startPocCreation).guaranteeCase {
      case ExitCase.Canceled =>
        logger.info("Canceled")
        Task(PocCreationLoop.loopState.set(Cancelled))
      case ExitCase.Error(_) =>
        logger.info("Error")
        Task(PocCreationLoop.loopState.set(ErrorTerminated(DateTime.now())))
      case ExitCase.Completed =>
        logger.info("Completed")
        Task(PocCreationLoop.loopState.set(Completed))
    }

}

object PocCreationLoop {
  val loopState: AtomicAny[LoopState] = AtomicAny(Starting(DateTime.now()))
}
