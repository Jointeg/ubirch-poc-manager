package com.ubirch.models.poc

import org.joda.time.DateTime

import java.util.UUID

case class PocStatus(
  pocId: UUID,
  certifyRoleCreated: Boolean = false,
  certifyGroupCreated: Boolean = false,
  certifyGroupRoleAssigned: Boolean = false,
  certifyGroupTenantRoleAssigned: Boolean = false,
  deviceRoleCreated: Boolean = false,
  deviceGroupCreated: Boolean = false,
  deviceGroupRoleAssigned: Boolean = false,
  deviceGroupTenantRoleAssigned: Boolean = false,
  deviceCreated: Boolean = false,
  assignedDataSchemaGroup: Boolean = false,
  assignedDeviceGroup: Boolean = false,
  clientCertRequired: Boolean,
  clientCertCreated: Option[Boolean],
  clientCertProvided: Option[Boolean],
  logoRequired: Boolean,
  logoReceived: Option[Boolean],
  logoStored: Option[Boolean],
  goClientProvided: Boolean = false,
  certifyApiProvided: Boolean = false,
  errorMessage: Option[String] = None,
  lastUpdated: Updated = Updated(DateTime.now()), //updated automatically on storage in DB
  created: Created = Created(DateTime.now())
) {
  override def toString: String = {
    s"pocId:$pocId\n" +
      s"certifyRoleCreated:$certifyRoleCreated\n" +
      s"certifyGroupCreated:$certifyGroupCreated\n" +
      s"certifyGroupRoleAssigned:$certifyGroupRoleAssigned\n" +
      s"certifyGroupTenantRoleAssigned:$certifyGroupTenantRoleAssigned\n" +
      s"deviceRoleCreated:$deviceRoleCreated\n" +
      s"deviceGroupCreated:$deviceGroupCreated\n" +
      s"deviceGroupRoleAssigned:$deviceGroupRoleAssigned\n" +
      s"deviceGroupTenantRoleAssigned:$deviceGroupTenantRoleAssigned\n" +
      s"deviceCreated:$deviceCreated\n" +
      s"clientCertRequired:$clientCertRequired\n" +
      s"clientCertCreated:$clientCertCreated\n" +
      s"clientCertProvided:$clientCertProvided\n" +
      s"logoRequired:$logoRequired\n" +
      s"logoReceived:$logoReceived\n" +
      s"logoStored:$logoStored\n" +
      s"certApiProvided:$certifyApiProvided\n" +
      s"goClientProvided:$goClientProvided\n" +
      s"errorMessage:$errorMessage\n" +
      s"lastUpdated:$lastUpdated\n" +
      s"created:$created"
  }
}

object PocStatus {
  def init(poc: Poc): PocStatus =
    PocStatus(
      pocId = poc.id,
      clientCertRequired = poc.clientCertRequired,
      clientCertCreated = if (poc.clientCertRequired) Some(false) else None,
      clientCertProvided = if (poc.clientCertRequired) Some(false) else None,
      logoRequired = poc.certifyApp,
      logoReceived = if (poc.certifyApp) Some(false) else None,
      logoStored = if (poc.certifyApp) Some(false) else None
    )
}
