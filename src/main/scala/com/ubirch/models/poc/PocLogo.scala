package com.ubirch.models.poc

import cats.effect.Resource
import monix.eval.Task

import java.io.ByteArrayInputStream
import java.util.UUID
import javax.imageio.ImageIO

case class PocLogo(
  pocId: UUID,
  img: Array[Byte]
)

object PocLogo {
  val MAX_IMAGE_SIZE = 1048576
  val MAX_WIDTH = 1024
  val MAX_HEIGHT = 1024
  def create(pocId: UUID, img: Array[Byte]): Task[Either[String, PocLogo]] = {
    Resource.make(
      Task(new ByteArrayInputStream(img))
    )(in => Task(in.close())).use { in =>
      val imageIO = ImageIO.read(in)
      if (img.size > MAX_IMAGE_SIZE || imageIO.getWidth > MAX_WIDTH || imageIO.getHeight > MAX_HEIGHT) {
        Task(Left("Logo image is too big"))
      } else {
        Task(Right(PocLogo(pocId, img)))
      }
    }.onErrorHandle {
      // The ImageIO returns null when the input is not image
      case _: NullPointerException =>
        Left("The url seems not to be image.")
    }
  }
}
