package com.ubirch.teamdrive

import com.ubirch.services.teamdrive._
import com.ubirch.services.teamdrive.model.Read
import com.ubirch.test.TaskSupport._
import com.ubirch.test.TestData._
import com.ubirch.test._
import org.json4s.{ Formats, Serialization }
import sttp.client.SttpBackend
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend

import java.nio.ByteBuffer
import scala.concurrent.Future

class SttpTeamDriveClientTest extends HttpTest {

  implicit private val backend: SttpBackend[Future, Nothing, WebSocketHandler] = AsyncHttpClientFutureBackend()(ec)
  implicit private val serialization: Serialization = org.json4s.native.Serialization
  implicit private val formats: Formats = org.json4s.DefaultFormats

  import SttpTeamDriveClientTest._

  "SttpTeamDriveClient.createSpace" should {
    "create one for given name and path" in httpTest { httpStub =>
      // given
      val client = new SttpTeamDriveClient(config(httpStub.url))
      httpStub.spaceWillBeCreated(spaceId = 8, spaceName = spaceName, spacePath = spacePath)

      // when
      val response = client.createSpace(spaceName, spacePath).unwrap

      // then
      response mustBe model.SpaceId(8)
    }

    "fail when TeamDrive responds with an error" in httpTest { httpStub =>
      // given
      val client = new SttpTeamDriveClient(config(httpStub.url))
      httpStub.createSpaceWillFail(errorCode = 30, errorMessage = "some error", statusCode = 400)

      // when
      val response = client.createSpace(spaceName, spacePath).catchError

      // then
      response mustBe model.TeamDriveError(30, "some error")
    }
  }

  "SttpTeamDriveClient.putFile" should {
    "send file to given space" in httpTest { httpStub =>
      // given
      val client = new SttpTeamDriveClient(config(httpStub.url))
      val content = "content".getBytes
      httpStub.fileWillBeSent(spaceId = 8, fileBody = content, fileName = "cert.txt", fileId = 16)

      // when
      val response = client.putFile(model.SpaceId(8), "cert.txt", ByteBuffer.wrap(content)).unwrap

      // then
      response mustBe model.FileId(16)
    }

    "fail when TeamDrive responds with an error" in httpTest { httpStub =>
      // given
      val client = new SttpTeamDriveClient(config(httpStub.url))
      httpStub.putFileWillFail(
        errorCode = 30,
        errorMessage = "some error",
        statusCode = 400,
        fileName = "cert.txt",
        spaceId = 8)

      // when
      val response = client.putFile(model.SpaceId(8), "cert.txt", ByteBuffer.wrap("content".getBytes)).catchError

      // then
      response mustBe model.TeamDriveError(30, "some error")
    }
  }

  "SttpTeamDriveClient.inviteMember" should {
    "send invitation" in httpTest { httpStub =>
      // given
      val client = new SttpTeamDriveClient(config(httpStub.url))
      httpStub.invitationWillBeAccepted(spaceId = 8, permissionLevel = "read", email = "admin@ubirch.com")

      // when
      val response = client.inviteMember(model.SpaceId(8), "admin@ubirch.com", Read).unwrap

      // then
      response mustBe true
    }

    "fail when TeamDrive responds with an error" in httpTest { httpStub =>
      // given
      val client = new SttpTeamDriveClient(config(httpStub.url))
      httpStub.invitationWillFail(
        errorCode = 30,
        errorMessage = "some error",
        statusCode = 400,
        spaceId = 8,
        permissionLevel = "read",
        email = "admin@ubirch.com")

      // when
      val response = client.inviteMember(model.SpaceId(8), "admin@ubirch.com", Read).catchError

      // then
      response mustBe model.TeamDriveError(30, "some error")
    }
  }
}

object SttpTeamDriveClientTest {
  def config(url: String, username: String = "username", password: String = "password"): SttpTeamDriveClient.Config =
    SttpTeamDriveClient.Config(url, username, password)
}
