package com.ubirch.db.tables

import com.google.inject.Inject
import com.ubirch.db.context.QuillJdbcContext
import com.ubirch.db.tables.PocRepository.{ PaginatedPocs, PocCriteria }
import com.ubirch.models.common.{ Page, Sort }
import com.ubirch.models.poc.{ Completed, Poc, PocStatus, Status }
import io.getquill.{ Insert, Query, Update }
import monix.eval.Task

import java.util.UUID

trait PocRepository {
  def createPoc(poc: Poc): Task[UUID]

  def createPocAndStatus(poc: Poc, pocStatus: PocStatus): Task[Long]

  def updatePocAndStatus(poc: Poc, pocStatus: PocStatus): Task[Long]

  def updatePoc(poc: Poc): Task[Unit]

  def deletePoc(pocId: UUID): Task[Unit]

  def getPoc(pocId: UUID): Task[Option[Poc]]

  def getAllPocsByTenantId(tenantId: UUID): Task[List[Poc]]

  def getAllPocsByCriteria(pocCriteria: PocCriteria): Task[PaginatedPocs]

  def getAllUncompletedPocs(): Task[List[Poc]]
}

object PocRepository {
  case class PocCriteria(tenantId: UUID, page: Page, sort: Sort)
  case class PaginatedPocs(total: Long, pocs: Seq[Poc])
}

class PocTable @Inject() (quillJdbcContext: QuillJdbcContext) extends PocRepository {

  import quillJdbcContext.ctx._

  private def createPocQuery(poc: Poc): Quoted[Insert[Poc]] =
    quote {
      querySchema[Poc]("poc_manager.poc_table").insert(lift(poc))
    }

  private def createPocStatusQuery(pocStatus: PocStatus): Quoted[Insert[PocStatus]] =
    quote {
      querySchema[PocStatus]("poc_manager.poc_status_table").insert(lift(pocStatus))
    }

  private def updatePocQuery(poc: Poc) =
    quote {
      querySchema[Poc]("poc_manager.poc_table").filter(_.id == lift(poc.id)).update(lift(poc))
    }

  private def updatePocStatusQuery(pocStatus: PocStatus): Quoted[Update[PocStatus]] =
    quote {
      querySchema[PocStatus]("poc_manager.poc_status_table")
        .filter(_.pocId == lift(pocStatus.pocId))
        .update(lift(pocStatus))
    }

  private def removePocQuery(pocId: UUID) =
    quote {
      querySchema[Poc]("poc_manager.poc_table").filter(_.id == lift(pocId)).delete
    }

  private def getPocQuery(pocId: UUID) =
    quote {
      querySchema[Poc]("poc_manager.poc_table").filter(_.id == lift(pocId))
    }

  private def getAllPocsByTenantIdQuery(tenantId: UUID) =
    quote {
      querySchema[Poc]("poc_manager.poc_table").filter(_.tenantId == lift(tenantId))
    }

  private def getAllPocsWithoutStatusQuery(status: Status) =
    quote {
      querySchema[Poc]("poc_manager.poc_table").filter(_.status != lift(status))
    }

  private def countAllPocsByCriteriaQuery(page: Int, size: Int): Quoted[Query[Poc]] =
    quote {
      querySchema[Poc]("poc_manager.poc_table")
        .drop(liftScalar(page))
        .take(liftScalar(size))
    }

  private def getAllPocsByCriteriaQuery(criteria: PocCriteria): Quoted[Query[Poc]] =
    quote {
      querySchema[Poc]("poc_manager.poc_table")
        .drop(liftScalar(criteria.page.index * criteria.page.size))
        .take(liftScalar(criteria.page.size))
    }

  override def createPoc(poc: Poc): Task[UUID] = Task(run(createPocQuery(poc))).map(_ => poc.id)

  override def createPocAndStatus(poc: Poc, pocStatus: PocStatus): Task[Long] =
    Task {
      transaction {
        run(createPocQuery(poc))
        run(createPocStatusQuery(pocStatus))
      }
    }

  def updatePocAndStatus(poc: Poc, pocStatus: PocStatus): Task[Long] =
    Task {
      transaction {
        run(updatePocQuery(poc))
        run(updatePocStatusQuery(pocStatus))
      }
    }

  override def updatePoc(poc: Poc): Task[Unit] = Task(run(updatePocQuery(poc)))

  override def deletePoc(pocId: UUID): Task[Unit] = Task(run(removePocQuery(pocId)))

  override def getPoc(pocId: UUID): Task[Option[Poc]] = Task(run(getPocQuery(pocId))).map(_.headOption)

  override def getAllPocsByTenantId(tenantId: UUID): Task[List[Poc]] =
    Task(run(getAllPocsByTenantIdQuery(tenantId: UUID)))

  override def getAllPocsByCriteria(pocCriteria: PocCriteria): Task[PaginatedPocs] =
    Task {
      transaction {
        val total =
          run(countAllPocsByCriteriaQuery(pocCriteria.page.index * pocCriteria.page.size, pocCriteria.page.size))
        val pocs = run(getAllPocsByCriteriaQuery(pocCriteria))
        PaginatedPocs(total.size, pocs)
      }
    }

  def getAllUncompletedPocs(): Task[List[Poc]] = Task(run(getAllPocsWithoutStatusQuery(Completed)))
}
