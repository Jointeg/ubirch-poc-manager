package com.ubirch.services.poc
import com.google.inject.Inject
import com.typesafe.config.Config
import com.ubirch.models.poc.PocStatus
import org.json4s.Formats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TestInformationProviderImpl @Inject() (conf: Config)(implicit formats: Formats)
  extends InformationProviderImpl(conf) {

  override protected def goClientRequest(statusAndPW: StatusAndPW, body: String): Future[StatusAndPW] = {
    val status = statusAndPW.pocStatus
    Future(statusAndPW.copy(pocStatus = status.copy(goClientProvided = true)))
  }

  override protected def certApiRequest(statusAndPW: StatusAndPW, body: String): Future[PocStatus] = {
    val status = statusAndPW.pocStatus
    Future(status.copy(certApiProvided = true))
  }

}
