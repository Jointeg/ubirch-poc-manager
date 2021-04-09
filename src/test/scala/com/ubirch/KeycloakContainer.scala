package com.ubirch

import com.dimafeng.testcontainers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.MountableFile

import java.io.File
import java.time.Duration

class KeycloakContainer(underlying: GenericContainer) extends GenericContainer(underlying) {
  underlying.container.withCopyFileToContainer(
    MountableFile.forHostPath("./keycloak/realms/realm-export.json"),
    "/tmp/realm-export.json")
  underlying.container.withCopyFileToContainer(
    MountableFile.forHostPath("./keycloak/extensions/get_users_by_attributes_extension.jar"),
    "/opt/jboss/keycloak/standalone/deployments/get_users_by_attributes_extension.jar"
  )
}

object KeycloakContainer {

  case class Def()
    extends GenericContainer.Def[KeycloakContainer](
      new KeycloakContainer(
        GenericContainer(
          dockerImage = "quay.io/keycloak/keycloak:11.0.3",
          exposedPorts = List(8080),
          env = Map(
            "KEYCLOAK_USER" -> "admin",
            "KEYCLOAK_PASSWORD" -> "admin",
            "KEYCLOAK_IMPORT" -> "/tmp/realm-export.json"
          ),
          command = List(
            "-c standalone.xml",
            "-b 0.0.0.0",
            "-Dkeycloak.profile.feature.upload_scripts=enabled",
            "-Dkeycloak.profile.feature.scripts=enabled"
          ),
          waitStrategy = Wait.forHttp("/auth").forPort(8080).withStartupTimeout(Duration.ofSeconds(45))
        )
      )
    )

}
