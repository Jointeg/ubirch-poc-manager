import { SortDirection } from '@angular/material/sort';
import { DEFAULT_PAGE_SIZE } from '../utils/constants';

export class PocFilters {
    search?: string = undefined;
    sortColumn?: string = undefined;
    sortOrder: SortDirection = 'asc';
    pageIndex = 0;
    pageSize: number = DEFAULT_PAGE_SIZE;
}
