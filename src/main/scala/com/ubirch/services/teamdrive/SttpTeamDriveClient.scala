package com.ubirch.services.teamdrive

import com.ubirch.services.teamdrive.model._
import monix.eval.Task
import org.json4s.{ Formats, Serialization }
import sttp.client._
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.json4s._

import java.io.FileInputStream
import scala.concurrent.Future

class SttpTeamDriveClient(config: SttpTeamDriveClient.Config)(
  implicit backend: SttpBackend[Future, Nothing, WebSocketHandler],
  serialization: Serialization,
  formats: Formats
) extends TeamDriveClient {

  import SttpTeamDriveClient._

  private val authenticatedRequest: RequestT[Empty, Either[String, String], Nothing] =
    basicRequest.auth.basic(config.username, config.password)

  override def createSpace(name: String, path: String): Task[CreateSpaceResponse] =
    callCreateSpace(name, path).flatMap { r =>
      r.body match {
        case Left(e)  => Task.raiseError(e)
        case Right(v) => Task.pure(SpaceCreated(v.spaceId))
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

  def getSpaces() = {
    Task.fromFuture(
      authenticatedRequest
        .get(uri"http://127.0.0.1:4040/api/getSpaces")
        .send()
    )
  }

  def putFile() = {
    Task.fromFuture {
      var read: Option[FileInputStream] = None
      var body: Array[Byte] = Array.emptyByteArray
      try {
        read = Some(new FileInputStream(
          "/Users/gzhk/workspace/ubirch-poc-manager/src/main/resources/db/migration/V1_0__test_migration.sql"))
        body = read.get.readAllBytes()
      } catch {
        case e: Throwable => e.printStackTrace()
      } finally {
        read.get.close()
      }

      authenticatedRequest
        .put(uri"http://127.0.0.1:4040/files/2/V1_0__test_migration.sql")
        .body(body)
        .send()
    }
  }

  def getFile() =
    Task.fromFuture(
      authenticatedRequest
        .get(uri"http://127.0.0.1:4040/files/2/V1_0__test_migration.sql")
        .send()
    )

  def getSpaceIds() =
    Task.fromFuture(
      authenticatedRequest
        .get(uri"http://127.0.0.1:4040/api/getSpaceIds")
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

  def getSpacePermissionLevels() =
    Task.fromFuture(
      authenticatedRequest
        .get(uri"http://127.0.0.1:4040/api/getSpacePermissionLevels")
        .send()
    )

  def getServers() =
    Task.fromFuture(
      authenticatedRequest
        .get(uri"http://127.0.0.1:4040/api/getServers")
        .send()
    )
}

object SttpTeamDriveClient {
  case class Config(url: String, username: String, password: String)

  case class CreateSpace_IN(spaceName: String, spacePath: String, disableFileSystem: Boolean, webAccess: Boolean)
  case class CreateSpace_OUT(result: Boolean, spaceId: Int)
}
