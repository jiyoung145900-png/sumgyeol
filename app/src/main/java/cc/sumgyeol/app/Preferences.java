package cc.sumgyeol.app;

import android.content.Context;
import android.content.SharedPreferences;

public class Preferences {
	public static final String PREFS = "SumgyeolPrefs";
	private SharedPreferences p;

	public Preferences(Context c) {
		p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
	}

	// 프록시 종류: "socks5" 또는 "http"
	public String getType()        { return p.getString("type", "socks5"); }
	public void   setType(String v){ p.edit().putString("type", v).apply(); }

	public String getServer()        { return p.getString("server", ""); }
	public void   setServer(String v){ p.edit().putString("server", v).apply(); }

	public int    getPort()        { return p.getInt("port", 1080); }
	public void   setPort(int v)   { p.edit().putInt("port", v).apply(); }

	public String getUser()        { return p.getString("user", ""); }
	public void   setUser(String v){ p.edit().putString("user", v).apply(); }

	public String getPass()        { return p.getString("pass", ""); }
	public void   setPass(String v){ p.edit().putString("pass", v).apply(); }

	public String getName()        { return p.getString("name", "내 프록시"); }
	public void   setName(String v){ p.edit().putString("name", v).apply(); }

	// UDP를 TCP로 감싸기 (HTTP 프록시일 때 자동 true)
	public boolean getUdpInTcp()        { return p.getBoolean("udpInTcp", false); }
	public void    setUdpInTcp(boolean v){ p.edit().putBoolean("udpInTcp", v).apply(); }

	// 연결 상태 (부팅 자동연결/타일용)
	public boolean getEnable()        { return p.getBoolean("enable", false); }
	public void    setEnable(boolean v){ p.edit().putBoolean("enable", v).apply(); }

	// 부팅 시 자동 연결
	public boolean getAutoStart()        { return p.getBoolean("autoStart", false); }
	public void    setAutoStart(boolean v){ p.edit().putBoolean("autoStart", v).apply(); }

	// 고정 터널 설정값
	public int    getMtu()              { return 8500; }
	public String getTunIpv4()          { return "198.18.0.1"; }
	public int    getTunIpv4Prefix()    { return 32; }
	public String getTunIpv6()          { return "fc00::1"; }
	public int    getTunIpv6Prefix()    { return 128; }
	public int    getTaskStackSize()    { return 81920; }
	public String getMappedDns()        { return "198.18.0.2"; }
}
