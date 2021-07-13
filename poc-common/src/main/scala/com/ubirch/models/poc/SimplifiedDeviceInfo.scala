package com.ubirch.models.poc

import com.ubirch.models.poc.SimplifiedDeviceInfo.formulaExecutionSigns

case class SimplifiedDeviceInfo(externalId: String, pocName: String, deviceId: DeviceId) {

  val externalIdSanitized: String = if (formulaExecutionSigns.contains(externalId.head)) s"'$externalId" else externalId
  val pocNameSanitized: String = if (formulaExecutionSigns.contains(pocName.head)) s"'$pocName" else pocName
  val deviceIdSanitized: String =
    if (formulaExecutionSigns.contains(deviceId.toString.head)) s"'$deviceId" else deviceId.toString

  def toCSVFormat: String =
    s""""$externalIdSanitized"; "$pocNameSanitized"; "$deviceIdSanitized"""".stripMargin
}

object SimplifiedDeviceInfo {
  val formulaExecutionSigns = Seq('=', '+', '-', '@')
}
