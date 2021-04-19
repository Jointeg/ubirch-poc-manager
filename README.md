# Ubirch POC Service

REST API for the UBIRCH POC Service

## Getting started

These instructions will get you a copy of the project up and running on your local machine for development and testing
purposes.

### Starting the project

Run the `docker compose up` command to set up all dependant services. After that, project can be started by executing
the main function in the com.ubirch.Service.

In order to make the polling service work correctly, You have to manually create a user in Keycloak `test-realm` realm
and assign him an `admin` role. The username and password has to be same as set
in `application.conf` (`keycloak.client.adminUsername`
, `keycloak.client.adminPassword`). After that, You should see logs indicating how many users were confirmed, but the
mail to POC manager was not sent yet.

## Database and migrations

POC-Service uses `PostgreSQL` as DB and `Flyway` for data migration. All the data is stored inside custom `poc_manager`
schema.

If You ran service locally, then invoking `docker compose up` set up clean DB and performs migration automatically. So
each time You ran it, You will be working on latest possible DB version.

If You want to perform migration manually, then You have to invoke
command `mvn clean flyway:migrate -Dflyway.configFiles=flyway.conf`, where `flyway.conf` is a configuration file with
User/Password/DB URL information.

## POC user confirmation-mail-sent attribute

Each user that will be created in a Keycloak via POC-service will have assigned  `confirmation-mail-sent` attribute set
to `false`. This flag will be used by polling mechanism, that will retrieve all users who had completed the
registration, but mail to POC-manager was not sent yet. Once the mail will be sent, we will change the state of this
attribute to `true`.

## Running the tests

Tests can be run with ```mvn clean test```.

Each test that extends the `E2ETestBase` will create a whole new environment for each test suite. New environment means
fresh Keycloak and Postgres instance. Keycloak will be initialized with realm data that can be found
in `keycloak/realms/realm-export.json`. Postgres will be created as empty DB and the `flyway`
migration will be performed to run tests in newest possible DB version.

TODO: Create `UnitTestBase` that will work in memory (without containers or any other external communication). Those
tests should be the core of the service and unlike the `E2E`, they will run fast

## Deployment

All the required attributes that needs to be set up are listed in `application-docker.conf`. Following list will contain
only those, that might be unclear:

* `keycloak.users.realm` - Realm where the users created by POC-Service will be stored.
* `keycloak.polling.interval` - Interval (in seconds) that indicates how often POC-Service will poll Keycloak for newly
  registered users (look at `confirmation-mail-sent` attribute).
* `keycloak.client.config` - Config of a client that will be used for polling purposes. It should be in format similar
  to:
  ` """{ "realm": "test-realm", "auth-server-url": "http://localhost:8080/auth", "ssl-required": "external", "resource": "ubirch-2.0-user-access-local", "credentials": { "secret": "ca942e9b-8336-43a3-bd22-adcaf7e5222f" }, "confidential-port": 0 }""" `
  with replaced data where it is needed. Similar configuration is already used by `web-ui-rest` application. It is used
  by a polling service in order to obtain an entry point for Keycloak Authorization Services (for example for
  obtaining `Access Token` for polling admin user which is defined below)
* `keycloak.client.adminUsername` - Username of a user with `admin` role assigned. It will be used by a polling service
  to authenticate and obtain data from Keycloak.
* `keycloak.client.adminPassword` - Password of a user with `admin` role assigned. It will be used by a polling service
  to authenticate and obtain data from Keycloak.
* `database.dataSourceClassName` - Represents class name of DataSource that will be used by POC-Service. We are using
  PostgreSQL so it will be `org.postgresql.ds.PGSimpleDataSource`.
* `database.dataSource.serverName` - Hostname of DB.   

## Built With

Scalatra - The web framework used.

Maven - Dependency Management.

KeyCloak - The user management system.
