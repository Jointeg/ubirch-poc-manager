import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { TenantAdminGuard } from 'src/app/core/auth/tenant-admin.guard';
import { DashboardComponent } from './dashboard.component';

const routes: Routes = [
  {
    path: '',
    component: DashboardComponent,
    canActivate: [TenantAdminGuard]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class DashboardRoutingModule { }
