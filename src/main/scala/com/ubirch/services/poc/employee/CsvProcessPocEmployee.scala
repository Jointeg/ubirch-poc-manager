package com.ubirch.services.poc.employee

import cats.data.EitherT
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.db.context.QuillMonixJdbcContext
import com.ubirch.db.tables.{ PocEmployeeRepository, PocEmployeeStatusRepository, TenantRepository }
import com.ubirch.models.poc.{ Completed, PocAdmin }
import com.ubirch.models.pocEmployee.{ PocEmployeeFromCsv, PocEmployeeStatus }
import com.ubirch.models.tenant.{ Tenant, TenantId }
import com.ubirch.services.poc.employee.parsers.PocEmployeeCsvParser
import com.ubirch.services.poc.util.CsvConstants
import monix.eval.Task

import java.util.UUID
import javax.inject.Inject

trait CsvProcessPocEmployee {
  def createListOfPocEmployees(
    csv: String,
    pocAdmin: PocAdmin): Task[Either[CreateEmployeeFromCsvError, Unit]]
}

class CsvProcessPocEmployeeImpl @Inject() (
  tenantRepository: TenantRepository,
  employeeRepository: PocEmployeeRepository,
  employeeStatusRepository: PocEmployeeStatusRepository,
  quillMonixJdbcContext: QuillMonixJdbcContext
) extends CsvProcessPocEmployee
  with LazyLogging {

  private val pocEmployeeCsvParser = new PocEmployeeCsvParser()

  override def createListOfPocEmployees(
    csv: String,
    pocAdmin: PocAdmin): Task[Either[CreateEmployeeFromCsvError, Unit]] = {
    (for {
      _ <- verifyPocAdminStatus(pocAdmin)
      tenant <- EitherT.fromOptionF(tenantRepository.getTenant(pocAdmin.tenantId), UnknownTenant(pocAdmin.tenantId))
      parsingResult <- EitherT.liftF(pocEmployeeCsvParser.parseList(csv, tenant))
      errorCsvRows <- EitherT.liftF(parsingResult.toList.traverseFilter {
        case Right(rowResult) => storePocEmployee(rowResult.pocEmployeeFromCsv, pocAdmin.pocId, tenant)
        case Left(csvRow)     => Task(Some(csvRow))
      })
      response <- EitherT.fromEither[Task](createResponse(errorCsvRows))
    } yield response).value
  }

  private def verifyPocAdminStatus(pocAdmin: PocAdmin) = {
    if (pocAdmin.status == Completed) {
      EitherT.rightT[Task, CreateEmployeeFromCsvError](())
    } else {
      EitherT.leftT[Task, Unit](PocAdminNotInCompletedStatus(pocAdmin.id))
    }
  }

  private def storePocEmployee(
    pocEmployeeFromCsv: PocEmployeeFromCsv,
    pocId: UUID,
    tenant: Tenant): Task[Option[String]] = {
    val initializedPocEmployee = pocEmployeeFromCsv.toFullPocEmployeeRepresentation(pocId, tenant.id)
    val initialPocEmployeeStatus = PocEmployeeStatus(initializedPocEmployee.id)
    quillMonixJdbcContext.withTransaction {
      for {
        _ <- employeeRepository.createPocEmployee(initializedPocEmployee)
        _ <- employeeStatusRepository.createStatus(initialPocEmployeeStatus)
      } yield None
    }
  }

  private def createResponse(errorCsvRows: List[String]): Either[CreateEmployeeFromCsvError, Unit] = {
    if (errorCsvRows.isEmpty) {
      Right(())
    } else {
      val csvRows = CsvConstants.pocEmployeeHeaderLine :+ errorCsvRows
      Left(CsvContainedErrors(csvRows.mkString(CsvConstants.carriageReturn)))
    }
  }
}

sealed trait CreateEmployeeFromCsvError
case class PocAdminNotInCompletedStatus(pocAdminId: UUID) extends CreateEmployeeFromCsvError
case class UnknownTenant(tenantId: TenantId) extends CreateEmployeeFromCsvError
case class CsvContainedErrors(csvErrors: String) extends CreateEmployeeFromCsvError
