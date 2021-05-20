package com.ubirch

import com.ubirch.models.auth.{ Base64String, EncryptedData }
import com.ubirch.models.poc._
import com.ubirch.models.tenant._
import com.ubirch.util.ServiceConstants.TENANT_GROUP_PREFIX
import org.joda.time.LocalDate
import org.json4s.native.JsonMethods.parse

import java.util.UUID

object ModelCreationHelper {

  private val token =
    "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiJ9.eyJpc3MoOiJodHRwczovL3Rva2VuLmRldi51YmlyY2guY29tIiwic3ViIjoiMmQ1OGUwYTYtYmI4Ny00Y2YxLTllNWYtZWFmYTU5MmM4YmM1IiwiYXVkIjoiaHR0cHM6Ly9hcGkuY29uc29sZS5kZXYudWJpcmNoLmNvbSIsImV4cCI6MTYyMzEzNjExNiwibmJmIjoxNjIwNDU3NzE3LCJpYXQiOjE2MjA0NTc3NjAsImp0aSI6IjFjNzExMjM0LWVhYWUtNGJmOS1hM2JhLTViYjgxN2VkZDExZSIsInNjcCI6WyJ0aGluZzpjcmVhdGUiXSwicHVyIjoiVEVOX3RlbmFudE5hbWUiLCJ0Z3AiOlsiNDQyYjkyM2QtNTM0NS00Mjk4LWE5NTgtNmIwYjVlZWM3YzdhIl0sInRpZCI6WyIqIl0sIm9yZCI6W119.Zj7YhxqM1MIfWiN00v7SFQdi4WQb6gd-gZmG7d3ccxG5lJNUYnOIN5oZm-WzLGBgYVlvHLZm6OKoO02cl-LC1Q"
  private val base64String = Base64String(token)
  val cert: String =
    "-----BEGIN CERTIFICATE-----\nMIIEOjCCA9+gAwIBAgIRAORwn415ikB0hRno7leNZ0IwCgYIKoZIzj0EAwIwWTEY\nMBYGA1UECgwPdGVzdF9vcmdfY2VydF8wMS8wLQYDVQQDDCZUZW5hbnQgdGVzdF9v\ncmdfY2VydF8wIEludGVybWVkaWF0ZSBDQTEMMAoGA1UELgwDZGV2MB4XDTIxMDUx\nMzA4NTExMFoXDTIzMDUxMzA4NTExMFowgYwxGDAWBgNVBAoMD3Rlc3Rfb3JnX2Nl\ncnRfMDEaMBgGA1UECwwRdGVzdF9vcmdfY2VydF8wXzAxRjBEBgNVBAMMPVRlbmFu\ndCB0ZXN0X29yZ19jZXJ0XzAgVW5pdCB0ZXN0X29yZ19jZXJ0XzBfMCBJbnRlcm1l\nZGlhdGUgQ0ExDDAKBgNVBC4MA2RldjBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IA\nBOlRMRBxXJO/zGmVw5LQawjhT5MN6wccidA0qt/pQ21kapk4+EsVcJLNnjJPm0Oe\nL4Cm06gXiY2O1Qyf+CVhiI6jggJSMIICTjA4BgNVHRIEMTAvhi11cm46dXVpZDoz\nMDBhNWRjMy04YzAzLTQ2OGYtOTI0YS1mOGMwNDczNmI4ZWUwEgYDVR0TAQH/BAgw\nBgEB/wIBATAOBgNVHQ8BAf8EBAMCAQYwOAYDVR0RBDEwL4YtdXJuOnV1aWQ6MGRl\nMzMwNDktYWI0ZS00NjRkLTg1NTgtY2QyZjc4OWU4YjFlMEMGECsGAQQBg6YFAfRK\nhQaENwAELxYtdXJuOnV1aWQ6MGRlMzMwNDktYWI0ZS00NjRkLTg1NTgtY2QyZjc4\nOWU4YjFlMEMGECsGAQQBg6YFAfRKhQaENwEELxYtdXJuOnV1aWQ6MzAwYTVkYzMt\nOGMwMy00NjhmLTkyNGEtZjhjMDQ3MzZiOGVlMEMGECsGAQQBg6YFAfRKhQaENwIE\nLxYtdXJuOnV1aWQ6MGRlMzMwNDktYWI0ZS00NjRkLTg1NTgtY2QyZjc4OWU4YjFl\nMB0GA1UdDgQWBBSjJBgV3dHrTuP8gCe1mGBOj9SPrDCBxQYDVR0jBIG9MIG6gBSg\nIvurvNV7aozSHfFDK54htWGQzaGBjqSBizCBiDELMAkGA1UEBhMCREUxDzANBgNV\nBAgMBkJlcmxpbjEPMA0GA1UEBwwGQmVybGluMQ8wDQYDVQQKDAZ1YmlyY2gxDTAL\nBgNVBAsMBHRlc3QxFzAVBgNVBAMMDnRlc3RfYXV0aG9yaXR5MR4wHAYJKoZIhvcN\nAQkBFg9pbmZvQHViaXJjaC5jb22CEQDB9J3HVFpJy4doRxISHCbkMAoGCCqGSM49\nBAMCA0kAMEYCIQCRvolwJVbI1wC/NrOiX1ESMirv5OAOHRJaqtYCnTaoXwIhAOzF\n/CzkLQXZcapvl/ZUqB/19FYRjVyf/C7Vt3OwzgmG\n-----END CERTIFICATE-----\n"
  private val encryptedData = EncryptedData(base64String)
  private val deviceCreationToken = EncryptedDeviceCreationToken(encryptedData)
  val dataSchemaGroupId = "data-schema-id"
  val pocTypeValue = "ub_vac_app"

  private val tenantName = "tenantName"

  def createTenant(
    name: String = tenantName,
    sharedAuthCert: Option[SharedAuthCert] = Some(SharedAuthCert(cert))): Tenant = {

    Tenant(
      TenantId(TenantName(name)),
      TenantName(name),
      API,
      deviceCreationToken,
      IdGardIdentifier("folder-identifier"),
      TenantCertifyGroupId(TENANT_GROUP_PREFIX + tenantName),
      TenantDeviceGroupId(TENANT_GROUP_PREFIX + tenantName),
      OrgId(TenantId(TenantName(name)).value),
      sharedAuthCertRequired = true,
      Some(OrgUnitId(UUID.randomUUID())),
      Some(GroupId(UUID.randomUUID())),
      sharedAuthCert
    )
  }

  def createPoc(
    id: UUID = UUID.randomUUID(),
    tenantName: TenantName,
    externalId: String = UUID.randomUUID().toString,
    name: String = "pocName",
    status: Status = Pending
  ): Poc =
    Poc(
      id,
      TenantId(tenantName),
      externalId,
      pocTypeValue,
      name,
      Address("", "", None, 67832, "", None, None, "France"),
      "pocPhone",
      certifyApp = true,
      None,
      clientCertRequired = false,
      dataSchemaGroupId,
      Some(JsonConfig(parse("""{"test":"hello"}"""))),
      PocManager("surname", "", "", "08023-782137"),
      status
    )

  def createPocAdmin(
    pocAdminId: UUID = UUID.randomUUID(),
    pocId: UUID,
    tenantName: TenantName,
    name: String = "firstname",
    surname: String = "lastname",
    email: String = "test@example.com",
    mobilePhone: String = "08023-782137",
    webIdentRequired: Boolean = false,
    webIdentIdentifier: Option[String] = None,
    certifierUserId: UUID = UUID.randomUUID(),
    dateOfBirth: BirthDate = BirthDate(LocalDate.now.minusYears(20)),
    status: Status = Pending
  ): PocAdmin = {
    PocAdmin(
      pocAdminId,
      pocId,
      TenantId(tenantName),
      name,
      surname,
      email,
      mobilePhone,
      webIdentRequired,
      webIdentIdentifier,
      certifierUserId,
      dateOfBirth,
      status
    )
  }

  def createPocStatus(pocId: UUID = UUID.randomUUID()): PocStatus =
    PocStatus(
      pocId,
      clientCertRequired = false,
      clientCertCreated = None,
      clientCertProvided = None,
      orgUnitCertCreated = None,
      logoRequired = false,
      logoReceived = None,
      logoStored = None
    )

  def createPocAdminStatus(pocAdminId: UUID = UUID.randomUUID(), webIdentRequired: Boolean = false): PocAdminStatus = {
    val webIdentTriggered = if (webIdentRequired) Some(false) else None
    val webIdentIdentifierSuccess = if (webIdentRequired) Some(false) else None
    PocAdminStatus(
      pocAdminId,
      webIdentRequired,
      webIdentTriggered,
      webIdentIdentifierSuccess
    )
  }

  def createTenantRequest(sharedAuthCertRequired: Boolean = true): CreateTenantRequest =
    CreateTenantRequest(
      TenantName("tenantName"),
      API,
      PlainDeviceCreationToken(token),
      IdGardIdentifier("empty"),
      TenantCertifyGroupId(UUID.randomUUID().toString),
      TenantDeviceGroupId(UUID.randomUUID().toString),
      sharedAuthCertRequired = sharedAuthCertRequired
    )
}
