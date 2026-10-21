package cc.sumgyeol.app;

import java.io.File;

import android.app.ActivityManager;
import android.content.Context;

// 화면/타일에서 쓰는 상태 확인 (엔진 라이브러리를 불러오지 않음)
public class VpnState {
	public static final String PROCESS_SUFFIX = ":native";

	// VPN 서비스 프로세스가 실제로 살아 있으면 연결된 것
	public static boolean isRunning(Context c) {
		try {
			ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
			String name = c.getPackageName() + PROCESS_SUFFIX;
			java.util.List<ActivityManager.RunningAppProcessInfo> list = am.getRunningAppProcesses();
			if (list == null) return false;
			for (ActivityManager.RunningAppProcessInfo p : list) {
				if (name.equals(p.processName)) return true;
			}
		} catch (Exception e) {
		}
		return false;
	}

	public static File appLogFile(Context c) { return new File(c.getCacheDir(), "app.log"); }
	public static File engineLogFile(Context c) { return new File(c.getCacheDir(), "tunnel.log"); }
}
