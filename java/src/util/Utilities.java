package util;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.awt.GraphicsConfiguration;
import java.awt.Rectangle;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Container;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

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
import java.text.NumberFormat;
import java.text.SimpleDateFormat;

import backend.Constants;
import symap.frame.HelpBar;
import symap.frame.HelpListener;

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
	private static boolean FULL_INT = true;
	private static boolean TRACE = Constants.TRACE;
	public static final Color HELP_PROMPT = new Color(0xEEFFEE); // CAS504 moved from dead file - for Help
	

	private Utilities() { }

	/************************************************************
	 * XXX Random interface 
	 */
	// someday i will break this into createImageButton, createButton and createCheckBox; takes more lines to overload...
	public static AbstractButton createButton(HelpListener parent, String path, String tip, HelpBar bar, 
			 ActionListener listener, boolean isCheckbox, boolean bSelected) {
		AbstractButton button;
		
		Icon icon = ImageViewer.getImageIcon(path); 
		if (icon != null) {
		    if (isCheckbox)
		    	button = new JCheckBox(icon);
		    else
		    	button = new JButton(icon);
		    	button.setMargin(new Insets(0,0,0,0));
		}
		else {
		    if (isCheckbox)
		    	button = new JCheckBox(path);
		    else
		    	button = new JButton(path);
		    	button.setMargin(new Insets(1,3,1,3));
		}
		if (isCheckbox) button.setSelected(bSelected); // CAS521 add
		
		if (listener != null) 
		    button.addActionListener(listener);

		button.setToolTipText(tip);
		
		button.setName(tip); 
		if (bar != null) bar.addHelpListener(button,parent);
		
		return button;
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
	/*******************************************************************
	 * XXX basic array and string ops
	 */
	 public static String pad(String s, int width)
	 {
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
	public static int getInt(String i) { // CAS532 added
		try {
			int x = Integer.parseInt(i);
			return x;
		}
		catch (Exception e) {return 0;}
	}

	public static int[] getIntArray(Collection<Integer> ints) throws ClassCastException {
		int[] ret = new int[ints.size()];
		Iterator<Integer> iter = ints.iterator();
		for (int i = 0; i < ret.length; ++i)
			ret[i] = iter.next().intValue();
		return ret;
	}

	public static boolean isStringEmpty(String s) {
		return (s == null || s.length() == 0);
	}
	
	/***************************************************
	 * XXX Geometry
	 */
	
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
    
	public static File checkCreateDir(String dir, boolean bPrt)
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
				if (bPrt) System.out.println("Create directory " + dir); // CAS511
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
	// CAS513 add for Gene/hit popup; CAS531 discontinued for gene/hit as moved to Closeup.TextPopup
	public static JOptionPane displayInfoMonoSpace(Component parentFrame, String title, 
			String theMessage, Dimension d, double x, double y) 
	{ 	
		// scrollable selectable message area
		JTextArea messageArea = new JTextArea(theMessage);
		JScrollPane sPane = new JScrollPane(messageArea); 
		messageArea.setFont(new Font("monospaced", Font.BOLD, 12));
		messageArea.setEditable(false);
		messageArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		
		// put scrollable pane in option pane
		JOptionPane pane = new JOptionPane(sPane, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION);
		
		// add dialog, which has title
		JDialog helpDiag = pane.createDialog(parentFrame, title);
		helpDiag.setModal(false); // true - freeze other windows
		helpDiag.setResizable(true);
		if (helpDiag.getWidth() >= d.width || helpDiag.getHeight() >= d.height) helpDiag.setSize(d);
		helpDiag.setVisible(true);	
		helpDiag.setAlwaysOnTop(true);
		if (x!=0.0 && y!=0.0) helpDiag.setLocation((int) x, (int) y);
		
		return pane;
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
	
    // CAS508 - for writing to log
    static public String kMText(long len) {
		double d = (double) len;
		String x = len+"";
		if (len>=1000000000) {
			d = d/1000000000.0;
			x = String.format("%.1fB", d);
		}
		else if (len>=1000000) {
			d = d/1000000.0;
			x = String.format("%.1fM", d);
		}
		else if (len>=1000)  {
			d = d/1000.0;
			x = String.format("%.1fk", d);
		}
		return x;
	}
  
    static public String kText(int len) { // CAS513 change to use NumberFormat
    	if (FULL_INT) return String.format("%,d", len);
    	if (len>=10000) {
    		NumberFormat nf = NumberFormat.getNumberInstance();
    		nf.setMaximumFractionDigits(2);
    		nf.setMinimumFractionDigits(2);
    		
    		double d = ((double) len)/1000.0;
    		return nf.format(d) + "k";
    	}
    	else {
    		return String.format("%,d", len);
    	}
    }
   
    // CAS513 - to remove leading zeros before making full block name
    static public String blockStr(String c1, String c2, int block) {
    	String x1 = (c1.startsWith("0") && c1.length()>1) ? c1.substring(1) : c1;
    	String x2 = (c2.startsWith("0") && c2.length()>1) ? c2.substring(1) : c2;
    	return x1 + "." + x2 + "." + block;
    }
    
    // CAS518 return genenum.suffix or genenum
    public static String makeChrGenenum(String c1, String tag) { 	
    	String chr = (c1.startsWith("0") && c1.length()>1) ? c1.substring(1) : c1;
    	
    	if (tag==null || tag=="") return chr +".?."; // older databases do not have tag
    	
    	String gn = getGenenumFromTag(tag);
    	return chr + "." + gn;
    }
    static public String getGenenumFromTag(String tag) {
    	Pattern pat1 = Pattern.compile("Gene #(\\d+)([a-z]+[0-9]*)(.*)$");
		Pattern pat2 = Pattern.compile("Gene #(\\d+)(.*)$");
		Matcher m = pat1.matcher(tag);
		if (m.matches()) {
			String y = m.group(1);
			String z = m.group(2);
			return y + "." + z;
		}
		else { 
			m = pat2.matcher(tag);
			if (m.matches()) {
				String y = m.group(1);
				return y + ".";
			}
			else return tag;
		}
    }
  
    static public String formatAbbrev(String dname) {
    	if (dname==null || dname=="") return "????";
    	if (dname.length()>4) return dname.substring(dname.length()-4);
    	else if (dname.length()<4) {
    		String n = dname;
    		while (n.length()<4) n+="0";
    		return n;
    	}
    	else return dname;
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
	
    private static String pad(String s, int width, int o)
    {
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
