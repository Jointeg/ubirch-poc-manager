package com.ubirch.db.tables

import com.google.inject.Inject
import com.ubirch.db.context.QuillMonixJdbcContext
import com.ubirch.models.poc.PocStatus
import io.getquill.{ Delete, EntityQuery, Insert, Update }
import monix.eval.Task

import java.util.UUID

trait PocStatusRepository {
  def createPocStatus(pocStatus: PocStatus): Task[Unit]

  def updatePocStatus(pocStatus: PocStatus): Task[Unit]

  def deletePocStatus(pocId: UUID): Task[Unit]

  def getPocStatus(pocId: UUID): Task[Option[PocStatus]]
}

class PocStatusTable @Inject() (QuillMonixJdbcContext: QuillMonixJdbcContext) extends PocStatusRepository {

  import QuillMonixJdbcContext.ctx._

  private def createPocStatusQuery(pocStatus: PocStatus): Quoted[Insert[PocStatus]] =
    quote {
      querySchema[PocStatus]("poc_manager.poc_status_table").insert(lift(pocStatus))
    }

  private def updatePocStatusQuery(pocStatus: PocStatus): Quoted[Update[PocStatus]] =
    quote {
      querySchema[PocStatus]("poc_manager.poc_status_table")
        .filter(_.pocId == lift(pocStatus.pocId))
        .update(lift(pocStatus))
    }

  private def removePocStatusQuery(pocStatusId: UUID): Quoted[Delete[PocStatus]] =
    quote {
      querySchema[PocStatus]("poc_manager.poc_status_table").filter(_.pocId == lift(pocStatusId)).delete
    }

  private def getPocStatusQuery(pocStatusId: UUID): Quoted[EntityQuery[PocStatus]] =
    quote {
      querySchema[PocStatus]("poc_manager.poc_status_table").filter(_.pocId == lift(pocStatusId))
    }

  override def createPocStatus(pocStatus: PocStatus): Task[Unit] = run(createPocStatusQuery(pocStatus)).void

  override def updatePocStatus(pocStatus: PocStatus): Task[Unit] = run(updatePocStatusQuery(pocStatus)).void

  override def deletePocStatus(pocId: UUID): Task[Unit] = run(removePocStatusQuery(pocId)).void

  override def getPocStatus(pocId: UUID): Task[Option[PocStatus]] =
    run(getPocStatusQuery(pocId)).map(_.headOption)
}
