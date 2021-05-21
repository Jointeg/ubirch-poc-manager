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

  def updatePocAdmin(pocAdmin: PocAdmin): Task[Unit]

  def updateWebIdentIdAndStatus(webIdentId: UUID, pocAdminId: UUID): Task[Unit]

  def assignWebIdentInitiateId(pocAdminId: UUID, webIdentInitiateId: UUID): Task[Unit]
}

class PocAdminTable @Inject() (QuillMonixJdbcContext: QuillMonixJdbcContext, pocAdminStatusTable: PocAdminStatusTable)
  extends PocAdminRepository {
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

  private def updateWebIdentIdQuery(webIdentId: UUID, pocAdminId: UUID) =
    quote {
      querySchema[PocAdmin]("poc_manager.poc_admin_table").filter(_.id == lift(pocAdminId)).update(
        _.webIdentId -> lift(Option(webIdentId.toString))
      )
    }

  def createPocAdmin(pocAdmin: PocAdmin): Task[UUID] =
    run(createPocAdminQuery(pocAdmin)).map(_ => pocAdmin.id)

  def getPocAdmin(pocAdminId: UUID): Task[Option[PocAdmin]] =
    run(getPocAdminQuery(pocAdminId)).map(_.headOption)

  def getAllPocAdminsByTenantId(tenantId: TenantId): Task[List[PocAdmin]] = {
    run(getAllPocAdminsByTenantIdQuery(tenantId))
  }
  override def updatePocAdmin(pocAdmin: PocAdmin): Task[Unit] = run(updatePocAdminQuery(pocAdmin)).void

  override def assignWebIdentInitiateId(pocAdminId: UUID, webIdentInitiateId: UUID): Task[Unit] = {
    run(updateWebInitiateId(pocAdminId, webIdentInitiateId)).void
  }
  override def updateWebIdentIdAndStatus(webIdentId: UUID, pocAdminId: UUID): Task[Unit] =
    transaction {
      for {
        _ <- run(updateWebIdentIdQuery(webIdentId, pocAdminId)).void
        _ <- pocAdminStatusTable.updateWebIdentIdentified(pocAdminId, webIdentIdentified = true)
      } yield ()
    }
}
