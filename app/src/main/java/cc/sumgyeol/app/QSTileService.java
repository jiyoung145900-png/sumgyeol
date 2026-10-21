package cc.sumgyeol.app;

import android.content.Intent;
import android.net.VpnService;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class QSTileService extends TileService {
	public static void requestUpdate(android.content.Context c) {
		try {
			requestListeningState(c, new android.content.ComponentName(c, QSTileService.class));
		} catch (Exception e) {
		}
	}

	@Override
	public void onStartListening() {
		super.onStartListening();
		updateTile();
	}

	private void updateTile() {
		Tile tile = getQsTile();
		if (tile == null) return;
		boolean on = VpnState.isRunning(this);
		tile.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
		tile.setLabel(getString(R.string.app_name));
		tile.updateTile();
	}

	@Override
	public void onClick() {
		super.onClick();
		Preferences prefs = new Preferences(this);
		if (VpnState.isRunning(this)) {
			Intent i = new Intent(this, TProxyService.class);
			i.setAction(TProxyService.ACTION_DISCONNECT);
			startService(i);
		} else {
			Intent prepare = VpnService.prepare(this);
			if (prepare != null) {
				// 권한 필요 → 앱 열기
				Intent open = new Intent(this, MainActivity.class);
				open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivityAndCollapse(open);
			} else {
				Intent i = new Intent(this, TProxyService.class);
				i.setAction(TProxyService.ACTION_CONNECT);
				startForegroundService(i);
			}
		}
		updateTile();
	}
}
