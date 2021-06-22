package com.ubirch.models.poc

import io.getquill.{ Embedded, MappedEncoding }

import java.net.URL

case class LogoURL(url: URL) extends Embedded

object LogoURL {
  implicit val encodeLogoURL: MappedEncoding[LogoURL, String] =
    MappedEncoding[LogoURL, String](_.url.toString)
  implicit val decodeLogoURL: MappedEncoding[String, LogoURL] =
    MappedEncoding[String, LogoURL](url => LogoURL(new URL(url)))
}
