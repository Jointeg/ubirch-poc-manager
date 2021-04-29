# Ubirch POC Service

REST API for the UBIRCH POC Service

## Getting started

These instructions will get you a copy of the project up and running on your local machine for development and testing
purposes.

### Starting the project

Run the `docker compose up` command to set up all dependant services.

Go to `    "http://localhost:8080/auth/realms/test-realm/protocol/openid-connect/certs"` and obtain KID of ES256 key.
Replace the value in `application.base.conf` of `keycloak-users.tokenVerification.kid` with obtained KID. Go
to `    "http://localhost:8081/auth/realms/test-realm/protocol/openid-connect/certs"` and obtain KID of ES256 key.
Replace the value in `application.base.conf` of `keycloak-device.tokenVerification.kid` with obtained KID.

After that, project can be started by executing the main function in the com.ubirch.Service.

In order to make the polling service work correctly, You have to manually create a user in Keycloak `test-realm` realm
and assign him an `admin` role. The username and password has to be same as set
in `application.conf` (`keycloak-users.client.adminUsername`
, `keycloak-users.client.adminPassword`). After that, You should see logs indicating how many users were confirmed, but
the mail to POC manager was not sent yet. Polling service works only against `Users` Keycloak.

## Database and migrations

POC-Service uses `PostgreSQL` as DB and `Flyway` for data migration. All the data is stored inside custom `poc_manager`
schema.

If You ran service locally, then invoking `docker compose up` set up clean DB and performs migration automatically. So
each time You ran it, You will be working on latest possible DB version.

## Keycloaks

POC-manager operates by using two different instances of Keycloaks. One is named `device` and will be used mainly by
SUPER_ADMINs to create POCs / POC-admins. Second one, called `users`, will be default store for all POC employees and
POC-admins.

## POC user confirmation-mail-sent attribute

Each user that will be created in a Keycloak via POC-service will have assigned  `confirmation-mail-sent` attribute set
to `false`. This flag will be used by polling mechanism, that will retrieve all users who had completed the
registration, but mail to POC-manager was not sent yet. Once the mail will be sent, we will change the state of this
attribute to `true`.

## Running the tests

Tests can be run with ```mvn clean test```.

Each test that extends the `E2ETestBase` will create a whole new environment for each test suite. New environment means
fresh Keycloaks and Postgres instance. Keycloaks will be initialized with realm data that can be found
in `keycloak/realms/device-export.json` and `keycloak/realms/users-export.json`. Postgres will be created as empty DB
and the `flyway`migration will be performed to run tests in newest possible DB version.

Each test that extends `UnitTestBase` will be run against in-memory implementations of DB/Keycloaks. They will run fast,
focus and should focus on testing the correctness of the modelled flow, rather than the integrations. Due to the nature
of such tests, some functionalities might not fully work (for example UserPolling).

## Deployment

All the required attributes that needs to be set up are listed in `application-docker.conf`. Following list will contain
only those, that might be unclear:

* `system.aesEncryption.secretKey` - 256 bit secret key that will be used for encrypting DeviceCreationToken and
  CertificationCreationToken before storing them in DB
* `keycloak-users.realm` - Realm where the users created by POC-Service will be stored.
* `keycloak-users.polling.interval` - Interval (in seconds) that indicates how often POC-Service will poll Keycloak for
  newly registered users (look at `confirmation-mail-sent` attribute).
* `keycloak-users.client.config` - Config of a client that will be used for polling purposes. It should be in format
  similar to:
  ` """{ "realm": "test-realm", "auth-server-url": "http://localhost:8080/auth", "ssl-required": "external", "resource": "ubirch-2.0-user-access-local", "credentials": { "secret": "ca942e9b-8336-43a3-bd22-adcaf7e5222f" }, "confidential-port": 0 }""" `
  with replaced data where it is needed. Similar configuration is already used by `web-ui-rest` application. It is used
  by a polling service in order to obtain an entry point for Keycloak Authorization Services (for example for
  obtaining `Access Token` for polling admin user which is defined below)
* `keycloak-users.client.adminUsername` - Username of a user with `admin` role assigned. It will be used by a polling
  service to authenticate and obtain data from Keycloak.
* `keycloak-users.client.adminPassword` - Password of a user with `admin` role assigned. It will be used by a polling
  service to authenticate and obtain data from Keycloak.
* `keycloak-device.realm` - Realm where the users are stored.
* `database.dataSourceClassName` - Represents class name of DataSource that will be used by POC-Service. We are using
  PostgreSQL so it will be `org.postgresql.ds.PGSimpleDataSource`.
* `database.dataSource.serverName` - Hostname of DB.

### Performing DB migration

If You want to perform migration, then You have to invoke
command `mvn clean flyway:migrate -Dflyway.configFiles=flyway.conf`, where `flyway.conf` is a configuration file with
User/Password/DB URL information. It will run migration basing on scripts located in `resources/db/migration` location.

## Built With

Scalatra - The web framework used.

Maven - Dependency Management.

KeyCloak - The user management system.
