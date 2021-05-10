package com.ubirch.formats

import com.ubirch.UnitTestBase
import com.ubirch.models.poc.{ Created, DeviceId, PocManager, Updated }
import com.ubirch.models.tenant.{ TenantId, TenantName }
import com.ubirch.services.formats.CustomFormats
import org.joda.time.DateTime
import org.json4s.ext.{ JavaTypesSerializers, JodaTimeSerializers }
import org.json4s.native.Serialization._
import org.json4s.{ DefaultFormats, Formats, StringInput }

class JsonFormatTests extends UnitTestBase {

  implicit private val formats: Formats =
    DefaultFormats.lossless ++ CustomFormats.all ++ JavaTypesSerializers.all ++ JodaTimeSerializers.all

  "TenantId" should {
    "be serialized to value: uuid format" in {
      val tenantId = TenantId(TenantName("name"))

      val serializedTenantId = write[TenantId](tenantId)
      serializedTenantId shouldBe s"""{"value":"${tenantId.value.value.toString}"}"""
    }

    "be possible to be read from UUID string" in {
      val tenantId = """{"value": "cdc1e27c-ff79-5bd8-38a1-bf918d618b2b"}"""
      val deserializedTenantId = read[TenantId](StringInput(tenantId))
      deserializedTenantId shouldBe TenantId.unsafeApply("cdc1e27c-ff79-5bd8-38a1-bf918d618b2b")
    }
  }

  "DeviceId" should {
    "be serialized to value: uuid format" in {
      val deviceId = DeviceId(TenantId(TenantName("name")), "external")

      val serializedDeviceId = write[DeviceId](deviceId)
      serializedDeviceId shouldBe s"""{"value":"${deviceId.value.value.toString}"}"""
    }

    "be possible to be read from UUID string" in {
      val deviceId = """{"value": "cdc1e27c-ff79-5bd8-38a1-bf918d618b2b"}"""
      val deserializedDeviceId = read[DeviceId](StringInput(deviceId))
      deserializedDeviceId shouldBe DeviceId.unsafeApply("cdc1e27c-ff79-5bd8-38a1-bf918d618b2b")
    }
  }

  "PocManager" should {
    "be serialized to format with different field naming" in {
      val pocManager = PocManager("Kowalski", "Adam", "test@email.com", "5123213123")

      val serializedPocManager = write[PocManager](pocManager)
      serializedPocManager shouldBe
        """{"lastName":"Kowalski","firstName":"Adam","email":"test@email.com","mobilePhone":"5123213123"}""".stripMargin
    }

    "be possible to be deserialized from different field naming" in {
      val pocManagerJson =
        """{"lastName":"Kowalski","firstName":"Adam","email":"test@email.com","mobilePhone":"5123213123"}"""

      val deserializedPocManager = read[PocManager](StringInput(pocManagerJson))
      deserializedPocManager shouldBe PocManager("Kowalski", "Adam", "test@email.com", "5123213123")
    }
  }

  "Created" should {
    "be serialized to yyyy-MM-dd'T'HH:mm:ss.SSS format" in {
      val created = Created(DateTime.parse("2021-05-07T17:34:40.890Z"))

      val serializedCreated = write[Created](created)
      serializedCreated shouldBe """"2021-05-07T17:34:40.890Z""""
    }
  }

  "Updated" should {
    "be serialized to yyyy-MM-dd'T'HH:mm:ss.SSS format" in {
      val updated = Updated(DateTime.parse("2021-05-07T19:34:40.890Z"))

      val serializedUpdated = write[Updated](updated)
      serializedUpdated shouldBe """"2021-05-07T19:34:40.890Z""""
    }
  }
}
