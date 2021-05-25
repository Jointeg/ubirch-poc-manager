package com.ubirch.services.poc

import com.typesafe.scalalogging.{ LazyLogging, Logger }
import com.ubirch.db.context.QuillMonixJdbcContext
import com.ubirch.db.tables.{ PocEmployeeRepository, PocEmployeeStatusRepository, PocRepository }
import com.ubirch.models.poc.{ Completed, Poc, Processing, Status }
import com.ubirch.models.pocEmployee.{ PocEmployee, PocEmployeeStatus }
import monix.eval.Task

import javax.inject.Inject

trait PocEmployeeCreator {
  def createPocEmployees(): Task[PocEmployeeCreationResult]
}

object PocEmployeeCreator {
  @throws[PocEmployeeCreationError]
  def throwAndLogError(employeeAndStatus: EmployeeAndStatus, msg: String, ex: Throwable, logger: Logger): Nothing = {
    logger.error(msg, ex)
    throwError(employeeAndStatus, msg + ex.getMessage)
  }

  @throws[PocEmployeeCreationError]
  def throwError(employeeAndStatus: EmployeeAndStatus, msg: String) =
    throw PocEmployeeCreationError(
      employeeAndStatus.copy(status = employeeAndStatus.status.copy(errorMessage = Some(msg))),
      msg)
}

case class EmployeeTriple(poc: Poc, employee: PocEmployee, status: PocEmployeeStatus)

class PocEmployeeCreatorImpl @Inject() (
  certifyHelper: EmployeeCertifyHelper,
  pocTable: PocRepository,
  employeeTable: PocEmployeeRepository,
  employeeStatusTable: PocEmployeeStatusRepository,
  quillMonixJdbcContext: QuillMonixJdbcContext)
  extends PocEmployeeCreator
  with LazyLogging {

  import certifyHelper._

  def createPocEmployees(): Task[PocEmployeeCreationResult] = {
    employeeTable.getUncompletedPocEmployees().flatMap {
      case pocEmployees if pocEmployees.isEmpty =>
        logger.debug("no poc employees waiting for completion")
        Task(NoWaitingPocEmployee)
      case pocEmployees =>
        logger.info(s"starting to create ${pocEmployees.size} pocEmployees")
        Task.gather(pocEmployees.map(createPocEmployee))
          .map(PocEmployeeCreationMaybeSuccess)
    }
  }

  private def createPocEmployee(employee: PocEmployee): Task[Either[String, PocEmployeeStatus]] = {
    retrieveStatusAndPoc(employee).flatMap {
      case (Some(poc: Poc), Some(status: PocEmployeeStatus)) =>
        process(EmployeeTriple(poc, employee, status))
      case (_, _) =>
        Task(logAndGetLeft(s"cannot create employee ${employee.id}, poc or status couldn't be found"))
    }.onErrorHandle { e =>
      logAndGetLeft(s"cannot create employee ${employee.id}, poc and status retrieval failed ${e.getMessage}", e)
    }
  }

  private def process(triple: EmployeeTriple): Task[Either[String, PocEmployeeStatus]] = {

    val creationResult = for {
      employee <- updateStatusOfEmployee(triple.employee, Processing)
      _ <- employeeTable.updatePocEmployee(employee)
      eAs1 <- createCertifyUserWithRequiredActions(EmployeeAndStatus(employee, triple.status))
      eAs2 <- addGroupsToCertifyUser(eAs1, triple.poc)
      eAs3 <- sendEmailToCertifyUser(eAs2)
    } yield eAs3

    (for {
      eAs <- creationResult
      _ <- quillMonixJdbcContext.withTransaction {
        updateStatusOfEmployee(eAs.employee, Completed) >>
          employeeStatusTable.updateStatus(eAs.status)
      }
    } yield {
      logger.info(s"finished to create poc employee with id ${eAs.employee.id}")
      Right(eAs.status)
    }).onErrorHandleWith(handlePocEmployeeCreationError)
  }

  private def updateStatusOfEmployee(pocEmployee: PocEmployee, newStatus: Status): Task[PocEmployee] = {
    if (pocEmployee.status == newStatus) Task(pocEmployee)
    else {
      val updatedPoc = pocEmployee.copy(status = newStatus)
      employeeTable.updatePocEmployee(updatedPoc).map(_ => updatedPoc)
    }
  }

  private def retrieveStatusAndPoc(employee: PocEmployee): Task[(Option[Poc], Option[PocEmployeeStatus])] = {
    for {
      status <- employeeStatusTable.getStatus(employee.id)
      poc <- pocTable.getPoc(employee.pocId)
    } yield (poc, status)
  }

  private def handlePocEmployeeCreationError[A](ex: Throwable): Task[Either[String, A]] = {
    ex match {
      case pace: PocEmployeeCreationError =>
        (for {
          _ <- quillMonixJdbcContext.withTransaction {
            employeeTable.updatePocEmployee(pace.employeeAndStatus.employee) >>
              employeeStatusTable.updateStatus(pace.employeeAndStatus.status)
          }
        } yield {
          logAndGetLeft(s"poc employee creation failed; ${pace.employeeAndStatus.status}, error: ${pace.message}")
        }).onErrorHandle { ex =>
          logAndGetLeft(s"failed to persist employeeStatus after creation failed ${pace.employeeAndStatus.status}")
        }
      case ex: Throwable =>
        Task(logAndGetLeft("unexpected error during poc employee creation; ", ex))
    }
  }

  private def logAndGetLeft(errorMsg: String): Left[String, Nothing] = {
    logger.error(errorMsg)
    Left(errorMsg)
  }

  private def logAndGetLeft(errorMsg: String, ex: Throwable): Left[String, Nothing] = {
    logger.error(errorMsg, ex)
    Left(errorMsg + ex.getMessage)
  }

}

sealed trait PocEmployeeCreationResult
case object NoWaitingPocEmployee extends PocEmployeeCreationResult
case class PocEmployeeCreationMaybeSuccess(list: Seq[Either[String, PocEmployeeStatus]])
  extends PocEmployeeCreationResult

case class EmployeeAndStatus(employee: PocEmployee, status: PocEmployeeStatus)
case class PocEmployeeCreationError(employeeAndStatus: EmployeeAndStatus, message: String) extends Exception(message)
