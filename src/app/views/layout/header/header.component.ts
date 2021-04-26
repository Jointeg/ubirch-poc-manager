import { Component, HostBinding, OnInit } from '@angular/core';
import { KeycloakService } from 'keycloak-angular';

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss'],
})
export class HeaderComponent implements OnInit {

  @HostBinding('class') class = 'dark-theme mat-app-background';
  constructor(private keycloak: KeycloakService) { }

  ngOnInit() { }

  logout() {
    this.keycloak.logout();
  }

}
