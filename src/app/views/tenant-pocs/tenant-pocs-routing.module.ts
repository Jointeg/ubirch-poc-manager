import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { TenantPocsComponent } from './tenant-pocs/tenant-pocs.component';

const routes: Routes = [
  {
    path: '',
    component: TenantPocsComponent
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class TenantPocsRoutingModule { }
