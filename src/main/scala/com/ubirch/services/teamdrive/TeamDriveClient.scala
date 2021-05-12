package com.ubirch.services.teamdrive

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import org.json4s.Serialization
import sttp.client._
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend

import java.io.FileInputStream
import scala.concurrent.Future
import scala.concurrent.duration._

trait TeamDriveClient {

}

class SttpTeamDriveClient extends TeamDriveClient {

  implicit private val backend: SttpBackend[Future, Nothing, WebSocketHandler] = AsyncHttpClientFutureBackend()
  implicit private val serialization: Serialization = org.json4s.native.Serialization

  private val authenticatedRequest: RequestT[Empty, Either[String, String], Nothing] =
    basicRequest.auth.basic("username", "password")

  def createSpace() = {
    Task.fromFuture(
      authenticatedRequest
        .post(uri"http://127.0.0.1:4040/api/createSpace")
        .body(
          Map(
            "spaceName" -> "ubrich-test-space-name",
            "spacePath" -> "ubrich-test-space-name/123",
            "disableFileSystem" -> "false",
            "webAccess" -> "true"
          )
        )
        .send()
    ).runSyncUnsafe(5.seconds)
  }

  def getSpaces() = {
    Task.fromFuture(
      authenticatedRequest
        .get(uri"http://127.0.0.1:4040/api/getSpaces")
        .send()
    ).runSyncUnsafe(5.seconds)
  }

  def putFile() = {
    Task.fromFuture {
      var read: Option[FileInputStream] = None
      var body: Array[Byte] = Array.emptyByteArray
      try {
        read = Some(new FileInputStream("/Users/gzhk/workspace/ubirch-poc-manager/src/main/resources/db/migration/V1_0__test_migration.sql"))
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
    }.runSyncUnsafe(5.seconds)
  }

  def getFile() =
    Task.fromFuture(
      authenticatedRequest
        .get(uri"http://127.0.0.1:4040/files/2/V1_0__test_migration.sql")
        .send()
    ).runSyncUnsafe(5.seconds)

  def getSpaceIds() =
    Task.fromFuture(
      authenticatedRequest
        .get(uri"http://127.0.0.1:4040/api/getSpaceIds")
        .send()
    ).runSyncUnsafe(5.seconds)

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
    }.runSyncUnsafe(5.seconds)

  def getSpacePermissionLevels() =
    Task.fromFuture(
      authenticatedRequest
        .get(uri"http://127.0.0.1:4040/api/getSpacePermissionLevels")
        .send()
    ).runSyncUnsafe(5.seconds)

  def getServers() =
    Task.fromFuture(
      authenticatedRequest
        .get(uri"http://127.0.0.1:4040/api/getServers")
        .send()
    ).runSyncUnsafe(5.seconds)
}
