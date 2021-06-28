package com.ubirch.models

import memeid4s.UUID
import org.scalatest.{ Matchers, WordSpec }

import java.util.{ UUID => jUUID }

class NamespacedUUIDTest extends WordSpec with Matchers {

  "Ubirch UUID" should {
    "be namespaced with 'ubirch' name and created from only 0's UUID" in {
      val expectedUUID = UUID.V5(UUID.fromUUID(jUUID.fromString("00000000-0000-0000-0000-000000000000")), "ubirch")
      NamespacedUUID.ubirchUUID shouldBe expectedUUID
    }
  }

}
