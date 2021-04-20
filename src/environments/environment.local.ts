const keycloakConfig: any = {
    url: 'https://id.dev.ubirch.com/auth',
    realm: 'ubirch-default-realm',
    clientId: 'poc-manager-user-access-local'
};

export const environment = {
    production: true,
    keycloak: keycloakConfig,
    anchoringAPIUrl: 'https://api.certify.dev.ubirch.com',
    // verificationPageDefaultUrl: 'https//localhost:1313/v/gd-vcc'
    verificationPageDefaultUrl: 'https//localhost:1313/v',
};
