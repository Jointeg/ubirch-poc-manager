package com.ubirch.services.poc

import com.google.inject.Inject
import com.ubirch.controllers.TenantAdminContext
import com.ubirch.models.tenant.{ API, Tenant }
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
  def createListOfPoCs(csv: String, tenant: Tenant, tenantContext: TenantAdminContext): Task[Either[String, Unit]]
}

@Singleton
class PocBatchHandlerImpl @Inject() (processPoc: CsvProcessPoc, processPocAdmin: CsvProcessPocAdmin)(implicit
scheduler: Scheduler)
  extends PocBatchHandlerTrait {

  /**
    * This method dispatches processes for a Poc csv file and a PocAdmin csv file based on the number of csv header.
    */
  def createListOfPoCs(csv: String, tenant: Tenant, tenantContext: TenantAdminContext): Task[Either[String, Unit]] =
    CsvHelper.openFile(csv).use { source =>
      val lines = source.getLines()

      if (lines.hasNext) {
        val header = lines.next()

        header.split(columnSeparator).map(_.trim).length match {
          case colNum if colNum >= pocAdminHeaderColOrderLength && tenant.usageType == API =>
            Task(Left("cannot parse admin creation for a tenant with usageType API"))
          case colNum if colNum >= pocAdminHeaderColOrderLength =>
            processPocAdmin.createListOfPoCsAndAdmin(csv, tenant, tenantContext)
          case colNum if colNum >= pocHeaderColOrderLength =>
            processPoc.createListOfPoCs(csv, tenant, tenantContext)
          case colNum =>
            Task(Left(s"The number of header columns $colNum is not enough."))
        }
      } else {
        Task(Left("The provided csv is empty."))
      }
    }
}
