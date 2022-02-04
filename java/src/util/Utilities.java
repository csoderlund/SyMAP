package util;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Window;
import java.awt.GraphicsConfiguration;
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
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import java.awt.Frame;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.StringTokenizer;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.MalformedURLException;

import java.io.File;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;

import symap.SyMAP;
import symap.frame.HelpBar;
import symap.frame.HelpListener;
import backend.Constants;

/**
 * Class <code>Utilities</code> class for doing some miscelaneous things that 
 * may be useful to others. Requires plugin.jar
 * 
 * CAS506 - remove goobs on unused stuff and rearranged into the following order (marked with X's
 * Interface
 * basic array and string ops
 * Geometry
 * DNA string
 * File
 * Time
 * PopUps
 */
public class Utilities {
	private static boolean TRACE = Constants.TRACE;
	public static final Color HELP_PROMPT = new Color(0xEEFFEE); // CAS504 moved from dead file - for Help
	
	private static Class resClass = null; // store this so help pages can be loaded from anywhere
	private static Frame helpParentFrame = null; 
	
	private Utilities() { }

	/** Instance stuff - only class methods **/ 
	public static void setResClass(Class c)
	{
		resClass = c;
	}
	public static void setHelpParentFrame(Frame f)
	{
		helpParentFrame = f;
	}
	
	/************************************************************
	 * XXX Random interface 
	 */
	public static boolean isLinux() {
		return System.getProperty("os.name").toLowerCase().contains("linux");
	}
	
	public static boolean isWindows() {
		return System.getProperty("os.name").toLowerCase().contains("windows");
	}
	
	public static boolean isMac() {
		return System.getProperty("os.name").toLowerCase().contains("mac");
	}
	
	public static boolean is64Bit() {
		return System.getProperty("os.arch").toLowerCase().contains("64");
	}
	
	//  attempts to find the screen bounds from the screen insets.  
	public static Rectangle getScreenBounds(Window window) {
		GraphicsConfiguration config = window.getGraphicsConfiguration();
		Rectangle b = config.getBounds();
		Insets inset = Toolkit.getDefaultToolkit().getScreenInsets(config);
		if (inset.left != 0 || inset.right != 0 || inset.top != 0 || inset.bottom != 0)
			b.setBounds(b.x+inset.left,b.y+inset.top,b.width-inset.left-inset.right,b.height-inset.top-inset.bottom);
		
		return b;
	}

	 // sets the window to the full size of the available screen or it's preferred
	 // size, whichever is smaller, using the other methods in this class.
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
	
	public static void setCursorBusy(Component c, boolean busy) {
		if (busy)
			c.setCursor( Cursor.getPredefinedCursor( Cursor.WAIT_CURSOR ) );
		else
			c.setCursor( Cursor.getDefaultCursor() );
	}
	
	public static void exit(int status) {
		System.out.println("Exiting SyMAP");
		System.exit(status);
	}
	public static void sleep(int milliseconds) {
 		try{ Thread.sleep(milliseconds); }
 		catch (InterruptedException e) { }
	}

	public static boolean isRunning(String processName)
	{
		boolean running = false;
		try  {
	        	String line;
	        	Process p = Runtime.getRuntime().exec("ps -ef");
	        	BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
	        	while ((line = input.readLine()) != null) 
	        	{
	            	if (line.contains(processName))
	            	{
	            		running = true;	
	            		break;
	            	}
	        	}
	        	input.close();
	    } 
		catch (Exception err) {}

		return running;
	}
	 
	 public static AbstractButton createButton(HelpListener parent, String path, String tip, HelpBar bar, ActionListener listener, boolean checkbox) {
		AbstractButton button;
		
		Icon icon = ImageViewer.getImageIcon(path); 
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
		
		button.setName(tip); 
		if (bar != null) bar.addHelpListener(button,parent);
		
		return button;
	 }
	 /**********************************************************
	  * CAS507 replace the old one with this
	  */
	 public static boolean tryOpenURL (String theLink ) {
    	if (theLink == null) return false;
    	
    	try {
    		new URL(theLink); // make sure it works
    	}
    	catch (MalformedURLException e) {
    		System.out.println("Malformed URL: " + theLink);
    		return false;
    	}
    	if (isMac()) 	return tryOpenMac(theLink);
    	else 			return tryOpenLinux(theLink);	
	}
	// CAS507 the previous method was becoming obsolete, but this doesn't work on Linux 1.8 
	private static boolean tryOpenMac(String theLink) { 
		Desktop desktop = java.awt.Desktop.getDesktop();
    	URI oURL;
    	try {
			oURL = new URI(theLink);
    	} catch (URISyntaxException e) {
    		e.printStackTrace();
    		return false;
    	}
    	
		try {
			desktop.browse(oURL);
			return true;
 		} catch (IOException e) {
 			e.printStackTrace();
 		}
		return false;
	}
	
	public static boolean tryOpenLinux (String theLink) // CAS507 removed Applet
	{		
		// Copied this from: http://www.centerkey.com/java/browser/   CAS507 added all listed browsers from this site
    	try 
    	{ 
    		if (isWindows()) {
    			Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + theLink); // no idea if this works
    			return true;
    		}
    		else { 
    			String [] browsers = {"firefox", "opera", "konqueror", "epiphany", "mozilla", "netscape",
    					   "google-chrome", "conkeror", "midori", "kazehakase", "x-www-browser"}; // CAS507 added
    			String browser = null; 
    			for (int count = 0; count < browsers.length && browser == null; count++) 
    				if (Runtime.getRuntime().exec( new String[] {"which", browsers[count]}).waitFor() == 0) 
    					browser = browsers[count]; 
    			if (browser == null) 
    				return false;
    			else {
    				Runtime.getRuntime().exec(new String[] {browser, theLink});
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
	 
	/*******************************************************************
	 * XXX basic array and string ops
	 */
	 public static String pad(String s, int width)
	 {
	    width -= s.length();
	    while (width-- > 0) s += " ";
	    return s;
	 }
	 
	public static boolean contains(Object[] ar, Object o) {
		if (ar != null)
			for (int i = 0; i < ar.length; ++i)
				if (equals(o,ar[i])) return true;
		return false;
	}
	private static boolean equals(Object t1, Object t2) {
		return t1 == null ? t2 == null : t1.equals(t2);
	}
	
	// adjust rectangle coordinates for negative width or height
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

	public static int[] copy(int[] a, int len) {
		if (a == null || a.length == 0 || len <= 0) return new int[0];
		int[] ret = new int[len];
		System.arraycopy(a,0,ret,0,Math.min(len,a.length));
		return ret;
	}

	 // copys the array and sorts the copy, returning the copied array.
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

	private static Collection<Integer> getInts(String input) throws IllegalArgumentException {
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
						ints.add(Integer.valueOf(a)); // CAS506 new Integer(a)
				}
				else {
					for (; a >= b; a--)
						ints.add(Integer.valueOf(a)); // CAS506 new Integer(a)
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
						if (!ints.contains(Integer.valueOf(a))) // CAS506 new Integer(a)
							ints.add(Integer.valueOf(a));
					}
				}
				else {
					for (; a >= b; a--) {
						if (!ints.contains(Integer.valueOf(a))) // CAS506 new Integer(a)
							ints.add(Integer.valueOf(a));
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

	public static boolean isStringEmpty(String s) {
		return (s == null || s.length() == 0);
	}
	
	/***************************************************
	 * XXX Geometry
	 */
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
	/*****************************************************
	 * XXX DNA string
	 */
	// handle reverse complement of sequences for database changes
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
	
	public static String reverseString(String s) {
		return new StringBuffer(s).reverse().toString();
	}

	/********************************************************************
	 * XXX File ops
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

	// Return the Files; if fromHome==true, relative to home directroy
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


	public static void deleteFile ( String strPath )
	{
		File theFile = new File ( strPath );
		theFile.delete();
	}

	public static void clearAllDir(File d)
	{
		if (d.isDirectory())
		{
			for (File f : d.listFiles())
			{
				if (f.isDirectory() && !f.getName().equals(".") && !f.getName().equals("..")) 
				{
					clearAllDir(f);
				}
				f.delete();
			}
		}
		if (TRACE) System.out.println("XYZ clear all directory " + d);
	}
	
    public static boolean fileExists(String filepath)
    {
	    	if (filepath == null) return false;
	    	File f = new File(filepath);
	    	return f.exists() && f.isFile();
    }
    public static boolean dirExists(String filepath)
    {
	    	if (filepath == null) return false;
	    	File f = new File(filepath);
	    	return f.exists() && f.isDirectory();
    }
    public static boolean pathExists(String path)
    {
    		if (path == null) return false;
    		File f = new File(path);
    		return f.exists();
    }
    public static int dirNumFiles(File d)
	{
		int numFiles = 0;
		for (File f : d.listFiles())
		{
			if (f.isFile() && !f.isHidden()) numFiles++;	
		}
		return numFiles;
	}
    public static File checkCreateDir(File path, String dir, String trace)
	{
		try {
			File f = new File(path, dir);
			if (f.exists() && f.isFile()) {
				System.out.println("Please remove file " + f.getName()
						+ " as SyMAP needs to create a directory at this path");
				System.exit(0);
			}
			if (!f.exists()) {
				f.mkdir();
				if (TRACE) System.out.println(trace + ": XYZ mkdir: " + path.getName() + " " + dir);
			}
			return f;
		}
		catch (Exception e) {
			ErrorReport.print(e, "Create dir " + path.getName() + " " + dir);
			return null;
		}
	}
    
	public static File checkCreateDir(String dir, String trace)
	{
		try {
			File f = new File(dir);
			if (f.exists() && f.isFile()) {
				System.out.println("Please remove file " + f.getName()
						+ " as SyMAP needs to create a directory at this path");
				System.exit(0);
			}
			
			if (!f.exists()) {
				f.mkdir();
				if (TRACE) System.out.println(trace + ": XYZ mkdir: " + dir);
			}
	
			return f;
		}
		catch (Exception e) {
			ErrorReport.print(e, "Create dir " + dir);
			return null;
		}
	}
	
	public static void checkCreateFile(String path, String trace)
	{
		File f = new File(path);
		if (f.exists()) {
			if (TRACE) System.out.println(trace + ": XYZ delete existing file: " + path);
			f.delete();
		}
		try {
			f.createNewFile();
			if (TRACE) System.out.println(trace + ": XYZ create file: " + path);
		}
		catch (Exception e) {
			ErrorReport.print(e, "Create file " + path);
		}
	}
	
	public static File checkCreateFile(File path, String name, String trace)
	{
		File f = new File(path, name);
		if (f.exists()) {
			if (TRACE) System.out.println(trace + ": XYZ delete existing file: " + f.getName());
			f.delete();
		}
		try {
			f.createNewFile();
			if (TRACE) System.out.println(trace + ": XYZ create file: " + f.getName());
			return f;
		}
		catch (Exception e) {
			ErrorReport.print(e, "Create file " + path.getName() + " " + name);
			return null;
		}	
	}
	
	public static String fileOnly(String path) {
		return path.substring(path.lastIndexOf("/")+1, path.length());
	}
    
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
   
    /**********************************************************
     * XXX Time methods
     */
	static public String getDateOnly ( ) // CAS506 day-month-year
    {
        Date date=new Date();
        SimpleDateFormat sdf=new SimpleDateFormat("dd-MMM-yy"); 
        return sdf.format(date);
    }
	       
	public static String getDurationString(long duration) { // milliseconds
    	duration /= 1000;
    	long min = duration / 60; 
    	long sec = duration % 60; 
    	long hr = min / 60;
    	min = min % 60; 
    	long day = hr / 24;
    	hr = hr % 24;
    	
    	return (day > 0 ? day+" days " : "") + (hr > 0 ? hr+" hr " : "") + (min > 0 ? min+" min " : "") 
    			+ sec + " sec";
	}
	public static void timerStr(String msg, Date timeStart) {
		Date now = new Date();
		Long elapsed = now.getTime() - timeStart.getTime();
		elapsed /= 1000;
		String x = String.format("%-8s  ", msg);
		if (elapsed <= 60)
		{
			System.out.println(x + elapsed + "s");
		}
		else
		{
			elapsed /= 60;
			long sec = elapsed%60;
			System.out.println(x + elapsed + "m:" + sec + "s");
		}
	}
	
	static public long getNanoTime () {
 		return System.nanoTime(); 
	}
	static public void printElapsedNanoTime(String msg, long startTime) {
		long et = System.nanoTime()-startTime;
		long sec = et /1000000000;
		timerStr2(msg, sec);
	}
	
	static public void timerStr2(String msg, long et) {
		String x = String.format("%-8s  ", msg);
	
		long day = 	et/86400; //24*3600
		long time =  et%86400;
		long hr =  time/3600;
		
		time %= 3600;
		long min = time/60;
		long sec = time%60;
		
		String str = " ";
		if (day > 0) str += day + "d:";
		if (hr > 0 ) str += hr + "h:";
		str += min + "m:" + sec + "s";
		System.out.println(x + str);
	}

	/**************************************************************
	 * XXX Popups
	 */
	
	// CAS504 add
	// isModal=true means that everything is frozen until the window is closed
	public static void displayInfoMonoSpace(Component parentFrame, String title, 
			String theMessage, boolean isModal) {
		JOptionPane pane = new JOptionPane();
		
		JTextArea messageArea = new JTextArea(theMessage);

		JScrollPane sPane = new JScrollPane(messageArea); 
		messageArea.setFont(new Font("monospaced", Font.BOLD, 12));
		messageArea.setEditable(false);
		messageArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		
		pane.setMessage(sPane);
		pane.setMessageType(JOptionPane.PLAIN_MESSAGE);

		JDialog helpDiag = pane.createDialog(parentFrame, title);
		helpDiag.setModal(isModal);
		helpDiag.setResizable(true);
		
		helpDiag.setVisible(true);		
	}
	static public boolean showContinue (String title, String msg) {
		String [] options = {"Continue", "Cancel"};
		int ret = JOptionPane.showOptionDialog(null, 
				msg + "\nContinue?",
				title, JOptionPane.YES_NO_OPTION, 
				JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
		if (ret == JOptionPane.NO_OPTION) return false;
		return true;
	}
	static public boolean showContinue (Component c,String title, String msg) {
		String [] options = {"Continue", "Cancel"};
		int ret = JOptionPane.showOptionDialog(c, 
				msg + "\nContinue?",
				title, JOptionPane.YES_NO_OPTION, 
				JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
		if (ret == JOptionPane.NO_OPTION) return false;
		return true;
	}
	public static void showWarningMessage(String msg) {
		System.out.println(msg);
		JOptionPane.showMessageDialog(null, msg, "Warning", JOptionPane.WARNING_MESSAGE);
	}
	
	public static void showErrorMessage(String msg) {
		System.err.println(msg);
		JOptionPane.showMessageDialog(null, msg, "Error", JOptionPane.ERROR_MESSAGE);
	}
	
	public static void showErrorMessage(String msg, int exitStatus) {
		showErrorMessage(msg);
		exit(exitStatus);
	}
	
	public static void showOutOfMemoryMessage(Component parent) {
		System.err.println("Not enough memory.");
		JOptionPane optionPane = new JOptionPane("Not enough memory - increase $maxmem in symap script.", JOptionPane.ERROR_MESSAGE);
		
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
		catch(Exception e){ErrorReport.print(e, "Show HTML page");}
		
		String html = sb.toString();
		
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

// html listener for product info editor pane CAS507 re
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
	        	//tryOpenURL(urlString);
	        	System.out.println("Did not open URL string");
	        }
	        catch (Exception e)
	        {
	            System.out.println("Unable to launch external browser");
	        }
	    }
	    else System.out.println("Not activated");
    } 
 
}
