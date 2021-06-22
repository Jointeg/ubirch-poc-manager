package com.ubirch.models.tenant

import com.ubirch.models.keycloak.group

case class DeviceAndCertifyGroups(
  deviceGroup: group.GroupId,
  certifyGroup: group.GroupId,
  tenantTypeGroup: Option[group.GroupId])
