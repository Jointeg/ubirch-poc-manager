import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { PocListComponent } from './poc-list/poc-list.component';

const routes: Routes = [
  {
    path: '',
    component: PocListComponent
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class PocListRoutingModule { }
