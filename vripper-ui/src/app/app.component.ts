import { SettingsComponent } from './settings/settings.component';
import { Component, OnInit } from '@angular/core';
import { MatDialog } from '@angular/material';
import { BreakpointObserver, BreakpointState, Breakpoints } from '@angular/cdk/layout';
import { Observable } from 'rxjs';
import { WsConnectionService, WSState } from './ws-connection.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent implements OnInit {
  constructor(public dialog: MatDialog, private breakpointObserver: BreakpointObserver, private ws: WsConnectionService) {
    this.state = this.ws.state;
    this.state.subscribe(e => {
      if (e === WSState.CLOSE) {
        this.dialog.closeAll();
      }
    });
  }

  state: Observable<WSState>;

  isExtraSmall: Observable<BreakpointState> = this.breakpointObserver.observe(Breakpoints.XSmall);

  openSettings(): void {
    const dialogRef = this.dialog.open(SettingsComponent, {
      width: '70%',
      height: '70%',
      maxWidth: '100vw',
      maxHeight: '100vh'
    });

    const smallDialogSubscription = this.isExtraSmall.subscribe(result => {
      if (result.matches) {
        dialogRef.updateSize('100%', '100%');
      } else {
        dialogRef.updateSize('70%', '70%');
      }
    });

    dialogRef.afterClosed().subscribe(result => {
      smallDialogSubscription.unsubscribe();
    });
  }

  noConnectionState(state: WSState): boolean {
    return state === WSState.CLOSE;
  }

  connecting(state: WSState): boolean {
    return state === WSState.CONNECTING;
  }

  ngOnInit() {}
}
