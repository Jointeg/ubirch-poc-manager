package com.ubirch.db.context

import com.google.inject.{ Inject, Singleton }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.services.lifeCycle.Lifecycle
import io.getquill.{ PostgresMonixJdbcContext, SnakeCase }
import monix.eval.Task
import monix.execution.Scheduler

import scala.concurrent.Future

trait QuillMonixJdbcContext {
  val ctx: PostgresMonixJdbcContext[SnakeCase]

  def withTransaction[T](f: => Task[T]): Task[T]
}

@Singleton
case class PostgresQuillMonixJdbcContext @Inject() (lifecycle: Lifecycle)(implicit scheduler: Scheduler)
  extends QuillMonixJdbcContext
  with LazyLogging {
  val ctx: PostgresMonixJdbcContext[SnakeCase] =
    try {
      new PostgresMonixJdbcContext(SnakeCase, "database")
    } catch {
      case _: IllegalStateException =>
        //This error will contain otherwise password information, which we wouldn't want to log.
        throw new IllegalStateException(
          "something went wrong on constructing postgres jdbc context; we're hiding the original exception message," +
            " so no password will be shown. You might want to activate the error and change the password afterwards.")
      case ex: Throwable => throw ex
    }

  def withTransaction[T](f: => Task[T]): Task[T] = {
    ctx.transaction {
      f
    }
  }

  lifecycle.addStopHook(() => Future.successful(ctx.close()))
}
