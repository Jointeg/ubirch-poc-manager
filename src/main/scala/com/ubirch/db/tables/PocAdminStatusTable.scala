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

  def updateWebIdentSuccess(pocAdminId: UUID, webIdentSuccess: Boolean): Task[Unit]

  def updateWebIdentInitiated(pocAdminId: UUID, webIdentInitiated: Boolean): Task[Unit]
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

  private def updateStatusQuery(pocAdminStatus: PocAdminStatus): Quoted[Update[PocAdminStatus]] =
    quote {
      querySchema[PocAdminStatus]("poc_manager.poc_admin_status_table")
        .filter(status => status.pocAdminId == lift(pocAdminStatus.pocAdminId)).update(lift(pocAdminStatus))
    }

  def createStatus(pocAdminStatus: PocAdminStatus): Task[Unit] =
    run(createPocAdminStatusQuery(pocAdminStatus)).void

  private def updateWebIdentIdSuccessQuery(pocAdminId: UUID, webIdentSuccess: Boolean): Quoted[Update[PocAdminStatus]] =
    quote {
      querySchema[PocAdminStatus]("poc_manager.poc_admin_status_table").filter(_.pocAdminId == lift(pocAdminId)).update(
        _.webIdentSuccess -> lift(Option(webIdentSuccess)))
    }

  private def updateWebIdentInitiatedQuery(
    pocAdminId: UUID,
    webIdentInitiated: Boolean): Quoted[Update[PocAdminStatus]] =
    quote {
      querySchema[PocAdminStatus]("poc_manager.poc_admin_status_table").filter(_.pocAdminId == lift(pocAdminId)).update(
        _.webIdentInitiated -> lift(Option(webIdentInitiated)))
    }

  def getStatus(pocAdminId: UUID): Task[Option[PocAdminStatus]] =
    run(getStatusQuery(pocAdminId)).map(_.headOption)

  def updateStatus(pocAdminStatus: PocAdminStatus): Task[Unit] = run(updateStatusQuery(pocAdminStatus)).void

  def updateWebIdentSuccess(
    pocAdminId: UUID,
    webIdentSuccess: Boolean): Task[Unit] = run(updateWebIdentIdSuccessQuery(pocAdminId, webIdentSuccess)).void

  def updateWebIdentInitiated(
    pocAdminId: UUID,
    webIdentInitiated: Boolean): Task[Unit] = run(updateWebIdentInitiatedQuery(pocAdminId, webIdentInitiated)).void
}
