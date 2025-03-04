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

import symap.closeup.AlignPool; // CAS541 to get sequence
import symap.manager.Mproject;
import symapMultiAlign.AlignmentViewPanel;

/**************************************************
 * The main query frame
 */
public class SyMAPQueryFrame extends JFrame {
	private final int MIN_WIDTH = 1000, MIN_HEIGHT = 720; 	// CAS513 changed from 1024
	private final int MIN_DIVIDER_LOC = 200; 				// CAS543 was screenWidth * 1/4
	
	private static final String [] MENU_ITEMS = { "> Instructions", "> Query Setup", "> Results" }; 
	static private String propNameAll="SyMapColumns1"; // 0&1's; use if match number of columns in current DB
	static private String propNameSingle="SyMapColumns2"; 
	
	protected String title=""; // CAS560 add for Export
	/******************************************************
	 * ManagerFrame creates this object, add the projects, then calls build.
	 */
	public SyMAPQueryFrame(String title, DBconn2 dbc2, Vector <Mproject> pVec, boolean useAlgo2) {
		setTitle("Query " + title); // CAS514 add version; CAS540 add db
		
		this.title = title;
		this.tdbc2= new DBconn2("Query-" + DBconn2.getNumConn(), dbc2);
		
		theProjects = new Vector<Mproject> ();			// CAS532 change to pass project in (needed for column saving)
		for (Mproject p: pVec) theProjects.add(p);
		this.bUseAlgo2 = useAlgo2; 						// CAS547 add for "All genes with hits"
		
		// CAS532 add the following for column saving, including cookies
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
		setLocationRelativeTo(null); 						// CAS513 center frame
	}
	public void build() { // ManagerFrame
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

		allResults = new Vector<JPanel> ();
		
		overviewPanel = new OverviewPanel(this);
		queryPanel = 	new QueryPanel(this);
		resultsPanel = 	new ResultSummaryPanel(this);
		
		mainPanel.add(overviewPanel);
		localQueryPane = new JScrollPane(queryPanel);
		mainPanel.add(localQueryPane);
		mainPanel.add(resultsPanel);
		
		updateView();
	}
	/************************************************
	 * called from queryPanel on Run Query
	 */
	protected void makeTable(String query, String sum, boolean isSingle) {
		boolean [] saveLastColumns = getLastColumns(isSingle);
		
		TableDataPanel newTablePanel = new TableDataPanel(getInstance(), getNextResultLabel(), 
								saveLastColumns, query, sum, isSingle);
		
		menuPanel.addResult(newTablePanel.getName()+":");
		
		mainPanel.add(newTablePanel);
		
		allResults.add(newTablePanel);
		
		updateView(); // shows the table with Stop 
	}
	private String getNextResultLabel() { return "Result " + (++resultCounter); }
	private SyMAPQueryFrame getInstance() { return this; }
	
	
	/***********************************************
	 * Column defaults -  Called for makeTable and shutdown
	 * the ColumnSelection is a boolean vector, with no knowledge of species anno's
	 * Read/write from PersistentProps cookies
	 */
	// Only knows selections from FieldData, does not know order
	private boolean [] getLastColumns(boolean bSingle) {
	try {
		boolean [] saveLastColumns = null;
		for(int x=allResults.size()-1; x>=0 && saveLastColumns == null; x--) {
			if (allResults.get(x) instanceof TableDataPanel) {
				if (((TableDataPanel)allResults.get(x)).isSingle()==bSingle) { // CAS519 last result that is single/!single
					saveLastColumns = ((TableDataPanel)allResults.get(x)).getColumnSelections(); // from FieldData
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
		int nGenCol = FieldData.getGenColumnCount(bSingle);
		int nChrCol = FieldData.getSpColumnCount(bSingle); 
		if (nCol < (nGenCol+nChrCol)) return null; 
		
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
	// CAS532 new for column saving; called on shutdown
	private void saveColumnDefaults() {
	try {
		String [] abbrev = getAbbrevNames();
		
		PersistentProps cookies = new PersistentProps();
		PersistentProps allCook = cookies.copy(propNameAll);
		
		boolean [] defs1 = getLastColumns(false);
		if (defs1!=null) { // CAS540 add check - .symap_saved_props may have been removed
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
	/*************************************************************
	 * TableDataPanel 
	 */
	protected void updateResultCount(TableDataPanel newTablePanel) {
		int numResults = newTablePanel.getNumResults();
		
		String res = (numResults == 0) ? ": No results" : ": " + newTablePanel.getNumResults();
		
		menuPanel.updateResultLabel(newTablePanel.getName() + ":", newTablePanel.getName() + res);
		
		resultsPanel.addResult(newTablePanel.getSummary()); // CAS513 put here so have row count
		
		updateView();
	}
	protected void addAlignmentTab(TableDataPanel parent, String [] names, String [] lines, String [] seqs, String sum) {
		final SyMAPQueryFrame theFrame = this;
		final TableDataPanel parentCopy = parent;
		final String [] theNames = names;
		final String [] theLines = lines;
		final String [] theSeqs = seqs;
		final String summary = sum;
		Thread thread = new Thread(new Runnable() {
			public void run() {
				try {
					String filename = "temp";
					
					AlignmentViewPanel newTab = new AlignmentViewPanel(theFrame, theNames, theLines, theSeqs, filename);
					String tabName = "Align " + (++nMultiAlignmentCounter) + ": " + summary;
						
					addResultPanel(parentCopy, newTab, tabName, summary);
				} catch (Exception e) {
					ErrorReport.print(e, "Align sequence");
				}
			}
		});
		thread.setPriority(Thread.MIN_PRIORITY);
		thread.start();
	}
	// Muscle alignment
	private void addResultPanel(JPanel parentPanel, JPanel newPanel, String name, String summary) {
		String [] row = new String[2];
		row[0] = name;
		row[1] = summary;
		
		resultsPanel.addResult(row);
		allResults.add(newPanel);
		menuPanel.addResult(name);
		mainPanel.add(newPanel);
		updateView();
	}
	protected String getSequence(int start, int stop, int groupIdx) {
		try {
			return new AlignPool(tdbc2).loadPseudoSeq(start + ":" + stop, groupIdx);
		} catch (Exception e) {ErrorReport.print(e, "Get sequence");}
		return "";
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
	protected QueryPanel getQueryPanel() {return queryPanel;}
	
	/***************************************************
	 * ResultSummaryPanel
	 */
	protected void removeResult(int result) {
		JPanel temp = allResults.get(result);
		
		mainPanel.remove(temp);
		allResults.remove(result);
		menuPanel.removeResult(result);
		updateView();
	}
	protected void resetCounter() { resultCounter=0;} // CAS513 for clear all on Results page
	
	private void updateView() {
		overviewPanel.setVisible(false);
		localQueryPane.setVisible(false);
		resultsPanel.setVisible(false);
		
		for(int x=0; x<allResults.size(); x++)
			allResults.get(x).setVisible(false);
		
		int selection = menuPanel.getCurrentSelection();
		
		switch(selection) { 
			case 0: overviewPanel.setVisible(true);		break;
			case 1: localQueryPane.setVisible(true);	break;
			case 2: resultsPanel.setVisible(true);		break;
			default:
				selection -= MENU_ITEMS.length;
				allResults.get(selection).setVisible(true);
		}
	}
	protected void selectResult(int position) {
		menuPanel.setSelection(position + MENU_ITEMS.length);
		updateView();
	}
	/**********************************************************/
	protected DBconn2 getDBC() { return tdbc2; }
	protected Vector<Mproject> getProjects() { return theProjects; }
	protected boolean isAlgo2() {return bUseAlgo2;};
	public boolean isAnno() { // CAS555 add for queryPanel
		for (Mproject mp : theProjects) {
			if (!mp.hasGenes()) return false;
		}
		return true;
	}
	
	private DBconn2 tdbc2 = null;
	private Vector<Mproject> theProjects = null;
	private boolean bUseAlgo2=false;
	
	private int screenWidth, screenHeight;
	private JSplitPane splitPane = null;
	private JPanel mainPanel = null;
	private MenuPanel menuPanel = null;
	private OverviewPanel overviewPanel = null;
	
	private JScrollPane localQueryPane = null;
	private ResultSummaryPanel resultsPanel = null;
	
	private Vector<JPanel> allResults = null;

	private QueryPanel queryPanel = null;
	
	private int resultCounter = 0; 				// CAS532 this was static 
	private int nMultiAlignmentCounter = 0; 	// CAS532 this was static -
	private static final long serialVersionUID = 9349836385271744L;
}
