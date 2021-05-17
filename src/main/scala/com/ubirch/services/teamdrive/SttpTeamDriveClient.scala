package com.ubirch.services.teamdrive

import com.ubirch.services.teamdrive.model._
import monix.eval.Task
import org.json4s.native.Serialization.read
import org.json4s.{ Formats, Serialization }
import sttp.client._
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.json4s._
import sttp.model.MediaType

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import scala.concurrent.Future
import scala.concurrent.duration.Duration

class SttpTeamDriveClient(config: SttpTeamDriveClient.Config)(
  implicit backend: SttpBackend[Future, Nothing, WebSocketHandler],
  serialization: Serialization,
  formats: Formats
) extends TeamDriveClient {

  import SttpTeamDriveClient._

  private val authenticatedRequest: RequestT[Empty, Either[String, String], Nothing] =
    basicRequest.auth.basic(config.username, config.password)
      .readTimeout(config.readTimeout)

  override def createSpace(name: String, path: String): Task[SpaceId] =
    callCreateSpace(name, path).flatMap { r =>
      handleResponse(r) { v => Task.pure(SpaceId(v.spaceId)) }
    }

  private def callCreateSpace(
    name: String,
    path: String
  ): Task[Response[Either[ResponseError[Exception], CreateSpace_OUT]]] =
    Task.fromFuture(
      authenticatedRequest
        .post(uri"${config.url}/api/createSpace")
        .body(CreateSpace_IN(spaceName = name, spacePath = path, disableFileSystem = false, webAccess = true))
        .response(asJson[CreateSpace_OUT])
        .send()
    )

  override def putFile(spaceId: SpaceId, fileName: String, file: ByteBuffer): Task[FileId] =
    callPutFile(spaceId, fileName, file).flatMap { r =>
      handleResponse(r) { v => Task.pure(FileId(v.file.id)) }
    }

  private def callPutFile(
    spaceId: SpaceId,
    fileName: String,
    file: ByteBuffer
  ): Task[Response[Either[ResponseError[Exception], PutFile_OUT]]] =
    Task.fromFuture {
      authenticatedRequest
        .put(uri"${config.url}/files/${spaceId.v}/$fileName")
        .body(file)
        .response(asJson[PutFile_OUT])
        .send()
    }

  override def inviteMember(spaceId: SpaceId, email: String, permissionLevel: PermissionLevel): Task[Boolean] =
    callInviteMember(spaceId, email, permissionLevel).flatMap { r =>
      handleResponse(r) { v => Task.pure(v.result) }
    }

  private def callInviteMember(
    spaceId: SpaceId,
    email: String,
    permissionLevel: PermissionLevel
  ): Task[Response[Either[ResponseError[Exception], InviteMember_OUT]]] =
    Task.fromFuture {
      authenticatedRequest
        .contentType(MediaType.ApplicationJson.charset(StandardCharsets.UTF_8))
        .post(uri"${config.url}/api/inviteMember")
        .body(
          InviteMember_IN(
            spaceId = spaceId.v,
            text = "This is your cert. Enjoy!!",
            permissionLevel = PermissionLevel.toFormattedString(permissionLevel),
            sendEmail = true,
            name = email
          )
        )
        .response(asJson[InviteMember_OUT])
        .send()
    }

  private def handleResponse[T, R](r: Response[Either[ResponseError[Exception], T]])(onSuccess: T => Task[R]): Task[R] =
    r.body match {
      case Left(e) =>
        e match {
          case HttpError(body, _) =>
            Task(read[TeamDriveError_OUT](body)).flatMap(e => Task.raiseError(TeamDriveError(e.error, e.error_message)))
          case a @ DeserializationError(_, _) => Task.raiseError(a)
        }
      case Right(v) => onSuccess(v)
    }
}

object SttpTeamDriveClient {
  case class Config(url: String, username: String, password: String, readTimeout: Duration)

  case class TeamDriveError_OUT(error: Int, error_message: String, result: Boolean, status_code: Int)

  case class CreateSpace_IN(spaceName: String, spacePath: String, disableFileSystem: Boolean, webAccess: Boolean)
  case class CreateSpace_OUT(result: Boolean, spaceId: Int)

  case class PutFile_OUT(file: File, newVersionId: Int, result: Boolean)
  case class File(id: Int, confirmed: Boolean, creator: String, spaceId: Int, permissions: String)

  case class InviteMember_IN(spaceId: Int, text: String, permissionLevel: String, sendEmail: Boolean, name: String)
  case class InviteMember_OUT(result: Boolean)
}
