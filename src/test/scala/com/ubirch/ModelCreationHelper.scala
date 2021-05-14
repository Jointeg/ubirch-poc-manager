package com.ubirch

import com.ubirch.models.auth.{ Base64String, EncryptedData }
import com.ubirch.models.poc._
import com.ubirch.models.tenant._
import com.ubirch.util.ServiceConstants.TENANT_GROUP_PREFIX
import org.json4s.native.JsonMethods.parse

import java.util.UUID

object ModelCreationHelper {

  val base64X509Cert: Base64String =
    Base64String("LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUNURENDQWJXZ0F3SUJBZ0lCQURBTkJna3Foa2lHOXcwQkFRVUZBREJETVFzd0NRWURWUVFHRXdKMWN6RU4KTUFzR0ExVUVDQXdFVkdWemRERVFNQTRHQTFVRUNnd0hWR1Z6ZEU5eVp6RVRNQkVHQTFVRUF3d0tWR1Z6ZEVSdgpiV0ZwYmpBZUZ3MHlNVEExTURReE1ERXlNVEZhRncweU1qQTFNRFF4TURFeU1URmFNRU14Q3pBSkJnTlZCQVlUCkFuVnpNUTB3Q3dZRFZRUUlEQVJVWlhOME1SQXdEZ1lEVlFRS0RBZFVaWE4wVDNKbk1STXdFUVlEVlFRRERBcFUKWlhOMFJHOXRZV2x1TUlHZk1BMEdDU3FHU0liM0RRRUJBUVVBQTRHTkFEQ0JpUUtCZ1FEQS9VZ0VTTVlyVW1yNQpVdWl3UktFQzEwVlR5TVcwcTBNeVdxa1Y2ZXhrSzJxaytkYnRENVBrdjBjU2ZwbTBwWkdkckhWNXVTeDkvT0w4CkZxSUlzMVRxT0lidSsyd1BPaDl4VGoxZkhTSjVnTU9HMFBQeVVDNmh4MnZVOXd5aHVuU0ExTEg0eXdHMmZrOU4KNzhBdWZNeWFteldYczhyZ2RMcGYvRHJYaWxFSXV3SURBUUFCbzFBd1RqQWRCZ05WSFE0RUZnUVVzR2xnSkNEWQpRQkhKb0VYVmNqMXlxK0xNYTNjd0h3WURWUjBqQkJnd0ZvQVVzR2xnSkNEWVFCSEpvRVhWY2oxeXErTE1hM2N3CkRBWURWUjBUQkFVd0F3RUIvekFOQmdrcWhraUc5dzBCQVFVRkFBT0JnUUJnTFVrR3RUYmJ2ZHNyME02TVQ3b2YKRjVONXpzSHZ5NHZsL05zNnpuNEpiejllbERtQTgzbm5KVllmdEhUeGZVWEswb0MwTDJOQ2RUcDVXUml0QzJFVQowWHBuUWh3cW96bnpzM0N1T2NkV25ITzZNWjk1bVNFYUFBTHBLa2xYWGhaWGxqdmtsdTNwWXErWTFCNEw1T3FhCjRRQTFoUU5YV1dMUm1FUWtQZlBRUEE9PQotLS0tLUVORCBDRVJUSUZJQ0FURS0tLS0t")
  private val base64String = Base64String(
    "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiJ9.eyJpc3MoOiJodHRwczovL3Rva2VuLmRldi51YmlyY2guY29tIiwic3ViIjoiMmQ1OGUwYTYtYmI4Ny00Y2YxLTllNWYtZWFmYTU5MmM4YmM1IiwiYXVkIjoiaHR0cHM6Ly9hcGkuY29uc29sZS5kZXYudWJpcmNoLmNvbSIsImV4cCI6MTYyMzEzNjExNiwibmJmIjoxNjIwNDU3NzE3LCJpYXQiOjE2MjA0NTc3NjAsImp0aSI6IjFjNzExMjM0LWVhYWUtNGJmOS1hM2JhLTViYjgxN2VkZDExZSIsInNjcCI6WyJ0aGluZzpjcmVhdGUiXSwicHVyIjoiVEVOX3RlbmFudE5hbWUiLCJ0Z3AiOlsiNDQyYjkyM2QtNTM0NS00Mjk4LWE5NTgtNmIwYjVlZWM3YzdhIl0sInRpZCI6WyIqIl0sIm9yZCI6W119.Zj7YhxqM1MIfWiN00v7SFQdi4WQb6gd-gZmG7d3ccxG5lJNUYnOIN5oZm-WzLGBgYVlvHLZm6OKoO02cl-LC1Q")
  private val encryptedData = EncryptedData(base64String)
  private val deviceCreationToken = EncryptedDeviceCreationToken(encryptedData)
  val dataSchemaGroupId = "data-schema-id"

  private val tenantName = "tenantName"

  def createTenant(
    name: String = tenantName,
    clientCert: Option[ClientCert] = Some(ClientCert(base64X509Cert))): Tenant = {

    Tenant(
      TenantId(TenantName(name)),
      TenantName(name),
      API,
      deviceCreationToken,
      IdGardIdentifier("folder-identifier"),
      TenantCertifyGroupId(TENANT_GROUP_PREFIX + tenantName),
      TenantDeviceGroupId(TENANT_GROUP_PREFIX + tenantName),
      clientCert
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

  def createPocStatus(pocId: UUID = UUID.randomUUID()): PocStatus =
    PocStatus(
      pocId,
      validDataSchemaGroup = true,
      clientCertRequired = false,
      clientCertCreated = None,
      clientCertProvided = None,
      logoRequired = false,
      logoReceived = None,
      logoStored = None
    )
}
