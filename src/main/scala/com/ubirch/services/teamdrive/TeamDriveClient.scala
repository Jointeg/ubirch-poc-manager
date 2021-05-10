package com.ubirch.services.teamdrive

import com.ubirch.models.keycloak.user.KeycloakUser
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import org.json4s.native.Serialization
import sttp.client._
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.json4s.asJson

import scala.concurrent.Future
import scala.concurrent.duration._

trait TeamDriveClient {}

class SttpTeamDriveClient extends TeamDriveClient {

  implicit private val backend: SttpBackend[Future, Nothing, WebSocketHandler] = AsyncHttpClientFutureBackend()
  implicit private val serialization: Serialization.type = org.json4s.native.Serialization

  def asd() = {
    Task.fromFuture(
      basicRequest
        .get(uri"http://127.0.0.1:4040/files/getSpaceByName?spaceName=test")
        .send()
    ).runSyncUnsafe(5.seconds)
  }

}
