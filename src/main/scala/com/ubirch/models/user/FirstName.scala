package com.ubirch.models.user

import java.nio.charset.StandardCharsets

final case class FirstName(value: String) extends AnyVal

object FirstName {
  def apply(value: String): FirstName = {
    new FirstName(new String(value.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8))
  }
}
