package symap.projectmanager.common;

/*****************************************************
 * The main frame and provides all the high level functions for it.
 * CAS508 moved the routines for alignment and synteny to AlignProjs, except for doAllPairs
 */
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.Collections;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.Comparator;
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
import symap.pool.Version;
import util.Cancelled;
import util.ErrorCount;
import util.DatabaseReader;
import util.ErrorReport;
import util.Utilities;
import util.ProgressDialog;
import util.LinkLabel;
import util.PropertiesReader;

import blockview.*;
import circview.*;

import symapQuery.Q;
import symapQuery.SyMAPQueryFrame;

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class ProjectManagerFrameCommon extends JFrame implements ComponentListener {
	/************************************************
	 * Command line arguments; set in symapCE.ProjectManagerFrame
	 */
	public static boolean inReadOnlyMode = false;
	public static String  MAIN_PARAMS =   "symap.config"; // default
	public static boolean TEST_TRACE = false;
	
	private static int     maxCPUs = -1;
	private static boolean isCat = true;
	private static boolean lgProj1st = false; // For Mummer; false sort on name; true sort on length
	
	/************************************************/
	private static final String HTML = "/html/ProjMgrInstruct.html";
	private static final String WINDOW_TITLE = "SyMAP " + SyMAP.VERSION + " ";
	private final String DB_ERROR_MSG = "A database error occurred, please see the Troubleshooting Guide at:\n" + SyMAP.TROUBLE_GUIDE_URL;	
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
	protected Vector<Project> availProjects = null;
	
	private Vector<Project> projects;
	private HashMap<Integer,Vector<Integer>> projPairs = null; // project idx mapping from database
	private SyProps globalPairProps = null;     // values from Parameter window
	private PairPropertyFrame ppFrame = null; // only !null if opened
	private PropertiesReader mProps;
	private Project curProj = null; // used only in the load-all-projects function
	private String dbName = null; // CAS508 append to LOAD.log
	
	private int totalCPUs=0;
	
	private JButton btnAllChrExp, btnSelDotplot, btnAllDotplot, btnAllPairs, btnSelClearPair,
		btnSelBlockView, btnSelCircView, btnAllQueryView, btnSelSummary,
	    btnAddProject = null, btnSelAlign, btnPairParams;
	
	private JSplitPane splitPane;
	private JComponent instructionsPanel;
	private JTable alignmentTable;
	
	private AddProjectPanel addProjectPanel = null;
	
	private JTextField txtCPUs =null;
	private JCheckBox checkCat=null;
	protected ActionListener explorerListener = null;
	
	/*****************************************************************/
	public ProjectManagerFrameCommon( String args[]) {
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
		
        setParams(args);
        
		initialize(Utilities.hasCommandLineOption(args, "-v"));
		
		instructionsPanel = createInstructionsPanel();
		
		splitPane = new JSplitPane( JSplitPane.HORIZONTAL_SPLIT,
									createProjectPanel(), instructionsPanel );
		splitPane.setDividerLocation(MIN_DIVIDER_LOC);
		
		add(splitPane);
		
		setSize(MIN_WIDTH, MIN_HEIGHT);
		setLocationRelativeTo(null); // CAS513 center frame
		
		addComponentListener(this); // hack to enforce minimum frame size
	}
	/******************************************************************
	 * CAS511 the perl script was removed, so the parameters need to be written here
	 */
	public static void prtParams(String args[]) {
		if (Utilities.hasCommandLineOption(args, "-r")) {
			System.out.println("Usage:  ./viewSymap [options]");
			System.out.println("  -c string : filename of config file (to use instead of symap.config)");
		}
		else {
			System.out.println("Usage:  ./symap [options]");
			System.out.println("  -h        : show help to terminal and exit");
			System.out.println("  -v        : check MySQL for important settings");
			System.out.println("  -c string	: filename of config file (to use instead of symap.config)");
	
			System.out.println("\nAlignment:");
			System.out.println("  -p N		: number of CPUs to use");
			System.out.println("  -s		: print stats for debugging");
			System.out.println("  -o		: use original draft ordering algorithm");
		}
	}
	// these are listed to terminal in the 'symap' perl script.
	private void setParams(String args[]) { // CAS505 used here and by ProjectManagerFrame3D
		if (args.length > 0) {
			if (Utilities.hasCommandLineOption(args, "-r")) {// used by viewSymap
				inReadOnlyMode = true; // no message to terminal
			}
			if (Utilities.hasCommandLineOption(args, "-s")) {// not shown in -h help
				System.out.println("-s Print Stats");
				Constants.PRT_STATS = true;
			}
			
			if (Utilities.hasCommandLineOption(args, "-t")) {// not shown in -h help
				System.out.println("-t Debug output");
				Constants.TRACE = true;
			}
			if (Utilities.hasCommandLineOption(args, "-o")) {// CAS505
				System.out.println("-o Use the original version of draft ordering");
				Constants.NEW_ORDER = false;
			}
			if (Utilities.hasCommandLineOption(args, "-d")) {// not shown in -h help
				System.out.println("-d Extra output for SyMAP Query");
				Q.TEST_TRACE = true;
				TEST_TRACE = true; // CAS12
			}
			
			if (Utilities.hasCommandLineOption(args, "-a")) {// not shown in -h help
				System.out.println("-a Align largest project to smallest");
				lgProj1st = true;
			}
			if (Utilities.hasCommandLineOption(args, "-p")) { // #CPU
				String x = Utilities.getCommandLineOption(args, "-p"); //CAS500
				try {
					maxCPUs = Integer.parseInt(x);
					System.out.println("-p Max CPUs " + maxCPUs);
				}
				catch (Exception e){ System.err.println(x + " is not an integer. Ignoring.");}
			}
			if (Utilities.hasCommandLineOption(args, "-c")) {// CAS501
				MAIN_PARAMS = Utilities.getCommandLineOption(args, "-c");
				System.out.println("-c Configuration file " + MAIN_PARAMS);
			}
		}
	}
	private DatabaseReader getDatabaseReader()  { // CAS508 remove throw
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
		String cpus 		   = mProps.getProperty("nCPUs"); // CAS500

		if (db_server != null) 			db_server = db_server.trim();
		if (db_name != null) 			db_name = db_name.trim();
		if (db_adminuser != null) 		db_adminuser = db_adminuser.trim();
		if (db_adminpasswd != null) 	db_adminpasswd = db_adminpasswd.trim();
		if (db_clientuser != null) 		db_clientuser = db_clientuser.trim();
		if (db_clientpasswd != null) 	db_clientpasswd = db_clientpasswd.trim();
		dbName = db_name;

		try { // CAS508 stop nested exception on DatabaseUser calls
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
			
			String mummer 			= mProps.getProperty("mummer_path"); // CAS508 
			if (mummer!=null && mummer!="") {
				System.out.println("MUMmer path: " + mummer);
				Constants.setMummer4Path(mummer);
			}
			
			if (cpus!=null && maxCPUs<=0) {
				try {
					maxCPUs = Integer.parseInt(cpus.trim()); // CAS508 add trim
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
	        else { // CAS511 add if exist
	        	if (!DatabaseUser.existDatabase(db_server, db_name, db_clientuser, db_clientpasswd)) {
		        	System.err.println("*** Database '" + db_name + "' does not exist");
		        	System.exit(-1);
		        }
	        }
	        String url = DatabaseUser.getDatabaseURL(db_server, db_name);
	        String user = (inReadOnlyMode ? db_clientuser : db_adminuser);
	        String pw   = (inReadOnlyMode ? db_clientpasswd : db_adminpasswd);
	        return DatabaseUser.getDatabaseReader(SyMAPConstants.DB_CONNECTION_PROJMAN,
	        										url, user, pw, null);
		}
		catch (Exception e) {ErrorReport.print("Error getting connection"); return null;}
	}
	
	private void initialize(boolean checkSQL) {
		try {
			if (dbReader == null) {
				dbReader = getDatabaseReader();
				
				new Version(new UpdatePool(dbReader));
				
				if (checkSQL)
					DatabaseUser.checkVariables(dbReader.getConnection(), true); // CAS511 add
			}
			
			Utilities.setResClass(this.getClass());
			Utilities.setHelpParentFrame(this);
			
			projPairs = loadProjectPairsFromDB();
			projects = loadProjectsFromDB();
			
			if (globalPairProps==null) {
				globalPairProps = new SyProps();
			}
			
			if (!inReadOnlyMode) { // CAS500
				String strDataPath = Constants.dataDir;
				if (Utilities.dirExists(strDataPath)) { // CAS511 - okay if /data is missing
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
					
					Utilities.checkCreateDir(Constants.logDir, true /* bPrt */);
					
					// CAS511 quit creating data/seq, data/fpc, etc on startup
				
					loadProjectsFromDisk(projects, strDataPath,  Constants.seqType);
					loadProjectsFromDisk(projects, strDataPath,  Constants.fpcType);
				}
			}
			
			if (availProjects == null)
				availProjects = new Vector<Project>();
			else { // refresh already selected projects (including from database)
				Vector<Project> newSelectedProjects = new Vector<Project>();
				for (Project p1 : availProjects) {
					for (Project p2 : projects) {
						if ( p1.equals(p2) ) {
							newSelectedProjects.add(p2);
							break;
						}
					}
				}
				availProjects = newSelectedProjects;
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
					if ( !Utilities.tryOpenURL(e.getURL().toString()) ) 
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
		sortProjects(availProjects); // redundant?
		Vector<String> columnNames = new Vector<String>();
		Vector<Vector<String>> rowData = new Vector<Vector<String>>();

		int maxProjName = 0;
		for (Project p1 : availProjects) {
			maxProjName = Math.max(p1.getDisplayName().length(), maxProjName );
		}
		String blank = "";
		for(int q = 1; q <= maxProjName + 20; q++) blank += " ";
		
		columnNames.add(blank);
		for (Project p1 : availProjects) {
			if (p1.getStatus() == Project.STATUS_ON_DISK) continue; // skip if not in DB
			
			int id1 = p1.getID();
			columnNames.add( p1.getDisplayName() );
			Vector<String> row = new Vector<String>();
			row.add( p1.getDisplayName() );
		
			for (Project p2 : availProjects) {
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
	private Project getProjectByDisplayName( String strName ) {
		for (Project p : projects)
			if (p.getDisplayName().equals( strName ))
				return p;
		return null;
	}
	// first column is project names. The top row of project names part of table.
	private int getNumCompleted(boolean ignSelf) {
		int count = 0;
		for (int row = 0;  row < alignmentTable.getRowCount();  row++) {
			for (int col = 0;  col < alignmentTable.getColumnCount();  col++) {
				if (col > (row+1)) continue;
				if (ignSelf && (row+1)==col) continue;
				
				String val = (String)alignmentTable.getValueAt(row, col);
				if (val != null && val.contains(TBL_DONE)) count++;
			}
		}	
		return count;
	}
	private boolean getDoAllPairs() {
		for (int row = 0;  row < alignmentTable.getRowCount();  row++) {
			for (int col = 0;  col < alignmentTable.getColumnCount();  col++) {
				if (col >= (row+1)) continue;
				
				String val = (String)alignmentTable.getValueAt(row, col);
				if (val==null) return true;
				if (val.contains(TBL_ADONE)) return true;
				if (val.contains(TBL_QDONE)) return true;
			}
		}
		return false;
	}
	private boolean isAllSeq() {
		for (Project p : availProjects) 
			if (p.isFPC()) return false;
		return true;
	}
	
	/***********************************************************
	 * CAS505 rewrote
	 */
	private void updateEnable() {
		if (alignmentTable == null) return;
		
		Project[] projects = getSelectedAlignmentProjects();
		btnPairParams.setEnabled(projects!=null);  // CAS v507 must select one 
		
		int numProj =  alignmentTable.getRowCount();
		btnAllPairs.setEnabled(numProj>1 && getDoAllPairs());
		
		int numDone = getNumCompleted(false); // do not ignore self
		btnAllChrExp.setEnabled(numDone>0);
		
		numDone = getNumCompleted(true); // ignore selfs
		btnAllDotplot.setEnabled(numDone>0);
		btnAllQueryView.setEnabled(numDone>0 && isAllSeq());
		
		if ((projects!=null)) {
			String val = getSelectedAlignmentValue();
			
			boolean bothFPC =  (projects[0].isFPC() && projects[1].isFPC());
			boolean allDone =  (val!=null && val.contains(TBL_DONE));
			boolean partDone = (val!=null && (!val.contains(TBL_ADONE) || !val.contains(TBL_QDONE)));
			
			if (allDone) btnSelAlign.setText("Selected Pair (Redo)");	
			else 		 btnSelAlign.setText("Selected Pair");	
			
			btnSelAlign.setEnabled(!bothFPC);
			btnSelClearPair.setEnabled(allDone || partDone);
			btnSelDotplot.setEnabled( allDone ); 
			btnSelCircView.setEnabled(allDone);  
			btnSelSummary.setEnabled(allDone);   
			btnSelBlockView.setEnabled(allDone); 
		}
		else {
			btnSelAlign.setEnabled(false);
			btnSelClearPair.setEnabled(false);
			btnSelDotplot.setEnabled(false); 
			btnSelCircView.setEnabled(false);  
			btnSelSummary.setEnabled(false);   
			btnSelBlockView.setEnabled(false);
		}
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
				updateEnable();
		}
	};
	
	private TableColumnModelListener tableColumnListener = new TableColumnModelListener() { // called after tableRowListener
		public void columnAdded(TableColumnModelEvent e) { }
		public void columnMarginChanged(ChangeEvent e) { }
		public void columnMoved(TableColumnModelEvent e) { }
		public void columnRemoved(TableColumnModelEvent e) { }
		public void columnSelectionChanged(ListSelectionEvent e) {
			if (!e.getValueIsAdjusting())
				updateEnable();
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
		for (Project p : availProjects) {
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
		for (Project p : availProjects) {
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
						String.format("%s%d    Bases: %,d    %s", 
								chrLbl, p.getNumGroups(), p.getLength(), p.getAnnoStr());
					
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
				btnRemoveFromDisk = new ProjectLinkLabel("Remove from disk", p, Color.red);
				btnRemoveFromDisk.addMouseListener( doRemoveDisk );

				btnLoadOrRemove = new ProjectLinkLabel("Load project", p, Color.red);
				btnLoadOrRemove.addMouseListener( doLoad );

				btnReloadParams = new ProjectLinkLabel("Parameters", p, Color.blue);
				btnReloadParams.addMouseListener( doSetParamsNotLoaded ); 
			}
			else { // CAS42 1/1/18 was just "Remove" which is unclear; CAS505 removed 'project'
				btnLoadOrRemove = new ProjectLinkLabel("Remove from database", p, Color.blue);
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
		
			btnSelAlign = new JButton("Selected Pair");
			btnSelAlign.setVisible(true);
			btnSelAlign.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					Project[] projects = getSelectedAlignmentProjects();
					
					if (checkPairs(projects[0], projects[1])) {
						int nCPU = getCPUs();
						if (nCPU == -1) return;
						
						if (!checkProj(projects[0], projects[1])) return;
						new AlignProjs().run(getInstance(), projects[0], projects[1], 
								false, dbReader, mProps, nCPU, checkCat.isSelected());
					}
				}
			} );
			
			btnSelClearPair = new JButton("Clear Pair");
			btnSelClearPair.setVisible(true);
			btnSelClearPair.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					Project[] projects = getSelectedAlignmentProjects();
					progressClearPair(projects[0], projects[1]);
				}
			} );	
			
			btnAllPairs = new JButton("All Pairs");
			btnAllPairs.setVisible(true);
			btnAllPairs.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent e) { 
					doAllPairs();
				}
			});
			
			btnSelDotplot = new JButton("Dot Plot");
			btnSelDotplot.setVisible(true);
			btnSelDotplot.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent e) { 
					showDotplot();
				}
			});

			btnSelBlockView = new JButton("Block View");
			btnSelBlockView.setVisible(true);
			btnSelBlockView.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent e) { 
					showBlockView();
				}
			});

			btnSelCircView = new JButton("Circle View");
			btnSelCircView.setVisible(true);
			btnSelCircView.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent e) { 
					showCircleView();
				}
			});
			
			btnSelSummary = new JButton("Summary");
			btnSelSummary.setVisible(true);
			btnSelSummary.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent e) { 
					showSummary();
				}
			});
		
			btnAllChrExp = new JButton("Chromosome Explorer");
			btnAllChrExp.setVisible(true); 
			btnAllChrExp.addActionListener( explorerListener);
			
			btnAllDotplot = new JButton("Dot Plot");
			btnAllDotplot.setVisible(true);
			btnAllDotplot.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent e) { 
					showAllDotPlot();
				}
			});

			btnAllQueryView = new JButton("SyMAP Queries");
			btnAllQueryView.setVisible(true);
			btnAllQueryView.addActionListener(new ActionListener() {
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
			
			JLabel lbl1 = new JLabel("Alignment & Synteny");
			JLabel lbl2 = new JLabel("Display for Selected Pair");
			JLabel lbl3 = new JLabel("Display for All Pairs");
			JLabel lbl4 = new JLabel("For Seq-to-Seq Pairs");
			Dimension d = lbl2.getPreferredSize();
			lbl1.setPreferredSize(d);
			lbl3.setPreferredSize(d);
			lbl4.setPreferredSize(d);
			
			totalCPUs = Runtime.getRuntime().availableProcessors(); // CAS42 12/6/17
			if (maxCPUs<=0) maxCPUs = totalCPUs;
			if (txtCPUs != null) {
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
			checkCat = new JCheckBox("Concat", isCat);
			checkCat.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					isCat = checkCat.isSelected();
				}
			});

			mainPanel.add( new JSeparator() );
			mainPanel.add( Box.createVerticalStrut(15) );
			mainPanel.add(instructText); 
			mainPanel.add( Box.createVerticalStrut(15) );
			if (!inReadOnlyMode) {
				Component cpu = createHorizPanel( new Component[] 
						{ tableScroll, new JLabel("   CPUs:"), txtCPUs, new JLabel("  "), checkCat},5 );
				mainPanel.add(cpu);			
			}
			else {	
				mainPanel.add( createHorizPanel( new Component[] 
						{ tableScroll, new JLabel(" ")}, 5 ));			
			}
			btnPairParams = new JButton("Parameters");
			btnPairParams.addActionListener(showPairProps);
			
			if (!inReadOnlyMode)
			{
				mainPanel.add( Box.createRigidArea(new Dimension(0,15)) );	
				mainPanel.add( createHorizPanel( new Component[] { lbl1,
						btnAllPairs, btnSelAlign, btnSelClearPair,  btnPairParams}, 5) ); // CAS507 put allpairs first since btnPair is for select only now
			}
			mainPanel.add( Box.createRigidArea(new Dimension(0,15))  );
			mainPanel.add( createHorizPanel( new Component[] {lbl2, btnSelDotplot, btnSelBlockView, btnSelCircView, btnSelSummary }, 5) );

			mainPanel.add( Box.createRigidArea(new Dimension(0,15))  );
			mainPanel.add( createHorizPanel( new Component[] {lbl3, btnAllChrExp, btnAllDotplot }, 5) );

			mainPanel.add( Box.createRigidArea(new Dimension(0,15))  );
			mainPanel.add( createHorizPanel( new Component[] {lbl4, btnAllQueryView }, 5) );
		} else {
			mainPanel.add( Box.createVerticalGlue() );
			mainPanel.add( createTextArea("") ); // kludge to fix layout problem			
		}
				
		updateEnable();
		
		return mainPanel;
	}
	/******************************************************
	 * CAS506 - add some checks before starting order against project
	 */
	private boolean checkPairs(Project mProj1, Project mProj2) {
		String n1, n2, o;
		
		if (mProj1.isFPC() || mProj2.isFPC()) return true;
		
		String o1 = mProj1.getOrderAgainst();
		String o2 = mProj2.getOrderAgainst();
		
		if (o1==null || o2==null) return true; // CAS506 got null on linux
		if (o1.equals("") && o2.equals("")) return true;
		
		if (!o1.equals("") && !o2.equals("")) {
			String msg = mProj1.getDBName() + " and " + mProj2.getDBName() + " both have 'ordered against'" +
					"\nOnly one can have this set";
			Utilities.showErrorMessage(msg);
			return false;
		}
		
		if (o2.equals(""))      {o=o1; n1=mProj1.getDBName(); n2=mProj2.getDBName(); }
		else if (o1.equals("")) {o=o2; n1=mProj2.getDBName(); n2=mProj1.getDBName(); }
		else return true;
		
		try {
			if (!o.equals(n2)) {
				String msg = n1 + " 'order against' set to " + o + 
						"\nNo ordering with alignment to  " + n2 +
						"\n(You may want to Cancel and set 'order against' to " + n2 +")\n";
				if (!Utilities.showContinue("Order against", msg)) return false;
			}
		
			String ordProjName = n1 + OrderAgainst.orderSuffix; // directory uses dbname, no display name
			String ordDirName =  Constants.seqDataDir + ordProjName;
			File   ordDir = new File(ordDirName);
	
			if (ordDir.exists()) {
				String msg = "Directory exists: " + ordProjName  + "\nIt will be over-written.\n";
				if (!Utilities.showContinue("Order against", msg)) return false;
			}	
			
			return true;
		}
		catch (Exception e) {ErrorReport.print(e, "Checking order against"); return false;}
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
	private void showNewProjectHelp(){
		Utilities.showHTMLPage(addProjectPanel,"New Project Help", "/html/NewProjHelp.html");	
	}
	private void showHelp()  {
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
					
					if ( project.isFPC() ) {
						success = FPCLoadMain.run( pool, progress, projName );
						
						if (!success || Cancelled.isCancelled()) { // CAS500
							System.err.println("Cancel load");
							return;
						}
					}
					else if ( project.isPseudo() )  {
						success = SeqLoadMain.run( pool, progress, projName );
					
						if (success && !Cancelled.isCancelled())  {
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
					if (!progress.wasCancelled())
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
				progress.appendText("\n>>> Load all projects <<<"); 
				
				for (Project project : availProjects) {
					if (project.getStatus() != Project.STATUS_ON_DISK) continue;
				
					curProj = project;
					try {
						UpdatePool pool = new UpdatePool(dbReader);
						String projName = project.getDBName();
						
						if ( project.isFPC() ) {
							success = FPCLoadMain.run( pool, progress, projName );
						}
						else if ( project.isPseudo() )  {
							success = SeqLoadMain.run( pool, progress, projName );
						
							if (success && !Cancelled.isCancelled())  {
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
					progress.finish(success);
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
			SyMAPQueryFrame qFrame = new SyMAPQueryFrame(DatabaseReader.getInstance(SyMAPConstants.DB_CONNECTION_SYMAP_3D, dbReader), false);
			for (Project p : availProjects) {
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
					
					if ( project.isPseudo() ) {
						AnnotLoadMain annot = new AnnotLoadMain(pool, progress, mProps);
						success = annot.run( projName );
					}
				}
				catch (Exception e) {
					success = false;
					ErrorReport.print(e, "Run Reload");
				}
				finally {
					if (!progress.wasCancelled()) // CAS512 add check
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
			String msg = "Remove " + p.getDisplayName() + " from database"; // CAS513 add dbname
			if (!removeAlignmentsCheck(p)) {
				if (!progressConfirm2(msg,msg)) return;
				progressRemoveProject(0, p );
			}
			else {
				int rc = progressConfirm3(msg,msg +
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
					"\nbut not the alignments.")) return; // CAS512 remove MUMmer and BLAT
			
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
	/****************************************************************
	 * 
	 */
	private MouseListener doReloadParams = new MouseAdapter() {
		public void mouseClicked(MouseEvent e) {
			Project theProject = ((ProjectLinkLabel)e.getSource()).getProject();
			
			PropertyFrame propFrame = new PropertyFrame(
				getRef(), theProject.isPseudo(), theProject.getDisplayName(), 
				theProject.getDBName(), dbReader, true, theProject.getPathName());
			
			propFrame.setVisible(true);
			
			if (propFrame.isSaveToDB()) {			
				theProject.updateParameters(new UpdatePool(dbReader),null);
			}
			if (propFrame.isChgGrpPrefix()) { // CAS513 add the ability to change GrpPrefix after load
				theProject.updateGrpPrefix(new UpdatePool(dbReader), null);
			}
			refreshMenu();		
		}
	};
	private MouseListener doSetParamsNotLoaded = new MouseAdapter() {
		public void mouseClicked(MouseEvent e) {
			Project theProject = ((ProjectLinkLabel)e.getSource()).getProject();
			
			PropertyFrame propFrame = new PropertyFrame(
				getRef(), theProject.isPseudo(), theProject.getDisplayName(), 
				theProject.getDBName(), dbReader, false, theProject.getPathName());
			
			propFrame.setVisible(true);
			
			refreshMenu(); // CAS504
		}		
	};

	private ProjectManagerFrameCommon getRef() { return this; }

	private boolean removeAlignmentsCheck(Project p) {
		try {
			File[] topLevels = new File[2];
	
			if (p.isFPC()) {
				topLevels[0] = new File(Constants.getNameResultsDir(true));
				topLevels[1] = null;
			}
			else {
				topLevels[0] = new File(Constants.getNameResultsDir(true));
				topLevels[1] = new File(Constants.getNameResultsDir(false));
			}
			
			for (File top : topLevels){
				if (top == null || !top.exists()) continue; // CAS511 may not have /data/seq_results directory
				Vector<File> alignDirs = new Vector<File>();
				
				for (File f : top.listFiles()) {
					if (f.isDirectory()) {
						if (p.isFPC() && f.getName().startsWith(p.getDBName()+Constants.projTo)) {
							alignDirs.add(f); // for fpc, it has to come first
						}
						else{
							if	(f.getName().startsWith(p.getDBName()+ Constants.projTo) ||
									f.getName().endsWith(Constants.projTo+p.getDBName())) {
								alignDirs.add(f);
							}
						}
					}
					if (alignDirs.size() > 0) return true;
				}
			}
		} catch (Exception e) {ErrorReport.print(e, "Remove alignment files");} // CAS511 add try-catch
		return false;
	}
	private boolean removeAllAlignmentsFromDisk(Project p, ProgressDialog progress) {
		try {
			if (progress!=null)
				progress.appendText("Removing alignments from disk - " + p.getDBName());		
			
			File[] topLevels = new File[2];
			
			if (p.isFPC()) {
				topLevels[0] = new File(Constants.getNameResultsDir(true)); // check fpc_results 
				topLevels[1] = null;
			}
			else {
				topLevels[0] = new File(Constants.getNameResultsDir(false)); // check seq_results
				topLevels[1] = new File(Constants.getNameResultsDir(true));  // check fpc_results
			}
			boolean success = true;
			String projTo = Constants.projTo;
			
			for (File top : topLevels) {
				if (top == null  || !top.exists()) continue;
				
				Vector<File> alignDirs = new Vector<File>();
				
				for (File f : top.listFiles()) {
					if (f.isDirectory()) {
						if (p.isFPC() && f.getName().startsWith(p.getDBName()+ projTo)) { // fpc name must come first
							alignDirs.add(f); 
						}
						else {
							if	(f.getName().startsWith(p.getDBName()+ projTo) ||
								 f.getName().endsWith(projTo+p.getDBName())) {
								alignDirs.add(f);
							}
						}
					}
				}
				if (alignDirs.size() == 0) return true;
				
				for (File f : alignDirs) {
					if (progress!=null)
						progress.appendText("   Removing alignment - " + f.getName());	
					else 
						System.out.println("   Removing alignment - " + f.getName());
					success &= removeDir(f);
				}
			}
			return success;
		} catch (Exception e) {ErrorReport.print(e, "Remove all alignment files"); return false;}
	}
	
	private void progressRemoveProject(final int rc, final Project project) {
		ErrorCount.init();
		FileWriter fw = buildLogLoad(); // CAS500
		final ProgressDialog progress = new ProgressDialog(this, 
				"Removing Project From Database",
				"Removing project from database '" + project.getDBName() + "' ...", 
				false, false, fw);
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
		// CAS515 add option to only clear from database
		String msg = "Clear pair " + p1.getDBName() + " to " + p2.getDBName();
		int rc = progressConfirm3("Clear project", msg +
				"\nOnly: Delete synteny from database only" +
				"\nAll : Delete synteny from database and remove previous alignments");
		if (rc==0) return;
		
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
					try {
						Thread.sleep(1000);
					}
					catch(Exception e){}

					removeAlignmentFromDB(p1,p2);
					if (rc==2) removeAlignmentFromDisk(p1,p2);
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
				false, false, fw);
		progress.closeIfNoErrors();
		
		Thread rmThread = new Thread() {
			public void run() {
				boolean success = true;
				progress.appendText("Remove project from disk - " + project.getDBName());
				
				try {
					try { // CAS505 see if this stops it from crashing on Linux in progress.start
						Thread.sleep(1000);
					}
					catch(Exception e){}

					removeAllAlignmentsFromDisk(project, null); // CAS500 in case not earlier removed
					
					String path = (project.isFPC()) ? Constants.fpcDataDir : Constants.seqDataDir;
					path += project.getDBName();
					
					removeDir(new File(path)); // CAS505 not removing topdir on Linux, so try again
					if (new File(path).exists()) new File(path).delete();
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
		
		progress.start( rmThread ); // FIXME crashed on linux
		
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
					
					if (success && !Cancelled.isCancelled()) {
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
			if (p1.getLength() < p2.getLength()) 
				return new Project[] { p2, p1 }; 
			else
				return new Project[] { p1, p2 };	
		}
		else { // original -- compatible with existing symap databases
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
	public String buildLogAlignDir(Project p1, Project p2) {
		try
		{
			String logName = Constants.logDir + p1.getDBName() + Constants.projTo + p2.getDBName();
			if (Utilities.dirExists(logName))
				System.out.println("Log alignments in directory: " + logName);
			Utilities.checkCreateDir(logName, true /* bPrt */);
			return logName + "/";
		}
		catch (Exception e){ErrorReport.print(e, "Creating log file");}
		return null;
	}
	// CAS500 New log file for load
	private boolean first=true;
	private FileWriter buildLogLoad()
	{
		FileWriter ret = null;
		try {
			String name = dbName + "_" + Constants.loadLog; // CAS508 prefix with database name
			 
			File pd = new File(Constants.logDir);
			if (!pd.isDirectory()) pd.mkdir();
			
			File lf = new File(pd,name);
			if (!lf.exists())
				System.out.println("Create log file: " + Constants.logDir + name);
			else if (first) {
				System.out.println("Append to log file: " + Constants.logDir + name + 
						" (Length: " + Utilities.kMText(lf.length()) + ")"); // CAS508 add, CAS511 add prt
				first=false;
			}
			ret = new FileWriter(lf, true);	
		}
		catch (Exception e) {ErrorReport.print(e, "Creating log file");}
		return ret;
	}
	/****************************************************
	 * Methods for renewing pairIdx and saving pair parameters
	 */
	public int getPairIdx(int id1, int id2) {
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
	
	public int pairIdxCreate(int id1, int id2, UpdatePool pool) {
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
	
	// Called when Pair Parameter window opened
	public SyProps getPairPropsFromDB(int id1, int id2) {
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
	
	
	/***********************************************
	 * XXX All Pairs is selected
	 * CAS506 added checkPairs logic
	 */
	private void doAllPairs() 
	{
		Cancelled.init();
		if (alignmentTable == null) return;
		
		Vector <Project> pList1 = new Vector <Project> ();
		Vector <Project> pList2 = new Vector <Project> ();
		
		int nRows = alignmentTable.getRowCount();
		int nCols = alignmentTable.getColumnCount();
		if (nRows == 0 || nCols == 0) return;
		
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
				if (entry.equals("-")) continue;
				else if (entry.equals(TBL_DONE)) doThis = false;
				else if (entry.equals(TBL_ADONE) || entry.equals(TBL_QDONE)) doThis = true;	
				else if (entry.equals("")) doThis = true;	
				
				if (doThis)
				{
					Project p1 = getProjectByDisplayName( strRowProjName );
					Project p2 = getProjectByDisplayName( strColProjName );
					Project[] ordered = orderProjects(p1,p2);
					p1 = ordered[0];
					p2 = ordered[1];
					if (p1.getID() != p2.getID())
					{
						if (checkPairs(p1, p2)) { // CAS506 
							pList1.add(p1);
							pList2.add(p2);
						}
						else {
							System.out.println("Abort All Pairs");
							return;
						}
					}
				}
			}	
		}
		int maxCPUs = getCPUs();
		if (maxCPUs==-1) return;
		
		System.out.println("\n---- Start all pairs: processing " + pList1.size() + " project pairs ---");
		for (int i=0; i<pList1.size(); i++) {
			if (!checkProj(pList1.get(i), pList2.get(i))) return;
			new AlignProjs().run(this, pList1.get(i), pList2.get(i), true, dbReader, mProps, maxCPUs, checkCat.isSelected());
		}
		System.out.println("All Pairs complete. ");
	}
	private int getCPUs() {
		int maxCPUs = 1;
		try { maxCPUs = Integer.parseInt(txtCPUs.getText());}
		catch (Exception e) {
			Utilities.showErrorMessage("Please enter a valid value for number of CPUs to use.");
			return -1;
		}
		if (maxCPUs <= 0) maxCPUs = 1;
		return maxCPUs;
	}
	// CAS511 create directories if alignment is initiated
	// An alignment may be in the database, yet no /data directory. The files will be rewritten, so the directories are needed.
	private boolean checkProj(Project p1, Project p2) { 
		try {
			String strDataPath = DATA_PATH;
			Utilities.checkCreateDir(strDataPath, true);
			if (p1.isFPC() || p2.isFPC()) {
				Utilities.checkCreateDir(Constants.fpcDataDir, true);
				Utilities.checkCreateDir(Constants.fpcRunDir,  true);
			}
			else Utilities.checkCreateDir(Constants.seqRunDir,  true);
			
			Utilities.checkCreateDir(Constants.seqDataDir, true); 
			
			return true;
		}
		catch (Exception e){return false;}
	}
	public void refreshMenu() {
		Utilities.setCursorBusy(this, true);
		
		initialize(false);
		
		splitPane.setLeftComponent( createProjectPanel() );
		if (availProjects.size() > 0)
			splitPane.setRightComponent( createSummaryPanel() );
		else
			splitPane.setRightComponent( instructionsPanel );
		
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
		
		// CAS513 changed for Vector to TreeSet so alphabetical display
		TreeMap<String, TreeSet<Project>> cat2Proj = new TreeMap<String, TreeSet<Project>>();
		for (Project p : projects) {
			String category = p.getCategory();
			if (category == null || category.length() == 0)
				category = "Uncategorized";
			if (!cat2Proj.containsKey(category))
				cat2Proj.put(category, new TreeSet<Project>());
			cat2Proj.get(category).add(p);
		}
		
		// CAS513 Figure out what to show 
		Set <String> categories = cat2Proj.keySet();
		TreeMap <String, Integer> showMap = new TreeMap <String, Integer> ();
		for (String cat : categories) {
			for (Project p : cat2Proj.get(cat)) {
				if (inReadOnlyMode && p.getCntSynteny().equals("")) continue; // CAS513 nothing to view
				
				if (showMap.containsKey(cat)) showMap.put(cat, showMap.get(cat)+1);
				else 						  showMap.put(cat, 1);
			}
		}
		for (String cat : showMap.keySet()) {
			if (showMap.get(cat)==0) continue;
			
			subPanel.add( new JLabel(cat) );
			
			for (Project p : cat2Proj.get(cat)) {
				if (inReadOnlyMode && p.getCntSynteny().equals("")) continue; // CAS513 nothing to view
					
				ProjectCheckBox cb = null;
				String name = p.getDisplayName();
				boolean bLoaded = (p.getStatus() != Project.STATUS_ON_DISK);
				if (bLoaded) 
					name += " (" + p.getLoadDate() + ") " + p.getCntSynteny(); // CAS513 change from (not loaded) 
				cb = new ProjectCheckBox( name, p );
				Font f = new Font(cb.getFont().getName(), Font.PLAIN, cb.getFont().getSize());
				cb.setFont(f); 
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
		try{
			BlockViewFrame frame = new BlockViewFrame(dbReader, projXIdx, projYIdx);
			frame.setMinimumSize( new Dimension(MIN_WIDTH, MIN_HEIGHT) );
			frame.setVisible(true);
		}
		catch (Exception e) {
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
		for (Project p : availProjects) {
			if (p.getStatus() != Project.STATUS_ON_DISK) {
				nProj++;
			}
		}
		int[] pids = new int[nProj];
		int i = 0;
		for (Project p : availProjects) {
			if (p.getStatus() != Project.STATUS_ON_DISK) {
				pids[i] = p.getID();
				i++;
			}
		}

		// Open dot plot
		DotPlotFrame frame = new DotPlotFrame(dbReader, pids, null, null, null, true);
		frame.setSize( new Dimension(MIN_WIDTH, MIN_HEIGHT) );
		frame.setVisible(true);
	}
	private ItemListener checkboxListener = new ItemListener() {
		public void itemStateChanged(ItemEvent e) {
			ProjectCheckBox cb = (ProjectCheckBox)e.getSource();
			Project p = cb.getProject();
			boolean changed = false;
			
			if ( e.getStateChange() == ItemEvent.SELECTED ) {
				if ( !availProjects.contains(p) )
					availProjects.add(p);
				cb.setBackground(Color.yellow); // highlight
				changed = true;
			}
			else if ( e.getStateChange() == ItemEvent.DESELECTED ) {
				availProjects.remove(p);
				cb.setBackground(Color.white); // un-highlight
				changed = true;
			}
			
			if (changed) {
				sortProjects( availProjects );
				if (alignmentTable != null) {
					alignmentTable.clearSelection();	
				}

				if ( availProjects.size() > 0 )
					splitPane.setRightComponent( createSummaryPanel() ); // regenerate since changed
					// Note: recreating each time thrashes heap, but probably negligible performance impact
				else
					splitPane.setRightComponent( instructionsPanel );
			}
		}
	};
	
	
	private HashMap<Integer,Vector<Integer>> loadProjectPairsFromDB() throws SQLException
	{
		HashMap<Integer,Vector<Integer>> pairs = new HashMap<Integer,Vector<Integer>>();
		
		// Load the aligned project pairs
		UpdatePool pool = new UpdatePool(dbReader);
        ResultSet rs = pool.executeQuery("SELECT pairs.proj1_idx, pairs.proj2_idx FROM pairs " + 
        			" join projects as p1 on p1.idx=pairs.proj1_idx join projects as p2 on p2.idx=pairs.proj2_idx "  +
        				" where pairs.aligned=1 ");
        
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
        ResultSet rs = pool.executeQuery("SELECT idx, name, type, loaddate FROM projects");
        while ( rs.next() ) {
        	int nIdx = rs.getInt(1);
        	String strName = rs.getString(2);
        	String strType = rs.getString(3);
        	String strDate = rs.getString(4); // CAS513 add
        	projects.add( new Project(nIdx, strName, strType, strDate) );
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
	        rs = pool.executeQuery("SELECT count(*) FROM xgroups AS p " +
	        		"WHERE proj_idx=" + p.getID());
	        
	        rs.next();
	        int nGroups = rs.getInt(1);
	        p.setNumGroups(nGroups);
	        rs.close();
	        
	        rs = pool.executeQuery("select count(*) from pseudo_annot as pa " +
	        		" join xgroups as g on g.idx=pa.grp_idx " +
	        		" where g.proj_idx=" + p.getID());
	        rs.first();
	        int numAnnot = rs.getInt(1);
	        
	        rs = pool.executeQuery("select count(*) from pseudo_annot as pa " +
	        		" join xgroups as g on g.idx=pa.grp_idx " +
	        		" where g.proj_idx=" + p.getID() + " and type='gene'");
	        rs.first();
	        int numGene = rs.getInt(1); // CAS505 add numGene and numGap
	        
	        rs = pool.executeQuery("select count(*) from pseudo_annot as pa " +
	        		" join xgroups as g on g.idx=pa.grp_idx " +
	        		" where g.proj_idx=" + p.getID() + " and type='gap'");
	        rs.first();
	        int numGap = rs.getInt(1);
	        rs.close();
	        
	        p.setAnno(numGene, numGap, numAnnot);
	        
	        // CAS500 for Summary and for orderProjects
	        rs = pool.executeQuery("select sum(length) from pseudos " +
					"join xgroups on xgroups.idx=pseudos.grp_idx  " +
					"where xgroups.proj_idx=" + p.getID());
			if (rs.next()) p.setLength(rs.getLong(1));
			rs.close();
			
			// CAS513 for Main list on left, mark those with synteny
			rs = pool.executeQuery("select count(*) from pairs where proj1_idx=" + p.getID() +
					" or proj2_idx=" + p.getID());
			if (rs.next()) p.setCntSynteny(rs.getInt(1));
			rs.close();
        }
        
        sortProjects(projects);
        
        return projects;
	}
	private void loadProjectsFromDisk(Vector<Project> projects, String strDataPath, String dirName) {
		File root = new File(strDataPath + dirName); // dirName is same as strType p
	
		if (root == null || !root.isDirectory()) {
			// CAS511 System.err.println("Missing directory " + root.getName());
			return;
		}
		for (File f : root.listFiles()) {
			if (f.isDirectory() && !f.getName().startsWith(".")) {
				Project newProj = new Project(-1, f.getName(), dirName, f.getName(), "");
				newProj.setStatus( Project.STATUS_ON_DISK );
				
				if (!projects.contains(newProj))  {
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
        pool.resetIdx("idx", "projects"); // CAS512 thought is was a problem, but wasn't
        pool.resetIdx("idx", "xgroups"); // only resets if empty, otherwise, set auto-inc to max(idx)
	}
	private void removeProjectAnnotationFromDB(Project p) throws SQLException
	{
		UpdatePool pool = new UpdatePool(dbReader);
        pool.executeUpdate("DELETE pseudo_annot.* from pseudo_annot, xgroups where pseudo_annot.grp_idx=xgroups.idx and xgroups.proj_idx=" + p.getID());
        pool.resetIdx("idx", "pseudo_annot"); // CAS512
	}	
	
	public void removeAlignmentFromDB(Project p1, Project p2) throws Exception
	{
		UpdatePool pool = new UpdatePool(dbReader);
        pool.executeUpdate("DELETE from pairs WHERE proj1_idx="+p1.getID()+" AND proj2_idx="+p2.getID());
        pool.resetIdx("idx", "pairs"); // CAS512
        pool.resetIdx("idx", "pseudo_hits"); // CAS515
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
            
            if (children != null) {
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
		Project newProj = new Project(-1, name, type, name, "" );
		newProj.setStatus( Project.STATUS_ON_DISK );
		
		if (!projects.contains(newProj)) {
			System.out.println("Create " + DATA_PATH + type + "/" + name);
			Utilities.checkCreateDir(DATA_PATH + type + "/" + name, true /* bprt*/);
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
			
			String items [] = {"Select type...", "Sequence", "FPC"};
			cmbType = new JComboBox <String> (items);
			cmbType.setAlignmentX(Component.LEFT_ALIGNMENT);
			cmbType.setBackground(Color.WHITE);
			
			cmbType.setSelectedIndex(1);
			cmbType.setMaximumSize(cmbType.getPreferredSize());
			cmbType.setMinimumSize(cmbType.getPreferredSize());
			
			btnAdd = new JButton("Add");
			btnAdd.setBackground(Color.WHITE);
			btnAdd.addActionListener(addListener);
			btnAdd.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					if (isAddValid())
						dispose();
				}
			});
			btnAdd.setEnabled(true); // CAS505 was not turning to true on Linux, so check valid on Add
			
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
		public String getPType() { 
			String type = (String)cmbType.getSelectedItem(); 
			if (type.equals("Sequence")) type = Constants.seqType;
			return type;
		}
		
		public void reset() { 
			txtName.setText("");
			cmbType.setSelectedIndex(1); // CAS504 make sequence the default
			btnAdd.setEnabled(true);
		}
		
		private boolean isAddValid() { 
			String name = txtName.getText().trim();
			if (name.length()==0) {
				Utilities.showErrorMessage("Must enter a name");
				return false;
			}
			if (!name.matches("^[\\w]+$")) {
				Utilities.showErrorMessage("Name must be characters, numbers or underscore");
				return false;
			}
			if  (cmbType.getSelectedIndex() == 0) {
				Utilities.showErrorMessage("The type must be set");
				return false;
			}
			// CAS511 check/create directories here if Add Project
			String strDataPath = DATA_PATH;
			Utilities.checkCreateDir(strDataPath, true);
			if  (cmbType.getSelectedIndex() == 1) {
				Utilities.checkCreateDir(Constants.seqDataDir, true /* bPrt */); 
				Utilities.checkCreateDir(Constants.seqRunDir,  true);
			}
			else {
				Utilities.checkCreateDir(Constants.fpcDataDir, true );
				Utilities.checkCreateDir(Constants.fpcRunDir,  true);
			}
			return true;
		}
		
		private JButton btnAdd = null;
		private JButton btnCancel = null, btnHelp = null;
		private JTextField txtName = null;
		private JComboBox <String> cmbType = null;
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
			if (availProjects.contains(p)) {
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
