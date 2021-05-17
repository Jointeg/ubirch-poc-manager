package com.ubirch.db.context

import com.google.inject.{Inject, Singleton}
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.services.lifeCycle.Lifecycle
import io.getquill.{PostgresJdbcContext, SnakeCase}
import monix.eval.Task
import monix.execution.Scheduler

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

trait QuillJdbcContext {
  val ctx: PostgresJdbcContext[SnakeCase]

  def withTransaction[T](f: => Task[T]): Task[T]
}

@Singleton
case class PostgresQuillJdbcContext @Inject() (lifecycle: Lifecycle)(implicit scheduler: Scheduler)
  extends QuillJdbcContext with LazyLogging {
  val ctx: PostgresJdbcContext[SnakeCase] =
    try {
      new PostgresJdbcContext(SnakeCase, "database")
    } catch {
      case _: IllegalStateException =>
        //This error will contain otherwise password information, which we wouldn't want to log.
        throw new IllegalStateException(
          "something went wrong on constructing postgres jdbc context; we're hiding the original exception message," +
            " so no password will be shown. You might want to activate the error and change the password afterwards.")
      case ex: Throwable => throw ex
    }

  def withTransaction[T](f: => Task[T]): Task[T] = {
    Task {
      ctx.transaction {
        // @TODO consider using PostgresMonixJdbcContext because here blocks the thread.
        f.runSyncUnsafe(10.seconds)
      }
    }
  }

  lifecycle.addStopHook(() => Future.successful(ctx.close()))
}
