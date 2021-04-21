package com.ubirch.models.user
import io.getquill.MappedEncoding

final case class Email(value: String) extends AnyVal

object Email {

  implicit val encodeEmail: MappedEncoding[Email, String] = MappedEncoding[Email, String](_.value)
  implicit val decodeEmail: MappedEncoding[String, Email] = MappedEncoding[String, Email](Email(_))

}
