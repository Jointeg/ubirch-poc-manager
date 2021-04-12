# Keycloak extensions

Keycloak Extensions that are needed for POC Manager to work correctly

## Deploying

In order to deploy extension, we have to build it with `mvn clean install`. This will create a JAR, that we have to copy
into `standalone/deployments` keycloak folder. Once the JAR will be in this folder, Keycloak will automatically detect
the extension, and it will be available without restarting Keycloak itself.

## Get users by attributes extension

One of the POC-Service requirements is to send and email to POC manager when user completes the registration. In order
to do so, we need to have a way to retrieve users that completed the registration, but the email was not sent yet. This
extension provides a possibility to invoke a custom REST endpoint, which returns users with verified emails
and `confirmation_mail_sent` attribute set to `false`.

The endpoint is published under `{keycloak-address}/auth/realms/{realm}/user-search/users-without-confirmation-mail`
path. It can be invoked only by user with assigned `admin` role.