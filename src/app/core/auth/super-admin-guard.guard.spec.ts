import { ActivatedRouteSnapshot, Router } from '@angular/router';
import { KeycloakService } from 'keycloak-angular';
import { TENANT_ADMIN } from '../models/roles';
import { fakeRouterState } from './fake-router-snapshot';

import { NOT_AUTHORIZED_URL, TenantAdminGuard } from './tenant-admin.guard';

describe('TenantAdminGuardGuard', () => {
    const dummyRoute = {} as ActivatedRouteSnapshot;
    let routerSpy: jasmine.SpyObj<Router>;
    let serviceSpy: jasmine.SpyObj<KeycloakService>;
    let guard: TenantAdminGuard;

    beforeEach(() => {
        routerSpy = jasmine.createSpyObj<Router>('Router', ['navigate']);
        serviceSpy = jasmine.createSpyObj<KeycloakService>('KeycloakService', ['isLoggedIn', 'getUserRoles']);
        serviceSpy.isLoggedIn.and.resolveTo(true);
        guard = new TenantAdminGuard(routerSpy, serviceSpy);
    });

    it('should be created', () => {
        expect(guard).toBeTruthy();
    });

    it('should reject when missing tenant_admin role', async () => {
        serviceSpy.getUserRoles.and.returnValue(['dummy_role']);
        const allowed = await guard.canActivate(dummyRoute, fakeRouterState(''));
        expect(allowed).toBe(false);
    });

    it('should navigate to not-authenticated when missing tenant_admin role', async () => {
        serviceSpy.getUserRoles.and.returnValue(['dummy_role']);
        const allowed = await guard.canActivate(dummyRoute, fakeRouterState(''));
        expect(routerSpy.navigate).toHaveBeenCalledTimes(1);
        expect(routerSpy.navigate).toHaveBeenCalledWith([NOT_AUTHORIZED_URL]);
    });


    it('should allow user with tenant_admin role', async () => {
        serviceSpy.getUserRoles.and.returnValue(['dummy_role', TENANT_ADMIN]);
        const allowed = await guard.canActivate(dummyRoute, fakeRouterState(''));
        expect(allowed).toBe(true);
    });

});
