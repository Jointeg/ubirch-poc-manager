package com.ubirch.db.tables

import com.ubirch.models.common.{ Page, Sort }
import com.ubirch.models.poc.Status
import com.ubirch.models.tenant.TenantId

import java.util.UUID

object model {
  case class Criteria(tenantId: TenantId, page: Page, sort: Sort, search: Option[String], filter: StatusFilter)
  case class AdminCriteria(adminId: UUID, page: Page, sort: Sort, search: Option[String], filter: StatusFilter)
  case class StatusFilter(status: Seq[Status])

  case class PaginatedResult[T](total: Long, records: Seq[T])

}
