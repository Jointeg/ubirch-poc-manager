
const keycloakConfig: any = {
  realm: '@@KEYCLOAK_REALM@@',
  url: '@@KEYCLOAK_URL@@',
  clientId: '@@KEYCLOAK_CLIENT_ID@@'
};

export const environment = {
  production: true,
  keycloak: keycloakConfig,
  anchoringAPIUrl: '@@ANCHORING_API_URL@@',
  verificationPageDefaultUrl: '@@VERIFICATION_PAGE_URL@@'
};
