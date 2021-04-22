import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { SortDirection } from '@angular/material/sort';
import { Observable, of } from 'rxjs';
import { delay } from 'rxjs/operators';
import { PocStatus } from '../models/enums/poc-status.enum';
import { ListResult } from '../models/interfaces/poc-list-result';
import { Poc } from '../models/interfaces/poc.interface';
import { PocFilters } from '../models/poc-filters';


const MOCK_DATA: Poc[] = [
  { id: 1, pocId: 1, name: 'POC #1', deviceId: '33qwe234erw', folderIdentifier: 'poc_folder_1', createdAt: new Date, updatedAt: new Date, status: PocStatus.ready },
  { id: 2, pocId: 2, name: 'POC #2', deviceId: '33qwe234erw', folderIdentifier: 'poc_folder_2', createdAt: new Date, updatedAt: new Date, status: PocStatus.ready },
  { id: 3, pocId: 3, name: 'POC #3', deviceId: 'AAqwe234erw', folderIdentifier: 'poc_folder_3', createdAt: new Date, status: PocStatus.processing },
  { id: 4, pocId: 4, name: 'POC #4', deviceId: '33qwe234erw', folderIdentifier: 'poc_folder_4', createdAt: new Date, status: PocStatus.pending },
  { id: 5, pocId: 5, name: 'POC #5', deviceId: '55qwe234erw', folderIdentifier: 'poc_folder_5', createdAt: new Date, updatedAt: new Date, status: PocStatus.ready },
  { id: 6, pocId: 6, name: 'POC #6', deviceId: '33qwe234erw', folderIdentifier: 'poc_folder_6', createdAt: new Date, updatedAt: new Date, status: PocStatus.ready },
  { id: 7, pocId: 7, name: 'POC #7', deviceId: '33qwe234erw', folderIdentifier: 'poc_folder_7', createdAt: new Date, updatedAt: new Date, status: PocStatus.ready },
  { id: 8, pocId: 8, name: 'POC #8', deviceId: '33qwe234erw', folderIdentifier: 'poc_folder_8', createdAt: new Date, status: PocStatus.processing },
  { id: 9, pocId: 9, name: 'POC #9', deviceId: '33qwe234erw', folderIdentifier: 'poc_folder_9', createdAt: new Date, status: PocStatus.pending },
  { id: 10, pocId: 10, name: 'POC #10', deviceId: '33qwe234erw', folderIdentifier: 'poc_folder_10', createdAt: new Date, updatedAt: new Date, status: PocStatus.ready },
];

@Injectable({
  providedIn: 'root'
})
export class PocsService {

  constructor(private http: HttpClient) { }

  loadPocs(filters: PocFilters): Observable<ListResult<Poc>> {
    let data = MOCK_DATA;

    if (filters.search?.trim()) {
      data = data.filter(r => JSON.stringify(r).includes(filters.search));
    }

    if (filters.sortColumn) {
      data = data.slice().sort((a: Poc, b: Poc) => {
        if (typeof a[filters.sortColumn] === 'number') { return a[filters.sortColumn] - b[filters.sortColumn]; }
        else { return ('' + a[filters.sortColumn]).localeCompare(b[filters.sortColumn]); }
      });
      data = filters.sortOrder === 'asc' ? data : data.reverse();
    }

    return of({
      total: data.length,
      pocs: data.slice(filters.pageIndex * filters.pageSize, filters.pageIndex * filters.pageSize + filters.pageSize)
    } as ListResult<Poc>).pipe(delay(1000));

    // TODO: Implement actual processing of datafetching
  }

}
