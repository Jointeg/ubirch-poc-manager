package com.ubirch.services.poc

import cats.implicits.catsSyntaxApply
import com.typesafe.scalalogging.{ LazyLogging, Logger }
import com.ubirch.db.context.QuillMonixJdbcContext
import com.ubirch.db.tables.{ PocEmployeeRepository, PocEmployeeStatusRepository, PocRepository }
import com.ubirch.models.common.{ ProcessingElements, WaitingForNewElements }
import com.ubirch.models.poc.{ Completed, Poc, Processing, Status }
import com.ubirch.models.pocEmployee.{ PocEmployee, PocEmployeeStatus }
import com.ubirch.util.PocAuditLogging
import monix.eval.Task
import org.joda.time.DateTime

import javax.inject.Inject

trait PocEmployeeCreator {
  def createPocEmployees(): Task[PocEmployeeCreationResult]
}

object PocEmployeeCreator {
  @throws[PocEmployeeCreationError]
  def throwAndLogError(triple: EmployeeTriple, msg: String, ex: Throwable, logger: Logger): Nothing = {
    logger.error(msg, ex)
    throwError(triple, msg + ex.getMessage)
  }

  @throws[PocEmployeeCreationError]
  def throwError(triple: EmployeeTriple, msg: String) =
    throw PocEmployeeCreationError(
      EmployeeAndStatus(employee = triple.employee, status = triple.status.copy(errorMessage = Some(msg))),
      msg
    )
}

case class EmployeeTriple(poc: Poc, employee: PocEmployee, status: PocEmployeeStatus)

class PocEmployeeCreatorImpl @Inject() (
  certifyHelper: EmployeeCertifyHelper,
  pocTable: PocRepository,
  employeeTable: PocEmployeeRepository,
  employeeStatusTable: PocEmployeeStatusRepository,
  quillMonixJdbcContext: QuillMonixJdbcContext)
  extends PocEmployeeCreator
  with LazyLogging
  with PocAuditLogging {

  import certifyHelper._

  def createPocEmployees(): Task[PocEmployeeCreationResult] = {
    employeeTable.getUncompletedPocEmployees().flatMap {
      case pocEmployees if pocEmployees.isEmpty =>
        logger.debug("no poc employees waiting for completion")
        Task(PocEmployeeCreationLoop.loopState.set(WaitingForNewElements(DateTime.now(), "PoC Employee"))) >>
          Task(NoWaitingPocEmployee)
      case pocEmployees =>
        logger.info(s"starting to create ${pocEmployees.size} pocEmployees")
        Task(ProcessingElements(DateTime.now(), "PoC Employee", pocEmployees.map(_.id.toString).mkString(", "))) >>
          Task.sequence(pocEmployees.map(employee => Task.cancelBoundary *> createPocEmployee(employee).uncancelable))
            .map(PocEmployeeCreationMaybeSuccess)
    }
  }

  private def createPocEmployee(employee: PocEmployee): Task[Either[String, PocEmployeeStatus]] = {
    retrieveStatusAndPoc(employee).flatMap {
      case (Some(poc: Poc), Some(status: PocEmployeeStatus)) =>
        process(EmployeeTriple(poc, employee, status.copy(errorMessage = None)))
      case (_, _) =>
        Task(logAndGetLeft(s"cannot create employee ${employee.id}, poc or status couldn't be found"))
    }.onErrorHandle { e =>
      logAndGetLeft(s"cannot create employee ${employee.id}, poc and status retrieval failed ${e.getMessage}", e)
    }
  }

  private def process(triple: EmployeeTriple): Task[Either[String, PocEmployeeStatus]] = {

    val creationResult = for {
      employee <- updateStatusOfEmployee(triple.employee, Processing)
      triple1 <- createCertifyUserWithRequiredActions(triple.copy(employee = employee))
      triple2 <- addGroupsToCertifyUser(triple1)
      triple3 <- sendEmailToCertifyUser(triple2)
    } yield triple3

    (for {
      triple <- creationResult
      _ <- quillMonixJdbcContext.withTransaction {
        updateStatusOfEmployee(triple.employee, Completed) >>
          employeeStatusTable.updateStatus(triple.status)
      }.map(_ => logAuditEventInfo(s"updated poc employee and status with id ${triple.employee.id} by service"))
    } yield {
      logger.info(s"finished to create poc employee with id ${triple.employee.id}")
      Right(triple.status)
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
          _ <- quillMonixJdbcContext
            .withTransaction {
              employeeTable.updatePocEmployee(pace.employeeAndStatus.employee) >>
                employeeStatusTable.updateStatus(pace.employeeAndStatus.status)
            }.map(_ =>
              logAuditEventInfo(
                s"updated poc employee and status with id ${pace.employeeAndStatus.employee.id} by service"))
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
