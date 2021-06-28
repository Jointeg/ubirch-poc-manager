package com.ubirch.db.tables
import com.ubirch.db.context.QuillMonixJdbcContext
import com.ubirch.models.poc.PocLogo
import io.getquill.EntityQuery
import monix.eval.Task

import java.util.UUID
import javax.inject.{ Inject, Singleton }

trait PocLogoRepository {
  def getPocLogoById(pocId: UUID): Task[Option[PocLogo]]
}

@Singleton
class PocLogoTable @Inject() (quillMonixJdbcContext: QuillMonixJdbcContext) extends PocLogoRepository {
  import quillMonixJdbcContext.ctx._

  private def getPocLogoByIdQuery(pocId: UUID): Quoted[EntityQuery[PocLogo]] =
    quote {
      querySchema[PocLogo]("poc_manager.poc_logo_table").filter(_.pocId == lift(pocId))
    }

  def getPocLogoById(pocId: UUID): Task[Option[PocLogo]] =
    run(getPocLogoByIdQuery(pocId)).map(_.headOption)
}
