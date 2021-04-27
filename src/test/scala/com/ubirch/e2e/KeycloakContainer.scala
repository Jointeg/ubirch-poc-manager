package com.ubirch.e2e

import com.dimafeng.testcontainers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.MountableFile

import java.time.Duration

class KeycloakContainer(underlying: GenericContainer, mountExtension: Boolean, realmExportFile: String)
  extends GenericContainer(underlying) {
  underlying.container.withCopyFileToContainer(
    MountableFile.forHostPath(s"./keycloak/realms/$realmExportFile"),
    s"/tmp/$realmExportFile")
  if (mountExtension) {
    underlying.container.withCopyFileToContainer(
      MountableFile.forHostPath("./keycloak/extensions/get_users_by_attributes_extension.jar"),
      "/opt/jboss/keycloak/standalone/deployments/get_users_by_attributes_extension.jar"
    )
  }
}

object KeycloakContainer {

  case class Def(mountExtension: Boolean, realmExportFile: String)
    extends GenericContainer.Def[KeycloakContainer](
      new KeycloakContainer(
        GenericContainer(
          dockerImage = "quay.io/keycloak/keycloak:11.0.3",
          exposedPorts = List(8080),
          env = Map(
            "KEYCLOAK_USER" -> "admin",
            "KEYCLOAK_PASSWORD" -> "admin",
            "KEYCLOAK_IMPORT" -> s"/tmp/$realmExportFile"
          ),
          command = List(
            "-c standalone.xml",
            "-b 0.0.0.0",
            "-Dkeycloak.profile.feature.upload_scripts=enabled",
            "-Dkeycloak.profile.feature.scripts=enabled"
          ),
          waitStrategy = Wait.forHttp("/auth").forPort(8080).withStartupTimeout(Duration.ofSeconds(45))
        ),
        mountExtension,
        realmExportFile
      )
    )

}
