import { Component, HostBinding, OnInit } from '@angular/core';
import { KeycloakService } from 'keycloak-angular';
import { from } from 'rxjs';
import { map } from 'rxjs/operators';

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss'],
})
export class HeaderComponent implements OnInit {
  @HostBinding('class') class = 'dark-theme mat-app-background';

  userName: string;
  email: string;

  constructor(private keycloak: KeycloakService) { }

  async ngOnInit() {
    const user = await this.keycloak.loadUserProfile();
    this.userName = `${user.firstName} ${user.lastName}`.trim();
    this.email = user.email;
  }

  logout() {
    this.keycloak.logout();
  }

}
