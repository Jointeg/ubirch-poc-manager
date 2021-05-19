package com.ubirch.db.tables

import com.ubirch.db.context.QuillMonixJdbcContext
import com.ubirch.models.poc.PocAdmin
import com.ubirch.models.tenant.TenantId
import io.getquill.{ EntityQuery, Insert }
import monix.eval.Task

import java.util.UUID
import javax.inject.Inject

trait PocAdminRepository {
  def createPocAdmin(pocAdmin: PocAdmin): Task[UUID]

  def getPocAdmin(pocAdminId: UUID): Task[Option[PocAdmin]]

  def getAllPocAdminsByTenantId(tenantId: TenantId): Task[List[PocAdmin]]

  def assignWebInitiateId(pocAdminId: UUID, webInitiateId: UUID): Task[Unit]
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

  private def updateWebInitiateId(pocAdminId: UUID, webInitiateId: UUID) =
    quote {
      querySchema[PocAdmin]("poc_manager.poc_admin_table").filter(_.id == lift(pocAdminId)).update(
        _.webInitiateId -> lift(Option(webInitiateId))
      )
    }

  def createPocAdmin(pocAdmin: PocAdmin): Task[UUID] =
    run(createPocAdminQuery(pocAdmin)).map(_ => pocAdmin.id)

  def getPocAdmin(pocAdminId: UUID): Task[Option[PocAdmin]] =
    run(getPocAdminQuery(pocAdminId)).map(_.headOption)

  def getAllPocAdminsByTenantId(tenantId: TenantId): Task[List[PocAdmin]] = {
    run(getAllPocAdminsByTenantIdQuery(tenantId))
  }
  override def assignWebInitiateId(pocAdminId: UUID, webInitiateId: UUID): Task[Unit] = {
    run(updateWebInitiateId(pocAdminId, webInitiateId)).void
  }
}
