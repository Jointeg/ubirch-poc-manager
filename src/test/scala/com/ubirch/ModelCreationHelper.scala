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
      TenantGroupId(TENANT_GROUP_PREFIX + name),
      Some(ClientCert(base64X509Cert))
    )
  }

  def createPoc(
    id: UUID = UUID.randomUUID(),
    tenantId: UUID = UUID.randomUUID(),
    tenantGroupName: String = TENANT_GROUP_PREFIX + tenantName,
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
      clientCertCreated = None,
      clientCertProvided = None,
      logoRequired = false,
      logoReceived = None,
      logoStored = None
    )
}
