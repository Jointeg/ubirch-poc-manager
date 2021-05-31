package com.ubirch.models.user

import java.nio.charset.StandardCharsets

final case class LastName(value: String) extends AnyVal

object LastName {
  def apply(value: String): LastName = {
    new LastName(new String(value.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8))
  }
}
