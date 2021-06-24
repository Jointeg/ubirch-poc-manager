package com.ubirch

import com.google.inject.binder.ScopedBindingBuilder
import com.google.inject.{ AbstractModule, Module }
import com.typesafe.config.Config
import com.ubirch.db.context.{ PostgresQuillMonixJdbcContext, QuillMonixJdbcContext }
import com.ubirch.db.tables._
import com.ubirch.services.clock.ClockProvider
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.execution.{ ExecutionProvider, SchedulerProvider }
import com.ubirch.services.formats.{ CustomJsonFormats, EmptyCustomJsonFormats, JsonFormatsProvider }
import com.ubirch.services.lifecycle.{ DefaultJVMHook, DefaultLifecycle, JVMHook, Lifecycle }
import com.ubirch.services.poc.employee.{ PocEmployeeService, PocEmployeeServiceImpl }
import com.ubirch.services.rest.SwaggerProvider
import monix.execution.Scheduler
import org.json4s.Formats
import org.scalatra.swagger.Swagger

import java.time.Clock
import scala.concurrent.ExecutionContext

class Binder extends AbstractModule {

  def Config: ScopedBindingBuilder = bind(classOf[Config]).toProvider(classOf[ConfigProvider])

  def PocConfig: ScopedBindingBuilder = bind(classOf[PocConfig]).to(classOf[PocConfigImpl])

  def Clock: ScopedBindingBuilder = bind(classOf[Clock]).toProvider(classOf[ClockProvider])

  def ExecutionContext: ScopedBindingBuilder = bind(classOf[ExecutionContext]).toProvider(classOf[ExecutionProvider])

  def Scheduler: ScopedBindingBuilder = bind(classOf[Scheduler]).toProvider(classOf[SchedulerProvider])

  def Swagger: ScopedBindingBuilder = bind(classOf[Swagger]).toProvider(classOf[SwaggerProvider])

  def QuillMonixJdbcContext: ScopedBindingBuilder =
    bind(classOf[QuillMonixJdbcContext]).to(classOf[PostgresQuillMonixJdbcContext])

  def CustomFormats: ScopedBindingBuilder = bind(classOf[CustomJsonFormats]).to(classOf[EmptyCustomJsonFormats])

  def Formats: ScopedBindingBuilder = bind(classOf[Formats]).toProvider(classOf[JsonFormatsProvider])

  def Lifecycle: ScopedBindingBuilder = bind(classOf[Lifecycle]).to(classOf[DefaultLifecycle])

  def JVMHook: ScopedBindingBuilder = bind(classOf[JVMHook]).to(classOf[DefaultJVMHook])

  def PocRepository: ScopedBindingBuilder = bind(classOf[PocRepository]).to(classOf[PocTable])

  def PocLogoRepository: ScopedBindingBuilder =
    bind(classOf[PocLogoRepository]).to(classOf[PocLogoTable])

  def PocEmployeeService: ScopedBindingBuilder =
    bind(classOf[PocEmployeeService]).to(classOf[PocEmployeeServiceImpl])

  override def configure(): Unit = {
    Config
    PocConfig
    Clock
    ExecutionContext
    Scheduler
    Swagger
    QuillMonixJdbcContext
    CustomFormats
    Formats
    Lifecycle
    JVMHook
    PocRepository
    PocLogoRepository
    PocEmployeeService
    ()
  }
}

object Binder {
  def modules: List[Module] = List(new Binder)
}
