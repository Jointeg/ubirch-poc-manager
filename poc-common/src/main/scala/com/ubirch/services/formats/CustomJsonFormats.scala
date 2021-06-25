package com.ubirch.services.formats
import org.json4s.Serializer

import javax.inject.Singleton

trait CustomJsonFormats {
  def formats: Iterable[Serializer[_]]
}

@Singleton
class EmptyCustomJsonFormats extends CustomJsonFormats {
  val formats: List[Serializer[_]] = List.empty
}
