package com.ubirch.test

import com.ubirch.services.teamdrive.SttpTeamDriveClient.Space
import com.ubirch.services.teamdrive.model._
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
    welcomeMessage: String,
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

  override def getSpaceByName(spaceName: SpaceName): Task[Option[Space]] = Task {
    spaces.filter(_._2 == spaceName.v).headOption.map {
      case (spaceId, name) =>
        Space(spaceId.v, name, Active.value, "read", true)
    }
  }

  override def getSpaceByNameWithActivation(spaceName: SpaceName): Task[Option[Space]] =
    getSpaceByName(spaceName)

  def emailIsInvited(spaceId: SpaceId, email: String): Boolean =
    invitations(spaceId).contains(email)

  def fileExist(spaceId: SpaceId, certificateFileId: FileId): Boolean =
    files(spaceId).contains(certificateFileId)

  def spaceIsCreated(spaceId: SpaceId, spaceName: String): Boolean = spaces(spaceId) == spaceName

  override def getLoginInformation(): Task[LoginInformation] = Task.pure(LoginInformation(isLoginRequired = false))

  override def withLogin[T](mainTask: => Task[T]): Task[T] = mainTask

  override def login(): Task[Unit] = Task.unit

  override def activateSpace(spaceId: SpaceId): Task[Unit] = Task.unit
}
