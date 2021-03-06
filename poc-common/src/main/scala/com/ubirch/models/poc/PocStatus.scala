package com.ubirch.models.poc

import org.joda.time.DateTime

import java.util.UUID

case class PocStatus(
  pocId: UUID,
  certifyRoleCreated: Boolean = false,
  certifyGroupCreated: Boolean = false,
  certifyGroupRoleAssigned: Boolean = false,
  adminGroupCreated: Option[Boolean],
  adminRoleAssigned: Option[Boolean],
  pocTypeRoleCreated: Option[Boolean],
  pocTypeGroupCreated: Option[Boolean],
  pocTypeGroupRoleAssigned: Option[Boolean],
  employeeGroupCreated: Option[Boolean],
  employeeRoleAssigned: Option[Boolean],
  deviceRoleCreated: Boolean = false,
  deviceGroupCreated: Boolean = false,
  deviceGroupRoleAssigned: Boolean = false,
  deviceCreated: Boolean = false,
  assignedDataSchemaGroup: Boolean = false,
  assignedTrustedPocGroup: Boolean = false,
  assignedDeviceGroup: Boolean = false,
  clientCertRequired: Boolean,
  orgUnitCertCreated: Option[Boolean],
  clientCertCreated: Option[Boolean],
  clientCertProvided: Option[Boolean],
  logoRequired: Boolean,
  logoStored: Option[Boolean],
  goClientProvided: Boolean = false,
  certifyApiProvided: Boolean = false,
  errorMessage: Option[String] = None,
  lastUpdated: Updated = Updated(DateTime.now()), //updated automatically on storage in DB
  created: Created = Created(DateTime.now())
) {
  override def toString: String = {
    s"pocId: $pocId\n" +
      s"certifyRoleCreated: $certifyRoleCreated\n" +
      s"certifyGroupCreated: $certifyGroupCreated\n" +
      s"certifyGroupRoleAssigned: $certifyGroupRoleAssigned\n" +
      s"adminGroupCreated: $adminGroupCreated\n" +
      s"adminRoleAssigned: $adminRoleAssigned\n" +
      s"pocTypeRoleCreated: $pocTypeRoleCreated\n" +
      s"pocTypeGroupCreated: $pocTypeGroupCreated\n" +
      s"pocTypeGroupRoleAssigned: $pocTypeGroupRoleAssigned\n" +
      s"employeeGroupCreated: $employeeGroupCreated\n" +
      s"employeeRoleAssigned: $employeeRoleAssigned\n" +
      s"deviceRoleCreated: $deviceRoleCreated\n" +
      s"deviceGroupCreated: $deviceGroupCreated\n" +
      s"deviceGroupRoleAssigned: $deviceGroupRoleAssigned\n" +
      s"deviceCreated: $deviceCreated\n" +
      s"assignedDataSchemaGroup: $assignedDataSchemaGroup\n" +
      s"assignedTrustedPocGroup: $assignedTrustedPocGroup\n" +
      s"assignedDeviceGroup: $assignedDeviceGroup\n" +
      s"clientCertRequired: $clientCertRequired\n" +
      s"orgUnitCertIdCreated: $orgUnitCertCreated\n" +
      s"clientCertCreated: $clientCertCreated\n" +
      s"clientCertProvided: $clientCertProvided\n" +
      s"logoRequired: $logoRequired\n" +
      s"logoStored: $logoStored\n" +
      s"goClientProvided: $goClientProvided\n" +
      s"certApiProvided: $certifyApiProvided\n" +
      s"errorMessage: $errorMessage\n" +
      s"lastUpdated: $lastUpdated\n" +
      s"created: $created"
  }
}

object PocStatus {
  def init(poc: Poc): PocStatus =
    PocStatus(
      pocId = poc.id,
      clientCertRequired = poc.typeIsApp,
      orgUnitCertCreated = if (poc.typeIsApp) Some(false) else None,
      clientCertCreated = if (poc.typeIsApp) Some(false) else None,
      clientCertProvided = if (poc.typeIsApp) Some(false) else None,
      adminGroupCreated = if (poc.typeIsApp) Some(false) else None,
      adminRoleAssigned = if (poc.typeIsApp) Some(false) else None,
      pocTypeRoleCreated = if (poc.typeIsApp) Some(false) else None,
      pocTypeGroupCreated = if (poc.typeIsApp) Some(false) else None,
      pocTypeGroupRoleAssigned = if (poc.typeIsApp) Some(false) else None,
      employeeGroupCreated = if (poc.typeIsApp) Some(false) else None,
      employeeRoleAssigned = if (poc.typeIsApp) Some(false) else None,
      logoRequired = poc.typeIsApp,
      logoStored = if (poc.typeIsApp) Some(false) else None
    )
}
