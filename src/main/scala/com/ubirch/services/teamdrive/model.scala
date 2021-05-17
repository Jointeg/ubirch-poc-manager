package com.ubirch.services.teamdrive

import monix.eval.Task

import java.nio.ByteBuffer

object model {
  trait TeamDriveClient {
    def createSpace(name: String, path: String): Task[SpaceId]
    def putFile(spaceId: SpaceId, fileName: String, file: ByteBuffer): Task[FileId]
  }

  case class SpaceId(v: Int) extends AnyVal
  case class FileId(v: Int) extends AnyVal

  case class TeamDriveError(code: Int, message: String)
    extends RuntimeException(s"TeamDrive failed with message '$message' and code '$code'")
}
