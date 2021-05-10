package com.ubirch.services.poc
import com.google.inject.Inject
import com.typesafe.config.Config
import com.ubirch.models.auth.DecryptedData
import com.ubirch.models.poc.PocStatus
import com.ubirch.models.tenant.Tenant
import com.ubirch.services.auth.AESEncryption
import monix.eval.Task
import org.json4s.Formats

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DeviceCreatorMockSuccess @Inject() (conf: Config, aESEncryption: AESEncryption)(implicit formats: Formats)
  extends DeviceCreatorImpl(conf, aESEncryption) {

  override protected def requestDeviceCreation(
    token: DecryptedData,
    status: PocStatus,
    body: String): Future[StatusAndPW] = {
    Future(StatusAndPW(status.copy(deviceCreated = true), UUID.randomUUID().toString))
  }

  override protected def decryptToken(tenant: Tenant): Task[DecryptedData] =
    Task(DecryptedData(tenant.deviceCreationToken.value.value.value))

}
