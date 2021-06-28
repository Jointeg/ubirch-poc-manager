package com.ubirch.teamdrive

import com.ubirch.services.teamdrive._
import com.ubirch.services.teamdrive.model.{ LoginInformation, Read, SpaceName }
import com.ubirch.test.TaskSupport._
import com.ubirch.test.TestData._
import com.ubirch.test._
import monix.eval.Task
import org.json4s.Formats
import sttp.client.SttpClientException

import java.nio.ByteBuffer
import scala.concurrent.duration._

class SttpTeamDriveClientTest extends HttpTest {

  implicit private val formats: Formats = org.json4s.DefaultFormats

  "SttpTeamDriveClient.createSpace" must {
    "create one for given name and path" in httpTest { httpStub =>
      // given
      val client = new SttpTeamDriveClient(sttpTeamDriveConfig(httpStub.url))
      httpStub.spaceWillBeCreated(spaceId = 8, spaceName = spaceName, spacePath = spacePath)

      // when
      val response = client.createSpace(SpaceName(spaceName), spacePath).unwrap

      // then
      response mustBe model.SpaceId(8)
    }

    "fail when TeamDrive responds with an error" in httpTest { httpStub =>
      // given
      val client = new SttpTeamDriveClient(sttpTeamDriveConfig(httpStub.url))
      httpStub.createSpaceWillFail(errorCode = 30, errorMessage = "some error", statusCode = 400)

      // when
      val response = client.createSpace(SpaceName(spaceName), spacePath).catchError

      // then
      response mustBe model.TeamDriveHttpError(30, "some error")
    }
  }

  "SttpTeamDriveClient.putFile" must {
    "send file to given space" in httpTest { httpStub =>
      // given
      val client = new SttpTeamDriveClient(sttpTeamDriveConfig(httpStub.url))
      val content = "content".getBytes
      httpStub.fileWillBeSent(spaceId = 8, fileBody = content, fileName = "cert.txt", fileId = 16)

      // when
      val response = client.putFile(model.SpaceId(8), "cert.txt", ByteBuffer.wrap(content)).unwrap

      // then
      response mustBe model.FileId(16)
    }

    "fail when TeamDrive responds with an error" in httpTest { httpStub =>
      // given
      val client = new SttpTeamDriveClient(sttpTeamDriveConfig(httpStub.url))
      httpStub.putFileWillFail(
        errorCode = 30,
        errorMessage = "some error",
        statusCode = 400,
        fileName = "cert.txt",
        spaceId = 8)

      // when
      val response = client.putFile(model.SpaceId(8), "cert.txt", ByteBuffer.wrap("content".getBytes)).catchError

      // then
      response mustBe model.TeamDriveHttpError(30, "some error")
    }
  }

  "SttpTeamDriveClient.inviteMember" must {
    "send invitation" in httpTest { httpStub =>
      // given
      val client = new SttpTeamDriveClient(sttpTeamDriveConfig(httpStub.url))
      httpStub.invitationWillBeAccepted(spaceId = 8, permissionLevel = "read", email = "admin@ubirch.com")

      // when
      val response = client.inviteMember(model.SpaceId(8), "admin@ubirch.com", "welcome", Read).unwrap

      // then
      response mustBe true
    }

    "fail when TeamDrive responds with an error" in httpTest { httpStub =>
      // given
      val client = new SttpTeamDriveClient(sttpTeamDriveConfig(httpStub.url))
      httpStub.invitationWillFail(
        errorCode = 30,
        errorMessage = "some error",
        statusCode = 400,
        spaceId = 8,
        permissionLevel = "read",
        email = "admin@ubirch.com")

      // when
      val response = client.inviteMember(model.SpaceId(8), "admin@ubirch.com", "welcome", Read).catchError

      // then
      response mustBe model.TeamDriveHttpError(30, "some error")
    }
  }

  "SttpTeamDriveClient.getLoginInformation" must {
    "return whether login is required" in httpTest { httpStub =>
      // given
      val client = new SttpTeamDriveClient(sttpTeamDriveConfig(httpStub.url))
      httpStub.getLoginInformationWillReturn(isLoginRequired = true)

      // when
      val response = client.getLoginInformation().unwrap

      // then
      response mustBe LoginInformation(isLoginRequired = true)
    }

    "fail when TeamDrive responds with an error" in httpTest { httpStub =>
      // given
      val client = new SttpTeamDriveClient(sttpTeamDriveConfig(httpStub.url))
      httpStub.getLoginInformationWillFail(errorMessage = "some error", statusCode = 400, errorCode = 30)

      // when
      val response = client.getLoginInformation().catchError

      // then
      response mustBe model.TeamDriveHttpError(30, "some error")
    }
  }

  "SttpTeamDriveClient.login" must {
    "return whether login is required" in httpTest { httpStub =>
      // given
      val client = new SttpTeamDriveClient(sttpTeamDriveConfig(httpStub.url))
      httpStub.loginWillBeOk(username, password)

      // expect
      client.login().unwrap mustBe ((): Unit)
    }

    "fail when TeamDrive responds with an error" in httpTest { httpStub =>
      // given
      val client = new SttpTeamDriveClient(sttpTeamDriveConfig(httpStub.url))
      httpStub.loginWillFail(
        username = username,
        password = password,
        errorMessage = "some error",
        statusCode = 400,
        errorCode = 30)

      // when
      val response = client.login().catchError

      // then
      response mustBe model.TeamDriveHttpError(30, "some error")
    }
  }

  "SttpTeamDriveClient" must {
    "get request timeout on each endpoint, for configured value" in httpTest { httpStub =>
      // given
      val client = new SttpTeamDriveClient(sttpTeamDriveConfig(url = httpStub.url, readTimeout = 1.second))
      httpStub.anyRequestWillTimeout(delay = 5.seconds)

      val matchTimeout: PartialFunction[Throwable, String] = {
        case _: SttpClientException.ReadException => "read timeout"
      }
      val createSpace = client.createSpace(SpaceName(spaceName), spacePath).onErrorRecover(matchTimeout)
      val inviteMember =
        client.inviteMember(model.SpaceId(8), "admin@ubirch.com", "welcome", Read).onErrorRecover(matchTimeout)
      val putFile =
        client.putFile(model.SpaceId(8), "cert.txt", ByteBuffer.wrap("content".getBytes)).onErrorRecover(matchTimeout)
      val getLoginInformation = client.getLoginInformation().onErrorRecover(matchTimeout)
      val login = client.login().onErrorRecover(matchTimeout)

      // when
      val a = Task.gather(Seq(createSpace, inviteMember, putFile, getLoginInformation, login)).runSyncUnsafe()

      // then
      a.size mustBe a.count(_ == "read timeout")
    }

    "get connection timeout on each endpoint, for configured value" in httpTest { httpStub =>
      // given
      httpStub.kill()
      val client = new SttpTeamDriveClient(sttpTeamDriveConfig(url = httpStub.url))

      val matchTimeout: PartialFunction[Throwable, String] = {
        case _: SttpClientException.ConnectException => "connection timeout"
      }
      val createSpace = client.createSpace(SpaceName(spaceName), spacePath).onErrorRecover(matchTimeout)
      val inviteMember =
        client.inviteMember(model.SpaceId(8), "admin@ubirch.com", "welcome", Read).onErrorRecover(matchTimeout)
      val putFile =
        client.putFile(model.SpaceId(8), "cert.txt", ByteBuffer.wrap("content".getBytes)).onErrorRecover(matchTimeout)
      val getLoginInformation = client.getLoginInformation().onErrorRecover(matchTimeout)
      val login = client.login().onErrorRecover(matchTimeout)

      // when
      val a = Task.gather(Seq(createSpace, inviteMember, putFile, getLoginInformation, login)).runSyncUnsafe()

      // then
      a.size mustBe a.count(_ == "connection timeout")
    }
  }
}
