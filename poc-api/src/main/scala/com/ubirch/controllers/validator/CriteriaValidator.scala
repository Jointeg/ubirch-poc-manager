package com.ubirch.controllers.validator

import cats.data.Validated._
import cats.data._
import cats.implicits._
import com.ubirch.controllers.validator.CriteriaValidator.PocCriteriaValidationResult
import com.ubirch.db.tables.model.{ AdminCriteria, Criteria, StatusFilter }
import com.ubirch.models.common.{ ASC, Order, Page, Sort }
import com.ubirch.models.poc.Status
import com.ubirch.models.tenant.TenantId
import org.scalatra.Params

import java.util.UUID
import scala.util.{ Failure, Success, Try }

sealed trait CriteriaValidator {
  protected def validatePageIndex(params: Params, default: Int): PocCriteriaValidationResult[Int] =
    params.get("pageIndex") match {
      case Some(v) =>
        if (v.forall(_.isDigit) && v.nonEmpty) v.toInt.validNec
        else ("pageIndex", s"Invalid value for param 'pageIndex': $v").invalidNec
      case None => default.validNec
    }

  protected def validatePageSize(params: Params, default: Int): PocCriteriaValidationResult[Int] =
    params.get("pageSize") match {
      case Some(v) =>
        if (v.forall(_.isDigit) && v.nonEmpty) v.toInt.validNec
        else ("pageSize", s"Invalid value for param 'pageSize': $v").invalidNec
      case None => default.validNec
    }

  protected def validateSortOrder(params: Params, default: Order): PocCriteriaValidationResult[Order] =
    params.get("sortOrder") match {
      case Some(v) =>
        if (Seq("asc", "desc").contains(v)) Order.fromString(v).validNec
        else ("sortOrder", s"Invalid value for param 'sortOrder': $v. Valid values: asc, desc").invalidNec
      case None => default.validNec
    }

  protected def validateSortColumn(
    params: Params,
    validSortColumns: Seq[String]): PocCriteriaValidationResult[Option[String]] =
    params.get("sortColumn") match {
      case Some(v) =>
        if (validSortColumns.contains(v)) v.some.validNec
        else (
          "sortColumn",
          s"Invalid value for param 'sortColumn': $v. Valid values: ${validSortColumns.mkString(", ")}").invalidNec
      case None => None.validNec
    }

  protected def validateFilterColumnStatus(params: Params): PocCriteriaValidationResult[Seq[Status]] =
    params.get("filterColumn[status]") match {
      case Some(v) =>
        val (invalidStatuses, validStatuses) = v.split(",")
          .filterNot(_.trim == "")
          .map { s =>
            Try(Status.unsafeFromString(s.toUpperCase)) match {
              case Failure(_) => s.asLeft
              case Success(s) => s.asRight
            }
          }
          .toList
          .partitionEither {
            case Left(s)  => s.asLeft
            case Right(s) => s.asRight
          }

        if (invalidStatuses.nonEmpty)
          (
            "filterColumn[status]",
            s"Invalid value for param 'filterColumn[status]': ${invalidStatuses.mkString(", ")}").invalidNec
        else validStatuses.validNec
      case None => Seq.empty.validNec
    }
}

object CriteriaValidator extends CriteriaValidator {
  type PocCriteriaValidationResult[A] = ValidatedNec[(String, String), A]

  val validSortColumnsForPoc: Seq[String] =
    Seq(
      "id",
      "tenantId",
      "externalId",
      "pocName",
      "phone",
      "certifyApp",
      "clientCertRequired",
      "dataSchemaId",
      "roleName",
      "deviceId",
      "clientCertFolder",
      "status",
      "lastUpdated",
      "created"
    )

  val validSortColumnsForPocAdmin: Seq[String] =
    Seq("id", "firstName", "email", "lastName", "pocName", "active", "state", "createdAt")

  def validateParams(
    tenantId: TenantId,
    params: Params,
    validSortColumns: Seq[String]): PocCriteriaValidationResult[Criteria] = {
    val page = (validatePageIndex(params, default = 0), validatePageSize(params, default = 20)).mapN(Page)
    val sort = (validateSortColumn(params, validSortColumns), validateSortOrder(params, default = ASC)).mapN(Sort)
    val search: PocCriteriaValidationResult[Option[String]] = params.get("search").validNec

    (page, sort, search, validateFilterColumnStatus(params)).mapN {
      (page, sort, search, filterColumnStatus) =>
        Criteria(tenantId, page, sort, search, StatusFilter(filterColumnStatus))
    }
  }
}

object AdminCriteriaValidator extends CriteriaValidator {
  type PocCriteriaValidationResult[A] = ValidatedNec[(String, String), A]

  val validSortColumnsForEmployees: Seq[String] = Seq("lastName", "email", "firstName", "active", "status", "createdAt")

  def validateParams(
    pocAdmin: UUID,
    params: Params,
    validSortColumns: Seq[String]): PocCriteriaValidationResult[AdminCriteria] = {
    val page = (validatePageIndex(params, default = 0), validatePageSize(params, default = 20)).mapN(Page)
    val sort = (validateSortColumn(params, validSortColumns), validateSortOrder(params, default = ASC)).mapN(Sort)
    val search: PocCriteriaValidationResult[Option[String]] = params.get("search").validNec

    (page, sort, search, validateFilterColumnStatus(params)).mapN {
      (page, sort, search, filterColumnStatus) =>
        AdminCriteria(pocAdmin, page, sort, search, StatusFilter(filterColumnStatus))
    }
  }
}
