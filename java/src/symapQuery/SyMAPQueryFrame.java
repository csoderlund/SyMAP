package symapQuery;

/**************************************************
 * The main query frame
 */
import java.applet.Applet;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Vector;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;

import backend.UpdatePool;

import util.DatabaseReader;
import util.Utilities;

import util.ErrorReport;
import symap.pool.PoolManager;
import symap.pool.Pools;
import symap.projectmanager.common.Project;
import symapMultiAlign.AlignmentViewPanel;

public class SyMAPQueryFrame extends JFrame {
	private static final String [] MENU_ITEMS = { "> Instructions", "> Query Setup", "> Results" }; 
	public static final String [] LOCAL_RESULT_COLUMNS = { "Query", "Filters" };
	
	public SyMAPQueryFrame(DatabaseReader dr, boolean is3D) {
		this(null, dr, is3D);
	}

	public SyMAPQueryFrame(Applet applet, DatabaseReader dr, boolean is3D) {
		setTitle("SyMAP Query");
		theApplet = applet;
		theReader = dr;
		bIs3D = is3D;
		
		theProjects = new Vector<Project> ();		
		
		Rectangle screenRect = Utilities.getScreenBounds(applet,this);
		screenWidth  = Math.min(1024, screenRect.width);
		screenHeight = Math.min(1024, screenRect.height);
		setSize(screenWidth, screenHeight);	
	}
	
	public void addAlignmentTab(TableDataPanel parent, String [] names, String [] sequences) {
		String label = names.length + " sequences";
		
		final SyMAPQueryFrame theFrame = this;
		final TableDataPanel parentCopy = parent;
		final String [] theNames = names;
		final String [] theSequences = sequences;
		final String thePOGName = label;
		final String summary = "Summary";
		Thread thread = new Thread(new Runnable() {
			public void run() {
				try {
					String filename = "temp";
					
					AlignmentViewPanel newTab = new AlignmentViewPanel(theFrame, theNames, theSequences, filename);
					String tabName;
					tabName = "Align " + (++nMultiAlignmentCounter) + ": " + thePOGName;
						
					addResultPanel(parentCopy, newTab, tabName, summary);
				} catch (Exception e) {
					ErrorReport.print(e, "Align sequence");
				}
			}
		});
		thread.setPriority(Thread.MIN_PRIORITY);
		thread.start();
	}

	public String [] getDisplayNames() {
		String [] retVal = new String[theProjects.size()];
		
		for(int x=0; x<retVal.length; x++) {
			retVal[x] = theProjects.get(x).getDisplayName();
		}
		return retVal;
	}
	
	public void addProject(Project project) {
		if(!project.getType().equals("fpc"))
			theProjects.add(project);
	}
	
	public boolean hasProject(String strName, String strType) {
		for (Project p : theProjects)
			if (p.getDisplayName().equals(strName) && p.getType().equals(strType))
				return true;
		return false;
	}	

	public boolean addProject(String strName, String strType) {
		try {
			Project p = loadProject(strName, strType);
			if ( p != null ) {
				theProjects.add( p );
				return true;
			}
		}
		catch (Exception e) {ErrorReport.print(e, "Add project");}

		return false;
	}
	
	private Project loadProject(String strProjName, String strTypeName) throws SQLException
	{
	     int nProjIdx = -1;
	     String strDisplayName = null;
	
	     UpdatePool pool = new UpdatePool(theReader);
	     ResultSet rs = pool.executeQuery("SELECT p.idx, pp.value " +
	     		"FROM projects AS p " +
	     		"JOIN proj_props AS pp ON (p.idx=pp.proj_idx) " +
	     		"WHERE pp.name='display_name' " +
	     		"AND p.name='"+strProjName+"' AND p.type='"+strTypeName+"'");
	     
	     if ( rs.next() ) {
	     	nProjIdx = rs.getInt("p.idx");
	     	strDisplayName = rs.getString("pp.value");
	     }
	     rs.close();
	     
	     if (nProjIdx < 0) {
	     	System.err.println("Couldn't find project '"+strProjName+"' in database.");
	     	return null;
	     }
	     
	     Project p = new Project(nProjIdx, strProjName, strTypeName, strDisplayName);
	     p.loadGroups(pool);
	     return p;
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
	
	public void setToolTipTextForMenu(String label, String text) {
		menuPanel.setToolTipForMenu(label, text);
	}
	
	public DatabaseReader getDatabase() { return theReader; }
	public Applet getApplet() { return theApplet; }
	public Vector<Project> getProjects() { return theProjects; }
	
	// Query result
	public void addResult(TableDataPanel newPanel) {
		if(newPanel.isValidData()) {
			resultsPanel.addResult(newPanel.getSummary());
			results.add(newPanel);
			menuPanel.addResult(newPanel.getName()+":");
			mainPanel.add(newPanel);
			updateView();
		}
	}
	// Muscle alignment
	public void addResultPanel(JPanel parentPanel, JPanel newPanel, String name, String summary) {
		String [] row = new String[2];
		row[0] = name;
		row[1] = summary;
		
		resultsPanel.addResult(row);
		results.add(newPanel);
		menuPanel.addResult(name);
		mainPanel.add(newPanel);
		updateView();
	}
	
	public boolean updateResultCount(TableDataPanel panel) {
		int numResults = panel.getNumResults();
		
		if(numResults == 0)
			return menuPanel.updateResultLabel(panel.getName()+":", panel.getName() + ": No results");
		return menuPanel.updateResultLabel(panel.getName()+":", panel.getName() + ": " + panel.getNumResults());
	}
	
	public void removeResult(int result) {
		JPanel temp = results.get(result);
		
		mainPanel.remove(temp);
		results.remove(result);
		menuPanel.removeResult(result);
		updateView();
	}
	
	private void buildMenuPanel() {
		menuPanel = new MenuPanel(MENU_ITEMS, 0, new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				menuPanel.handleClick(arg0);
				updateView();
			}
		});
	}
	
	private void buildMainPanel() {
		mainPanel = new JPanel();
		mainPanel.setLayout( new BoxLayout(mainPanel, BoxLayout.PAGE_AXIS) );
		mainPanel.setBackground(Color.WHITE);

		results = new Vector<JPanel> ();
		
		overviewPanel = new OverviewPanel(this);
		queryPanel = new QueryPanel(this);
		resultsPanel = new ResultSummaryPanel(this, LOCAL_RESULT_COLUMNS);
		
		queryPanel.addExecuteListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				boolean [] oldSelections = null;

				for(int x=results.size()-1; x>=0 && oldSelections == null; x--) {
					if(results.get(x) instanceof TableDataPanel) {
						oldSelections = ((TableDataPanel)results.get(x)).getColumnSelections();
					}
				}
				addResult(new TableDataPanel(getThis(), getNextResultLabel(), oldSelections));
			}
		});
		
		mainPanel.add(overviewPanel);
		localQueryPane = new JScrollPane(queryPanel);
		mainPanel.add(localQueryPane);
		mainPanel.add(resultsPanel);
		
		updateView();
	}
	
	public String getNextResultLabel() { return "Result " + (++resultCounter); }
	
	private void updateView() {
		overviewPanel.setVisible(false);
		localQueryPane.setVisible(false);
		resultsPanel.setVisible(false);
		for(int x=0; x<results.size(); x++)
			results.get(x).setVisible(false);
		
		int selection = menuPanel.getCurrentSelection();
		switch(selection) {
			case 0: overviewPanel.setVisible(true);
			break;
			case 1: localQueryPane.setVisible(true);
			break;
			case 2: resultsPanel.setVisible(true);
			break;
			default:
				selection -= MENU_ITEMS.length;
				results.get(selection).setVisible(true);
		}
	}
	
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
	
	public SyMAPQueryFrame getThis() { return this; }
	public QueryPanel getQueryPanel() {return queryPanel;}
		
	private Applet theApplet = null;
	private DatabaseReader theReader = null;
	private Vector<Project> theProjects = null;
	
	private int screenWidth, screenHeight;
	private JSplitPane splitPane = null;
	private JPanel mainPanel = null;
	private MenuPanel menuPanel = null;
	private OverviewPanel overviewPanel = null;
	
	private JScrollPane localQueryPane = null;
	private ResultSummaryPanel resultsPanel = null;
	
	private Vector<JPanel> results = null;
	private boolean bIs3D = false;
	
	private QueryPanel queryPanel = null;
	
	private static int resultCounter = 0; 
	private static int nMultiAlignmentCounter = 0;
	private static final long serialVersionUID = 9349836385271744L;
}
