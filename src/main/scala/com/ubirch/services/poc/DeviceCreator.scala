package com.ubirch.services.poc

import com.google.inject.Inject
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ServicesConfPaths
import com.ubirch.models.poc.{ Poc, PocStatus }
import com.ubirch.models.tenant.Tenant
import monix.eval.Task
import monix.execution.Scheduler
import org.json4s.Formats
import org.json4s.native.Serialization
import org.json4s.native.Serialization.write
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.json4s.asJson
import sttp.client.{ basicRequest, ResponseError, SttpBackend, UriContext }

import java.util.UUID
import scala.concurrent.Future

trait DeviceCreator {

  def createDevice(poc: Poc, status: PocStatus, tenant: Tenant): Task[PocStatus]
}

class DeviceCreatorImpl @Inject() (conf: Config)(implicit formats: Formats) extends DeviceCreator with LazyLogging {

  implicit private val scheduler: Scheduler = monix.execution.Scheduler.global
  implicit private val backend: SttpBackend[Future, Nothing, WebSocketHandler] = AsyncHttpClientFutureBackend()
  private val thingUrl: String = conf.getString(ServicesConfPaths.THING_API_URL)
  implicit private val serialization: Serialization.type = org.json4s.native.Serialization

  //  private val thingApiURL: URI =
  //    try {
  //      val url = conf.getString(THING_API_URL)
  //      new URI(url)
  //    } catch {
  //      case ex: Throwable =>
  //        logger.error(s"couldn't parse thingApiURL ${conf.getString(THING_API_URL)}", ex)
  //        throw ex
  //    }

  override def createDevice(poc: Poc, status: PocStatus, tenant: Tenant): Task[PocStatus] = {
    val body =
      DeviceRequestBody(
        poc.deviceId.toString,
        s"device of ${tenant.tenantName}'s poc ${poc.pocName}",
        List(poc.dataSchemaId, tenant.groupId.value, poc.roleName)
      )

    val response =
      basicRequest
        .post(uri"$thingUrl")
        .body(write[DeviceRequestBody](body))
        .auth
        .bearer(tenant.deviceCreationToken.value.value.value)
        .response(asJson[DeviceRequestResponse])
        .send()
        .map {
          _.body match {
            case Right(response: DeviceRequestResponse) =>
              if (response.details.state == "Ok")
                Right(response.details.apiConfig)
              else
                Left(s"state of DeviceCreationResponse from Thing APP is not ok, but ${response.details.state}")
            case Left(error: ResponseError[Exception]) =>
              val errorMsg = "something went wrong creating device via Thing API; "
              logger.error(errorMsg, error)
              Left(errorMsg + error.getMessage)
          }
        }
    Task.fromFuture {
      response
    }
    Task(status)
  }
}

case class DeviceRequestBody(
  hwDeviceId: String,
  description: String,
  listGroups: List[String] = Nil,
  attributes: Map[String, List[String]] = Map.empty
)

case class DeviceRequestResponse(id: String, details: ResponseDetails)

case class ResponseDetails(state: String, apiConfig: ApiConfig)

case class ApiConfig(password: UUID, keyService: String, niomon: String, data: String)
