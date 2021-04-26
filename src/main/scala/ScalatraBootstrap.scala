import com.ubirch.Service
import com.ubirch.controllers.{ InfoController, ResourcesController, SuperAdminController }
import org.scalatra.LifeCycle

import javax.servlet.ServletContext

class ScalatraBootstrap extends LifeCycle {

  override def init(context: ServletContext): Unit = {

    lazy val infoController: InfoController = Service.get[InfoController]
    lazy val superAdminController = Service.get[SuperAdminController]
    lazy val resourcesController = Service.get[ResourcesController]

    context.setInitParameter("org.scalatra.cors.preflightMaxAge", "5")
    context.setInitParameter("org.scalatra.cors.allowCredentials", "false")

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
      handler = resourcesController,
      urlPattern = "/api-docs",
      name = "Resources"
    )
  }

}
