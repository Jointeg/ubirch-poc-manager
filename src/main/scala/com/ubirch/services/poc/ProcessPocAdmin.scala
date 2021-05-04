package com.ubirch.services.poc

import com.typesafe.config.Config
import com.ubirch.ConfPaths.ServicesConfPaths
import com.ubirch.db.context.QuillJdbcContext
import com.ubirch.db.tables.{ PocAdminRepository, PocAdminStatusRepository, PocRepository, PocStatusRepository }
import com.ubirch.models.poc.{ Poc, PocAdmin, PocAdminStatus, PocStatus }
import com.ubirch.models.tenant.Tenant
import com.ubirch.services.poc.parsers.PocAdminCsvParser
import com.ubirch.services.poc.util.{ CsvConstants, HeaderCsvException }
import monix.eval.Task

import javax.inject.{ Inject, Singleton }

trait ProcessPocAdmin {
  def createListOfPoCsAndAdmin(csv: String, tenant: Tenant): Task[Either[String, Unit]]
}

@Singleton
class ProcessPocAdminImpl @Inject() (
  conf: Config,
  quillJdbcContext: QuillJdbcContext,
  pocRepository: PocRepository,
  pocAdminRepository: PocAdminRepository,
  pocStatusRepository: PocStatusRepository,
  pocAdminStatusRepository: PocAdminStatusRepository)
  extends ProcessPocAdmin {
  import quillJdbcContext.ctx._

  private val dataSchemaGroupIds =
    conf
      .getString(ServicesConfPaths.DATA_SCHEMA_GROUP_IDS)
      .split(", ")
      .toList

  def createListOfPoCsAndAdmin(csv: String, tenant: Tenant): Task[Either[String, Unit]] = {
    PocAdminCsvParser.parseList(csv, tenant).flatMap { parsingResult =>
      val r = parsingResult.map {
        case Right(rowResult) =>
          storePocAndStatus(rowResult.poc, rowResult.pocAdmin, rowResult.csvRow)
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

  private def storePocAndStatus(poc: Poc, pocAdmin: PocAdmin, csvRow: String): Task[Option[String]] = {
    val pocStatus = PocStatus.init(poc, dataSchemaGroupIds)
    val pocAdminStatus = PocAdminStatus.init(pocAdmin)
    transaction {
      (for {
        _ <- pocRepository.createPoc(poc)
        _ <- pocStatusRepository.createPocStatus(pocStatus)
        _ <- pocAdminRepository.createPoc(pocAdmin)
        _ <- pocAdminStatusRepository.createPocAdminStatus(pocAdminStatus)
      } yield {
        None
      }).onErrorHandle(_ => Some(csvRow))
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
