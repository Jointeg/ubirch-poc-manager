import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from '../core/auth/auth.guard';
import { TenantAdminGuard } from '../core/auth/tenant-admin.guard';
import { ViewsComponent } from './views.component';

const routes: Routes = [
  {
    path: '',
    component: ViewsComponent,
    children: [
      {
        path: '',
        redirectTo: 'dashboard',
        pathMatch: 'full'
      },
      {
        path: 'dashboard',
        loadChildren: () =>
          import('./dashboard/dashboard.module').then((m) => m.DashboardModule),
        canActivate: [AuthGuard],
      },
      {
        path: 'tenant-pocs',
        loadChildren: () =>
          import('./tenant-pocs/tenant-pocs.module').then((m) => m.TenantPocsModule),
        canActivate: [TenantAdminGuard],
      },
      {
        path: 'import',
        loadChildren: () =>
          import('./import/import.module').then((m) => m.ImportModule),
        canActivate: [TenantAdminGuard],
      }

    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class ViewsRoutingModule { }
