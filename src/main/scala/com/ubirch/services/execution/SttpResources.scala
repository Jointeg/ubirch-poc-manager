package com.ubirch.services.execution

import sttp.client.asynchttpclient.monix.AsyncHttpClientMonixBackend

object SttpResources {
  /**
    * This is one single sttp backend with Monix.Task
    * @Important: when you call a http request with Task, this backend object has to be used.
    */
  val monixBackend = AsyncHttpClientMonixBackend()
}
