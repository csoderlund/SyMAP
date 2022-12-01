package symap;

import java.sql.SQLException;
import java.awt.Component;
import javax.swing.JOptionPane;

import util.*;
import symap.frame.*;
import symap.pool.PoolManager;
import symap.pool.Pools;
import colordialog.*;
import props.*;
import symap.drawingpanel.DrawingPanel;
import history.History;
import history.HistoryControl;
import symap.closeup.CloseUp;
import symapQuery.TableDataPanel;

/**
 * Class SyMAP sets up the Explorer; it is used to acquire and configure the desired objects.
 * common.SyMAPFrameCommon.regenerate2DView
 * dotplot.Data
 * blockview.Block2Frame
 * symapQuery.tableDataPanel.showSynteny
 * 
 * Note: it makes no sense why this is under symap and not under common. Plus why constants are here...
 * 
 * The properties file (java/src/properties/symap.properties) needs to 
 * be properly set along with all of the corresponding .properties files.
 * CAS517 removed some dead code
 */
public class SyMAP {
	public static final String 	VERSION = "v5.2.1";
	public static final String 	DATE = " (1-Dec-22)";
	public static final int 	DBVER =  4; 			// CAS512 2->3, CAS520 3->4
	public static final String  DBVERSTR = "db" + DBVER;
	
	// CAS500 change v4.2 to doc; CAS510 change base to github; put #links here
	public static final String BASE_HELP_URL_prev51 =   "http://www.agcol.arizona.edu/software/symap/doc/"; 
	public static final String BASE_HELP_URL = 			"https://csoderlund.github.io/SyMAP/"; // CAS510
	public static final String USER_GUIDE_URL =    BASE_HELP_URL + "UserGuide.html"; 
	public static final String circle =  "#circle";				
	public static final String dotplot = "#dotplot_display";
	public static final String align2d = "#alignment_display_2d";
	public static final String align3d = "#alignment_display_3d";
	public static final String TROUBLE_GUIDE_URL = BASE_HELP_URL + "TroubleShoot.html";
		
	public static final  String PERSISTENT_PROPS_FILE = ".symap_saved_props"; // under user's directory; see props.PersistenProps
	private static final String PROPERTIES_FILE = "/properties/symap.properties"; // under java/src; see util.PropertiesReader 
	private static final int HISTORY_SIZE = 10;

	public static boolean TRACE=false; // set in ProjectManagerFrameCommon on -t
	public static boolean DEBUG=false; // CAS519 set in ProjectManagerFrameCommon on -d
	static public boolean GENEN_ONLY=false; // -z CAS519b to update the gene# without having to redo synteny
	
	private static final int    TIME_BETWEEN_MEMORY_TEST;
	static {
		PropertiesReader props = new PropertiesReader(SyMAP.class.getResource(PROPERTIES_FILE)); 
		TIME_BETWEEN_MEMORY_TEST = props.getInt("timeBetweenMemoryTest");
	}
	
	private SyMAPFrame         frame;
	private DrawingPanel       drawingPanel;
	private ControlPanel       controlPanel;
	private HelpBar            helpBar;
	private ColorDialogHandler colorDialogHandler;
	private DatabaseReader     databaseReader;
	private HistoryControl     historyControl;
	private History            history;
	private ImageViewer        imageViewer;
	private PersistentProps    persistentProps;
	private CloseUp            closeup;

	/** Dotplot, Block2Frame, Query **/
	public SyMAP(DatabaseReader dr, TableDataPanel theTablePanel) throws SQLException {
		this(dr, null, theTablePanel);
	}
	/** Chromosome Explorer **/
	public SyMAP(DatabaseReader dr, HelpBar hb, TableDataPanel theTablePanel) // CAS507 removed applet
	throws SQLException 
	{
		this.databaseReader = dr;

		imageViewer = new ImageViewer();

		persistentProps = new PersistentProps(); // CAS521 changed PersistentProps - it has all args now

		if (hb == null) helpBar = new HelpBar(-1, 17); // CAS521 removed dead args
		else			helpBar = hb; // for full explorer

		history = new History(HISTORY_SIZE);

		historyControl = new HistoryControl(history);

		Pools pool = null;
		try {
			pool = PoolManager.getInstance().getPools(dr);
		} catch (SQLException e) {
			ErrorReport.print(e, "Getting pool");
			if (showDatabaseErrorMessage(e.getMessage())) pool = PoolManager.getInstance().getPools(dr); 
			else throw e;
		}

		drawingPanel = new DrawingPanel(theTablePanel,pool,historyControl,helpBar);

		historyControl.setListener(drawingPanel);

		colorDialogHandler = new ColorDialogHandler(persistentProps); // CAS521 moved properties to ColorDialogHandler

		controlPanel = new ControlPanel(drawingPanel,historyControl,imageViewer,colorDialogHandler,helpBar);

		frame = new SyMAPFrame(controlPanel,drawingPanel,helpBar,hb==null,persistentProps);
		
		closeup = new CloseUp(drawingPanel,colorDialogHandler);

		drawingPanel.setCloseUp(closeup);

		colorDialogHandler.addListener((ColorListener)drawingPanel);
		
		if (TIME_BETWEEN_MEMORY_TEST >= 0) MemoryTest.run(TIME_BETWEEN_MEMORY_TEST,System.out);
	}
	/** added in CAS517, then totally removed in CAS521 
	public void setHasFPC(boolean hasFPC) {
		colorDialogHandler.setHasFPC(hasFPC);
		colorDialogHandler.setColors();   
	}
	**/
	private boolean showDatabaseErrorMessage(String msg) {
		return JOptionPane.showConfirmDialog(null,msg,"Database error occurred, try again?",
				JOptionPane.YES_NO_OPTION,JOptionPane.ERROR_MESSAGE) == JOptionPane.YES_OPTION;
	}
	
	public void clear() {
		getDrawingPanel().clearData(); // clear caches
		getHistory().clear(); // clear history
	}

	public SyMAPFrame getFrame() {return frame;}

	public DrawingPanel getDrawingPanel() {return drawingPanel;}

	public History getHistory() {return history;}

	public ImageViewer getImageViewer() {return imageViewer;}

	public DatabaseReader getDatabaseReader() { return databaseReader;}

	public static void printVersion() {
		String base = BASE_HELP_URL.substring(0, BASE_HELP_URL.length()-1);
		String d = (Runtime.getRuntime().maxMemory() / (1024*1024)) + "m";
		System.out.println("\nSyMAP " + VERSION + DATE + "   " + base
			+ "   Java v" + getInstalledJavaVersionStr() + " (" + d + ")");
	}
	
	public static boolean checkJavaSupported(Component frame) {	
		/** CAS42 10/16/17 - did not work for Java SE 9 **/
		int rMajor=1, rMinor=8;
		String rVersion = rMajor + "." + rMinor;
		String version = getInstalledJavaVersionStr();
		
		if (version != null) {
			String[] subs = version.split("\\.");
			int major = 0;
			int minor = 0;

			if (subs.length > 0) major  = Integer.valueOf(subs[0]);
			if (subs.length > 1) minor  = Integer.valueOf(subs[1]);
			
			// Java version not less than 1.8
			if (major == rMajor && minor < rMinor) {
				System.err.println("Java version " + rVersion + " or later is required.");
				
				javax.swing.JOptionPane.showMessageDialog(null,
					    "The installed Java version is " + getInstalledJavaVersionStr() + ".  \n"
					    + "SyMAP requires version " + rVersion + " or later.  \n"
					    + "Please visit http://java.com/download/ to upgrade.", "Java Incompatibility",
					    javax.swing.JOptionPane.ERROR_MESSAGE);
				return false;
			}
			else
				return true;
		}
		else 
			System.err.println("Could not determine Java version.");
		return true;
	}
	private static String getInstalledJavaVersionStr() {
		return System.getProperty("java.version");
	}
	public static String getOSType() {
		return System.getProperty("os.name");
	}
}
