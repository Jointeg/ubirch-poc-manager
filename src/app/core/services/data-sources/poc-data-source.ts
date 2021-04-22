import { CollectionViewer, DataSource } from '@angular/cdk/collections';
import { SortDirection } from '@angular/material/sort';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { catchError, finalize } from 'rxjs/operators';
import { ListResult } from '../../models/interfaces/poc-list-result';
import { Poc } from '../../models/interfaces/poc.interface';
import { PocFilters } from '../../models/poc-filters';
import { PocsService } from '../pocs.service';

export class PocDataSource implements DataSource<Poc> {

    private pocSubject = new BehaviorSubject<Poc[]>([]);
    private loadingSubject = new BehaviorSubject<boolean>(false);
    private totalItemsSubject = new BehaviorSubject<number>(0);

    public loading$ = this.loadingSubject.asObservable();
    public totalItems$ = this.totalItemsSubject.asObservable();

    get data() { return this.pocSubject.value; }

    constructor(private service: PocsService) { }

    connect(collectionViewer: CollectionViewer): Observable<Poc[] | readonly Poc[]> {
        return this.pocSubject.asObservable();
    }

    disconnect(collectionViewer: CollectionViewer): void {
        this.pocSubject.complete();
        this.loadingSubject.complete();
    }

    loadPocs(filters: PocFilters) {
        this.loadingSubject.next(true);

        this.service.loadPocs(filters).pipe(
            catchError(() => of({} as ListResult<Poc>)),
            finalize(() => this.loadingSubject.next(false))
        ).subscribe(pocResult => {
            this.pocSubject.next(pocResult.pocs ?? []);
            this.totalItemsSubject.next(pocResult.total ?? 0);
        });

    }
}
