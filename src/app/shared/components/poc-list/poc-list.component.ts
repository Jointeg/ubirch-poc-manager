import { AfterViewInit, Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { Subject } from 'rxjs';
import { takeUntil, tap } from 'rxjs/operators';
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

  private unsubscribe$ = new Subject();

  constructor(private pocService: PocsService) { }

  ngOnInit() {
    this.dataSource = new PocDataSource(this.pocService);
    this.dataSource.loadPocs(undefined, undefined, 0, this.defaultPageSize);
  }

  ngAfterViewInit(): void {
    this.sort.sortChange.pipe(
      tap(s => {
        this.paginator.pageIndex = 0;
        this.loadPocPage();
      }),
      takeUntil(this.unsubscribe$)
    ).subscribe();

    this.paginator.page.pipe(
      tap(() => this.loadPocPage()),
      takeUntil(this.unsubscribe$)
    ).subscribe();
  }

  ngOnDestroy(): void {
    if (this.unsubscribe$) {
      this.unsubscribe$.next();
      this.unsubscribe$.complete();
    }
  }

  private loadPocPage() {
    this.dataSource.loadPocs(this.sort.active, this.sort.direction, this.paginator.pageIndex, this.paginator.pageSize);
  }


}
