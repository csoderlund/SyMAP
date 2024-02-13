package symap;

import java.awt.Cursor;
import java.io.File;

/******************************************
 * CAS534 added in order to move globals from SyMAP2d, SyMAPConstants and Manager
 * See util:Jhtml.java for more constants for Help pages
 * See SyMAPmanager:printVerions() for banner including java version
 * See DBconn2 for checking database variables
 */
public class Globals {
	public static final String 	VERSION = "v5.4.8"; 
	public static final String 	DATE = " (13-Feb-24)";
	public static final String  VERDATE = VERSION + " " + DATE;
	public static final int 	DBVER =  7; 	// CAS512 v3, CAS520 v4, CAS522 v5, CAS543 v6, CAS546 v7
	public static final String  DBVERSTR = "db" + DBVER;
	
	public static String  MAIN_PARAMS =   "symap.config"; 	// default; changed on command line -c
	
	public static final  String PERSISTENT_PROPS_FILE = ".symap_saved_props"; // under user's directory; see props.PersistenProps

	public static boolean TRACE=false; 		// set in SyMAPmanager on -tt; 			use to add info; write anchor2 files
	public static boolean DEBUG=false; 		// CAS519 set in SyMAPmanager on -dd; 	use for possible error
	public static boolean DBDEBUG=false;	// CAS535 set in SyMAPmanger on -dbd	adds fields to DB
	public static boolean GENEN_ONLY=false; // -z CAS519b to update the gene# without having to redo synteny
	public static boolean HITCNT_ONLY=false;// -y CAS541 to update the hitcnt without having to redo synteny
	public static boolean bTrim=true;		// CAS531 do not trim 2D alignments; see closeup.AlignPool
	
	public static final int MAX_CLOSEUP_BP=30000;	// Used in CloseUp and Query
	public static final String MAX_CLOSEUP_K = "30kb";
	public static final String exonTag = "Exon #";
	public static final String geneTag = "Gene #";
	public static final int MAX_RANGE=50000; 		// used in SeqFilter for drawing yellow boxes
	
	public static final String minorAnno = "*"; // CAS548 used in Query and Anno popup for hit to non-assigned anno
	
	// CAS534 start moving constants from SyMAPConstants
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
  
    public static String getExport() { // CAS547 add for 3 uses
    	String saveDir = System.getProperty("user.dir") + "/exports/";
		File temp = new File(saveDir);
		if(!temp.exists()) {
			System.out.println("Create " + saveDir);
			temp.mkdir();
		}
		return saveDir;
    }
    public static String exonNum() { // CAS548 removes "#"
    	return "#" + Globals.exonTag.substring(0, Globals.exonTag.indexOf(" ")) + "s=";
    }
    public static void prt(String msg)  {System.out.println(msg);}
    public static void tprt(String msg) {if (TRACE) System.out.println(msg);}
    public static void dprt(String msg) {if (DEBUG) System.out.println(msg);}
}
