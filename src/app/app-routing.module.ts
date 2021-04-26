import { NgModule } from '@angular/core';
import { PreloadAllModules, RouterModule, Routes } from '@angular/router';
import { AuthGuard } from './core/auth/auth.guard';
import { TenantAdminGuard } from './core/auth/tenant-admin.guard';

const routes: Routes = [
    {
        path: '',
        redirectTo: 'views',
        pathMatch: 'full',
    },
    {
        path: 'views',
        loadChildren: () =>
            import('./views/views.module').then((m) => m.ViewsModule),
        canActivate: [AuthGuard],
    },
];

@NgModule({
    imports: [
        RouterModule.forRoot(routes, { preloadingStrategy: PreloadAllModules, enableTracing: true }),
    ],
    exports: [RouterModule],
})
export class AppRoutingModule { }
