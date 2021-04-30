package com.ubirch.services.poc

import com.google.inject.Inject
import com.typesafe.config.Config
import com.ubirch.ConfPaths.ServicesConfPaths
import com.ubirch.db.tables.{ PocStatusTable, PocTable }
import com.ubirch.models.poc.{ Poc, PocStatus }
import com.ubirch.services.poc.util.CsvConstants
import monix.eval.Task
import monix.execution.Scheduler

import javax.inject.Singleton
import scala.util.{ Failure, Success, Try }

trait PocBatchHandlerTrait {
  def createListOfPoCs(csv: String): Task[Either[String, Unit]]
}

@Singleton
class PocBatchHandlerImpl @Inject() (conf: Config, pocTable: PocTable, pocStatusTable: PocStatusTable)
  extends PocBatchHandlerTrait {

  private val csvHandler = new CsvPocBatchParserImp
  private val dataSchemaGroupIds =
    conf
      .getString(ServicesConfPaths.DATA_SCHEMA_GROUP_IDS)
      .split(", ")
      .toList
  implicit val scheduler: Scheduler = monix.execution.Scheduler.global

  def createListOfPoCs(csv: String): Task[Either[String, Unit]] = {

    Try(csvHandler.parsePocCreationList(csv)) match {

      case Success(parsingResult) =>
        val r = parsingResult.map {
          case Right((poc, csvRow)) =>
            storePocAndStatus(poc, csvRow)
          case Left(csvRow) =>
            Task(Some(csvRow))
        }
        createResponse(r)

      case Failure(ex: HeaderCsvException) =>
        Task(Left(ex.errorMsg))

      case Failure(ex: Throwable) =>
        Task(Left(s"something unexpected went wrong ${ex.getMessage}"))
    }

  }

  private def storePocAndStatus(poc: Poc, csvRow: String): Task[Option[String]] = {
    val status = createInitialPocCreationState(poc)
    (for {
      _ <- pocTable.createPoc(poc)
      _ <- pocStatusTable.createPocStatus(status)
    } yield {
      None
    }).onErrorHandle(_ => Some(csvRow))
  }

  private def createResponse(result: Seq[Task[Option[String]]]): Task[Either[String, Unit]] = {
    Task
      .gather(result)
      .map { csvRowOptions =>
        val errorCsvRows = csvRowOptions.flatten
        if (errorCsvRows.isEmpty)
          Right(Unit)
        else {
          val csvRows = CsvConstants.headerLine +: errorCsvRows
          Left(csvRows.mkString(CsvConstants.carriageReturn))
        }
      }
  }

  def createInitialPocCreationState(poc: Poc): PocStatus = {
    //Todo: check if poc.clientCertRequired == false, that tenant already has idgard URL?!
    println(dataSchemaGroupIds)
    PocStatus(
      pocId = poc.id,
      validDataSchemaGroup = dataSchemaGroupIds.contains(poc.dataSchemaId),
      clientCertRequired = poc.clientCertRequired,
      clientCertDownloaded = if (poc.clientCertRequired) Some(false) else None,
      clientCertProvided = if (poc.clientCertRequired) Some(false) else None,
      logoRequired = poc.certifyApp,
      logoReceived = if (poc.certifyApp) Some(false) else None,
      logoStored = if (poc.certifyApp) Some(false) else None
    )
  }

}
