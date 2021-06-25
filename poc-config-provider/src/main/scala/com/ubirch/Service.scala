package com.ubirch
import com.ubirch.services.rest.RestService

import java.util.concurrent.CountDownLatch
import javax.inject.{ Inject, Singleton }

@Singleton
class Service @Inject() (restService: RestService)() {

  def start(): Unit = {
    restService.start()

    val cd = new CountDownLatch(1)
    cd.await()
  }

}

object Service extends Boot(List(new Binder)) {
  def main(args: Array[String]): Unit = * {
    get[Service].start()
  }
}
