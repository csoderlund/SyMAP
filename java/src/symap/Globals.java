package symap;

import java.awt.Cursor;

/******************************************
 * CAS534 added in order to move globals from SyMAP2d, SyMAPConstants and Manager
 */
public class Globals {
	public static final String 	VERSION = "v5.4.1"; 
	public static final String 	DATE = " (22-June-23)";
	public static final String  VERDATE = VERSION + " " + DATE;
	public static final int 	DBVER =  5; 	 	// CAS512 v3, CAS520 v4, CAS522 v5
	public static final String  DBVERSTR = "db" + DBVER;
	
	public static String  MAIN_PARAMS =   "symap.config"; 	// default; changed on command line -c
	
	public static final  String PERSISTENT_PROPS_FILE = ".symap_saved_props"; // under user's directory; see props.PersistenProps

	public static boolean TRACE=false; 		// set in SyMAPmanager on -tt; 			use to add info
	public static boolean DEBUG=false; 		// CAS519 set in SyMAPmanager on -dd; 	use for possible error
	public static boolean DBDEBUG=false;	// CAS535 set in SyMAPmanger on -dbd	adds fields to DB
	public static boolean GENEN_ONLY=false; // -z CAS519b to update the gene# without having to redo synteny
	public static boolean HITCNT_ONLY=false;// -y CAS541 to update the hitcnt without having to redo synteny
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
}