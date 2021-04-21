import { AfterViewInit, Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { fromEvent, merge, Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, filter, mapTo, takeUntil, tap } from 'rxjs/operators';
import { PocFilters } from 'src/app/core/models/poc-filters';
import { PocDataSource } from 'src/app/core/services/data-sources/poc-data-source';
import { PocsService } from 'src/app/core/services/pocs.service';
import { DEFAULT_PAGE_SIZE, PAGE_SIZES } from 'src/app/core/utils/constants';

@Component({
  selector: 'app-poc-list',
  templateUrl: './poc-list.component.html',
  styleUrls: ['./poc-list.component.scss'],
})
export class PocListComponent implements OnInit, AfterViewInit, OnDestroy {

  @ViewChild(MatPaginator) paginator: MatPaginator;
  @ViewChild(MatSort) sort: MatSort;
  // @ViewChild('input') input: ElementRef;

  dataSource: PocDataSource;
  displayedColumns: string[] = [
    'pocId',
    'name',
    'deviceId',
    'folderIdentifier',
    'createdAt',
    'updatedAt',
    'status',
  ];
  defaultPageSize = DEFAULT_PAGE_SIZE;
  pageSizes = PAGE_SIZES;

  filters: FormGroup;
  get search() { return this.filters.get('search'); }

  private unsubscribe$ = new Subject();

  constructor(
    private pocService: PocsService,
    private fb: FormBuilder,
  ) { }

  ngOnInit() {
    this.dataSource = new PocDataSource(this.pocService);
    this.dataSource.loadPocs(new PocFilters());
    this.generateFilters();
  }

  ngAfterViewInit(): void {
    const search$ = this.search.valueChanges.pipe(
      filter(() => this.search.valid),
      distinctUntilChanged(),
      debounceTime(1000),
      mapTo(search => ({ search })),
    );

    const sort$ = this.sort.sortChange.pipe(
      mapTo(sort => ({
        pageIndex: 0,
        sortColumn: sort.active,
        sortOrder: sort.direction,
      }))
    );

    const paginate$ = this.paginator.page.pipe(
      mapTo(page => ({
        pageIndx: page.pageIndex,
        pageSize: page.pageSize
      }))
    );

    merge(search$, sort$, paginate$).pipe(
      tap(filters => {
        this.filters.patchValue(filters);
        this.loadPocPage();
      }),
      takeUntil(this.unsubscribe$),
    ).subscribe();
  }

  ngOnDestroy(): void {
    if (this.unsubscribe$) {
      this.unsubscribe$.next();
      this.unsubscribe$.complete();
    }
  }

  private loadPocPage() {
    this.dataSource.loadPocs(this.filters.value);
  }

  private generateFilters() {
    this.filters = this.fb.group(new PocFilters());
    this.filters.get('search').setValidators([Validators.minLength(3)]);

  }


}
