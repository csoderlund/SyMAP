package symap.projectmanager.common;

/*****************************************************
 * The main frame and provides all the high level functions for it.
 */
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.Vector;
import java.util.Comparator;
import java.util.Date;
import java.util.Set;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;

import java.awt.*;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.table.*;
import javax.swing.event.*;

import backend.*;
import dotplot.DotPlotFrame;
import symap.SyMAP;
import symap.SyMAPConstants;
import symap.pool.DatabaseUser;
import util.Cancelled;
import util.ErrorCount;
import util.DatabaseReader;
import util.ErrorReport;
import util.Utilities;
import util.ProgressDialog;
import util.Logger;
import util.LinkLabel;
import util.PropertiesReader;

import blockview.*;
import circview.*;

import symap.projectmanager.common.PropertyFrame;
import symapQuery.SyMAPQueryFrame;
import symap.projectmanager.common.Project;


@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class ProjectManagerFrameCommon extends JFrame implements ComponentListener {
	// can be set in symap3D and symapCE
	public static boolean inReadOnlyMode = false;
	public static String  MAIN_PARAMS = Constants.cfgFile; 
	public static int     maxCPUs = -1;
	public static boolean printStats=false; 
	public static boolean lgProj1st = false; // false sort on name; true sort on length
	
	private static final String HTML = "/html/ProjMgrInstruct.html";
	private static final String WINDOW_TITLE = "SyMAP " + SyMAP.VERSION + " Project Manager";
	private final String DB_ERROR_MSG = "A database error occurred, please see the Troubleshooting Guide at:\n" + SyMAP.TROUBLE_GUIDE_URL + "#database_error";	
	private final String DATA_PATH = Constants.dataDir;
	
	private final int MIN_WIDTH = 900;
	private final int MIN_HEIGHT = 600;
	private final int MIN_CWIDTH = 900;
	private final int MIN_CHEIGHT = 1000;

	private final int MIN_DIVIDER_LOC = 240;
	
	// If changed - don't make one a substring of another!!
	private final String TBL_DONE = "\u2713";
	private final String TBL_ADONE = "A";
	private final String TBL_QDONE = "?";
	private final String TBL_NA = "n/a";
	
	protected DatabaseReader dbReader = null;
	protected Vector<Project> selectedProjects = null;
	protected ActionListener explorerListener = null;
	
	private Vector<Project> projects;
	private HashMap<Integer,Vector<Integer>> projPairs = null; // project idx mapping from database
	private JButton btnShowChrExp, btnShowDotplot, btnAllDotplot, btnDoAll, btnClearPair,
		btnShowBlockView, btnShowCircView, btnQueryView, btnShowSummary;
	private JSplitPane splitPane;
	private JComponent instructionsPanel;
	private JTable alignmentTable;
	private JButton btnAddProject = null;
	private JButton btnDoAlign;
	private boolean isAlignmentAvail = (Utilities.isLinux() || Utilities.isMac());
	private PropertiesReader mProps;
	private Project curProj = null; // used only in the load-all-projects function
	
	private AddProjectPanel addProjectPanel = null;
	private SyProps globalPairProps = null;     // values from Parameter window
	private PairPropertyFrame ppFrame = null; // only !null if opened
	private JTextField txtCPUs =null;
	private int totalCPUs=0;
	
	public ProjectManagerFrameCommon( ) {
		super(WINDOW_TITLE);
		SyMAP.printVersion();
		
        // Add window handler to kill mysqld on clean exit
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			public void windowClosed(WindowEvent e) {
				shutdown();
				System.exit(0); // sometimes this is needed
			}
		});
		
		// Add shutdown handler to kill mysqld on CTRL-C
        Runtime.getRuntime().addShutdownHook( new MyShutdown() );
		
		initialize();
		
		instructionsPanel = createInstructionsPanel();
		
		splitPane = new JSplitPane( JSplitPane.HORIZONTAL_SPLIT,
									createProjectPanel(), instructionsPanel );
		splitPane.setDividerLocation(MIN_DIVIDER_LOC);
		
		add(splitPane);
		
		setSize(MIN_WIDTH, MIN_HEIGHT);
		
		addComponentListener(this); // hack to enforce minimum frame size
	}
	
	private DatabaseReader getDatabaseReader() throws Exception {
		String paramsfile = MAIN_PARAMS;
		
		if (Utilities.fileExists(paramsfile)) {
			System.out.println("Reading from file " + paramsfile);
		}
		else {
			ErrorReport.die("Configuration file not available: " + paramsfile);
		}
		mProps = new PropertiesReader(new File(paramsfile));
		
		String db_server       = mProps.getProperty("db_server");
		String db_name         = mProps.getProperty("db_name");
		String db_adminuser    = mProps.getProperty("db_adminuser"); // note: user needs write access
		String db_adminpasswd  = mProps.getProperty("db_adminpasswd");
		String db_clientuser   = mProps.getProperty("db_clientuser");
		String db_clientpasswd = mProps.getProperty("db_clientpasswd");
		String cpus 			  = 	mProps.getProperty("nCPUs"); // CAS500

		if (db_server != null) 			db_server = db_server.trim();
		if (db_name != null) 			db_name = db_name.trim();
		if (db_adminuser != null) 		db_adminuser = db_adminuser.trim();
		if (db_adminpasswd != null) 		db_adminpasswd = db_adminpasswd.trim();
		if (db_clientuser != null) 		db_clientuser = db_clientuser.trim();
		if (db_clientpasswd != null) 	db_clientpasswd = db_clientpasswd.trim();

		if (inReadOnlyMode) { // CAS500 crashed if config file not present
			if (db_clientuser==null || db_clientuser.length()==0) {
				if (db_adminuser==null || db_adminuser.length()==0) 
					ErrorReport.die("No db_adminuser or db_clientuser in " + paramsfile);
				else db_clientuser = db_adminuser;
			}
			if (db_clientpasswd==null || db_clientpasswd.length()==0) {
				if (db_adminpasswd==null || db_adminpasswd.length()==0) 
					ErrorReport.die("No db_adminpasswd or db_clientpasswd in " + paramsfile);
				else db_clientpasswd = db_adminpasswd;
			}
		}
		else {
			if (db_adminuser==null || db_adminuser.length()==0) 
				ErrorReport.die("No db_adminuser defined in " + paramsfile);
			if (db_adminpasswd==null || db_adminpasswd.length()==0) 
				ErrorReport.die("No db_adminpasswd defined in " + paramsfile);
		}
		
		if (db_server == null) 	db_server = "";
		
		if (cpus!=null && maxCPUs<=0) {
			try {
				maxCPUs = Integer.parseInt(cpus);
				totalCPUs = Runtime.getRuntime().availableProcessors();
				System.out.println("Max CPUs " + maxCPUs + " (out of " + totalCPUs + " available)");
			}
			catch (Exception e){ System.err.println("nCPUs: " + cpus + " is not an integer. Ignoring.");}
		}
	
		DatabaseUser.checkDBRunning(db_server);
		
		// Check db params and set defaults
		if (Utilities.isStringEmpty(db_adminuser))	db_adminuser = "admin";
		if (Utilities.isStringEmpty(db_clientuser))	db_clientuser = "admin";
		if (Utilities.isStringEmpty(db_name))		db_name = "symap";
		setTitle(WINDOW_TITLE + " - Database: " + db_name);
		
		System.out.println("SyMAP database " + db_name);
		
		// Open database connection and create database if it doesn't exist
        Class.forName("com.mysql.jdbc.Driver");
        DatabaseUser.shutdown(); // in case shutdown at exit didn't work/happen
        if (!inReadOnlyMode) {
	        	if (!DatabaseUser.createDatabase(db_server, db_name, db_adminuser, db_adminpasswd)) {
	        		System.err.println(DB_ERROR_MSG);
	        		System.exit(-1);
	        	}
        }
        return DatabaseUser.getDatabaseReader(SyMAPConstants.DB_CONNECTION_PROJMAN,
        		DatabaseUser.getDatabaseURL(db_server, db_name),
        		(inReadOnlyMode ? db_clientuser : db_adminuser),
        		(inReadOnlyMode ? db_clientpasswd : db_adminpasswd), null);
	}
	
	private void initialize() {
		try {
			if (dbReader == null) dbReader = getDatabaseReader();
			
			Utilities.setResClass(this.getClass());
			Utilities.setHelpParentFrame(this);
			
			projPairs = loadProjectPairsFromDB();
			projects = loadProjectsFromDB();
			
			if (globalPairProps==null) {
				globalPairProps = new SyProps();
			}
			
			if (!inReadOnlyMode) { // CAS500
				String strDataPath = DATA_PATH;
				if (!Utilities.dirExists(strDataPath)) 
					System.out.println("Creating /data directories for synteny computation");
				
				if (Utilities.dirExists(strDataPath + "/pseudo") &&
					!Utilities.dirExists(Constants.seqDataDir)) {
					
					System.err.println("+++ /data/pseudo is v4.2, whereas data/seq is v5");
					System.err.println("    v4.2 file organization does not work with v5");
					System.err.println("    See SystemGuide.html for v5 file organization.");
					int ret = JOptionPane.showConfirmDialog(null, "This appears to use v4.2 /data directory structure,\n " +
							"which does not work with v5; Continue? ",
							"Confirm",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE);	
					if (ret == JOptionPane.NO_OPTION) {
						System.exit(-1);
					}
				}
				
				Utilities.checkCreateDir(Constants.logDir,     "PM log");
				Utilities.checkCreateDir(strDataPath, 		  "PM data");
				Utilities.checkCreateDir(Constants.seqDataDir, "PM data");
				Utilities.checkCreateDir(Constants.fpcDataDir, "PM data");			
				Utilities.checkCreateDir(Constants.seqRunDir,  "PM data");
				Utilities.checkCreateDir(Constants.fpcRunDir,  "PM data");
			
				loadProjectsFromDisk(projects, strDataPath,  Constants.seqType);
				loadProjectsFromDisk(projects, strDataPath,  Constants.fpcType);
			}
			
			if (selectedProjects == null)
				selectedProjects = new Vector<Project>();
			else { // refresh already selected projects
				Vector<Project> newSelectedProjects = new Vector<Project>();
				for (Project p1 : selectedProjects) {
					for (Project p2 : projects) {
						if ( p1.equals(p2) ) {
							newSelectedProjects.add(p2);
							break;
						}
					}
				}
				selectedProjects = newSelectedProjects;
			}
		}
		catch (Exception e) { ErrorReport.die(e, DB_ERROR_MSG);}
	}

	private static boolean isShutdown = false;
	private synchronized void shutdown() {
		if (!isShutdown) {
			DatabaseUser.shutdown(); // Shutdown mysqld
			isShutdown = true;
		}
	}
	
	private class MyShutdown extends Thread { // runs at program exit
	    public void run() {
	    		shutdown();
	    }
	}
	
	private JComponent createInstructionsPanel() {
		StringBuffer sb = new StringBuffer();
		try
		{
			InputStream str = this.getClass().getResourceAsStream(HTML);
			
			int ci = str.read();
			while (ci != -1)
			{
				sb.append((char)ci);
				ci = str.read();
			}
		}
		catch(Exception e){ErrorReport.print(e, "Show instructions");}
		
		JEditorPane editorPane = new JEditorPane();
		editorPane.setEditable(false);
		editorPane.setBackground(getBackground());
		editorPane.setContentType("text/html");
		editorPane.setText(sb.toString());

		JScrollPane scrollPane = new JScrollPane(editorPane);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
		scrollPane.setBackground(getBackground());
		
		editorPane.addHyperlinkListener(new HyperlinkListener() {
			public void hyperlinkUpdate(HyperlinkEvent e) {
				if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
					if ( !Utilities.tryOpenURL(null,e.getURL()) )
						System.err.println("Error opening URL: " + e.getURL().toString());
				}
			}
		});
		
		return scrollPane;
	}
	
	private JComponent createAlignmentTable() {
		// Clear previous table, disable listeners
		if (alignmentTable != null) {
			alignmentTable.getSelectionModel().removeListSelectionListener(tableRowListener);
			alignmentTable.getColumnModel().removeColumnModelListener( tableColumnListener );
			alignmentTable = null; 
		}
		// Create table contents
		sortProjects(selectedProjects); // redundant?
		Vector<String> columnNames = new Vector<String>();
		Vector<Vector<String>> rowData = new Vector<Vector<String>>();

		int maxProjName = 0;
		for (Project p1 : selectedProjects) {
			maxProjName = Math.max(p1.getDisplayName().length(), maxProjName );
		}
		String blank = "";
		for(int q = 1; q <= maxProjName + 20; q++) blank += " ";
		
		columnNames.add(blank);
		for (Project p1 : selectedProjects) {
			if (p1.getStatus() == Project.STATUS_ON_DISK) continue; // skip if not in DB
			
			int id1 = p1.getID();
			columnNames.add( p1.getDisplayName() );
			Vector<String> row = new Vector<String>();
			row.add( p1.getDisplayName() );
		
			for (Project p2 : selectedProjects) {
				if (p2.getStatus() == Project.STATUS_ON_DISK) continue; // skip if not in DB		
				int id2 = p2.getID();
				
				Project[] ordP = orderProjects(p1,p2);
				String resultDir = Constants.getNameAlignDir(ordP[0].getDBName(), ordP[0].isFPC(), ordP[1].getDBName());
				
				if ((projPairs.containsKey(id1) && projPairs.get(id1).contains(id2)) ||
					(projPairs.containsKey(id2) && projPairs.get(id2).contains(id1)))
				{
					row.add(TBL_DONE); 
				}
				else if (p1.isFPC() && p2.isFPC() )
				{
					row.add(TBL_NA);	
				}
				else if (Utils.checkDoneFile(resultDir))
				{					
					row.add(TBL_ADONE); 
				}
				else if (Utils.checkDoneMaybe(resultDir, ordP[0].isFPC())>0)
				{
					row.add( TBL_QDONE); 
				}
				else
					row.add(null);
			}
			rowData.add( row );
		}
		
		if (rowData.size() == 0)
			return null;
		
		// Make table
		alignmentTable = new MyTable(rowData, columnNames);
		alignmentTable.setGridColor(Color.BLACK);
		alignmentTable.setShowHorizontalLines(true);
		alignmentTable.setShowVerticalLines(true);
		alignmentTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		alignmentTable.setSelectionBackground(Color.YELLOW);
		alignmentTable.setSelectionForeground(Color.BLACK);
		alignmentTable.setCellSelectionEnabled( true );
		alignmentTable.setModel( new ReadOnlyTableModel(rowData, columnNames) );
		DefaultTableCellRenderer tcr = new MyTableCellRenderer();
		tcr.setHorizontalAlignment(JLabel.CENTER);
		alignmentTable.setDefaultRenderer( Object.class, tcr);
	    TableColumn col = alignmentTable.getColumnModel().getColumn(0);
		DefaultTableCellRenderer tcr2 = new MyTableCellRenderer();	  
		tcr2.setHorizontalAlignment(JLabel.LEFT);
	    col.setCellRenderer(tcr2);

		alignmentTable.getSelectionModel().addListSelectionListener( tableRowListener );
		alignmentTable.getColumnModel().addColumnModelListener( tableColumnListener );
		alignmentTable.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
			}
		});
		autofitColumns(alignmentTable);
		// A scroll pane is needed or the column names aren't shown
		JScrollPane scroller = new JScrollPane( alignmentTable, 
				JScrollPane.VERTICAL_SCROLLBAR_NEVER,
				JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroller.setAlignmentX(Component.LEFT_ALIGNMENT);
		Dimension tableSize = new Dimension(
				(int)alignmentTable.getPreferredSize().getWidth(),
				(int)alignmentTable.getPreferredSize().getHeight() 
					+ (int)alignmentTable.getTableHeader().getPreferredSize().getHeight() + 2);
		scroller.setPreferredSize( tableSize );
		scroller.setMaximumSize( tableSize );
		scroller.setMinimumSize( tableSize );
		scroller.setBorder( null );
		
		return scroller;
	}
	public void autofitColumns(JTable tbl) 
	{
        TableModel model = tbl.getModel();
        TableColumn column;
        Component comp;
        int headerWidth;
        int cellWidth;
        TableCellRenderer headerRenderer = tbl.getTableHeader().getDefaultRenderer();
       
        for (int i = 0;  i < tbl.getModel().getColumnCount();  i++) { // for each column
            column = tbl.getColumnModel().getColumn(i);

            comp = headerRenderer.getTableCellRendererComponent(
                                 tbl, column.getHeaderValue(),
                                 false, false, 0, i);
           
            headerWidth = comp.getPreferredSize().width + 10;
            
            cellWidth = 0;
            for (int j = 0;  j < tbl.getModel().getRowCount();  j++) { // for each row
                comp = tbl.getDefaultRenderer(model.getColumnClass(i)).getTableCellRendererComponent(
                                     tbl, model.getValueAt(j, i),
                                     false, false, j, i);
                cellWidth = Math.max(cellWidth, comp.getPreferredSize().width);
                //Strings need to be adjusted
                if(model.getColumnClass(i) == String.class)
                    cellWidth += 5;
                if (j > 100) break; // only check beginning rows, for performance reasons
            }

            column.setPreferredWidth(Math.min(Math.max(headerWidth, cellWidth), 200));
        }
    }	
	private int getNumCompletedAlignments() {
		int count = 0;
		
		for (int row = 0;  row < alignmentTable.getRowCount();  row++) {
			for (int col = 0;  col < alignmentTable.getColumnCount();  col++) {
				String val = (String)alignmentTable.getValueAt(row, col);
				if (val != null && val.contains(TBL_DONE))
					count++;
			}
		}	
		return count;
	}
	
	private Project getProjectByDisplayName( String strName ) {
		for (Project p : projects)
			if (p.getDisplayName().equals( strName ))
				return p;
		return null;
	}
	/***********************************************************
	 * enter status of pair (align done or synteny done or none)
	 */
	private void updateSelectionLabel() {
		Project[] projects = getSelectedAlignmentProjects();
		if (projects == null) {
			if (alignmentTable != null) {
				btnDoAlign.setVisible(true);
				btnClearPair.setVisible(true);
				btnShowDotplot.setVisible(true); 
			}
			return;
		}
		
		Utilities.setCursorBusy(this, true);
		
		String val = getSelectedAlignmentValue();
		
		if (val == null) {
			btnDoAlign.setText("Selected Pair");
		}
		else if (val.contains(TBL_DONE)) {
			btnDoAlign.setText("Selected Pair (Re-do)");	
		}
		else {
			btnDoAlign.setText("Selected Pair");	
		}
		boolean bothFPC = (projects[0].isFPC() && projects[1].isFPC());
		boolean alignExists = (val == null || !val.contains(TBL_ADONE) || !val.contains(TBL_QDONE));
		boolean noAlign = (!isAlignmentAvail && !alignExists);
		boolean allDone = (val != null && val.contains(TBL_DONE));
		
		btnDoAlign.setEnabled( !bothFPC && !noAlign );
		btnClearPair.setEnabled(allDone || alignExists);
		btnShowDotplot.setEnabled( allDone ); // enable only if already fully aligned
		btnShowCircView.setEnabled(allDone);  //&& !oneUnord); // enable only if already fully aligned
		btnShowSummary.setEnabled(allDone);   //CAS500 !isSelf && !oneUnord); // enable only if already fully aligned
		btnShowBlockView.setEnabled(allDone); // enable only if already fully aligned
		
		Utilities.setCursorBusy(this, false);
	}
	
	// *** Begin table customizations ******************************************
	
	private class MyTable extends JTable {
		public MyTable(Vector rowData, Vector columnNames) {
			super(rowData, columnNames);
		}
		
		public void changeSelection(int rowIndex, int columnIndex, boolean toggle, boolean extend) {
			if (columnIndex > 0 && columnIndex <= rowIndex + 1)
				super.changeSelection(rowIndex, columnIndex, toggle, extend);
		}
	}
	
	private ListSelectionListener tableRowListener = new ListSelectionListener() { // called before tableColumnListener
		public void valueChanged(ListSelectionEvent e) {
			if (!e.getValueIsAdjusting())
				updateSelectionLabel();
		}
	};
	
	private TableColumnModelListener tableColumnListener = new TableColumnModelListener() { // called after tableRowListener
		public void columnAdded(TableColumnModelEvent e) { }
		public void columnMarginChanged(ChangeEvent e) { }
		public void columnMoved(TableColumnModelEvent e) { }
		public void columnRemoved(TableColumnModelEvent e) { }
		public void columnSelectionChanged(ListSelectionEvent e) {
			if (!e.getValueIsAdjusting())
				updateSelectionLabel();
		}
		
	};
	
	private class ReadOnlyTableModel extends DefaultTableModel {
		public ReadOnlyTableModel(Vector<Vector<String>> rowData, Vector<String> columnNames)  { super(rowData, columnNames); }
		public boolean isCellEditable(int row, int column) { return false; }
	}
	
	// From http://www.chka.de/swing/table/faq.html
	private class MyTableCellRenderer extends DefaultTableCellRenderer {
		public Component getTableCellRendererComponent(JTable table, 
				Object value, boolean isSelected, boolean hasFocus, 
				int row, int column)
		{
			super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			
			if (column == 0 )
			{
				setBackground( UIManager.getColor("TableHeader.background") );
			}
			else if (column > row + 1)
			{
				// CAS42 1/7/18 can't tell this from white setBackground(new Color(245,245,245));
				setBackground(new Color(217,217,217));
			}
			else if (!isSelected)
				setBackground(null);		
			return this;
		}
	}
	// *** End table customizations ********************************************
	
	private JTextArea createTextArea(String text) {
		JTextArea textArea = new JTextArea(text);
		
		textArea.setAlignmentX(Component.LEFT_ALIGNMENT);
		textArea.setBackground( getBackground() );
		textArea.setEditable(false);
		textArea.setLineWrap(true);
		textArea.setWrapStyleWord(true);
		
		return textArea;
	}

	private JLabel createLabel(String text, int fontStyle, int fontSize) {
		JLabel label = new JLabel(text);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setFont( new Font(label.getFont().getName(), fontStyle, fontSize) );
		return label;
	}
	
	private Font createPlainFont(Font basis) {
		return new Font(basis.getName(), Font.PLAIN, basis.getSize());
	}
	
	private JPanel createSummaryPanel() {
		// Make enclosing panel
		JLabel lblTitle = createLabel("Summary", Font.BOLD, 18);

		JButton btnHelp = new JButton("Help");
		btnHelp.setVisible(true);
		btnHelp.setEnabled(true);
		btnHelp.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				showHelp();
			}
		} );

		JPanel mainPanel = new JPanel();
		mainPanel.setLayout( new BoxLayout ( mainPanel, BoxLayout.Y_AXIS ) );
		mainPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 0));
		mainPanel.add( createHorizPanel( new Component[] { lblTitle,btnHelp},60));
		mainPanel.add( new JSeparator() );
		
		// Add individual project summaries
		JPanel subPanel = new JPanel();
		subPanel.setLayout( new BoxLayout ( subPanel, BoxLayout.Y_AXIS ) );
		subPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		subPanel.add( Box.createVerticalStrut(5) );
		
		int nUnloaded = 0;
		for (Project p : selectedProjects) {
			if (p.getStatus() == Project.STATUS_ON_DISK) {
				nUnloaded++;
			}
		}
		if (nUnloaded > 0)
		{
			LinkLabel  btnLoadAllProj = new LinkLabel("Load All Projects", Color.blue, Color.blue.darker());
			btnLoadAllProj.addMouseListener( doLoadAllProj );
			subPanel.add(btnLoadAllProj );
			subPanel.add( Box.createVerticalStrut(10) );			
		}
		for (Project p : selectedProjects) {
			lblTitle = createLabel( p.getDisplayName(), Font.BOLD, 15 );
			
			JPanel textPanel = new JPanel();
			textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.PAGE_AXIS));
			textPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
			
			// CAS500 re-ordered and add Length and Mask; CAS504 add notLoadedInfo
			// line 1
			String label1 = "Project: " + p.getDBName(); // CAS500 was Database Name
			
			boolean isFPC = p.getType().equals(Constants.fpcType);
			if (isFPC) label1 += "    FPC Map";
				
			String o = p.getOrderAgainst();
			if (o != null && o.length() > 0) label1 += "    Order against: " + o;
			
			if (p.getIsMasked()) label1 += "    Mask all but genes";
			textPanel.add(new JLabel(label1)); 
			
			// line 2
			String d = p.getDescription();
			if(d != null && d.length()>0) 
				textPanel.add(new JLabel("Description: " + d));
			
			// rest of lines
			if(p.getStatus() == Project.STATUS_ON_DISK) { 
				String x = p.notLoadedInfo(); 
				textPanel.add(new JLabel(x));	
			} 
			else {
				String chrLbl = (p.chrLabel.endsWith("s")) ? 
						p.chrLabel() + "s: " :  p.chrLabel() + ": "; 
				
				if (isFPC)
					textPanel.add(new JLabel(chrLbl + p.getNumGroups()));	
				else {
					String label2 = 
						String.format("%s%d    Total: %,d    Annotations: %,d", 
								chrLbl, p.getNumGroups(), p.length,p.numAnnot);
					
					textPanel.add(new JLabel(label2));
				}
			}
			textPanel.setMaximumSize(textPanel.getPreferredSize());

			ProjectLinkLabel btnRemoveFromDisk = null;
			ProjectLinkLabel btnLoadOrRemove = null;
			ProjectLinkLabel btnReloadAnnot = null;
			ProjectLinkLabel btnReloadParams = null;
			ProjectLinkLabel btnReloadSeq = null;
			if (p.getStatus() == Project.STATUS_ON_DISK) {
				btnRemoveFromDisk = new ProjectLinkLabel("Remove project from disk", p, Color.red);
				btnRemoveFromDisk.addMouseListener( doRemoveDisk );

				btnLoadOrRemove = new ProjectLinkLabel("Load project", p, Color.red);
				btnLoadOrRemove.addMouseListener( doLoad );

				btnReloadParams = new ProjectLinkLabel("Parameters", p, Color.blue);
				btnReloadParams.addMouseListener( doSetParamsNotLoaded ); 
			}
			else { // CAS42 1/1/18 was just "Remove" which is unclear
				btnLoadOrRemove = new ProjectLinkLabel("Remove project from database", p, Color.blue);
				btnLoadOrRemove.addMouseListener( doRemove );

				btnReloadParams = new ProjectLinkLabel("Parameters", p, Color.blue);
				btnReloadParams.addMouseListener( doReloadParams );

				btnReloadSeq = new ProjectLinkLabel("Reload project", p, Color.blue);
				btnReloadSeq.addMouseListener( doReloadSeq );
				
				if (p.isPseudo()) {
					btnReloadAnnot = new ProjectLinkLabel("Reload annotation", p, Color.blue);
					btnReloadAnnot.addMouseListener( doReloadAnnot );
				}
			}
			if (p.getStatus() == Project.STATUS_ON_DISK) {
				if (!inReadOnlyMode) // CAS500
					subPanel.add( createHorizPanel( new Component[] 
						{ lblTitle, btnRemoveFromDisk, btnLoadOrRemove, btnReloadParams }, 15 ) );				
			}
			else {
				if (!inReadOnlyMode) { 
					if (p.isPseudo()) {
						subPanel.add( createHorizPanel( new Component[] 
							{ lblTitle, btnLoadOrRemove, btnReloadSeq,btnReloadAnnot, btnReloadParams}, 15 ) );						
					}
					else {
						subPanel.add( createHorizPanel( new Component[] 
							{ lblTitle, btnLoadOrRemove,btnReloadAnnot, btnReloadParams}, 15 ) );											
					}
				}
				else { // CAS500
					subPanel.add( createHorizPanel( new Component[] { lblTitle}, 15));
				}
			}
			subPanel.add( textPanel );
			subPanel.add( Box.createVerticalStrut(10) );
		}
		
		subPanel.setMaximumSize( subPanel.getPreferredSize() );
		JScrollPane subScroller = new JScrollPane(subPanel, 
				JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, 
				JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		subScroller.setAlignmentX(Component.LEFT_ALIGNMENT);
		subScroller.getVerticalScrollBar().setUnitIncrement(10);
		subScroller.setBorder(null);
		subScroller.setMaximumSize( new Dimension( MIN_WIDTH, 280 ) );
		mainPanel.add( subScroller );
		
		// Add alignment table
		JComponent tableScroll = createAlignmentTable();
		if (tableScroll != null) {
			lblTitle = createLabel("Available Syntenies", Font.BOLD, 16);
			
			JTextArea text1 = new JTextArea(
					"Table Legend:\n"
					+ TBL_DONE + " : synteny has been computed, ready to view.\n"
					+ TBL_ADONE + " : alignment is done, synteny needs to be computed.\n"
					+ "n/a : the projects cannot be aligned."
					,4,1);
			text1.setBackground( getBackground() );
			text1.setEditable(false);
			text1.setLineWrap(false);
			text1.setAlignmentX(Component.LEFT_ALIGNMENT);
			
			// CAS42 1/5/18
			JTextArea text2 = new JTextArea("Click a lower diagonal white table cell\nto select the project pair.",2,1);
			text2.setBackground( getBackground() );
			text2.setEditable(false);
			text2.setLineWrap(false);
			text2.setAlignmentX(Component.LEFT_ALIGNMENT);
			text2.setForeground(Color.BLUE);
		
			btnDoAlign = new JButton("Selected Pair");
			btnDoAlign.setVisible(true);
			btnDoAlign.setEnabled(false);
			btnDoAlign.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					Project[] projects = getSelectedAlignmentProjects();
					promptAndProgressAlignProjects(projects[0], projects[1], false);
				}
			} );
			
			btnClearPair = new JButton("Clear Pair");
			btnClearPair.setVisible(true);
			btnClearPair.setEnabled(false);
			btnClearPair.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					Project[] projects = getSelectedAlignmentProjects();
					progressClearPair(projects[0], projects[1]);
				}
			} );	
			boolean canShow3DButton =    (getNumCompletedAlignments() > 0);
			boolean canShowAllDPButton = (getNumCompletedAlignments() >= 1 && selectedProjects.size() >= 2);
			boolean canShowDoAll =       (selectedProjects.size() > 1); // CAS500 was >=1 
			
			btnShowChrExp = new JButton("Chromosome Explorer");
			btnShowChrExp.setVisible(true); 
			btnShowChrExp.setEnabled( canShow3DButton );
			btnShowChrExp.addActionListener( explorerListener);
			
			btnShowDotplot = new JButton("Dot Plot");
			btnShowDotplot.setVisible(true);
			btnShowDotplot.setEnabled(false);
			btnShowDotplot.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent e) { 
					showDotplot();
				}
			});

			btnShowBlockView = new JButton("Block View");
			btnShowBlockView.setVisible(true);
			btnShowBlockView.setEnabled(false);
			btnShowBlockView.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent e) { 
					showBlockView();
				}
			});

			btnShowCircView = new JButton("Circle View");
			btnShowCircView.setVisible(true);
			btnShowCircView.setEnabled(false);
			btnShowCircView.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent e) { 
					showCircleView();
				}
			});
			
			btnShowSummary = new JButton("Summary");
			btnShowSummary.setVisible(true);
			btnShowSummary.setEnabled(false);
			btnShowSummary.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent e) { 
					showSummary();
				}
			});
		
			btnAllDotplot = new JButton("Dot Plot");
			btnAllDotplot.setVisible(canShowAllDPButton);
			btnAllDotplot.setEnabled(canShowAllDPButton);
			btnAllDotplot.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent e) { 
					showAllDotPlot();
				}
			});

			btnDoAll = new JButton("All Pairs");
			btnDoAll.setVisible(canShowDoAll);
			btnDoAll.setEnabled(canShowDoAll);
			btnDoAll.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent e) { 
					doAll(false);
				}
			});

			btnQueryView = new JButton("SyMAP Queries");
			btnQueryView.setEnabled(false);
			btnQueryView.setVisible(true);
			btnQueryView.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					showQuery();
				}
			});

			JPanel titleText = new JPanel();
			titleText.setLayout(new BoxLayout ( titleText, BoxLayout.Y_AXIS ) );
			titleText.add(lblTitle);
			titleText.add( Box.createRigidArea(new Dimension(0,5)) );
			titleText.add(text2);
			JPanel instructText = new JPanel();
			instructText.setLayout(new BoxLayout ( instructText, BoxLayout.X_AXIS ) );
			instructText.setAlignmentX(Component.LEFT_ALIGNMENT);
			instructText.add(titleText);
			instructText.add(Box.createRigidArea(new Dimension(20,0)));
			instructText.add(new JSeparator(SwingConstants.VERTICAL)); //
			instructText.add(Box.createRigidArea(new Dimension(20,0)));
			instructText.add(text1);
			instructText.add(Box.createHorizontalGlue());
			instructText.setMaximumSize(instructText.getPreferredSize());
			
			JLabel lbl1 = new JLabel("Alignment & Synteny:");
			JLabel lbl2 = new JLabel("Display for Selected Pair:");
			JLabel lbl3 = new JLabel("Display for All Projects:");
			JLabel lbl4 = new JLabel("Queries:");
			
			totalCPUs = Runtime.getRuntime().availableProcessors(); // CAS42 12/6/17
			if (maxCPUs<=0) 
				maxCPUs = totalCPUs;
			if (txtCPUs != null)
			{
				try{
					maxCPUs = Integer.parseInt(txtCPUs.getText());
				}
				catch (Exception e){
					System.out.println("Error: CPUs must be an integer");
					maxCPUs = 1;
				}
			}
			
			txtCPUs = new JTextField(2);
			txtCPUs.setMaximumSize(txtCPUs.getPreferredSize());
			txtCPUs.setMinimumSize(txtCPUs.getPreferredSize());

			txtCPUs.setText("" + maxCPUs);

			mainPanel.add( new JSeparator() );
			mainPanel.add( Box.createVerticalStrut(15) );
			mainPanel.add(instructText); 
			mainPanel.add( Box.createVerticalStrut(15) );
			if (!inReadOnlyMode) {
				mainPanel.add( createHorizPanel( new Component[] 
					{ tableScroll, new JLabel(" "),new JLabel("CPUs:"), txtCPUs},5 ));			
			}
			else {	
				mainPanel.add( createHorizPanel( new Component[] 
						{ tableScroll, new JLabel(" ")}, 5 ));			
			}
			JButton btnPairParams = new JButton("Parameters");
			btnPairParams.addActionListener(showPairProps);
			
			if (!inReadOnlyMode)
			{
				mainPanel.add( Box.createRigidArea(new Dimension(0,15)) );	
				mainPanel.add( createHorizPanel( new Component[] { lbl1,
						btnDoAlign, btnClearPair, btnDoAll, btnPairParams}, 5) );
			}
			mainPanel.add( Box.createRigidArea(new Dimension(0,15))  );
			mainPanel.add( createHorizPanel( new Component[] {lbl2, btnShowDotplot, btnShowBlockView, btnShowCircView, btnShowSummary }, 5) );

			mainPanel.add( Box.createRigidArea(new Dimension(0,15))  );
			mainPanel.add( createHorizPanel( new Component[] {lbl3, btnShowChrExp, btnAllDotplot }, 5) );

			mainPanel.add( Box.createRigidArea(new Dimension(0,15))  );
			mainPanel.add( createHorizPanel( new Component[] {lbl4, btnQueryView }, 5) );
		} else {
			mainPanel.add( Box.createVerticalGlue() );
			mainPanel.add( createTextArea("") ); // kludge to fix layout problem			
		}
				
		updateSelectionLabel();
		
		return mainPanel;
	}
	
	
	private ActionListener showPairProps = new ActionListener() {
		public void actionPerformed(ActionEvent arg0) 
		{
			SyProps prevProps = null;
			Project[] sel = getSelectedAlignmentProjects();
			Project p1=null, p2=null;
			if (sel != null) {
				try {
					p1 = sel[0]; p2 = sel[1];
					prevProps = getPairPropsFromDB(p1.getID(), p2.getID()); // return null if none
				}
				catch (Exception e){ErrorReport.print(e, "Show pair properties");}
			}
			else prevProps = globalPairProps; 
			
			ppFrame = new PairPropertyFrame(globalPairProps, prevProps, p1, p2, getInstance());
			ppFrame.setVisible(true);
		}
	};
	
	ActionListener newProjListener = new ActionListener() {
		public void actionPerformed(ActionEvent e) {
			showNewProjectHelp();
		}
	};
	private void showNewProjectHelp()
	{
		Utilities.showHTMLPage(addProjectPanel,"New Project Help", "/html/NewProjHelp.html");	
	}
	private void showHelp() 
	{
		Utilities.showHTMLPage(null,"Project Manager Help", "/html/ProjManagerHelp.html");

	}		

	private MouseListener doLoad = new MouseAdapter() {
		public void mouseClicked(MouseEvent e) {
			progressLoadProject( ((ProjectLinkLabel)e.getSource()).getProject() );
		}
	};
	
	private void progressLoadProject(final Project project) {
		ErrorCount.init();
		FileWriter fw = buildLogLoad(); // CAS500 
		final ProgressDialog progress = new ProgressDialog(this, 
				"Loading Project",
				"Loading project '" + project.getDBName() + "' ...", 
				false, true, fw);
		progress.closeIfNoErrors();
		
		Thread loadThread = new Thread() {
			public void run() {
				boolean success = true;
				
				// Load project sequence and annotation data
				try {
					UpdatePool pool = new UpdatePool(dbReader);
					String projName = project.getDBName();
					
					if ( project.isFPC() )
					{
						success = FPCLoadMain.run( pool, progress, projName );
						
						if (!success || Cancelled.isCancelled()) { // CAS500
							System.err.println("Cancel load");
							return;
						}
					}
					else if ( project.isPseudo() ) 
					{
						success = SeqLoadMain.run( pool, progress, projName );
					
						if (success && !Cancelled.isCancelled()) 
						{
							AnnotLoadMain annot = new AnnotLoadMain(pool, progress,mProps);
							success = annot.run( projName );
						}
						else { // CAS500
							System.err.println("Cancel load");
							return;
						}
					}
				}
				catch (Exception e) {
					success = false;
					ErrorReport.print(e, "Loading project");
				}
				finally {
					progress.finish(success);
				}
			}
		};
		
		progress.start( loadThread ); // blocks until thread finishes or user cancels
		
		if (progress.wasCancelled()) {// Remove partially-loaded project
			try {
				System.out.println("Cancel loading - " + project + " (removing project)");
				removeProjectFromDB( project );
			}
			catch (Exception e) { 
				ErrorReport.print(e, "Remove partially loaded project");
			}
		}
		
		refreshMenu();
	}

	private void progressLoadAllProjects() {
		ErrorCount.init();
		FileWriter fw = buildLogLoad(); // CAS500
		final ProgressDialog progress = new ProgressDialog(this, 
				"Loading All Projects",
				"Loading all projects" , 
				false, true, fw);
		progress.closeIfNoErrors();
		Thread loadThread = new Thread() {
			public void run() {
				boolean success = true;
				progress.appendText("\nLoad all projects"); 
				
				for (Project project : selectedProjects)
				{
					if (project.getStatus() != Project.STATUS_ON_DISK) continue;
				
					curProj = project;
					try {
						UpdatePool pool = new UpdatePool(dbReader);
						String projName = project.getDBName();
						
						if ( project.isFPC() )
						{
							success = FPCLoadMain.run( pool, progress, projName );
						}
						else if ( project.isPseudo() ) 
						{
							success = SeqLoadMain.run( pool, progress, projName );
						
							if (success && !Cancelled.isCancelled()) 
							{
								AnnotLoadMain annot = new AnnotLoadMain(pool, progress,mProps);
								success = annot.run( projName );
							}
						}
					}
					catch (Exception e) {
						success = false;
						ErrorReport.print(e, "Loading all projects");
					}
				}
				if (!progress.wasCancelled())
				{
					progress.finish(success);
				}
			}
		};
		
		progress.start( loadThread ); // blocks until thread finishes or user cancels
		
		if (progress.wasCancelled() && curProj != null) {// Remove partially-loaded project		
			try {
				System.out.println("Cancel removing " + curProj.strDBName + " (removing project)"); 
				removeProjectFromDB( curProj ); 
			}
			catch (Exception e) { 
				ErrorReport.print(e, "Remove partially loaded project");
			}
		}
		refreshMenu();
	}
	
	private void showQuery() {
		Utilities.setCursorBusy(this, true);
		try {
			SyMAPQueryFrame qFrame = new SyMAPQueryFrame(DatabaseReader.getInstance(SyMAPConstants.DB_CONNECTION_SYMAP_APPLET_3D, dbReader), false);
			for (Project p : selectedProjects) {
				if(p.getStatus() == Project.STATUS_IN_DB)
					qFrame.addProject( p );
			}
			qFrame.build();
			qFrame.setVisible(true);
		} 
		catch(Exception e) {
			ErrorReport.print(e, "Show Query");
		}
		finally {
			Utilities.setCursorBusy(this, false);
		}
	}
	
	private void progressReloadAnnotation(final Project project) {
		FileWriter fw = buildLogLoad(); // CAS500
		final ProgressDialog progress = new ProgressDialog(this, 
				"Loading Annotation",
				"Loading Annotation '" + project.getDBName() + "' ...", 
				false, true, fw);
		progress.closeIfNoErrors();
		
		Thread loadThread = new Thread() {
			public void run() {
				boolean success = true;
				progress.appendText("Reload annotation - " + project.getDBName()); 
				
				try {
					UpdatePool pool = new UpdatePool(dbReader);
					String projName = project.getDBName();
					
					if ( project.isPseudo() ) 
					{
						AnnotLoadMain annot = new AnnotLoadMain(pool, progress, mProps);
						success = annot.run( projName );
					}
				}
				catch (Exception e) {
					success = false;
					ErrorReport.print(e, "Run Reload");
				}
				finally {
					progress.finish(success);
				}
			}
		};
		
		progress.start( loadThread ); // blocks until thread finishes or user cancels
		
		if (progress.wasCancelled()) {
			try {
				System.out.println("Cancel reload annotation - " + project + " (removing annotation)");
				removeProjectAnnotationFromDB( project ); 
			}
			catch (Exception e) { 
				ErrorReport.print(e, "Cancel load annotation");
			}
		}
		
		refreshMenu();
	}
	/************* XXX Listeners ********************/
	private MouseListener doLoadAllProj = new MouseAdapter() {
		public void mouseClicked(MouseEvent e) {
			progressLoadAllProjects(  );
		}
	};
	private MouseListener doRemove = new MouseAdapter() {
		public void mouseClicked(MouseEvent e) {
			Project p = ((ProjectLinkLabel)e.getSource()).getProject();
			if (!removeAlignmentsCheck(p)) {
				if (!progressConfirm2("Remove project from database","Remove project from database")) return;
				progressRemoveProject(0, p );
			}
			else {
				int rc = progressConfirm3("Remove project from database","Remove project from database" +
					"\nOnly: remove only" +
					"\nAll : remove project and remove previous alignments");
				if (rc==0) return;
				progressRemoveProject(rc, p );
			}
		}
	};
	private MouseListener doRemoveDisk = new MouseAdapter() {
		public void mouseClicked(MouseEvent e) {
			if (!progressConfirm2("Remove project from disk","Remove project from disk")) return;
			
			progressRemoveProjectFromDisk( ((ProjectLinkLabel)e.getSource()).getProject() );
		}
	};
	private MouseListener doReloadSeq = new MouseAdapter() {
		public void mouseClicked(MouseEvent e) {
			Project p = ((ProjectLinkLabel)e.getSource()).getProject();
			if (!removeAlignmentsCheck(p)) {
				if (!progressConfirm2("Reload project","Reload project")) return;
				progressReloadSequences(p, false ); // no alignments to remove
			}
			else {
				int rc = progressConfirm3("Reload project","Reload project" +
					"\nOnly: reload only" +
					"\nAll : reload project and remove previous alignments");
				if (rc==0) return;
				progressReloadSequences( p, rc==2 ); // remove if All selected
			}
		}
	};
	private MouseListener doReloadAnnot = new MouseAdapter() {
		public void mouseClicked(MouseEvent e) {
			if (!progressConfirm2("Reload annotation", "Reload annotation" +
					"\n\nYou will need to re-run the synteny computations for this project, " +
					"\nbut not the MUMmer or BLAT alignments.")) return;
			
			progressReloadAnnotation( ((ProjectLinkLabel)e.getSource()).getProject() );
		}
	};	
	// CAS42 1/4/18 confirm any action that removes things
	private boolean progressConfirm2(String title, String msg) {
		String [] options = {"Cancel", "Continue"};
		int ret = JOptionPane.showOptionDialog(null, 
				msg,
				title, JOptionPane.YES_NO_OPTION, 
				JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
		if (ret == 0) return false;
		else return true;
	}
	// CAS500 this is when there is two possible actions
	private int progressConfirm3(String title, String msg) {
		String [] options = {"Cancel", "Only", "All"};
		return JOptionPane.showOptionDialog(null, 
				msg,
				title, JOptionPane.YES_NO_OPTION, 
				JOptionPane.QUESTION_MESSAGE, null, options, options[2]);
	}
	private MouseListener doReloadParams = new MouseAdapter() {
		public void mouseClicked(MouseEvent e) {
			Project theProject = ((ProjectLinkLabel)e.getSource()).getProject();
			PropertyFrame propFrame = new PropertyFrame(
				getRef(), theProject.isPseudo(), theProject.getDisplayName(), 
				theProject.getDBName(), dbReader, true);
			propFrame.setVisible(true);
			
			if(!propFrame.isDisarded())
			{			
				theProject.updateParameters(new UpdatePool(dbReader),null);
			}
			
			refreshMenu();		
		}
	};
	private MouseListener doSetParamsNotLoaded = new MouseAdapter() {
		public void mouseClicked(MouseEvent e) {
			Project theProject = ((ProjectLinkLabel)e.getSource()).getProject();
			PropertyFrame propFrame = new PropertyFrame(
				getRef(), theProject.isPseudo(), theProject.getDisplayName(), 
				theProject.getDBName(), dbReader, false);
			propFrame.setVisible(true);
			
			refreshMenu(); // CAS504
		}		
	};

	private ProjectManagerFrameCommon getRef() { return this; }

	private boolean removeAlignmentsCheck(Project p) {
		File[] topLevels = new File[2];

		if (p.isFPC())
		{
			topLevels[0] = new File(Constants.getNameResultsDir(true));
			topLevels[1] = null;
		}
		else
		{
			topLevels[0] = new File(Constants.getNameResultsDir(true));
			topLevels[1] = new File(Constants.getNameResultsDir(false));
		}
		
		for (File top : topLevels)
		{
			if (top == null) continue;
			Vector<File> alignDirs = new Vector<File>();
			
			for (File f : top.listFiles()) 
			{
				if (f.isDirectory())
				{
					if (p.isFPC() && f.getName().startsWith(p.getDBName()+Constants.projTo))
					{
						alignDirs.add(f); // for fpc, it has to come first
					}
					else
					{
						if	(f.getName().startsWith(p.getDBName()+ Constants.projTo) ||
								f.getName().endsWith(Constants.projTo+p.getDBName())) 
						{
							alignDirs.add(f);
						}
					}
				}
				if (alignDirs.size() > 0) return true;
			}
		}
		return false;
	}
	private boolean removeAllAlignmentsFromDisk(Project p, ProgressDialog progress)
	{
		if (progress!=null)
			progress.appendText("Removing alignments from disk - " + p.getDBName());		
		
		File[] topLevels = new File[2];
		
		if (p.isFPC())
		{
			topLevels[0] = new File(Constants.getNameResultsDir(true)); // check fpc_results 
			topLevels[1] = null;
		}
		else
		{
			topLevels[0] = new File(Constants.getNameResultsDir(false)); // check seq_results
			topLevels[1] = new File(Constants.getNameResultsDir(true));  // check fpc_results
		}
		boolean success = true;
		String projTo = Constants.projTo;
		
		for (File top : topLevels)
		{
			if (top == null) continue;
			
			Vector<File> alignDirs = new Vector<File>();
			
			for (File f : top.listFiles()) 
			{
				if (f.isDirectory())
				{
					if (p.isFPC() && f.getName().startsWith(p.getDBName()+ projTo)) // fpc name must come first
					{
						alignDirs.add(f); 
					}
					else
					{
						if	(f.getName().startsWith(p.getDBName()+ projTo) ||
							 f.getName().endsWith(projTo+p.getDBName())) 
						{
							alignDirs.add(f);
						}
					}
				}
			}
			if (alignDirs.size() == 0) return true;
			
			for (File f : alignDirs) 
			{
				if (progress!=null)
					progress.appendText("   Removing alignment - " + f.getName());	
				else 
					System.out.println("   Removing alignment - " + f.getName());
				success &= removeDir(f);
			}
		}
		return success;
	}
	
	private void progressRemoveProject(final int rc, final Project project) {
		ErrorCount.init();
		FileWriter fw = buildLogLoad(); // CAS500
		final ProgressDialog progress = new ProgressDialog(this, 
				"Removing Project From Database",
				"Removing project from database '" + project.getDBName() + "' ...", 
				false, true, fw);
		progress.closeIfNoErrors();
		
		Thread rmThread = new Thread() {
			public void run() {
				boolean success = true;
				progress.appendText("Remove project from database - " + project.getDBName());
				
				try {
					removeProjectFromDB(project);
					if (rc==2) removeAllAlignmentsFromDisk(project, progress); 
				}
				catch (Exception e) {
					success = false;
					ErrorReport.print(e, "Remove project");
				}
				finally {
					progress.finish(success);
				}
			}
		};
		
		progress.start( rmThread ); 
		
		refreshMenu();
	}
	private void progressClearPair(final Project p1, final Project p2) {
		String strOptionMessage = "Previous MUMmer or BLAT output files for " + p1.getDBName() + " to " + p2.getDBName() + " will be deleted. Continue? \n";
	
		JOptionPane optionPane = new JOptionPane(
			strOptionMessage,
			JOptionPane.QUESTION_MESSAGE,
		    JOptionPane.YES_NO_OPTION);
	
		JDialog dialog = optionPane.createDialog(null, "Clear Alignment");
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		dialog.setVisible(true);
	
		// Wait on user input
		Object selectedValue = optionPane.getValue();
	
		if (selectedValue == null || ((Integer)selectedValue).intValue() != JOptionPane.YES_OPTION) 
		{	
			return;
		}
		ErrorCount.init();
		FileWriter fw = buildLogLoad(); // CAS500
		final ProgressDialog progress = new ProgressDialog(this, 
				"Clearing alignment pair",
				"Clearing alignment: " + p1.getDBName() + " to " + p2.getDBName(), 
				false, true, fw);
		progress.closeIfNoErrors();
		
		Thread clearThread = new Thread() {
			public void run() {
				boolean success = true;
				progress.appendText("Clearing alignment: " + p1.getDBName() + " to " + p2.getDBName());
				
				try {
					try
					{
						Thread.sleep(1000);
					}
					catch(Exception e){}

					removeAlignmentFromDB(p1,p2);
					removeAlignmentFromDisk(p1,p2);
				}
				catch (Exception e) {
					success = false;
					ErrorReport.print(e, "Clearing alignment");
				}
				finally {
					progress.finish(success);
				}
			}
		};
		
		progress.start( clearThread ); 
		
		refreshMenu();
	}
	private void progressRemoveProjectFromDisk(final Project project) {
		ErrorCount.init();
		FileWriter fw = buildLogLoad(); // CAS500
		final ProgressDialog progress = new ProgressDialog(this, 
				"Removing Project From Disk",
				"Removing project from disk '" + project.getDBName() + "' ...", 
				false, true, fw);
		progress.closeIfNoErrors();
		
		Thread rmThread = new Thread() {
			public void run() {
				boolean success = true;
				progress.appendText("Remove project from disk - " + project.getDBName());
				
				try {
					removeAllAlignmentsFromDisk(project, null); // CAS500 in case not earlier removed
					
					String path = (project.isFPC()) ? 
						Constants.fpcDataDir : Constants.seqDataDir;
					path += project.getDBName();
					
					removeDir(new File(path));
				}
				catch (Exception e) {
					success = false;
					ErrorReport.print(e, "Remove project from disk");
				}
				finally {
					progress.finish(success);
				}
			}
		};
		
		progress.start( rmThread ); 
		
		refreshMenu();
	}
	private void progressReloadSequences(final Project project, final boolean bRmAlign) {
		ErrorCount.init();
		FileWriter fw = buildLogLoad(); // CAS500
		final ProgressDialog progress = new ProgressDialog(this, 
				"Reloading " + project.getDBName() ,
				"Reloading '" + project.getDBName() + "' ...", 
				false, true, fw);
		progress.closeIfNoErrors();
		
		Thread rmThread = new Thread() {
			public void run() {
				boolean success = true;
				progress.appendText("Removing project from database - " + project.getDBName());
				
				// Load project sequence and annotation data
				try {
					UpdatePool pool = new UpdatePool(dbReader);
					String projName = project.getDBName();
					
					removeProjectFromDB(project);
					if (bRmAlign) removeAllAlignmentsFromDisk(project, progress); 
					
					success = SeqLoadMain.run( pool, progress, projName );
					
					if (success && !Cancelled.isCancelled()) 
					{
						AnnotLoadMain annot = new AnnotLoadMain(pool, progress,mProps);
						success = annot.run( projName );
					}
				}
				catch (Exception e) {
					success = false;
					ErrorReport.print(e, "Remove sequences");
				}
				finally {
					progress.finish(success);
				}
			}
		};
		
		progress.start( rmThread ); 
		
		refreshMenu();
	}

	private Project[] getSelectedAlignmentProjects() {
		if (alignmentTable == null)
			return null;
		
		int nRow = alignmentTable.getSelectedRow();
		int nCol = alignmentTable.getSelectedColumn();
		if (nRow < 0 || nCol < 0)
			return null;
		
		String strRowProjName = alignmentTable.getValueAt(nRow, 0).toString();
		String strColProjName = alignmentTable.getValueAt(nCol-1, 0).toString();
		
		Project p1 = getProjectByDisplayName( strRowProjName );
		Project p2 = getProjectByDisplayName( strColProjName );
		
		return orderProjects(p1,p2);

	}
	// XXX this is important as having FPC first is assumed in other places
	private Project[] orderProjects(Project p1, Project p2)
	{
		if (p1.isFPC()) 			return new Project[] { p1, p2 };	// keep fpc first
		else if ( p2.isFPC()  ) 	return new Project[] { p2, p1 }; // not allows make fpc first
		
		if (lgProj1st) { // often biggest first is best, but not always
			if (p1.length < p2.length) 
				return new Project[] { p2, p1 }; 
			else
				return new Project[] { p1, p2 };	
		}
		else { // original -- compatible with existing symap databases
			if (printStats)
				if (p1.strDBName.compareToIgnoreCase(p2.strDBName) > 0)
					System.out.println(p2.strDBName + " before " + p1.strDBName);
			
			if (p1.strDBName.compareToIgnoreCase(p2.strDBName) > 0) // make alphabetic
				return new Project[] { p2, p1 }; 
			else
				return new Project[] { p1, p2 };
		}
	}
	
	private String getSelectedAlignmentValue() {
		int nRow = alignmentTable.getSelectedRow();
		int nCol = alignmentTable.getSelectedColumn();
		return (String)alignmentTable.getValueAt(nRow, nCol);
	}
	// CAS500 was putting log in data/pseudo_pseudo/seq1_to_seq2/symap.log
	// changed to put in logs/seq1_to_seq2/symap.log
	private FileWriter buildLogAlign(Project p1, Project p2)
	{
		FileWriter ret = null;
		try
		{
			File pd = new File(Constants.logDir);
			if (!pd.isDirectory()) pd.mkdir();
			
			String pairDir = Constants.logDir + p1.getDBName() + Constants.projTo + p2.getDBName(); 
			pd = new File(pairDir);
			if (!pd.isDirectory()) pd.mkdir();
			
			File lf = new File(pd,Constants.syntenyLog);
			ret = new FileWriter(lf, true); // append
			System.out.println("Append to log file: " + pairDir + "/" + Constants.syntenyLog);	
		}
		catch (Exception e)
		{
			ErrorReport.print(e, "Creating log file");
		}
		return ret;
	}
	private String buildLogAlignDir(Project p1, Project p2)
	{
		try
		{
			String logName = Constants.logDir + p1.getDBName() + Constants.projTo + p2.getDBName();
			if (Utilities.dirExists(logName))
				System.out.println("Log alignments in directory: " + logName);
			Utilities.checkCreateDir(logName, "PM buildLogAlign");
			return logName + "/";
		}
		catch (Exception e){ErrorReport.print(e, "Creating log file");}
		return null;
	}
	// CAS500 New log file for load
	private FileWriter buildLogLoad()
	{
		FileWriter ret = null;
		try
		{
			String name = Constants.loadLog;
			 
			File pd = new File(Constants.logDir);
			if (!pd.isDirectory()) pd.mkdir();
			
			File lf = new File(pd,name);
			if (!lf.exists())
				System.out.println("Create log file: " + Constants.logDir + name);
			ret = new FileWriter(lf, true);	
		}
		catch (Exception e) {ErrorReport.print(e, "Creating log file");}
		return ret;
	}
	/****************************************************
	 * Methods for renewing pairIdx and saving pair parameters
	 */
	private int getPairIdx(int id1, int id2) {
		int idx = 0;
		try {
			UpdatePool pool = new UpdatePool(dbReader);
			ResultSet rs = pool.executeQuery("select idx from pairs " +
					"where (proj1_idx=" + id1 + " and proj2_idx=" + id2 + 
					") or  (proj2_idx=" + id1 + " and proj1_idx=" + id2 + ")" );
			if (rs.first())
				idx = rs.getInt(1);	
			rs.close();
		}
		catch (Exception e) {ErrorReport.die(e, "SyMAP error getting pair idx");}
		return idx;	
	}
	
	private int pairIdxRenew(int id1, int id2) {
		try {
			int idx = getPairIdx(id1, id2);
			UpdatePool pool = new UpdatePool(dbReader);
			
			// Remove all previous pair info
			if (idx>0) 
				pool.executeUpdate("DELETE FROM pairs WHERE idx=" + idx);
			
			return pairIdxCreate(id1, id2, pool);
		}
		catch (Exception e) {ErrorReport.die(e, "SyMAP error renewing pairidx");}
		return 0;
	}
	private int pairIdxCreate(int id1, int id2, UpdatePool pool) {
		try {
			int idx=0;
			if (pool==null) pool = new UpdatePool(dbReader);
			
			pool.executeUpdate("INSERT INTO pairs (proj1_idx,proj2_idx) " +
						" VALUES('" + id1 + "','" + id2 + "')");
				
			ResultSet rs = pool.executeQuery("SELECT idx FROM pairs " +
					"WHERE proj1_idx=" + id1 + " AND proj2_idx=" + id2);
			if (rs.first())
				idx = rs.getInt(1);	
			rs.close();
			return idx;
		}
		catch (Exception e) {ErrorReport.die(e, "SyMAP error getting pair idx");}
		return 0;
	}
	// After getting props, renewing pairIdx, then save props with new pairIdx (here)
	private void savePairProps(SyProps props, int pairIdx, Project p1, Project p2, Logger log) {
		try {
			String n1 = p1.getDBName(), n2=p2.getDBName();
			String dir = Constants.getNameResultsDir(p1.strDBName, p1.isFPC(), p2.strDBName);
			props.saveNonDefaulted(log, dir, p1.getID(), p2.getID(), pairIdx, n1, n2, new UpdatePool(dbReader));
		} 
		catch (Exception e){ErrorReport.print(e, "Save pair parameters");}
	}
	// Called when Pair Parameter window opened
	private SyProps getPairPropsFromDB(int id1, int id2) {
		try {
			SyProps props = new SyProps();
			
			int pairIdx = getPairIdx(id1, id2);
			if (pairIdx==0) {
				if (globalPairProps==null) globalPairProps = new SyProps(); // should not happen
				props.copyProps(globalPairProps);
				return props; 
			}
			
			UpdatePool db = new UpdatePool(dbReader);
			
			ResultSet rs =db.executeQuery("select name,value from pair_props where pair_idx=" + pairIdx);
			while (rs.next())
			{
				String name = rs.getString(1);
				String value = rs.getString(2);
				props.setProperty(name, value);
			}
			return props;
		}
		catch (Exception e) {ErrorReport.die(e, "SyMAP error getting pair props");}
		return null;
	}
	// Called on Pair Parameter window SAVE, and make pairSyProps default
	public void setPairProps(SyProps p, Project p1, Project p2) {
		if (p1==null) {
			globalPairProps.copyProps(p);
			globalPairProps.printNonDefault(null, "Global");
			return; // no selected project
		}
		try {
			UpdatePool pool = new UpdatePool(dbReader);
			int id1=p1.getID(), id2=p2.getID();
			
			int pairIdx = getPairIdx(id1, id2);
			if (pairIdx==0) 
				pairIdx = pairIdxCreate(id1, id2, pool);
			
			String dir = Constants.getNameResultsDir(p1.strDBName, p1.isFPC(), p2.strDBName);
			String n1 = p1.getDBName(), n2=p2.getDBName();
			p.saveNonDefaulted(null, dir, id1, id2, pairIdx, n1,n2, pool);
		} 
		catch (Exception e){ErrorReport.print(e, "Save pair parameters");}
	}
	
	private ProjectManagerFrameCommon getInstance() { return this; }
	
	/************************************************************************/
	private void getPseudoCounts(TreeMap<String,Integer> hitCounts, int pidx) throws Exception
	{
		UpdatePool db = new UpdatePool(dbReader);
	
		ResultSet rs = db.executeQuery("select count(*) as nhits from pseudo_hits where pair_idx=" + pidx);	
		rs.first();
		hitCounts.put("nhits", rs.getInt("nhits"));
		rs.close();
		
		rs = db.executeQuery("select count(*) as nhits from pseudo_block_hits as pbh join pseudo_hits as ph on pbh.hit_idx=ph.idx " +
								" where ph.pair_idx=" + pidx);
		rs.first();
		hitCounts.put("blkhits", rs.getInt("nhits"));
		rs.close();

		rs =  db.executeQuery("select count(*) as nhits from pseudo_hits where gene_overlap > 0 and pair_idx=" + pidx);	
		rs.first();
		hitCounts.put("genehits", rs.getInt("nhits"));
		rs.close();

		rs = db.executeQuery("select count(*) as n from blocks where pair_idx=" + pidx);	
		rs.first();
		hitCounts.put("blocks", rs.getInt("n"));
		rs.close();

	}
	private void getFPCCounts(TreeMap<String,Integer> counts, int pidx) throws Exception
	{
		UpdatePool db = new UpdatePool(dbReader);
	
		ResultSet rs = db.executeQuery("select count(*) as n from mrk_hits where pair_idx=" + pidx);	
		rs.first();
		counts.put("mrkhits", rs.getInt("n"));
		rs.close();
		
		rs = db.executeQuery("select count(*) as n from bes_hits where pair_idx=" + pidx);	
		rs.first();
		counts.put("beshits", rs.getInt("n"));
		rs.close();		

		rs = db.executeQuery("select count(*) as n from blocks where pair_idx=" + pidx);	
		rs.first();
		counts.put("blocks", rs.getInt("n"));
		rs.close();
		
		rs = db.executeQuery("select count(*) as n from mrk_block_hits as pbh join mrk_hits as ph on pbh.hit_idx=ph.idx " +
								" where ph.pair_idx=" + pidx);
		rs.first();
		counts.put("mrkblkhits", rs.getInt("n"));
		rs.close();

		rs = db.executeQuery("select count(*) as n from bes_block_hits as pbh join bes_hits as ph on pbh.hit_idx=ph.idx " +
								" where ph.pair_idx=" + pidx);
		rs.first();
		counts.put("besblkhits", rs.getInt("n"));
		rs.close();


	}
	private int getFPCMarkers(Project p) throws Exception
	{
		int num = 0;
		UpdatePool db = new UpdatePool(dbReader);
		ResultSet rs =db.executeQuery("select count(*) as n from markers where proj_idx=" + p.getID());
		rs.first();
		num = rs.getInt("n");
		rs.close();
		return num;
	}
	private int getFPCBES(Project p) throws Exception
	{
		int num = 0;
		UpdatePool db = new UpdatePool(dbReader);
		ResultSet rs =db.executeQuery("select count(*) as n from bes_seq where proj_idx=" + p.getID());
		rs.first();
		num = rs.getInt("n");
		rs.close();
		return num;
	}
	
	private void printStats(ProgressDialog prog, Project p1, Project p2) throws Exception
	{
		if (p2.isFPC()) // in case order is backwards
		{
			Project temp = p1;
			p1 = p2;
			p2 = temp;
		}
		int pairIdx = getPairIdx(p1.getID(),p2.getID());
		if (pairIdx==0) return;

		if (p1.isPseudo()) // pseudo/pseudo
		{
			Utils.prtNumMsg(prog, p1.getNumGroups(), p1.getDisplayName() + " chromosomes");
			Utils.prtNumMsg(prog, p2.getNumGroups(), p2.getDisplayName() + " chromosomes");
			TreeMap<String,Integer> counts = new TreeMap<String,Integer>();
			getPseudoCounts(counts,pairIdx);
			Utils.prtNumMsg(prog, counts.get("nhits"), "hits");
			Utils.prtNumMsg(prog, counts.get("blocks"), "synteny blocks");
			Utils.prtNumMsg(prog, counts.get("genehits"), "gene hits");
			Utils.prtNumMsg(prog, counts.get("blkhits"), "synteny hits");
		}
		else
		{
			Utils.prtNumMsg(prog, p2.getNumGroups(), p2.getDisplayName() + " chromosomes");
			Utils.prtNumMsg(prog, p1.getNumGroups(), p1.getDisplayName() + "(FPC) chromosomes (or LG)");
			int nMrk = getFPCMarkers(p1);
			Utils.prtNumMsg(prog, nMrk, "markers");
			Utils.prtNumMsg(prog, getFPCBES(p1), "BESs");
			
			TreeMap<String,Integer> counts = new TreeMap<String,Integer>();
			getFPCCounts(counts,pairIdx);
			Utils.prtNumMsg(prog, counts.get("blocks"), "synteny blocks");
			Utils.prtNumMsg(prog, counts.get("beshits"), "BES hits");
			Utils.prtNumMsg(prog, counts.get("besblkhits"), "BES synteny hits");
			if (nMrk > 0)
			{
				Utils.prtNumMsg(prog, counts.get("mrkhits"), "marker hits");
				Utils.prtNumMsg(prog, counts.get("mrkblkhits"), "marker synteny hits");
			}
		}		
	}
	
	private void doAll(boolean redo) // not used
	{
		Cancelled.init();
		if (alignmentTable == null)
			return;
		
		int nRows = alignmentTable.getRowCount();
		int nCols = alignmentTable.getColumnCount();
		if (nRows == 0 || nCols == 0)
			return;

		if (redo)
		{
			int ret = JOptionPane.showConfirmDialog(null, "Any existing mummer or blat output files will be removed! Continue?");	
			if (ret == JOptionPane.CANCEL_OPTION) {
				return;	
			}
			else if (ret == JOptionPane.NO_OPTION) {
				return;	
			}
		}
		
		for (int r = 0; r < nRows; r++)
		{
			String strRowProjName = alignmentTable.getValueAt(r, 0).toString();
			if (Cancelled.isCancelled()) break;

			for (int c = 1; c <= r+1; c++)
			{
				if (Cancelled.isCancelled()) break;

				String strColProjName = alignmentTable.getValueAt(c-1,0).toString();
				String entry = 	(alignmentTable.getValueAt(r,c) == null ? "" : alignmentTable.getValueAt(r,c).toString().trim());
				boolean doThis = false;
				if (entry.equals("-")) {
					continue;
				}
				else if (entry.equals(TBL_DONE)) {
					doThis = redo;
				}
				else if (entry.equals(TBL_ADONE) || entry.equals(TBL_QDONE)) {
					doThis = true;	
				}
				else if (entry.equals("")) {
					doThis = true;	
				}
				if (doThis)
				{
					Project p1 = getProjectByDisplayName( strRowProjName );
					Project p2 = getProjectByDisplayName( strColProjName );
					Project[] ordered = orderProjects(p1,p2);
					p1 = ordered[0];
					p2 = ordered[1];
					if (p1.getID() != p2.getID())
					{
						System.out.println("\n>>Starting " + p1.getDBName() + " and " + p2.getDBName());
					
						promptAndProgressAlignProjects(p1, p2, true);
					}
				}
			}	
		}
	}
	private void promptAndProgressAlignProjects(final Project p1, final Project p2, boolean closeWhenDone) {

		FileWriter fw =      buildLogAlign(p1,p2);
		String alignLogDir = buildLogAlignDir(p1,p2);
		
		int maxCPUs = 1;
		try {
			maxCPUs = Integer.parseInt(txtCPUs.getText());
		}
		catch (Exception e) {
			Utilities.showErrorMessage("Please enter a valid value for number of CPUs to use.");
			return;
		}
		if (maxCPUs <= 0) maxCPUs = 1;
		
		try {
			Date date = new Date();
			fw.write("\n-------------- starting " + date.toString() + " --------------------\n");
		} catch (Exception e){}
		
		String msg = (p1 == p2) ? 
				"Aligning project " + p1.getDisplayName() + " to itself ..." :
				"Aligning projects " + p1.getDisplayName() + " and " + p2.getDisplayName() + " ...";
		
		final ProgressDialog progress = new ProgressDialog(this, 
				"Aligning Projects", msg, false, true, fw);
		
		// getting existing pair props
		int id1=p1.getID(), id2=p2.getID();
		SyProps pairProps = getPairPropsFromDB(id1, id2);
		
		// remove existing pair stuff and reassign pair props 
		int pairIdx = pairIdxRenew(id1, id2);
		Logger log = progress;
		savePairProps(pairProps, pairIdx, p1, p2, log);
		
		if (closeWhenDone) {
			progress.closeWhenDone();	
		}
		else {
			progress.closeIfNoErrors();
		}
		
		final AlignMain aligner = 
			new AlignMain(new UpdatePool(dbReader), progress, 
				p1.getDBName(), p2.getDBName(), maxCPUs, mProps, pairProps, alignLogDir);
		if (aligner.mCancelled) return;
		
		final AnchorsMain anchors = 
			new AnchorsMain(pairIdx, new UpdatePool(dbReader), progress, mProps, pairProps );
		
		final SyntenyMain synteny = 
			new SyntenyMain( new UpdatePool(dbReader), progress, mProps, pairProps );
		
		final Thread statusThread = new Thread() {
			public void run() {
				
				while (aligner.notStarted() && !Cancelled.isCancelled()) Utilities.sleep(1000);
				if (aligner.getNumRemaining() == 0 && aligner.getNumRunning() == 0) return;
				if (Cancelled.isCancelled()) return;
				
				progress.updateText("\nRunning alignments:\n",
				aligner.getStatusSummary() + "\n" +
				aligner.getNumCompleted() + " alignments completed, " +
				aligner.getNumRunning()   + " running, " +
				aligner.getNumRemaining() + " remaining\n\n");

				while (aligner.getNumRunning() > 0 || aligner.getNumRemaining() > 0) {
					if (Cancelled.isCancelled()) return;
					Utilities.sleep(10000);
					if (Cancelled.isCancelled()) return;
					
					progress.updateText("\nRunning alignments:\n",
							aligner.getStatusSummary() + "\n" +
							aligner.getNumCompleted() + " alignments completed, " +
							aligner.getNumRunning()   + " running, " +
							aligner.getNumRemaining() + " remaining\n\n");
				}
				
				progress.updateText("\nRunning alignments:\n",
						aligner.getStatusSummary() + "\n" +
						aligner.getNumCompleted() + " alignments completed, " +
						aligner.getNumRunning()   + " running, " +
						aligner.getNumRemaining() + " remaining\n\n");
			}
		};
		
		final Thread alignThread = new Thread() {
			public void run() {
				boolean success = true;
				
				try {
					// Perform alignment, load anchors and compute synteny
					long timeStart = System.currentTimeMillis();

					success &= aligner.run();
					if (Cancelled.isCancelled()) 
					{
						progress.setVisible(false);
						progress.setCancelled();
						return;
					}
					if (!success) 
					{
						progress.finish(false);
						return;
					}
							
					long timeEnd = System.currentTimeMillis();
					long diff = timeEnd - timeStart;
					String timeMsg = Utilities.getDurationString(diff);
					
					// CAS501 - guess deleted so anchor could add, removed anchor add
					//pool.executeUpdate("delete from pairs where (" + 
					//	"	proj1_idx=" + p1.getID() + " and proj2_idx=" + p2.getID() + ") or (proj1_idx=" + p2.getID() + " and proj2_idx=" + p1.getID() + ")"); 
					
					if (Cancelled.isCancelled()) return;
					
					success &= anchors.run( p1.getDBName(), p2.getDBName() );
					if (!success) 
					{
						progress.finish(false);
						return;
					}
					if (Cancelled.isCancelled()) return;
					
					success &= synteny.run( p1.getDBName(), p2.getDBName() );
					if (!success) 
					{
						progress.finish(false);
						return;
					}
					if (Cancelled.isCancelled()) return;
											
					timeEnd = System.currentTimeMillis();
					diff = timeEnd - timeStart;
					timeMsg = Utilities.getDurationString(diff);
					progress.appendText(">> Summary for " + p1.getDisplayName() + " and " + p2.getDisplayName() + "\n\n");
					printStats(progress, p1, p2);
					progress.appendText("\nFinished in " + timeMsg + "\n\n");
					
					UpdatePool pool = new UpdatePool(dbReader);
					pool.executeUpdate("update pairs set aligned=1,aligndate=NOW() where (" + 
						"	proj1_idx=" + p1.getID() + " and proj2_idx=" + p2.getID() + ") or (proj1_idx=" + p2.getID() + " and proj2_idx=" + p1.getID() + ")"); 
				}
				catch (OutOfMemoryError e) {
					success = false;
					statusThread.interrupt();
					progress.msg( "Not enough memory");
					Utilities.showOutOfMemoryMessage(progress);
				}
				catch (Exception e) {
					success = false;
					ErrorReport.print(e,"Running alignment");
					statusThread.interrupt();
				}
				
				progress.finish(success);
			}
		};
		
		progress.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				System.out.println(e.getActionCommand());
				// Stop aligner and wait on monitor threads to finish
				aligner.interrupt();
				anchors.interrupt();
				try {
					alignThread.wait(5000);
					statusThread.wait(5000);
				}
				catch (Exception e2) { }
				
				// Threads should be done by now, but kill just in case
				alignThread.interrupt();
				statusThread.interrupt();
				
				// Close dialog
				progress.setVisible(false);
				if (e.getActionCommand().equals("Cancel"))
				{
					progress.setCancelled();
					Cancelled.cancel();
				}
			}
		});
		
		alignThread.start();
		statusThread.start();
		progress.start(); // blocks until thread finishes or user cancels
		
		if (progress.wasCancelled()) {
			// Remove partially-loaded alignment
			try {
				Thread.sleep(1000);
				System.out.println("Cancel alignment (removing alignment from DB)");
				removeAlignmentFromDB(p1, p2);
				System.out.println("Removal complete");
			}
			catch (Exception e) { 
				ErrorReport.print(e, "Removing alignment");
			}
		}
		else if (closeWhenDone)
		{
			progress.dispose();	
		}
		
		//System.gc(); // cleanup memory after AlignMain, AnchorsMain, SyntenyMain
		System.out.println("--------------------------------------------------");
		refreshMenu();
		try
		{
			if (fw != null)
			{
				Date date = new Date();
				fw.write("\n-------------- done " + date.toString() + " --------------------\n");
				fw.close();
			}
		}
		catch (Exception e) {}
	}
	
	private void refreshMenu() {
		Utilities.setCursorBusy(this, true);
		
		initialize();
		
		splitPane.setLeftComponent( createProjectPanel() );
		if (selectedProjects.size() > 0)
			splitPane.setRightComponent( createSummaryPanel() );
		else
			splitPane.setRightComponent( instructionsPanel );
		
		refreshQueryButton();
		Utilities.setCursorBusy(this, false);
	}
	
	private JPanel createProjectPanel() {
		addProjectPanel = new AddProjectPanel(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				addNewProjectFromUI(projects, addProjectPanel.getName(), addProjectPanel.getPType());
				refreshMenu();
			}
		});
		JPanel panel = new JPanel();
		panel.setLayout( new BoxLayout ( panel, BoxLayout.Y_AXIS ) );
		panel.setBorder( BorderFactory.createEmptyBorder(10, 5, 10, 5) );
		
		JPanel tempPnl = new JPanel();
		tempPnl.setLayout(new BoxLayout(tempPnl, BoxLayout.LINE_AXIS));
		tempPnl.setAlignmentX(Component.CENTER_ALIGNMENT);

		JLabel lblTitle = createLabel("Projects", Font.BOLD, 18);
		lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
		tempPnl.add(lblTitle);
		panel.add(tempPnl);
		panel.add(Box.createVerticalStrut(10));
		
		JPanel subPanel = new JPanel();
		subPanel.setLayout( new BoxLayout ( subPanel, BoxLayout.Y_AXIS ) );
		subPanel.setBorder( BorderFactory.createEmptyBorder(5, 5, 5, 0) );
		subPanel.setBackground(Color.white);
		
		TreeMap<String,Vector<Project>> cat2Proj = new TreeMap<String,Vector<Project>>();
		for (Project p : projects) {
			String category = p.getCategory();
			if (category == null || category.length() == 0)
				category = "Uncategorized";
			if (!cat2Proj.containsKey(category))
				cat2Proj.put(category, new Vector<Project>());
			cat2Proj.get(category).add(p);
		}
		
		Set<String> categories = cat2Proj.keySet();
		for (String category : categories) {
			if (categories.size() > 1 || !category.equals("Uncategorized")) // Don't show "Uncategorized" if no other categories exist
				subPanel.add( new JLabel(category) );
			for (Project p : cat2Proj.get(category)) {
				ProjectCheckBox cb = null;
				String name = p.getDisplayName();
				if(p.getStatus() == Project.STATUS_ON_DISK)
					name += " (not loaded)";
				cb = new ProjectCheckBox( name, p );
				cb.setFont(createPlainFont(cb.getFont()));
				subPanel.add( cb );
			}
			subPanel.add( Box.createVerticalStrut(5) );
		}
		
		JScrollPane scroller = new JScrollPane(subPanel, 
				JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scroller.getVerticalScrollBar().setUnitIncrement(10);
		scroller.setAlignmentX(Component.CENTER_ALIGNMENT);
		
		panel.add( scroller );
		
		panel.add(Box.createVerticalStrut(10));
		tempPnl = new JPanel();
		tempPnl.setLayout(new BoxLayout(tempPnl, BoxLayout.LINE_AXIS));
		tempPnl.setAlignmentX(Component.CENTER_ALIGNMENT);
		
		btnAddProject = new JButton("Add Project");
		btnAddProject.setAlignmentX(Component.CENTER_ALIGNMENT);
		btnAddProject.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				addProjectPanel.reset();
				addProjectPanel.setVisible(true);
			}
		});
		
		if (!inReadOnlyMode) tempPnl.add(btnAddProject);
		
		panel.add(tempPnl);
		
		panel.setMinimumSize( new Dimension(MIN_DIVIDER_LOC, MIN_HEIGHT) );
		
		return panel;
	}
	
	private Component createHorizPanel( Component[] comps, int gapWidth ) {
		JPanel panel = new JPanel();
		panel.setLayout( new BoxLayout ( panel, BoxLayout.X_AXIS ) );
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		
		for (Component c : comps) {
			if (c != null) {
				panel.add( c );
				if (gapWidth > 0)
					panel.add( Box.createHorizontalStrut(gapWidth) );
			}
		}
		
		panel.add( Box.createHorizontalGlue() );
		
		return panel;
	}

	private void showDotplot() { 
		Utilities.setCursorBusy(this, true);
		
		// Get the two projects selected in alignment table
		Project[] p = getSelectedAlignmentProjects();
		int projXIdx = p[0].getID();
		int projYIdx = projXIdx; // default to self-alignment
		if (p.length > 1)
			projYIdx = p[1].getID();
		
		// Swap x and y projects to match database - no longer necessary after #206
		if (projPairs.containsKey(projXIdx) && projPairs.get(projXIdx).contains(projYIdx)) {
			int temp = projXIdx;
			projXIdx = projYIdx;
			projYIdx = temp;
		}
		
		// Open dot plot
		DotPlotFrame frame = new DotPlotFrame(dbReader, projXIdx, projYIdx);
		frame.setSize( new Dimension(MIN_WIDTH, MIN_HEIGHT) );
		frame.setVisible(true);
		
		Utilities.setCursorBusy(this, false);
	}
	private void showBlockView()  { 
		Utilities.setCursorBusy(this, true);
		
		// Get the two projects selected in alignment table
		Project[] p = getSelectedAlignmentProjects();
		int projXIdx = p[0].getID();
		int projYIdx = projXIdx; // default to self-alignment
		if (p.length > 1)
			projYIdx = p[1].getID();
		
		// Swap x and y projects to match database - 
		if (projPairs.containsKey(projXIdx) && projPairs.get(projXIdx).contains(projYIdx)) {
			int temp = projXIdx;
			projXIdx = projYIdx;
			projYIdx = temp;
		}
		
		try
		{
			BlockViewFrame frame = new BlockViewFrame(dbReader, projXIdx, projYIdx);
			frame.setMinimumSize( new Dimension(MIN_WIDTH, MIN_HEIGHT) );
			frame.setVisible(true);
		}
		catch (Exception e)
		{
			ErrorReport.die(e, "Show block view");
		}
		
		Utilities.setCursorBusy(this, false);
	}
	private void showSummary()
	{
		Utilities.setCursorBusy(this, true);
		
		// Get the two projects selected in alignment table
		Project[] p = getSelectedAlignmentProjects();
		int projXIdx = p[0].getID();
		int projYIdx = projXIdx; // default to self-alignment
		if (p.length > 1)
			projYIdx = p[1].getID();
		
		// Swap x and y projects to match database - no longer necessary after #206
		if (projPairs.containsKey(projXIdx) && projPairs.get(projXIdx).contains(projYIdx)) {
			int temp = projXIdx;
			projXIdx = projYIdx;
			projYIdx = temp;
		}
		// if (projYIdx == projXIdx) projYIdx = 0; // CAS500 allows self summary
				
		SumFrame frame = new SumFrame(dbReader, projXIdx, projYIdx);
		frame.setSize( new Dimension(MIN_CWIDTH, MIN_HEIGHT) );
		frame.setVisible(true);
		
		Utilities.setCursorBusy(this, false);		
	}
	private void showCircleView() 
	{ 
		Utilities.setCursorBusy(this, true);
		
		// Get the two projects selected in alignment table
		Project[] p = getSelectedAlignmentProjects();
		int projXIdx = p[0].getID();
		int projYIdx = projXIdx; // default to self-alignment
		if (p.length > 1)
			projYIdx = p[1].getID();
		
		// Swap x and y projects to match database - no longer necessary after #206
		if (projPairs.containsKey(projXIdx) && projPairs.get(projXIdx).contains(projYIdx)) {
			int temp = projXIdx;
			projXIdx = projYIdx;
			projYIdx = temp;
		}
		if (projYIdx == projXIdx)
		{
			projYIdx = 0;
		}		
		CircFrame frame = new CircFrame(dbReader, projXIdx, projYIdx);
		frame.setSize( new Dimension(MIN_CWIDTH, MIN_CHEIGHT) );
		frame.setVisible(true);
		
		Utilities.setCursorBusy(this, false);
	}

	private void showAllDotPlot()	{
		int nProj = 0;
		for (Project p : selectedProjects) {
			if (p.getStatus() != Project.STATUS_ON_DISK) {
				nProj++;
			}
		}
		int[] pids = new int[nProj];
		int i = 0;
		for (Project p : selectedProjects) {
			if (p.getStatus() != Project.STATUS_ON_DISK) {
				pids[i] = p.getID();
				i++;
			}
		}

		// Open dot plot
		DotPlotFrame frame = new DotPlotFrame(null, dbReader, pids, null, null, null, true);
		frame.setSize( new Dimension(MIN_WIDTH, MIN_HEIGHT) );
		frame.setVisible(true);
	}
	private ItemListener checkboxListener = new ItemListener() {
		public void itemStateChanged(ItemEvent e) {
			ProjectCheckBox cb = (ProjectCheckBox)e.getSource();
			Project p = cb.getProject();
			boolean changed = false;
			
			if ( e.getStateChange() == ItemEvent.SELECTED ) {
				if ( !selectedProjects.contains(p) )
					selectedProjects.add(p);
				cb.setBackground(Color.yellow); // highlight
				changed = true;
			}
			else if ( e.getStateChange() == ItemEvent.DESELECTED ) {
				selectedProjects.remove(p);
				cb.setBackground(Color.white); // un-highlight
				changed = true;
			}
			
			if (changed) {
				sortProjects( selectedProjects );
				if (alignmentTable != null)
				{
					alignmentTable.clearSelection();	
				}

				if ( selectedProjects.size() > 0 )
					splitPane.setRightComponent( createSummaryPanel() ); // regenerate since changed
					// Note: recreating each time thrashes heap, but probably negligible performance impact
				else
					splitPane.setRightComponent( instructionsPanel );
			}
			refreshQueryButton();
		}
	};
	
	private void refreshQueryButton() {
		Iterator<Project> iter = selectedProjects.iterator();
		int pseudoCount = 0;
		
		while(iter.hasNext()) {
			if(iter.next().isPseudo())
				pseudoCount++;
		}
		
		if (btnQueryView != null)
		{
			if(pseudoCount > 1) {
				btnQueryView.setEnabled(true);
			}
			else {
				btnQueryView.setEnabled(false);
			}
		}
	}
	
	private HashMap<Integer,Vector<Integer>> loadProjectPairsFromDB() throws SQLException
	{
		HashMap<Integer,Vector<Integer>> pairs = new HashMap<Integer,Vector<Integer>>();
		
		// Load the aligned project pairs
		UpdatePool pool = new UpdatePool(dbReader);
        ResultSet rs = pool.executeQuery("SELECT pairs.proj1_idx, pairs.proj2_idx FROM pairs " + 
        			" join projects as p1 on p1.idx=pairs.proj1_idx join projects as p2 on p2.idx=pairs.proj2_idx "  +
        				" where pairs.aligned=1 " 
        				);
        
        while ( rs.next() ) {
	        	int proj1Idx = rs.getInt("proj1_idx");
	        	int proj2Idx = rs.getInt("proj2_idx");
	        	
	        	if (!pairs.containsKey(proj1Idx))
	        		pairs.put( proj1Idx, new Vector<Integer>() );
	        	pairs.get(proj1Idx).add(proj2Idx);
        }
        
        rs.close();

        return pairs;
	}
	
	private HashMap<String,String> loadProjectProps(UpdatePool pool, int projIdx) throws SQLException
	{
		HashMap<String,String> projProps = new HashMap<String,String>();
        ResultSet rs = pool.executeQuery("SELECT name, value " +
        		"FROM proj_props WHERE proj_idx=" + projIdx);
        while ( rs.next() )
        		projProps.put( rs.getString("name"), rs.getString("value") );
        rs.close();
        return projProps;
	}
	
	private Vector<Project> loadProjectsFromDB() throws SQLException
	{
		Vector<Project> projects = new Vector<Project>();
		
		// Get loaded projects
		UpdatePool pool = new UpdatePool(dbReader);
        ResultSet rs = pool.executeQuery("SELECT idx, name, type FROM projects");
        while ( rs.next() ) {
	        	int nIdx = rs.getInt("idx");
	        	String strName = rs.getString("name");
	        	String strType = rs.getString("type");
	        	projects.add( new Project(nIdx, strName, strType) );
        }
        rs.close();
        
        // Load project properties from DB
        for (Project p : projects) {
	        	HashMap<String,String> projProps = loadProjectProps(pool, p.getID()); 
	        	
	        	p.setDisplayName(projProps.get("display_name"));
	        	p.setDescription(projProps.get("description"));
	        	p.setCategory(projProps.get("category"));
           	p.setOrderAgainst(projProps.get("order_against"));
           	
           	if (projProps.containsKey("grp_type") && 
           			!projProps.get("grp_type").equals("")) 
           				p.setChrLabel(projProps.get("grp_type"));
           	
           	// CAS504 add isMasked
           	boolean isMasked =  (projProps.containsKey("mask_all_but_genes") && 
           						 projProps.get("mask_all_but_genes").equals("1"));
           	p.setIsMasked(isMasked);
        }
        
        // Load count of groups for each project
        for (Project p : projects) {
	        rs = pool.executeQuery("SELECT count(*) FROM groups AS p " +
	        		"WHERE proj_idx=" + p.getID());
	        
	        rs.next();
	        int nGroups = rs.getInt(1);
	        p.setNumGroups(nGroups);
	        rs.close();
	        
	        rs = pool.executeQuery("select count(*) from pseudo_annot as pa join groups as g on g.idx=pa.grp_idx " +
	        		" where g.proj_idx=" + p.getID());
	        rs.first();
	        p.numAnnot = rs.getInt(1);
	        rs.close();
	        
	        // CAS500 for Summary and for orderProjects
	        rs = pool.executeQuery("select sum(length) from pseudos " +
					"join groups on groups.idx=pseudos.grp_idx  " +
					"where groups.proj_idx=" + p.getID());
			if (rs.next()) p.length = rs.getLong(1);
			rs.close();
        }
        
        sortProjects(projects);
        
        return projects;
	}
	private void loadProjectsFromDisk(Vector<Project> projects, String strDataPath, String dirName) {
		File root = new File(strDataPath + dirName); // dirName is same as strType p
	
		if (root == null || !root.isDirectory()) {
			System.err.println("Missing directory " + root.getName());
			return;
		}
		for (File f : root.listFiles()) {
			if (f.isDirectory() && !f.getName().startsWith(".")) {
				Project newProj = new Project(-1, f.getName(), dirName, f.getName() );
				newProj.setStatus( Project.STATUS_ON_DISK );
				
				if (!projects.contains(newProj)) 
				{
					newProj.getPropsFromDisk(f);
					projects.add( newProj );	
				}
			}
		}
	}
	
	private void sortProjects(Vector<Project> projects) {
		Collections.sort( projects, new Comparator<Project>() {
		    	public int compare(Project p1, Project p2) {
		    		return p1.getDBName().compareTo( p2.getDBName() );
		    	}
	    });
	}
	
	private void removeProjectFromDB(Project p) throws SQLException
	{
		UpdatePool pool = new UpdatePool(dbReader);
        pool.executeUpdate("DELETE from projects WHERE name='"+p.getDBName()+"' AND type='"+p.getType()+"'");
	}
	private void removeProjectAnnotationFromDB(Project p) throws SQLException
	{
		UpdatePool pool = new UpdatePool(dbReader);
        pool.executeUpdate("DELETE pseudo_annot.* from pseudo_annot, groups where pseudo_annot.grp_idx=groups.idx and groups.proj_idx=" + p.getID());
	}	
	
	private void removeAlignmentFromDB(Project p1, Project p2) throws Exception
	{
		UpdatePool pool = new UpdatePool(dbReader);
        pool.executeUpdate("DELETE from pairs WHERE proj1_idx="+p1.getID()+" AND proj2_idx="+p2.getID());
	}
	
	private boolean removeAlignmentFromDisk(Project p1, Project p2)
	{
		String path = Constants.getNameResultsDir(p1.getDBName(), p1.isFPC(), p2.getDBName());
		System.out.println("Remove " + path);
		return removeDir( new File( path ) );
	}
		
    // Deletes all files and sub-directories under dir.
    private boolean removeDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null)
            {
	            for (int i = 0;  i < children.length;  i++) {
	                boolean success = removeDir(new File(dir, children[i]));
	                if (!success) {
	                		System.err.println("Error deleting " + dir.getAbsolutePath());
	                    return false;
	                }
	            }
            }
        } 
        System.gc(); // closes any file streams that were left open to ensure delete() succeeds
        return dir.delete();
    }

    private void addNewProjectFromUI(Vector<Project> projects, String name, String type) {
		Project newProj = new Project(-1, name, type, name );
		newProj.setStatus( Project.STATUS_ON_DISK );
		if (!projects.contains(newProj))
		{
			Utilities.checkCreateDir(DATA_PATH + type + "/" + name, "PM add new project");
			projects.add( newProj );						
		}
    }
	
	private class AddProjectPanel extends JDialog {
		public AddProjectPanel(ActionListener addListener) {
			//Used for Java 1.5... 1.6 uses setModalityType(type)
			setModal(true);
			setResizable(false);
			getContentPane().setBackground(Color.WHITE);
			setTitle("Add Project");
			
			pnlMainPanel = new JPanel();
			pnlMainPanel.setLayout(new BoxLayout(pnlMainPanel, BoxLayout.PAGE_AXIS));
			pnlMainPanel.setBackground(Color.WHITE);
			
			txtName = new JTextField(20);
			txtName.setAlignmentX(Component.LEFT_ALIGNMENT);
			txtName.setMaximumSize(txtName.getPreferredSize());
			txtName.setMinimumSize(txtName.getPreferredSize());
			txtName.addCaretListener(new CaretListener() {
				public void caretUpdate(CaretEvent arg0) {
					btnAdd.setEnabled(isAddValid());
				}
			});
			
			cmbType = new JComboBox();
			cmbType.setAlignmentX(Component.LEFT_ALIGNMENT);
			cmbType.setBackground(Color.WHITE);
			cmbType.addItem("Select type...");
			cmbType.addItem("sequence");
			cmbType.addItem("fpc");
			cmbType.setSelectedIndex(1);
			cmbType.setMaximumSize(cmbType.getPreferredSize());
			cmbType.setMinimumSize(cmbType.getPreferredSize());
			cmbType.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					btnAdd.setEnabled(isAddValid());
				}
			});
			
			btnAdd = new JButton("Add");
			btnAdd.setBackground(Color.WHITE);
			btnAdd.addActionListener(addListener);
			btnAdd.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					dispose();
				}
			});
			btnAdd.setEnabled(false);
			
			btnCancel = new JButton("Cancel");
			btnCancel.setBackground(Color.WHITE);
			btnCancel.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					dispose();
				}
			});
			btnAdd.setPreferredSize(btnCancel.getPreferredSize());
			btnAdd.setMinimumSize(btnCancel.getPreferredSize());
			btnAdd.setMaximumSize(btnCancel.getPreferredSize());

			btnHelp = new JButton("Help");
			btnHelp.setBackground(Color.WHITE);
			btnHelp.addActionListener(newProjListener);
			JPanel tempRow = new JPanel();
			tempRow.setLayout(new BoxLayout(tempRow, BoxLayout.LINE_AXIS));
			tempRow.setAlignmentX(Component.CENTER_ALIGNMENT);
			tempRow.setBackground(Color.WHITE);
						
			tempRow.add(new JLabel("Name:"));
			tempRow.add(Box.createHorizontalStrut(5));
			tempRow.add(txtName);
			
			tempRow.setMaximumSize(tempRow.getPreferredSize());
			
			pnlMainPanel.add(Box.createVerticalStrut(10));
			pnlMainPanel.add(tempRow);
			
			tempRow = new JPanel();
			tempRow.setLayout(new BoxLayout(tempRow, BoxLayout.LINE_AXIS));
			tempRow.setAlignmentX(Component.CENTER_ALIGNMENT);
			tempRow.setBackground(Color.WHITE);

			tempRow.add(new JLabel("Type:"));
			tempRow.add(Box.createHorizontalStrut(5));
			tempRow.add(cmbType);
			
			pnlMainPanel.add(Box.createVerticalStrut(20));
			pnlMainPanel.add(tempRow);
			
			tempRow = new JPanel();
			tempRow.setLayout(new BoxLayout(tempRow, BoxLayout.LINE_AXIS));
			tempRow.setAlignmentX(Component.CENTER_ALIGNMENT);
			tempRow.setBackground(Color.WHITE);

			tempRow.add(btnAdd);
			tempRow.add(Box.createHorizontalStrut(20));
			tempRow.add(btnCancel);
			tempRow.add(Box.createHorizontalStrut(20));
			tempRow.add(btnHelp);
			
			pnlMainPanel.add(Box.createVerticalStrut(30));
			pnlMainPanel.add(tempRow);
			
			pnlMainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
			pnlMainPanel.setMaximumSize(pnlMainPanel.getPreferredSize());
			pnlMainPanel.setMinimumSize(pnlMainPanel.getPreferredSize());
			
			add(pnlMainPanel);
			
			pack();
		}
		
		public String getName() { return txtName.getText(); }
		public String getPType() 
		{ 
			String type = (String)cmbType.getSelectedItem(); 
			if (type.equals("sequence")) type = Constants.seqType;
			return type;
		}
		
		public void reset() { 
			txtName.setText("");
			cmbType.setSelectedIndex(1); // CAS504 make sequence the default
			btnAdd.setEnabled(false);
		}
		
		private boolean isAddValid() { return (txtName.getText().length() > 0 && 
				 txtName.getText().matches("^[\\w]+$") && cmbType.getSelectedIndex() > 0); }
		
		private JButton btnAdd = null;
		private JButton btnCancel = null, btnHelp = null;
		private JTextField txtName = null;
		private JComboBox cmbType = null;
		private JPanel pnlMainPanel = null;
	}

	private class ProjectLinkLabel extends LinkLabel {
		private Project project; // associated project
		
		public ProjectLinkLabel(String text, Project project, Color c) {
			super(text, c.darker().darker(), c);
			this.project = project;
		}
		
		public Project getProject() { return project; }
	}
	
	private class ProjectCheckBox extends JCheckBox {
		private Project project = null; // associated project
		
		public ProjectCheckBox(String text, Project p) {
			super(text);
			project = p;
			setFocusPainted(false);
			if (selectedProjects.contains(p)) {
				setSelected(true);
				setBackground( Color.yellow );
			}
			else
				setBackground( Color.white );
			addItemListener( checkboxListener );
		}
		
		public Project getProject() { return project; }
	}

	// *** Begin ComponentListener interface ***********************************
	public void componentResized(ComponentEvent e) {
    	int width = getWidth();
        int height = getHeight();
        if (width < MIN_WIDTH) width = MIN_WIDTH;
        if (height < MIN_HEIGHT) height = MIN_HEIGHT;
        setSize(width, height);
	}
	public void componentMoved(ComponentEvent e) { }
	public void componentShown(ComponentEvent e) { }
	public void componentHidden(ComponentEvent e) { }
	// *** End ComponentListener interface ************************************
}
