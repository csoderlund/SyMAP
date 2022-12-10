package symap;

import java.awt.Cursor;

public interface SyMAPConstants {
	// most constants are in symap.SyMAP
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
	public static final String DB_CONNECTION_BACKEND = "Backend";
	public static final String DB_CONNECTION_PROJMAN = "Project Manager";
	public static final String DB_CONNECTION_SYMAP_3D = "SyMAP 3D"; 
	public static final String DB_CONNECTION_DOTPLOT = "Dotplot";
	public static final String DB_CONNECTION_DOTPLOT_2D = "Dotplot 2D";
	
	/*** old FPC stuff **/
	public static final String NORF_VALUE_STR = "";
	public static final String R_VALUE_STR = "r";
	public static final String F_VALUE_STR = "f";
	 // low 4 bits
	public static final byte NORF_VALUE = (byte)0x00;
	public static final byte R_VALUE    = (byte)0x01;
	public static final byte F_VALUE    = (byte)0x02;
	 // high 4 bits
    public static final byte NO_HITS       = (byte)0x00;
    public static final byte IN_BLOCK      = (byte)0x10;
    public static final byte LEAST_ONE_HIT = (byte)0x20;
 
    public static final String SEQ_TYPE = "seq";
}
