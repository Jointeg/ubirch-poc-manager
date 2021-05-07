package com.ubirch.db.tables

import com.google.inject.Inject
import com.ubirch.db.context.QuillJdbcContext
import com.ubirch.db.tables.PocRepository.{ PaginatedPocs, PocCriteria }
import com.ubirch.models.common
import com.ubirch.models.common.{ Page, Sort }
import com.ubirch.models.poc.{ Completed, Poc, PocStatus, Status }
import io.getquill.{ Insert, Ord, Query, Update }
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
  case class PocCriteria(tenantId: UUID, page: Page, sort: Sort, search: Option[String])

  case class PaginatedPocs(total: Long, pocs: Seq[Poc])
}

class PocTable @Inject() (quillJdbcContext: QuillJdbcContext) extends PocRepository {

  import quillJdbcContext.ctx._

  override def createPoc(poc: Poc): Task[UUID] = Task(run(createPocQuery(poc))).map(_ => poc.id)

  private def createPocQuery(poc: Poc): Quoted[Insert[Poc]] =
    quote {
      querySchema[Poc]("poc_manager.poc_table").insert(lift(poc))
    }

  override def createPocAndStatus(poc: Poc, pocStatus: PocStatus): Task[Long] =
    Task {
      transaction {
        run(createPocQuery(poc))
        run(createPocStatusQuery(pocStatus))
      }
    }

  private def createPocStatusQuery(pocStatus: PocStatus): Quoted[Insert[PocStatus]] =
    quote {
      querySchema[PocStatus]("poc_manager.poc_status_table").insert(lift(pocStatus))
    }

  def updatePocAndStatus(poc: Poc, pocStatus: PocStatus): Task[Long] =
    Task {
      transaction {
        run(updatePocQuery(poc))
        run(updatePocStatusQuery(pocStatus))
      }
    }

  private def updatePocStatusQuery(pocStatus: PocStatus): Quoted[Update[PocStatus]] =
    quote {
      querySchema[PocStatus]("poc_manager.poc_status_table")
        .filter(_.pocId == lift(pocStatus.pocId))
        .update(lift(pocStatus))
    }

  private def updatePocQuery(poc: Poc) =
    quote {
      querySchema[Poc]("poc_manager.poc_table").filter(_.id == lift(poc.id)).update(lift(poc))
    }

  override def updatePoc(poc: Poc): Task[Unit] = Task(run(updatePocQuery(poc)))

  override def deletePoc(pocId: UUID): Task[Unit] = Task(run(removePocQuery(pocId)))

  private def removePocQuery(pocId: UUID) =
    quote {
      querySchema[Poc]("poc_manager.poc_table").filter(_.id == lift(pocId)).delete
    }

  override def getPoc(pocId: UUID): Task[Option[Poc]] = Task(run(getPocQuery(pocId))).map(_.headOption)

  private def getPocQuery(pocId: UUID) =
    quote {
      querySchema[Poc]("poc_manager.poc_table").filter(_.id == lift(pocId))
    }

  override def getAllPocsByTenantId(tenantId: UUID): Task[List[Poc]] =
    Task(run(getAllPocsByTenantIdQuery(tenantId: UUID)))

  private def getAllPocsByTenantIdQuery(tenantId: UUID) =
    quote {
      querySchema[Poc]("poc_manager.poc_table").filter(_.tenantId == lift(tenantId))
    }

  override def getAllPocsByCriteria(pocCriteria: PocCriteria): Task[PaginatedPocs] =
    Task {
      transaction {
        val pocsByCriteria = getAllPocsByCriteriaQuery(pocCriteria)
        val sortedPocs = sortPocs(pocsByCriteria, pocCriteria.sort)
        val total = run(pocsByCriteria.size)
        val pocs = run {
          sortedPocs
            .drop(quote(lift(pocCriteria.page.index * pocCriteria.page.size)))
            .take(quote(lift(pocCriteria.page.size)))
        }
        PaginatedPocs(total, pocs)
      }
    }

  private def getAllPocsByCriteriaQuery(criteria: PocCriteria) = {
    val pocByTenantId = quote {
      querySchema[Poc]("poc_manager.poc_table")
        .filter(_.tenantId == lift(criteria.tenantId))
    }

    criteria.search match {
      case Some(s) =>
        quote {
          pocByTenantId
            .filter(_.pocName.like(lift(s"$s%")))
        }
      case None => pocByTenantId
    }
  }

  private def sortPocs(q: Quoted[Query[Poc]], sort: Sort) = {
    def ord[T]: Ord[T] = sort.order match {
      case common.ASC  => Ord.asc[T]
      case common.DESC => Ord.desc[T]
    }
    sort.field match {
      case Some("id")                 => q.dynamic.sortBy(p => quote(p.id))(ord)
      case Some("tenantId")           => q.dynamic.sortBy(p => quote(p.tenantId))(ord)
      case Some("externalId")         => q.dynamic.sortBy(p => quote(p.externalId))(ord)
      case Some("pocName")            => q.dynamic.sortBy(p => quote(p.pocName))(ord)
      case Some("phone")              => q.dynamic.sortBy(p => quote(p.phone))(ord)
      case Some("certifyApp")         => q.dynamic.sortBy(p => quote(p.certifyApp))(ord)
      case Some("clientCertRequired") => q.dynamic.sortBy(p => quote(p.clientCertRequired))(ord)
      case Some("dataSchemaId")       => q.dynamic.sortBy(p => quote(p.dataSchemaId))(ord)
      case Some("roleAndGroupName")   => q.dynamic.sortBy(p => quote(p.roleAndGroupName))(ord)
      case Some("groupPath")          => q.dynamic.sortBy(p => quote(p.groupPath))(ord)
      case Some("deviceId")           => q.dynamic.sortBy(p => quote(p.deviceId))(ord)
      case Some("clientCertFolder")   => q.dynamic.sortBy(p => quote(p.clientCertFolder))(ord)
      case Some("status")             => q.dynamic.sortBy(p => quote(p.status))(ord)
      case Some("lastUpdated")        => q.dynamic.sortBy(p => quote(p.lastUpdated))(ord)
      case Some("created")            => q.dynamic.sortBy(p => quote(p.created))(ord)
      case _                          => q.dynamic
    }
  }

  def getAllUncompletedPocs(): Task[List[Poc]] = Task(run(getAllPocsWithoutStatusQuery(Completed)))

  private def getAllPocsWithoutStatusQuery(status: Status) =
    quote {
      querySchema[Poc]("poc_manager.poc_table").filter(_.status != lift(status))
    }
}
