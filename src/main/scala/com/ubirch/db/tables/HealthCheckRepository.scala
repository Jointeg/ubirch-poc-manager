package com.ubirch.db.tables
import com.ubirch.db.context.QuillMonixJdbcContext
import monix.eval.Task

import javax.inject.Inject

trait HealthCheckRepository {
  def healthCheck(): Task[Unit]
}

class DefaultHealthCheckRepository @Inject() (quillMonixJdbcContext: QuillMonixJdbcContext)
  extends HealthCheckRepository {
  import quillMonixJdbcContext.ctx._

  private def healthCheckQuery =
    quote {
      infix"SELECT EXISTS(SELECT 1 FROM information_schema.schemata WHERE schema_name = 'poc_manager')".as[Boolean]
    }

  override def healthCheck(): Task[Unit] =
    run(healthCheckQuery).flatMap {
      case true  => Task.unit
      case false => Task.raiseError(new RuntimeException("Could not find poc_manager schema"))
    }
}
