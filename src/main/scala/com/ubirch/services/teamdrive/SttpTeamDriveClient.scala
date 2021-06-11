package com.ubirch.services.teamdrive

import cats.data.OptionT
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.services.execution.SttpResources
import com.ubirch.services.teamdrive.model._
import monix.eval.Task
import org.json4s.Formats
import org.json4s.native.Serialization
import org.json4s.native.Serialization.read
import sttp.client._
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.json4s._
import sttp.model.MediaType

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import javax.inject.{ Inject, Singleton }
import scala.concurrent.Future
import scala.concurrent.duration.Duration

/**
  * This class internally calls the TeamDrive Http Api
  * [[https://docs.teamdrive.net/Agent/4.6.11/html/TeamDrive_Http_Api.html#]]
  */
@Singleton
class SttpTeamDriveClient @Inject() (config: TeamDriveClientConfig)(implicit formats: Formats)
  extends TeamDriveClient
  with LazyLogging {

  implicit private val serialization: Serialization.type = org.json4s.native.Serialization
  implicit private val backend: SttpBackend[Future, Nothing, WebSocketHandler] = SttpResources.backend

  import SttpTeamDriveClient._

  private val basicRequestWithTimeout: RequestT[Empty, Either[String, String], Nothing] =
    basicRequest.readTimeout(config.readTimeout)

  private val authenticatedRequest: RequestT[Empty, Either[String, String], Nothing] =
    basicRequestWithTimeout.auth.basic(config.username, config.password)

  override def createSpace(name: SpaceName, path: String): Task[SpaceId] =
    callCreateSpace(name.v, path).flatMap { r =>
      handleResponse(r) { v => Task.pure(SpaceId(v.spaceId)) }
    }

  private def callCreateSpace(
    name: String,
    path: String
  ): Task[Response[Either[ResponseError[Exception], CreateSpace_OUT]]] =
    Task.deferFuture(
      authenticatedRequest
        .post(uri"${config.url}/api/createSpace")
        .body(CreateSpace_IN(spaceName = name, spacePath = path, disableFileSystem = false, webAccess = true))
        .response(asJson[CreateSpace_OUT])
        .send()
    )

  private def handleResponse[T, R](r: Response[Either[ResponseError[Exception], T]])(onSuccess: T => Task[R]): Task[R] =
    r.body match {
      case Left(e) =>
        e match {
          case HttpError(body, _) =>
            Task(read[TeamDriveError_OUT](body))
              .onErrorHandle { ex =>
                logger.error(s"an error occurred parsing error reponse body ($body) by teamdrive", ex)
                throw TeamDriveHttpError(r.code.code, body)
              }
              .flatMap(e => Task.raiseError(TeamDriveHttpError(e.error, e.error_message)))

          case a @ DeserializationError(_, _) => Task.raiseError(a)
        }
      case Right(v) => onSuccess(v)
    }

  override def putFile(spaceId: SpaceId, fileName: String, file: ByteBuffer): Task[FileId] =
    callPutFile(spaceId, fileName, file).flatMap { r =>
      handleResponse(r) { v => Task.pure(FileId(v.file.id)) }
    }

  private def callPutFile(
    spaceId: SpaceId,
    fileName: String,
    file: ByteBuffer
  ): Task[Response[Either[ResponseError[Exception], PutFile_OUT]]] =
    Task.deferFuture {
      authenticatedRequest
        .put(uri"${config.url}/files/${spaceId.v}/$fileName")
        .body(file)
        .response(asJson[PutFile_OUT])
        .send()
    }

  override def inviteMember(
    spaceId: SpaceId,
    email: String,
    welcomeMessage: String,
    permissionLevel: PermissionLevel): Task[Boolean] =
    callInviteMember(spaceId, email, welcomeMessage, permissionLevel).flatMap { r =>
      handleResponse(r) { v => Task.pure(v.result) }
    }

  private def callInviteMember(
    spaceId: SpaceId,
    email: String,
    welcomeMessage: String,
    permissionLevel: PermissionLevel
  ): Task[Response[Either[ResponseError[Exception], InviteMember_OUT]]] =
    Task.deferFuture {
      authenticatedRequest
        .contentType(MediaType.ApplicationJson.charset(StandardCharsets.UTF_8))
        .post(uri"${config.url}/api/inviteMember")
        .body(
          InviteMember_IN(
            spaceId = spaceId.v,
            text = welcomeMessage,
            permissionLevel = PermissionLevel.toFormattedString(permissionLevel),
            sendEmail = true,
            name = email
          )
        )
        .response(asJson[InviteMember_OUT])
        .send()
    }

  override def getSpaceByName(spaceName: SpaceName): Task[Option[Space]] = {
    callGetSpaces().map {
      _.body match {
        case Right(spaces) =>
          val filteredSpaces = spaces.filter(_.name == spaceName.v)
          if (filteredSpaces.isEmpty) {
            logger.warn(s"couldn't find the space name: ${spaceName.v}")
            None
          } else if (filteredSpaces.size > 1) {
            val spaceInfo = filteredSpaces.map { s => s"id: ${s.id}, name: ${s.name}" }
            val errorMsg = s"found duplicate space names: $spaceInfo"
            logger.error(s"found duplicate space names: $spaceInfo")
            throw TeamDriveError(errorMsg)
          } else {
            Some(filteredSpaces.head)
          }
        case Left(ex) =>
          val errorMsg = s"couldn't retrieve space by ${spaceName.v}, error: $ex"
          logger.error(errorMsg)
          throw TeamDriveError(errorMsg)
      }
    }
  }

  override def getSpaceByNameWithActivation(spaceName: SpaceName): Task[Option[Space]] = {
    (for {
      space <- OptionT(getSpaceByName(spaceName))
      _ <-
        if (space.status == Active) {
          OptionT.liftF[Task, Space] {
            activateSpace(SpaceId(space.id)).map(_ => space)
          }
        } else OptionT.some[Task](space)
    } yield {
      space
    }).value
  }

  private def callGetSpaces(): Task[Response[Either[ResponseError[Exception], Seq[Space]]]] = {
    Task.deferFuture {
      authenticatedRequest
        .get(uri"${config.url}/api/getSpaces")
        .response(asJson[Seq[Space]])
        .send()
    }
  }

  override def getLoginInformation(): Task[LoginInformation] =
    callGetLoginInformation().flatMap { r =>
      handleResponse(r)(li => Task.pure(li))
    }

  private def callGetLoginInformation(): Task[Response[Either[ResponseError[Exception], LoginInformation]]] =
    Task.deferFuture {
      basicRequestWithTimeout
        .contentType(MediaType.ApplicationJson.charset(StandardCharsets.UTF_8))
        .get(uri"${config.url}/getLoginInformation")
        .response(asJson[LoginInformation])
        .send()
    }

  override def login(): Task[Unit] =
    callLogin().flatMap { r =>
      handleResponse(r)(_ => Task.unit)
    }

  override def withLogin[T](mainTask: => Task[T]): Task[T] = for {
    loginInformation <- getLoginInformation()
    _ <- Task(logger.debug(s"TeamDrive agent requires login: ${loginInformation.isLoginRequired}"))
    _ <- loginInformation match {
      case LoginInformation(isLoginRequired) if isLoginRequired => login()
      case _                                                    => Task.unit
    }
    result <- mainTask
  } yield {
    result
  }

  private def callLogin(): Task[Response[Either[ResponseError[Exception], LoginInformation]]] =
    Task.deferFuture {
      basicRequestWithTimeout
        .contentType(MediaType.ApplicationJson.charset(StandardCharsets.UTF_8))
        .post(uri"${config.url}/login")
        .body(Login_IN(config.username, config.password))
        .response(asJson[LoginInformation])
        .send()
    }

  override def activateSpace(spaceId: SpaceId): Task[Unit] =
    callJoinSpace(spaceId).map {
      _.body match {
        case Right(response: String) =>
          logger.debug(s"activate the space: ${spaceId.v}. ${response}")
        case Left(ex) =>
          throw TeamDriveError(ex)
      }
    }

  private def callJoinSpace(spaceId: SpaceId): Task[Response[Either[String, String]]] =
    Task.deferFuture {
      basicRequestWithTimeout
        .contentType(MediaType.ApplicationJson.charset(StandardCharsets.UTF_8))
        .get(uri"${config.url}/api/joinSpace")
        .body(JoinSpace_IN(spaceId.v.toString))
        .response(asString)
        .send()
    }
}

object SttpTeamDriveClient {
  case class Config(url: String, username: String, password: String, readTimeout: Duration)
    extends TeamDriveClientConfig

  case class TeamDriveError_OUT(error: Int, error_message: String, result: Boolean, status_code: Int)

  case class CreateSpace_IN(spaceName: String, spacePath: String, disableFileSystem: Boolean, webAccess: Boolean)
  case class CreateSpace_OUT(result: Boolean, spaceId: Int)

  case class PutFile_OUT(file: File, newVersionId: Int, result: Boolean)
  case class File(id: Int, confirmed: Boolean, creator: String, spaceId: Int, permissions: String)

  case class InviteMember_IN(spaceId: Int, text: String, permissionLevel: String, sendEmail: Boolean, name: String)
  case class InviteMember_OUT(result: Boolean)

  case class Login_IN(username: String, password: String)

  case class JoinSpace_IN(id: String)
  case class JoinSpace_OUT(result: Boolean)

  // these are some of fields from the getSpace endpoint
  case class Space(
    id: Int,
    name: String,
    status: String,
    permissionLevel: String,
    webAccessAllowed: Boolean)
}
