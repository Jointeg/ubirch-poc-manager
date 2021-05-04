package com.ubirch.db.tables

import com.ubirch.db.context.QuillJdbcContext
import com.ubirch.models.poc.PocAdminStatus
import io.getquill.Insert
import monix.eval.Task

import java.util.UUID
import javax.inject.Inject

trait PocAdminStatusRepository {
  def createPocAdminStatus(pocAdminStatus: PocAdminStatus): Task[UUID]
}

class PocAdminStatusTable @Inject() (quillJdbcContext: QuillJdbcContext) extends PocAdminStatusRepository {
  import quillJdbcContext.ctx._

  private def createPocAdminStatusQuery(pocAdminStatus: PocAdminStatus): Quoted[Insert[PocAdminStatus]] =
    quote {
      querySchema[PocAdminStatus]("poc_maanger.poc_admin_status_table").insert(lift(pocAdminStatus))
    }

  def createPocAdminStatus(pocAdminStatus: PocAdminStatus): Task[UUID] =
    Task(run(createPocAdminStatusQuery(pocAdminStatus))).map(_ => pocAdminStatus.pocAdminId)
}
