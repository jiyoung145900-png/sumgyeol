package cc.sumgyeol.app;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.content.pm.ServiceInfo;

import androidx.core.app.NotificationCompat;

public class TProxyService extends VpnService {
	private static native boolean TProxyStartService(String config_path, int fd);
	private static native boolean TProxyStopService();
	private static native boolean TProxyIsRunning();
	private static native long[] TProxyGetStats();

	public static final String ACTION_CONNECT = "cc.sumgyeol.app.CONNECT";
	public static final String ACTION_DISCONNECT = "cc.sumgyeol.app.DISCONNECT";

	private static boolean libLoaded = false;
	static {
		try {
			System.loadLibrary("hev-socks5-tunnel");
			libLoaded = true;
		} catch (Throwable t) {
			libLoaded = false;
		}
	}

	private ParcelFileDescriptor tunFd = null;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		try {
			if (intent != null && ACTION_DISCONNECT.equals(intent.getAction())) {
				stopService();
				return START_NOT_STICKY;
			}
			startService();
		} catch (Throwable t) {
			try {
				android.widget.Toast.makeText(this, "오류: " + t.getMessage(), android.widget.Toast.LENGTH_LONG).show();
			} catch (Throwable ignore) {}
			stopService();
		}
		return START_STICKY;
	}

	@Override
	public void onRevoke() {
		stopService();
		super.onRevoke();
	}

	public void startService() {
		if (tunFd != null)
			return;

		// 포그라운드 알림을 "먼저" 띄운다 (안 그러면 최신 안드로이드에서 즉시 강제종료)
		String ch = "sumgyeol";
		initChannel(ch);
		showNotification(ch);

		if (!libLoaded) {
			android.widget.Toast.makeText(this, "엔진 로드 실패 - 앱을 다시 설치해 주세요.", android.widget.Toast.LENGTH_LONG).show();
			stopService();
			return;
		}

		Preferences prefs = new Preferences(this);

		/* VPN 인터페이스 구성 */
		VpnService.Builder builder = new VpnService.Builder();
		builder.setBlocking(false);
		builder.setMtu(prefs.getMtu());
		builder.addAddress(prefs.getTunIpv4(), prefs.getTunIpv4Prefix());
		builder.addRoute("0.0.0.0", 0);
		builder.addAddress(prefs.getTunIpv6(), prefs.getTunIpv6Prefix());
		builder.addRoute("::", 0);
		// 원격 DNS: 매핑 DNS로 보내 터널을 타게 함 (DNS 누수 방지)
		builder.addDnsServer(prefs.getMappedDns());
		builder.setSession("숨결");

		// 앱 자신은 터널에서 제외 (루프 방지)
		try {
			builder.addDisallowedApplication(getPackageName());
		} catch (Exception e) {
		}

		tunFd = builder.establish();
		if (tunFd == null) {
			stopSelf();
			return;
		}

		/* 엔진 설정 파일 생성 */
		File confFile = new File(getCacheDir(), "tproxy.conf");
		try {
			confFile.createNewFile();
			FileOutputStream fos = new FileOutputStream(confFile, false);

boolean http = "http".equalsIgnoreCase(prefs.getType());
			// HTTP 프록시는 UDP 미지원 → UDP를 TCP로 감쌈
			String udpMode = (http || prefs.getUdpInTcp()) ? "tcp" : "udp";

			// 엔진 로그를 파일로 남김 (문제 생기면 원인 확인용)
			String logPath = new File(getCacheDir(), "tunnel.log").getAbsolutePath();

			String conf = "misc:\n" +
				"  task-stack-size: " + prefs.getTaskStackSize() + "\n" +
				"  log-file: '" + logPath + "'\n" +
				"  log-level: info\n" +
				"tunnel:\n" +
				"  mtu: " + prefs.getMtu() + "\n" +
				"  icmp: 'reply'\n" +
				"socks5:\n" +
				"  port: " + prefs.getPort() + "\n" +
				"  address: '" + prefs.getServer() + "'\n" +
				"  udp: '" + udpMode + "'\n";

			if (!prefs.getUser().isEmpty()) {
				conf += "  username: '" + prefs.getUser().replace("'", "''") + "'\n";
				conf += "  password: '" + prefs.getPass().replace("'", "''") + "'\n";
			}

			// 매핑 DNS: 앱이 보낸 DNS 질의를 가짜 IP로 응답하고 터널 안에서 처리 (DNS 누수 방지)
			conf += "mapdns:\n" +
				"  address: " + prefs.getMappedDns() + "\n" +
				"  port: 53\n" +
				"  network: 240.0.0.0\n" +
				"  netmask: 240.0.0.0\n" +
				"  cache-size: 10000\n";

			fos.write(conf.getBytes());
			fos.close();
		} catch (IOException e) {
			stopService();
			return;
		}

		try {
			TProxyStartService(confFile.getAbsolutePath(), tunFd.getFd());
		} catch (Throwable t) {
			android.widget.Toast.makeText(this, "연결 시작 실패: " + t.getMessage(), android.widget.Toast.LENGTH_LONG).show();
			stopService();
			return;
		}
		prefs.setEnable(true);
		QSTileService.requestUpdate(this);
	}

	public void stopService() {
		if (tunFd == null)
			return;

		new Preferences(this).setEnable(false);
		QSTileService.requestUpdate(this);
		stopForeground(true);

		TProxyStopService();

		try {
			tunFd.close();
		} catch (IOException e) {
		}
		tunFd = null;

		System.exit(0);
	}

	private void showNotification(String ch) {
		Intent i = new Intent(this, MainActivity.class);
		i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE);
		NotificationCompat.Builder nb = new NotificationCompat.Builder(this, ch);
		Notification n = nb
			.setContentTitle(getString(R.string.app_name))
			.setContentText("프록시 연결됨")
			.setSmallIcon(R.drawable.ic_notification)
			.setContentIntent(pi)
			.setOngoing(true)
			.build();
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
			startForeground(1, n);
		} else {
			startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
		}
	}

	private void initChannel(String ch) {
		NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel channel = new NotificationChannel(ch, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW);
			nm.createNotificationChannel(channel);
		}
	}
}