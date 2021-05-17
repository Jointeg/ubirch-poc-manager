package com.ubirch.services.poc

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.PocConfig
import com.ubirch.db.context.QuillJdbcContext
import com.ubirch.db.tables.{ PocRepository, PocStatusRepository }
import com.ubirch.models.poc.{ Poc, PocStatus }
import com.ubirch.models.tenant.Tenant
import com.ubirch.services.poc.parsers.PocCsvParser
import com.ubirch.services.poc.util.{ CsvConstants, HeaderCsvException }
import monix.eval.Task
import monix.execution.Scheduler

import javax.inject.{ Inject, Singleton }

trait CsvProcessPoc {
  def createListOfPoCs(csv: String, tenant: Tenant): Task[Either[String, Unit]]
}

@Singleton
class CsvProcessPocImpl @Inject() (
  pocConfig: PocConfig,
  quillJdbcContext: QuillJdbcContext,
  pocRepository: PocRepository,
  pocStatusRepository: PocStatusRepository,
  scheduler: Scheduler)
  extends CsvProcessPoc
  with LazyLogging {
  implicit val sc = scheduler

  private val pocCsvParser = new PocCsvParser(pocConfig)

  def createListOfPoCs(csv: String, tenant: Tenant): Task[Either[String, Unit]] =
    pocCsvParser.parseList(csv, tenant).flatMap { parsingResult =>
      val r = parsingResult.map {
        case Right(rowResult) =>
          storePocAndStatus(rowResult.poc, rowResult.csvRow)
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

  private def storePocAndStatus(poc: Poc, csvRow: String): Task[Option[String]] = {
    val status = PocStatus.init(poc)
    quillJdbcContext.withTransaction {
      for {
        _ <- pocRepository.createPoc(poc)
        _ <- pocStatusRepository.createPocStatus(status)
      } yield {
        None
      }
    }.onErrorHandle {
      case e =>
        logger.error(s"fail to create poc and status. poc: $poc, error: ${e.getMessage}")
        Some(csvRow)
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
          val csvRows = CsvConstants.pocHeaderLine +: errorCsvRows
          Left(csvRows.mkString(CsvConstants.carriageReturn))
        }
      }
  }
}
