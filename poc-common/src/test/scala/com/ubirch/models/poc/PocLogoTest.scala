package com.ubirch.models.poc

import org.scalatest.{ Matchers, WordSpec }

import java.io.File
import java.nio.file.Files
import java.util.UUID

class PocLogoTest extends WordSpec with Matchers {
  "Creation of PocLogo" should {
    "create successfully when image size is 620*620" in {
      val file = new File("src/test/resources/img/620_620.jpg")
      val imgBytes = Files.readAllBytes(file.toPath)
      val result = PocLogo.create(UUID.randomUUID(), imgBytes)
      assert(result.isRight)
    }

    "create successfully when image size is 1024*1024" in {
      val file = new File("src/test/resources/img/1024_1024.jpg")
      val imgBytes = Files.readAllBytes(file.toPath)
      val result = PocLogo.create(UUID.randomUUID(), imgBytes)
      assert(result.isRight)
    }

    "fail to create when image size is 1025*1025" in {
      val file = new File("src/test/resources/img/1025_1025.png")
      val imgBytes = Files.readAllBytes(file.toPath)
      val result = PocLogo.create(UUID.randomUUID(), imgBytes)
      assert(result.isLeft)
    }
  }
}
