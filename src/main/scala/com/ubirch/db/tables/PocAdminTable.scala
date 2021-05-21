package com.ubirch.db.tables

import com.ubirch.db.context.QuillMonixJdbcContext
import com.ubirch.db.tables.model.{ Criteria, PaginatedResult }
import com.ubirch.models.common
import com.ubirch.models.common.Sort
import com.ubirch.models.poc.{ Poc, PocAdmin, Status }
import com.ubirch.models.tenant.TenantId
import io.getquill.{ EntityQuery, Insert, Ord, Query }
import monix.eval.Task

import java.util.UUID
import javax.inject.Inject

trait PocAdminRepository {
  def createPocAdmin(pocAdmin: PocAdmin): Task[UUID]

  def getPocAdmin(pocAdminId: UUID): Task[Option[PocAdmin]]

  def getAllPocAdminsByTenantId(tenantId: TenantId): Task[List[PocAdmin]]

  def getAllByCriteria(criteria: Criteria): Task[PaginatedResult[(PocAdmin, Poc)]]
}

class PocAdminTable @Inject() (QuillMonixJdbcContext: QuillMonixJdbcContext) extends PocAdminRepository {
  import QuillMonixJdbcContext.ctx._

  private def createPocAdminQuery(pocAdmin: PocAdmin): Quoted[Insert[PocAdmin]] =
    quote {
      querySchema[PocAdmin]("poc_manager.poc_admin_table").insert(lift(pocAdmin))
    }

  private def getPocAdminQuery(pocAdminId: UUID): Quoted[EntityQuery[PocAdmin]] =
    quote {
      querySchema[PocAdmin]("poc_manager.poc_admin_table").filter(_.id == lift(pocAdminId))
    }

  private def getAllPocAdminsByTenantIdQuery(tenantId: TenantId): Quoted[EntityQuery[PocAdmin]] =
    quote {
      querySchema[PocAdmin]("poc_manager.poc_admin_table").filter(_.tenantId == lift(tenantId))
    }

  def createPocAdmin(pocAdmin: PocAdmin): Task[UUID] =
    run(createPocAdminQuery(pocAdmin)).map(_ => pocAdmin.id)

  def getPocAdmin(pocAdminId: UUID): Task[Option[PocAdmin]] =
    run(getPocAdminQuery(pocAdminId)).map(_.headOption)

  def getAllPocAdminsByTenantId(tenantId: TenantId): Task[List[PocAdmin]] = {
    run(getAllPocAdminsByTenantIdQuery(tenantId))
  }

  def getAllByCriteria(criteria: Criteria): Task[PaginatedResult[(PocAdmin, Poc)]] =
    transaction {
      val pocsByCriteria = filterByStatuses(getAllByCriteriaQuery(criteria), criteria.filter.status)
      val sorted = sortedPocAdmins(pocsByCriteria, criteria.sort)
      for {
        total <- run(pocsByCriteria.size)
        pocs <- run {
          sorted
            .drop(quote(lift(criteria.page.index * criteria.page.size)))
            .take(quote(lift(criteria.page.size)))
        }
      } yield {
        PaginatedResult(total, pocs)
      }
    }

  private def getAllByCriteriaQuery(criteria: Criteria) = {
    val pocByTenantId = quote {
      querySchema[PocAdmin]("poc_manager.poc_admin_table")
        .filter(_.tenantId == lift(criteria.tenantId))
    }

    criteria.search match {
      case Some(s) =>
        quote {
          pocByTenantId
            .filter(_.email.like(lift(s"$s%")))
        }
      case None => pocByTenantId
    }
  }

  private def sortedPocAdmins(q: Quoted[Query[PocAdmin]], sort: Sort) = {
    def ord[T]: Ord[T] = sort.order match {
      case common.ASC  => Ord.asc[T]
      case common.DESC => Ord.desc[T]
    }
    val dynamic =
      quote(q.join(querySchema[Poc]("poc_manager.poc_table")).on { case (pa, p) => pa.pocId == p.id }).dynamic
    sort.field match {
      case Some("name")    => dynamic.sortBy(r => quote(r._1.name))(ord)
      case Some("pocName") => dynamic.sortBy(r => quote(r._2.pocName))(ord)
      case _               => dynamic
    }
  }

  private def filterByStatuses(q: Quoted[Query[PocAdmin]], statuses: Seq[Status]) =
    statuses match {
      case Nil => quote(q)
      case _   => quote(q.filter(p => liftQuery(statuses).contains(p.status)))
    }

}
