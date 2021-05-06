package com.ubirch.services.poc

import com.google.inject.Inject
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ServicesConfPaths
import com.ubirch.models.poc.{ Poc, PocStatus, StatusAndDeviceInfo }
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

  def createDevice(poc: Poc, status: PocStatus, tenant: Tenant): Task[StatusAndDeviceInfo]
}

class DeviceCreatorImpl @Inject() (conf: Config)(implicit formats: Formats) extends DeviceCreator with LazyLogging {

  implicit private val scheduler: Scheduler = monix.execution.Scheduler.global
  implicit private val backend: SttpBackend[Future, Nothing, WebSocketHandler] = AsyncHttpClientFutureBackend()
  private val thingUrl: String = conf.getString(ServicesConfPaths.THING_API_URL)
  implicit private val serialization: Serialization.type = org.json4s.native.Serialization
  private def deviceDescription(tenant: Tenant, poc: Poc) = s"device of ${tenant.tenantName}'s poc ${poc.pocName}"

  override def createDevice(poc: Poc, status: PocStatus, tenant: Tenant): Task[StatusAndDeviceInfo] = {
    if (status.deviceCreated) {
      throwError(status, "device has been created, but retrieval of password is not implemented yet")
//      Task(StatusAndDeviceInfo(status, None))
    } else {
      Task.fromFuture(requestDeviceCreation(poc, status, tenant))
    }
  }

  private def requestDeviceCreation(poc: Poc, status: PocStatus, tenant: Tenant) = {
    val body = getBody(poc, tenant)

    basicRequest
      .post(uri"$thingUrl")
      .body(write[DeviceRequestBody](body))
      .auth
      .bearer(tenant.deviceCreationToken.value.value.value)
      .response(asJson[Array[Map[String, DeviceResponse]]])
      .send()
      .map {
        _.body match {
          case Right(array: Array[Map[String, DeviceResponse]]) =>
            if (array.length == 1 && array.head.size == 1) {
              val pw = array.head.head._2.apiConfig.password
              StatusAndDeviceInfo(status, pw)
            } else {
              throwError(status, s"unexpected size of thing api response array: ${array.length}; ")
            }
          case Left(ex: ResponseError[Exception]) =>
            throwAndLogError(status, "creating device via Thing API failed; ", ex)
        }
      }
  }
  private def getBody(poc: Poc, tenant: Tenant) = {
    DeviceRequestBody(
      poc.deviceId.toString,
      deviceDescription(tenant, poc),
      List(poc.dataSchemaId, tenant.deviceGroupId.value, poc.roleName)
    )
  }

  private def throwAndLogError(status: PocStatus, msg: String, ex: Throwable): Nothing = {
    logger.error(msg, ex)
    throwError(status, msg)
  }

  def throwError(status: PocStatus, msg: String) =
    throw PocCreationError(status.copy(errorMessages = Some(msg)))

}

case class DeviceRequestBody(
  hwDeviceId: String,
  description: String,
  listGroups: List[String] = Nil,
  attributes: Map[String, List[String]] = Map.empty
)

case class DeviceResponse(state: String, apiConfig: ApiConfig)
case class ApiConfig(password: String, keyService: String, niomon: String, data: String)
