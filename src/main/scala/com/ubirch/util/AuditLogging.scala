package com.ubirch.util

import com.typesafe.scalalogging.Logger
import com.ubirch.controllers.{ SuperAdminContext, TenantAdminContext }
import net.logstash.logback.argument.StructuredArguments.v
import org.slf4j.{ LoggerFactory, Marker, MarkerFactory }

trait AuditLogging {

  /**
    * Logging with a marker is pretty straightforward and
    * it allows keeping the kind of log level of your code and
    * at the same time mark the logging statement with a particular tag or category.
    *
    * For example:
    *
    *  logAuditEventInfo("Accessing root page")
    *
    * And if you have a json appender, you should see something like this being logged.
    * {"@timestamp":"2021-05-12T08:58:32.303+00:00","@version":"1","message":"Accessing root page","logger_name":"com.ubirch.events.AuditLogger","thread_name":"qtp1413145457-29","level":"INFO","level_value":20000,"tags":["AUDIT"]}
    */
  final private val AUDIT: Marker = MarkerFactory.getMarker("AUDIT")
  //Private so that this instance doesn't interfere when used with normal logging
  @transient
  private lazy val logger: Logger = Logger(LoggerFactory.getLogger(auditLoggerName))

  def auditLoggerName = "com.ubirch.events.AuditLogger"

  def logAuditEventInfo(message: String, args: Any*): Unit = logger.info(AUDIT, message, args)

}

trait PocAuditLogging extends AuditLogging {
  //This can be overridden in case a different name is needed
  override def auditLoggerName: String = super.auditLoggerName

  def logAuditWithTenantContext(msg: String, tenantContext: TenantAdminContext): Unit = {
    logAuditEventInfo(msg + s": tenantAdminId ${tenantContext.userId}; tenantId: ${tenantContext.tenantId}")
  }

  def logAuditWithSuperAdminContext(msg: String, superAdminContext: SuperAdminContext): Unit = {
    logAuditEventInfo(msg + s": superAdminId ${superAdminContext.userId}")
  }

}
