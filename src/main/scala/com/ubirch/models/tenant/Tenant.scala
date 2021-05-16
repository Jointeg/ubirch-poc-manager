package com.ubirch.models.tenant

import com.ubirch.models.poc.{ Created, Updated }
import org.joda.time.DateTime

case class Tenant(
  id: TenantId,
  tenantName: TenantName,
  usageType: UsageType,
  deviceCreationToken: EncryptedDeviceCreationToken,
  idGardIdentifier: IdGardIdentifier,
  certifyGroupId: TenantCertifyGroupId,
  deviceGroupId: TenantDeviceGroupId,
  clientCert: Option[ClientCert],
  lastUpdated: Updated = Updated(DateTime.now()),
  created: Created = Created(DateTime.now())
)
