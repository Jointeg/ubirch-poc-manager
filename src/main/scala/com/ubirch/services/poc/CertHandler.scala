package com.ubirch.services.poc

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ServicesConfPaths
import com.ubirch.models.auth.CertIdentifier
import com.ubirch.models.poc.{ Poc, PocStatus }
import monix.eval.Task
import org.json4s.Formats
import org.json4s.native.Serialization.write
import sttp.client._
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.model.StatusCode

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.Future

trait CertHandler {

  def createOrganisationalUnitCertificate(
    orgUUID: UUID,
    orgUnitId: UUID,
    identifier: CertIdentifier): Task[Unit]

  def createCert(poc: Poc, status: PocStatus): Task[PocStatus]

  def provideCert(poc: Poc, status: PocStatus): Task[PocStatus]
}

class CertCreatorImpl @Inject() (conf: Config)(implicit formats: Formats) extends CertHandler with LazyLogging {

  private val certManagerUrl: String = conf.getString(ServicesConfPaths.CERT_MANAGER_URL)
  implicit private val backend: SttpBackend[Future, Nothing, WebSocketHandler] = AsyncHttpClientFutureBackend()

  override def createCert(poc: Poc, status: PocStatus): Task[PocStatus] = {
    status.clientCertCreated match {
      case Some(false) =>
        Task(status)
      // create client cert, update state and return
      case _ => Task(status)
    }
  }

  override def provideCert(poc: Poc, status: PocStatus): Task[PocStatus] = {
    status.clientCertProvided match {
      case Some(false) =>
        Task(status)
      // provide client cert, update state and return
      case _ => Task(status)
    }
  }
  override def createOrganisationalUnitCertificate(
    orgUUID: UUID,
    orgUnitId: UUID,
    identifier: CertIdentifier): Task[Unit] = {
    val response = Task.fromFuture(basicRequest
      .post(uri"$certManagerUrl/orgs/${orgUUID.toString}/units/${orgUnitId.toString}")
      .body(write[CreateOrganisationalUnitCertRequest](CreateOrganisationalUnitCertRequest(identifier.value)))
      .response(ignore)
      .send())

    response.flatMap(response =>
      if (response.code == StatusCode.Created)
        Task.unit
      else
        logAndThrow(
          s"Could not create organisational unit certificate with orgUnitId: $orgUnitId because received ${response.code} from CertManager"))
  }

  private def logAndThrow[A](msg: String): Task[A] = {
    logger.error(msg)
    Task.raiseError(CertificateCreationError)
  }

  case class CreateOrganisationalUnitCertRequest(identifier: String)
}

case object CertificateCreationError extends Throwable
