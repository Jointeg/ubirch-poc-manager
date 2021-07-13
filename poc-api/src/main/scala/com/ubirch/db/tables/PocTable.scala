package com.ubirch.db.tables

import com.google.inject.Inject
import com.ubirch.db.context.QuillMonixJdbcContext
import com.ubirch.db.tables.model.{ Criteria, PaginatedResult }
import com.ubirch.models.common.Sort
import com.ubirch.models.poc._
import com.ubirch.models.tenant.TenantId
import io.getquill.{ Insert, Query }
import monix.eval.Task

import java.util.UUID
import javax.inject.Singleton

trait PocRepository {
  def createPoc(poc: Poc): Task[UUID]

  def createPocAndStatus(poc: Poc, pocStatus: PocStatus): Task[Unit]

  def updatePoc(poc: Poc): Task[UUID]

  def deletePoc(pocId: UUID): Task[Unit]

  def getPoc(pocId: UUID): Task[Option[Poc]]

  def single(id: UUID): Task[Poc]

  def getAllPocsByTenantId(tenantId: TenantId): Task[List[Poc]]

  def getAllPocsByCriteria(criteria: Criteria): Task[PaginatedResult[Poc]]

  def getAllUncompletedPocsIds(): Task[List[UUID]]

  def unsafeGetUncompletedPocByIds(id: UUID): Task[Poc]

  def getPoCsSimplifiedDeviceInfoByTenant(tenantId: TenantId): Task[List[SimplifiedDeviceInfo]]

  def incrementCreationAttempt(id: UUID): Task[Unit]
}

object PocRepository {
  case class PocNotFound(id: UUID) extends RuntimeException(s"Poc with id '$id' does not exist")
}

@Singleton
class PocTable @Inject() (QuillMonixJdbcContext: QuillMonixJdbcContext) extends PocRepository {

  import QuillMonixJdbcContext.ctx._

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

  private def removePocQuery(pocId: UUID) =
    quote {
      querySchema[Poc]("poc_manager.poc_table").filter(_.id == lift(pocId)).delete
    }

  private def getPocQuery(pocId: UUID) =
    quote {
      querySchema[Poc]("poc_manager.poc_table").filter(_.id == lift(pocId))
    }

  private def getAllPocsByTenantIdQuery(tenantId: TenantId) =
    quote {
      querySchema[Poc]("poc_manager.poc_table").filter(_.tenantId == lift(tenantId))
    }

  private def getPoCsSimplifiedDeviceInfoByTenantQuery(tenantId: TenantId) =
    quote {
      querySchema[Poc]("poc_manager.poc_table").filter(_.tenantId == lift(tenantId)).map(poc =>
        SimplifiedDeviceInfo(poc.externalId, poc.pocName, poc.deviceId))
    }

  private def getAllPocIdsWithoutStatusesQuery(statuses: Status*) =
    quote {
      querySchema[Poc]("poc_manager.poc_table").filter(poc => !liftQuery(statuses).contains(poc.status)).map(_.id)
    }

  private def getPocWithoutStatusesByIdQuery(id: UUID, statuses: Status*) =
    quote {
      querySchema[Poc]("poc_manager.poc_table").filter(poc =>
        !liftQuery(statuses).contains(poc.status) && poc.id == lift(id))
    }

  private def incrementCreationAttemptQuery(id: UUID) = quote {
    querySchema[Poc]("poc_manager.poc_table").filter(poc => poc.id == lift(id)).update(poc =>
      poc.creationAttempts -> (poc.creationAttempts + 1))
  }

  override def createPoc(poc: Poc): Task[UUID] = run(createPocQuery(poc)).map(_ => poc.id)

  override def createPocAndStatus(poc: Poc, pocStatus: PocStatus): Task[Unit] =
    transaction {
      for {
        _ <- run(createPocQuery(poc))
        _ <- run(createPocStatusQuery(pocStatus))
      } yield {
        ()
      }
    }

  override def updatePoc(poc: Poc): Task[UUID] = run(updatePocQuery(poc)).map(_ => poc.id)

  override def deletePoc(pocId: UUID): Task[Unit] = run(removePocQuery(pocId)).void

  override def getPoc(pocId: UUID): Task[Option[Poc]] = run(getPocQuery(pocId)).map(_.headOption)

  override def single(id: UUID): Task[Poc] =
    getPoc(id).flatMap {
      case Some(v) => Task.pure(v)
      case None    => Task.raiseError(PocRepository.PocNotFound(id))
    }

  override def getAllPocsByTenantId(tenantId: TenantId): Task[List[Poc]] =
    run(getAllPocsByTenantIdQuery(tenantId))

  override def getAllUncompletedPocsIds(): Task[List[UUID]] = run(getAllPocIdsWithoutStatusesQuery(Completed, Aborted))

  override def unsafeGetUncompletedPocByIds(id: UUID): Task[Poc] =
    run(getPocWithoutStatusesByIdQuery(id, Completed, Aborted)).map(_.head)

  override def getAllPocsByCriteria(pocCriteria: Criteria): Task[PaginatedResult[Poc]] =
    transaction {
      val pocsByCriteria = filterByStatuses(getAllPocsByCriteriaQuery(pocCriteria), pocCriteria.filter.status)
      val sortedPocs = sortPocs(pocsByCriteria, pocCriteria.sort)
      for {
        total <- run(pocsByCriteria.size)
        pocs <- run {
          sortedPocs
            .drop(quote(lift(pocCriteria.page.index * pocCriteria.page.size)))
            .take(quote(lift(pocCriteria.page.size)))
        }
      } yield {
        PaginatedResult(total, pocs)
      }
    }

  private def getAllPocsByCriteriaQuery(criteria: Criteria) = {
    val pocByTenantId = quote {
      querySchema[Poc]("poc_manager.poc_table")
        .filter(_.tenantId == lift(criteria.tenantId))
    }

    criteria.search match {
      case Some(s) =>
        val lowerCased = s.toLowerCase
        quote {
          pocByTenantId
            .filter(poc =>
              poc.pocName.toLowerCase.like(lift(s"%$lowerCased%")) || poc.address.city.toLowerCase.like(lift(
                s"%$lowerCased%")) || poc.externalId.like(
                lift(s"%$lowerCased%")))
        }
      case None => pocByTenantId
    }
  }

  private def sortPocs(q: Quoted[Query[Poc]], sort: Sort) = {
    val dynamic = q.dynamic
    sort.field match {
      case Some("id")          => dynamic.sortBy(p => quote(p.id))(sort.ord)
      case Some("externalId")  => dynamic.sortBy(p => quote(p.externalId))(sort.ord)
      case Some("pocName")     => dynamic.sortBy(p => quote(p.pocName))(sort.ord)
      case Some("status")      => dynamic.sortBy(p => quote(p.status))(sort.ord)
      case Some("lastUpdated") => dynamic.sortBy(p => quote(p.lastUpdated))(sort.ord)
      case Some("created")     => dynamic.sortBy(p => quote(p.created))(sort.ord)
      case _                   => dynamic
    }
  }

  private def filterByStatuses(q: Quoted[Query[Poc]], statuses: Seq[Status]) =
    statuses match {
      case Nil => quote(q)
      case _   => quote(q.filter(p => liftQuery(statuses).contains(p.status)))
    }

  def getPoCsSimplifiedDeviceInfoByTenant(tenantId: TenantId): Task[List[SimplifiedDeviceInfo]] =
    run(getPoCsSimplifiedDeviceInfoByTenantQuery(tenantId))

  override def incrementCreationAttempt(id: UUID): Task[Unit] = run(incrementCreationAttemptQuery(id)).void
}
