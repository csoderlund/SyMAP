package symapQuery;

/**************************************************
 * The main query frame
 */
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.SQLException;
import java.util.Vector;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;

import util.DatabaseReader;
import util.Utilities;

import util.ErrorReport;
import symap.SyMAP;
import symap.pool.PoolManager;
import symap.pool.Pools;
import symap.projectmanager.common.Project;
import symapMultiAlign.AlignmentViewPanel;

public class SyMAPQueryFrame extends JFrame {
	private static final String [] MENU_ITEMS = { "> Instructions", "> Query Setup", "> Results" }; 
	
	// ProjectManager creates this object, add the projects, then calls build.
	public SyMAPQueryFrame(DatabaseReader dr, boolean is3D) {
		setTitle("SyMAP Query " + SyMAP.VERSION); // CAS514 add version
		theReader = dr;
		
		/** CAS521 was in the middle of adding column save; finish for 522
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
		    public void windowClosing(WindowEvent e) {
		        FieldData.saveColumnDefaults(getAbbrevNames(), getLastColumns(false));
		        dispose();
		    }
		});
		FieldData.setColumnDefaults(getAbbrevNames());
		**/
		
		theProjects = new Vector<Project> ();		
		
		Rectangle screenRect = Utilities.getScreenBounds(this);
		screenWidth  = Math.min(1000, screenRect.width);
		screenHeight = Math.min(720, screenRect.height); // CAS513 changed from 1024
		setSize(screenWidth, screenHeight);	
		setLocationRelativeTo(null); // CAS513 center frame
	}
	public void addProject(Project project) {
		if(!project.getType().equals("fpc")) {
			theProjects.add(project);
			if (!project.hasAbbrev()) // CAS519 need to let user know that it can be set
				System.err.println(project.getDisplayName() + " does not have abbrev set in Parameters: using " + project.getAbbrevName());
		}
	}
	public void build() {
		buildMenuPanel();
		buildMainPanel();
		
		splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setContinuousLayout(true);
        splitPane.setDividerLocation(screenWidth * 1/4);

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
	// called from queryPanel on Run Query
	public void makeTable(String query, String sum, boolean bSingle) {
		boolean [] saveLastColumns = getLastColumns(bSingle);
		
		TableDataPanel newTablePanel = new TableDataPanel(getInstance(), getNextResultLabel(), 
											saveLastColumns, query, sum, bSingle);
		
		menuPanel.addResult(newTablePanel.getName()+":");
		
		mainPanel.add(newTablePanel);
		
		allResults.add(newTablePanel);
		
		updateView(); // shows the table with Stop 
	}
	private boolean [] getLastColumns(boolean bSingle) {
		boolean [] saveLastColumns = null;
		for(int x=allResults.size()-1; x>=0 && saveLastColumns == null; x--) {
			if (allResults.get(x) instanceof TableDataPanel) {
				if (((TableDataPanel)allResults.get(x)).isSingle()==bSingle) { // CAS519 add single check, and break
					saveLastColumns = ((TableDataPanel)allResults.get(x)).getColumnSelections();
					break;
				}
			}
		}
		return saveLastColumns;
	}
	public void updateResultCount(TableDataPanel newTablePanel) {
		int numResults = newTablePanel.getNumResults();
		
		String res = (numResults == 0) ? ": No results" : ": " + newTablePanel.getNumResults();
		
		menuPanel.updateResultLabel(newTablePanel.getName() + ":", newTablePanel.getName() + res);
		
		resultsPanel.addResult(newTablePanel.getSummary()); // CAS513 put here so have row count
		
		updateView();
	}
	public void addAlignmentTab(TableDataPanel parent, String [] names, String [] lines, String [] seqs, String sum) {
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
	public void addResultPanel(JPanel parentPanel, JPanel newPanel, String name, String summary) {
		String [] row = new String[2];
		row[0] = name;
		row[1] = summary;
		
		resultsPanel.addResult(row);
		allResults.add(newPanel);
		menuPanel.addResult(name);
		mainPanel.add(newPanel);
		updateView();
	}

	public void removeResult(int result) {
		JPanel temp = allResults.get(result);
		
		mainPanel.remove(temp);
		allResults.remove(result);
		menuPanel.removeResult(result);
		updateView();
	}
	
	public String getNextResultLabel() { return "Result " + (++resultCounter); }
	public void resetCounter() { resultCounter=0;} // CAS513 for clear all on Results page
	
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
	
	public String [] getDisplayNames() {
		String [] retVal = new String[theProjects.size()];
		
		for(int x=0; x<retVal.length; x++) {
			retVal[x] = theProjects.get(x).getDisplayName();
		}
		return retVal;
	}
	public String [] getAbbrevNames() { // CAS519 added for column headings
		String [] retVal = new String[theProjects.size()];
		
		for(int x=0; x<retVal.length; x++) {
			retVal[x] = theProjects.get(x).getAbbrevName();
		}
		return retVal;
	}
	public String getDisplayFromAbbrev(String aname) { // CA519 added for loadRow used by Show Synteny 
		for (Project p : theProjects) {
			if (aname.contentEquals(p.getAbbrevName())) return p.getDisplayName();
		}
		return null;
	}
	public DatabaseReader getDatabase() { return theReader; }
	
	public Vector<Project> getProjects() { return theProjects; }
	
	public String getSequence(int start, int stop, int groupIdx) {
		Pools p = null;
		try {
			p = PoolManager.getInstance().getPools(theReader);	
			return p.getSequencePool().loadPseudoSeq(start + ":" + stop, groupIdx);
		} catch (SQLException e) {
			ErrorReport.print(e, "Get sequence");
		}
		return "";
	}
	
	public void selectResult(int position) {
		menuPanel.setSelection(position + MENU_ITEMS.length);
		updateView();
	}
	
	public SyMAPQueryFrame getInstance() { return this; }
	public QueryPanel getQueryPanel() {return queryPanel;}
		
	private DatabaseReader theReader = null;
	private Vector<Project> theProjects = null;
	
	private int screenWidth, screenHeight;
	private JSplitPane splitPane = null;
	private JPanel mainPanel = null;
	private MenuPanel menuPanel = null;
	private OverviewPanel overviewPanel = null;
	
	private JScrollPane localQueryPane = null;
	private ResultSummaryPanel resultsPanel = null;
	
	private Vector<JPanel> allResults = null;

	private QueryPanel queryPanel = null;
	
	private static int resultCounter = 0; 
	private static int nMultiAlignmentCounter = 0;
	private static final long serialVersionUID = 9349836385271744L;
}
