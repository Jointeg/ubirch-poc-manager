package com.ubirch.services.poc

import com.google.inject.Inject
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ServicesConfPaths
import com.ubirch.models.auth.DecryptedData
import com.ubirch.models.poc.{ Poc, PocStatus }
import com.ubirch.models.tenant.Tenant
import com.ubirch.services.auth.AESEncryption
import com.ubirch.services.execution.SttpResources
import com.ubirch.services.poc.PocCreator._
import monix.eval.Task
import monix.execution.Scheduler
import org.json4s.Formats
import org.json4s.native.Serialization
import org.json4s.native.Serialization.write
import sttp.client.json4s.asJson
import sttp.client.{ basicRequest, UriContext }

trait DeviceCreator {

  def createDevice(poc: Poc, status: PocStatus, tenant: Tenant): Task[StatusAndPW]
}

class DeviceCreatorImpl @Inject() (conf: Config, aESEncryption: AESEncryption)(implicit formats: Formats)
  extends DeviceCreator
  with LazyLogging {

  implicit private val scheduler: Scheduler = monix.execution.Scheduler.global

  private val thingUrlCreateDevice: String = conf.getString(ServicesConfPaths.THING_API_URL_CREATE_DEVICE)
  private val thingUrlGetInfo: String = conf.getString(ServicesConfPaths.THING_API_URL_GET_INFO)
  implicit private val serialization: Serialization.type = org.json4s.native.Serialization
  private def deviceDescription(tenant: Tenant, poc: Poc) = s"device of ${tenant.tenantName}'s poc: ${poc.pocName}"

  @throws[PocCreationError]
  override def createDevice(poc: Poc, status: PocStatus, tenant: Tenant): Task[StatusAndPW] = {
    if (status.deviceCreated) {
      decryptToken(tenant, poc, status)
        .flatMap(token => requestDeviceInfo(token, poc, status))
        .onErrorHandle(ex =>
          throwAndLogError(
            PocAndStatus(poc, status),
            "an error occurred when retrieving api-config from thing api; ",
            ex,
            logger))
    } else {
      val body = getBody(poc, status, tenant)
      decryptToken(tenant, poc, status)
        .flatMap(token => requestDeviceCreation(token, poc, status, body))
        .onErrorHandle(ex =>
          throwAndLogError(
            PocAndStatus(poc, status),
            "an error occurred when creating device via thing api; ",
            ex,
            logger))
    }
  }

  @throws[PocCreationError]
  protected def requestDeviceCreation(
    token: DecryptedData,
    poc: Poc,
    status: PocStatus,
    body: String): Task[StatusAndPW] =
    Task.deferFuture {
      // an error could occur before calls the send() method.
      // In this case, the deferFuture method is needed because the fromFuture method can't catch such an error.
      val request = basicRequest
        .post(uri"$thingUrlCreateDevice")
        .body(body)
        .auth
        .bearer(token.value)
        .response(asJson[Array[Map[String, DeviceResponse]]])
      SttpResources.backend
        .send(request)
        .map {
          _.body match {
            case Right(array: Array[Map[String, DeviceResponse]]) =>
              if (array.length == 1 && array.head.size == 1) {
                val pw = array.head.head._2.apiConfig.password
                StatusAndPW(status.copy(deviceCreated = true), pw)
              } else {
                throwError(
                  PocAndStatus(poc, status),
                  s"unexpected size of thing api response array: ${array.length}; ")
              }
            case Left(ex) =>
              throwAndLogError(PocAndStatus(poc, status), "creating device via Thing API failed: ", ex, logger)
          }
        }
    }

  @throws[PocCreationError]
  protected def requestDeviceInfo(
    token: DecryptedData,
    poc: Poc,
    status: PocStatus): Task[StatusAndPW] = Task.deferFuture {
    val request = basicRequest
      .post(uri"$thingUrlGetInfo?device_id=${poc.getDeviceId}")
      .auth
      .bearer(token.value)
      .response(asJson[Array[Map[String, ApiConfig]]])
    SttpResources.backend.send(request).map {
      _.body match {
        case Right(array: Array[Map[String, ApiConfig]]) =>
          if (array.length == 1 && array.head.size == 1) {
            val pw = array.head.head._2.password
            StatusAndPW(status, pw)
          } else {
            throwError(
              PocAndStatus(poc, status),
              s"unexpected size of thing api response array: ${array.length}; ")
          }
        case Left(ex) =>
          throwAndLogError(PocAndStatus(poc, status), "retrieving api-config via Thing API failed: ", ex, logger)
      }
    }
  }

  @throws[PocCreationError]
  private def getBody(poc: Poc, status: PocStatus, tenant: Tenant): String = {
    if (poc.deviceGroupId.isEmpty)
      throwError(PocAndStatus(poc, status), "poc.deviceGroupId is missing; cannot create device")

    val body = DeviceRequestBody(
      poc.getDeviceId,
      deviceDescription(tenant, poc),
      List()
    )
    write[DeviceRequestBody](body)
  }

  protected def decryptToken(tenant: Tenant, poc: Poc, status: PocStatus): Task[DecryptedData] = {
    if (tenant.deviceCreationToken.isEmpty)
      throwError(PocAndStatus(poc, status), "poc.deviceGroupId is missing; cannot create device")
    aESEncryption
      .decrypt(tenant.deviceCreationToken.get.value)(identity)
  }

}

case class DeviceRequestBody(
  hwDeviceId: String,
  description: String,
  listGroups: List[String] = Nil,
  attributes: Map[String, List[String]] = Map.empty
)

case class DeviceResponse(state: String, apiConfig: ApiConfig)
case class ApiConfig(password: String, keyService: String, niomon: String, data: String)
