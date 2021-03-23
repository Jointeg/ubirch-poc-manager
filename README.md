# Ubirch POC Manager

REST API for the UBIRCH POC Manager

## Getting started

These instructions will get you a copy of the project up and running on your local machine for development and testing
purposes. See deployment for notes on how to deploy the project on a live system.

### Prerequisites

What things you need to run the REST api and how to install them.

* **Keycloak server:**
    * Standalone installation:
        * Get the KeyCloak server zip [here](https://www.keycloak.org/downloads.html), extract it and
          run ```bin/standalone.sh```. This will start a local KeyCloak instance on port 8080. Configure it by logging
          in the KeyCloak web admin interface [here](http://localhost:8080/auth) and creating a new admin user.
        * Create a new realm, called **test-realm**.
        * In this realm, create a new client called _ubirch-2.0-user-access-local_ and configure it with the following
          informations:
            * Root URL: http://localhost:9101
            * Valid Redirect URIs: http://localhost:9101/\*
            * Admin URL: http://localhost:9101/
            * Web Origins: http://localhost:9101/
            * Access Token Signature Algorithm: ES256
            * ID Token Signature Algorithm: ES256
            * User Info Signed Response Algorithm: None
        * Access the jwk of the newly created
          client [here](http://localhost:8080/auth/realms/test-realm/protocol/openid-connect/certs) and pass it in
          core/src/main/resources/application.base.conf. Also change the relevant information in this file if need be.
    * Docker installation:
        * Execute
          command `docker run -e KEYCLOAK_USER=admin -e KEYCLOAK_PASSWORD=admin -e KEYCLOAK_IMPORT=/tmp/realm-export.json -e JAVA_OPTS="-Dkeycloak.profile.feature.scripts=enabled -Dkeycloak.profile.feature.upload_scripts=enabled" -v ~/IdeaProjects/ubirch-poc-manager/realms:/tmp -p 8080:8080 quay.io/keycloak/keycloak:11.0.3`
        * You can access Keycloak instance via http://localhost:8080, username: admin, password: admin. Test-realm
          mentioned in `standalone-installation` are already performed and imported during the startup.
        * To find out kid access click [here](http://localhost:8080/auth/realms/test-realm/protocol/openid-connect/certs)
* **PostMan** (optional). Provide an easy way to send REST requests to the server and obtaining access token delivered
  by KeyCloak. Installation instructions can be found on the project [webpage](https://www.getpostman.com/downloads/).

### Starting the project

This project can be started by executing the main function in the com.ubirch.Service.

## Running the tests

Tests can be run with
```mvn clean test```.

## Built With

Scalatra - The web framework used. Maven - Dependency Management. KeyCloak - The user management system.
