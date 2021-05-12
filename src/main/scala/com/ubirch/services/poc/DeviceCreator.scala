package com.ubirch.services.poc

import com.google.inject.Inject
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ServicesConfPaths
import com.ubirch.models.auth.DecryptedData
import com.ubirch.models.poc.{ Poc, PocStatus }
import com.ubirch.models.tenant.Tenant
import com.ubirch.services.auth.AESEncryption
import monix.eval.Task
import monix.execution.Scheduler
import org.json4s.Formats
import org.json4s.native.Serialization
import org.json4s.native.Serialization.write
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.json4s.asJson
import sttp.client.{ basicRequest, ResponseError, SttpBackend, UriContext }

import scala.concurrent.Future

trait DeviceCreator {

  def createDevice(poc: Poc, status: PocStatus, tenant: Tenant): Task[StatusAndPW]
}

class DeviceCreatorImpl @Inject() (conf: Config, aESEncryption: AESEncryption)(implicit formats: Formats)
  extends DeviceCreator
  with LazyLogging {

  implicit private val scheduler: Scheduler = monix.execution.Scheduler.global
  implicit private val backend: SttpBackend[Future, Nothing, WebSocketHandler] = AsyncHttpClientFutureBackend()
  private val thingUrlCreateDevice: String = conf.getString(ServicesConfPaths.THING_API_URL_CREATE_DEVICE)
  private val thingUrlGetInfo: String = conf.getString(ServicesConfPaths.THING_API_URL_GET_INFO)
  implicit private val serialization: Serialization.type = org.json4s.native.Serialization
  private def deviceDescription(tenant: Tenant, poc: Poc) = s"device of ${tenant.tenantName}'s poc ${poc.pocName}"

  @throws[PocCreationError]
  override def createDevice(poc: Poc, status: PocStatus, tenant: Tenant): Task[StatusAndPW] = {
    if (status.deviceCreated) {
      decryptToken(tenant)
        .map(token => Task.fromFuture(requestDeviceInfo(token, poc, status)))
        .flatten
        .onErrorHandle(ex => throwAndLogError(status, "an error occurred when creating device via thing api; ", ex))
    } else {
      val body = getBody(poc, status, tenant)
      decryptToken(tenant)
        .map(token => Task.fromFuture(requestDeviceCreation(token, status, body)))
        .flatten
        .onErrorHandle(ex => throwAndLogError(status, "an error occurred when creating device via thing api; ", ex))
    }
  }

  protected def requestDeviceCreation(
    token: DecryptedData,
    status: PocStatus,
    body: String): Future[StatusAndPW] = {

    Future(
      basicRequest
        .post(uri"$thingUrlCreateDevice")
        .body(body)
        .auth
        .bearer(token.value)
        .response(asJson[Array[Map[String, DeviceResponse]]])
        .send()
        .map {
          _.body match {
            case Right(array: Array[Map[String, DeviceResponse]]) =>
              if (array.length == 1 && array.head.size == 1) {
                val pw = array.head.head._2.apiConfig.password
                StatusAndPW(status.copy(deviceCreated = true), pw)
              } else {
                throwError(status, s"unexpected size of thing api response array: ${array.length}; ")
              }
            case Left(ex: ResponseError[Exception]) =>
              throwAndLogError(status, "creating device via Thing API failed: ", ex)
          }
        }).flatten
  }

  protected def requestDeviceInfo(
    token: DecryptedData,
    poc: Poc,
    status: PocStatus): Future[StatusAndPW] = {

    Future(
      basicRequest
        .get(uri"$thingUrlGetInfo/${poc.deviceId}")
        .auth
        .bearer(token.value)
        .response(asJson[Array[Map[String, DeviceResponse]]])
        .send()
        .map {
          _.body match {
            case Right(array: Array[Map[String, DeviceResponse]]) =>
              if (array.length == 1 && array.head.size == 1) {
                val pw = array.head.head._2.apiConfig.password
                StatusAndPW(status, pw)
              } else {
                throwError(status, s"unexpected size of thing api response array: ${array.length}; ")
              }
            case Left(ex: ResponseError[Exception]) =>
              throwAndLogError(status, "retrieving api-config via Thing API failed: ", ex)
          }
        }).flatten
  }

  private def getBody(poc: Poc, status: PocStatus, tenant: Tenant): String = {
    if (poc.deviceGroupId.isEmpty)
      throwError(status, "poc.deviceGroupId is missing; cannot create device")

    val body = DeviceRequestBody(
      poc.getDeviceId,
      deviceDescription(tenant, poc),
      List(poc.dataSchemaId, tenant.deviceGroupId.value, poc.deviceGroupId.get)
    )
    write[DeviceRequestBody](body)
  }

  protected def decryptToken(tenant: Tenant): Task[DecryptedData] =
    aESEncryption
      .decrypt(tenant.deviceCreationToken.value)(identity)

  @throws[PocCreationError]
  private def throwAndLogError(status: PocStatus, msg: String, ex: Throwable): Nothing = {
    logger.error(msg, ex)
    throwError(status, msg)
  }

  @throws[PocCreationError]
  def throwError(status: PocStatus, msg: String) =
    throw PocCreationError(status.copy(errorMessage = Some(msg)))

}

case class DeviceRequestBody(
  hwDeviceId: String,
  description: String,
  listGroups: List[String] = Nil,
  attributes: Map[String, List[String]] = Map.empty
)

case class DeviceResponse(state: String, apiConfig: ApiConfig)
case class ApiConfig(password: String, keyService: String, niomon: String, data: String)
