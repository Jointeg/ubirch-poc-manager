package com.ubirch.services.poc

import com.google.inject.Inject
import com.ubirch.models.tenant.Tenant
import com.ubirch.services.poc.util.CsvConstants.{
  columnSeparator,
  pocAdminHeaderColOrderLength,
  pocHeaderColOrderLength
}
import com.ubirch.services.util.CsvHelper
import monix.eval.Task
import monix.execution.Scheduler

import javax.inject.Singleton

trait PocBatchHandlerTrait {
  def createListOfPoCs(csv: String, tenant: Tenant): Task[Either[String, Unit]]
}

@Singleton
class PocBatchHandlerImpl @Inject() (processPoc: CsvProcessPoc, processPocAdmin: CsvProcessPocAdmin)(implicit
scheduler: Scheduler)
  extends PocBatchHandlerTrait {

  /**
    * This method dispatches processes for a Poc csv file and a PocAdmin csv file based on the number of csv header.
    */
  def createListOfPoCs(csv: String, tenant: Tenant): Task[Either[String, Unit]] =
    CsvHelper.openFile(csv).use { source =>
      val lines = source.getLines()

      if (lines.hasNext) {
        val header = lines.next()
        val colNum = header.split(columnSeparator).map(_.trim).length
        // @todo enables this part later
        /*if (colNum >= pocAdminHeaderColOrderLength) {
          processPocAdmin.createListOfPoCsAndAdmin(csv, tenant)
        } else*/
        if (colNum >= pocHeaderColOrderLength) {
          processPoc.createListOfPoCs(csv, tenant)
        } else {
          Task(Left(s"$header; the number of header($colNum) is not enough."))
        }
      } else {
        Task(Left("the csv is empty."))
      }
    }
}
