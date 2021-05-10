package com.ubirch.services.poc

import com.google.inject.Inject
import com.typesafe.config.Config
import com.ubirch.models.poc.PocStatus
import org.json4s.Formats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class InformationProviderMockSuccess @Inject() (conf: Config)(implicit formats: Formats)
  extends InformationProviderImpl(conf) {

  override def goClientRequest(statusAndPW: StatusAndPW, body: String): Future[StatusAndPW] = {
    val successState = statusAndPW.pocStatus.copy(goClientProvided = true)
    Future(statusAndPW.copy(successState))
  }

  override def certifyApiRequest(statusAndPW: StatusAndPW, body: String): Future[PocStatus] =
    Future(statusAndPW.pocStatus.copy(certifyApiProvided = true))
}

class InformationProviderMockWrongURL @Inject() (conf: Config)(implicit formats: Formats)
  extends InformationProviderImpl(conf) {
  override val goClientURL: String = "urlWithout.schema.com"
  override val certifyApiURL: String = "urlWithout.schema.com"

}
