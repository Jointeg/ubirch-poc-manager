package com.ubirch.services.poc
import com.google.inject.Inject
import com.typesafe.config.Config
import com.ubirch.ConfPaths.ServicesConfPaths.POC_CREATION_INTERVAL
import monix.reactive.Observable
import org.json4s.native.Serialization
import sttp.client.SttpBackend
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend

import javax.inject.Singleton
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

trait PocCreationLoop {
  def startPocCreationLoop[T](operation: PocCreationResult => Observable[T]): Observable[T]
}

@Singleton
class PocCreationLoopImpl @Inject() (conf: Config, pocCreator: PocCreator) extends PocCreationLoop {

  implicit private val backend: SttpBackend[Future, Nothing, WebSocketHandler] = AsyncHttpClientFutureBackend()
  implicit private val serialization: Serialization.type = org.json4s.native.Serialization
  private val pocCreatorInterval = conf.getInt(POC_CREATION_INTERVAL)

  private val startPocCreation: Observable[PocCreationResult] = {
    for {
      _ <- Observable.intervalWithFixedDelay(pocCreatorInterval.seconds)
      result <-
        Observable
          .fromTask(
            pocCreator
              .createPocs())
    } yield result
  }

  private def retryWithDelay[A](source: Observable[A]): Observable[A] = {
    source.onErrorHandleWith { _ =>
      retryWithDelay(source).delayExecution(pocCreatorInterval.seconds)
    }
  }

  override def startPocCreationLoop[T](operation: PocCreationResult => Observable[T]): Observable[T] =
    retryWithDelay(startPocCreation.flatMap(operation))

}
