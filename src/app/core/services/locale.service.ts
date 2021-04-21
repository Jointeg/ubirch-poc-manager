import { Injectable } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

@Injectable({
  providedIn: 'root'
})
export class LocaleService {

  constructor(private translate: TranslateService) {
    translate.setDefaultLang('de');
    translate.use('de');
  }

  getLanguage() {
    return this.translate.currentLang;
  }

  changeLanguage(lang: string) {
    this.translate.use(lang);
  }


}
