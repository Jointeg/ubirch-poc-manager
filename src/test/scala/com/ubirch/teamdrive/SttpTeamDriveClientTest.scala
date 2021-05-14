package com.ubirch.teamdrive

import com.ubirch.services.teamdrive._
import com.ubirch.test._
import org.json4s.{Formats, Serialization}
import sttp.client.SttpBackend
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend

import scala.concurrent.Future
import scala.concurrent.duration._

class SttpTeamDriveClientTest extends HttpTest {

  implicit private val backend: SttpBackend[Future, Nothing, WebSocketHandler] = AsyncHttpClientFutureBackend()(ec)
  implicit private val serialization: Serialization = org.json4s.native.Serialization
  implicit private val formats: Formats = org.json4s.DefaultFormats

  import SttpTeamDriveClientTest._

  "SttpTeamDriveClient" should {
    "create space for given name and path" in testWithHttp { httpStub =>
      // given
      val client = new SttpTeamDriveClient(config(httpStub.url))
      httpStub.spaceWillBeCreated(spaceId = 1410, spaceName = "test-space", spacePath = "test/space")

      // when
      val response = client.createSpace("test-space", "test/space").runSyncUnsafe(2.seconds)

      // then
      response mustBe model.SpaceCreated(spaceId = 1410)
    }
  }
}

object SttpTeamDriveClientTest {
  def config(url: String, username: String = "username", password: String = "password"): SttpTeamDriveClient.Config =
    SttpTeamDriveClient.Config(url, username, password)
}
