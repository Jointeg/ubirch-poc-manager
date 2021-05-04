package com.ubirch.services.poc.parsers

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.tenant.Tenant
import com.ubirch.services.poc.util.CsvConstants.columnSeparator
import com.ubirch.services.poc.util.{EmptyCsvException, HeaderCsvException}
import com.ubirch.services.util.CsvHelper
import monix.eval.Task

trait ParseRowResult {
  val csvRow: String
}

trait CsvParser[T <: ParseRowResult] extends LazyLogging {
  def parseList(csv: String, tenant: Tenant): Task[Seq[Either[String, T]]] = {
    CsvHelper.openFile(csv).use { source =>
      val lines = source.getLines()
      if (lines.hasNext) {
        validateHeaders(lines.next().split(columnSeparator).map(_.trim))
      } else {
        throw EmptyCsvException("the csv file mustn't be empty")
      }
      val pocRows = lines.map { line =>
        val cols = line.split(columnSeparator).map(_.trim)
        parseRow(cols, line, tenant)
      }.toSeq
      Task(pocRows)
    }.onErrorRecover {
      case ex: HeaderCsvException => throw ex

      case ex: Throwable =>
        val errorMsg = "something unexpected went wrong parsing the csv"
        logger.error(errorMsg, ex)
        Seq(Left(errorMsg))
    }
  }

  protected def parseRow(cols: Array[String], line: String, tenant: Tenant): Either[String, T]

  @throws[HeaderCsvException]
  protected def validateHeaders(cols: Array[String]): Unit
}

