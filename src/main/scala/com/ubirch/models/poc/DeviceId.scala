package com.ubirch.models.poc
import com.ubirch.models.NamespacedUUID
import com.ubirch.models.tenant.TenantId
import io.getquill.MappedEncoding
import memeid4s.UUID

import java.util.{ UUID => jUUID }

final case class DeviceId private (value: NamespacedUUID) extends AnyVal {
  override def toString: String = value.value.toString
}

object DeviceId {

  def apply(tenantId: TenantId, externalId: String) =
    new DeviceId(NamespacedUUID(UUID.V5(tenantId.value.value, externalId)))

  def unsafeApply(uuid: String) =
    new DeviceId(NamespacedUUID.fromJavaUUID(jUUID.fromString(uuid)))

  implicit val encodeTenantId: MappedEncoding[DeviceId, jUUID] =
    MappedEncoding[DeviceId, jUUID](_.value.value.asJava())
  implicit val decodeTenantId: MappedEncoding[jUUID, DeviceId] =
    MappedEncoding[jUUID, DeviceId](uuid => new DeviceId(NamespacedUUID.fromJavaUUID(uuid)))
}
