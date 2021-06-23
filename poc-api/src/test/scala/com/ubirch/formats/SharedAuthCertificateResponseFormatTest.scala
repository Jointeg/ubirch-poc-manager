package com.ubirch.formats

import com.ubirch.UnitTestBase
import com.ubirch.models.auth.Base16String
import com.ubirch.models.auth.cert.{ Passphrase, SharedAuthCertificateResponse }
import com.ubirch.services.formats.CustomFormats
import org.json4s.ext.{ JavaTypesSerializers, JodaTimeSerializers }
import org.json4s.native.Serialization._
import org.json4s.{ DefaultFormats, Formats, StringInput }

import java.util.UUID

class SharedAuthCertificateResponseFormatTest extends UnitTestBase {
  implicit private val formats: Formats =
    DefaultFormats.lossless ++ CustomFormats.all ++ JavaTypesSerializers.all ++ JodaTimeSerializers.all

  "SharedAuthCertificateResponse format should correctly handle snake case" in {
    val sharedAuthCertificateResponse =
      """{"cert_uuid": "cdc1e27c-ff79-5bd8-38a1-bf918d618b2b", "passphrase": "test", "pkcs12": "123"}"""
    val deserializedTenantId = read[SharedAuthCertificateResponse](StringInput(sharedAuthCertificateResponse))
    deserializedTenantId shouldBe SharedAuthCertificateResponse(
      UUID.fromString("cdc1e27c-ff79-5bd8-38a1-bf918d618b2b"),
      Passphrase("test"),
      Base16String("123"))
  }
}
