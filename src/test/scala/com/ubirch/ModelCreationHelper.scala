package com.ubirch

import com.ubirch.models.auth.{ Base64String, EncryptedData }
import com.ubirch.models.poc._
import com.ubirch.models.tenant._
import org.json4s.native.JsonMethods.parse

import java.util.UUID

object ModelCreationHelper {

  private val base64String = Base64String(
    "TWFuIGlzIGRpc3Rpbmd1aXNoZWQsIG5vdCBvbmx5IGJ5IGhpcyByZWFzb24sIGJ1dCBieSB0aGlz\nIHNpbmd1bGFyIHBhc3Npb24gZnJvbSBvdGhlciBhbmltYWxzLCB3aGljaCBpcyBhIGx1c3Qgb2Yg\ndGhlIG1pbmQsIHRoYXQgYnkgYSBwZXJzZXZlcmFuY2Ugb2YgZGVsaWdodCBpbiB0aGUgY29udGlu\ndWVkIGFuZCBpbmRlZmF0aWdhYmxlIGdlbmVyYXRpb24gb2Yga25vd2xlZGdlLCBleGNlZWRzIHRo\nZSBzaG9ydCB2ZWhlbWVuY2Ugb2YgYW55IGNhcm5hbCBwbGVhc3VyZS4=")
  private val encryptedData = EncryptedData(base64String)
  private val deviceCreationToken = EncryptedDeviceCreationToken(encryptedData)
  private val certCreationToken = EncryptedCertificationCreationToken(encryptedData)
  private val tenantName = "tenantName"
  def createTenant(id: UUID = UUID.randomUUID(), name: String = tenantName): Tenant = {
    Tenant(
      TenantId(id),
      TenantName(name),
      API,
      deviceCreationToken,
      certCreationToken,
      IdGardIdentifier("folder-identifier"),
      TenantGroupId("T_" + name)
    )
  }

  def createPoc(
    id: UUID = UUID.randomUUID(),
    tenantId: UUID = UUID.randomUUID(),
    tenantGroupName: String = s"T_$tenantName",
    externalId: String = UUID.randomUUID().toString): Poc =
    Poc(
      id,
      tenantId,
      tenantGroupName,
      externalId,
      "pocName",
      Address("", "", None, 67832, "", None, None, "France"),
      "pocPhone",
      certifyApp = true,
      None,
      clientCertRequired = false,
      "data-schema-id",
      Some(JsonConfig(parse("""{"test":"hello"}"""))),
      PocManager("surname", "", "", "08023-782137")
    )

  def createPocStatus(id: UUID = UUID.randomUUID()): PocStatus =
    PocStatus(
      id,
      validDataSchemaGroup = true,
      clientCertRequired = false,
      clientCertDownloaded = None,
      clientCertProvided = None,
      logoRequired = false,
      logoReceived = None,
      logoStored = None
    )
}
