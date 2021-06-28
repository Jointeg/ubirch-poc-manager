package com.ubirch.models.tenant

import com.ubirch.models.NamespacedUUID
import io.getquill.MappedEncoding
import memeid4s.UUID

import java.util.{ UUID => jUUID }

final case class TenantId private (value: NamespacedUUID) extends AnyVal {
  def asUUID(): jUUID = value.value.asJava()
}

object TenantId {

  def apply(tenantName: TenantName): TenantId =
    new TenantId(NamespacedUUID(UUID.V5(NamespacedUUID.ubirchUUID, tenantName)))

  def unsafeApply(uuid: String): TenantId = new TenantId(NamespacedUUID.fromJavaUUID(jUUID.fromString(uuid)))

  implicit val encodeTenantId: MappedEncoding[TenantId, jUUID] =
    MappedEncoding[TenantId, jUUID](_.value.value.asJava())
  implicit val decodeTenantId: MappedEncoding[jUUID, TenantId] =
    MappedEncoding[jUUID, TenantId](uuid => new TenantId(NamespacedUUID.fromJavaUUID(uuid)))
}
