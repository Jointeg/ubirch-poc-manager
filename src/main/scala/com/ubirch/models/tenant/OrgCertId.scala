package com.ubirch.models.tenant

import com.ubirch.models.NamespacedUUID
import io.getquill.MappedEncoding

import java.util.UUID

case class OrgCertId(value: NamespacedUUID)
object OrgCertId {
  implicit val encodeOrgCertId: MappedEncoding[OrgCertId, UUID] =
    MappedEncoding[OrgCertId, UUID](_.value.value.asJava())
  implicit val decodeOrgCertId: MappedEncoding[UUID, OrgCertId] =
    MappedEncoding[UUID, OrgCertId](uuid => OrgCertId(NamespacedUUID.fromJavaUUID(uuid)))
}
