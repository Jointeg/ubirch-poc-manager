package com.ubirch.models.tenant

import com.ubirch.models.NamespacedUUID
import io.getquill.MappedEncoding

import java.util.UUID

case class OrgId(value: NamespacedUUID)
object OrgId {
  implicit val encodeOrgCertId: MappedEncoding[OrgId, UUID] =
    MappedEncoding[OrgId, UUID](_.value.value.asJava())
  implicit val decodeOrgCertId: MappedEncoding[UUID, OrgId] =
    MappedEncoding[UUID, OrgId](uuid => OrgId(NamespacedUUID.fromJavaUUID(uuid)))
}
