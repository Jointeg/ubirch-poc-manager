package com.ubirch.test

import cats.effect.{IO, Resource}
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.ubirch.TestBase

import java.net.ServerSocket

trait HttpTest extends TestBase {
  def httpTest(test: HttpStub => Unit): Unit = {
    val port: Int = randomPort().unsafeRunSync()
    val wiremockUrl = s"http://localhost:$port"
    val wireMockServer = new WireMockServer(wireMockConfig().port(port))
    wireMockServer.start()
    val httpStub = new HttpStub(wireMockServer, wiremockUrl)
    test(httpStub)
    wireMockServer.stop()
    ()
  }

  private def randomPort(): IO[Int] =
    Resource
      .fromAutoCloseable(IO(new ServerSocket(0)))
      .use { in =>
        IO {
          while (!in.isBound) Thread.sleep(50)
          in.getLocalPort
        }
      }
}
