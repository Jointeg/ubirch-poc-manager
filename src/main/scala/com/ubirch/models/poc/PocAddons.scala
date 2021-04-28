package com.ubirch.models.poc

import io.getquill.Embedded

import java.util.UUID

case class PocAddons(
  userRealmRoleName: Option[String] = None,
  userRealmGroupId: Option[UUID] = None,
  deviceRealmRoleName: Option[String] = None,
  deviceRealmGroupId: Option[UUID] = None,
  deviceId: Option[UUID] = None,
  clientCertFolder: Option[String] = None
) extends Embedded
