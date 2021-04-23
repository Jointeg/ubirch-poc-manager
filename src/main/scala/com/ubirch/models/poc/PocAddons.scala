package com.ubirch.models.poc

import java.util.UUID

case class PocAddons(
                      userRealmRoleId: UUID,
                      userRealmGroupId: UUID,
                      deviceRealmRoleId: UUID,
                      deviceRealmGroupId: UUID,
                      deviceId: UUID,
                      clientCertFolder: Option[String])
