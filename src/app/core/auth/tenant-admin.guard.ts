import { Injectable } from '@angular/core';
import {
    ActivatedRouteSnapshot,
    RouterStateSnapshot,
    Router,
} from '@angular/router';
import { KeycloakService } from 'keycloak-angular';
import { AuthGuard } from './auth.guard';
import { TENANT_ADMIN } from '../models/roles';


export const NOT_AUTHORIZED_URL = 'not-authorized';

@Injectable({
    providedIn: 'root',
})
export class TenantAdminGuard extends AuthGuard {
    constructor(
        protected readonly router: Router,
        protected readonly keycloak: KeycloakService
    ) {
        super(router, keycloak);
    }

    public async isAccessAllowed(
        route: ActivatedRouteSnapshot,
        state: RouterStateSnapshot
    ) {
        const authenticated = await super.isAccessAllowed(route, state);

        if (!this.roles.includes(TENANT_ADMIN)) {
            this.router.navigate([NOT_AUTHORIZED_URL]);
            return false;
        }

        return authenticated;
    }
}
