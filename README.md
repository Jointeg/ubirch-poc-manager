# Ubirch POC Manager

REST API for the UBIRCH POC Manager

## Getting started

These instructions will get you a copy of the project up and running on your local machine for development and testing
purposes.

### Starting the project

Run the `docker compose up` command to set up all dependant services. After that, project can be started by executing
the main function in the com.ubirch.Service.

In order to make the polling service work correctly, You have to manually create a user in Keycloak `test-realm` realm and assign him
an `admin` role. The username and password has to be same as set in `application.conf` (`keycloak.client.adminUsername`
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

## Built With

Scalatra - The web framework used.

Maven - Dependency Management.

KeyCloak - The user management system.
