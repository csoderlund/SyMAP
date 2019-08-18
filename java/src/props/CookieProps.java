package props;

import java.applet.Applet;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * Class <code>CookieProps</code> is a wrapper for a CookieUtil the stores all the
 * needed information for setting and deleting a specific cookie.
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 * @see CookieUtil
 * @see PersistentProps
 */
public class CookieProps implements PersistentProps {
    private CookieUtil util;
    private String path, domain, name;
    private boolean secure = false;
    private Date expires;
    
    /**
     * Creates a new <code>CookieProps</code> instance.
     *
     * @param util a <code>CookieUtil</code> value
     * @param path a <code>String</code> value
     * @param domain a <code>String</code> value
     * @param expires a <code>Date</code> value
     * @param secure a <code>boolean</code> value
     * @param name a <code>String</code> value
     */
    public CookieProps(CookieUtil util, String path, String domain, Date expires, boolean secure, String name) {
	this.util = util;
	this.path = path;
	this.domain = domain;
	this.expires = expires;
	this.secure = secure;
	this.name = name;
    }

    /**
     * Creates a new <code>CookieProps</code> instance using <code>applet</code> to create a new CookieUtil.
     *
     * @param applet an <code>Applet</code> value
     * @param path a <code>String</code> value
     * @param domain a <code>String</code> value
     * @param expires a <code>Date</code> value
     * @param secure a <code>boolean</code> value
     * @param name a <code>String</code> value
     */
    public CookieProps(Applet applet, String path, String domain, Date expires, boolean secure, String name) {
	this(new CookieUtil(applet),path,domain,expires,secure,name);
    }

    /**
     * Method <code>copy</code> returns a new CookieProps identical to this one
     * only with a different cookie name.
     *
     * @param name a <code>String</code> value of the name of the cookie for the returned CookieProps
     * @return a <code>CookieProps</code> value
     */
    public PersistentProps copy(String name) {
	return new CookieProps(util,path,domain,expires,secure,name);
    }

    /**
     * Method <code>equals</code> returns true if obj is a CookieProps with the same settings for path, domain,
     * and name.
     *
     * @param obj an <code>Object</code> value
     * @return a <code>boolean</code> value
     */
    public boolean equals(Object obj) {
	if (obj instanceof CookieProps) {
	    CookieProps ch = (CookieProps)obj;
	    return ((path == null && ch.path == null) || (path != null && path.equals(ch.path))) &&
		((domain == null && ch.domain == null) || (domain != null && domain.equals(ch.domain))) &&
		((name == null && ch.name == null) || (name != null && name.equals(ch.name)));
	}
	return false;
    }

    /**
     * Method <code>getPath</code> returns the path previously specified or null
     *
     * @return a <code>String</code> value
     */
    public String getPath() {
	return path;
    }

    /**
     * Method <code>setPath</code> sets the path
     *
     * @param path a <code>String</code> value of path or null
     */
    public void setPath(String path) {
	this.path = path;
    }

    /**
     * Method <code>getDomain</code> returns the domain previously specified or null
     *
     * @return a <code>String</code> value
     */
    public String getDomain() {
	return domain;
    }

    /**
     * Method <code>setDomain</code> sets the domain
     *
     * @param domain a <code>String</code> value of domain or null
     */
    public void setDomain(String domain) {
	this.domain = domain;
    }

    /**
     * Method <code>getSecure</code> returns the current default value of the secure property for cookies set.
     *
     * @return a <code>boolean</code> value
     */
    public boolean getSecure() {
	return secure;
    }

    /**
     * Method <code>setSecure</code> sets the default value for secure when a cookie is set.
     *
     * @param secure a <code>boolean</code> value
     */
    public void setSecure(boolean secure) {
	this.secure = secure;
    }

    /**
     * Method <code>getExpires</code> returns the default value for when a cookie will be set to expire.
     *
     * @return a <code>Date</code> value
     */
    public Date getExpires() {
	return expires;
    }

    /**
     * Method <code>setExpires</code> sets the default value for when a cookie will be set to expire.
     *
     * @param expires a <code>Date</code> value
     */
    public void setExpires(Date expires) {
	this.expires = expires;
    }

    /**
     * Method <code>getName</code> returns the name of the cookie being used.
     *
     * @return a <code>String</code> value
     */
    public String getName() {
	return name;
    }

    /**
     * Method <code>setName</code> sets the name of the cookie to be used.
     *
     * @param name a <code>String</code> value
     */
    public void setName(String name) {
	this.name = name;
    }

    /**
     * Method <code>getProp</code> returns the value of the cookie
     *
     * @return a <code>String</code> decoded value of the cookie.
     */
    public String getProp() {
	return util.getCookie(name);
    }

    /**
     * Method <code>setProp</code> sets the value of the cookie.
     *
     * @param value a <code>String</code> value
     */
    public void setProp(String value) {
	util.setCookie(name,value,expires,path,domain,secure);
    }

    /**
     * Method <code>deleteProp</code> deletes the cookie.
     *
     */
    public void deleteProp() {
	util.deleteCookie(name,path,domain);
    }

    /**
     * Method <code>getDaysFromNow</code> returns the date that is <code>days</code> in the future from 
     * this moment.
     *
     * @param days an <code>int</code> value
     * @return a <code>Date</code> value
     */
    public static Date getDaysFromNow(int days) {
	GregorianCalendar gc = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
	gc.add(Calendar.DAY_OF_YEAR,days);
	return gc.getTime();
    }
}
