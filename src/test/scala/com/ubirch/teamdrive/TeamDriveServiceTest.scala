package com.ubirch.teamdrive

import com.ubirch.TestBase
import com.ubirch.services.poc.TestCertHandler
import com.ubirch.services.teamdrive.TeamDriveService
import com.ubirch.test.{ FakeTeamDriveClient, TestData }

class TeamDriveServiceTest extends TestBase {
  import com.ubirch.test.TaskSupport._

  "TeamDriveServiceTest.shareCert" must {
    "share cert and passphrase with given email address" in testTeamDriveService { (service, fakeClient) =>
      // when
      val result = service.shareCert(
        "spaceName",
        Seq(TestData.email, TestData.email2),
        TestCertHandler.passphrase,
        TestCertHandler.validPkcs12).unwrap

      // expect
      fakeClient.spaceIsCreated(result.spaceId, "spaceName") mustBe true
      fakeClient.fileExist(result.spaceId, result.certificateFileId) mustBe true
      fakeClient.fileExist(result.spaceId, result.passphraseFileId) mustBe true
      fakeClient.emailIsInvited(result.spaceId, TestData.email) mustBe true
      fakeClient.emailIsInvited(result.spaceId, TestData.email2) mustBe true
    }
  }

  def testTeamDriveService(test: (TeamDriveService, FakeTeamDriveClient) => Unit): Unit = {
    val client = new FakeTeamDriveClient()
    val service = new TeamDriveService(client)
    test(service, client)
    ()
  }
}
