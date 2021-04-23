package com.ubirch.models.poc

import org.joda.time.DateTime

import java.util.UUID

/**
  * @param validDataSchemaGroup MVP1: if dataSchemaGroup is not valid, don't start creating PoC
  */
case class PocStatus(
                      pocId: UUID,
                      validDataSchemaGroup: Boolean,
                      userRealmRoleCreated: Boolean = false,
                      userRealmGroupCreated: Boolean = false,
                      deviceRealmRoleCreated: Boolean = false,
                      deviceRealmGroupCreated: Boolean = false,
                      deviceCreated: Boolean = false,
                      clientCertRequired: Boolean,
                      clientCertDownloaded: Option[Boolean],
                      clientCertProvided: Option[Boolean],
                      logoRequired: Boolean,
                      logoReceived: Option[Boolean],
                      logoStored: Option[Boolean],
                      certApiProvided: Boolean = false,
                      goClientProvided: Boolean = false,
                      errorMessages: Option[String] = None,
                      lastUpdated: Updated = Updated(DateTime.now()),
                      created: Created = Created(DateTime.now())
                    )
