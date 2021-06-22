package com.ubirch.models.poc

import com.ubirch.models.tenant.{ TenantId, TenantName }
import org.scalatest.{ Matchers, WordSpec }

class SimplifiedDeviceInfoTest extends WordSpec with Matchers {

  "SimplifiedDeviceInfo.toCSVFormat" should {
    "produce correctly formatted CSV from object" in {
      val deviceId = DeviceId(TenantId(TenantName("name")), "extId")
      val simplifiedDeviceInfo = SimplifiedDeviceInfo("extId", "PoCName", deviceId)

      simplifiedDeviceInfo.toCSVFormat shouldBe
        s""""extId"; "PoCName"; "${deviceId.toString}"""".stripMargin
    }
  }
}
