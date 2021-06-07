package com.ubirch.formats

import com.ubirch.UnitTestBase
import com.ubirch.models.tenant
import com.ubirch.models.tenant._
import org.json4s._
import org.json4s.native.JsonMethods._

class CreateTenantRequestFormatTest extends UnitTestBase {

  "CreateTenantRequest" should {

    "Parse if all mandatory fields are provided" in {
      withInjector { injector =>
        implicit val formats: Formats = injector.get[Formats]
        val createTenantRequestJSON = parse(
          s"""
             |{
             |    "tenantName": "someRandomName",
             |    "usageType": "API",
             |    "tenantType": "${TenantType.UBIRCH_STRING}"
             |}
             |""".stripMargin)

        createTenantRequestJSON.extract[CreateTenantRequest] shouldBe CreateTenantRequest(
          TenantName("someRandomName"),
          API,
          tenant.UBIRCH
        )
      }
    }

    "Parse if all fields are provided" in {
      withInjector { injector =>
        implicit val formats: Formats = injector.get[Formats]
        val createTenantRequestJSON = parse(
          s"""
             |{
             |    "tenantName": "someRandomName",
             |    "usageType": "API",
             |    "tenantType": "${TenantType.BMG_STRING}"
             |}
             |""".stripMargin)

        createTenantRequestJSON.extract[CreateTenantRequest] shouldBe CreateTenantRequest(
          TenantName("someRandomName"),
          API,
          BMG
        )
      }
    }

    "Fail to parse JSON if no fields are provided at all" in {
      withInjector { injector =>
        implicit val formats: Formats = injector.get[Formats]
        val createTenantRequestJSON = parse("")

        createTenantRequestJSON.extractOpt[CreateTenantRequest] shouldBe None
      }
    }

    "Fail to parse JSON if one of required values is not provided" in {
      withInjector { injector =>
        implicit val formats: Formats = injector.get[Formats]
        val createTenantRequestJSON = parse(
          """
            |{
            |    "tenantName": "someRandomName",
            |    "usageType": "API",
            |}
            |""".stripMargin)

        createTenantRequestJSON.extractOpt[CreateTenantRequest] shouldBe None
      }
    }

    "Fail to parse JSON if unknown value is provided for tenant type" in {
      withInjector { injector =>
        implicit val formats: Formats = injector.get[Formats]
        val createTenantRequestJSON = parse(
          s"""
             |{
             |    "tenantName": "someRandomName",
             |    "usageType": "API",
             |    "tenantType": "bmgX"
             |}
             |""".stripMargin)

        createTenantRequestJSON.extractOpt[CreateTenantRequest] shouldBe None
      }
    }
  }

  "UsageType" should {
    "Parse known values" in {
      withInjector { injector =>
        implicit val formats: Formats = injector.get[Formats]
        JString("API").extract[UsageType] shouldBe API
        JString("APP").extract[UsageType] shouldBe APP
        JString("BOTH").extract[UsageType] shouldBe Both
      }
    }

    "Fail if provided value is not known" in {
      withInjector { injector =>
        implicit val formats: Formats = injector.get[Formats]
        JString("NotAnUsage").extractOpt[UsageType] shouldBe None
      }
    }
  }

  "TenantType" should {
    "Parse known values" in {
      withInjector { injector =>
        implicit val formats: Formats = injector.get[Formats]
        JString("ubirch").extract[TenantType] shouldBe UBIRCH
        JString("bmg").extract[TenantType] shouldBe BMG
      }
    }

    "Fail if provided value is not known" in {
      withInjector { injector =>
        implicit val formats: Formats = injector.get[Formats]
        JString("unknown_value").extractOpt[TenantType] shouldBe None
      }
    }
  }

}
