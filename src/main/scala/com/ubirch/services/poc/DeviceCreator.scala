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
import PocCreator._

import scala.concurrent.Future

trait DeviceCreator {

  def createDevice(poc: Poc, status: PocStatus, tenant: Tenant): Task[StatusAndPW]
}

class DeviceCreatorImpl @Inject() (conf: Config, aESEncryption: AESEncryption)(implicit formats: Formats)
  extends DeviceCreator
  with LazyLogging {

  implicit private val scheduler: Scheduler = monix.execution.Scheduler.global
  implicit private val backend: SttpBackend[Future, Nothing, WebSocketHandler] = AsyncHttpClientFutureBackend()
  private val thingUrl: String = conf.getString(ServicesConfPaths.THING_API_URL)
  implicit private val serialization: Serialization.type = org.json4s.native.Serialization
  private def deviceDescription(tenant: Tenant, poc: Poc) = s"device of ${tenant.tenantName}'s poc ${poc.pocName}"

  @throws[PocCreationError]
  override def createDevice(poc: Poc, status: PocStatus, tenant: Tenant): Task[StatusAndPW] = {
    if (status.deviceCreated) {
      throwError(PocAndStatus(poc, status), "device has been created, but retrieval of password is not implemented yet")
    } else {
      val body = getBody(poc, tenant)
      decryptToken(tenant)
        .flatMap(token => requestDeviceCreation(token, poc, status, body))
        .onErrorHandle(ex =>
          throwAndLogError(
            PocAndStatus(poc, status),
            "an error occurred when creating device via thing api; ",
            ex,
            logger))
    }
  }

  protected def requestDeviceCreation(
    token: DecryptedData,
    poc: Poc,
    status: PocStatus,
    body: String): Task[StatusAndPW] = Task.deferFuture {
    // an error could occur before calls the send() method.
    // In this case, the deferFuture method is needed because the fromFuture method can't catch such an error.
    basicRequest
      .post(uri"$thingUrl")
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
              throwError(PocAndStatus(poc, status), s"unexpected size of thing api response array: ${array.length}; ")
            }
          case Left(ex: ResponseError[Exception]) =>
            throwAndLogError(PocAndStatus(poc, status), "creating device via Thing API failed: ", ex, logger)
        }
      }
  }

  private def getBody(poc: Poc, tenant: Tenant): String = {
    val body = DeviceRequestBody(
      poc.getDeviceId,
      deviceDescription(tenant, poc),
      List(poc.dataSchemaId, tenant.deviceGroupId.value, poc.roleName)
    )
    write[DeviceRequestBody](body)
  }

  protected def decryptToken(tenant: Tenant): Task[DecryptedData] =
    aESEncryption
      .decrypt(tenant.deviceCreationToken.value)(identity)
}

case class DeviceRequestBody(
  hwDeviceId: String,
  description: String,
  listGroups: List[String] = Nil,
  attributes: Map[String, List[String]] = Map.empty
)

case class DeviceResponse(state: String, apiConfig: ApiConfig)
case class ApiConfig(password: String, keyService: String, niomon: String, data: String)
