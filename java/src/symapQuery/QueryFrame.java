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
import props.PersistentProps;
import util.Utilities;
import util.ErrorReport;
import symap.closeup.MsaMainPanel;
import symap.manager.Mproject;

/**************************************************
 * The main query frame: 
 * 		this build static tabs on the left (MenuPanel), runs query and msa, and add their tabs on the left
 * 		updateView responds to a tab being click, and it replaces the right panel with the clicked tab panel
 *  
 * CAS564 was 'SyMAPQueryFrame; renamed some stuff; made Table tab and MSA tab work the same 
 */
public class QueryFrame extends JFrame {
	private final int MIN_WIDTH = 1000, MIN_HEIGHT = 720; 	
	private final int MIN_DIVIDER_LOC = 200; 				// CAS543 was screenWidth * 1/4
	
	private static final String [] MENU_ITEMS = { "> Instructions", "> Query Setup", "> Results" }; 
	static private String propNameAll = "SyMapColumns1"; // 0&1's; use if match number of columns in current DB
	static private String propNameSingle = "SyMapColumns2"; 
	
	protected String title=""; 								// CAS560 add for Export
	/******************************************************
	 * ManagerFrame creates this object, add the projects, then calls build.
	 */
	public QueryFrame(String title, DBconn2 dbc2, Vector <Mproject> pVec, boolean useAlgo2) {
		setTitle("Query " + title); // title has: SyMAP vN.N.N - dbName
		
		this.title = title;
		this.tdbc2= new DBconn2("Query-" + DBconn2.getNumConn(), dbc2);
		
		theProjects = new Vector<Mproject> ();			
		for (Mproject p: pVec) theProjects.add(p);
		this.bUseAlgo2 = useAlgo2; 						
		
		// for column saving, including cookies
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
		    public void windowClosing(WindowEvent e) {
		    	tdbc2.close();
		        saveColumnDefaults();
		        dispose();
		    }
		});
		
		Rectangle screenRect = Utilities.getScreenBounds(this);
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
		
		overviewPanel = new OverviewPanel(this);
		queryPanel = 	new QueryPanel(this);
		resultsPanel = 	new ResultsPanel(this);
		
		mainPanel.add(overviewPanel);
		localQueryPane = new JScrollPane(queryPanel);
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
	 * MSA Align; called from UtilSelect/Table MSA...; CAS564 was threaded, but MsaMainPanel is so not needed here 
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
	public void updateTabText(String oldTab, String newTab, String [] resultsRow) { // CAS564 was Table specific, make general
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
				if (((TableMainPanel)allPanelVec.get(x)).isSingle()==bSingle) { // CAS519 last result that is single/!single
					saveLastColumns = ((TableMainPanel)allPanelVec.get(x)).getColumnSelections(); // from FieldData
					break;
				}
			}
		}
		if (saveLastColumns==null) saveLastColumns = makeColumnDefaults(bSingle);
		
		return saveLastColumns; // if this is null, TableDataPanel will use defaults
	} catch (Exception e) {ErrorReport.print(e, "get last columns"); return null;}
	}
	// will be called when first table is created; CAS532 new for column saving
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
		if (saveCols.charAt(0)=='0') return null; // CAS563 indicates a problem if row# not 1
		
		int nThisSp=theProjects.size();
		
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
		
		if (defs2!=null) { // CAS540 add check
			String d2 = abbrev.length + ":";
			for (boolean b : defs2) {
				d2 += (b) ? "1" : "0";
			}
			sngCook.setProp(d2);
		}	
	} catch (Exception e) {ErrorReport.print(e, "save columns");};
	}
	
	protected String [] getAbbrevNames() { // CAS519 added for column headings
		String [] retVal = new String[theProjects.size()];
		
		for(int x=0; x<retVal.length; x++) {
			retVal[x] = theProjects.get(x).getdbAbbrev();
		}
		return retVal;
	}
	protected String getDisplayFromAbbrev(String aname) { // CA519 added for loadRow used by Show Synteny 
		for (Mproject p : theProjects) {
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
	public void removeResult(JPanel rmPanel) { // closeup.MsaMainPanel Stop; CAS564  add
		for (int pos=0; pos<allPanelVec.size(); pos++) {
			if (allPanelVec.get(pos)==rmPanel) {
				removeResult(pos);
				break;
			}
		}
	}
	protected void resetCounter() { resultCounter=0; msaCounter=0;} // for clear all on Results page; CAS564 add msaCounter
	
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
	protected QueryPanel getQueryPanel() {return queryPanel;}
	protected Vector<Mproject> getProjects() {return theProjects;}
	protected boolean isAlgo2() {return bUseAlgo2;};
	
	protected boolean isAnno() { // CAS555 add for queryPanel; not being used, but could be to disable gene queries
		for (Mproject mp : theProjects) {
			if (!mp.hasGenes()) return false;
		}
		return true;
	}
	/**********************************************************/
	private DBconn2 tdbc2 = null;
	private Vector<Mproject> theProjects = null;
	private boolean bUseAlgo2=false;
	
	private int screenWidth, screenHeight;
	private JSplitPane splitPane = null;
	private JPanel mainPanel = null;
	private MenuPanel menuPanel = null;
	private OverviewPanel overviewPanel = null;
	
	private JScrollPane localQueryPane = null;
	private ResultsPanel resultsPanel = null;
	
	private Vector<JPanel> allPanelVec = null;

	private QueryPanel queryPanel = null;
	
	private int resultCounter = 0; 	// CAS532 this was static 
	private int msaCounter = 0; 	// CAS532 this was static -
	private static final long serialVersionUID = 9349836385271744L;
}
