package com.ubirch.db.tables

import com.ubirch.db.context.QuillMonixJdbcContext
import com.ubirch.db.tables.model.{ Criteria, PaginatedResult }
import com.ubirch.models.common.Sort
import com.ubirch.models.poc.{ Completed, Poc, PocAdmin, Status }
import com.ubirch.models.tenant.TenantId
import io.getquill.{ EntityQuery, Insert, Query }
import monix.eval.Task

import java.util.UUID
import javax.inject.Inject

trait PocAdminRepository {
  def createPocAdmin(pocAdmin: PocAdmin): Task[UUID]

  def updatePocAdmin(pocAdmin: PocAdmin): Task[UUID]

  def getPocAdmin(pocAdminId: UUID): Task[Option[PocAdmin]]

  def getAllPocAdminsByTenantId(tenantId: TenantId): Task[List[PocAdmin]]

  def getAllUncompletedPocAdminsIds(): Task[List[UUID]]

  def unsafeGetUncompletedPocAdminById(id: UUID): Task[PocAdmin]

  def updateWebIdentIdAndStatus(webIdentId: String, pocAdminId: UUID): Task[Unit]

  def getAllByCriteria(criteria: Criteria): Task[PaginatedResult[(PocAdmin, Poc)]]

  def assignWebIdentInitiateId(pocAdminId: UUID, webIdentInitiateId: UUID): Task[Unit]

  def getByCertifyUserId(certifyUserId: UUID): Task[Option[PocAdmin]]
}

class PocAdminTable @Inject() (QuillMonixJdbcContext: QuillMonixJdbcContext, pocAdminStatusTable: PocAdminStatusTable)
  extends PocAdminRepository {
  import QuillMonixJdbcContext._
  import ctx._

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

  private def getAllPocAdminsIdsWithoutStatusQuery(status: Status) =
    quote {
      querySchema[PocAdmin]("poc_manager.poc_admin_table").filter(_.status != lift(status)).map(_.id)
    }

  private def getPocAdminWithoutStatusById(status: Status, id: UUID) =
    quote {
      querySchema[PocAdmin]("poc_manager.poc_admin_table").filter(admin =>
        admin.status != lift(status) && admin.id == lift(id))
    }

  private def updatePocAdminQuery(pocAdmin: PocAdmin) =
    quote {
      querySchema[PocAdmin]("poc_manager.poc_admin_table").filter(_.id == lift(pocAdmin.id)).update(lift(pocAdmin))
    }

  private def updateWebInitiateId(pocAdminId: UUID, webInitiateId: UUID) =
    quote {
      querySchema[PocAdmin]("poc_manager.poc_admin_table").filter(_.id == lift(pocAdminId)).update(
        _.webIdentInitiateId -> lift(Option(webInitiateId))
      )
    }

  private def updateWebIdentIdQuery(webIdentId: String, pocAdminId: UUID) =
    quote {
      querySchema[PocAdmin]("poc_manager.poc_admin_table").filter(_.id == lift(pocAdminId)).update(
        _.webIdentId -> lift(Option(webIdentId))
      )
    }

  private def getByCertifyUserIdQuery(certifyUserId: UUID) =
    quote {
      querySchema[PocAdmin]("poc_manager.poc_admin_table").filter(_.certifyUserId == lift(Option(certifyUserId)))
    }

  def createPocAdmin(pocAdmin: PocAdmin): Task[UUID] =
    run(createPocAdminQuery(pocAdmin)).map(_ => pocAdmin.id)

  def getPocAdmin(pocAdminId: UUID): Task[Option[PocAdmin]] =
    run(getPocAdminQuery(pocAdminId)).map(_.headOption)

  def getAllPocAdminsByTenantId(tenantId: TenantId): Task[List[PocAdmin]] = {
    run(getAllPocAdminsByTenantIdQuery(tenantId))
  }

  def updatePocAdmin(pocAdmin: PocAdmin): Task[UUID] = run(updatePocAdminQuery(pocAdmin)).map(_ => pocAdmin.id)

  def assignWebIdentInitiateId(pocAdminId: UUID, webIdentInitiateId: UUID): Task[Unit] = {
    transaction {
      for {
        _ <- run(updateWebInitiateId(pocAdminId, webIdentInitiateId)).void
        _ <- pocAdminStatusTable.updateWebIdentInitiated(pocAdminId, webIdentInitiated = true)
      } yield ()
    }
  }

  def updateWebIdentIdAndStatus(webIdentId: String, pocAdminId: UUID): Task[Unit] =
    transaction {
      for {
        _ <- run(updateWebIdentIdQuery(webIdentId, pocAdminId)).void
        _ <- pocAdminStatusTable.updateWebIdentSuccess(pocAdminId, webIdentSuccess = true)
      } yield ()
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
            .filter(pocAdmin =>
              pocAdmin.email.like(lift(s"$s%")) || pocAdmin.name.like(lift(s"$s%")) || pocAdmin.surname.like(
                lift(s"$s%")))
        }
      case None => pocByTenantId
    }
  }

  private def sortedPocAdmins(q: Quoted[Query[PocAdmin]], sort: Sort) = {
    val dynamic =
      quote(q.join(querySchema[Poc]("poc_manager.poc_table")).on { case (pa, p) => pa.pocId == p.id }).dynamic
    sort.field match {
      case Some("id")        => dynamic.sortBy(r => quote(r._1.id))(sort.ord)
      case Some("lastName")  => dynamic.sortBy(r => quote(r._1.surname))(sort.ord)
      case Some("firstName") => dynamic.sortBy(r => quote(r._1.name))(sort.ord)
      case Some("email")     => dynamic.sortBy(r => quote(r._1.email))(sort.ord)
      case Some("active")    => dynamic.sortBy(r => quote(r._1.active))(sort.ord)
      case Some("state")     => dynamic.sortBy(r => quote(r._1.status))(sort.ord)
      case Some("pocName")   => dynamic.sortBy(r => quote(r._2.pocName))(sort.ord)
      case Some("createdAt") => dynamic.sortBy(r => quote(r._1.created))(sort.ord)
      case _                 => dynamic
    }
  }

  private def filterByStatuses(q: Quoted[Query[PocAdmin]], statuses: Seq[Status]) =
    statuses match {
      case Nil => quote(q)
      case _   => quote(q.filter(p => liftQuery(statuses).contains(p.status)))
    }

  override def getByCertifyUserId(certifyUserId: UUID): Task[Option[PocAdmin]] =
    run(getByCertifyUserIdQuery(certifyUserId)).map(_.headOption)
  override def getAllUncompletedPocAdminsIds(): Task[List[UUID]] = run(getAllPocAdminsIdsWithoutStatusQuery(Completed))
  override def unsafeGetUncompletedPocAdminById(id: UUID): Task[PocAdmin] =
    run(getPocAdminWithoutStatusById(Completed, id)).map(_.head)
}
