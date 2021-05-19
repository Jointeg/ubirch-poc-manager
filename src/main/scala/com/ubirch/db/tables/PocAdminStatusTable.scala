package com.ubirch.db.tables

import com.ubirch.db.context.QuillMonixJdbcContext
import com.ubirch.models.poc.PocAdminStatus
import io.getquill.{ EntityQuery, Insert }
import monix.eval.Task

import java.util.UUID
import javax.inject.Inject

trait PocAdminStatusRepository {
  def createStatus(pocAdminStatus: PocAdminStatus): Task[UUID]

  def getStatus(pocAdminId: UUID): Task[Option[PocAdminStatus]]

  def updateStatus(pocAdminStatus: PocAdminStatus): Task[Unit]
}

class PocAdminStatusTable @Inject() (QuillMonixJdbcContext: QuillMonixJdbcContext) extends PocAdminStatusRepository {
  import QuillMonixJdbcContext.ctx._

  private def createPocAdminStatusQuery(pocAdminStatus: PocAdminStatus): Quoted[Insert[PocAdminStatus]] =
    quote {
      querySchema[PocAdminStatus]("poc_manager.poc_admin_status_table").insert(lift(pocAdminStatus))
    }

  private def getStatusQuery(pocAdminId: UUID): Quoted[EntityQuery[PocAdminStatus]] =
    quote {
      querySchema[PocAdminStatus]("poc_manager.poc_admin_status_table").filter(_.pocAdminId == lift(pocAdminId))
    }

  private def updateStatusQuery(pocAdminStatus: PocAdminStatus) =
    quote {
      querySchema[PocAdminStatus]("poc_manager.poc_admin_status_table").update(lift(pocAdminStatus))
    }

  def createStatus(pocAdminStatus: PocAdminStatus): Task[UUID] =
    run(createPocAdminStatusQuery(pocAdminStatus)).map(_ => pocAdminStatus.pocAdminId)

  def getStatus(pocAdminId: UUID): Task[Option[PocAdminStatus]] =
    run(getStatusQuery(pocAdminId)).map(_.headOption)

  override def updateStatus(pocAdminStatus: PocAdminStatus): Task[Unit] =
    run(updateStatusQuery(pocAdminStatus)).void
}
