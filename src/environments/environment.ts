// This file can be replaced during build by using the `fileReplacements` array.
// `ng build --prod` replaces `environment.ts` with `environment.prod.ts`.
// The list of file replacements can be found in `angular.json`.

const keycloakConfig: any = {
    url: 'https://id.dev.ubirch.com/auth',
    realm: 'ubirch-default-realm',
    clientId: 'poc-manager-user-access-local'
};

export const environment = {
    production: false,
    keycloak: keycloakConfig,
    anchoringAPIUrl: 'https://api.certify.dev.ubirch.com',
    // verificationPageDefaultUrl: 'https://verification.dev.ubirch.com/v/gd-vcc',
    verificationPageDefaultUrl: 'https://verification.dev.ubirch.com/v',
};

/*
 * For easier debugging in development mode, you can import the following file
 * to ignore zone related error stack frames such as `zone.run`, `zoneDelegate.invokeTask`.
 *
 * This import should be commented out in production mode because it will have a negative impact
 * on performance if an error is thrown.
 */
// import 'zone.js/dist/zone-error';  // Included with Angular CLI.
