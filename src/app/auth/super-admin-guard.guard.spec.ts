import { TestBed } from '@angular/core/testing';

import { SuperAdminGuard } from './super-admin.guard';

describe('SuperAdminGuardGuard', () => {
    let guard: SuperAdminGuard;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        guard = TestBed.inject(SuperAdminGuard);
    });

    it('should be created', () => {
        expect(guard).toBeTruthy();
    });
});
