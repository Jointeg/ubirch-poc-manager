package com.ubirch.services.formats

import org.json4s.ext.{ JavaTypesSerializers, JodaTimeSerializers }
import org.json4s.{ DefaultFormats, Formats, Serializer }

import javax.inject._

/**
  * Represents a Json Formats Provider
  */
@Singleton
class JsonFormatsProvider @Inject() (customJsonFormats: CustomJsonFormats) extends Provider[Formats] {

  protected val formats: Formats =
    DefaultFormats.lossless ++ customJsonFormats.formats ++ JavaTypesSerializers.all ++ JodaTimeSerializers.all
  override def get(): Formats = formats

}
