package com.ubirch.services.execution

import sttp.client.SttpBackend
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend

import scala.concurrent.Future

object SttpResources {
  /**
    * This is one single sttp backend with Future
    * @Important: when you call a http request with Future, this backend object has to be used.
    */
  val backend: SttpBackend[Future, Nothing, WebSocketHandler] = AsyncHttpClientFutureBackend()
}
