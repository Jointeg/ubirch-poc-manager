package com.ubirch.db.context

import com.google.inject.{Inject, Singleton}
import com.ubirch.services.lifeCycle.Lifecycle
import io.getquill.{PostgresJdbcContext, SnakeCase}

import scala.concurrent.Future

trait QuillJdbcContext {
  val ctx: PostgresJdbcContext[SnakeCase]
}

@Singleton
case class PostgresQuillJdbcContext @Inject() (lifecycle: Lifecycle) extends QuillJdbcContext {
  val ctx: PostgresJdbcContext[SnakeCase] =
    try {
      new PostgresJdbcContext(SnakeCase, "database")
    } catch {
      case _: IllegalStateException =>
        //This error will contain otherwise password information, which we wouldn't want to log.
        throw new IllegalStateException(
          "something went wrong on constructing postgres jdbc context; we're hiding the original exception message," +
            " so no password will be shown. You might want to activate the error and change the password afterwards.")
      case ex => throw ex
    }

  lifecycle.addStopHook(() => Future.successful(ctx.close()))
}
