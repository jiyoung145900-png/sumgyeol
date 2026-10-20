package cc.sumgyeol.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;

public class ServiceReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))
			return;
		Preferences prefs = new Preferences(context);
		if (!prefs.getAutoStart())
			return;
		if (prefs.getServer().isEmpty())
			return;
		// 부팅 자동연결은 VPN 권한이 이미 승인된 경우에만 가능
		if (VpnService.prepare(context) != null)
			return;
		Intent i = new Intent(context, TProxyService.class);
		i.setAction(TProxyService.ACTION_CONNECT);
		context.startForegroundService(i);
	}
}
