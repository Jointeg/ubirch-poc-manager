import { ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';
import { KeycloakService } from 'keycloak-angular';
import { SUPER_ADMIN } from '../core/models/roles';
import { fakeRouterState } from './fake-router-snapshot';

import { NOT_AUTHORIZED_URL, SuperAdminGuard } from './super-admin.guard';

describe('SuperAdminGuardGuard', () => {
    const dummyRoute = {} as ActivatedRouteSnapshot;
    let routerSpy: jasmine.SpyObj<Router>;
    let serviceSpy: jasmine.SpyObj<KeycloakService>;
    let guard: SuperAdminGuard;

    beforeEach(() => {
        routerSpy = jasmine.createSpyObj<Router>('Router', ['navigate']);
        serviceSpy = jasmine.createSpyObj<KeycloakService>('KeycloakService', ['isLoggedIn', 'getUserRoles',]);
        serviceSpy.isLoggedIn.and.resolveTo(true);
        guard = new SuperAdminGuard(routerSpy, serviceSpy);
    });

    it('should be created', () => {
        expect(guard).toBeTruthy();
    });

    it('should reject when missing super_admin role', async() => {
        serviceSpy.getUserRoles.and.returnValue(['dummy_role']);
        const allowed = await guard.canActivate(dummyRoute, fakeRouterState(''));
        expect(allowed).toBe(false);
    });

    it('should navigate to not-authenticated when missing super_admin role', async() => {
        serviceSpy.getUserRoles.and.returnValue(['dummy_role']);
        const allowed = await guard.canActivate(dummyRoute, fakeRouterState(''));
        expect(routerSpy.navigate).toHaveBeenCalledTimes(1);
        expect(routerSpy.navigate).toHaveBeenCalledWith([NOT_AUTHORIZED_URL]);
    });
    
    
    it('should allow user with super_admin role', async() => {
        serviceSpy.getUserRoles.and.returnValue(['dummy_role', SUPER_ADMIN]);
        const allowed = await guard.canActivate(dummyRoute, fakeRouterState(''));
        expect(allowed).toBe(true);
    });
    
});
