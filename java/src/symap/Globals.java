package symap;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.io.File;

/******************************************
 * Global constants and prt routines
 * See util:Jhtml.java for more constants for Help pages
 * See SyMAPmanager:printVerions() for banner including java version
 * See DBconn2 for checking database variables
 */
public class Globals {
	public static final String 	VERSION = "v5.7.1"; 
	public static final String 	DATE = " (17-Aug-25)";
	public static final String  VERDATE = VERSION + " " + DATE;
	public static final int 	DBVER = 7; 	// CAS512 v3, CAS520 v4, CAS522 v5, CAS543 v6, CAS546 v7
	public static final String  DBVERSTR = "db" + DBVER;
	public static final String  JARVERION = "17.0.11"; // verify with 'jar xvf symap.jar META-INF', view META_INF
	
	public static String  MAIN_PARAMS =   "symap.config"; 	// default; changed on command line -c
	
	public static final  String PERSISTENT_PROPS_FILE = ".symap_saved_props"; // under user's directory; see props.PersistenProps
	
	// Set in SyMAPmanager 
	public static boolean INFO=false;		// -ii; popup indices, query overlap; -ii -tt DB output, Hit Sort Popup; CAS560 add; 
	public static boolean TRACE=false; 		// -tt; write files for query and anchor2
	public static boolean DEBUG=false; 		// -dd; use for possible error
	public static boolean DBDEBUG=false;	// -dbd; adds fields to DB

	public static boolean bMySQL=false;		  // -sql; check MySQL 
	public static boolean bTrim=true;		  // -a do not trim 2D alignments; see closeup.AlignPool
	public static boolean bRedoSum=false;	  // -s redo summary 
	public static boolean bQueryOlap=false;   // -q show gene olap for algo2 instead of exon
	public static boolean bQueryPgeneF=false; // -g run old PgeneF algo instead of new Cluster; CAS563 

	public static final String exonTag = "Exon #";	 // type exon
	public static final String geneTag = "Gene #";   // type gene
	
	// These are the pseudo_annot.type values
	public static final String geneType = "gene";	  // this is hard-coded many places; search 'gene'
	public static final String exonType = "exon";
	public static final String pseudoType = "pseudo"; // pseudo type
	public static final String gapType = "gap";
	public static final String centType = "centromere";
	
	public static final String pseudoChar = "~";	 // pseudo tag;
	public static final String minorAnno = "*";      // used in Query and Anno popup for hit to non-assigned anno
	public static final String DOT = ".", SDOT="\\.";// separates block, collinear, gene#; second is for split
	
	public static final int MAX_CLOSEUP_BP=50000;	// Used in CloseUp and Query; 
	public static final String MAX_CLOSEUP_K = "50kb";
	public static final int MAX_2D_DISPLAY=30000;	// Query and Sfilter; 
	public static final String MAX_2D_DISPLAY_K = "30kb";
	public static final int MAX_YELLOW_BOX=50000; 	// used in SeqFilter for drawing yellow boxes
	
	public static final int T=0, Q=1;				// for target (2) and query (1); 
	
	public static final int NO_VALUE = Integer.MIN_VALUE;

    public static final int LEFT_ORIENT   = -1;
    public static final int CENTER_ORIENT = 0;
    public static final int RIGHT_ORIENT  = 1;
  
    // Cursor Types
    public static final Cursor DEFAULT_CURSOR   = Cursor.getDefaultCursor();
    public static final Cursor WAIT_CURSOR      = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR);
    public static final Cursor HAND_CURSOR      = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
    public static final Cursor S_RESIZE_CURSOR  = Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR);
    public static final Cursor MOVE_CURSOR      = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
    public static final Cursor CROSSHAIR_CURSOR = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
  
    public static final Font textFont = new Font(Font.MONOSPACED,Font.PLAIN,12);
    public static final int textHeight = 12;
    public static final Color white = Color.white;
    
    // Exports
    public static String alignDir = "queryMSA";	
    public static String getExport() { 			
    	String saveDir = System.getProperty("user.dir") + "/exports/";
		File temp = new File(saveDir);
		if(!temp.exists()) {
			System.out.println("Create " + saveDir);
			temp.mkdir();
		}
		return saveDir;
    }
   
    // positive is the gap between (s1,e1) and (s2, e2); negative is the overlap
 	// works for contained; copied from anchor2.Arg.java 
    public static int pGap_nOlap(int s1, int e1, int s2, int e2) {
		return -(Math.min(e1,e2) - Math.max(s1,s2) + 1); 
	}
    
    // outputs
    public static String bToStr(boolean b) {return (b) ? "T" : "F";}
    public static void prt(String msg)  {System.out.println(msg);} // permanent message
    public static void prtStdErr(String msg)  {System.err.println(msg);} // permanent message; 
    public static void xprt(String msg) {System.out.println(msg);} // temp message
    public static void rprt(String msg) {System.err.print("      " + msg + "...                   \r");}// to stderr
    public static void rclear() {
    	System.err.print("                                                                          \r");} 
    public static void eprt(String msg) {System.err.println("***Error: " + msg);}
    public static void die(String msg)  {eprt(msg); System.exit(-1);}
    
    public static void dtprt(String msg)         {if (TRACE || DEBUG) System.out.println(msg);}
    public static void tprt(String msg)          {if (TRACE) System.out.println(msg);}
    public static void tprt(int num, String msg) {if (TRACE) System.out.format("%,10d %s\n",num, msg);}
    public static void dprt(String msg)          {if (DEBUG) System.out.println(msg);}
    public static void deprt(String msg)         {if (DEBUG) System.err.println(msg);} 
    public static String toTF(boolean b) {return (b) ? "T" : "F";}
}
