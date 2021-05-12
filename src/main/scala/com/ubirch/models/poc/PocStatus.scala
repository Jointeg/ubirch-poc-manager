package com.ubirch.models.poc

import org.joda.time.DateTime

import java.util.UUID

/**
  * @param validDataSchemaGroup MVP1: if dataSchemaGroup is not valid, don't start creating PoC
  */
case class PocStatus(
  pocId: UUID,
  validDataSchemaGroup: Boolean,
  userRoleCreated: Boolean = false,
  userGroupCreated: Boolean = false,
  userGroupRoleAssigned: Boolean = false,
  deviceRoleCreated: Boolean = false,
  deviceGroupCreated: Boolean = false,
  deviceGroupRoleAssigned: Boolean = false,
  deviceCreated: Boolean = false,
  clientCertRequired: Boolean,
  clientCertCreated: Option[Boolean],
  clientCertProvided: Option[Boolean],
  orgUnitCertIdCreated: Option[Boolean],
  logoRequired: Boolean,
  logoReceived: Option[Boolean],
  logoStored: Option[Boolean],
  certifyApiProvided: Boolean = false,
  goClientProvided: Boolean = false,
  errorMessage: Option[String] = None,
  lastUpdated: Updated = Updated(DateTime.now()),
  created: Created = Created(DateTime.now())
) {
  override def toString: String = {
    s"pocId:$pocId\n" +
      s"validDataSchemaGroup:$validDataSchemaGroup\n" +
      s"userRoleCreated:$userRoleCreated\n" +
      s"userGroupCreated:$userGroupCreated\n" +
      s"userGroupRoleAssigned:$userGroupRoleAssigned\n" +
      s"deviceRoleCreated:$deviceRoleCreated\n" +
      s"deviceGroupCreated:$deviceGroupCreated\n" +
      s"deviceGroupRoleAssigned:$deviceGroupRoleAssigned\n" +
      s"deviceCreated:$deviceCreated\n" +
      s"clientCertRequired:$clientCertRequired\n" +
      s"clientCertCreated:$clientCertCreated\n" +
      s"clientCertProvided:$clientCertProvided\n" +
      s"orgUnitCertIdCreated:$orgUnitCertIdCreated\n" +
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
