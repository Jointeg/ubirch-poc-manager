package com.ubirch.db.tables
import com.google.inject.Inject
import com.ubirch.db.context.QuillMonixJdbcContext
import com.ubirch.models.poc.Poc
import monix.eval.Task

import java.util.UUID
import javax.inject.Singleton

trait PocRepository {
  def getPoc(pocId: UUID): Task[Option[Poc]]
}

@Singleton
class PocTable @Inject() (QuillMonixJdbcContext: QuillMonixJdbcContext) extends PocRepository {

  import QuillMonixJdbcContext.ctx._

  private def getPocQuery(pocId: UUID) =
    quote {
      querySchema[Poc]("poc_manager.poc_table").filter(_.id == lift(pocId))
    }

  override def getPoc(pocId: UUID): Task[Option[Poc]] = run(getPocQuery(pocId)).map(_.headOption)
}
