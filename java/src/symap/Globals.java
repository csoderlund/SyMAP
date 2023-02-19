package symap;

import java.awt.Cursor;

/******************************************
 * CAS534 added in order to move globals from SyMAP2d, SyMAPConstants and Manager
 */
public class Globals {
	public static final String 	VERSION = "v5.3.4"; // pre-v530 code can not read post-dbs
	public static final String 	DATE = " (19-Feb-23)";
	public static final int 	DBVER =  5; 	 	// CAS512 v3, CAS520 v4, CAS522 v5
	public static final String  DBVERSTR = "db" + DBVER;
	
	public static String  MAIN_PARAMS =   "symap.config"; 	// default; changed on command line -c
	
	public static final  String PERSISTENT_PROPS_FILE = ".symap_saved_props"; // under user's directory; see props.PersistenProps

	public static boolean TRACE=false; 		// set in ProjectManagerFrameCommon on -t
	public static boolean DEBUG=false; 		// CAS519 set in ProjectManagerFrameCommon on -d
	public static boolean GENEN_ONLY=false; // -z CAS519b to update the gene# without having to redo synteny
	public static boolean bTrim=true;		// CAS531 do not trim 2D alignments; see closeup.AlignPool
	
	public static final int MAX_CLOSEUP_BP=30000;
	
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
    
	// All possible concurrent connections
	public static final String DB_CONN_PROJMAN = "Project Manager"; // managerframe; used by everything else
	public static final String DB_CONN_SYMAP = 	 "SyMAP"; 	// explore and query
	public static final String DB_CONN_DOTPLOT = "Dotplot"; // dotplot
}
