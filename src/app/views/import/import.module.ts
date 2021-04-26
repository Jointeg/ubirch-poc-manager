import { NgModule } from '@angular/core';

import { ImportRoutingModule } from './import-routing.module';
import { SharedModule } from 'src/app/shared/shared.module';
import { ImportComponent } from './import/import.component';


@NgModule({
  declarations: [
    ImportComponent
  ],
  imports: [
    SharedModule,
    ImportRoutingModule
  ]
})
export class ImportModule { }
