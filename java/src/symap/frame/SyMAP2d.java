package symap.frame;

import java.sql.SQLException;
import util.*;
import colordialog.*;
import database.DBconn;
import props.*;
import symap.drawingpanel.DrawingPanel;
import symap.manager.Pools;
import history.History;
import history.HistoryControl;
import symap.closeup.CloseUp;
import symapQuery.TableDataPanel;

/**
 * SyMAP sets up the 2D display for:
 * 	manager.SyMAPFrame.regenerate2DView
 * 	dotplot.Data
 * 	blockview.Block2Frame
 * 	symapQuery.tableDataPanel.showSynteny
 * 
 *	CAS532 moved HTML links to Jhtml
 *  CAS534 renamed from SyMAP=> SyMAP2d; moved all globals to symap.Globals
 */
public class SyMAP2d {
	private static final int HISTORY_SIZE = 10;

	private Frame2d         frame;
	private DrawingPanel       drawingPanel;
	private ControlPanel       controlPanel;
	private HelpBar            helpBar;
	
	private DBconn     databaseReader;
	private HistoryControl     historyControl;
	private History            history;
	private ImageViewer        imageViewer;
	private PersistentProps    persistentProps;
	private CloseUp            closeup;
	private ColorDialogHandler colorDialogHandler;

	/** Dotplot, Block2Frame, Query **/
	public SyMAP2d(DBconn dr, TableDataPanel theTablePanel) throws SQLException {
		this(dr, null, theTablePanel);
	}
	/** Chromosome Explorer **/
	public SyMAP2d(DBconn dr, HelpBar hb, TableDataPanel theTablePanel) // CAS507 removed applet
	throws SQLException 
	{
		this.databaseReader = dr;

		persistentProps = new PersistentProps(); // CAS521 changed PersistentProps - it has all args now

		if (hb == null) helpBar = new HelpBar(-1, 17); // CAS521 removed dead args
		else			helpBar = hb; // for full explorer

		history = new History(HISTORY_SIZE);

		historyControl = new HistoryControl(history);

		Pools pool = null; 
		try { pool = new Pools(dr); // CAS534 removed PoolManager.java
		} catch (SQLException e) {
			ErrorReport.print(e, "Getting pool");
			throw e;
		}

		drawingPanel = new DrawingPanel(theTablePanel,pool,historyControl,helpBar);

		historyControl.setListener(drawingPanel);

		colorDialogHandler = new ColorDialogHandler(persistentProps); // This sets changed colors; CAS521 moved properties to ColorDialogHandler

		controlPanel = new ControlPanel(drawingPanel,historyControl, colorDialogHandler,helpBar);

		frame = new Frame2d(controlPanel,drawingPanel,helpBar,hb==null,persistentProps);
		
		closeup = new CloseUp(drawingPanel,colorDialogHandler);

		drawingPanel.setCloseUp(closeup);

		colorDialogHandler.addListener((ColorListener)drawingPanel);
	}
	/** added in CAS517, then totally removed in CAS521 
	public void setHasFPC(boolean hasFPC) {
		colorDialogHandler.setHasFPC(hasFPC);
		colorDialogHandler.setColors();   
	}
	private boolean showDatabaseErrorMessage(String msg) { // CAS534 removed PoolManager
		return JOptionPane.showConfirmDialog(null,msg,"Database error occurred, try again?",
				JOptionPane.YES_NO_OPTION,JOptionPane.ERROR_MESSAGE) == JOptionPane.YES_OPTION;
	}
	**/
	
	public void clear() {
		getDrawingPanel().clearData(); // clear caches
		getHistory().clear(); // clear history
	}

	public Frame2d getFrame() {return frame;}

	public DrawingPanel getDrawingPanel() {return drawingPanel;}
	
	public ControlPanel getControlPanel() {return controlPanel;} // CAS531 add for 

	public History getHistory() {return history;}

	public ImageViewer getImageViewer() {return imageViewer;}

	public DBconn getDatabaseReader() { return databaseReader;}
}
