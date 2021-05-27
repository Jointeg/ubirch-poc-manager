package com.ubirch.services.poc.employee

import cats.data.EitherT
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.db.context.QuillMonixJdbcContext
import com.ubirch.db.tables.{ PocEmployeeRepository, PocEmployeeStatusRepository, TenantRepository }
import com.ubirch.models.poc.{ Completed, PocAdmin }
import com.ubirch.models.pocEmployee.PocEmployeeStatus
import com.ubirch.models.tenant.{ Tenant, TenantId }
import com.ubirch.services.poc.employee.parsers.{ PocEmployeeCsvParseResult, PocEmployeeCsvParser }
import com.ubirch.services.poc.util.CsvConstants.columnSeparator
import com.ubirch.services.poc.util.{ CsvConstants, EmptyCsvException, HeaderCsvException }
import com.ubirch.util.PocAuditLogging
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
  with LazyLogging
  with PocAuditLogging {

  private val pocEmployeeCsvParser = new PocEmployeeCsvParser()

  override def createListOfPocEmployees(
    csv: String,
    pocAdmin: PocAdmin): Task[Either[CreateEmployeeFromCsvError, Unit]] = {
    (for {
      _ <- verifyPocAdminStatus(pocAdmin)
      tenant <- EitherT.fromOptionF(tenantRepository.getTenant(pocAdmin.tenantId), UnknownTenant(pocAdmin.tenantId))
      parsingResult <- EitherT(pocEmployeeCsvParser.parseList(csv, tenant).map(result =>
        result.asRight[CreateEmployeeFromCsvError]).onErrorHandle {
        case HeaderCsvException(msg: String) => Left(HeaderParsingError(msg))
        case EmptyCsvException(msg: String)  => Left(EmptyCSVError(msg))
        case exception                       => Left(UnknownCsvParsingError(exception.getMessage))
      })
      errorCsvRows <- EitherT.liftF(parsingResult.toList.traverseFilter {
        case Right(rowResult) => storePocEmployee(rowResult, pocAdmin, tenant)
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
    pocEmployeeCsvParseResult: PocEmployeeCsvParseResult,
    pocAdmin: PocAdmin,
    tenant: Tenant): Task[Option[String]] = {
    val employee =
      pocEmployeeCsvParseResult.pocEmployeeFromCsv.toFullPocEmployeeRepresentation(pocAdmin.pocId, tenant.id)
    val employeeStatus = PocEmployeeStatus(employee.id)
    quillMonixJdbcContext.withTransaction {
      for {
        _ <- employeeRepository.createPocEmployee(employee)
        _ <- employeeStatusRepository.createStatus(employeeStatus)
      } yield {
        logAuditByPocAdmin(s"created pocEmployee and status with ${employee.id}", pocAdmin)
        None
      }
    }.onErrorHandle { e =>
      logger.error(
        s"fail to create poc employee and status. poc: $pocAdmin.pocId, pocEmployee: ${pocEmployeeCsvParseResult.pocEmployeeFromCsv}, error: ${e.getMessage}")
      Some(
        pocEmployeeCsvParseResult.csvRow + columnSeparator + "error on persisting objects; maybe duplicated key error")
    }
  }

  private def createResponse(errorCsvRows: List[String]): Either[CreateEmployeeFromCsvError, Unit] = {
    if (errorCsvRows.isEmpty) {
      Right(())
    } else {
      val csvRows = CsvConstants.pocEmployeeHeaderLine +: errorCsvRows
      Left(CsvContainedErrors(csvRows.mkString(CsvConstants.carriageReturn)))
    }
  }
}

sealed trait CreateEmployeeFromCsvError
case class PocAdminNotInCompletedStatus(pocAdminId: UUID) extends CreateEmployeeFromCsvError
case class UnknownTenant(tenantId: TenantId) extends CreateEmployeeFromCsvError
case class CsvContainedErrors(csvErrors: String) extends CreateEmployeeFromCsvError
case class HeaderParsingError(msg: String) extends CreateEmployeeFromCsvError
case class EmptyCSVError(msg: String) extends CreateEmployeeFromCsvError
case class UnknownCsvParsingError(msg: String) extends CreateEmployeeFromCsvError
