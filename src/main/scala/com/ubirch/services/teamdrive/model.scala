package com.ubirch.services.teamdrive

import com.ubirch.models.poc.Poc
import com.ubirch.models.tenant.Tenant
import monix.eval.Task

import java.nio.ByteBuffer

object model {
  sealed trait PermissionLevel

  trait TeamDriveClient {
    def createSpace(name: SpaceName, path: String): Task[SpaceId]
    def putFile(spaceId: SpaceId, fileName: String, file: ByteBuffer): Task[FileId]
    def inviteMember(spaceId: SpaceId, email: String, permissionLevel: PermissionLevel): Task[Boolean]
    def getSpaceIdByName(spaceName: SpaceName): Task[Option[SpaceId]]
  }

  case class SpaceId(v: Int) extends AnyVal {
    override def toString: String = v.toString
  }

  case class SpaceName(v: String) extends AnyVal {
    override def toString: String = v
  }

  object SpaceName {
    def forTenant(stage: String, tenant: Tenant): SpaceName =
      SpaceName(s"${stage}_${tenant.tenantName.value}")
    def forPoc(stage: String, tenant: Tenant, poc: Poc): SpaceName =
      SpaceName(s"${stage}_${poc.pocType.split("_")(1)}_${tenant.tenantName.value}_${poc.pocName}_${poc.externalId}")
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
}
