import com.ubirch.Service
import com.ubirch.controllers.{ PocEmployeeController, ResourcesController }
import org.scalatra.LifeCycle

import javax.servlet.ServletContext

class ScalatraBootstrap extends LifeCycle {

  override def init(context: ServletContext): Unit = {

    lazy val pocEmployeeController = Service.get[PocEmployeeController]
    lazy val resourcesController = Service.get[ResourcesController]

    context.setInitParameter("org.scalatra.cors.preflightMaxAge", "5")
    context.setInitParameter("org.scalatra.cors.allowCredentials", "false")
    context.setInitParameter("org.scalatra.environment", "production")

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
