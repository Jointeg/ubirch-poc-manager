import { KeycloakService } from 'keycloak-angular';

import { environment } from '../../../environments/environment';

export function initializer(keycloak: KeycloakService): () => Promise<any> {
    return (): Promise<void> => {
        return new Promise(async (resolve, reject) => {
            try {
                await keycloak.init({
                    config: environment.keycloak,
                    initOptions: {
                        // onLoad: 'login-required',
                        onLoad: 'check-sso',
                        checkLoginIframe: false,
                    },
                    enableBearerInterceptor: true,
                });
                resolve();
            } catch (error) {
                reject(error);
            }
        });
    };
}
