package com.ubirch.models

import io.getquill.MappedEncoding
import memeid4s.UUID

import java.util.{ UUID => jUUID }

case class NamespacedUUID(value: UUID)

object NamespacedUUID {
  private val nullUUID = UUID.fromUUID(jUUID.fromString("00000000-0000-0000-0000-000000000000"))
  val ubirchUUID: UUID = UUID.V5.apply(nullUUID, "ubirch")

  def fromJavaUUID(uuid: jUUID): NamespacedUUID = NamespacedUUID(UUID.fromUUID(uuid))

  implicit val encodeNamespacedUUID: MappedEncoding[NamespacedUUID, jUUID] =
    MappedEncoding[NamespacedUUID, jUUID](_.value.asJava())
  implicit val decodeNamespacedUUID: MappedEncoding[jUUID, NamespacedUUID] =
    MappedEncoding[jUUID, NamespacedUUID](uuid => NamespacedUUID.fromJavaUUID(uuid))
}
