package symap;

import java.util.Date;
import java.sql.SQLException;
import java.awt.Component;
import java.awt.Color;
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
 * Class SyMAP is used to acquire and configure the desired objects.
 *
 * The properties file (/properties/symap.properties) needs to 
 * be properly set along with all of the corresponding .properties files.
 */
public class SyMAP {
	public static final String 	VERSION = "v5.1.3";
	public static final String 	DATE = " (30-Aug-22)";
	public static final int 	DBVER =  3; 			// CAS512 update from 2
	public static final String DBVERSTR = "db" + DBVER;
	
	// CAS500 change v4.2 to doc; CAS510 change base to github; put #links here
	public static final String BASE_HELP_URL_prev51 =    "http://www.agcol.arizona.edu/software/symap/doc/"; 
	public static final String BASE_HELP_URL = "https://csoderlund.github.io/SyMAP/"; // CAS510
	public static final String USER_GUIDE_URL =    BASE_HELP_URL + "UserGuide.html"; 
	public static final String circle = "#circle";				
	public static final String dotplot = "#dotplot_display";
	public static final String align2d = "#alignment_display_2d";
	public static final String align3d = "#alignment_display_3d";
	public static final String TROUBLE_GUIDE_URL = BASE_HELP_URL + "TroubleShoot.html";
		
	public static final int MAX_PROJECTS = 100; // for parsing applet params
	
	private static final int REQUIRED_JAVA_MAJOR_VERSION = 1; 
	private static final int REQUIRED_JAVA_MINOR_VERSION = 8; // CAS500
	
	private static final Color purple = new Color(204, 128, 255); 
	public static final Color[] projectColors = { Color.cyan, Color.green, purple, Color.yellow, Color.orange }; // should match SyMAP3D.projectColors

	public static final boolean DEBUG;
	public static final int     TIME_BETWEEN_MEMORY_TEST;

	static {
		PropertiesReader props = new PropertiesReader(SyMAP.class.getResource("/properties/symap.properties"));
		DEBUG                    = props.getBoolean("debug");
		TIME_BETWEEN_MEMORY_TEST = props.getInt("timeBetweenMemoryTest");
	}
	
	public static final Date COOKIE_EXPIRES = CookieProps.getDaysFromNow(365);
	public static final String PERSISTENT_PROPS_FILE = ".symap_saved_props"; // CAS doesn't change
	public static final int HISTORY_SIZE = 10;

	private static final String PP_HEADER = "SyMAP Saved Properties. Do not modify.";

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

	public SyMAP(DatabaseReader dr, TableDataPanel theTablePanel) throws SQLException {
		this(dr, null, theTablePanel);
	}

	public SyMAP(DatabaseReader dr, HelpBar hb, TableDataPanel theTablePanel) // CAS507 removed applet
	throws SQLException 
	{
		this.databaseReader = dr;

		imageViewer = new ImageViewer();

		persistentProps = new FileProps(Utilities.getFile(PERSISTENT_PROPS_FILE,true),PP_HEADER,null);

		if (hb == null)
			helpBar = new HelpBar(-1, 17, true, false, false);
		else
			helpBar = hb; // for 3D

		history = new History(HISTORY_SIZE);

		historyControl = new HistoryControl(history);

		Pools p = null;
		try {
			p = PoolManager.getInstance().getPools(dr);
		} catch (SQLException e) {
			e.printStackTrace();
			if (showDatabaseErrorMessage(e.getMessage())) p = PoolManager.getInstance().getPools(dr); 
			else throw e;
		}

		drawingPanel = new DrawingPanel(theTablePanel,p,historyControl,helpBar);

		historyControl.setListener(drawingPanel);

		colorDialogHandler = new ColorDialogHandler(persistentProps,/*helpHandler,*/new PropertiesReader(SyMAP.class.getResource("/properties/colors.properties")));

		controlPanel = new ControlPanel(drawingPanel,historyControl,imageViewer,colorDialogHandler,helpBar);

		frame = new SyMAPFrame(controlPanel,drawingPanel,helpBar,hb==null,persistentProps);
		
		closeup = new CloseUp(drawingPanel,colorDialogHandler);

		drawingPanel.setCloseUp(closeup);

		colorDialogHandler.addListener((ColorListener)drawingPanel);
		colorDialogHandler.setColors();

		if (TIME_BETWEEN_MEMORY_TEST >= 0) MemoryTest.run(TIME_BETWEEN_MEMORY_TEST,System.out);
	}

	private boolean showDatabaseErrorMessage(String msg) {
		return JOptionPane.showConfirmDialog(null,msg,"Database error occurred, try again?",
				JOptionPane.YES_NO_OPTION,JOptionPane.ERROR_MESSAGE) == JOptionPane.YES_OPTION;
	}
	
	public void clear() {
		getDrawingPanel().clearData(); // clear caches
		getHistory().clear(); // clear history
	}

	public SyMAPFrame getFrame() {
		return frame;
	}

	public DrawingPanel getDrawingPanel() {
		return drawingPanel;
	}

	public History getHistory() {
		return history;
	}

	public ImageViewer getImageViewer() {
		return imageViewer;
	}

	public DatabaseReader getDatabaseReader() {
		return databaseReader;
	}

	public static void printVersion() {
		String base = BASE_HELP_URL.substring(0, BASE_HELP_URL.length()-1);
		String d = (Runtime.getRuntime().maxMemory() / (1024*1024)) + "m";
		System.out.println("\nSyMAP " + VERSION + DATE + "   " + base
			+ "   Java v" + getInstalledJavaVersionStr() + " (" + d + ")");
	}
	
	public static boolean checkJavaSupported(Component frame) {	
		/** CAS42 10/16/17 - did not work for Java SE 9 **/
		String version = getInstalledJavaVersionStr();
		if (version != null) {
			String[] subs = version.split("\\.");
			int major = 0;
			int minor = 0;

			if (subs.length > 0) major  = Integer.valueOf(subs[0]);
			if (subs.length > 1) minor  = Integer.valueOf(subs[1]);
			// not less than 1.7
			if (major == REQUIRED_JAVA_MAJOR_VERSION && minor < REQUIRED_JAVA_MINOR_VERSION)
			{
				System.err.println("Java version " 
						+ getRequiredJavaVersionStr()
						+ " or later is required.");
				
					javax.swing.JOptionPane.showMessageDialog(null,
						    "The installed Java version is "
						    + getInstalledJavaVersionStr() + ".  \n"
						    + "SyMAP requires version "
						    + getRequiredJavaVersionStr() + " or later.  \n"
						    + "Please visit http://java.com/download/ to upgrade.",
						    "Java Incompatibility",
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
	
	private static String getRequiredJavaVersionStr() {
		return REQUIRED_JAVA_MAJOR_VERSION
				+ "." + REQUIRED_JAVA_MINOR_VERSION;
	}
	
	private static String getInstalledJavaVersionStr() {
		return System.getProperty("java.version");
	}
	public static String getOSType() {
		return System.getProperty("os.name");
	}
}
