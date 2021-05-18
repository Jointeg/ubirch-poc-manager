package com.ubirch.db.tables

import com.ubirch.db.context.QuillMonixJdbcContext
import com.ubirch.models.poc.PocAdminStatus
import io.getquill.{ EntityQuery, Insert, Update }
import monix.eval.Task

import java.util.UUID
import javax.inject.Inject

trait PocAdminStatusRepository {
  def createStatus(pocAdminStatus: PocAdminStatus): Task[Unit]

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

  private def updatePocAdminStatusQuery(pocAdminStatus: PocAdminStatus): Quoted[Update[PocAdminStatus]] =
    quote {
      querySchema[PocAdminStatus]("poc_manager.poc_admin_status_table").filter(
        _.pocAdminId == lift(pocAdminStatus.pocAdminId)).update(lift(pocAdminStatus))
    }

  def createStatus(pocAdminStatus: PocAdminStatus): Task[Unit] =
    run(createPocAdminStatusQuery(pocAdminStatus)).void

  def getStatus(pocAdminId: UUID): Task[Option[PocAdminStatus]] =
    run(getStatusQuery(pocAdminId)).map(_.headOption)

  def updateStatus(pocAdminStatus: PocAdminStatus): Task[Unit] =
    run(updatePocAdminStatusQuery(pocAdminStatus)).void
}
