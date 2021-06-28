package com.ubirch.models.poc

import com.ubirch.models.tenant.{ TenantId, TenantName }
import org.scalatest.{ Matchers, WordSpec }

class DeviceIdTest extends WordSpec with Matchers {

  "Creation of DeviceId" should {
    "result in same UUID if provided with same tenantId and externalId" in {
      val deviceId1 = DeviceId(TenantId(TenantName("name")), "1")
      val deviceId2 = DeviceId(TenantId(TenantName("name")), "1")

      deviceId1 shouldBe deviceId2
    }

    "result in different UUID if provided with same tenantId but different externalId" in {
      val deviceId1 = DeviceId(TenantId(TenantName("name")), "1")
      val deviceId2 = DeviceId(TenantId(TenantName("name")), "2")

      deviceId1 shouldNot be(deviceId2)
    }

    "result in different UUID if provided with different tenantId but same externalId" in {
      val deviceId1 = DeviceId(TenantId(TenantName("name1")), "1")
      val deviceId2 = DeviceId(TenantId(TenantName("name2")), "1")

      deviceId1 shouldNot be(deviceId2)
    }
  }

}
