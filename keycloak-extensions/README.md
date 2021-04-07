# Keycloak extensions

Keycloak Extensions that are needed for POC Manager to work correctly

## Deploying

In order to deploy extension, we have to build it with `mvn clean install`. This will create a JAR, that we have to copy into `standalone/deployments` keycloak folder.
Once the JAR will be in this folder, Keycloak will automatically detect the extension, and it will be available without restarting Keycloak itself. 