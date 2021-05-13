package com.ubirch.services.formats

import com.ubirch.ModelCreationHelper.base64X509Cert
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
             |    "deviceCreationToken": "1234567890",
             |    "certificationCreationToken": "987654321",
             |    "idGardIdentifier": "gard-identifier",
             |    "userGroupId": "random-certify-group",
             |    "deviceGroupId": "random-device-group"
             |}
             |""".stripMargin)

        createTenantRequestJSON.extract[CreateTenantRequest] shouldBe CreateTenantRequest(
          TenantName("someRandomName"),
          API,
          PlainDeviceCreationToken("1234567890"),
          PlainCertificationCreationToken("987654321"),
          IdGardIdentifier("gard-identifier"),
          TenantCertifyGroupId("random-certify-group"),
          TenantDeviceGroupId("random-device-group"),
          None
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
             |    "deviceCreationToken": "1234567890",
             |    "certificationCreationToken": "987654321",
             |    "idGardIdentifier": "gard-identifier",
             |    "userGroupId": "random-certify-group",
             |    "deviceGroupId": "random-device-group",
             |    "clientCert": "${base64X509Cert.value}",
             |
             |}
             |""".stripMargin)

        createTenantRequestJSON.extract[CreateTenantRequest] shouldBe CreateTenantRequest(
          TenantName("someRandomName"),
          API,
          PlainDeviceCreationToken("1234567890"),
          PlainCertificationCreationToken("987654321"),
          IdGardIdentifier("gard-identifier"),
          TenantCertifyGroupId("random-certify-group"),
          TenantDeviceGroupId("random-device-group"),
          Some(ClientCert(base64X509Cert))
        )
      }
    }

    //Todo: fix this test

    //    "Fail to parse JSON if clientCert is not in Base64" in {
    //      withInjector { injector =>
    //        implicit val formats: Formats = injector.get[Formats]
    //        assertThrows[MappingException](parse(
    //          s"""
    //             |{
    //             |    "tenantName": "someRandomName",
    //             |    "usageType": "API",
    //             |    "deviceCreationToken": "1234567890",
    //             |    "certificationCreationToken": "987654321",
    //             |    "idGardIdentifier": "gard-identifier",
    //             |    "tenantGroupId": "random-group"
    //             |    "clientCert": "illegal character ㋡"
    //             |}
    //             |""".stripMargin).extract[CreateTenantRequest])
    //      }
    //    }

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
            |    "deviceCreationToken": "1234567890",
            |    "idGardIdentifier": "gard-identifier",
            |    "tenantGroupId": "random-group"
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
