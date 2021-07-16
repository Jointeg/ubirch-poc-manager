package com.ubirch.db.context

import com.google.inject.{ Inject, Singleton }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.services.lifecycle.Lifecycle
import io.getquill.{ PostgresMonixJdbcContext, SnakeCase }
import monix.eval.Task
import monix.execution.Scheduler
import org.joda.time.DateTime

import java.sql
import java.sql.Types
import java.time.Clock
import java.util.{ Calendar, TimeZone }
import scala.concurrent.Future

trait QuillMonixJdbcContext {
  val ctx: PostgresMonixJdbcContext[SnakeCase]
  def systemClock: Clock

  import ctx._

  implicit def dateTimeEncoder: Encoder[DateTime] =
    encoder(
      Types.TIMESTAMP,
      (index, value, row) =>
        row.setTimestamp(
          index,
          new sql.Timestamp(value.getMillis),
          Calendar.getInstance(TimeZone.getTimeZone(systemClock.getZone)))
    )

  implicit def dateTimeDecoder: Decoder[DateTime] =
    decoder((index, row) =>
      new DateTime(row.getTimestamp(index, Calendar.getInstance(TimeZone.getTimeZone(systemClock.getZone))).getTime))

  def withTransaction[T](f: => Task[T]): Task[T]
}

@Singleton
case class PostgresQuillMonixJdbcContext @Inject() (lifecycle: Lifecycle, clock: Clock)(implicit
val scheduler: Scheduler)
  extends QuillMonixJdbcContext
  with LazyLogging {

  override val systemClock: Clock = clock

  val ctx: PostgresMonixJdbcContext[SnakeCase] =
    try {
      new PostgresMonixJdbcContext(SnakeCase, "database")
    } catch {
      case e: IllegalStateException =>
        logger.error("can't connect postgres.", e.getMessage)
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

  lifecycle.addStopHook(() => Future(ctx.close()))
}
