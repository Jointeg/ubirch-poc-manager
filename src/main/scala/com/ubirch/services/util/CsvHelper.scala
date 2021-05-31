package com.ubirch.services.util

import cats.effect.Resource
import monix.eval.Task

import scala.io.{Codec, Source}

object CsvHelper {
  def openFile(csv: String): Resource[Task, Source] =
    Resource.make {
      Task(Source.fromBytes(csv.getBytes, Codec.UTF8.name))
    } { source =>
      Task(source.close())
    }
}
