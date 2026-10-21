package cc.sumgyeol.app;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

public class TProxyService extends VpnService {
	private static native boolean TProxyStartService(String config_path, int fd);
	private static native boolean TProxyStopService();
	private static native boolean TProxyIsRunning();
	private static native long[] TProxyGetStats();

	public static final String ACTION_CONNECT = "cc.sumgyeol.app.CONNECT";
	public static final String ACTION_DISCONNECT = "cc.sumgyeol.app.DISCONNECT";

	private static boolean libLoaded = false;
	private static String libError = "";
	static {
		try {
			System.loadLibrary("hev-socks5-tunnel");
			libLoaded = true;
		} catch (Throwable t) {
			libLoaded = false;
			libError = String.valueOf(t.getMessage());
		}
	}

	private ParcelFileDescriptor tunFd = null;

	// ─────────── 앱 로그 (연결 단계 기록 → 화면에서 '로그 보기'로 확인) ───────────

	private void log(String msg) {
		try {
			String t = new SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(new Date());
			FileWriter w = new FileWriter(VpnState.appLogFile(this), true);
			w.write("[" + t + "] " + msg + "\n");
			w.close();
		} catch (Exception e) {
		}
	}

	private void toast(final String msg) {
		new Handler(Looper.getMainLooper()).post(() -> {
			try { Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show(); } catch (Throwable ignore) {}
		});
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		boolean disconnect = intent != null && ACTION_DISCONNECT.equals(intent.getAction());
		// 포그라운드 알림을 "가장 먼저" 띄운다 (최신 안드로이드는 늦으면 강제종료)
		if (!disconnect) try {
			initChannel("sumgyeol");
			showNotification("sumgyeol", "연결하는 중...");
		} catch (Throwable t) {
			log("알림 표시 실패: " + t.getMessage());
		}

		try {
			if (disconnect) {
				log("사용자가 연결 끊기를 누름");
				stopTunnel();
				return START_NOT_STICKY;
			}
			startTunnel();
		} catch (Throwable t) {
			log("오류: " + t);
			toast("오류: " + t.getMessage());
			stopTunnel();
			return START_NOT_STICKY;
		}
		return START_STICKY;
	}

	@Override
	public void onRevoke() {
		log("다른 VPN 앱이 연결을 가져감 (또는 VPN 권한 해제)");
		toast("다른 VPN 앱이 켜져서 숨결 연결이 끊겼어요. 다른 VPN을 끄고 다시 연결하세요.");
		stopTunnel();
		super.onRevoke();
	}

	private void startTunnel() {
		if (tunFd != null) {
			log("이미 연결되어 있음");
			return;
		}
		// 새 시도마다 로그 새로 시작
		VpnState.appLogFile(this).delete();
		VpnState.engineLogFile(this).delete();
		log("연결 시작");

		if (!libLoaded) {
			log("엔진 로드 실패: " + libError);
			toast("엔진 로드 실패 - 앱을 다시 설치해 주세요.");
			stopTunnel();
			return;
		}

		Preferences prefs = new Preferences(this);
		String server = prefs.getServer().trim();
		log("프록시: " + prefs.getType() + " " + server + ":" + prefs.getPort()
			+ (prefs.getUser().isEmpty() ? " (아이디 없음)" : " (아이디 있음)"));

		if (server.isEmpty()) {
			log("서버 주소가 비어 있음");
			toast("프록시 정보를 먼저 입력하세요.");
			stopTunnel();
			return;
		}
		// 엔진은 IP 주소만 받으므로, 도메인이면 미리 IP로 변환 (VPN 켜기 전에)
		if (!server.matches("^[0-9.]+$") && !server.contains(":")) {
			final String[] resolved = { null };
			final String host = server;
			Thread th = new Thread(() -> {
				try { resolved[0] = java.net.InetAddress.getByName(host).getHostAddress(); } catch (Exception e) { }
			});
			th.start();
			try { th.join(8000); } catch (InterruptedException e) { }
			if (resolved[0] == null) {
				log("도메인 주소를 IP로 바꾸지 못함: " + host);
				toast("서버 주소를 찾을 수 없어요: " + host);
				stopTunnel();
				return;
			}
			log("도메인 → IP: " + host + " → " + resolved[0]);
			server = resolved[0];
		}

		/* VPN 인터페이스 구성 */
		VpnService.Builder builder = new VpnService.Builder();
		builder.setBlocking(false);
		builder.setMtu(prefs.getMtu());
		builder.addAddress(prefs.getTunIpv4(), prefs.getTunIpv4Prefix());
		builder.addRoute("0.0.0.0", 0);
		// IPv6는 터널에 넣지 않음: 대부분의 프록시가 IPv6를 못 다뤄서 텔레그램 등이 IPv6로 접속하다 막힘.
		// VPN에 IPv6를 설정하지 않으면 안드로이드가 IPv6를 자동 차단 → 진짜 IP 누수 없이
		// 앱들이 즉시 IPv4로 넘어가 프록시를 정상적으로 탄다.
		builder.addDnsServer(prefs.getMappedDns());
		builder.setSession("숨결");
		try {
			// 앱 자신은 터널에서 제외 (엔진이 프록시 서버로 직접 나가야 하므로)
			builder.addDisallowedApplication(getPackageName());
		} catch (Exception e) {
			log("자기 앱 제외 실패: " + e.getMessage());
		}

		tunFd = builder.establish();
		if (tunFd == null) {
			log("VPN 생성 실패 (권한이 없거나 다른 VPN이 '항상 켜기'로 설정됨)");
			toast("VPN을 만들 수 없어요. 다른 VPN 앱의 '항상 켜기'를 꺼 주세요.");
			stopTunnel();
			return;
		}
		log("VPN 인터페이스 생성 완료");

		/* 엔진 설정 파일 */
		File confFile = new File(getCacheDir(), "tproxy.conf");
		try {
			boolean http = "http".equalsIgnoreCase(prefs.getType());
			String udpMode = (http || prefs.getUdpInTcp()) ? "tcp" : "udp";
			String conf = "misc:\n" +
				"  task-stack-size: " + prefs.getTaskStackSize() + "\n" +
				"  log-file: '" + VpnState.engineLogFile(this).getAbsolutePath() + "'\n" +
				"  log-level: info\n" +
				"tunnel:\n" +
				"  mtu: " + prefs.getMtu() + "\n" +
				"  icmp: 'reply'\n" +
				"socks5:\n" +
				"  port: " + prefs.getPort() + "\n" +
				"  address: '" + server + "'\n" +
				"  udp: '" + udpMode + "'\n";
			if (!prefs.getUser().isEmpty()) {
				conf += "  username: '" + prefs.getUser().replace("'", "''") + "'\n";
				conf += "  password: '" + prefs.getPass().replace("'", "''") + "'\n";
			}
			conf += "mapdns:\n" +
				"  address: " + prefs.getMappedDns() + "\n" +
				"  port: 53\n" +
				"  network: 240.0.0.0\n" +
				"  netmask: 240.0.0.0\n" +
				"  cache-size: 10000\n";
			FileOutputStream fos = new FileOutputStream(confFile, false);
			fos.write(conf.getBytes("UTF-8"));
			fos.close();
		} catch (IOException e) {
			log("설정 파일 쓰기 실패: " + e.getMessage());
			stopTunnel();
			return;
		}

		boolean ok = TProxyStartService(confFile.getAbsolutePath(), tunFd.getFd());
		log("엔진 시작 결과: " + (ok ? "성공" : "실패"));
		if (!ok) {
			toast("터널 엔진을 시작하지 못했어요.");
			stopTunnel();
			return;
		}

		prefs.setEnable(true);
		showNotification("sumgyeol", "연결됨 · " + prefs.getServer() + ":" + prefs.getPort());
		QSTileService.requestUpdate(this);
		log("연결 완료 - 이제 휴대폰 전체가 프록시를 탑니다");
	}

	// 어떤 상태에서 불려도 항상 '끊김'으로 정리 → 가짜 '연결됨'이 남지 않음
	private void stopTunnel() {
		try { new Preferences(this).setEnableNow(false); } catch (Throwable t) { }
		try { QSTileService.requestUpdate(this); } catch (Throwable t) { }
		boolean wasRunning = (tunFd != null);
		if (wasRunning) {
			try { TProxyStopService(); } catch (Throwable t) { }
			try { tunFd.close(); } catch (IOException e) { }
			tunFd = null;
			log("연결 끊김");
		}
		try { stopForeground(true); } catch (Throwable t) { }
		stopSelf();
		// 엔진은 한 프로세스에서 재시작이 안 되므로 VPN 프로세스를 완전히 종료
		new Handler(Looper.getMainLooper()).postDelayed(() -> System.exit(0), 300);
	}

	private void showNotification(String ch, String text) {
		Intent i = new Intent(this, MainActivity.class);
		i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE);
		Notification n = new NotificationCompat.Builder(this, ch)
			.setContentTitle(getString(R.string.app_name))
			.setContentText(text)
			.setSmallIcon(R.drawable.ic_notification)
			.setContentIntent(pi)
			.setOngoing(true)
			.build();
		if (Build.VERSION.SDK_INT >= 34) {
			startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
		} else {
			startForeground(1, n);
		}
	}

	private void initChannel(String ch) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			nm.createNotificationChannel(new NotificationChannel(ch, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW));
		}
	}
}