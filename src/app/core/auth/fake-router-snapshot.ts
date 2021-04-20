import { RouterStateSnapshot } from '@angular/router';

export const fakeRouterState = (url: string): RouterStateSnapshot => ({
  url,
} as RouterStateSnapshot);
