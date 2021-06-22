package com.ubirch.models.poc

import java.io.ByteArrayInputStream
import java.net.URL
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
  def create(pocId: UUID, img: Array[Byte]): Either[String, PocLogo] = {
    try {
      val bais = new ByteArrayInputStream(img)
      val imageIO = ImageIO.read(bais)
      if (img.length > MAX_IMAGE_SIZE || imageIO.getWidth > MAX_WIDTH || imageIO.getHeight > MAX_HEIGHT) {
        Left("Logo image is too big")
      } else {
        Right(PocLogo(pocId, img))
      }
    } catch {
      case _: NullPointerException => Left("The url seems not to be image.")
    }
  }

  def getFileExtension(logoUrl: URL): String = {
    logoUrl.toString.split("\\.").last.toLowerCase() match {
      case "jpg"     => "jpeg"
      case jpegOrPng => jpegOrPng
    }
  }

  //https://stackoverflow.com/questions/59089118/javax-imageio-imageio-file-format-constants
  //also possible would be wbmp, gif, bmp, tif and tiff
  def hasAcceptedFileExtension(logoUrl: URL): Boolean = {
    List("jpg", "jpeg", "png").contains(logoUrl.toString.split("\\.").last.toLowerCase())
  }
}
