package symapQuery;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.Vector;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;

import database.DBconn2;
import database.Version;
import props.PersistentProps;
import util.Popup;
import util.Utilities;
import util.ErrorReport;
import symap.closeup.MsaMainPanel;
import symap.manager.ManagerFrame;
import symap.manager.Mpair;
import symap.manager.Mproject;

/**************************************************
 * The main query frame: 
 * 		this build static tabs on the left (MenuPanel), runs query and msa, and add their tabs on the left
 * 		updateView responds to a tab being click, and it replaces the right panel with the clicked tab panel
 * Called by ManagerFrame
 *   Calls QueryPanel, which performs database call, sends to DBdata, then TableMainPanel
 */
public class QueryFrame extends JFrame {
	private final int MIN_WIDTH = 1000, MIN_HEIGHT = 720; 	
	private final int MIN_DIVIDER_LOC = 200; 				
	
	private static final String [] MENU_ITEMS = { "> Instructions", "> Query Setup", "> Results" }; 
	static private String propNameAll = "SyMapColumns1"; // 0&1's; use if match number of columns in current DB
	static private String propNameSingle = "SyMapColumns2"; 
	
	protected String title=""; 								// for Export
	/******************************************************
	 * ManagerFrame creates this object, add the projects, then calls build.
	 */
	public QueryFrame(ManagerFrame mFrame, String title, DBconn2 dbc2, Vector <Mproject> mProjVec, 
			boolean useAlgo2, int cntUsePseudo, int cntSynteny, boolean isSelf) {
		setTitle("Query " + title); // title has: SyMAP vN.N.N - dbName
		
		this.mFrame = mFrame;  //  for getMpair
		this.title = title;
		this.tdbc2= new DBconn2("Query-" + DBconn2.getNumConn(), dbc2);
		
		mProjs = new Vector<Mproject> ();			
		for (Mproject p: mProjVec) mProjs.add(p);
		String [] ab = getAbbrevNames();
		for (int i=0; i<ab.length-1; i++) {// This is now checked in ProjParams, but to be sure...
			for (int j=i+1; j<ab.length; j++) {
				if (ab[i].equals(ab[j])) {
					Popup.showErrorMessage("Duplicate abbreviations of " + ab[i] + "\nPlease fix with symap and try again.");
					return;
				}
			}
		}
		if (mProjs.size()==2 && mProjs.get(0).getIdx()==mProjs.get(1).getIdx()) {
			int pairIdx = mFrame.getMpair(mProjs.get(0).getIdx(), mProjs.get(1).getIdx()).getPairIdx();
			if (new Version(tdbc2).isVerLt(pairIdx, 575)) 
				Popup.showWarning("Self-synteny must be updated with A&S v5.7.5 or later to work in Queries");
		}
		
		this.bUseAlgo2 = useAlgo2; 		
		this.cntUsePseudo = cntUsePseudo;
		this.cntSynteny = cntSynteny;
		this.isSelf = isSelf; // abbrev are diff, all else the same
		
		// for column saving, including cookies
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
		    public void windowClosing(WindowEvent e) {
		    	tdbc2.close();
		        saveColumnDefaults();
		        dispose();
		    }
		});
		
		Rectangle screenRect = util.Jcomp.getScreenBounds(this);
		screenWidth  = Math.min( MIN_WIDTH, screenRect.width);
		screenHeight = Math.min(MIN_HEIGHT, screenRect.height); 	
		setSize(screenWidth, screenHeight);	
		setLocationRelativeTo(null); 						
	}
	public void build() { // QueryFrame
		buildMenuPanel();
		buildMainPanel();
		
		splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setContinuousLayout(true);
        splitPane.setDividerLocation(MIN_DIVIDER_LOC);

        splitPane.setBorder(null);
        splitPane.setRightComponent(mainPanel);
        splitPane.setLeftComponent(menuPanel);
        
        setLayout(new BorderLayout());
        add(splitPane, BorderLayout.CENTER);		
	}
	// left side 
	private void buildMenuPanel() {
		menuPanel = new MenuPanel(MENU_ITEMS, 0, new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				menuPanel.handleClick(arg0);
				updateView();
			}
		});
	}
	// right side
	private void buildMainPanel() {
		mainPanel = new JPanel();
		mainPanel.setLayout( new BoxLayout(mainPanel, BoxLayout.PAGE_AXIS) );
		mainPanel.setBackground(Color.WHITE);

		allPanelVec = new Vector<JPanel> ();
		
		qPanel 			= new QueryPanel(this);
		overviewPanel 	= new OverviewPanel(this);
		resultsPanel 	= new ResultsPanel(this);
		
		mainPanel.add(overviewPanel);
		localQueryPane = new JScrollPane(qPanel);
		mainPanel.add(localQueryPane);
		mainPanel.add(resultsPanel);
		
		updateView();
	}
	/************************************************
	 * Make table and add tab: called from queryPanel on Run Query 
	 */
	protected void makeTabTable(String query, String sum, boolean isSingle) {
		boolean [] saveLastColumns = getLastColumns(isSingle);
		
		String tabName = "Result " + (++resultCounter); // DO NOT CHANGE, same as in TableMainPanel.tableFinish
		TableMainPanel tablePanel = new TableMainPanel(getInstance(), tabName, 
								saveLastColumns, query, sum, isSingle);
		
		menuPanel.addLeftTab(tabName+":"); 
		mainPanel.add(tablePanel);
		allPanelVec.add(tablePanel);
		
		updateView(); // shows the table with Stop 
	}
	
	/************************************************ 
	 * MSA Align; called from UtilSelect/Table MSA...; 
	 */
	protected void makeTabMSA(TableMainPanel tablePanel, String [] names, String [] seqs, String [] tabLines, 
			String progress, String tabAdd, String resultSum, String msaSum, 
			boolean bTrim, boolean bAuto, int cpus) 
	{			
			String tabName = "MSA " + (++msaCounter) + ":";
			MsaMainPanel msaPanel = new MsaMainPanel(tablePanel, getInstance(), names, seqs, tabLines, 
					progress, msaSum, tabName, tabAdd, resultSum, bTrim, bAuto, cpus);
		
			menuPanel.addLeftTab(tabName+":");
			mainPanel.add(msaPanel); 	// set Visible when tab clicked
			allPanelVec.add(msaPanel);
			
			updateView();
	}

	// Called from TableMainPanel and closeup.MsaMainPanel to finish 
	public void updateTabText(String oldTab, String newTab, String [] resultsRow) { 
		menuPanel.updateLeftTab(oldTab, newTab); 
		
		resultsPanel.addResultText(resultsRow); 
	}
	private QueryFrame getInstance() { return this; }
	
	/***********************************************
	 * Column defaults -  Called for makeTable and shutdown
	 * the ColumnSelection is a boolean vector, with no knowledge of species anno's
	 * Read/write from PersistentProps cookies
	 */
	// Only knows selections from FieldData, does not know order
	private boolean [] getLastColumns(boolean bSingle) {
	try {
		boolean [] saveLastColumns = null;
		for(int x=allPanelVec.size()-1; x>=0 && saveLastColumns == null; x--) {
			if (allPanelVec.get(x) instanceof TableMainPanel) {
				if (((TableMainPanel)allPanelVec.get(x)).isSingle()==bSingle) {
					saveLastColumns = ((TableMainPanel)allPanelVec.get(x)).getColumnSelections(); // from FieldData
					break;
				}
			}
		}
		if (saveLastColumns==null) saveLastColumns = makeColumnDefaults(bSingle);
		
		return saveLastColumns; // if this is null, TableDataPanel will use defaults
	} catch (Exception e) {ErrorReport.print(e, "get last columns"); return null;}
	}
	// will be called when first table is created; 
	// If made for a different database, only the General and Species will be set
	private boolean [] makeColumnDefaults(boolean bSingle) {
	try {
		PersistentProps cookies = new PersistentProps(); 
		String which = (bSingle) ? propNameSingle : propNameAll;
		String propSaved = cookies.copy(which).getProp();
		if (propSaved==null || propSaved.length()<5) return null;
	
		String [] tok = propSaved.split(":");
		if (tok.length!=2) return null;
		int nSaveSp = Utilities.getInt(tok[0]);
		if (nSaveSp<=1) return null;
		
		String saveCols = tok[1];
		int nCol = saveCols.length();
		int nGenCol = TableColumns.getGenColumnCount(bSingle);
		int nChrCol = TableColumns.getSpColumnCount(bSingle); 
		if (nCol < (nGenCol+nChrCol)) return null; 
		if (saveCols.charAt(0)=='0') return null; // indicates a problem if row# not 1
		
		int nThisSp=mProjs.size();
		
		// same number of species: the anno counts could be wrong, but TableDataPanel.setColumnSelection checks
		if (nSaveSp==nThisSp) { 
			boolean [] bcols = new boolean [nCol];
			
			for (int i=0; i<nCol; i++) {
				bcols[i] = (saveCols.charAt(i)=='1') ? true : false;
			}
			return bcols;
		}
		// different number of species: set general and all species according to the last 1st
		int nSave = nGenCol + (nChrCol*nThisSp); 
		boolean [] bcols = new boolean [nSave];
		Arrays.fill(bcols, true);
		
		// general	
		int x=0;
		for (int i=0; i<nGenCol && x<nSave; i++) {
			bcols[x++] = (saveCols.charAt(i)=='1') ? true : false;
		}
		if (saveCols.length()< (nGenCol+nChrCol)) return bcols;
		
		// species
		String chrSp1 = saveCols.substring(nGenCol, nGenCol+nChrCol); 
		for (int j=0; j<nThisSp; j++) {
			for (int i=0; i<nChrCol && x<nSave; i++) {
				bcols[x++] = (chrSp1.charAt(i)=='1') ? true : false;
			}
		}
		return bcols;
	} catch (Exception e) {ErrorReport.print(e, "make column defaults"); return null;}	
	}
	// for column saving; called on shutdown
	private void saveColumnDefaults() {
	try {
		String [] abbrev = getAbbrevNames();
		
		PersistentProps cookies = new PersistentProps();
		PersistentProps allCook = cookies.copy(propNameAll);
		
		boolean [] defs1 = getLastColumns(false);
		if (defs1!=null) { 						// check - .symap_saved_props may have been removed
			String d1 = abbrev.length + ":";
			for (boolean b : defs1) {
				d1 += (b) ? "1" : "0";
			}
			allCook.setProp(d1);
		}
		boolean [] defs2 = getLastColumns(true);
		PersistentProps sngCook = cookies.copy(propNameSingle);
		
		if (defs2!=null) { //
			String d2 = abbrev.length + ":";
			for (boolean b : defs2) {
				d2 += (b) ? "1" : "0";
			}
			sngCook.setProp(d2);
		}	
	} catch (Exception e) {ErrorReport.print(e, "save columns");};
	}
	
	protected String [] getAbbrevNames() { // for column headings
		String [] retVal = new String[mProjs.size()];
		
		for(int x=0; x<retVal.length; x++) {
			retVal[x] = mProjs.get(x).getdbAbbrev();
		}
		return retVal;
	}
	protected String getDisplayFromAbbrev(String aname) { // for loadRow used by Show Synteny 
		for (Mproject p : mProjs) {
			if (aname.contentEquals(p.getdbAbbrev())) return p.getDisplayName();
		}
		return null;
	}
	
	/***************************************************
	 * Left side content
	 */
	protected void removeResult(int pos) {
		if (pos>allPanelVec.size()) return; 	// can happen on Stop
		
		JPanel tPanel = allPanelVec.get(pos);
		
		mainPanel.remove(tPanel);   // remove panel from queryFrame
		
		allPanelVec.remove(pos); 	// remove panel from list of panels
		
		menuPanel.removeResult(pos); // remove from left panel
		
		updateView();
	}
	public void removeResult(JPanel rmPanel) { // closeup.MsaMainPanel Stop; 
		for (int pos=0; pos<allPanelVec.size(); pos++) {
			if (allPanelVec.get(pos)==rmPanel) {
				removeResult(pos);
				break;
			}
		}
	}
	protected void resetCounter() { resultCounter=0; msaCounter=0;} // for clear all on Results page; 
	
	private void updateView() {
		overviewPanel.setVisible(false);
		localQueryPane.setVisible(false);
		resultsPanel.setVisible(false);
		
		for(int x=0; x<allPanelVec.size(); x++)
			allPanelVec.get(x).setVisible(false);
		
		int pos = menuPanel.getCurrentSelection();
		
		switch(pos) { 
			case 0: overviewPanel.setVisible(true);		break;
			case 1: localQueryPane.setVisible(true);	break;
			case 2: resultsPanel.setVisible(true);		break;
			default:
				pos -= MENU_ITEMS.length;
				if (pos<allPanelVec.size())
					allPanelVec.get(pos).setVisible(true);
		}
	}
	protected void selectResult(int position) { // ResultPanel mouse click
		menuPanel.setSelection(position + MENU_ITEMS.length);
		updateView();
	}
	/**********************************************************/
	protected DBconn2 getDBC() {return tdbc2;}
	protected QueryPanel getQueryPanel() {return qPanel;}
	protected Vector<Mproject> getProjects() {return mProjs;}
	protected boolean isAlgo2() {return bUseAlgo2;};
	protected boolean isSelf() {return isSelf;}
	
	protected Mpair getMpair(int idx1, int idx2) { // used to get pairIdx instead of reading from DB; CAS575
		return mFrame.getMpair(idx1, idx2);
	}
	protected boolean isAnno() { // not being used, but could be to disable gene queries
		for (Mproject mp : mProjs) {
			if (!mp.hasGenes()) return false;
		}
		return true;
	}
	/**********************************************************/
	private DBconn2 tdbc2 = null;
	private ManagerFrame mFrame;
	private QueryPanel qPanel = null;
	
	private Vector<Mproject> mProjs = null;
	protected boolean bUseAlgo2=false;
	protected boolean isSelf=false;			
	protected int cntUsePseudo=0, cntSynteny=0; // for instructions
	
	private int screenWidth, screenHeight;
	private JSplitPane splitPane = null;
	private JPanel mainPanel = null;
	private MenuPanel menuPanel = null;
	private OverviewPanel overviewPanel = null;
	
	private JScrollPane localQueryPane = null;
	private ResultsPanel resultsPanel = null;
	
	private Vector<JPanel> allPanelVec = null;

	private int resultCounter = 0; 	
	private int msaCounter = 0; 	
	private static final long serialVersionUID = 9349836385271744L;
}
