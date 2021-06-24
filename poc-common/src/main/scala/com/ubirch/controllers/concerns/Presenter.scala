package com.ubirch.controllers.concerns

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.{ NOK, Response }
import monix.eval.Task
import org.json4s.Formats
import org.json4s.native.Serialization.write
import org.scalatra.{ ActionResult, InternalServerError, Ok }

import scala.util.{ Failure, Success, Try }

object Presenter extends LazyLogging {
  def toJsonStr[T](r: Response[T])(implicit f: Formats): Task[String] = Task(write[Response[T]](r))

  def toJsonResult[T](t: T)(implicit f: Formats): ActionResult = {
    Try(write[T](t)) match {
      case Success(json) => Ok(json)
      case Failure(ex) =>
        val errorMsg = s"Could not parse ${t.getClass.getSimpleName} to json"
        logger.error(errorMsg, ex)
        InternalServerError(NOK.serverError(errorMsg))
    }
  }
}
