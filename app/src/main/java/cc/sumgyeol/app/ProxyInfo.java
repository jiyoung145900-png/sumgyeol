package cc.sumgyeol.app;

import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProxyInfo {
	public String type;   // "socks5" | "http" | null
	public String server = "";
	public int port = 0;
	public String user = "";
	public String pass = "";

	private static String dec(String s) {
		try { return URLDecoder.decode(s, "UTF-8"); } catch (Exception e) { return s; }
	}

	// 지원형식:
	//  scheme://user:pass@host:port   (scheme=socks5/socks/http/https)
	//  user:pass@host:port
	//  host:port:user:pass
	//  host:port
	public static ProxyInfo parse(String text) {
		if (text == null) return null;
		String t = text.replaceAll("\\s", "");
		if (t.isEmpty()) return null;

		ProxyInfo r = new ProxyInfo();

		Matcher sm = Pattern.compile("^([a-zA-Z0-9]+)://(.+)$").matcher(t);
		if (sm.matches()) {
			String sc = sm.group(1).toLowerCase();
			if (sc.matches("socks5?h?")) r.type = "socks5";
			else if (sc.equals("http")) r.type = "http";
			else if (sc.equals("https")) r.type = "http";
			else return null;
			t = sm.group(2).replaceAll("/+$", "");
		}

		Matcher m;
		if ((m = Pattern.compile("^(.+)@([^@]+)$").matcher(t)).matches()) {
			String cred = m.group(1), hp = m.group(2);
			int i = cred.indexOf(':');
			if (i >= 0) { r.user = dec(cred.substring(0, i)); r.pass = dec(cred.substring(i + 1)); }
			else r.user = dec(cred);
			Matcher hm = Pattern.compile("^([^:]+):(\\d{1,5})$").matcher(hp);
			if (!hm.matches()) return null;
			r.server = hm.group(1); r.port = Integer.parseInt(hm.group(2));
		} else if ((m = Pattern.compile("^([^:]+):(\\d{1,5}):([^:]*):(.*)$").matcher(t)).matches()) {
			r.server = m.group(1); r.port = Integer.parseInt(m.group(2));
			r.user = dec(m.group(3)); r.pass = dec(m.group(4));
		} else if ((m = Pattern.compile("^([^:]+):(\\d{1,5})$").matcher(t)).matches()) {
			r.server = m.group(1); r.port = Integer.parseInt(m.group(2));
		} else {
			return null;
		}

		if (r.port < 1 || r.port > 65535) return null;
		return r;
	}
}
