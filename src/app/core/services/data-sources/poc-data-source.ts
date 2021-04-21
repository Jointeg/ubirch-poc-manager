import { CollectionViewer, DataSource } from '@angular/cdk/collections';
import { SortDirection } from '@angular/material/sort';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { catchError, finalize } from 'rxjs/operators';
import { Poc } from '../../models/interfaces/poc.interface';
import { PocsService } from '../pocs.service';

export class PocDataSource implements DataSource<Poc> {

    private pocSubject = new BehaviorSubject<Poc[]>([]);
    private loadingSubject = new BehaviorSubject<boolean>(false);

    public loading$ = this.loadingSubject.asObservable();

    constructor(private service: PocsService) { }

    connect(collectionViewer: CollectionViewer): Observable<Poc[] | readonly Poc[]> {
        return this.pocSubject.asObservable();
    }

    disconnect(collectionViewer: CollectionViewer): void {
        this.pocSubject.complete();
        this.loadingSubject.complete();
    }

    loadPocs(sortColumn: string, sortDirection: SortDirection = 'asc', pageNo: number = 0, perPage: number = 5,) {
        this.loadingSubject.next(true);

        this.service.loadPocs(sortColumn, sortDirection, pageNo, perPage).pipe(
            catchError(() => of([])),
            finalize(() => this.loadingSubject.next(false))
        ).subscribe(pocs => this.pocSubject.next(pocs));

    }
}
