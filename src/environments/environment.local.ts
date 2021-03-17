const keycloakConfig: any = {
    realm: 'ubirch-certificator',
    url: 'https://id.dev.ubirch.com/auth/',
    clientId: 'admin-access-local',
};

export const environment = {
    production: true,
    keycloak: keycloakConfig,
    anchoringAPIUrl: 'https://api.certify.dev.ubirch.com',
    // verificationPageDefaultUrl: 'https//localhost:1313/v/gd-vcc'
    verificationPageDefaultUrl: 'https//localhost:1313/v',
};
