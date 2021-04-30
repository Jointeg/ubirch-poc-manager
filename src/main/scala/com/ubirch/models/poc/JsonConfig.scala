package com.ubirch.models.poc

import io.getquill.{ Embedded, MappedEncoding }
import org.json4s.JValue
import org.json4s.native.JsonMethods.{ compact, parse, render }

case class JsonConfig(jvalue: JValue) extends Embedded

object JsonConfig {

  implicit val encodeJsonConfig: MappedEncoding[JsonConfig, String] =
    MappedEncoding[JsonConfig, String](eC => compact(render(eC.jvalue)))

  implicit val decodeJsonConfig: MappedEncoding[String, JsonConfig] =
    MappedEncoding[String, JsonConfig](str => JsonConfig(parse(str)))
}
