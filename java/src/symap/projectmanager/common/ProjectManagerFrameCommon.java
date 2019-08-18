package symap.projectmanager.common;

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
import java.util.Properties;

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
import util.Utilities;
import util.ProgressDialog;
import util.LinkLabel;
import util.PropertiesReader;

import util.Logger;
import blockview.*;
import circview.*;

import symap.projectmanager.common.PropertyFrame;
import symapQuery.SyMAPQueryFrame;
import symap.projectmanager.common.Project;


// Project pair status tracking:
// 
// S = synteny done, ready to view           <==> pairs.aligned=1 in database
// A = alignment done, still needs anchor load and synteny   <==> all.done file exists in pair directory 
// null = none of the above, needs full alignment. 
//
// Note that status will be null for a partial alignment that was interrupted,
// e.g. by a machine going down. In this case the user *can* still salvage
// whatever was completed, if they choose a single-pair alignment. 
// It will prompt to re-use the files. If they use "do all", however,
// it will start over without prompting. 
//
// On sequence reload, user is prompted to remove all alignment files. If they choose yes, 
// all status automatically goes back to null. If they choose no, status will
// show as "A" for all the old alignments, and they will be reused. The only time
// anyone would probably do this is to align with masking but then load the 
// unmasked sequences for viewing. The main reason to have this option
// is just to not delete all the alignments without warning. 
//
// On annotation reload, user is told they will need to re-do the syntenies. 
// pairs.aligned is set to 0, and all status go to "S".
// 

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class ProjectManagerFrameCommon extends JFrame implements ComponentListener {
	public static boolean inReadOnlyMode = false;
	
	private static final String WINDOW_TITLE = "SyMAP " + SyMAP.VERSION + " - Project Manager";
	private static final String DB_ERROR_MSG = "A database error occurred, please see the Troubleshooting Guide at:\n" + SyMAP.TROUBLE_GUIDE_URL + "#database_error";	
	public static final String DATA_PATH = "data/";
	private static final String MAIN_PARAMS = "symap.config"; 
	
	private static final int MIN_WIDTH = 900;
	private static final int MIN_HEIGHT = 600;
	private static final int MIN_CWIDTH = 900;
	private static final int MIN_CHEIGHT = 1000;

	private static final int MIN_DIVIDER_LOC = 240;
	
	// If changed - don't make one a substring of another!!
	private static final String TBL_DONE = "\u2713";
	private static final String TBL_ADONE = "A";
	private static final String TBL_NA = "n/a";
	
	protected DatabaseReader dbReader = null;
	private Vector<Project> projects;
	protected Vector<Project> selectedProjects = null;
	private HashMap<Integer,Vector<Integer>> projPairs = null; // project idx mapping from database
	private JButton btnShow3D, btnShowDotplot, btnAllDotplot, btnDoAll, btnClearPair,
		btnRedoAll,btnShowBlockView, btnShowCircView, btnQueryView, btnShowSummary;
	private JSplitPane splitPane;
	private JComponent instructionsPanel;
	private JTable alignmentTable;
	private JTextArea txtSelection, txtSelection2;
	private JButton btnAddProject = null;
	private JButton btnDoAlign;
	private boolean isAlignmentAvail = (Utilities.isLinux() || Utilities.isMac());
	PropertiesReader mProps;
	private Project curProj = null; // used only in the load-all-projects function
	protected ActionListener explorerListener = null;
	private AddProjectPanel addProjectPanel = null;
	private SyProps mPairProps = null; // values from the PairProperties dialog
	private PairPropertyFrame ppFrame = null;
	private JTextField txtCPUs =null;
	
	public ProjectManagerFrameCommon( ) {
		super(WINDOW_TITLE);
		//System.out.println("\nStarting " + WINDOW_TITLE); CAS 1/10/18 printVersion prints this too
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
									createProjectPanel(),
									instructionsPanel );
		splitPane.setDividerLocation(MIN_DIVIDER_LOC);
		
		add(splitPane);
		
		setSize(MIN_WIDTH, MIN_HEIGHT);
		
		addComponentListener(this); // hack to enforce minimum frame size
	}
	
	private DatabaseReader getDatabaseReader() throws Exception {
		String paramsfile = MAIN_PARAMS;
		// Get DB info from params file
		if (Utilities.fileExists(paramsfile)) {
			System.out.println("Reading from file " + paramsfile);
		}
		else if (Utilities.fileExists("params")) { // backwards compatiable
			paramsfile = "params";
			System.out.println("Reading from file " + paramsfile);
		}
		else {		
			Utilities.checkCreateFile(paramsfile);
			System.out.println("Creating symap configuration file " + paramsfile);
		}
		mProps = new PropertiesReader(new File(paramsfile));
		
		String db_server       = mProps.getProperty("db_server");
		String db_name         = mProps.getProperty("db_name");
		String db_adminuser    = mProps.getProperty("db_adminuser"); // note: user needs write access
		String db_adminpasswd  = mProps.getProperty("db_adminpasswd");
		String db_clientuser   = mProps.getProperty("db_clientuser");
		String db_clientpasswd = mProps.getProperty("db_clientpasswd");

		if (db_server != null) 			db_server = db_server.trim();
		if (db_name != null) 			db_name = db_name.trim();
		if (db_adminuser != null) 		db_adminuser = db_adminuser.trim();
		if (db_adminpasswd != null) 	db_adminpasswd = db_adminpasswd.trim();
		if (db_clientuser != null) 		db_clientuser = db_clientuser.trim();
		if (db_clientpasswd != null) 	db_clientpasswd = db_clientpasswd.trim();

		if (db_server == null) 			db_server = "";

		
		DatabaseUser.checkDBRunning(db_server);
		
		// Check db params and set defaults
		if (Utilities.isStringEmpty(db_name))
			db_name = "symap";
		else
			setTitle(WINDOW_TITLE + " - Database: " + db_name);
		if (Utilities.isStringEmpty(db_adminuser))
			db_adminuser = "admin";
		if (Utilities.isStringEmpty(db_clientuser))
			db_clientuser = "admin";
		
		// Open database connection and create database if it doesn't exist
        Class.forName("com.mysql.jdbc.Driver");
        DatabaseUser.shutdown(); // in case shutdown at exit didn't work/happen
        if (!inReadOnlyMode) {
	        	if (!DatabaseUser.createDatabase(db_server, db_name, db_adminuser, db_adminpasswd, "scripts/symap.sql")) {
	        		System.err.println(DB_ERROR_MSG);
	        		System.exit(-1);
	        	}
        }
        return DatabaseUser.getDatabaseReader(SyMAPConstants.DB_CONNECTION_PROJMAN,
        		DatabaseUser.getDatabaseURL(db_server, db_name),
        		(inReadOnlyMode ? db_clientuser : db_adminuser),
        		(inReadOnlyMode ? db_clientpasswd : db_adminpasswd),
				null);
	}
	
	private void initialize() {
		try {
			if (dbReader == null) dbReader = getDatabaseReader();
			
			Utilities.setResClass(this.getClass());
			Utilities.setHelpParentFrame(this);
			
			projPairs = loadProjectPairsFromDB();
			projects = loadProjectsFromDB();
			
			mPairProps = new SyProps();
			
			String strDataPath = DATA_PATH;
			Utils.checkCreateDir(new File(strDataPath));
			Utils.checkCreateDir(new File(strDataPath + "pseudo"));
			Utils.checkCreateDir(new File(strDataPath + "fpc"));			
			Utils.checkCreateDir(new File(strDataPath + "pseudo_pseudo"));
			Utils.checkCreateDir(new File(strDataPath + "fpc_pseudo"));
			addProjectsFromDisk(projects, strDataPath, "pseudo");
			addProjectsFromDisk(projects, strDataPath, "fpc");
			
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
		catch (Exception e) { 
			e.printStackTrace();
			System.err.println(DB_ERROR_MSG);
			System.exit(-1);
		}
	}

	private static boolean isShutdown = false;
	private synchronized void shutdown() {
		if (!isShutdown) {
			// Shutdown mysqld
			DatabaseUser.shutdown();
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
			InputStream str = this.getClass().getResourceAsStream("/html/ProjMgrInstruct.html");
			
			int ci = str.read();
			while (ci != -1)
			{
				sb.append((char)ci);
				ci = str.read();
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		
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
				String pairSubdir = ((p1.isFPC() || p2.isFPC()) ? "fpc_pseudo" : "pseudo_pseudo");
				if (true)
				{					
					int id2 = p2.getID();
					String pairDir = null;
					Project[] ordP = orderProjects(p1,p2);
					pairDir = DATA_PATH  + pairSubdir + "/" + ordP[0].strDBName + "_to_" + ordP[1].strDBName;

					if ((projPairs.containsKey(id1) && projPairs.get(id1).contains(id2)) ||
						(projPairs.containsKey(id2) && projPairs.get(id2).contains(id1)))
					{
						row.add(TBL_DONE); 
					}
					else if (p1.isFPC() && p2.isFPC() )
					{
						row.add(TBL_NA);	
					}
					else if (Utils.checkDoneFile(pairDir))
					{					
						row.add(TBL_ADONE); 
					}
					else
						row.add(null);
				}
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
	
	private void updateSelectionLabel() {
		Project[] projects = getSelectedAlignmentProjects();
		if (projects == null) {
			if (alignmentTable != null) {
				// Prevent extra space above "Show 3D" button
				txtSelection.setVisible(true); 
				txtSelection2.setVisible(true); 
				btnDoAlign.setVisible(true);
				btnClearPair.setVisible(true);
				btnShowDotplot.setVisible(true); 
				txtSelection.setText("");
				txtSelection2.setText("Select a pair from the table");
			}
			return;
		}
		
		Utilities.setCursorBusy(this, true);
		
		String p1Name = projects[0].getDBName();
		String p2Name = projects[1].getDBName();
		String val = getSelectedAlignmentValue();
		String text = null;
		String text2 = "Choose an action to take for this pair:\n";;
		
		if (p1Name.equals(p2Name)) {
			text = "You selected '" + p1Name + "' against itself.\n";
			if (val == null)
				text += "This pair has not yet been aligned.\n";
			else if (val.contains(TBL_DONE))
				text += "Alignment and synteny have been computed for this pair.\n";
			else if (val.contains(TBL_ADONE))
				text += "This pair has been partially completed.\n";
		}
		else {
			text = "You selected '" + p1Name + "' against '" + p2Name + "'.\n";
			if (val == null)
				text += "This pair has not yet been aligned.\n";
			else if (val.contains(TBL_DONE))
				text += "Alignment and synteny have been computed for this pair.\n";
			else if (val.contains(TBL_ADONE))
				text += "This pair has been partially completed\n";
		}
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
		boolean bothSeq = (projects[0].isPseudo() && projects[1].isPseudo());
		boolean isSelf = (projects[0].getID() == projects[1].getID());
		boolean noAlign = (!isAlignmentAvail && (val == null || !val.contains(TBL_ADONE)));
		if (bothFPC)
			text += "FPC-to-FPC alignment is currently unsupported.";
		
		else if (noAlign)
			text += "Alignments can only be performed on Linux and Mac systems.";
		else {
		}
		boolean noOptions = bothFPC;// || bothUnord || FPCUnord;
		txtSelection.setText(text);
		txtSelection.setVisible(true);
		txtSelection2.setText(text2);
		txtSelection2.setVisible(!noOptions);
		txtSelection.setMaximumSize( new Dimension(500, (int)txtSelection.getPreferredSize().getHeight()) );
		txtSelection2.setMaximumSize( new Dimension(500, (int)txtSelection2.getPreferredSize().getHeight()) );
		
		btnDoAlign.setEnabled( !noOptions && !noAlign );
		btnClearPair.setEnabled( val != null && (val.contains(TBL_DONE) || val.contains(TBL_ADONE)) );
		btnShowDotplot.setEnabled( val != null && val.contains(TBL_DONE)); // enable only if already fully aligned
		btnShowCircView.setEnabled( val != null && val.contains(TBL_DONE) ); //&& !oneUnord); // enable only if already fully aligned
		btnShowSummary.setEnabled( val != null && val.contains(TBL_DONE) && bothSeq && !isSelf); //&& !oneUnord); // enable only if already fully aligned
		btnShowBlockView.setEnabled( val != null && val.contains(TBL_DONE) ); // enable only if already fully aligned
		
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
				// CAS 1/7/18 can't tell this from white setBackground(new Color(245,245,245));
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

	private static JLabel createLabel(String text, int fontStyle, int fontSize) {
		JLabel label = new JLabel(text);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setFont( new Font(label.getFont().getName(), fontStyle, fontSize) );
		return label;
	}
	
	private static Font createPlainFont(Font basis) {
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
			// CAS 1/6/18 don't add s if already input with s at end
			String chrLbl = (p.chrLabel.endsWith("s")) ? p.chrLabel() + "s: " :  p.chrLabel() + ": "; 
			String tstr = (p.getType().equals("fpc") ? "FPC Map\n" : "");
			
			JPanel textPanel = new JPanel();
			textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.PAGE_AXIS));
			textPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
			
			textPanel.add(new JLabel("Database Name: " + p.getDBName()));
			textPanel.add(new JLabel(tstr));
			if(p.getStatus() != Project.STATUS_ON_DISK) {
				textPanel.add(new JLabel(chrLbl + p.getNumGroups()));
				if(p.getDescription() != null)
					textPanel.add(new JLabel("Description: " + p.getDescription()));
			} 
			else
			{
				textPanel.add(new JLabel("Use the Parameters link to import sequences, annotation, and set project metadata"));
			}
			if (p.getOrderAgainst() != null && p.getOrderAgainst().length() > 0)
			{
				textPanel.add(new JLabel("\nOrder Against: " + p.getOrderAgainst()));
			}
			if (p.getType().equals("pseudo"))
			{
				if (p.numAnnot > 0)
				{
					textPanel.add(new JLabel(p.numAnnot + " annotations"));
				}
				else
				{
					textPanel.add(new JLabel("Not annotated"));
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
			else { // CAS 1/1/18 was just "Remove" which is unclear
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
				subPanel.add( createHorizPanel( new Component[] { lblTitle, btnRemoveFromDisk, btnLoadOrRemove, btnReloadParams }, 15 ) );				
			}
			else {
				if (p.isPseudo())
				{
					subPanel.add( createHorizPanel( new Component[] { lblTitle, btnLoadOrRemove, btnReloadSeq,btnReloadAnnot, btnReloadParams}, 15 ) );						
				}
				else
				{
					subPanel.add( createHorizPanel( new Component[] { lblTitle, btnLoadOrRemove,btnReloadAnnot, btnReloadParams}, 15 ) );											
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
			// CAS 1/5/18
			JTextArea text2 = new JTextArea("Click a lower diagonal white table cell\nto select the project pair.",2,1);
			text2.setBackground( getBackground() );
			text2.setEditable(false);
			text2.setLineWrap(false);
			text2.setAlignmentX(Component.LEFT_ALIGNMENT);
			text2.setForeground(Color.BLUE);
			
			txtSelection = createTextArea(null);
			
			txtSelection2 = createTextArea("Choose an action to take for this pair:");
			txtSelection2.setForeground(Color.BLUE);
			
			btnDoAlign = new JButton("Selected Pair");
			btnDoAlign.setVisible(true);
			btnDoAlign.setEnabled(false);
			btnDoAlign.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					Project[] projects = getSelectedAlignmentProjects();
					promptAndProgressAlignProjects(projects[0], projects[1],true, false);
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
			boolean canShow3DButton = (getNumCompletedAlignments() > 0);
			boolean canShowDPButton = false; 
			boolean canShowAllDPButton = (getNumCompletedAlignments() >= 1 && selectedProjects.size() >= 2);
			boolean canShowDoAll = (selectedProjects.size() >= 1);
			boolean canShowBVButton = false;
			boolean canShowCVButton = false;
			boolean canShowSumButton = false;
			
			JTextArea txtShow3D = createTextArea("Or, choose an option below to view ALL SPECIES from the table.");
			txtShow3D.setForeground(Color.BLUE);
			txtShow3D.setMaximumSize( new Dimension(500, (int)txtShow3D.getPreferredSize().getHeight()) );
			txtShow3D.setVisible(canShow3DButton);
			
			btnShow3D = new JButton("Chromosome Explorer");
			btnShow3D.setVisible(true); 
			btnShow3D.setEnabled( canShow3DButton );
			btnShow3D.addActionListener( explorerListener);
			
			btnShowDotplot = new JButton("Dot Plot");
			btnShowDotplot.setVisible(true);
			btnShowDotplot.setEnabled(canShowDPButton);
			btnShowDotplot.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent e) { 
					showDotplot();
				}
			});

			btnShowBlockView = new JButton("Block View");
			btnShowBlockView.setVisible(true);
			btnShowBlockView.setEnabled(canShowBVButton);
			btnShowBlockView.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent e) { 
					showBlockView();
				}
			});

			btnShowCircView = new JButton("Circle View");
			btnShowCircView.setVisible(true);
			btnShowCircView.setEnabled(canShowCVButton);
			btnShowCircView.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent e) { 
					showCircleView();
				}
			});
			
			btnShowSummary = new JButton("Summary");
			btnShowSummary.setVisible(true);
			btnShowSummary.setEnabled(canShowSumButton);
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

			JTextArea txtDoAll = createTextArea("Or, choose an option below to automatically perform alignments.");
			txtDoAll.setForeground(Color.BLUE);
			txtDoAll.setMaximumSize( new Dimension(500, (int)txtDoAll.getPreferredSize().getHeight()) );
			txtDoAll.setVisible(canShowDoAll);

			btnDoAll = new JButton("All Pairs");
			btnDoAll.setVisible(canShowDoAll);
			btnDoAll.setEnabled(canShowDoAll);
			btnDoAll.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent e) { 
					doAll(false);
				}
			});
			btnRedoAll = new JButton("Redo All Alignments");
			btnRedoAll.setVisible(canShowDoAll);
			btnRedoAll.setEnabled(canShowDoAll);
			btnRedoAll.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent e) { 
					doAll(true);
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
			
			int maxCPUs = Runtime.getRuntime().availableProcessors(); // CAS 12/6/17
			if (txtCPUs != null)
			{
				try
				{
					maxCPUs = Integer.parseInt(txtCPUs.getText());
				}
				catch (Exception e)
				{
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
			mainPanel.add( createHorizPanel( new Component[] { tableScroll, new JLabel(" "),new JLabel("CPUs:"), txtCPUs},5 ));			
			
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
			mainPanel.add( createHorizPanel( new Component[] {lbl3, btnShow3D, btnAllDotplot }, 5) );

			mainPanel.add( Box.createRigidArea(new Dimension(0,15))  );
			mainPanel.add( createHorizPanel( new Component[] {lbl4, btnQueryView }, 5) );
		} else {
			mainPanel.add( Box.createVerticalGlue() );
			mainPanel.add( createTextArea("") ); // kludge to fix layout problem			
		}
				
		updateSelectionLabel();
		
		return mainPanel;
	}
	
	private ActionListener pairPropListener = new ActionListener() {
		public void actionPerformed(ActionEvent arg0) 
		{
			ppFrame.fillValues(mPairProps);
		}
	};
	
	private ActionListener showPairProps = new ActionListener() {
		public void actionPerformed(ActionEvent arg0) 
		{
			SyProps prevProps = null;
			Project[] sel = getSelectedAlignmentProjects();
			if (sel != null)
			{
				try
				{
					int pidx = getPairIdx(sel[0],sel[1]);
					prevProps = new SyProps();
					if (0 == getPairPropsFromDB(prevProps,pidx))
					{
						prevProps = null;
					}
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
			ppFrame = new PairPropertyFrame(mPairProps,prevProps,pairPropListener);
			ppFrame.setVisible(true);
		}
	};
	
	public Properties getPairProps() { return mPairProps;}
	private int getPairPropsFromDB(SyProps props, int pairIdx) throws Exception
	{
		UpdatePool db = new UpdatePool(dbReader);
		int nProps = 0;
		ResultSet rs =db.executeQuery("select name,value from pair_props where pair_idx=" + pairIdx);
		while (rs.next())
		{
			nProps++;
			String name = rs.getString("name");
			String value = rs.getString("value");
			props.setProperty(name, value);
		}
		return nProps;
	}
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
		final ProgressDialog progress = new ProgressDialog(this, 
				"Loading Project",
				"Loading project '" + project.getDBName() + "' ...", 
				false, true, null);
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
					e.printStackTrace();
					progress.msg( e.getMessage() );
				}
				finally {
					progress.finish(success);
				}
			}
		};
		
		progress.start( loadThread ); // blocks until thread finishes or user cancels
		
		if (progress.wasCancelled()) {
			// Remove partially-loaded project
			try {
				System.out.println("Removing project " + project);
				removeProjectFromDB( project );
				//progressRemoveProject( project, loadThread,true, false ); 
			}
			catch (Exception e) { 
				e.printStackTrace();
			}
		}
		
		refreshMenu();
	}

	private void progressLoadAllProjects() {
		ErrorCount.init();
		final ProgressDialog progress = new ProgressDialog(this, 
				"Loading All Projects",
				"Loading all projects" , 
				false, true, null);
		progress.closeIfNoErrors();
		Thread loadThread = new Thread() {
			public void run() {
				boolean success = true;
				
				for (Project project : selectedProjects)
				{
					if (project.getStatus() != Project.STATUS_ON_DISK) continue;
				
					progress.msg("\n****** Start " + project.strDBName + " ******"); // CAS 1/5/18 add \n
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
						e.printStackTrace();
						progress.msg( e.getMessage() );
					}

				}
				if (!progress.wasCancelled())
				{
					progress.finish(success);
				}
			}
		};
		
		progress.start( loadThread ); // blocks until thread finishes or user cancels
		
		if (progress.wasCancelled() && curProj != null) {
			// Remove partially-loaded project
			try {
				System.out.println("Removing project " + curProj.strDBName);
				removeProjectFromDB( curProj ); 
			}
			catch (Exception e) { 
				e.printStackTrace();
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
			e.printStackTrace();
		}
		finally {
			Utilities.setCursorBusy(this, false);
		}
	}
	
	private void progressReloadAnnotation(final Project project) {
		
		final ProgressDialog progress = new ProgressDialog(this, 
				"Loading Annotation",
				"Loading Annotation '" + project.getDBName() + "' ...", 
				false, true, null);
		progress.closeIfNoErrors();
		
		Thread loadThread = new Thread() {
			public void run() {
				boolean success = true;
				
				// Load project sequence and annotation data
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
					e.printStackTrace();
					progress.msg( e.getMessage() );
				}
				finally {
					progress.finish(success);
				}
			}
		};
		
		progress.start( loadThread ); // blocks until thread finishes or user cancels
		
		if (progress.wasCancelled()) {
			try {
				System.out.println("Removing annotation for " + project);
				removeProjectAnnotationFromDB( project ); 
			}
			catch (Exception e) { 
				e.printStackTrace();
			}
		}
		
		refreshMenu();
	}

	private MouseListener doLoadAllProj = new MouseAdapter() {
		public void mouseClicked(MouseEvent e) {
			progressLoadAllProjects(  );
		}
	};
	private MouseListener doRemove = new MouseAdapter() {
		public void mouseClicked(MouseEvent e) {
			if (!progressConfirm("Remove project from database","Remove project from database")) return;
			
			progressRemoveProject( ((ProjectLinkLabel)e.getSource()).getProject() );
		}
	};
	private MouseListener doRemoveDisk = new MouseAdapter() {
		public void mouseClicked(MouseEvent e) {
			if (!progressConfirm("Remove project from disk","Remove project from disk")) return;
			
			progressRemoveProjectFromDisk( ((ProjectLinkLabel)e.getSource()).getProject() );
		}
	};
	private MouseListener doReloadSeq = new MouseAdapter() {
		public void mouseClicked(MouseEvent e) {
			if (!progressConfirm("Reload project","Reload project" +
					"\nAfter sequences are removed, you will be asked if you want to remove" +
					"\nthe alignments, which if anything has changed, you need to." +
					"\nThen you will need to re-run the alignments and synteny.")) return;
			
			progressReloadSequences( ((ProjectLinkLabel)e.getSource()).getProject() );
		}
	};
	private MouseListener doReloadAnnot = new MouseAdapter() {
		public void mouseClicked(MouseEvent e) {
			if (!progressConfirm("Reload annotation", "Reload annotation" +
					"\nYou will need to re-run the synteny computations for this project, " +
					"\nbut not the MUMmer or BLAT alignments.")) return;
			
			progressReloadAnnotation( ((ProjectLinkLabel)e.getSource()).getProject() );
		}
	};	
	// CAS 1/4/18 confirm any action that removes things
	private boolean progressConfirm(String title, String msg) {
		String [] options = {"Yes", "No"};
		int ret = JOptionPane.showOptionDialog(null, 
				"Confirm: " + msg,
				title, JOptionPane.YES_NO_OPTION, 
				JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
		if (ret == JOptionPane.NO_OPTION) return false;
		else return true;
	}
	private MouseListener doReloadParams = new MouseAdapter() {
		public void mouseClicked(MouseEvent e) {
			Project theProject = ((ProjectLinkLabel)e.getSource()).getProject();
			PropertyFrame propFrame = new PropertyFrame(getRef(), theProject.isPseudo(), theProject.getDisplayName(), theProject.getDBName(), dbReader, true);
			propFrame.setVisible(true);
			if(!propFrame.isDisarded())
			{			
				theProject.updateParameters(new UpdatePool(dbReader),null);
			}
			refreshMenu();		}
	};
	private MouseListener doSetParamsNotLoaded = new MouseAdapter() {
		public void mouseClicked(MouseEvent e) {
			Project theProject = ((ProjectLinkLabel)e.getSource()).getProject();
			PropertyFrame propFrame = new PropertyFrame(getRef(), theProject.isPseudo(), theProject.getDisplayName(), theProject.getDBName(), dbReader, false);
			propFrame.setVisible(true);
		}		
	};

	private ProjectManagerFrameCommon getRef() { return this; }

	private boolean removeAllAlignmentsFromDisk(Project p,Logger log, boolean fromReload)
	{
		
		File[] topLevels = new File[2];
		
		if (p.isFPC())
		{
			topLevels[0] = new File(DATA_PATH+"fpc_pseudo");
			topLevels[1] = null;
		}
		else
		{
			topLevels[0] = new File(DATA_PATH+"fpc_pseudo");
			topLevels[1] = new File(DATA_PATH+"pseudo_pseudo");;
		}
		boolean success = true;
		
		for (File top : topLevels)
		{
			if (top == null) continue;
			Vector<File> alignDirs = new Vector<File>();
			
			for (File f : top.listFiles()) 
			{
				if (f.isDirectory())
				{
					if (p.isFPC() && f.getName().startsWith(p.getDBName()+"_to_"))
					{
						alignDirs.add(f); // for fpc, it has to come first
					}
					else
					{
						if	(f.getName().startsWith(p.getDBName()+"_to_") ||
								f.getName().endsWith("_to_"+p.getDBName())) 
						{
							alignDirs.add(f);
						}
					}
				}
			}
			if (alignDirs.size() > 0)
			{
				String strOptionMessage;
				String fileType = (p.isFPC() ? "BLAT" : "MUMmer");
				strOptionMessage = "Do you want to remove any existing " + fileType + " alignment files for this project? \n";
				if (fromReload)
				{
					strOptionMessage += "(Choose 'yes' if the sequences have changed.)";
				}
				else
				{
					strOptionMessage += "(Choose 'yes' if you plan to reload the project with new sequences.)";				
				}
			
				JOptionPane optionPane = new JOptionPane(
					strOptionMessage,
					JOptionPane.QUESTION_MESSAGE,
				    JOptionPane.YES_NO_OPTION);
			
				JDialog dialog = optionPane.createDialog(null, "Remove Alignments");
				dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
				dialog.setVisible(true);
			
				// Wait on user input
				Object selectedValue = optionPane.getValue();
			
				if (selectedValue != null && ((Integer)selectedValue).intValue() == JOptionPane.YES_OPTION) 
				{				
					for (File f : alignDirs) 
					{
						//log.msg("removing " + f.getName()); not that kind of progress dialog
						System.out.println("removing " + f.getName());	
						success &= deleteDir(f);
						
					}
					System.out.println("done ");	
				}
			}
		}
		return success;
	}
	
	private void progressRemoveProject(final Project project) {
		ErrorCount.init();
		final ProgressDialog progress = new ProgressDialog(this, 
				"Removing Project From Database",
				"Removing project from database '" + project.getDBName() + "' ...", 
				false, false, null);
		progress.closeIfNoErrors();
		
		Thread rmThread = new Thread() {
			public void run() {
				boolean success = true;
				
				// Load project sequence and annotation data
				try {
					removeProjectFromDB(project);
					removeAllAlignmentsFromDisk(project, progress,false); // it asks first
				}
				catch (Exception e) {
					success = false;
					e.printStackTrace();
					progress.msg( e.getMessage() );
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
		final ProgressDialog progress = new ProgressDialog(this, 
				"Clearing alignment pair",
				"Clearing alignment:'" + p1.getDBName() + " to " + p2.getDBName(), 
				false, false, null);
		progress.closeIfNoErrors();
		
		Thread clearThread = new Thread() {
			public void run() {
				boolean success = true;
				
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
					e.printStackTrace();
					System.out.println(e.getMessage());
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
		final ProgressDialog progress = new ProgressDialog(this, 
				"Removing Project From Disk",
				"Removing project from disk '" + project.getDBName() + "' ...", 
				false, false, null);
		progress.closeIfNoErrors();
		
		Thread rmThread = new Thread() {
			public void run() {
				boolean success = true;
				
				// Load project sequence and annotation data
				try {
					String path = DATA_PATH;
					if(project.isFPC())
						path += "fpc/";
					else
						path += "pseudo/";
					path += project.getDBName();
					
					File f = new File(path);
					if(f.exists()) {
						Process p = Runtime.getRuntime().exec("rm -rf " + f.getAbsolutePath());
						p.waitFor();
						f.delete();
					}
				}
				catch (Exception e) {
					success = false;
					e.printStackTrace();
					progress.msg( e.getMessage() );
				}
				finally {
					progress.finish(success);
				}
			}
		};
		
		progress.start( rmThread ); 
		
		refreshMenu();
	}
	private void progressReloadSequences(final Project project) {
		ErrorCount.init();
		final ProgressDialog progress = new ProgressDialog(this, 
				"Reloading " + project.getDBName() ,
				"Reloading '" + project.getDBName() + "' ...", 
				false, true, null);
		progress.closeIfNoErrors();
		
		Thread rmThread = new Thread() {
			public void run() {
				boolean success = true;
				
				// Load project sequence and annotation data
				try {
					UpdatePool pool = new UpdatePool(dbReader);
					String projName = project.getDBName();
					
					progress.msg("Removing current sequences");
					removeProjectFromDB(project);
					success = SeqLoadMain.run( pool, progress, projName );
					
					if (success && !Cancelled.isCancelled()) 
					{
						AnnotLoadMain annot = new AnnotLoadMain(pool, progress,mProps);
						success = annot.run( projName );
					}
					removeAllAlignmentsFromDisk(project, progress,true); // it asks first
				}
				catch (Exception e) {
					success = false;
					e.printStackTrace();
					progress.msg( e.getMessage() );
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
		
		// Save selection
//		selectedCol = p2;
//		selectedRow = p1;
		
		return orderProjects(p1,p2);

	}
	private Project[] orderProjects(Project p1, Project p2)
	{
		if (p1.isFPC())
			return new Project[] { p1, p2 };	// keep fpc first
		else if ( p2.isFPC()  ) 
			return new Project[] { p2, p1 }; // put fpc project first
		else if (p1.strDBName.compareToIgnoreCase(p2.strDBName) > 0) 
			return new Project[] { p2, p1 }; // make alphabetic
		else
			return new Project[] { p1, p2 };			
	}
	
	private String getSelectedAlignmentValue() {
		int nRow = alignmentTable.getSelectedRow();
		int nCol = alignmentTable.getSelectedColumn();
		return (String)alignmentTable.getValueAt(nRow, nCol);
	}
	
	FileWriter buildLog(Project p1, Project p2)
	{
		FileWriter ret = null;
		try
		{
			String pairName = p1.getDBName() + "_to_" + p2.getDBName();
			String pairDir = (p1.getType().equals("pseudo") ? "data/pseudo_pseudo/" + pairName : "data/fpc_pseudo/" + pairName); 
			File pd = new File(pairDir);
			if (!pd.isDirectory())
			{
				pd.mkdir();
			}
			File lf = new File(pd,"symap.log");
			ret = new FileWriter(lf, true);
		}
		catch (Exception E)
		{
			System.out.println("Unable to create log file");
		}
		return ret;
	}
	private int getPairIdx(Project p1, Project p2) throws Exception
	{
		int idx = 0;
		UpdatePool db = new UpdatePool(dbReader);
		ResultSet rs = db.executeQuery("select idx from pairs where (proj1_idx=" + p1.getID() + " and proj2_idx=" + p2.getID() + 
										") or (proj2_idx=" + p1.getID() + " and proj1_idx=" + p2.getID() + ")"  );
		if (rs.first())
		{
			idx = rs.getInt("idx");	
		}
		rs.close();
		return idx;
	}
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
		int pairIdx = getPairIdx(p1,p2);

		if (p1.isPseudo()) // pseudo/pseudo
		{
			prog.appendText(p1.getNumGroups() + " " + p1.getDisplayName() + " chromosomes");
			prog.appendText(p2.getNumGroups() + " " + p2.getDisplayName() + " chromosomes");
			TreeMap<String,Integer> counts = new TreeMap<String,Integer>();
			getPseudoCounts(counts,pairIdx);
			prog.appendText(counts.get("nhits") + " hits");
			prog.appendText(counts.get("blocks") + " synteny blocks");
			prog.appendText(counts.get("genehits") + " gene hits");
			prog.appendText(counts.get("blkhits") + " synteny hits");
		}
		else
		{
			prog.appendText(p1.getNumGroups() + " " + p1.getDisplayName() + " (FPC) chromosomes (or LG)");
			int nMrk = getFPCMarkers(p1);
			prog.appendText(nMrk + " markers");
			prog.appendText(getFPCBES(p1) + " bes");
			prog.appendText(p2.getNumGroups() + " " + p2.getDisplayName() + " chromosomes");
			
			TreeMap<String,Integer> counts = new TreeMap<String,Integer>();
			getFPCCounts(counts,pairIdx);
			prog.appendText(counts.get("blocks") + " synteny blocks");
			prog.appendText(counts.get("beshits") + " BES hits");
			prog.appendText(counts.get("besblkhits") + " BES synteny hits");
			if (nMrk > 0)
			{
				prog.appendText(counts.get("mrkhits") + " marker hits");
				prog.appendText(counts.get("mrkblkhits") + " marker synteny hits");
			}
		}		

	}
	private void doAll(boolean redo)
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
			if (ret == JOptionPane.CANCEL_OPTION)
			{
				return;	
			}
			else if (ret == JOptionPane.NO_OPTION)
			{
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
				if (entry.equals("-"))
				{
					continue;
				}
				else if (entry.equals(TBL_DONE))
				{
					doThis = redo;
				}
				else if (entry.equals(TBL_ADONE))
				{
					doThis = true;	
				}
				else if (entry.equals(""))
				{
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
						System.out.println("Starting " + p1.getDBName() + "," + p2.getDBName());
					
						promptAndProgressAlignProjects(p1, p2, false, true);
					}
				}
			}
			
		}
	}
	private void promptAndProgressAlignProjects(final Project p1, final Project p2, boolean replacePrompt, boolean closeWhenDone) {

		FileWriter fw = buildLog(p1,p2);
		int maxCPUs = 1;
		try 
		{
			maxCPUs = Integer.parseInt(txtCPUs.getText());
		}
		catch (Exception e)
		{
			Utilities.showErrorMessage("Please enter a valid value for number of CPUs to use.");
			return;
		}
		if (maxCPUs <= 0) maxCPUs = 1;
		try
		{
			Date date = new Date();
			fw.write("\n-------------- starting " + date.toString() + " --------------------\n");
		} catch (Exception e){}
		final ProgressDialog progress = new ProgressDialog(this, 
				"Aligning Projects", 
				(p1 == p2 ? 
						"Aligning project '" + p1.getDisplayName() + "' to itself ..." :
						"Aligning projects '" + p1.getDisplayName() + "' and '" + p2.getDisplayName() + "' ..."), 
				false, true, fw);
		// CAS 1/1/18 progress.appendText("The alignment will use up to " + maxCPUs + " CPUs.\nTo change this, restart SyMAP using the '-p' parameter\n");
		if (closeWhenDone)
		{
			progress.closeWhenDone();	
		}
		else
		{
			progress.closeIfNoErrors();
		}
		final AlignMain aligner = new AlignMain(new UpdatePool(dbReader), progress, p1.getDBName(), p2.getDBName(),
					maxCPUs,mProps, mPairProps, replacePrompt);
		if (aligner.mCancelled) return;
		
		final AnchorsMain anchors = new AnchorsMain( new UpdatePool(dbReader), progress, mProps, mPairProps );
		final SyntenyMain synteny = new SyntenyMain( new UpdatePool(dbReader), progress, mProps, mPairProps );
		
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
					
					UpdatePool pool = new UpdatePool(dbReader);
					pool.executeUpdate("delete from pairs where (" + 
						"	proj1_idx=" + p1.getID() + " and proj2_idx=" + p2.getID() + ") or (proj1_idx=" + p2.getID() + " and proj2_idx=" + p1.getID() + ")"); 
					
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
					e.printStackTrace();
					statusThread.interrupt();
					progress.msg( e.getMessage() );
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
				System.out.println("Removing alignment");
				removeAlignmentFromDB(p1, p2);
			}
			catch (Exception e) { 
				e.printStackTrace();
			}
		}
		else if (closeWhenDone)
		{
			progress.dispose();	
		}
		
		//System.gc(); // cleanup memory after AlignMain, AnchorsMain, SyntenyMain
		System.out.println("--------------------------------------------------\n");
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
		
		tempPnl.add(btnAddProject);
		
		panel.add(tempPnl);
		
		panel.setMinimumSize( new Dimension(MIN_DIVIDER_LOC, MIN_HEIGHT) );
		
		return panel;
	}
	
	private static Component createHorizPanel( Component[] comps, int gapWidth ) {
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
		
		// Swap x and y projects to match database - FIXME: no longer necessary after #206
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
			e.printStackTrace();
			System.exit(0);
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
		
		// Swap x and y projects to match database - FIXME: no longer necessary after #206
		if (projPairs.containsKey(projXIdx) && projPairs.get(projXIdx).contains(projYIdx)) {
			int temp = projXIdx;
			projXIdx = projYIdx;
			projYIdx = temp;
		}
		if (projYIdx == projXIdx)
		{
			projYIdx = 0;
		}		
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
		
		// Swap x and y projects to match database - FIXME: no longer necessary after #206
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
		
		// Load projects
		UpdatePool pool = new UpdatePool(dbReader);
        ResultSet rs = pool.executeQuery("SELECT idx, name, type FROM projects");
        while ( rs.next() ) {
	        	int nIdx = rs.getInt("idx");
	        	String strName = rs.getString("name");
	        	String strType = rs.getString("type");
	        	projects.add( new Project(nIdx, strName, strType) );
        }
        rs.close();
        
        // Load project properties
        for (Project p : projects) {
	        	HashMap<String,String> projProps = loadProjectProps(pool, p.getID());
	        	p.setDisplayName(projProps.get("display_name"));
	        	p.setDescription(projProps.get("description"));
	        	p.setCategory(projProps.get("category"));
	        	boolean unord = projProps.containsKey("grp_sort") && projProps.get("grp_sort").equals("unordered");
	        	p.setUnordered(unord);
           	p.setOrderAgainst(projProps.get("order_against"));
           	if (projProps.containsKey("grp_type") && !projProps.get("grp_type").equals("")) p.setChrLabel(projProps.get("grp_type"));
        }
        
        // Load count of groups for each project
        for (Project p : projects) {
	        rs = pool.executeQuery("SELECT count(*) " +
	        		"FROM groups AS p " +
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
        }
        
        sortProjects(projects);
        
        return projects;
	}
	
	private static void sortProjects(Vector<Project> projects) {
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
	
	private static boolean removeAlignmentFromDisk(Project p1, Project p2)
	{
		String strDataPath = DATA_PATH + p1.getType() + "_" + p2.getType() 
								+ "/" + p1.getDBName() + "_to_" + p2.getDBName() ;
		
		return deleteDir( new File( strDataPath ) );
	}
		
    // Deletes all files and sub-directories under dir.
    public static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null)
            {
	            for (int i = 0;  i < children.length;  i++) {
	                boolean success = deleteDir(new File(dir, children[i]));
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

    private static void addNewProjectFromUI(Vector<Project> projects, String name, String type) {
		Project newProj = new Project(-1, name, type, name );
		newProj.setStatus( Project.STATUS_ON_DISK );
		if (!projects.contains(newProj))
		{
			Utils.checkCreateDir(new File(DATA_PATH + type + "/" + name));
			projects.add( newProj );						
		}
    }
	
	private static void addProjectsFromDisk(Vector<Project> projects, String strDataPath, String strType) {
		File root = new File(strDataPath + "/" + strType);
		
		if (root != null && root.isDirectory()) {
			for (File f : root.listFiles()) {
				if (f.isDirectory() && !f.getName().startsWith(".")) {
					Project newProj = new Project(-1, f.getName(), strType, f.getName() );
					newProj.setStatus( Project.STATUS_ON_DISK );
					if (!projects.contains(newProj))
					{
						newProj.getPropsFromDisk(f);
						projects.add( newProj );						
					}
				}
			}
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
			cmbType.setSelectedIndex(0);
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
			if (type.equals("sequence")) type = "pseudo";
			return type;
		}
		
		public void reset() { 
			txtName.setText("");
			cmbType.setSelectedIndex(0);
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
