package com.ubirch.models.tenant
import com.ubirch.UnitTestBase

class TenantIdTest extends UnitTestBase {

  "TenantID" should {
    "create always same TenantID if provided with same TenantName" in {
      val tenantName = TenantName("name_1")
      val tenantId1 = TenantId(tenantName)
      val tenantId2 = TenantId(tenantName)
      val tenantId3 = TenantId(tenantName)

      tenantId1 shouldBe tenantId2
      tenantId2 shouldBe tenantId3
    }

    "create different TenantIDs if provided with different TenantNames" in {
      val tenantId1 = TenantId(TenantName("name_1"))
      val tenantId2 = TenantId(TenantName("name_2"))

      tenantId1 shouldNot be(tenantId2)
    }
  }

}
