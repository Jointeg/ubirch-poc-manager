package com.ubirch.services.teamdrive

import monix.eval.Task

object model {
  trait TeamDriveClient {
    def createSpace(name: String, path: String): Task[CreateSpaceResponse]
  }

  sealed trait CreateSpaceResponse
  case class SpaceCreated(spaceId: Int) extends CreateSpaceResponse

}
