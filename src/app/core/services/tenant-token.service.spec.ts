import { TestBed } from '@angular/core/testing';

import { TenantTokenService } from './tenant-token.service';

describe('TenantTokenService', () => {
  let service: TenantTokenService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(TenantTokenService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
