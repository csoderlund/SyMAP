package symap.drawingpanel;

import colordialog.*;
import database.DBconn2;
import props.*;
import symap.closeup.CloseUp;
import symap.frame.HelpBar;
import symapQuery.TableMainPanel;

/**
 * SyMAP sets up the 2D display for  (see frame.ChrExpFrame for full Chromosome explorer):
 * 	manager.SyMAPFrame.regenerate2DView
 * 	dotplot.Data
 * 	blockview.Block2Frame
 * 	symapQuery.tableDataPanel.showSynteny
 * 
 *  CAS521 remove FPC; CAS532 moved HTML links to Jhtml
 *  CAS534 renamed from SyMAP=> SyMAP2d; moved all globals to symap.Globals
 *  CAS544 moved to drawingpanel for 2d stuff; CAS570 removed CAS comments
 */
public class SyMAP2d {
	private Frame2d            frame;
	private DrawingPanel       drawingPanel;
	private ControlPanel       controlPanel;
	private HelpBar            helpBar;
	
	private DBconn2 tdbc2;
	private HistoryControl     historyControl;
	private PersistentProps    persistentProps;
	private CloseUp            closeup;
	private ColorDialogHandler colorDialogHandler;

	/** Dotplot, Block2Frame, Query **/
	public SyMAP2d(DBconn2 dbc2, TableMainPanel theTablePanel)  {
		this(dbc2, null, theTablePanel);
	}
	/** symap.frame.ChrExpFrame.regenerate2Dview 
	 * hb==null is from dotplot or blocks display, so not full ChrExp
	 ***/
	public SyMAP2d(DBconn2 dbc2, HelpBar hb, TableMainPanel theTablePanel) 
	{	
		String type = (hb==null) ? "P" : "E";
		this.tdbc2 = new DBconn2("SyMAP2d" + type + "-" + DBconn2.getNumConn(), dbc2);
		
		persistentProps = new PersistentProps(); 

		if (hb == null) helpBar = new HelpBar(-1, 17); 
		else			helpBar = hb; // for full explorer

		historyControl = new HistoryControl(); 

		drawingPanel = new DrawingPanel(theTablePanel, tdbc2, historyControl, helpBar);

		historyControl.setListener(drawingPanel);

		colorDialogHandler = new ColorDialogHandler(persistentProps); // This sets changed colors

		controlPanel = new ControlPanel(drawingPanel, historyControl, colorDialogHandler, helpBar, (hb!=null));
		
		frame = new Frame2d(this, controlPanel, drawingPanel, helpBar, hb==null, persistentProps);
		
		closeup = new CloseUp(drawingPanel, colorDialogHandler);

		drawingPanel.setCloseUp(closeup);

		colorDialogHandler.addListener((ColorListener)drawingPanel);
	}
	
	public void clear() { // Frame2d.displose() called for non-Explorer 2d
		tdbc2.close(); 	 
		clearLast();
	} 
	public void clearLast() { // ChrExpFrame when creating a new 2d; do not close tdc2
		drawingPanel.clearData(); 	// clear caches
		historyControl.clear(); 	// clear history
		controlPanel.clear();
	}
	public Frame2d getFrame() {return frame;}

	public DrawingPanel getDrawingPanel() {return drawingPanel;}
	
	public ControlPanel getControlPanel() {return controlPanel;} 
}
