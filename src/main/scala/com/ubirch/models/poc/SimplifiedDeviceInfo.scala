package com.ubirch.models.poc

case class SimplifiedDeviceInfo(externalId: String, pocName: String, deviceId: DeviceId) {
  def toCSVFormat: String =
    s""""$externalId", "$pocName", "${deviceId.toString}"""".stripMargin
}
