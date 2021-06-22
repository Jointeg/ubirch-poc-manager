package com.ubirch.services.formats

import org.json4s.ext.{ JavaTypesSerializers, JodaTimeSerializers }
import org.json4s.{ DefaultFormats, Formats }

import javax.inject._

/**
  * Represents a Json Formats Provider
  */
@Singleton
class JsonFormatsProvider extends Provider[Formats] {

  protected val formats: Formats =
    DefaultFormats.lossless ++ CustomFormats.all ++ JavaTypesSerializers.all ++ JodaTimeSerializers.all
  override def get(): Formats = formats

}
