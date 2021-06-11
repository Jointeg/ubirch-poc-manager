import com.ubirch.Service
import com.ubirch.controllers.{
  InfoController,
  PocAdminController,
  PocEmployeeController,
  ResourcesController,
  SuperAdminController,
  TenantAdminController
}
import org.scalatra.LifeCycle

import javax.servlet.ServletContext

class ScalatraBootstrap extends LifeCycle {

  override def init(context: ServletContext): Unit = {

    lazy val infoController: InfoController = Service.get[InfoController]
    lazy val superAdminController = Service.get[SuperAdminController]
    lazy val tenantAdminController = Service.get[TenantAdminController]
    lazy val pocAdminController = Service.get[PocAdminController]
    lazy val resourcesController = Service.get[ResourcesController]
    lazy val pocEmployeeController = Service.get[PocEmployeeController]

    context.setInitParameter("org.scalatra.cors.preflightMaxAge", "5")
    context.setInitParameter("org.scalatra.cors.allowCredentials", "false")
    context.setInitParameter("org.scalatra.environment", "production")

    context.mount(
      handler = infoController,
      urlPattern = "/",
      name = "Info"
    )
    context.mount(
      handler = superAdminController,
      urlPattern = "/super-admin",
      name = "super-admin"
    )
    context.mount(
      handler = tenantAdminController,
      urlPattern = "/tenant-admin",
      name = "tenant-admin"
    )
    context.mount(
      handler = pocAdminController,
      urlPattern = "/poc-admin",
      name = "poc-admin"
    )
    context.mount(
      handler = pocEmployeeController,
      urlPattern = "/poc-employee",
      name = "poc-employee"
    )
    context.mount(
      handler = resourcesController,
      urlPattern = "/api-docs",
      name = "Resources"
    )
  }

}
