package com.ubirch

import com.ubirch.models.auth.{ Base64String, EncryptedData }
import com.ubirch.models.poc._
import com.ubirch.models.pocEmployee.{ PocEmployee, PocEmployeeStatus }
import com.ubirch.models.tenant._
import com.ubirch.util.ServiceConstants.TENANT_GROUP_PREFIX
import org.joda.time.LocalDate
import org.json4s.native.JsonMethods.parse

import java.util.UUID
import scala.util.Random

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
  private val tenantNameObj = TenantName("tenantName")
  private val tenantId = TenantId(TenantName(tenantName))

  def createTenant(
    name: String = tenantName,
    sharedAuthCert: Option[SharedAuthCert] = Some(SharedAuthCert(cert))): Tenant = {

    Tenant(
      TenantId(TenantName(name)),
      TenantName(name),
      API,
      deviceCreationToken,
      TenantCertifyGroupId(TENANT_GROUP_PREFIX + tenantName),
      TenantDeviceGroupId(TENANT_GROUP_PREFIX + tenantName),
      OrgId(TenantId(TenantName(name)).value),
      sharedAuthCertRequired = true
    ).copy(sharedAuthCert = sharedAuthCert)
  }

  def createPoc(
    id: UUID = UUID.randomUUID(),
    tenantName: TenantName = tenantNameObj,
    externalId: String = UUID.randomUUID().toString,
    name: String = "pocName",
    status: Status = Pending,
    clientCertRequired: Boolean = false
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
      clientCertRequired,
      dataSchemaGroupId,
      Some(JsonConfig(parse("""{"test":"hello"}"""))),
      PocManager("surname", "", "", "08023-782137"),
      status
    )

  def createPocAdmin(
    pocAdminId: UUID = UUID.randomUUID(),
    pocId: UUID,
    tenantId: TenantId,
    name: String = getRandomString,
    surname: String = getRandomString,
    email: String = getRandomString,
    mobilePhone: String = getRandomString,
    webIdentRequired: Boolean = true,
    webIdentInitiateId: Option[UUID] = None,
    webIdentId: Option[String] = None,
    certifyUserId: Option[UUID] = None,
    dateOfBirth: BirthDate = BirthDate(LocalDate.now.minusYears(20)),
    status: Status = Pending
  ): PocAdmin = {
    PocAdmin(
      pocAdminId,
      pocId,
      tenantId,
      name,
      surname,
      email,
      mobilePhone,
      webIdentRequired,
      webIdentInitiateId,
      webIdentId,
      certifyUserId,
      dateOfBirth,
      status
    )
  }

  private def getRandomString = Random.alphanumeric.take(10).mkString

  def createPocAdminStatus(pocAdmin: PocAdmin, poc: Poc): PocAdminStatus = PocAdminStatus.init(pocAdmin, poc)

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

  def createTenantRequest(sharedAuthCertRequired: Boolean = true): CreateTenantRequest =
    CreateTenantRequest(
      TenantName("tenantName"),
      API,
      PlainDeviceCreationToken(token),
      sharedAuthCertRequired = sharedAuthCertRequired
    )

  def createTenantPocAndEmployee: (Tenant, Poc, PocEmployee) = {
    val pocId = UUID.randomUUID()
    (createTenant(), createPoc(id = pocId, tenantNameObj), createPocEmployee(pocId = pocId))
  }

  def createTenantPocEmployeeAndStatus: (Tenant, Poc, PocEmployee, PocEmployeeStatus) = {
    val employeeId = UUID.randomUUID()
    val pocId = UUID.randomUUID()
    (
      createTenant(),
      createPoc(pocId, tenantNameObj),
      createPocEmployee(pocId = pocId, employeeId = employeeId),
      createPocEmployeeStatus(employeeId))
  }

  def createPocEmployee(employeeId: UUID = UUID.randomUUID(), pocId: UUID = UUID.randomUUID()): PocEmployee = {
    PocEmployee(
      employeeId,
      pocId,
      tenantId,
      "Hans",
      "Welsich",
      s"${employeeId.toString}@test.de"
    )
  }

  def createPocEmployeeStatus(employeeId: UUID = UUID.randomUUID()): PocEmployeeStatus =
    PocEmployeeStatus(
      employeeId
    )

}
