package com.ubirch.db.tables

import com.google.inject.Inject
import com.ubirch.db.context.QuillJdbcContext
import com.ubirch.models.poc.PocStatus
import io.getquill.{Delete, EntityQuery, Insert, Update}
import monix.eval.Task

import java.util.UUID

trait PocStatusRepository {
  def createPocStatus(pocStatus: PocStatus): Task[Long]

  def updatePocStatus(pocStatus: PocStatus): Task[Long]

  def deletePocStatus(pocId: UUID): Task[Long]

  def getPocStatus(pocId: UUID): Task[Option[PocStatus]]
}

class PocStatusTable @Inject()(quillJdbcContext: QuillJdbcContext) extends PocStatusRepository {

  import quillJdbcContext.ctx._

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

  override def createPocStatus(pocStatus: PocStatus): Task[Long] = Task(run(createPocStatusQuery(pocStatus)))

  override def updatePocStatus(pocStatus: PocStatus): Task[Long] = Task(run(updatePocStatusQuery(pocStatus)))

  override def deletePocStatus(pocId: UUID): Task[Long] = Task(run(removePocStatusQuery(pocId)))

  override def getPocStatus(pocId: UUID): Task[Option[PocStatus]] = Task(run(getPocStatusQuery(pocId))).map(_.headOption)
}
