package com.ubirch.test

import com.ubirch.services.teamdrive.model
import com.ubirch.services.teamdrive.model.{FileId, SpaceId, TeamDriveClient}
import monix.eval.Task

import java.nio.ByteBuffer

class FakeTeamDriveClient extends TeamDriveClient {
  private var nextSpaceId: SpaceId = SpaceId(1)
  private var spaces: Seq[SpaceId] = Seq.empty
  private var files: Map[SpaceId, Seq[FileId]] = Map.empty

  override def createSpace(name: String, path: String): Task[model.SpaceId] =
    Task {
      val id = nextSpaceId
      nextSpaceId = id.copy(id.v + 1)
      spaces = spaces :+ id
      id
    }

  override def putFile(spaceId: model.SpaceId, fileName: String, file: ByteBuffer): Task[model.FileId] = ???

  override def inviteMember(spaceId: model.SpaceId, email: String, permissionLevel: model.PermissionLevel): Task[Boolean] = ???

  def emailIsInvited(spaceId: SpaceId, email: String): Boolean = ???

  def fileExist(spaceId: SpaceId, certificateFileId: FileId): Boolean = ???

  def spaceIsCreated(spaceId: SpaceId, spaceName: String): Boolean = ???
}
