package com.ubirch.services.poc

import com.typesafe.scalalogging.{ LazyLogging, Logger }
import com.ubirch.db.context.QuillMonixJdbcContext
import com.ubirch.db.tables.{ PocEmployeeRepository, PocEmployeeStatusRepository, PocRepository }
import com.ubirch.models.common.{ ProcessingElements, WaitingForNewElements }
import com.ubirch.models.poc.{ Aborted, Completed, Poc, Processing, Status }
import com.ubirch.models.pocEmployee.{ PocEmployee, PocEmployeeStatus }
import com.ubirch.util.PocAuditLogging
import monix.eval.Task
import org.joda.time.DateTime

import javax.inject.Inject

trait PocEmployeeCreator {
  def createPocEmployees(): Task[Unit]
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

  def createPocEmployees(): Task[Unit] = {
    employeeTable.getAllPocEmployeesToBecomeProcessed().flatMap {
      case pocEmployeeIds if pocEmployeeIds.isEmpty =>
        logger.debug("no poc employees waiting for completion")
        Task(PocEmployeeCreationLoop.loopState.set(WaitingForNewElements(DateTime.now(), "PoC Employee"))).void
      case pocEmployeeIds =>
        Task.sequence(pocEmployeeIds.map(employeeId => {
          (for {
            _ <- Task(logger.info(s"Starting to process $employeeId pocEmployee"))
            _ <- Task.cancelBoundary
            _ <- Task(ProcessingElements(DateTime.now(), "PoC Employee", employeeId.toString))
            pocEmployee <- employeeTable.unsafeGetUncompletedPocEmployeeById(employeeId)
            _ <- createPocEmployee(pocEmployee).uncancelable
          } yield ()).onErrorHandle(ex => {
            logger.error(s"Unexpected error happened durring creating PoC Employee with id $pocEmployeeIds", ex)
            ()
          })
        })).void
    }
  }

  private def incrementCreationAttemptCounter(employee: PocEmployee) = {
    if (employee.creationAttempts >= 10) {
      Task(logger.warn(
        s"PoC Employee with ID ${employee.id} has exceeded maximum creation attempts number. Changing its status to Aborted.")) >>
        employeeTable.incrementCreationAttempts(employee.id) >> employeeTable.updatePocEmployee(employee.copy(status =
          Aborted)).void
    } else {
      employeeTable.incrementCreationAttempts(employee.id)
    }
  }

  private def createPocEmployee(employee: PocEmployee): Task[Either[String, PocEmployeeStatus]] = {
    import cats.syntax.all._
    retrieveStatusAndPoc(employee).flatMap {
      case (Some(poc: Poc), Some(status: PocEmployeeStatus)) =>
        for {
          result <- process(EmployeeTriple(poc, employee, status.copy(errorMessage = None)))
          _ <- result.leftTraverse(_ => incrementCreationAttemptCounter(employee))
        } yield result
      case (_, _) =>
        incrementCreationAttemptCounter(employee) >>
          Task(logAndGetLeft(s"cannot create employee ${employee.id}, poc or status couldn't be found"))
    }.onErrorHandleWith { e =>
      incrementCreationAttemptCounter(employee) >>
        Task(logAndGetLeft(
          s"cannot create employee ${employee.id}, poc and status retrieval failed ${e.getMessage}",
          e))
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

case class EmployeeAndStatus(employee: PocEmployee, status: PocEmployeeStatus)
case class PocEmployeeCreationError(employeeAndStatus: EmployeeAndStatus, message: String) extends Exception(message)
