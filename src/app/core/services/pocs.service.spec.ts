import { TestBed } from '@angular/core/testing';

import { PocsService } from './pocs.service';

describe('PocsService', () => {
  let service: PocsService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(PocsService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
