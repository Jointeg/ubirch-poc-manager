package com.ubirch.models.poc
import com.ubirch.UnitTestBase
import com.ubirch.models.tenant.{ TenantId, TenantName }

class SimplifiedDeviceInfoTest extends UnitTestBase {

  "SimplifiedDeviceInfo.toCSVFormat" should {
    "produce correctly formatted CSV from object" in {
      val deviceId = DeviceId(TenantId(TenantName("name")), "extId")
      val simplifiedDeviceInfo = SimplifiedDeviceInfo("extId", "PoCName", deviceId)

      simplifiedDeviceInfo.toCSVFormat shouldBe
        s""""extId"; "PoCName"; "${deviceId.toString}"""".stripMargin
    }
  }
}
