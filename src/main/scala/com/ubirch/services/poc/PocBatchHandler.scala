package com.ubirch.services.poc

import com.google.inject.Inject
import com.ubirch.models.tenant.Tenant
import com.ubirch.services.poc.util.CsvConstants.{
  columnSeparator,
  pocAdminHeaderColOrderLength,
  pocHeaderColOrderLength
}
import com.ubirch.services.poc.util.HeaderCsvException
import com.ubirch.services.util.CsvHelper
import monix.eval.Task
import monix.execution.Scheduler

import javax.inject.Singleton

trait PocBatchHandlerTrait {
  def createListOfPoCs(csv: String, tenant: Tenant): Task[Either[String, Unit]]
}

@Singleton
class PocBatchHandlerImpl @Inject() (processPoc: ProcessPoc, processPocAdmin: ProcessPocAdmin)
  extends PocBatchHandlerTrait {

  implicit val scheduler: Scheduler = monix.execution.Scheduler.global

  /**
    * This method dispatches processes for a Poc csv file and a PocAdmin csv file.
    */
  def createListOfPoCs(csv: String, tenant: Tenant): Task[Either[String, Unit]] =
    CsvHelper.openFile(csv).use { source =>
      val lines = source.getLines()

      if (lines.hasNext) {
        val colNum = lines.next().split(columnSeparator).map(_.trim).length
        colNum match {
          case `pocHeaderColOrderLength` =>
            processPoc.createListOfPoCs(csv, tenant)
          case `pocAdminHeaderColOrderLength` =>
            processPocAdmin.createListOfPoCsAndAdmin(csv, tenant)
          case _ =>
            throw HeaderCsvException(s"the number of column is incorrect. $colNum")
        }
      } else {
        throw HeaderCsvException("the csv is empty.")
      }
    }
}
