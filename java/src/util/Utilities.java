package util;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Window;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Container;
import java.awt.Shape;
import java.awt.event.ActionListener;
import java.awt.geom.Rectangle2D;

import javax.swing.AbstractButton;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import java.awt.Frame;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.Map;
import java.util.StringTokenizer;

import java.applet.Applet;
import java.net.URL;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

import java.io.File;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.TreeMap;
import java.util.Map;
import java.util.HashMap;

import symap.SyMAP;
import symap.frame.HelpBar;
import symap.frame.HelpListener;


/**
 * Class <code>Utilities</code> class for doing some miscelaneous things that 
 * may be useful to others. Requires plugin.jar
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 */
public class Utilities {

	private static Map<String, Integer> rs2col = null;
	private static Date mTimeStart = null;
	private static Class resClass = null; // store this so help pages can be loaded from anywhere
	private static Frame helpParentFrame = null; 
	
	private Utilities() { }

	/**
	 * Method <code>getScreenBounds</code> attempts to find an applet holder in 
	 * the chain of ownership and uses the applet to call 
	 * getScreenBounds(Applet,Window).
	 *
	 * @param window a <code>Window</code> value
	 * @return a <code>Rectangle</code> value
	 * @see AppletHolder
	 * @see #getScreenBounds(Applet,Window)
	 */
	public static Rectangle getScreenBounds(Window window) {
		Window owner = window;
		while (owner != null && !(owner instanceof AppletHolder)) owner = owner.getOwner();
		return getScreenBounds(owner instanceof AppletHolder ? ((AppletHolder)owner).getApplet() : null, window);
	}

	/**
	 * Method <code>getScreenBounds</code> attempts to find the screen bounds 
	 * from the screen insets.  If the insets don't appear to be set and 
	 * applet != null, the applet attempts to access the available height and 
	 * width through javascript (not MAYSCRIPT attribute in applet tag is most 
	 * likely necessary for this to work).
	 *
	 * @param applet an <code>Applet</code> value
	 * @param window a <code>Window</code> value
	 * @return a <code>Rectangle</code> value
	 */
	public static Rectangle getScreenBounds(Applet applet, Window window) {
		GraphicsConfiguration config = window.getGraphicsConfiguration();
		Rectangle b = config.getBounds();
		Insets inset = Toolkit.getDefaultToolkit().getScreenInsets(config);
		if (inset.left != 0 || inset.right != 0 || inset.top != 0 || inset.bottom != 0)
			b.setBounds(b.x+inset.left,b.y+inset.top,b.width-inset.left-inset.right,b.height-inset.top-inset.bottom);
		else if (applet != null &&
				config == GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration()) {
			try {
				netscape.javascript.JSObject host = netscape.javascript.JSObject.getWindow(applet);
				b.height = ((Number)host.eval("screen.availHeight")).intValue();
				b.width  = ((Number)host.eval("screen.availWidth")).intValue();
			} 
			catch (Exception e) {
				//System.err.println("Exception occurred getting screen dimensions from JavaScript!"); // mdb removed 12/29/08
				//e.printStackTrace(); // mdb removed 3/1/07 
			}
		}
		return b;
	}

	public static Dimension stringDimension(String str) {
		Dimension d = new Dimension();
		int cWidth = 0;
		for (int i = 0; i < str.length(); ++i) {
			if (str.charAt(i) == '\n') {
				d.width = Math.max(d.width,cWidth);
				d.height++;
				cWidth = 0;
			}
			else ++cWidth;
		}
		d.width = Math.max(d.width,cWidth);
		return d;
	}

	/**
	 * Method <code>setFullScreenSize</code> sets the window to the full screen size even it the
	 * window's perferred size is smaller.
	 *
	 * @param window a <code>Window</code> value
	 * @param view a <code>Container</code> value
	 * @return a <code>viod</code> value
	 */
	public static void setFullScreenSize(Window window, Container view) {
		Rectangle sb = getScreenBounds(window);
		setWinSize(window,sb,new Dimension(sb.width,sb.height),view);
	}

	/**
	 * Method <code>setFullSize</code> sets the window to the full size of the available screen or it's preferred
	 * size, whichever is smaller, using the other methods
	 * int this class.
	 *
	 * @param window a <code>Window</code> value
	 * @param view a <code>Container</code> value needed to invalidate to insure proper updated (may not work correctly if null)
	 * @see #getScreenBounds(Window)
	 */
	public static void setFullSize(Window window, Container view) {
		window.pack();

		Dimension pref = window.getPreferredSize();
		Rectangle b = getScreenBounds(window);

		pref.width = Math.min(pref.width,b.width);
		pref.height = Math.min(pref.height,b.height);

		setWinSize(window,b,pref,view);
	}

	private static void setWinSize(Window window, Rectangle screenBounds, Dimension pref, Container view) {
		Point loc = window.getLocation();
		if (pref.width+loc.x > screenBounds.width) loc.x = screenBounds.x;
		if (pref.height+loc.y > screenBounds.height) loc.y = screenBounds.y;

		window.setLocation(loc);
		window.setSize(pref);

		if (view != null) view.invalidate();
		window.validate();
	}
	
	// mdb added 8/28/09
	public static void setCursorBusy(Component c, boolean busy) {
		if (busy)
			c.setCursor( Cursor.getPredefinedCursor( Cursor.WAIT_CURSOR ) );
		else
			c.setCursor( Cursor.getDefaultCursor() );
	}

	/**
	 * Method <code>contains</code> returns true if the given array contains the element o.
	 *
	 * @param ar an <code>Object[]</code> value
	 * @param o an <code>Object</code> value
	 * @return a <code>boolean</code> value
	 */
	public static boolean contains(Object[] ar, Object o) {
		if (ar != null)
			for (int i = 0; i < ar.length; ++i)
				if (equals(o,ar[i])) return true;
		return false;
	}
	
	public static boolean contains(int[] ar, int x) {
		if (ar != null)
			for (int i = 0; i < ar.length; ++i)
				if (ar[i] == x) return true;
		return false;
	}

	public static Object get(Object[] ar, Object o) {
		if (ar != null)
			for (int i = 0; i < ar.length; ++i)
				if (equals(o,ar[i])) return ar[i];
		return null;
	}
	
	// mdb added 7/28/09 - adjust rectangle coordinates for negative width or height
	public static void fixRect(Rectangle2D rect) {
		if (rect.getWidth() < 0)
			rect.setRect(
					rect.getX()+rect.getWidth()+1,
					rect.getY(),
					Math.abs( rect.getWidth() ),
					rect.getHeight());
		if (rect.getHeight() < 0)
			rect.setRect(
					rect.getX(),
					rect.getY()+rect.getHeight()+1,
					rect.getWidth(),
					Math.abs( rect.getHeight() ));
	}

	/**
	 * Method <code>concat</code> concatenates the arrays returning a new non null array.
	 *
	 * @param a1 an <code>int[]</code> value
	 * @param a2 an <code>int[]</code> value
	 * @return an <code>int[]</code> value
	 */
	public static int[] concat(int[] a1, int[] a2) {
		int[] ret = new int[(a1 == null ? 0 : a1.length)+(a2 == null ? 0 : a2.length)];
		if (ret.length != 0) {
			if (a1 == null)
				System.arraycopy(a2,0,ret,0,a2.length);
			else if (a2 == null)
				System.arraycopy(a1,0,ret,0,a1.length);
			else {
				System.arraycopy(a1,0,ret,0,a1.length);
				System.arraycopy(a2,0,ret,a1.length,a2.length);
			}
		}
		return ret;
	}

	public static int[] copy(int a[]) {
		if (a == null) return new int[0];
		return copy(a,a.length);
	}

	public static int[] copy(int[] a, int len) {
		if (a == null || a.length == 0 || len <= 0) return new int[0];
		int[] ret = new int[len];
		System.arraycopy(a,0,ret,0,Math.min(len,a.length));
		return ret;
	}

	/**
	 * Method <code>sortCopy</code> copys the array and sorts the copy, returning the copied array.
	 *
	 * @param a an <code>int[]</code> value
	 * @return an <code>int[]</code> value
	 */
	public static int[] sortCopy(int[] a) {
		if (a == null || a.length == 0) return new int[0];
		int[] ret = new int[a.length];
		System.arraycopy(a,0,ret,0,ret.length);
		Arrays.sort(ret);
		return ret;
	}

	public static String getCommaSepString(int[] ints) {
		if (ints == null || ints.length == 0) return "";
		StringBuffer ret = new StringBuffer().append(ints[0]);
		for (int i = 1; i < ints.length; i++) ret.append(",").append(ints[i]);
		return ret.toString();
	}

	public static String getPrettyCommaSepString(int[] ints) {
		if (ints == null || ints.length == 0) return "";
		StringBuffer ret = new StringBuffer().append(ints[0]);
		for (int i = 1; i < ints.length; i++) {
			if (ints.length == 2)          ret.append(" and ");
			else if (i + 1 == ints.length) ret.append(", and ");
			else                           ret.append(", ");
			ret.append(ints[i]);
		}
		return ret.toString();
	}

	public static String getPrettyCommaSepString(char[] chars) {
		if (chars == null || chars.length == 0) return "";
		StringBuffer ret = new StringBuffer().append(chars[0]);
		for (int i = 1; i < chars.length; i++) {
			if (chars.length == 2)          ret.append(" and ");
			else if (i + 1 == chars.length) ret.append(", and ");
			else                            ret.append(", ");
			ret.append(chars[i]);
		}
		return ret.toString();
	}

	public static String getIntsString(int[] ints) {
		final StringBuffer DASH = new StringBuffer("-");
		final StringBuffer COMMA = new StringBuffer(",");
		StringBuffer ret = new StringBuffer();
		int i,k;
		for (i = 0; i < ints.length; i++) {
			ret.append(ints[i]);
			for (; i + 1 < ints.length; i++)
				if (ints[i+1] != ints[i]) break;
			if (i + 1 < ints.length) {
				for (k = i; i + 1 < ints.length; i++)
					if (ints[i] + 1 != ints[i+1]) break;

				if (k != i) {
					--i;
					ret.append(DASH);
				}
				else ret.append(COMMA);
			}
		}
		return ret.toString();	
	}

	public static String getIntsString(Collection<Integer> integerList) throws IllegalArgumentException {
		if (integerList.size() != new HashSet<Integer>(integerList).size())
			throw new IllegalArgumentException("Dublicate integers not allowed. "+integerList);
		int ints[] = new int[integerList.size()];
		Iterator<Integer> iter = integerList.iterator();
		for (int i = 0; iter.hasNext(); i++) 
			ints[i] = iter.next().intValue();
		return getIntsString(ints);
	}

	public static int[] getIntArray(String input) throws IllegalArgumentException {
		Collection<Integer> ints = getInts(input);
		int[] ret = new int[ints.size()];
		Iterator<Integer> iter = ints.iterator();
		for (int i = 0; i < ret.length; ++i)
			ret[i] = iter.next().intValue();
		return ret;
	}

	public static int[] getIntArray(Collection<Integer> ints) throws ClassCastException {
		int[] ret = new int[ints.size()];
		Iterator<Integer> iter = ints.iterator();
		for (int i = 0; i < ret.length; ++i)
			ret[i] = iter.next().intValue();
		return ret;
	}

	public static Integer[] getIntObjectArray(int[] ints) {
		if (ints == null || ints.length == 0) return new Integer[0];
		Integer ret[] = new Integer[ints.length];
		for (int i = 0; i < ints.length; i++) ret[i] = new Integer(ints[i]);
		return ret;
	}

	public static int[] getIntArray(Object[] ints) {
		if (ints == null || ints.length == 0) return new int[0];
		int[] ret = new int[ints.length];
		for (int i = 0; i < ints.length; i++)
			ret[i] = ((Number)ints[i]).intValue();
		return ret;
	}

	public static Collection<Integer> getInts(String input) throws IllegalArgumentException {
		if (input == null || input.length() == 0) return new ArrayList<Integer>(0);

		Collection<Integer> ints = new LinkedList<Integer>();
		StringTokenizer st = new StringTokenizer(input, ",");
		StringTokenizer it;
		int a, b;
		try {
			while (st.hasMoreTokens()) {
				it = new StringTokenizer(st.nextToken(), "-");
				a = Integer.decode(it.nextToken()).intValue();
				if (it.hasMoreTokens())
					b = Integer.decode(it.nextToken().trim()).intValue();
				else
					b = a;

				if (a < b) {
					for (; a <= b; a++)
						ints.add(new Integer(a));
				}
				else {
					for (; a >= b; a--)
						ints.add(new Integer(a));
				}
				if (it.hasMoreTokens())
					throw new IllegalArgumentException("Invalid integer range entered.");
			}
		} catch (NumberFormatException nfe) {
			throw new IllegalArgumentException("Number entered in integer range is invalid.");
		}

		return ints;
	}

	public static Collection<Integer> getIntsSet(String input) throws IllegalArgumentException {
		Collection<Integer> ints = new LinkedHashSet<Integer>();
		StringTokenizer st = new StringTokenizer(input, ",");
		StringTokenizer it;
		int a = 0, b;
		try {
			while (st.hasMoreTokens()) {
				it = new StringTokenizer(st.nextToken(), "-");
				a = Integer.decode(it.nextToken()).intValue();
				if (it.hasMoreTokens())
					b = Integer.decode(it.nextToken().trim()).intValue();
				else
					b = a;

				if (a < b) {
					for (; a <= b; a++) {
						// mdb removed 2/17/09
						//if (!ints.add(new Integer(a)))
						//	throw new IllegalArgumentException("Duplicate ints not allowed");
						
						// mdb added 2/17/09 - prevent duplicates
						if (!ints.contains(new Integer(a)))
							ints.add(new Integer(a));
					}
				}
				else {
					for (; a >= b; a--) {
						// mdb removed 2/17/09
						//if (!ints.add(new Integer(a)))
						//	throw new IllegalArgumentException("Duplicate ints not allowed");
						
						// mdb added 2/17/09 - prevent duplicates
						if (!ints.contains(new Integer(a)))
							ints.add(new Integer(a));
					}
				}
				if (it.hasMoreTokens())
					throw new IllegalArgumentException("Invalid integer range entered.");
			}
		} 
		catch (NumberFormatException nfe) {
			throw new IllegalArgumentException("Number entered in integer range is invalid: " + a);
		}

		return ints;
	}

	/**
	 * Method <code>openURL</code> opens the given url in a browser window.
	 * This should only be used for stand alone java applications that are able to execute
	 * commands.
	 *
	 * @param url a <code>String</code> value of the url to open
	 * @param showError a <code>boolean</code> value of true to display error message dialog if needed
	 * @see Runtime#getRuntime()
	 * @see Runtime#exec(String[])
	 * @return a <code>boolean</code> value of true if no exceptions where encountered
	 */
	public static boolean openURL(String url, boolean showError) { 
		boolean ret = true;
		String osName = System.getProperty("os.name");
		try { 
			if (osName.startsWith("Mac OS")) { 
				Class<?> macUtils = Class.forName("com.apple.mrj.MRJFileUtils");
				Method openURL = macUtils.getDeclaredMethod("openURL", new Class[] {String.class});
				openURL.invoke(null, new Object[] {url}); 
			} 
			else if (osName.startsWith("Windows")) 
				Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + url);
			else { //assume Unix or Linux 
				String[] browsers = { "firefox", "opera", "konqueror", "mozilla", "netscape" }; 
				String browser = null;
				for (int count = 0; count < browsers.length && browser == null; count++) 
					if (Runtime.getRuntime().exec( new String[] {"which", browsers[count]}).waitFor() == 0) 
						browser = browsers[count]; 
				if (browser == null) 
					throw new Exception("Could not find web browser."); 
				else Runtime.getRuntime().exec(new String[] {browser, url}); } 
		} catch (Exception e) { 
			if (showError)
				JOptionPane.showMessageDialog(null,"Error attempting to launch web browser:\n"+e.getLocalizedMessage()); 
			ret = false;
		}
		return ret;
	}

	/**
	 * Method <code>getFileExtension</code> returns the file's extension.
	 *
	 * @param f a <code>File</code> value
	 * @return a <code>String</code> value
	 */
	public static String getFileExtension(File f) {
		String ext = null;
		String s = f.getName();
		int i = s.lastIndexOf('.');

		if (i > 0 &&  i < s.length() - 1) {
			ext = s.substring(i+1).toLowerCase();
		}
		return ext;
	}

	/**
	 * Method <code>getFile</code> return the file. If fromHome the home directory is acquired
	 * by <code>System.getProperty("user.home")</code> and the File constructor taking two string is
	 * invoked (i.e. <code>new File(homeDir,name)<code>).  Otherwise, the file is created by
	 * <code>new File(name)</code>
	 *
	 * @param name a <code>String</code> value of file name
	 * @param fromHome a <code>boolean</code> value of true to set the path from the home directory
	 * @return a <code>File</code> value
	 */
	public static File getFile(String name, boolean fromHome) {
		if (name == null) return new File("");
		if (fromHome) {
			String dir = null;
			try {
				dir = System.getProperty("user.home");
			}
			catch (Exception e) {
				e.printStackTrace();
			}
			return new File(dir,name);
		}
		return new File(name);
	}

	/**
	 * Method <code>equals</code> returns <code>t1 == null ? t2 == null : t1.equals(t2)</code>
	 *
	 * @param t1 an <code>Object</code> value
	 * @param t2 an <code>Object</code> value
	 * @return a <code>boolean</code> value
	 */
	public static boolean equals(Object t1, Object t2) {
		return t1 == null ? t2 == null : t1.equals(t2);
	}

	public static String getHost(Applet applet) {
		if (applet == null) return null;
		String domain = applet.getCodeBase().getHost();
		int ind = domain.indexOf(":");
		if (ind > 0)
			domain = domain.substring(0,ind);
		if (domain.length() == 0) domain = null;
		return domain;
	}

	public static String getDomain(Applet applet) {
		if (applet == null) return null;
		String domain = applet.getCodeBase().getHost();
		int wwwInd = domain.indexOf("www.");
		if (wwwInd >= 0)
			domain = domain.substring(wwwInd+4);
		wwwInd = domain.indexOf(":");
		if (wwwInd > 0)
			domain = domain.substring(0,wwwInd);
		if (domain.length() == 0) domain = null;

		return domain;
	}

	public static String getURI(String uriStr, String altHost) throws URISyntaxException {
		if (uriStr.startsWith("jdbc:")) {
			URI u = new URI(uriStr.substring(5));
			return "jdbc:"+(new URI(u.getScheme(),u.getUserInfo(),altHost,u.getPort(),u.getPath(),u.getQuery(),u.getFragment()));
		}
		URI u = new URI(uriStr);
		return new URI(u.getScheme(),u.getUserInfo(),altHost,u.getPort(),u.getPath(),u.getQuery(),u.getFragment()).toString();
	}

	public static URL getURL(URL url, String altHost) throws MalformedURLException {
		if (altHost == null) 
			return url;
		else if (url != null)
			return new URL(url.getProtocol(),altHost,url.getPort(),url.getFile());
		return null;
	}

	public static double diagonal(Rectangle r) {
		if (r == null) return 0;
		return Math.sqrt((double)( (r.width*r.width) + (r.height*r.height) ));	
	}

	public static double distance(Rectangle r1, Rectangle r2) {
		if (r1.intersects(r2)) return 0;

		int x1 = r1.x+r1.width;
		int x2 = r2.x+r2.width;
		int y1 = r1.y+r1.height;
		int y2 = r2.y+r2.height;

		int d = 0;
		if (r1.x > x2)
			d += (r1.x - x2) * (r1.x - x2);
		else if (r2.x > x1)
			d += (r2.x - x1) * (r2.x - x1);

		if (r1.y > y2)
			d += (r1.y - y2) * (r1.y - y2);
		else if (r2.y > y1)
			d += (r2.y - y1) * (r2.y - y1);

		return Math.sqrt((double)d);
	}

	public static Shape getSmallestBoundingArea(Shape[] shapes) {
		if (shapes == null || shapes.length == 0) return null;
		Shape shape = null;
		Rectangle2D bounds = null;
		double min = -1;
		Shape comp = null;
		double area = 0;
		for (int i = 0; i < shapes.length; i++) {
			comp = shapes[i];
			if (comp != null) {
				bounds = comp.getBounds2D();
				area = bounds.getWidth() * bounds.getHeight();
				if (shape == null || area < min) {
					shape = comp;
					min = area;
				}
			}
		}

		return shape;
	}

	public static Shape getSmallestBoundingArea(Shape s1, Shape s2) {
		if (s1 == null) return s2;
		if (s2 == null) return s1;

		Rectangle2D b1, b2;
		b1 = s1.getBounds2D();
		b2 = s2.getBounds2D();

		return (b1.getWidth() * b1.getHeight()) <= (b2.getWidth() * b2.getHeight()) ? s1 : s2;
	}

	public static String toString(int n) {
		switch (n) {
		case 0: return "zeroth";
		case 1: return "first";
		case 2: return "second";
		case 3: return "third";
		case 4: return "fourth";
		case 5: return "fifth";
		case 6: return "sixth";
		case 7: return "seventh";
		case 8: return "eighth";
		case 9: return "nineth";
		case 10:return "tenth";
		}
		return new Integer(n).toString();
	}

	// ASD added to handle reverse complement of sequences for database changes
	public static String revComplement(String seq) {
		if (seq == null)
			return null;
		StringBuffer retStr = new StringBuffer();
		int i;
		for (i = 0; i < seq.length(); i++) {
			char c = seq.charAt(i);
			switch (c) {
			case 'A':
			case 'a':
				retStr.append("T");
				break;
			case 'T':
			case 't':
				retStr.append("A");
				break;
			case 'G':
			case 'g':
				retStr.append("C");
				break;
			case 'C':
			case 'c':
				retStr.append("G");
				break;
			default:
				retStr.append("N"); // anything nonstandard gets an N
			}
		}
		retStr = retStr.reverse();
		return retStr.toString();
	}
	
	// mdb added 8/25/09
	public static String reverseString(String s) {
		return new StringBuffer(s).reverse().toString();
	}

	// mdb added 5/25/07 #119
	public static void showWarningMessage(String msg) {
		System.out.println(msg);
		JOptionPane.showMessageDialog(null, msg, "Warning", JOptionPane.WARNING_MESSAGE);
	}
	
	// mdb added 5/25/07 #119
	public static void showErrorMessage(String msg) {
		System.err.println(msg);
		JOptionPane.showMessageDialog(null, msg, "Error", JOptionPane.ERROR_MESSAGE);
	}
	
	// mdb added 5/25/07 #119
	public static void showErrorMessage(String msg, int exitStatus) {
		showErrorMessage(msg);
		exit(exitStatus);
	}
	
	// mdb added 9/3/09
	public static void showOutOfMemoryMessage(Component parent) {
		System.err.println("Not enough memory.");
		JOptionPane optionPane = new JOptionPane("Not enough memory.", JOptionPane.ERROR_MESSAGE);
		
		LinkLabel label = new LinkLabel("Click to open the Troubleshooting Guide.", 
				SyMAP.TROUBLE_GUIDE_URL + "#not_enough_memory");
		label.setAlignmentX(Component.CENTER_ALIGNMENT);
		optionPane.add(new JLabel(" "), 1);
		optionPane.add(label, 2);
		optionPane.add(new JLabel(" "), 3);
		
		JDialog dialog = optionPane.createDialog(parent, "Error");
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		dialog.setVisible(true);
		optionPane.getValue(); // Wait on user input
	}
	public static void showOutOfMemoryMessage() { showOutOfMemoryMessage(null); }
	
	// mdb added 5/25/07 #119
	public static void exit(int status) {
		System.out.println("Exiting SyMAP");
		System.exit(status);
	}
	
	// mdb added 1/8/09
	public static boolean isOverlapping(int start1, int end1, int start2, int end2) {
		if (   (start1 >= start2 && start1 <= end2) 
			|| (end1   >= start2 && end1   <= end2)
			|| (start2 >= start1 && start2 <= end1)
			|| (end2   >= start1 && end2   <= end1))
		{
			return true;
		}
		
		return false;
	}
	
	// mdb added 1/8/09
	public static boolean isContained(int start1, int end1, int start2, int end2) {
		if (   (start1 >= start2 && start1 <= end2) 
			&& (end1   >= start2 && end1   <= end2))
		{
			return true;
		}
		
		return false;
	}
	
	// mdb added 4/29/09
	public static boolean isStringEmpty(String s) {
		return (s == null || s.length() == 0);
	}
	
	// mdb added 3/24/09
	public static void deleteFile ( String strPath )
	{
		File theFile = new File ( strPath );
		theFile.delete();
	}
	
	// mdb added 4/28/09
	public static void clearDir(String dir)
	{
		File d = new File(dir);
		if (!d.exists()) d.mkdir();
		if (d.isDirectory())
		{
			for (File f : d.listFiles())
				if (f.isFile())
					f.delete();
		}
	}
	
    // mdb added 3/24/09
    public static boolean fileExists(String filepath)
    {
    	if (filepath == null) return false;
    	File f = new File(filepath);
    	return f.isFile() && f.exists();
    }
    
    // mdb added 3/24/09
    public static boolean pathExists(String path)
    {
    	if (path == null) return false;
    	File f = new File(path);
    	return f.exists();
    }
    
    // mdb added 3/24/09
	public static void checkCreateDir(String dir)
	{
		File f = new File(dir);
		if (!f.exists())
			f.mkdir();
	}
	
	// mdb added 3/24/09
	public static void checkCreateFile(String path)
	{
		File f = new File(path);
		if (!f.exists()) {
			try {
				f.createNewFile();
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	// mdb added 4/27/09
	public static void checkCreateFile(String path, String contents)
	{
		if (contents == null || contents.length() == 0) {
			checkCreateFile(path);
		}
		else {
			File f = new File(path);
			if (!f.exists()) {
				try {
					FileWriter fw = new FileWriter(f);
					fw.write(contents, 0, contents.length());
					fw.close();
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
	
    // mdb added 4/6/09
    public static String getDurationString(long duration) { // milliseconds
    	duration /= 1000;
    	long min = duration / 60; 
    	long sec = duration % 60; 
    	long hr = min / 60;
    	min = min % 60; 
    	long day = hr / 24;
    	hr = hr % 24;
    	
    	return (day > 0 ? day+" days " : "")
    			+ (hr > 0 ? hr+" hr " : "")
    			+ (min > 0 ? min+" min " : "") 
    			+ sec + " sec";
    }
    
    // mdb added 4/9/09
    public static void sleep(int milliseconds) {
    	try{ Thread.sleep(milliseconds); }
    	catch (InterruptedException e) { }
    }
    
    // mdb added 4/20/09
    public static String pad(String s, int width)
    {
    	width -= s.length();
    	while (width-- > 0) s += " ";
    	return s;
    }
    
    public static boolean tryOpenURL ( Applet theApplet, String theLink ) {
    	if (theLink == null)
    		return false;
    	
    	URL url = null;
    	try {
    		url = new URL(theLink);
    	}
    	catch (MalformedURLException e) {
    		System.out.println("Malformed URL: " + theLink);
    		return false;
    	}
    	return tryOpenURL(theApplet, url);
    }
    
    // mdb added 4/27/09 - copied from jPAVE
	public static boolean tryOpenURL ( Applet theApplet, URL theLink )
    {
    	// Show document with applet if we have one
		if ( theApplet != null )
		{
			theApplet.getAppletContext().showDocument( theLink, "_blank" );
			return true;
		}
		
		// Brian says: Otherwise unless we become a web start application
    	// we are stuck with the below.  Copied this from: 
		// http://www.centerkey.com/java/browser/
    	try 
    	{ 
    		if (isMac()) 
    		{ 
    			Class<?> fileMgr = Class.forName("com.apple.eio.FileManager"); 
    			Method openURL = fileMgr.getDeclaredMethod("openURL", new Class[] {String.class}); 
    			openURL.invoke(null, new Object[] { theLink.toString() }); 
    			return true;
    		} 
    		else if (isWindows()) 
    		{
    			Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + theLink); 
    			return true;
    		}
    		else 
    		{ 
    			//assume Unix or Linux 
    			String[] browsers = { "firefox", "opera", "konqueror", "epiphany", "mozilla", "netscape" }; 
    			String browser = null; 
    			for (int count = 0; count < browsers.length && browser == null; count++) 
    				if (Runtime.getRuntime().exec( new String[] {"which", browsers[count]}).waitFor() == 0) 
    					browser = browsers[count]; 
    			if (browser == null) 
    				return false;
    			else 
    			{
    				Runtime.getRuntime().exec(new String[] {browser, theLink.toString()});
    				return true;
    			}
    		}
    	}
    	catch (Exception e) 
    	{ 	
    		e.printStackTrace();
    	}
    	
		return false;
    }
	
	// mdb added 5/11/09
	public static boolean isLinux() {
		return System.getProperty("os.name").toLowerCase().contains("linux");
	}
	
	// mdb added 5/11/09
	public static boolean isWindows() {
		return System.getProperty("os.name").toLowerCase().contains("windows");
	}
	
	// mdb added 5/11/09
	public static boolean isMac() {
		return System.getProperty("os.name").toLowerCase().contains("mac");
	}
	
	// mdb added 7/23/09
	public static boolean is64Bit() {
		return System.getProperty("os.arch").toLowerCase().contains("64");
	}
	
	// mdb moved here from dotplot.ControlPanel 7/8/09 - shared by symap.frame.ControlPanel
	// mdb rewritten 1/29/09 #159 to not use properties
    public static AbstractButton createButton(HelpListener parent, String path, String tip, HelpBar bar, ActionListener listener, boolean checkbox) {
		AbstractButton button;
		//Icon icon = ImageViewer.getIcon(data.getApplet(),ControlPanel.class.getResource(name)); // mdb removed 3/2/09
		Icon icon = ImageViewer.getImageIcon(path); // mdb added 3/2/09
		if (icon != null) {
		    if (checkbox)
		    	button = new JCheckBox(icon);
		    else
		    	button = new JButton(icon);
		    	button.setMargin(new Insets(0,0,0,0));
		}
		else {
		    if (checkbox)
		    	button = new JCheckBox(path);
		    else
		    	button = new JButton(path);
		    	button.setMargin(new Insets(1,3,1,3));
		}
		if (listener != null) 
		    button.addActionListener(listener);

		button.setToolTipText(tip);
		
		// mdb added 7/6/09
		button.setName(tip); 
		if (bar != null) bar.addHelpListener(button,parent);
		
		return button;
    }
    
	// mdb added 7/29/09
	public static boolean hasCommandLineOption(String[] args, String name)
	{
		for (int i = 0;  i < args.length;  i++)
			if (args[i].startsWith(name)) 
				return true;
		return false;
	}
	
	public static String getCommandLineOption(String[] args, String name)
	{
		for (int i = 0;  i < args.length;  i++)
		{
			if (args[i].startsWith(name) && !args[i].equals(name))
			{
				String ret = args[i];
				ret = ret.replaceFirst(name,"");
				return ret;
			}
			else if (args[i].equals(name) && i+1 < args.length) 
			{
				return args[i+1];
			}
		}

		return null;
	}
	
	// mdb added 2/9/10
	public static String getBrowserPopupMessage(Applet applet) {
		return (applet != null ? "\nMake sure popup windows are enabled in your web browser settings." : "" );
	}
	
	public static boolean isRunning(String processName)
	{
		boolean running = false;
		try 
		{
        	String line;
        	Process p = Runtime.getRuntime().exec("ps -ef");
        	BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
        	while ((line = input.readLine()) != null) 
        	{
        		//System.out.println(line);
            	if (line.contains(processName))
            	{
            		running = true;	
            		break;
            	}
        	}
        	input.close();
	    } 
		catch (Exception err) 
		{
	    }

		return running;
	}
	public static void updateSchemaTo35(DatabaseReader db)
	{
		try
		{
			Connection c = db.getConnection();
			Statement s = c.createStatement();
			ResultSet r = s.executeQuery("show columns from groups like 'flipped'");
			if (!r.first())
			{
				System.out.println("Updating database to v4.0");
				s.executeUpdate("alter table groups add flipped boolean default 0");
			}
			r.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.exit(0);
		}
	}
    public static int ctrGet(Map<String,Integer> ctr, String key)
    {
    	return (ctr.containsKey(key) ? ctr.get(key) : 0);
    }
    public static void init_rs2col()
    {
    	rs2col = new HashMap<String,Integer>();
    }
    // 
    // Store and retrieve ResultSet column mappings, since the native implementations are so slow.
    // Must be initialized with init_rs2col. 
    // Should be shareable between threads if they are all processing the exact same column output. 
    //
    public static synchronized int rs2col(String key, ResultSet rs) throws Exception
    {
    	if (!rs2col.containsKey(key))
    	{
    		try
    		{
    			rs2col.put(key, rs.findColumn(key));
    		}
    		catch(Exception e)
    		{
    			System.out.println("SQL result column " + key + " not found!!");
    			throw(e);
    		}
    	}
    	return rs2col.get(key);
    }
	public static void timerStart()
	{
		mTimeStart = new Date();
	}
	public static void timerEnd()
	{
		Date now = new Date();
		Long elapsed = now.getTime() - mTimeStart.getTime();
		elapsed /= 1000;
		if (elapsed < 300)
		{
			System.out.println("Finished in " + elapsed + " seconds");
		}
		else
		{
			elapsed /= 60;
			System.out.println("Finished in " + elapsed + " minutes");
		}
	}
	public static boolean tableHasColumn(String table, String column, Statement s) throws SQLException
	{
		boolean ret = false;
		ResultSet rs = s.executeQuery("show columns from " + table + " where field='" + column + "'");
		if (rs.first()) ret = true;
		rs.close();
		return ret;
	}
	
	public static void setResClass(Class c)
	{
		resClass = c;
	}
	public static void setHelpParentFrame(Frame f)
	{
		helpParentFrame = f;
	}
	public static void showHTMLPage(JDialog parent, String title, String resource)
	{
		if (resClass == null)
		{
			System.err.println("Help can't be shown.\nDid you call setResClass?");
			return;
		}
		JDialog dlgRoot = (parent == null ? new JDialog(helpParentFrame,title,false)
										: new JDialog(parent,title,false));
		dlgRoot.setPreferredSize(new Dimension(800,800));
		Container dlg = dlgRoot.getContentPane();
		dlg.setLayout(new BoxLayout(dlg,BoxLayout.Y_AXIS));
		
		StringBuffer sb = new StringBuffer();
		try
		{
			InputStream str = resClass.getResourceAsStream(resource);
			
			int ci = str.read();
			while (ci != -1)
			{
				sb.append((char)ci);
				ci = str.read();
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		
		//String imgsrc =   resClass.getResource("/html/test.png").toString();
		String html = sb.toString();
		
		//html = html.replace("IMG1", imgsrc);
		
		JEditorPane jep = new JEditorPane();
		jep.setContentType("text/html");
		jep.setEditable(false);
	    jep.addHyperlinkListener(new javax.swing.event.HyperlinkListener() 
	    {
	        public void hyperlinkUpdate(javax.swing.event.HyperlinkEvent evt) 
	        {
	            Utilities.jepHandle(evt);
	        }
	    });
		jep.setText(html);
		jep.setVisible(true);
		jep.setCaretPosition(0);
		JScrollPane jsp = new JScrollPane(jep);
		dlg.add(jsp);
	
		dlgRoot.pack();
		dlgRoot.setVisible(true);
		dlgRoot.toFront();
		dlgRoot.requestFocus();
	}


// html listener for product info editor pane
    private static void jepHandle(javax.swing.event.HyperlinkEvent evt) 
    {
    	javax.swing.event.HyperlinkEvent.EventType type = evt.getEventType();
	    if (type == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED)
	    {
	        String urlString = evt.getURL().toExternalForm();
	        if (urlString == null)
	        {
	            System.out.println("Problem extracting html link");
	            return;
	        }	
	        try
	        {
	        	tryOpenURL(null,urlString);
	        }
	        catch (Exception e)
	        {
	            System.out.println("Unable to laucnh external browser");
	        }
	    }
    } 
}
