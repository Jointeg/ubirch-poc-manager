package com.ubirch.formats

import com.ubirch.UnitTestBase
import com.ubirch.models.poc._
import com.ubirch.models.tenant.{ TenantId, TenantName }
import com.ubirch.services.formats.CustomFormats
import org.joda.time.DateTime
import org.json4s.ext.{ JavaTypesSerializers, JodaTimeSerializers }
import org.json4s.native.JsonMethods.parse
import org.json4s.native.Serialization._
import org.json4s.{ DefaultFormats, Formats }

import java.util.UUID

class PocJsonFormatTest extends UnitTestBase {

  implicit private val formats: Formats =
    DefaultFormats.lossless ++ CustomFormats.all ++ JavaTypesSerializers.all ++ JodaTimeSerializers.all

  "Poc should be converted to expected format" in {

    val poc = createPoc(
      id = UUID.fromString("da4ba61a-e6c9-4124-bd3a-df7373e73676"),
      tenantName = TenantName("name"),
      externalId = UUID.fromString("64ccc885-b512-413c-8155-4320e34e4ce7").toString
    ).copy(
      created = Created(DateTime.parse("2021-05-10T08:18:01.036Z")),
      lastUpdated = Updated(DateTime.parse("2021-05-10T08:18:01.000Z")))
    val serializedPoc = writePretty[Poc](poc)

    serializedPoc.trim shouldBe
      s"""|{
          |  "id":"da4ba61a-e6c9-4124-bd3a-df7373e73676",
          |  "tenantId":"cdc1e27c-ff79-5bd8-38a1-bf918d618b2b",
          |  "externalId":"64ccc885-b512-413c-8155-4320e34e4ce7",
          |  "pocName":"pocName",
          |  "address":{
          |    "street":"",
          |    "houseNumber":"",
          |    "zipcode":67832,
          |    "city":"",
          |    "country":"France"
          |  },
          |  "phone":"pocPhone",
          |  "certifyApp":true,
          |  "clientCertRequired":false,
          |  "dataSchemaId":"data-schema-id",
          |  "extraConfig":{
          |    "test":"hello"
          |  },
          |  "manager":{
          |    "lastName":"surname",
          |    "firstName":"",
          |    "email":"",
          |    "mobilePhone":"08023-782137"
          |  },
          |  "roleName":"POC_pocName_da4ba61a-e6c9-4124-bd3a-df7373e73676",
          |  "deviceId":"fa6efde5-238f-5cd9-9b28-c5be05a677f9",
          |  "status":"PENDING",
          |  "lastUpdated":"2021-05-10T08:18:01.000Z",
          |  "created":"2021-05-10T08:18:01.036Z"
          |}""".stripMargin
  }

  private def createPoc(
    id: UUID = UUID.randomUUID(),
    tenantName: TenantName,
    externalId: String = UUID.randomUUID().toString): Poc =
    Poc(
      id = id,
      tenantId = TenantId(tenantName),
      externalId = externalId,
      pocName = "pocName",
      address = Address("", "", None, 67832, "", None, None, "France"),
      phone = "pocPhone",
      certifyApp = true,
      logoUrl = None,
      clientCertRequired = false,
      dataSchemaId = "data-schema-id",
      extraConfig = Some(JsonConfig(parse(""" { "test":"hello" } """))),
      manager = PocManager("surname", "", "", "08023-782137")
    )
}
