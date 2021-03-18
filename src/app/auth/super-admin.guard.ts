import { Injectable } from '@angular/core';
import {
    ActivatedRouteSnapshot,
    RouterStateSnapshot,
    Router,
} from '@angular/router';
import { KeycloakService } from 'keycloak-angular';
import { AuthGuard } from './auth.guard';
import { SUPER_ADMIN } from '../core/models/roles';


export const NOT_AUTHORIZED_URL = 'not-authorized';

@Injectable({
    providedIn: 'root',
})
export class SuperAdminGuard extends AuthGuard {
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

        if(!this.roles.includes(SUPER_ADMIN)) {
            this.router.navigate([NOT_AUTHORIZED_URL]);
            return false;
        };

        return authenticated;
    }
}
