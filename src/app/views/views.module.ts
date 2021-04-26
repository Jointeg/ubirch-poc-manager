import { NgModule } from '@angular/core';

import { ViewsRoutingModule } from './views-routing.module';
import { SharedModule } from '../shared/shared.module';
import { ViewsComponent } from './views.component';
import { IonicModule } from '@ionic/angular';


@NgModule({
  declarations: [ViewsComponent],
  imports: [
    SharedModule,
    IonicModule,
    ViewsRoutingModule
  ]
})
export class ViewsModule { }
