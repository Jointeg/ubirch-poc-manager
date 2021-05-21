package com.ubirch.test

import com.ubirch.services.teamdrive.model.{ FileId, PermissionLevel, SpaceId, SpaceName, TeamDriveClient }
import monix.eval.Task

import java.nio.ByteBuffer
import javax.inject.Singleton
import scala.collection._

/**
  * Is not thread-safe
  */
@Singleton
class FakeTeamDriveClient extends TeamDriveClient {
  private var files: Map[SpaceId, Seq[FileId]] = Map.empty
  private var spaces: Map[SpaceId, String] = Map.empty
  private var invitations: Map[SpaceId, Seq[String]] = Map.empty
  private var nextSpaceId: SpaceId = SpaceId(1)
  private var nextFileId: FileId = FileId(1)

  override def createSpace(name: SpaceName, path: String): Task[SpaceId] =
    Task {
      val id = nextSpaceId
      nextSpaceId = id.copy(id.v + 1)
      files = files + (id -> Seq.empty)
      spaces = spaces + (id -> name.v)
      invitations = invitations + (id -> Seq.empty)
      id
    }

  override def putFile(spaceId: SpaceId, fileName: String, file: ByteBuffer): Task[FileId] =
    Task {
      val id = nextFileId
      nextFileId = id.copy(id.v + 1)
      files = files + (spaceId -> (files(spaceId) :+ id))
      id
    }

  override def inviteMember(
    spaceId: SpaceId,
    email: String,
    permissionLevel: PermissionLevel
  ): Task[Boolean] =
    Task {
      spaces.get(spaceId) match {
        case Some(_) =>
          invitations = invitations + (spaceId -> (invitations(spaceId) :+ email))
          true
        case None => false
      }
    }

  override def getSpaceIdByName(spaceName: SpaceName): Task[Option[SpaceId]] = Task {
    spaces.filter(_._2 == spaceName.v).keys.headOption
  }

  def emailIsInvited(spaceId: SpaceId, email: String): Boolean =
    invitations(spaceId).contains(email)

  def fileExist(spaceId: SpaceId, certificateFileId: FileId): Boolean =
    files(spaceId).contains(certificateFileId)

  def spaceIsCreated(spaceId: SpaceId, spaceName: String): Boolean = spaces(spaceId) == spaceName

}
