package com.ubirch.services.poc

import com.google.inject.Inject
import com.typesafe.config.Config
import com.ubirch.models.poc.{ Poc, PocStatus }
import monix.eval.Task
import org.json4s.Formats

class InformationProviderMockSuccess @Inject() (conf: Config, certHandler: CertHandler)(implicit formats: Formats)
  extends InformationProviderImpl(conf, certHandler) {

  override def goClientRequest(poc: Poc, statusAndPW: StatusAndPW, body: String): Task[StatusAndPW] = {
    val successState = statusAndPW.pocStatus.copy(goClientProvided = true)
    Task(statusAndPW.copy(successState))
  }

  override def certifyApiRequest(poc: Poc, statusAndPW: StatusAndPW, body: String): Task[PocStatus] =
    Task(statusAndPW.pocStatus.copy(certifyApiProvided = true))
}

class InformationProviderMockWrongURL @Inject() (conf: Config, certHandler: CertHandler)(implicit formats: Formats)
  extends InformationProviderImpl(conf, certHandler) {
  override val goClientURL: String = "urlWithout.schema.com"
  override val certifyApiURL: String = "urlWithout.schema.com"
}
