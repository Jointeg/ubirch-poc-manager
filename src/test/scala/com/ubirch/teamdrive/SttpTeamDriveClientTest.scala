package com.ubirch.teamdrive

import com.ubirch.UnitTestBase
import com.ubirch.services.teamdrive.SttpTeamDriveClient

class SttpTeamDriveClientTest extends UnitTestBase {
  "SttpTeamDriveClientTest" should {
    "work" in {
      val a = new SttpTeamDriveClient()
      val r = a.createSpace()
//      val r = a.getSpaces()
//      val r = a.putFile()
//      val r = a.inviteMember()
      println(r)
      true shouldEqual true
    }
  }
}

