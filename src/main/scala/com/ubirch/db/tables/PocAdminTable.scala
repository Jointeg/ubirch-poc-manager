package com.ubirch.db.tables

import com.ubirch.db.context.QuillJdbcContext
import com.ubirch.models.poc.PocAdmin
import io.getquill.Insert
import monix.eval.Task

import java.util.UUID
import javax.inject.Inject

trait PocAdminRepository {
  def createPoc(pocAdmin: PocAdmin): Task[UUID]
}

class PocAdminTable @Inject() (quillJdbcContext: QuillJdbcContext) extends PocAdminRepository {
  import quillJdbcContext.ctx._

  private def createPocQuery(pocAdmin: PocAdmin): Quoted[Insert[PocAdmin]] =
    quote {
      querySchema[PocAdmin]("poc_manager.poc_admin_table").insert(lift(pocAdmin))
    }

  def createPoc(pocAdmin: PocAdmin): Task[UUID] =
    Task(run(createPocQuery(pocAdmin))).map(_ => pocAdmin.id)
}
