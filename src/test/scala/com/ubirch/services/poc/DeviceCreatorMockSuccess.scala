package com.ubirch.services.poc
import com.google.inject.Inject
import com.typesafe.config.Config
import com.ubirch.models.poc.PocStatus
import com.ubirch.models.tenant.Tenant
import org.json4s.Formats

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DeviceCreatorMockSuccess @Inject() (conf: Config)(implicit formats: Formats) extends DeviceCreatorImpl(conf) {

  override protected def requestDeviceCreation(
    status: PocStatus,
    tenant: Tenant,
    body: String): Future[StatusAndPW] = {
    Future(StatusAndPW(status.copy(deviceCreated = true), UUID.randomUUID().toString))
  }
}
