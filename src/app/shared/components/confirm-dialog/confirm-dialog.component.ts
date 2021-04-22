import { Component, Inject } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';

@Component({
  selector: 'app-confirm-dialog',
  templateUrl: './confirm-dialog.component.html',
  styleUrls: ['./confirm-dialog.component.scss'],
})
export class ConfirmDialogComponent {
  constructor(
    public dialogRef: MatDialogRef<ConfirmDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: ConfirmDialogModel,
  ) { }

  onConfirm(): void { this.dialogRef.close(true); }
  onDismiss(): void { this.dialogRef.close(false); }
}

export class ConfirmDialogModel {
  constructor(
    public title: string = 'dialog.defaultTitle',
    public message: string = 'dialog.defaultMessage',
    public yes: string = 'dialog.yesLabel',
    public no: string = 'dialog.noLabel',
  ) { }
}
