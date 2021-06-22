package com.ubirch.models.poc
import java.util.UUID

trait HasCertifyUserId {
  def id: UUID
  def certifyUserId: Option[UUID]
}
