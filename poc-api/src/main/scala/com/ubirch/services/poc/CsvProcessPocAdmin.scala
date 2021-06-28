package com.ubirch.services.poc

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.PocConfig
import com.ubirch.controllers.TenantAdminContext
import com.ubirch.db.context.QuillMonixJdbcContext
import com.ubirch.db.tables.{ PocAdminRepository, PocAdminStatusRepository, PocRepository, PocStatusRepository }
import com.ubirch.models.poc.{ Poc, PocAdmin, PocAdminStatus, PocStatus }
import com.ubirch.models.tenant.Tenant
import com.ubirch.services.poc.parsers.PocAdminCsvParser
import com.ubirch.services.poc.util.CsvConstants.columnSeparator
import com.ubirch.services.poc.util.{ CsvConstants, HeaderCsvException }
import com.ubirch.util.PocAuditLogging
import monix.eval.Task
import monix.execution.Scheduler
import org.postgresql.util.PSQLException

import javax.inject.{ Inject, Singleton }

trait CsvProcessPocAdmin {
  def createListOfPoCsAndAdmin(
    csv: String,
    tenant: Tenant,
    tenantContext: TenantAdminContext): Task[Either[String, Unit]]
}

@Singleton
class CsvProcessPocAdminImpl @Inject() (
  pocConfig: PocConfig,
  QuillMonixJdbcContext: QuillMonixJdbcContext,
  pocRepository: PocRepository,
  pocAdminRepository: PocAdminRepository,
  pocStatusRepository: PocStatusRepository,
  pocAdminStatusRepository: PocAdminStatusRepository)(implicit val scheduler: Scheduler)
  extends CsvProcessPocAdmin
  with LazyLogging
  with PocAuditLogging {

  private val pocAdminCsvParser = new PocAdminCsvParser(pocConfig)

  def createListOfPoCsAndAdmin(
    csv: String,
    tenant: Tenant,
    tenantContext: TenantAdminContext): Task[Either[String, Unit]] = {

    pocAdminCsvParser
      .parseList(csv, tenant)
      .flatMap { parsingResult =>
        val r = parsingResult.map {
          case Right(rowResult) =>
            storePocAndStatus(rowResult.poc, rowResult.pocAdmin, rowResult.csvRow, tenantContext: TenantAdminContext)
          case Left(csvRow) =>
            Task(Some(csvRow))
        }
        createResponse(r)
      }.onErrorRecover {
        case ex: HeaderCsvException =>
          Left(ex.errorMsg)

        case ex: Throwable =>
          Left(s"something unexpected went wrong ${ex.getMessage}")
      }
  }

  private def storePocAndStatus(
    poc: Poc,
    pocAdmin: PocAdmin,
    csvRow: String,
    tenantContext: TenantAdminContext): Task[Option[String]] = {
    val pocStatus = PocStatus.init(poc)
    val pocAdminStatus = PocAdminStatus.init(pocAdmin, poc, pocConfig.pocTypeStaticSpaceNameMap)
    QuillMonixJdbcContext.withTransaction {
      for {
        _ <- pocRepository.createPoc(poc)
        _ <- pocStatusRepository.createPocStatus(pocStatus)
        _ <- pocAdminRepository.createPocAdmin(pocAdmin)
        _ <- pocAdminStatusRepository.createStatus(pocAdminStatus)
      } yield {
        logAuditByTenantAdmin(
          s"created poc and status with id ${poc.id} and pocAdmin and status with id ${pocAdmin.id}",
          tenantContext)
        None
      }
    }.onErrorHandle { e =>
      logger.error(s"fail to create poc and status. poc: $poc, error: ${e.getMessage}")
      e match {
        case _: PSQLException if e.getMessage.contains("duplicate") =>
          Some(csvRow + columnSeparator + s"error on persisting objects; admin email or the pair of (external_id and data_schema_id) already exist.")
        case _ =>
          Some(
            csvRow + columnSeparator + s"error on persisting objects; something unexpected went wrong ${e.getMessage}")
      }
    }
  }

  private def createResponse(result: Seq[Task[Option[String]]]): Task[Either[String, Unit]] = {
    Task
      .gather(result)
      .map { csvRowOptions =>
        val errorCsvRows = csvRowOptions.flatten
        if (errorCsvRows.isEmpty)
          Right(Unit)
        else {
          val csvRows = CsvConstants.pocAdminHeaderLine +: errorCsvRows
          Left(csvRows.mkString(CsvConstants.carriageReturn))
        }
      }
  }
}
