package com.ubirch.services.formats

import com.ubirch.UnitTestBase
import com.ubirch.models.tenant._
import org.json4s._
import org.json4s.native.JsonMethods._

class CreateTenantRequestFormatTest extends UnitTestBase {

  "CreateTenantRequest" should {
    "Parse if all required fields are provided" in {
      withInjector { injector =>
        implicit val formats: Formats = injector.get[Formats]
        val createTenantRequestJSON = parse("""
          |{
          |    "tenantName": "someRandomName",
          |    "pocUsageBase": "APIUsage",
          |    "deviceCreationToken": "1234567890",
          |    "certificationCreationToken": "987654321",
          |    "idGardIdentifier": "gard-identifier",
          |    "tenantGroupId": "random-group",
          |    "tenantOrganisationalUnitGroupId": "organisational-group-id"
          |}
          |""".stripMargin)

        createTenantRequestJSON.extract[CreateTenantRequest] shouldBe CreateTenantRequest(
          TenantName("someRandomName"),
          APIUsage,
          PlainDeviceCreationToken("1234567890"),
          PlainCertificationCreationToken("987654321"),
          IdGardIdentifier("gard-identifier"),
          TenantGroupId("random-group"),
          TenantOrganisationalUnitGroupId("organisational-group-id")
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
        val createTenantRequestJSON = parse("""
          |{
          |    "tenantName": "someRandomName",
          |    "pocUsageBase": "APIUsage",
          |    "deviceCreationToken": "1234567890",
          |    "idGardIdentifier": "gard-identifier",
          |    "tenantGroupId": "random-group",
          |    "tenantOrganisationalUnitGroupId": "organisational-group-id"
          |}
          |""".stripMargin)

        createTenantRequestJSON.extractOpt[CreateTenantRequest] shouldBe None
      }
    }
  }

  "POCUsageBase" should {
    "Parse known values" in {
      withInjector { injector =>
        implicit val formats: Formats = injector.get[Formats]
        JString("APIUsage").extract[POCUsageBase] shouldBe APIUsage
        JString("UIUsage").extract[POCUsageBase] shouldBe UIUsage
        JString("AllChannelsUsage").extract[POCUsageBase] shouldBe AllChannelsUsage
      }
    }

    "Fail if provided value is not known" in {
      withInjector { injector =>
        implicit val formats: Formats = injector.get[Formats]
        JString("NotAnUsage").extractOpt[POCUsageBase] shouldBe None
      }
    }
  }

}
