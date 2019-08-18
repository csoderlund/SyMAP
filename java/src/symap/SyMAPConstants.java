package symap;

import java.awt.Cursor;

public interface SyMAPConstants {
     public static final int NO_VALUE = Integer.MIN_VALUE;

    public static final int LEFT_ORIENT   = -1;
    public static final int CENTER_ORIENT = 0;
    public static final int RIGHT_ORIENT  = 1;

    // low 4 bits
    public static final byte NORF_VALUE = (byte)0x00;
    public static final byte R_VALUE    = (byte)0x01;
    public static final byte F_VALUE    = (byte)0x02;

    // high 4 bits
    public static final byte NO_HITS       = (byte)0x00;
    public static final byte IN_BLOCK      = (byte)0x10;
    public static final byte LEAST_ONE_HIT = (byte)0x20;
    public static final byte REPETITIVE  = (byte)0x40;

    public static final String NORF_VALUE_STR = "";
    public static final String R_VALUE_STR = "r";
    public static final String F_VALUE_STR = "f";

    // Cursor Types
    public static final Cursor DEFAULT_CURSOR   = Cursor.getDefaultCursor();
    public static final Cursor WAIT_CURSOR      = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR);
    public static final Cursor HAND_CURSOR      = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
    public static final Cursor S_RESIZE_CURSOR  = Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR);
    public static final Cursor MOVE_CURSOR      = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
    public static final Cursor CROSSHAIR_CURSOR = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);

    // Hit Types
    public static final String MRK_TYPE = "mrk";
    public static final String BES_TYPE = "bes";
    public static final String FINGERPRINT_TYPE = "clone";
    public static final String SEQ_TYPE = "seq";
    
	// mdb added 1/29/10 - all possible concurrent connections
	public static final String DB_CONNECTION_BACKEND = "Backend";
	public static final String DB_CONNECTION_PROJMAN = "Project Manager";
	public static final String DB_CONNECTION_SYMAP_APPLET = "SyMAP Applet";
	public static final String DB_CONNECTION_SYMAP_APPLET_3D = "SyMAP Applet 3D";
	public static final String DB_CONNECTION_DOTPLOT = "Dotplot";
	public static final String DB_CONNECTION_DOTPLOT_2D = "Dotplot 2D";
}
