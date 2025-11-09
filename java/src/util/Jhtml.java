package util;

import java.awt.Color;
import java.awt.Container;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JScrollPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

/*********************************************************
 */
public class Jhtml {
	public static final String BASE_HELP_URL_prev51 = "http://www.agcol.arizona.edu/software/symap/doc/"; // not used
	public static final String BASE_HELP_URL = 	"https://csoderlund.github.io/SyMAP/"; 
	public static final String TROUBLE_GUIDE_URL = BASE_HELP_URL + "TroubleShoot.html";
	public static final String macVerify = "#macos"; 									
	
	public static final String SYS_GUIDE_URL =    BASE_HELP_URL + "SystemGuide.html"; 
	public static final String create = "#create";
	public static final String ext = "#ext";										
	
	public static final String SYS_HELP_URL =    BASE_HELP_URL + "SystemHelp.html"; 
	public static final String build = 	"#build";
	public static final String param1 = "#projParams";
	public static final String param2Align = "#align2";
	public static final String param2Clust = "#clust";
	public static final String param2Syn = "#syn";
	
	public static final String USER_GUIDE_URL =  BASE_HELP_URL + "UserGuide.html"; 
	public static final String view =  		"#views";	
	public static final String circle =  	"#circle";				
	public static final String dotplot = 	"#dotplot_display";
	public static final String align2d = 	"#alignment_display_2d";
	public static final String colorIcon =  "#wheel";
	public static final String control = 	"#control";
	public static final String hitfilter =  "#hitfilter";
	public static final String seqfilter =  "#sequence_filter";
	public static final String dotfilter =	"#dotplot_filter";
	
	public static final String QUERY_GUIDE_URL =  BASE_HELP_URL + "Query.html";
	public static final String query = 	"#query";
	public static final String result = "#result";
	
	public static final String CONVERT_GUIDE_URL =  BASE_HELP_URL + "input/index.html"; 
	
	public static JButton createHelpIconSysSm(String main, String id) { 
		return createHelpIcon("/images/helpSm.png", main + id);
	}
	
	public static JButton createHelpIconUserLg(String id) {
		return createHelpIcon("/images/help.gif", USER_GUIDE_URL + id);
	}
	public static JButton createHelpIconUserSm(String id) {
		return createHelpIcon("/images/helpSm.png", USER_GUIDE_URL + id);
	}
	public static JButton createHelpIconQuery(String id) {
		return createHelpIcon("/images/helpSm.png", QUERY_GUIDE_URL + id);
	}
	private static JButton createHelpIcon(String img, String url) { 
		Icon icon = ImageViewer.getImageIcon(img);
		JButton button = new JButton(icon);

		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (!tryOpenURL(url) )
					System.err.println("Error opening URL: " + url);
			}
		});
		button.setToolTipText("Help: Online documentation.");
		return button;
	}
	public static void onlineHelp(String id) { // Used from 2D control drop-down
		String url = USER_GUIDE_URL + id;
		if (!tryOpenURL(url) )
			System.err.println("Error opening URL: " + url);
	}
	
	 public static boolean tryOpenURL (String theLink ) {
	   	if (theLink == null) return false;
	   	
	   	try {
	   		new URI(theLink); 
	   	}
		catch (URISyntaxException e) {
			ErrorReport.print(e, "URI Syntax: " + theLink);
	   		return false;
	   	}
	   	if (isMac()) 	return tryOpenMac(theLink);
	   	else 			return tryOpenLinux(theLink);	
	}
	
	private static boolean tryOpenMac(String theLink) { 
		Desktop desktop = java.awt.Desktop.getDesktop();
	   	URI oURL;
		try {
	   		oURL = new URI(theLink);
	   	}
		catch (URISyntaxException e) {
	   		ErrorReport.print(e, "URI Syntax: " + theLink);
	   		return false;
	   	}
		try {
			desktop.browse(oURL);
			return true;
		} catch (IOException e) {
			ErrorReport.print(e, "URL desktop error on Mac: " + theLink);
		}
		return false;
	}
	
	public static boolean tryOpenLinux (String theLink) { 
		// Copied this from: http://www.centerkey.com/java/browser/  
	   	try { 
	   		if (isWindows()) {
	   			String [] x = {"rundll32 url.dll,FileProtocolHandler", theLink}; 
	   			Runtime.getRuntime().exec(x); // no idea if this works on Windows
	   			return true;
	   		}
	   		else { 
	   			String [] browsers = {"firefox", "opera", "konqueror", "epiphany", "mozilla", "netscape",
	   					   "google-chrome", "conkeror", "midori", "kazehakase", "x-www-browser"}; 
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
	   	catch (Exception e) {ErrorReport.print(e, "URL error on Linux: " + theLink);}
		return false;
	}
	/********************************************************************
	 * 1. The following provides popups for java/src/html
	 * 2. The Try methods provide direct links to http URLs 
	 * 3. The ProjectManagerFrameCommon.createInstructionsPanel shows  the main page
	 */
	/** Instance stuff - only class methods **/ 
	private static Class <?> resClass = null; // store this so help pages can be loaded from anywhere
	private static Frame helpParentFrame = null; 
	
	public static void setResClass(Class <?> c)
	{
		resClass = c;
	}
	public static void setHelpParentFrame(Frame f)
	{
		helpParentFrame = f;
	}
	public static void showHTMLPage(JDialog parent, String title, String resource) {
		if (resClass == null) {
			System.err.println("Help can't be shown.\nDid you call setResClass?");
			return;
		}
		JDialog dlgRoot = (parent == null ? new JDialog(helpParentFrame,title,false)
										: new JDialog(parent,title,false));
		dlgRoot.setPreferredSize(new Dimension(800,800));
		Container dlg = dlgRoot.getContentPane();
		dlg.setLayout(new BoxLayout(dlg,BoxLayout.Y_AXIS));
		
		StringBuffer sb = new StringBuffer();
		try {
			InputStream str = resClass.getResourceAsStream(resource);
			
			int ci = str.read();
			while (ci != -1) {
				sb.append((char)ci);
				ci = str.read();
			}
		}
		catch(Exception e){ErrorReport.print(e, "Show HTML page");}
		
		String html = sb.toString();
		
		JEditorPane jep = new JEditorPane();
		jep.setContentType("text/html");
		jep.setEditable(false);
	   
	    jep.addHyperlinkListener(new HyperlinkListener() {
			public void hyperlinkUpdate(HyperlinkEvent e) {
				if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
					if (!tryOpenURL(e.getURL().toString()) ) 
						System.err.println("Error opening URL: " + e.getURL().toString());
				}
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
	// ManagerFrame initial view
	public static JComponent createInstructionsPanel(InputStream str, Color background) {
		StringBuffer sb = new StringBuffer();
		try {
			//InputStream str = this.getClass().getResourceAsStream(HTML);
			
			int ci = str.read();
			while (ci != -1) {
				sb.append((char)ci);
				ci = str.read();
			}
		}
		catch(Exception e){ErrorReport.print(e, "Show instructions");}
		
		JEditorPane editorPane = new JEditorPane();
		editorPane.setEditable(false);
		editorPane.setBackground(background);
		editorPane.setContentType("text/html");
		editorPane.setText(sb.toString());

		JScrollPane scrollPane = new JScrollPane(editorPane);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
		scrollPane.setBackground(background);
		
		editorPane.addHyperlinkListener(new HyperlinkListener() {
			public void hyperlinkUpdate(HyperlinkEvent e) {
				if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
					if ( !Jhtml.tryOpenURL(e.getURL().toString()) ) 
						System.err.println("Error opening URL: " + e.getURL().toString());
				}
			}
		});
		
		return scrollPane;
	}
	// TableReport; for Query report
	public static void showHtmlPanel(JDialog parent, String title, String html) {
		JDialog dlgRoot = new JDialog(parent,title,false); // works if sent null, and does not bounce other frames around
		dlgRoot.setPreferredSize(new Dimension(800,800));
		Container dlg = dlgRoot.getContentPane();
		dlg.setLayout(new BoxLayout(dlg,BoxLayout.Y_AXIS));
	
		JEditorPane jep = new JEditorPane();
		jep.setContentType("text/html");
		jep.setEditable(false);
	   
	    jep.addHyperlinkListener(new HyperlinkListener() {
			public void hyperlinkUpdate(HyperlinkEvent e) {
				if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
					if (!tryOpenURL(e.getURL().toString()) ) 
						System.err.println("Error opening URL: " + e.getURL().toString());
				}
			}
		});
		jep.setText(html);
		jep.setVisible(true);
		jep.setCaretPosition(0);
		JScrollPane jsp = new JScrollPane(jep);
		dlg.add(jsp);
	
		dlgRoot.pack();
		dlgRoot.setVisible(true);
	}
	// for TableReport
	public static String wrapLine(String line, int lineLength, String lineBreak) {
	    if (line.length() == 0) return "";
	    if (line.length() <= lineLength) return line;
	    
	    String[] words = line.split(" ");
	    StringBuilder allLines = new StringBuilder();
	    StringBuilder trimmedLine = new StringBuilder();
	    
	    for (String word : words) {
	        if (trimmedLine.length()+1+word.length() <= lineLength) {
	            trimmedLine.append(word).append(" ");
	        } else {
	            if (trimmedLine.length()>0) allLines.append(trimmedLine).append(lineBreak);
	            trimmedLine = new StringBuilder();
	            if (word.length()>lineLength) word = word.substring(0, lineLength-2) + "...";
	            trimmedLine.append(word).append(" ");
	        }
	    }
	    if (trimmedLine.length() > 0) allLines.append(trimmedLine);
	    return allLines.toString().trim();
	}
}
