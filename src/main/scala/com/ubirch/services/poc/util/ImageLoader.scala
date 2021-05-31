package com.ubirch.services.poc.util

import com.ubirch.services.execution.SttpResources
import monix.eval.Task
import monix.execution.Scheduler
import sttp.client.{ asByteArray, basicRequest, UriContext }
import java.net.URL
import javax.inject.{ Inject, Singleton }

trait ImageLoader {
  def getImage(url: URL): Task[Array[Byte]]
}

@Singleton
class ImageLoaderImpl @Inject() (implicit scheduler: Scheduler) extends ImageLoader {
  def getImage(url: URL): Task[Array[Byte]] = Task.deferFuture {
    val request = basicRequest
      .get(uri"$url")
      .response(asByteArray)
    SttpResources.backend.send(request).map { r =>
      r.body match {
        case Right(response: Array[Byte]) => response
        case Left(error) =>
          throw ImageLoadingError(error)
      }
    }
  }
}

case class ImageLoadingError(message: String) extends Exception