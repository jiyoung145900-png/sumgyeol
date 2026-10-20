package cc.sumgyeol.app;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
	private Preferences prefs;
	private Spinner typeSpinner;
	private EditText pasteInput, serverInput, portInput, userInput, passInput;
	private Button connectBtn;
	private TextView statusText;

	private static final int VPN_REQUEST = 1;

	@Override
	protected void onCreate(Bundle b) {
		super.onCreate(b);
		prefs = new Preferences(this);
		setContentView(R.layout.main);

		typeSpinner = findViewById(R.id.type);
		pasteInput  = findViewById(R.id.paste);
		serverInput = findViewById(R.id.server);
		portInput   = findViewById(R.id.port);
		userInput   = findViewById(R.id.user);
		passInput   = findViewById(R.id.pass);
		connectBtn  = findViewById(R.id.connect);
		statusText  = findViewById(R.id.status);

		ArrayAdapter<String> ad = new ArrayAdapter<>(this,
			android.R.layout.simple_spinner_dropdown_item,
			new String[]{"SOCKS5", "HTTP"});
		typeSpinner.setAdapter(ad);
		typeSpinner.setSelection("http".equals(prefs.getType()) ? 1 : 0);

		// 저장값 로드
		serverInput.setText(prefs.getServer());
		if (prefs.getPort() > 0) portInput.setText(String.valueOf(prefs.getPort()));
		userInput.setText(prefs.getUser());
		passInput.setText(prefs.getPass());

		// 한 줄 붙여넣기 자동 파싱
		((Button) findViewById(R.id.applyPaste)).setOnClickListener(v -> applyPaste());

		connectBtn.setOnClickListener(v -> onConnectClicked());

		updateStatus();
	}

	@Override
	protected void onResume() {
		super.onResume();
		updateStatus();
	}

	private void applyPaste() {
		String raw = pasteInput.getText().toString().trim();
		ProxyInfo pi = ProxyInfo.parse(raw);
		if (pi == null) {
			Toast.makeText(this, "형식을 알 수 없어요. 예) 1.2.3.4:8080:아이디:비번", Toast.LENGTH_LONG).show();
			return;
		}
		if (pi.type != null)
			typeSpinner.setSelection("http".equals(pi.type) ? 1 : 0);
		serverInput.setText(pi.server);
		portInput.setText(String.valueOf(pi.port));
		userInput.setText(pi.user);
		passInput.setText(pi.pass);
		pasteInput.setText("");
		Toast.makeText(this, "입력됐어요. [연결]을 누르세요.", Toast.LENGTH_SHORT).show();
	}

	private boolean saveInputs() {
		String server = serverInput.getText().toString().trim();
		String portStr = portInput.getText().toString().trim();
		if (server.isEmpty()) {
			Toast.makeText(this, "서버 주소를 입력하세요.", Toast.LENGTH_SHORT).show();
			return false;
		}
		int port;
		try {
			port = Integer.parseInt(portStr);
		} catch (Exception e) {
			Toast.makeText(this, "포트 번호를 확인하세요.", Toast.LENGTH_SHORT).show();
			return false;
		}
		if (port < 1 || port > 65535) {
			Toast.makeText(this, "포트는 1~65535 사이여야 해요.", Toast.LENGTH_SHORT).show();
			return false;
		}
		prefs.setType(typeSpinner.getSelectedItemPosition() == 1 ? "http" : "socks5");
		prefs.setServer(server);
		prefs.setPort(port);
		prefs.setUser(userInput.getText().toString());
		prefs.setPass(passInput.getText().toString());
		return true;
	}

	private void onConnectClicked() {
		if (isRunning()) {
			// 연결 끊기
			Intent i = new Intent(this, TProxyService.class);
			i.setAction(TProxyService.ACTION_DISCONNECT);
			startService(i);
			statusText.setText("연결을 끊는 중...");
			connectBtn.postDelayed(this::updateStatus, 1200);
			return;
		}
		if (!saveInputs()) return;

		if ("http".equals(prefs.getType())) {
			Toast.makeText(this,
				"참고: 모바일 엔진은 SOCKS5를 권장합니다. HTTP는 일부 기능이 제한될 수 있어요.",
				Toast.LENGTH_LONG).show();
		}

		Intent prepare = VpnService.prepare(this);
		if (prepare != null) {
			startActivityForResult(prepare, VPN_REQUEST);
		} else {
			onActivityResult(VPN_REQUEST, RESULT_OK, null);
		}
	}

	@Override
	protected void onActivityResult(int req, int res, Intent data) {
		super.onActivityResult(req, res, data);
		if (req == VPN_REQUEST && res == RESULT_OK) {
			Intent i = new Intent(this, TProxyService.class);
			i.setAction(TProxyService.ACTION_CONNECT);
			startForegroundService(i);
			statusText.setText("연결하는 중...");
			connectBtn.postDelayed(this::updateStatus, 1500);
		} else if (req == VPN_REQUEST) {
			Toast.makeText(this, "VPN 권한이 필요해요.", Toast.LENGTH_SHORT).show();
		}
	}

	private boolean isRunning() {
		return prefs.getEnable();
	}

	private void updateStatus() {
		if (isRunning()) {
			statusText.setText("● 연결됨");
			statusText.setTextColor(0xFF4ED17A);
			connectBtn.setText("연결 끊기");
		} else {
			statusText.setText("○ 연결 안 됨");
			statusText.setTextColor(0xFF9A8E92);
			connectBtn.setText("연결하기");
		}
	}
}
