package com.ubirch.db.tables

import com.ubirch.db.context.QuillJdbcContext
import com.ubirch.models.poc.PocAdminStatus
import io.getquill.{ EntityQuery, Insert }
import monix.eval.Task

import java.util.UUID
import javax.inject.Inject

trait PocAdminStatusRepository {
  def createStatus(pocAdminStatus: PocAdminStatus): Task[UUID]

  def getStatus(pocAdminId: UUID): Task[Option[PocAdminStatus]]
}

class PocAdminStatusTable @Inject() (quillJdbcContext: QuillJdbcContext) extends PocAdminStatusRepository {
  import quillJdbcContext.ctx._

  private def createPocAdminStatusQuery(pocAdminStatus: PocAdminStatus): Quoted[Insert[PocAdminStatus]] =
    quote {
      querySchema[PocAdminStatus]("poc_manager.poc_admin_status_table").insert(lift(pocAdminStatus))
    }

  private def getStatusQuery(pocAdminId: UUID): Quoted[EntityQuery[PocAdminStatus]] =
    quote {
      querySchema[PocAdminStatus]("poc_manager.poc_admin_status_table").filter(_.pocAdminId == lift(pocAdminId))
    }

  def createStatus(pocAdminStatus: PocAdminStatus): Task[UUID] =
    Task(run(createPocAdminStatusQuery(pocAdminStatus))).map(_ => pocAdminStatus.pocAdminId)

  def getStatus(pocAdminId: UUID): Task[Option[PocAdminStatus]] =
    Task(run(getStatusQuery(pocAdminId))).map(_.headOption)
}
