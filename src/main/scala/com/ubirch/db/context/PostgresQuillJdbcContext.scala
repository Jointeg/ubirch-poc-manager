package com.ubirch.db.context

import com.google.inject.{ Inject, Singleton }
import com.ubirch.services.lifeCycle.Lifecycle
import io.getquill.{ PostgresJdbcContext, SnakeCase }

import scala.concurrent.Future

trait QuillJdbcContext {
  val ctx: PostgresJdbcContext[SnakeCase]
}

@Singleton
case class PostgresQuillJdbcContext @Inject() (lifecycle: Lifecycle) extends QuillJdbcContext {
  val ctx = new PostgresJdbcContext(SnakeCase, "database")

  lifecycle.addStopHook(() => Future.successful(ctx.close()))
}
