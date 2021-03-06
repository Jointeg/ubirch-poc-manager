package com.ubirch.services.teamdrive

import com.ubirch.models.poc.Poc
import com.ubirch.models.tenant.Tenant
import com.ubirch.services.teamdrive.SttpTeamDriveClient.Space
import monix.eval.Task

import java.nio.ByteBuffer

object model {
  sealed trait PermissionLevel
  sealed trait SpaceStatus {
    val value: String
  }

  trait TeamDriveClient {
    def createSpace(name: SpaceName, path: String): Task[SpaceId]
    def putFile(spaceId: SpaceId, fileName: String, file: ByteBuffer): Task[FileId]
    def inviteMember(
      spaceId: SpaceId,
      email: String,
      welcomeMessage: String,
      permissionLevel: PermissionLevel): Task[Boolean]
    def getSpaceByName(spaceName: SpaceName): Task[Option[Space]]

    /**
      * This method gets space and makes the space active when the space is archived
      */
    def getSpaceByNameWithActivation(spaceName: SpaceName): Task[Option[Space]]
    def getLoginInformation(): Task[LoginInformation]
    def login(): Task[Unit]
    def withLogin[T](mainTask: => Task[T]): Task[T]
    def activateSpace(spaceId: SpaceId): Task[Unit]
  }

  case class SpaceId(v: Int) extends AnyVal {
    override def toString: String = v.toString
  }

  case class SpaceName(v: String) extends AnyVal {
    override def toString: String = v
  }

  case class LoginInformation(isLoginRequired: Boolean)

  object SpaceName {
    def ofTenant(stage: String, tenant: Tenant): SpaceName =
      SpaceName(s"${stage}_${tenant.tenantName.value}")
    def ofPoc(stage: String, tenant: Tenant, poc: Poc): SpaceName =
      SpaceName(s"${stage}_${poc.pocType.split("_")(1)}_${tenant.tenantName.value}_${poc.pocName}_${poc.externalId}")
    def of(stage: String, name: String): SpaceName =
      SpaceName(s"${stage}_$name")
  }

  case class FileId(v: Int) extends AnyVal

  sealed trait TeamDriveException {
    val message: String
  }

  case class TeamDriveHttpError(code: Int, message: String)
    extends RuntimeException(s"TeamDrive failed with message '$message' and code '$code'")
    with TeamDriveException
  case class TeamDriveError(message: String)
    extends RuntimeException(s"TeamDrive failed with message '$message'")
    with TeamDriveException

  case object Read extends PermissionLevel

  case object ReadWrite extends PermissionLevel

  object PermissionLevel {
    def toFormattedString(status: PermissionLevel): String = status match {
      case Read      => "read"
      case ReadWrite => "readWrite"
    }
  }

  case object Active extends SpaceStatus {
    val value = "active"
  }

  case object Archived extends SpaceStatus {
    val value = "archived"
  }
}
