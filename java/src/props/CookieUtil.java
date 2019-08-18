package props;

import java.applet.Applet;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import netscape.javascript.JSObject;

/**
 * Class <code>CookieUtil</code> is a class that can be used to create, access,
 * and delete cookies via an applet on a web page.
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 */
public class CookieUtil {
	private static final boolean DEBUG = false;
	private static final String CHARSET = "ISO-8859-1";
	
	private boolean bExceptionReported = false; // mdb added 12/11/08

	private JSObject host;

	/**
	 * Creates a new <code>CookieUtil</code> instance.
	 *
	 * @param applet an <code>Applet</code> value must be an applet currently being displayed
	 */
	public CookieUtil(Applet applet) {
		try {
			host = JSObject.getWindow(applet);
		} catch (Exception e) {
			System.err.println("Exception getting window object!");
			//e.printStackTrace(); // mdb removed 3/1/07
		}
	}

	/**
	 * Method <code>getCookie</code> returns the value of the cookie
	 * with name <code>name</code>
	 *
	 * @param name a <code>String</code> value the cookie to find
	 * @return a <code>String</code> decoded value of the cookie.
	 */
	public String getCookie(String name) {
		String cookie = getCookie();
		if (cookie == null || cookie.length() == 0) return null;
		String arg = name + "=";
		int alen = arg.length();
		int clen = cookie.length();
		int j, i;
		for (i = 0; i < clen;) {
			j = i + alen;
			if (j > clen) break;
			if (cookie.substring(i,j).equals(arg)) {
				return getCookieVal(j,cookie);
			}
			i = cookie.indexOf(" ",i)+1;
			if (i == 0) break;
		}
		return null;
	}

	/**
	 * Method <code>setCookie</code> sets the cookie with the specified values.  All object values
	 * except for name can be null.
	 *
	 * @param name a <code>String</code> value of the name for the cookie
	 * @param value a <code>String</code> value the value of the cookie (will be encoded with ISO-8859-1 for you)
	 * @param expires a <code>Date</code> value the expiration date, expires at the end of the session if null
	 * @param path a <code>String</code> value of the path for the cookie
	 * @param domain a <code>String</code> value of the domain for the cookie
	 * @param secure a <code>boolean</code> value true if the cookie requires a secure connection
	 */
	public void setCookie(String name, String value, Date expires, String path, String domain, boolean secure) {
		String cookie = name + "=" + escape(value);
		if (expires != null) {
			SimpleDateFormat simple = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z");
			simple.setTimeZone(TimeZone.getTimeZone("GMT"));
			cookie += "; expires="+simple.format(expires);
		}
		if (path != null)    cookie += "; path="+path;
		if (domain != null)  cookie += "; domain="+domain;
		if (secure)          cookie += "; secure";
		setCookie(cookie);
	}

	/**
	 * Method <code>deleteCookie</code> deletes the cookie with the specified values.
	 * A cookie is effectively deleted by changing the expiration date to an earlier date.
	 *
	 * @param name a <code>String</code> value
	 * @param path a <code>String</code> value of the path for the cookie, may be null
	 * @param domain a <code>String</code> value of the domain for the cookie, may be null
	 */
	public void deleteCookie(String name, String path, String domain) {
		if (getCookie(name) != null) {
			setCookie(name,null,new Date(0),path,domain,false);
		}
	}

	private void setCookie(String val) {
		if (DEBUG) System.out.println("Setting Cookie ["+val+"]");
		try {
			((JSObject)host.getMember("document")).setMember("cookie",val);	
		} catch (Exception e) {
			if (!bExceptionReported)
				System.err.println("Exception trying to set cookie value! (suppressing further notifications)");
			bExceptionReported = true; // mdb added 12/11/08
			//e.printStackTrace(); // mdb removed 3/1/07
		}
	}

	private String getCookie() {
		String cookie = null;
		try {
			cookie = (String)((JSObject)host.getMember("document")).getMember("cookie");
			if (cookie == null)
				cookie = (String)host.eval("document.cookie");
			if (DEBUG) System.out.println("Cookie Acquired ["+cookie+"]");
		}
		catch (Exception e) {
			if (!bExceptionReported)
				System.err.println("Exception trying to get cookie value!  (suppressing further notifications)");
			bExceptionReported = true; // mdb added 12/11/08
			//e.printStackTrace(); // mdb removed 3/1/07
		}
		if (DEBUG) System.out.println("CookieUtil::getCookie() => ["+cookie+"]");
		return cookie;
	}

	private String getCookieVal(int offset, String cookie) {
		int endstr = cookie.indexOf(";",offset);
		if (endstr == -1) endstr = cookie.length();
		return unescape(cookie.substring(offset,endstr));
	}

	private static String escape(String str) {
		if (str == null) return "";
		try {
			str = java.net.URLEncoder.encode(str,CHARSET);
		}
		catch (java.io.UnsupportedEncodingException e) {
			e.printStackTrace();
			str = null;
		}
		return str;
	}

	private static String unescape(String str) {
		if (str == null) return "";
		try {
			str = java.net.URLDecoder.decode(str,CHARSET);
		}
		catch (java.io.UnsupportedEncodingException e) {
			e.printStackTrace();
			str = null;
		}
		return str;
	}
}
