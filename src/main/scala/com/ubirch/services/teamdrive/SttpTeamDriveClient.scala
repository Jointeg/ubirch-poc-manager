package com.ubirch.services.teamdrive

import com.ubirch.services.teamdrive.model._
import monix.eval.Task
import org.json4s.native.Serialization.read
import org.json4s.{ Formats, Serialization }
import sttp.client._
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.json4s._

import java.nio.ByteBuffer
import java.time.Instant
import scala.concurrent.Future

class SttpTeamDriveClient(config: SttpTeamDriveClient.Config)(
  implicit backend: SttpBackend[Future, Nothing, WebSocketHandler],
  serialization: Serialization,
  formats: Formats
) extends TeamDriveClient {

  import SttpTeamDriveClient._

  private val authenticatedRequest: RequestT[Empty, Either[String, String], Nothing] =
    basicRequest.auth.basic(config.username, config.password)

  override def createSpace(name: String, path: String): Task[SpaceId] =
    callCreateSpace(name, path).flatMap { r =>
      r.body match {
        case Left(e) =>
          e match {
            case HttpError(body, _) =>
              Task(read[TeamDriveError_OUT](body)).flatMap(e =>
                Task.raiseError(TeamDriveError(e.error, e.error_message)))
            case a @ DeserializationError(_, _) => Task.raiseError(a)
          }
        case Right(v) => Task.pure(SpaceId(v.spaceId))
      }
    }

  override def putFile(spaceId: SpaceId, fileName: String, file: ByteBuffer): Task[FileId] =
    callPutFile(spaceId, fileName, file).flatMap { r =>
      r.body match {
        case Left(e) =>
          e match {
            case HttpError(body, _) =>
              Task(read[TeamDriveError_OUT](body)).flatMap(e =>
                Task.raiseError(TeamDriveError(e.error, e.error_message)))
            case a @ DeserializationError(_, _) => Task.raiseError(a)
          }
        case Right(v) => Task.pure(FileId(v.file.id))
      }
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

  def getFile() =
    Task.fromFuture(
      authenticatedRequest
        .get(uri"http://127.0.0.1:4040/files/2/V1_0__test_migration.sql")
        .send()
    )

  def inviteMember() =
    Task.fromFuture {
      authenticatedRequest
        .header("Content-Type", "application/json;charset=UTF-8")
        .post(uri"http://127.0.0.1:4040/api/inviteMember")
        .body(
          Map(
            "spaceId" -> "2",
            "text" -> "This is your cert. Enjoy!!",
            "permissionLevel" -> "read",
            "sendEmail" -> "true",
            "name" -> "email"
          )
        )
        .send()
    }
}

object SttpTeamDriveClient {
  case class Config(url: String, username: String, password: String)

  case class TeamDriveError_OUT(error: Int, error_message: String, result: Boolean, status_code: Int)

  case class CreateSpace_IN(spaceName: String, spacePath: String, disableFileSystem: Boolean, webAccess: Boolean)
  case class CreateSpace_OUT(result: Boolean, spaceId: Int)

  case class PutFile_OUT(file: File, newVersionId: Int, result: Boolean)
  case class File(id: Int, confirmed: Boolean, creator: String, spaceId: Int, permissions: String)
}
