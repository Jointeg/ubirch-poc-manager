package com.ubirch.services.util

import cats.effect.Resource
import monix.eval.Task

import scala.io.Source

object CsvHelper {
  def openFile(csv: String): Resource[Task, Source] =
    Resource.make {
      Task(Source.fromString(csv))
    } { source =>
      Task(source.close())
    }
}
