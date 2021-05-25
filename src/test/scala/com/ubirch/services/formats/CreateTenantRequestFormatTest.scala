package com.ubirch.services.formats

import com.ubirch.UnitTestBase
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
             |    "sharedAuthCertRequired": false
             |}
             |""".stripMargin)

        createTenantRequestJSON.extract[CreateTenantRequest] shouldBe CreateTenantRequest(
          TenantName("someRandomName"),
          API,
          sharedAuthCertRequired = false
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
             |    "sharedAuthCertRequired": true,
             |}
             |""".stripMargin)

        createTenantRequestJSON.extract[CreateTenantRequest] shouldBe CreateTenantRequest(
          TenantName("someRandomName"),
          API,
          sharedAuthCertRequired = true
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

}
