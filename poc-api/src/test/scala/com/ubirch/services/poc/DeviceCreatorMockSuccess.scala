package com.ubirch.services.poc

import com.google.inject.Inject
import com.typesafe.config.Config
import com.ubirch.models.auth.DecryptedData
import com.ubirch.models.poc.{ Poc, PocStatus }
import com.ubirch.models.tenant.Tenant
import com.ubirch.services.auth.AESEncryption
import monix.eval.Task
import org.json4s.Formats

import java.util.UUID

class DeviceCreatorMockSuccess @Inject() (conf: Config, aESEncryption: AESEncryption)(implicit formats: Formats)
  extends DeviceCreatorImpl(conf, aESEncryption) {

  override protected def requestDeviceCreation(
    token: DecryptedData,
    poc: Poc,
    status: PocStatus,
    body: String): Task[StatusAndPW] = {
    Task(StatusAndPW(status.copy(deviceCreated = true), UUID.randomUUID().toString))
  }

  override protected def requestDeviceInfo(token: DecryptedData, poc: Poc, status: PocStatus): Task[StatusAndPW] = {
    Task(StatusAndPW(status, UUID.randomUUID().toString))
  }

  override protected def decryptToken(tenant: Tenant, poc: Poc, status: PocStatus): Task[DecryptedData] =
    Task(DecryptedData(tenant.deviceCreationToken.get.value.value.value))

}
