package util;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.awt.GraphicsConfiguration;
import java.awt.Rectangle;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Container;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.WindowConstants;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;

import symap.Globals;
import symap.sequence.Annotation;

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
	private static boolean TRACE = false;
	public static final Color HELP_PROMPT = new Color(0xEEFFEE); // CAS504 moved from dead file - for Help

	private Utilities() { }

	/************************************************************
	 * XXX Random interface 
	 */
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
	 // CAS531 added a max size for CloseUpDialog as full screen is too big on a big screen
	public static void setFullSize(Window window, Container view, int max) {
		window.pack();

		Dimension pref = window.getPreferredSize();
		Rectangle b = getScreenBounds(window);

		pref.width = Math.min(pref.width,b.width); 
		pref.width = Math.min(pref.width, max);
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

	public static boolean isRunning(String processName) {
		boolean running = false;
		try  {
	        	String line;
	        	String [] x = {"ps", "-ef"};
	        	Process p = Runtime.getRuntime().exec(x); // CAS547 single string depreciated
	        	BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
	        	while ((line = input.readLine()) != null)  {
	            	if (line.contains(processName)){
	            		running = true;	
	            		break;
	            	}
	        	}
	        	input.close();
	    } 
		catch (Exception err) {}

		return running;
	}
	/*******************************************************************
	 * XXX basic array and string ops
	 */
	 public static String pad(String s, int width){
	    width -= s.length();
	    while (width-- > 0) s += " ";
	    return s;
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
	public static double getDouble(String i) { // CAS553 added
		try {
			double x = Double.parseDouble(i);
			return x;
		}
		catch (Exception e) {return -1.0;}
	}
	public static int getInt(String i) { // CAS532 added
		try {
			int x = Integer.parseInt(i);
			return x;
		}
		catch (Exception e) {return -1;}
	}

	public static int[] getIntArray(Collection<Integer> ints) throws ClassCastException {
		int[] ret = new int[ints.size()];
		Iterator<Integer> iter = ints.iterator();
		for (int i = 0; i < ret.length; ++i)
			ret[i] = iter.next().intValue();
		return ret;
	}

	public static boolean isEmpty(String s) {
		return (s == null || s.length() == 0);
	}
	
	public static boolean isOverlap(int start1, int end1, int start2, int end2) {
		if ((start1 >= start2 && start1 <= end2) || (end1  >= start2 && end1 <= end2)
		 || (start2 >= start1 && start2 <= end1) || (end2  >= start1 && end2 <= end1)) return true;
		
		return false;
	}
	
	/********************************************************************
	 * XXX File ops
	 */
	
	// Return the Files; if fromHome==true, relative to home directroy
	public static File getFile(String name, boolean fromHome) {
		if (name == null) return new File("");
		if (fromHome) {
			String dir = null;
			try {
				dir = System.getProperty("user.home");
			}
			catch (Exception e) {ErrorReport.print(e, "Get file from users's directory");}
			return new File(dir,name);
		}
		return new File(name);
	}
	public static void deleteFile ( String strPath ){
		File theFile = new File ( strPath );
		theFile.delete();
	}

	public static void clearAllDir(File d){ // does not remove top directory d
		if (d.isDirectory()){
			for (File f : d.listFiles()){
				if (f.isDirectory() && !f.getName().equals(".") && !f.getName().equals("..")) {
					clearAllDir(f);
				}
				f.delete();
			}
		}
		if (TRACE) System.out.println("XYZ clear all directory " + d);
	}
	public static boolean deleteDir(File dir) { // CAS534 moved from ManagerFrame;
        if (dir.isDirectory()) {
            String[] subdir = dir.list();
            
            if (subdir != null) {
	            for (int i = 0;  i < subdir.length;  i++) {
	                boolean success = deleteDir(new File(dir, subdir[i]));
	                if (!success) {
	                	System.err.println("Error deleting " + dir.getAbsolutePath());
	                    return false;
	                }
	            }
            }
        } 
        System.gc(); // closes any file streams that were left open to ensure delete() succeeds
        return dir.delete();
    }
    public static boolean fileExists(String filepath) {
    	if (filepath == null) return false;
    	File f = new File(filepath);
    	return f.exists() && f.isFile();
    }
    public static boolean dirExists(String filepath){
    	if (filepath == null) return false;
    	File f = new File(filepath);
    	return f.exists() && f.isDirectory();
    }
    public static boolean pathExists(String path) {
		if (path == null) return false;
		File f = new File(path);
		return f.exists();
    }
    public static int dirNumFiles(File d){
		int numFiles = 0;
		for (File f : d.listFiles()){
			if (f.isFile() && !f.isHidden()) numFiles++;	
		}
		return numFiles;
	}
    
	public static File checkCreateDir(String dir, boolean bPrt) {
		try {
			File f = new File(dir);
			if (f.exists() && f.isFile()) {
				ErrorReport.die("Please remove file " + f.getName()
					+ " as SyMAP needs to create a directory at this path");
			}
			if (!f.exists()) {
				f.mkdir();
				if (bPrt) System.out.println("Create directory " + dir); // CAS511
			}
			return f;
		}
		catch (Exception e) {
			ErrorReport.print(e, "Create dir " + dir);
			return null;
		}
	}
	public static void checkCreateFile(String path, String trace){
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
	
	public static File checkCreateFile(File path, String name, String trace){
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
	
	public static String fileDate(String path) { // CAS557 added for xToSymap
	try {
		File f = new File(path);
		 if (f.exists()) {
	         long lastModified = f.lastModified();
	         Date date = new Date(lastModified);
	         SimpleDateFormat formatter = new SimpleDateFormat("dd-MMM-yyyy HH:mm");
	         String formattedDate = formatter.format(date);
	         return formattedDate;
		 }
	}
	catch (Exception e) {ErrorReport.print(e, "get file date " + path); }
	return "";
	}
	// return path/file  (not path//file or pathfile path/file/)
	public static String fileNormalizePath(String path, String file) {
		String p = path, f = file;
		
		if (path.endsWith("/") && file.startsWith("/")) p = p.substring(0, p.length()-1);
		else if (!path.endsWith("/") && !file.startsWith("/")) f = "/" + f;
		
		if (file.endsWith("/")) f  = f.substring(0, f.length()-1); 
		
		return p + f;
	}
    /**********************************************************
     * XXX Time methods
     */
	static public String getNormalizedDate(String nDate) {// date from NOW() 'year-mo-dy time'
		String d=nDate.substring(0, nDate.indexOf(" "));
		String [] tok = d.split("-");
		if (tok.length!=3) return nDate;
		
		int m = getInt(tok[1]);
		String ms="Jan";
		if (m==2) ms="Feb"; 
		else if (m==3) ms="Mar";
		else if (m==4) ms="Apr";
		else if (m==5) ms="May";
		else if (m==6) ms="Jun";
		else if (m==7) ms="Jul";
		else if (m==8) ms="Aug";
		else if (m==9) ms="Sep";
		else if (m==10) ms="Oct";		// CAS546 Oct and Nov were swapped!
		else if (m==11) ms="Nov";
		else if (m==12) ms="Dec";
		return tok[2] + "-" + ms + "-" + tok[0];
	}
	
	public static String reformatAnnotDate(String adate) { // For Project - show on left of manager (see DB project.annotdate)
		try {
			String dt;
			if (adate.indexOf(" ")>0) dt = adate.substring(0, adate.indexOf(" "));
			else if (adate.startsWith("20")) dt = adate;
			else return "???";
			
			SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd"); 
	        Date d = sdf.parse(dt);
	        sdf = new SimpleDateFormat("ddMMMyy"); 
	        return sdf.format(d);
		}
		catch (Exception e) {return "???";}
	}
	public static String getDateStr(long l) {
		DateFormat sdf = new SimpleDateFormat("dd-MMM-yyyy hh:mm a");
		return sdf.format(l);
	}
	 static public String getDateTime ( )
    {
        Date date=new Date();
        SimpleDateFormat sdf=new SimpleDateFormat("dd-MMM-yy HH:mm:ss"); 
        return sdf.format(date);
    }
	static public String getDateOnly ( ) // CAS506 day-month-year
    {
        Date date=new Date();
        SimpleDateFormat sdf=new SimpleDateFormat("dd-MMM-yy"); 
        return sdf.format(date);
    }
	// System.currentTimeMillis()     
	public static String getDurationString(long duration) { // milliseconds
    	duration /= 1000;
    	long min = duration / 60; 
    	long sec = duration % 60; 
    	long hr = min / 60;
    	min = min % 60; 
    	long day = hr / 24;
    	hr = hr % 24;
    	
    	return (day > 0 ? day+"d:" : "") + (hr > 0 ? hr+"h:" : "") + (min > 0 ? min+"m:" : "") + sec + "s";
	}
	public static void timerStr(String msg, Date timeStart) {
		Date now = new Date();
		Long elapsed = now.getTime() - timeStart.getTime();
		elapsed /= 1000;
		String x = String.format("%-8s  ", msg);
		if (elapsed <= 60) {
			System.out.println(x + elapsed + "s");
		}
		else {
			elapsed /= 60;
			long sec = elapsed%60;
			System.out.println(x + elapsed + "m:" + sec + "s");
		}
	}
	
	static public long getNanoTime () {
 		return System.nanoTime(); 
	}
	static public String getNanoTimeStr(long startTime) { // CAS533
		long et = System.nanoTime()-startTime;
		long sec = et /1000000000;
		return timerStr2(sec);
	}
	static public void printElapsedNanoTime(String msg, long startTime) {
		long et = System.nanoTime()-startTime;
		long sec = et /1000000000;
		String t = timerStr2(sec);
		System.out.format("%-20s  %s\n", msg, t);
		
	}
	
	static public String timerStr2(long et) {
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
		return str;
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
	// CAS513 add for Gene/hit popup; CAS531 discontinued for gene/hit as moved to Closeup.TextPopup
	public static JOptionPane displayInfoMonoSpace(Component parentFrame, String title, 
			String theMessage, Dimension d, Annotation aObj) 
	{ 	
		// scrollable selectable message area
		JTextArea messageArea = new JTextArea(theMessage);
		JScrollPane sPane = new JScrollPane(messageArea); 
		messageArea.setFont(new Font("monospaced", Font.BOLD, 12));
		messageArea.setEditable(false);
		messageArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		
		// put scrollable pane in option pane
		JOptionPane optionPane = new JOptionPane(sPane, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION);
		
		// add dialog, which has title
		JDialog helpDiag = optionPane.createDialog(parentFrame, title);
		helpDiag.setModal(false); // true - freeze other windows
		helpDiag.setResizable(true);
		if (helpDiag.getWidth() >= d.width || helpDiag.getHeight() >= d.height) helpDiag.setSize(d);
		helpDiag.setVisible(true);	
		helpDiag.setAlwaysOnTop(true);
		
		helpDiag.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE); // CAS543 add the explicit close 
		helpDiag.addWindowListener(new WindowAdapter() {
			public void windowClosed(WindowEvent e) {
				aObj.setIsPopup(false);
			}
		});
		
		 optionPane.addPropertyChangeListener(
    		new PropertyChangeListener() {
        		public void propertyChange(PropertyChangeEvent e) {
        			//String prop = e.getPropertyName();
        			aObj.setIsPopup(false);
        		}
    		}
    	);
		 
		return optionPane;
	}
	
	public static void showInfoMsg(String title, String msg) {
		JOptionPane.showMessageDialog(null, msg, title, JOptionPane.WARNING_MESSAGE);
	}
	public static void showWarning(String msg) { // CAS564 need one without print to terminal
		JOptionPane.showMessageDialog(null, msg, "Warning", JOptionPane.WARNING_MESSAGE);
	}
	
	public static void showInfoMessage(String title, String msg) {
		System.out.println(msg);
		JOptionPane.showMessageDialog(null, msg, title, JOptionPane.WARNING_MESSAGE);
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
	static public boolean showYesNo (String title, String msg) { // CAS540 add; CAS544 reorder yes/no
		String [] options = {"No", "Yes"};
		int ret = JOptionPane.showOptionDialog(null, 
				msg,
				title, JOptionPane.YES_NO_OPTION, 
				JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
		if (ret == 0) return false;
		return true;
	}
	static public boolean showContinue (String title, String msg) { // CAS543 the cancel was in diff loc than below
		String [] options = {"Cancel", "Continue"};
		int ret = JOptionPane.showOptionDialog(null, 
				msg + "\nContinue?",
				title, JOptionPane.YES_NO_OPTION, 
				JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
		if (ret == 0) return false;
		return true;
	}
	
	// Cancel is false
	public static boolean showConfirm2(String title, String msg) {
		String [] options = {"Cancel", "Confirm"};
		int ret = JOptionPane.showOptionDialog(null, 
				msg, 
				title, JOptionPane.YES_NO_OPTION, 
				JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
		if (ret == 0) return false;
		else return true;
	}
	
	// three possible actions; used to (1) remove from database (2) and disk
	public static int showConfirm3(String title, String msg) {
		String [] options = {"Cancel", "Only", "All"};
		return JOptionPane.showOptionDialog(null, 
				msg, title, JOptionPane.YES_NO_OPTION, 
				JOptionPane.QUESTION_MESSAGE, null, options, options[1]); // CAS565 make default 'Only'
	}
	public static int showConfirmFile(String filename) {
		String [] options = {"Cancel", "Overwrite", "Append"};
		String title = "File exists";
		String msg = "File '" + filename + "' exists.\nDo you want to overwrite it or append to it?";
		return JOptionPane.showOptionDialog(null, 
				msg, title, JOptionPane.YES_NO_OPTION, 
				JOptionPane.QUESTION_MESSAGE, null, options, options[2]);
	}
	public static void showOutOfMemoryMessage(Component parent) {
		System.err.println("Not enough memory.");
		JOptionPane optionPane = new JOptionPane("Not enough memory - increase 'mem' in symap script.", JOptionPane.ERROR_MESSAGE);
		
		LinkLabel label = new LinkLabel("Click to open the Troubleshooting Guide.", Jhtml.TROUBLE_GUIDE_URL); // CAS510
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
	
	/*****************************************************************************/
	// CAS541 add for percents
	public static String pStr(int top, int bottom) {
		if (bottom==0) return "  N/A";
		if (top==0) return    "   0%";
		double x = (double) top/ (double) bottom;
		return String.format("%.2f%s", x, "%");
	}
	// these 3 are also in SeqData; CAS540 add , for dbp
	public static String coordsStr(int start, int end) {
		return String.format("%,d - %,d %,dbp", start, end, (end-start+1)) ;
	}
	public static String coordsStr(char o, int start, int end) {
		return String.format("%c(%,d - %,d) %,dbp", o, start, end, (end-start+1)) ;
	}
	public static String coordsStr(boolean isStrandPos, int start, int end) {
		String o = (isStrandPos) ? "+" : "-";
		return String.format("%s(%,d - %,d) %,dbp", o, start, end, (end-start+1)) ;
	}
	
    // CAS508 - for writing to log
    static public String kMText(long len) {
		double d = (double) len;
		String x = len+"";
		if (len>=1000000000) {
			d = d/1000000000.0;
			x = String.format("%.2fB", d);
		}
		else if (len>=1000000) {
			d = d/1000000.0;
			x = String.format("%.2fM", d);
		}
		else if (len>=1000)  {
			d = d/1000.0;
			x = String.format("%.1fk", d);
		}
		return x;
	}
    static public String mText(long len) {//CAS563 add for symapQuery
		double d = (double) len;
		String x = len+"";
		if (len>=1000000000) {
			d = d/1000000000.0;
			x = String.format("%.1fB", d);
		}
		else if (len>=1000000) {
			d = d/1000000.0;
			x = String.format("%.21M", d);
		}
		else x = String.format("%,d", len);
		return x;
	}
    static public String kMText(int len) {// CAS558 added for xToSymap summary; 
		double d = (double) len;
		String x = len+"";
		if (len>=1000000000) {
			d = d/1000000000.0;
			x = String.format("%dB", (int) d);
		}
		else if (len>=1000000) {
			d = d/1000000.0;
			x = String.format("%dM", (int) d);
		}
		else if (len>=1000)  {
			d = d/1000.0;
			x = String.format("%dk", (int) d);
		}
		return x; // <1000
	}
    static public String kText(int len) { // CAS513 change to use NumberFormat; CAS560 change back for AnchorMain2
    	if (len>=10000) {
    		double d = Math.round(((double) len)/1000.0);
    		return String.format("%dk", (int) d);
    	}
    	else {
    		return String.format("%,4d", len); // <10000
    	}
    }
   
    // CAS513 - to remove leading zeros before making full block name
    static public String blockStr(String c1, String c2, int block) {
    	String x1 = (c1.startsWith("0") && c1.length()>1) ? c1.substring(1) : c1;
    	String x2 = (c2.startsWith("0") && c2.length()>1) ? c2.substring(1) : c2;
    	return x1 + "." + x2 + "." + block;
    }
    /*****************************************************
     * The following are to parse the gene tag (I made a mess of this)
     * v544 DB: 992.a (1 746)
     * v542 DB: Gene #992a (9 1,306bp)
     * Exon tag created in Annotation class
     */
    public static String convertTag(String tag) {
    	if (!tag.startsWith("Gene")) return tag;
    	
    	String dbtag = tag.replace("bp","");
    	Pattern pat1 = Pattern.compile("Gene #(\\d+)([a-z]+[0-9]*)(.*)$");
		Matcher m = pat1.matcher(dbtag); // pre-CAS543 Gene #2 (9 1,306bp) or Gene #2b (9 1,306bp)
		if (m.matches()) {
			String d = m.group(1);
			String s = m.group(2);
			String p = m.group(3);
			return d + "." + s + " " + p;
		}
		Pattern pat2 = Pattern.compile("Gene #(\\d+)(.*)$");
		m = pat2.matcher(dbtag);
		if (m.matches()) {
			String d = m.group(1);
			String p = m.group(2);
			return d + "." + " " + p;
		}
		return dbtag;
    }
    // Annotation class; create fullTag for hover
    static public String createFullTagFromDBtag(String tag) {
    	String [] tok = tag.split("\\(");
    	
    	if (tok.length!=2) { // this should not happen but maybe very old db
    		if (tag.startsWith("Gene")) return tag;
    		else return Globals.geneTag + tag;
    	}
 
    	tok[1] = "(#Exon=" + tok[1];
    	if (!tag.startsWith("Gene")) { 
    		tok[0] = Globals.geneTag + tok[0];
    		tok[1] = tok[1].replace(")","bp)");
    	}
		return tok[0] + " " + tok[1];
    }
    // Annotation.popupDesc; e.g  1563.w (15 2164)
    // return tok[0]=Gene #1563.w and tok[1]=#Exons=15 2,164bp
    static public String [] getGeneExonFromTag(String tag) {
    	String [] ret = new String [2];
    	String [] tok = tag.split("\\(");
    	
    	if (tok.length!=2) {
    		System.out.println("SyMAP Error parsing tag: " + tag);
    		ret[0] = Globals.geneTag +  tag;
			ret[1] = Globals.exonTag +  " 0 0";
			return ret;
    	}
    
    	ret[0] = tok[0];
    	tok[1] = tok[1].replace(")","");
    	ret[1] = exonNum() + tok[1].replace(")",""); // CAS548 changed format - was Exon #
    	
    	if (!tag.startsWith("Gene")) { // v543 992.a (1 746)  old version started with Gene
			ret[0] = Globals.geneTag +  tok[0].trim();
			ret[1] = ret[1] + "bp";
		}
    	return ret;
    }
    // exonTag = "Exon #"; returns #Exons=
    public static String exonNum() { // CAS548 removes "#"; CAS560 moved from Globals
    	return "#" + Globals.exonTag.substring(0, Globals.exonTag.indexOf(" ")) + "s=";
    }
    static public boolean isEndsWithAlpha(String tag) {
    	String t = getGenenumFromDBtag(tag);
    	return (t.contains(".") && !t.endsWith("."));
    }
    // Created in backend.AnchorsPost.Gene; return "d.[a]"
    static public String getGenenumFromDBtag(String tag) {
		if (!tag.startsWith("Gene")) { // CAS544 '2 (9 1306)' or '2.b (9 1306)'
			String [] tok = tag.split(" \\(");
			if (tok.length==2) return tok[0].trim();
			else return tag; // shouldn't happen
		}
		
		Pattern pat1 = Pattern.compile("Gene #(\\d+)([a-z]+[0-9]*)(.*)$");
		Matcher m = pat1.matcher(tag); // pre-CAS544 Gene #2 (9 1,306bp) or Gene #2b (9 1,306bp)
		if (m.matches()) {
			String y = m.group(1);
			String z = m.group(2);
			return y + "." + z;
		}
		
		Pattern pat2 = Pattern.compile("Gene #(\\d+)(.*)$");
		m = pat2.matcher(tag);
		if (m.matches()) {
			String y = m.group(1);
			return y + ".";
		}
		else return tag;
    }
    // symapQuery.DBdata.finish CAS518 return genenum.suffix or genenum
    public static String makeChrGenenumFromDBtag(String c1, String tag) { 	
    	String chr = (c1.startsWith("0") && c1.length()>1) ? c1.substring(1) : c1;
    	
    	if (tag==null || tag=="") return chr +".?."; // older databases do not have tag
    	
    	String gn = getGenenumFromDBtag(tag);
    	return chr + "." + gn;
    }
    
    // symapQuery.DBdata.passFilters; return number only
    static public String getGenenumIntOnly(String tag) {
    	if (tag=="-") return "";
    	
    	String gn = tag.contains("(") ? getGenenumFromDBtag(tag) : tag; // CAS561 the () has already been removed
    	
    	String [] tok = gn.split("\\.");
    	if (tok.length>0) return tok[0]; 
    	else return gn; 						// CAS544 bugfix was returning full tag
    }
    static public int getGenenumInt(String tag) { // CAS560 add for DBdata overlap only
    	if (tag=="-") return -1;
    	String gn = getGenenumIntOnly(tag);
    	return getInt(gn);
    }
    // For Annotation
    static public int getGenenumSuffixInt(String gn) {
    	if (gn=="-") return -1;
    	if (!gn.contains(".")) return -1;
    	if (gn.endsWith(".")) return -1;
 
    	String [] tok = gn.split("\\.");
    	if (tok.length>0) {
    		char [] x = tok[1].toCharArray(); 
    		int i = (int) x[0];
    		if (i>=97 || i<=122) return  i-97;
    	}
    	return 0; 
    }
    
    // symap.QueryPanel
    public static boolean isValidGenenum(String gn) {
		String n=gn, s=null;
		if (gn.contains(".")) {
			String [] tok = gn.split("\\.");
			if (tok.length==0 || tok.length>2) return false;
			n = tok[0];
			if (tok.length==2) s=tok[1];
		} 
		try {
			Integer.parseInt(n);
		}
		catch (Exception e) { return false;}
		if (s!=null) {
			try {
				Integer.parseInt(s);
				return false;
			}
			catch (Exception e) { return true;}
		}
		return true;
	}
    
    // parse tag for collinear: CAS520 e.g. g2 c10.5 created in MapperPool.setPseudoPseudoData
    static public int getCollinear(String tag) {
    	try {
    		String set = "";
    		Pattern pat1 = Pattern.compile("g\\d+ c\\d+\\.(\\d+)");	
    		Matcher m = pat1.matcher(tag);
    		if (m.matches()) set = m.group(1);
    		
    		if (set=="") {
	    		Pattern pat2 = Pattern.compile("g\\d+(b[1-9].*)"); // pre-520 e.g. g2b5 (g2b0 is false)
	    		m = pat2.matcher(tag);
	    		if (m.matches()) set = m.group(1);
    		}
    		try {
    			int d = Integer.parseInt(set);
    			return d;
    		}
    		catch (Exception e) {}
    	}
    	catch (Exception e) {ErrorReport.print(e, "Parsing tag " + tag); }
    	return 0;
    }
    /*******************************************************
	 * XXX Table maker CAS517 copied from TCW
	 */
	public static String makeTable(
			int nCol, int nRow, String[] fields, int [] justify, String [][] rows) 
	{	
		Vector <String> lines = new Vector <String>();
		makeTable(lines, nCol, nRow, fields, justify, rows);
		String x="";
		for (String l : lines) x += l + "\n";
		return x;
	}
	public static void makeTable(
			Vector <String> lines, int nCol, int nRow, String[] fields, int [] justify, String [][] rows)
	{
		int c, r;
		String line;
		String space = "  ";
		
		// compute column lengths
		int []collen = new int [nCol];
		for (c=0; c < nCol; c++) collen[c] = 0;
		
        for (c=0; c< nCol; c++) { // longest value
            for (r=0; r<nRow && rows[r][c] != null; r++) {
        		if (rows[r][c] == null) rows[r][c] = "";
        		if (rows[r][c].length() > collen[c]) 
        			collen[c] = rows[r][c].length();
            }
        }
        if (fields != null) {    // heading longer than any value?
			for (c=0; c < nCol; c++) {
				if (collen[c] > 0) {
					if (fields[c] == null) fields[c] = "";
					if (fields[c].length() > collen[c]) 
						collen[c]=fields[c].length();
				}
			}
	        // output headings
	        line = space; // length of space in front
	        for (c=0; c< nCol; c++) 
	        		if (collen[c] > 0) 
	        			line += pad(fields[c],collen[c], 1) + space;
	        lines.add(line);
        }
        // output rows
        for (r=0; r<nRow; r++) {
        	line = space;
            for (c=0; c<nCol; c++) {
                 if (collen[c] > 0) 
                	 	line += pad(rows[r][c],collen[c],justify[c]) + space;
                 rows[r][c] = ""; // so wouldn't reuse in next table
            }
            lines.add(line);
        }
	}
	
    private static String pad(String s, int width, int o){
		if (s == null) return " ";
        if (s.length() > width) {
            String t = s.substring(0, width-1);
            System.out.println("'" + s + "' truncated to '" + t + "'");
            s = t;
            s += " ";
        }
        else if (o == 0) { // left
            String t="";
            width -= s.length();
            while (width-- > 0) t += " ";
            s = t + s;
        }
        else {
            width -= s.length();
            while (width-- > 0) s += " ";
        }
        return s;
    } 
}
