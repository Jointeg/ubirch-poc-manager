import { ActivatedRouteSnapshot, Router } from '@angular/router';
import { KeycloakService } from 'keycloak-angular';

import { AuthGuard } from './auth.guard';
import { fakeRouterState } from './fake-router-snapshot';



describe('AuthGuard', () => {
  const dummyRoute = {} as ActivatedRouteSnapshot;
  let routerSpy: Router;
  let serviceSpy: jasmine.SpyObj<KeycloakService>;
  let guard: AuthGuard;

  beforeEach(() => {
    routerSpy = {} as Router;
    serviceSpy = jasmine.createSpyObj<KeycloakService>('KeycloakService', ['login', 'isLoggedIn']);
    guard = new AuthGuard(routerSpy, serviceSpy);
  });

  it('should be created', () => {
    expect(guard).toBeTruthy();
  });

  it('should grant access for authenticated user', async () => {
    serviceSpy.isLoggedIn.and.returnValue(new Promise(() => true));
    const allowed = await guard.isAccessAllowed(dummyRoute, fakeRouterState('any'));
    expect(allowed).toBe(true);
  });

  it('should reject access for non-authenticated user', async () => {
    serviceSpy.isLoggedIn.and.returnValue(new Promise(() => false));
    const allowed = await guard.isAccessAllowed(dummyRoute, fakeRouterState('any'));
    expect(allowed).toBe(false);
  });

  it('should tirgger login function with redirect uri for non-authenticated user', () => {
    const url = '/not-authorized';
    serviceSpy.isLoggedIn.and.returnValue(new Promise(() => false));
    guard.isAccessAllowed(dummyRoute, fakeRouterState(url));
    expect(serviceSpy.login).toHaveBeenCalled();
    expect(serviceSpy.login).toHaveBeenCalledTimes(1);
    expect(serviceSpy.login).toHaveBeenCalledWith({ redirectUri: window.location.origin + url });
  });

});

