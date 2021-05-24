package com.ubirch.teamdrive

import com.ubirch.models.auth.Base16String
import com.ubirch.services.poc.TestCertHandler
import com.ubirch.services.teamdrive.TeamDriveService.SharedCertificate
import com.ubirch.services.teamdrive.model._
import com.ubirch.services.teamdrive.{ SttpTeamDriveClient, TeamDriveServiceImpl }
import com.ubirch.test.{ HttpTest, TestData }
import org.json4s.Formats

class TeamDriveServiceTest extends HttpTest {
  implicit private val formats: Formats = org.json4s.DefaultFormats

  import com.ubirch.test.TaskSupport._

  "TeamDriveServiceTest.shareCert" must {
    "share cert and passphrase with given email addresses" in httpTest { httpStub =>
      // given
      val service = new TeamDriveServiceImpl(new SttpTeamDriveClient(sttpTeamDriveConfig(httpStub.url)))

      httpStub.getLoginInformationWillReturn(isLoginRequired = false)
      httpStub.loginWillBeOk()
      httpStub.spaceWillBeCreated(spaceId = 8, spaceName = "spaceName", spacePath = "spaceName")
      httpStub.fileWillBeSent(
        spaceId = 8,
        fileBody = TestCertHandler.passphrase.value.getBytes,
        fileName = "passphrase_spaceName.pwd",
        fileId = 16)
      httpStub.fileWillBeSent(
        spaceId = 8,
        fileBody = Base16String.toByteArray(TestCertHandler.validPkcs12),
        fileName = "cert_spaceName.pfx",
        fileId = 17)
      httpStub.invitationWillBeAccepted(spaceId = 8, email = TestData.email, permissionLevel = "read")
      httpStub.invitationWillBeAccepted(spaceId = 8, email = TestData.email2, permissionLevel = "read")

      // when
      val result = service.shareCert(
        SpaceName("spaceName"),
        Seq(TestData.email, TestData.email2),
        TestCertHandler.passphrase,
        TestCertHandler.validPkcs12).unwrap

      // then
      result must equal(SharedCertificate(SpaceName("spaceName"), SpaceId(8), FileId(16), FileId(17)))
      httpStub.loginWasNotCalled()
    }

    "login if it is required" in httpTest { httpStub =>
      // given
      val service = new TeamDriveServiceImpl(new SttpTeamDriveClient(sttpTeamDriveConfig(httpStub.url)))

      httpStub.getLoginInformationWillReturn(isLoginRequired = true)
      httpStub.loginWillBeOk()
      httpStub.spaceWillBeCreated(spaceId = 8, spaceName = "spaceName", spacePath = "spaceName")
      httpStub.fileWillBeSent(
        spaceId = 8,
        fileBody = TestCertHandler.passphrase.value.getBytes,
        fileName = "passphrase_spaceName.pwd",
        fileId = 16)
      httpStub.fileWillBeSent(
        spaceId = 8,
        fileBody = Base16String.toByteArray(TestCertHandler.validPkcs12),
        fileName = "cert_spaceName.pfx",
        fileId = 17)
      httpStub.invitationWillBeAccepted(spaceId = 8, email = TestData.email, permissionLevel = "read")
      httpStub.invitationWillBeAccepted(spaceId = 8, email = TestData.email2, permissionLevel = "read")

      // when
      val result = service.shareCert(
        SpaceName("spaceName"),
        Seq(TestData.email, TestData.email2),
        TestCertHandler.passphrase,
        TestCertHandler.validPkcs12).unwrap

      // then
      result must equal(SharedCertificate(SpaceName("spaceName"), SpaceId(8), FileId(16), FileId(17)))
      httpStub.loginWasCalled()
    }
  }

}
