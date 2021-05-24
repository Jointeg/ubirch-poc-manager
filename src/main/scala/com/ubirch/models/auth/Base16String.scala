package com.ubirch.models.auth
import java.nio.charset.StandardCharsets

case class Base16String(value: String) extends AnyVal

object Base16String {
  def toISO8859String(base16String: Base16String): Either[Base16ParsingError, String] = {
    try {
      val byteRepresentation = toByteArray(base16String)
      Right(new String(byteRepresentation, StandardCharsets.ISO_8859_1))
    } catch {
      case ex: Exception => Left(Base16ParsingError(ex.getMessage))
    }
  }

  def toByteArray(base16String: Base16String): Array[Byte] = {
    base16String.value.sliding(2, 2).foldLeft(Array.empty[Byte])((acc, str) => {
      val byteValue = Integer.parseInt(str, 16)
      acc :+ byteValue.toByte
    })
  }
}

case class Base16ParsingError(msg: String)
