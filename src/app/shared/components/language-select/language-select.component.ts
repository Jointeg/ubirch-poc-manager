import { Component, OnInit } from '@angular/core';
import { LocaleService } from 'src/app/core/services/locale.service';

@Component({
  selector: 'app-language-select',
  templateUrl: './language-select.component.html',
  styleUrls: ['./language-select.component.scss'],
})
export class LanguageSelectComponent implements OnInit {

  constructor(private languageService: LocaleService) { }

  ngOnInit() { }

  get language() { return this.languageService.getLanguage(); }

  setLanguage(lang: string) {
    this.languageService.changeLanguage(lang);
  }

}
