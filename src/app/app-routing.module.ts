import { NgModule } from '@angular/core';
import { PreloadAllModules, RouterModule, Routes } from '@angular/router';
import { AuthGuard } from './core/auth/auth.guard';
import { TenantAdminGuard } from './core/auth/tenant-admin.guard';

const routes: Routes = [
    {
        path: '',
        redirectTo: 'dashboard',
        pathMatch: 'full',
    },
    {
        path: 'dashboard',
        loadChildren: () =>
            import('./views/dashboard/dashboard.module').then((m) => m.DashboardModule),
        canActivate: [AuthGuard],
    },
    {
        path: 'tenant-pocs',
        loadChildren: () =>
            import('./views/tenant-pocs/tenant-pocs.module').then((m) => m.TenantPocsModule),
        canActivate: [TenantAdminGuard],
    },
    {
        path: 'import',
        loadChildren: () =>
            import('./views/import/import.module').then((m) => m.ImportModule),
        canActivate: [TenantAdminGuard],
    },
];

@NgModule({
    imports: [
        RouterModule.forRoot(routes, { preloadingStrategy: PreloadAllModules }),
    ],
    exports: [RouterModule],
})
export class AppRoutingModule { }
