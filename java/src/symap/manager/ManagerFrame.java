package symap.manager;

import java.sql.ResultSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.Comparator;
import java.util.Set;
import java.io.File;
import java.io.FileWriter;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.table.*;
import javax.swing.event.*;

import backend.*;
import blockview.*;
import circview.*;
import dotplot.DotPlotFrame;
import props.PropertiesReader;
import symap.Globals;
import symap.frame.ChrExpInit;
import symapQuery.SyMAPQueryFrame;

import database.DBconn2;
import database.Version;

import util.Cancelled;
import util.ErrorReport;
import util.Utilities;
import util.Jhtml;
import util.Jcomp;
import util.LinkLabel;

/*****************************************************
 * The manager main frame and provides all the high level functions for it.
 * This also has command line help and arguments
 * 
 * CAS508 moved the routines for alignment and synteny to AlignProjs, except for doAllPairs
 * CAS522 removed the last of FPC
 * CAS534 renamed from ProjectManagerFrameCommon; 
 * 		  package renamed from symap.projectmanager.common to manager
 * 		  moved out utilities stuff to reduce this file; rearranged
 * 		  use Mproject and Mpair as shared class for build
 * CAS535 moved Loads to backend.LoadProj and the Removes to RemoveProj.
 * CAS541 replace DBconn/DBAbsUser/DBuser with DBconn2
 */

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class ManagerFrame extends JFrame implements ComponentListener {
	/************************************************
	 * Command line arguments; set in symapCE.SyMAPmanager main 
	 */
	private static boolean TRACE = false;
	protected static boolean inReadOnlyMode = false;// set from viewSymap script with -r
	protected static int     maxCPUs = -1;			// arg -p
	protected static boolean bCheckVersion = false; // arg -v
	protected static boolean lgProj1st = false; 	// For Mummer; false sort on name; true sort on length
	private static boolean isCat = true;
	
	private final String DB_ERROR_MSG = "A database error occurred, please see the Troubleshooting Guide at:\n" + Jhtml.TROUBLE_GUIDE_URL;	
	private final String DATA_PATH = Constants.dataDir;
	private final int MIN_CWIDTH = 800, MIN_CHEIGHT = 900;  // Circle; CAS552 was 900, 1000
	private final int MIN_WIDTH = 850, MIN_HEIGHT = 600;	// Manager; CAS542 was 900, 600
	private final int MIN_DIVIDER_LOC = 220; 				// CAS543 was 240
	private final int LEFT_PANEL = 450;      
	
	private static final String HTML = "/html/ProjMgrInstruct.html";
	
	private final Color cellColor = new Color(85,200,100,85); // pale green; CAS541 new color for selected box 0,100,0,85
	private final Color textColor = new Color(0,0,170,255);   // deep blue;  CAS541 new color for selected text
	
	// If changed - don't make one a substring of another!!
	private final String TBL_DONE = "\u2713", TBL_ADONE = "A", TBL_QDONE = "?";
	private final String symapLegend = "Table Legend:\n"
			+ TBL_DONE 	+ " : synteny has been computed, ready to view.\n"
			+ TBL_ADONE + " : alignment is done, synteny needs to be computed.\n"
			+ TBL_QDONE + "  : alignment is partially done.";
	private final String selectPairText = "Select a Pair by clicking\ntheir shared table cell.";// Click a lower diagonal cell to select the project pair.
		
	protected String frameTitle="SyMAP " + Globals.VERSION; // CAS543 add so all frames use same title
	protected DBconn2  dbc2 = null; // master, used for local loads, and used as template for processes
	
	private HashMap <String, Mproject> projObjMap = new HashMap <String, Mproject> (); // displayName, same set as projVec
	private HashMap <String, String> projNameMap = new HashMap <String, String> ();	   // DBname to displayName
	private Vector<Mproject> projVec = new Vector <Mproject> (); // projects on db and disk (!viewSymap)
	
	protected Vector<Mproject> selectedProjVec = new Vector<Mproject>();
	
	private HashMap<Integer,Vector<Integer>> pairIdxMap = null; // project idx mapping from database
	private HashMap<String, Mpair> pairObjMap = new HashMap <String, Mpair> (); // idx1:idx2 is key
	
	private PairParams ppFrame = null; // only !null if opened
	private PropertiesReader dbProps;
	private String dbName = null; // CAS508 append to LOAD.log
	
	private int totalCPUs=0;
	
	private JButton btnAllChrExp, btnSelDotplot, btnAllDotplot, btnAllPairs, btnSelClearPair,
		btnSelBlockView, btnSelCircView, btnAllQueryView, btnSelSummary,
	    btnAddProject = null, btnSelAlign, btnPairParams;
	
	private JSplitPane splitPane;
	private JComponent instructionsPanel;
	private JTable pairTable;
	
	private AddProjectPanel addProjectPanel = null;
	
	private JTextField txtCPUs =null;
	private JCheckBox checkCat=null;
	
	/*****************************************************************/
	public ManagerFrame() {
		super("SyMAP " + Globals.VERSION);
		
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
        
		initialize(bCheckVersion);
		
		instructionsPanel = Jhtml.createInstructionsPanel(this.getClass().getResourceAsStream(HTML), getBackground());
		JPanel projPanel = createProjectPanel();
		
		splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, projPanel, instructionsPanel );
		splitPane.setDividerLocation(MIN_DIVIDER_LOC);
		
		add(splitPane);
		
		setSize(MIN_WIDTH, MIN_HEIGHT);
		setLocationRelativeTo(null); // CAS513 center frame
		
		addComponentListener(this); // hack to enforce minimum frame size
	}
		
	/***********************************************************************
	 * Called at startup and refreshMenu (after any project changes
	 */
	private void initialize(boolean checkSQL) {
		try {
			if (dbc2 == null) {
				dbc2 = makeDBconn();
				
				new Version(dbc2);
				
				if (checkSQL) dbc2.checkVariables(true); // CAS511 add; CAS541 change to DBconn2
			}
			
			Jhtml.setResClass(this.getClass());
			Jhtml.setHelpParentFrame(this);
			
			loadPanelProjsFromDB();
			loadProjectPairsFromDB(); // used results of loadProjectsFromDB
			if (pairIdxMap==null || projVec==null) ErrorReport.die("Major SyMAP problem");
			if (TRACE) dumpPairs();
			
			if (!inReadOnlyMode) { 											// CAS500; read remaining projects from disk
				String strDataPath = Constants.dataDir;
				
				if (Utilities.dirExists(strDataPath)) { 					// CAS511 quit creating data/seq, /data can be missing
					Utilities.checkCreateDir(Constants.logDir,true/*bPrt*/);// CAS534 removed /pseudo check and message
					
					loadPanelProjsFromDisk(strDataPath,  Constants.seqType); 	// must come after loadFromDB
					loadPanelProjsFromDisk(strDataPath,  "fpc"); 				// CAS520 gives message on fpc
				}
			}
			sortProjDisplay(projVec);
			
			Vector<Mproject> newSelectedProjects = new Vector<Mproject>();
			for (Mproject p1 : selectedProjVec) {
				for (Mproject p2 : projVec) {
					if ( p1.equals(p2) ) {
						newSelectedProjects.add(p2);
						break;
					}
				}
			}
			selectedProjVec = newSelectedProjects;
		}
		catch (Exception e) { ErrorReport.die(e, DB_ERROR_MSG);}
	}

	private static boolean isShutdown = false;
	private synchronized void shutdown() {
		if (!isShutdown) {
			if (dbc2!=null) dbc2.shutdown(); // Shutdown mysqld
			isShutdown = true;
		}
	}
	private class MyShutdown extends Thread { // runs at program exit
	    public void run() {
	    		shutdown();
	    }
	}
	/** Right Panel *********************************************************************/
	private JPanel createProjectPanel() {
		addProjectPanel = new AddProjectPanel(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				addNewProjectFromUI(addProjectPanel.getName());
				refreshMenu();
			}
		});
		JPanel panel = new JPanel();
		panel.setLayout( new BoxLayout ( panel, BoxLayout.Y_AXIS ) );
		panel.setBorder( BorderFactory.createEmptyBorder(10, 5, 10, 5) );
		
		JPanel projPanel = new JPanel();
		projPanel.setLayout(new BoxLayout( projPanel, BoxLayout.LINE_AXIS));
		projPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

		JLabel lblTitle = Jcomp.createLabel("Projects", Font.BOLD, 18);
		lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
		projPanel.add(lblTitle);
		panel.add(projPanel);
		panel.add(Box.createVerticalStrut(10));
		
		JPanel subPanel = new JPanel();
		subPanel.setLayout( new BoxLayout ( subPanel, BoxLayout.Y_AXIS ) );
		subPanel.setBorder( BorderFactory.createEmptyBorder(5, 5, 5, 0) );
		subPanel.setBackground(Color.white);
		
		// CAS513 changed from Vector to TreeSet so alphabetical display
		TreeMap<String, TreeSet<Mproject>> cat2Proj = new TreeMap<String, TreeSet<Mproject>>();
		for (Mproject p : projVec) {
			String category = p.getdbCat();
			if (category == null || category.length() == 0) category = "Uncategorized";
			if (!cat2Proj.containsKey(category)) cat2Proj.put(category, new TreeSet<Mproject>());
			cat2Proj.get(category).add(p);
		}
		
		// CAS513 Figure out what to show 
		Set <String> categories = cat2Proj.keySet();
		TreeMap <String, Integer> showMap = new TreeMap <String, Integer> ();
		for (String cat : categories) {
			for (Mproject p : cat2Proj.get(cat)) {
				if (inReadOnlyMode && p.getCntSyntenyStr().equals("")) continue; // CAS513 nothing to view
				
				if (showMap.containsKey(cat)) showMap.put(cat, showMap.get(cat)+1);
				else 						  showMap.put(cat, 1);
			}
		}
		for (String cat : showMap.keySet()) {
			if (showMap.get(cat)==0) continue;
			
			subPanel.add( new JLabel(cat) );
			
			for (Mproject p : cat2Proj.get(cat)) {
				if (inReadOnlyMode && p.getCntSyntenyStr().equals("")) continue; // CAS513 nothing to view
					
				ProjectCheckBox cb = null;
				String name = p.getDisplayName();
				boolean bLoaded = (p.getStatus() != Mproject.STATUS_ON_DISK);
				if (bLoaded) 
					name += " (" + p.getLoadDate() + ") " + p.getCntSyntenyStr(); // CAS513 change from (not loaded) 
				cb = new ProjectCheckBox( name, p );
				Font f = new Font(cb.getFont().getName(), Font.PLAIN, cb.getFont().getSize());
				cb.setFont(f); 
				subPanel.add( cb );
			}
			subPanel.add( Box.createVerticalStrut(5) );
		}
		
		JScrollPane scroller = new JScrollPane(subPanel, 
				JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scroller.getVerticalScrollBar().setUnitIncrement(10);
		scroller.setAlignmentX(Component.CENTER_ALIGNMENT);
		
		panel.add( scroller );
		panel.add(Box.createVerticalStrut(10));
		
		JPanel addPanel = new JPanel();
		addPanel.setLayout(new BoxLayout(addPanel, BoxLayout.LINE_AXIS));
		addPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
		
		btnAddProject = new JButton("Add Project");
		btnAddProject.setAlignmentX(Component.CENTER_ALIGNMENT);
		btnAddProject.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				addProjectPanel.reset();
				addProjectPanel.setVisible(true);
			}
		});
		if (!inReadOnlyMode) addPanel.add(btnAddProject);
		
		panel.add(addPanel);
		
		panel.setMinimumSize( new Dimension(MIN_DIVIDER_LOC-20, MIN_HEIGHT) ); // CAS543 add -20 so can shrink a little
		
		return panel;
	}
	/***** Left panel ***********************************************/
	private JPanel createSelectedPanel() { // CAS534 renamed from createSummaryPanel
		JPanel mainPanel = new JPanel();
		mainPanel.setLayout( new BoxLayout ( mainPanel, BoxLayout.Y_AXIS ) );
		mainPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 0));
		
	// Top area
		JLabel lblTitle = Jcomp.createLabel("Selected", Font.BOLD, 18);
		JButton btnHelp;
		if (!inReadOnlyMode)btnHelp = Jhtml.createHelpIconSysSm(Jhtml.SYS_HELP_URL, Jhtml.build); // CAS534 chg to '?'
		else 				btnHelp = Jhtml.createHelpIconUserSm(Jhtml.view); // CAS534 diff '?' for view
		mainPanel.add( createHorizPanel( new Component[] { lblTitle,btnHelp}, LEFT_PANEL)); 
		//mainPanel.add( new JSeparator() );
		
	// Add individual project summaries
		JPanel subPanel = new JPanel();
		subPanel.setLayout( new BoxLayout ( subPanel, BoxLayout.Y_AXIS ) );
		subPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		subPanel.add( Box.createVerticalStrut(5) );
		
		int nUnloaded = 0;
		for (Mproject p : selectedProjVec) {
			if (p.getStatus() == Mproject.STATUS_ON_DISK) nUnloaded++;
		}
		if (nUnloaded > 0) {
			LinkLabel  btnLoadAllProj = new LinkLabel("Load All Projects", Color.blue, Color.blue.darker());
			btnLoadAllProj.addMouseListener( doLoadAllProj );
			subPanel.add(btnLoadAllProj );
			subPanel.add( Box.createVerticalStrut(10) );			
		}
		for (Mproject mProj : selectedProjVec) {
			lblTitle = Jcomp.createLabel( mProj.getDisplayName(), Font.BOLD, 15 );
			
			JPanel textPanel = new JPanel();
			textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.PAGE_AXIS));
			textPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
			
			// line 1 - CAS500 re-ordered and add Length and Mask; CAS504 add notLoadedInfo
			String label1 = "Project: " + mProj.getDBName(); // CAS500 was Database Name
			
			String o = mProj.getOrderAgainst();
			if (o != null && o.length() > 0) label1 += "    Order against: " + o;
			
			if (mProj.isMasked()) label1 += "    Mask all but genes";
			textPanel.add(new JLabel(label1)); 
			
			// line 2
			String d = mProj.getdbDesc();
			if(d != null && d.length()>0) textPanel.add(new JLabel("Description: " + d));
			
			// rest of lines
			if(mProj.getStatus() == Mproject.STATUS_ON_DISK) { 
				String x = mProj.notLoadedInfo(); 
				textPanel.add(new JLabel(x));	
			} 
			else {
				String group = mProj.getdbGrpName();
				if (Utilities.isEmpty(group)) group = "Chromosome"; // CAS534 backward compatible
				String label2 = String.format("%s: %d    Bases: %,d    %s", 
						group, mProj.getNumGroups(), mProj.getLength(), mProj.getAnnoStr());
				
				textPanel.add(new JLabel(label2));
			}
			textPanel.setMaximumSize(textPanel.getPreferredSize());

			ProjectLinkLabel btnRemoveFromDisk = null, btnLoadOrRemove = null;
			ProjectLinkLabel btnReloadAnnot = null, btnReloadSeq = null;
			ProjectLinkLabel btnReloadParams = null, btnView = null;
			
			if (mProj.getStatus() == Mproject.STATUS_ON_DISK) {
				btnRemoveFromDisk = new ProjectLinkLabel("Remove from disk", mProj, Color.red);
				btnRemoveFromDisk.addMouseListener( doRemoveDisk );

				btnLoadOrRemove = new ProjectLinkLabel("Load project", mProj, Color.red);
				btnLoadOrRemove.addMouseListener( doLoad );

				btnReloadParams = new ProjectLinkLabel("Parameters", mProj, Color.blue);
				btnReloadParams.addMouseListener( doSetParamsNotLoaded ); 
			}
			else { // CAS42 1/1/18 was just "Remove" which is unclear; CAS505 removed 'project'
				btnLoadOrRemove = new ProjectLinkLabel("Remove from database", mProj, Color.blue);
				btnLoadOrRemove.addMouseListener( doRemove );

				btnReloadSeq = new ProjectLinkLabel("Reload project", mProj, Color.blue);
				btnReloadSeq.addMouseListener( doReloadSeq );
				
				btnReloadAnnot = new ProjectLinkLabel("Reload annotation", mProj, Color.blue);
				btnReloadAnnot.addMouseListener( doReloadAnnot );
				
				btnReloadParams = new ProjectLinkLabel("Parameters", mProj, Color.blue);
				btnReloadParams.addMouseListener( doReloadParams );

				btnView = new ProjectLinkLabel("View", mProj, Color.blue); // CAS521 add
				btnView.addMouseListener( doViewProj );
			}
			if (mProj.getStatus() == Mproject.STATUS_ON_DISK) {
				if (!inReadOnlyMode) // CAS500
					subPanel.add( createHorizPanel( new Component[] 
						{ lblTitle, btnRemoveFromDisk, btnLoadOrRemove, btnReloadParams }, 15 ) );				
			}
			else {
				if (!inReadOnlyMode) { 
					subPanel.add( createHorizPanel( new Component[] 
					{ lblTitle, btnLoadOrRemove, btnReloadSeq, btnReloadAnnot, btnReloadParams, btnView}, 10 ) );						
				}
				else { // CAS500
					subPanel.add( createHorizPanel( new Component[] { lblTitle, btnView}, 15));
				}
			}
			subPanel.add( textPanel );
			subPanel.add( Box.createVerticalStrut(10) );
		}
		
		subPanel.setMaximumSize( subPanel.getPreferredSize() );
		JScrollPane subScroller = new JScrollPane(subPanel, 
				JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		subScroller.setAlignmentX(Component.LEFT_ALIGNMENT);
		subScroller.getVerticalScrollBar().setUnitIncrement(10);
		subScroller.setBorder(null);
		subScroller.setMaximumSize( new Dimension( MIN_WIDTH, 290 ) ); // CAS534 bigger for lower scroll bar
		mainPanel.add( subScroller );
		
	// Add alignment table; lower part
		JComponent tableScroll = createPairTable();
		if (tableScroll != null) {
			lblTitle = Jcomp.createLabel("Available Syntenies", Font.BOLD, 16);
		
			JTextArea text1 = new JTextArea(symapLegend, 4, 1);
					
			text1.setBackground( getBackground() );
			text1.setEditable(false);
			text1.setLineWrap(false);
			text1.setAlignmentX(Component.LEFT_ALIGNMENT);
			
			// CAS42 1/5/18
			JTextArea text2 = new JTextArea(selectPairText,2,1);
			text2.setBackground( getBackground() );
			text2.setEditable(false);
			text2.setLineWrap(false);
			text2.setAlignmentX(Component.LEFT_ALIGNMENT);
			text2.setForeground(textColor); 
		
			btnSelAlign = new JButton("Selected Pair");
			btnSelAlign.setVisible(true);
			btnSelAlign.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					alignSelectedPair();
				}
			} );
			btnSelClearPair = new JButton("Clear Pair");
			btnSelClearPair.setVisible(true);
			btnSelClearPair.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					Mproject[] projects = getSetSelectedPair();
					if (projects==null) return;
					
					Mpair mp = getMpair(projects[0].getIdx(),projects[1].getIdx());
					new RemoveProj(getInstance(), buildLogLoad()).removeClearPair(mp, projects[0], projects[1]);
					refreshMenu();
				}
			} );	
			
			btnAllPairs = new JButton("All Pairs");
			btnAllPairs.setVisible(true);
			btnAllPairs.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent e) { 
					alignAllPairs();
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
			//btnAllChrExp.addActionListener( explorerListener); CAS541 moved from SyMAPmanager
			btnAllChrExp.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent e) { 
					showChrExp();
				}
			});
			
			btnAllDotplot = new JButton("Dot Plot");
			btnAllDotplot.setVisible(true);
			btnAllDotplot.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent e) { 
					showAllDotPlot();
				}
			});
			btnAllQueryView = new JButton("Queries"); // CAS520 SyMAP Queries moves up a line
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
			JLabel lbl4 = new JLabel("                     "); // CAS520 change Seq-to-Seq to blank
			Dimension d = lbl2.getPreferredSize();
			lbl1.setPreferredSize(d); lbl3.setPreferredSize(d); lbl4.setPreferredSize(d);
			
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
				mainPanel.add( createHorizPanel( new Component[] { tableScroll, new JLabel(" ")}, 5 ));			
			}
			btnPairParams = new JButton("Parameters");
			btnPairParams.addActionListener(showPairProps);
			
			if (!inReadOnlyMode) {
				mainPanel.add( Box.createRigidArea(new Dimension(0,15)) );	
				mainPanel.add( createHorizPanel( new Component[] { lbl1,
						btnAllPairs, btnSelAlign, btnSelClearPair,  btnPairParams}, 5) ); // CAS507 put allpairs first since btnPair is for select only now
			}
			mainPanel.add( Box.createRigidArea(new Dimension(0,15))  ); // CAS552 put circle 1st
			mainPanel.add( createHorizPanel( new Component[] {lbl2, btnSelCircView, btnSelDotplot, btnSelBlockView,  btnSelSummary }, 5) );

			mainPanel.add( Box.createRigidArea(new Dimension(0,15))  );
			mainPanel.add( createHorizPanel( new Component[] {lbl3, btnAllChrExp, btnAllDotplot, btnAllQueryView }, 5) );

			mainPanel.add( Box.createRigidArea(new Dimension(0,15))  );
			mainPanel.add( createHorizPanel( new Component[] {lbl4 }, 5) );
		} else {
			mainPanel.add( Box.createVerticalGlue() );
			mainPanel.add( Jcomp.createTextArea("",getBackground(), false) ); // kludge to fix layout problem			
		}
				
		updateEnableButtons();
		
		return mainPanel;
	}
	private Component createHorizPanel( Component[] comps, int gapWidth ) {
		JPanel panel = new JPanel();
		panel.setLayout( new BoxLayout ( panel, BoxLayout.X_AXIS ) );
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		
		for (Component c : comps) {
			if (c != null) {
				panel.add( c );
				if (gapWidth > 0)panel.add( Box.createHorizontalStrut(gapWidth) );
			}
		}
		panel.add( Box.createHorizontalGlue() );
		
		return panel;
	}
	private JComponent createPairTable() { // CAS534 renamed from createAlignmentTable
		if (pairTable != null) {// Clear previous table, disable listeners
			pairTable.getSelectionModel().removeListSelectionListener(tableRowListener);
			pairTable.getColumnModel().removeColumnModelListener( tableColumnListener );
			pairTable = null; 
		}
	// Create table contents
		sortProjDisplay(selectedProjVec); // sort by Display name
		Vector<String> columnNames = new Vector<String>();
		Vector<Vector<String>> rowData = new Vector<Vector<String>>();

		int maxProjName = 0;
		for (Mproject p1 : selectedProjVec) {
			maxProjName = Math.max(p1.getDisplayName().length(), maxProjName );
		}
		String blank = "";
		for(int q = 1; q <= maxProjName + 20; q++) blank += " ";
		
		columnNames.add(blank);

		for (Mproject p1 : selectedProjVec) {
			if (p1.getStatus() == Mproject.STATUS_ON_DISK) continue; // skip if not in DB		
			
			int id1 = p1.getIdx();
			columnNames.add( p1.getDisplayName() );
			
			Vector<String> row = new Vector<String>();
			row.add( p1.getDisplayName() );
		
			for (Mproject p2 : selectedProjVec) {
				if (p2.getStatus() == Mproject.STATUS_ON_DISK) continue; // skip if not in DB	
				int id2 = p2.getIdx();

				Mproject[] ordP = orderProjName(p1,p2); // order indices according to DB pairs table
				String resultDir = Constants.getNameAlignDir(ordP[0].getDBName(), ordP[1].getDBName());
				
				boolean isDone=false;
				if (isPair(id1, id2) || isPair(id2, id1)) {	
					row.add(TBL_DONE); 
					isDone=true;
				}
				else if (Utils.checkDoneFile(resultDir)) {	
					row.add(TBL_ADONE); 
				}
				else if (Utils.checkDoneMaybe(resultDir)>0) {
					row.add( TBL_QDONE); 
				}
				else row.add(null);
				
				if (!isDone) { // cannot do anything without an mpair
					String key = ordP[0].getIdx() + ":" + ordP[1].getIdx();
					if (!pairObjMap.containsKey(key)) {
						Mpair mp = new Mpair(dbc2, -1, ordP[0], ordP[1], inReadOnlyMode);
			        	pairObjMap.put(key, mp);
					}
				}
			}
			rowData.add( row );
		}
		
		if (rowData.size() == 0) return null;
		
	// Make table
		pairTable = new MyTable(rowData, columnNames);
		pairTable.setGridColor(Color.BLACK);
		pairTable.setShowHorizontalLines(true);
		pairTable.setShowVerticalLines(true);
		pairTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		pairTable.setSelectionBackground(cellColor); // this is the only place this seems to matter
		pairTable.setSelectionForeground(Color.BLACK);
		pairTable.setCellSelectionEnabled( true );
		pairTable.setModel( new ReadOnlyTableModel(rowData, columnNames) );
		DefaultTableCellRenderer tcr = new MyTableCellRenderer();
		tcr.setHorizontalAlignment(JLabel.CENTER);
		pairTable.setDefaultRenderer( Object.class, tcr);
	    TableColumn col = pairTable.getColumnModel().getColumn(0);
		DefaultTableCellRenderer tcr2 = new MyTableCellRenderer();	  
		tcr2.setHorizontalAlignment(JLabel.LEFT);
	    col.setCellRenderer(tcr2);

		pairTable.getSelectionModel().addListSelectionListener( tableRowListener );
		pairTable.getColumnModel().addColumnModelListener( tableColumnListener );
		pairTable.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {}
		});
		autofitColumns(pairTable);
		
	// A scroll pane is needed or the column names aren't shown
		JScrollPane scroller = new JScrollPane( pairTable, 
				JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroller.setAlignmentX(Component.LEFT_ALIGNMENT);
		Dimension tableSize = new Dimension(
				(int)pairTable.getPreferredSize().getWidth(),
				(int)pairTable.getPreferredSize().getHeight() 
				+ (int)pairTable.getTableHeader().getPreferredSize().getHeight() + 2);
		scroller.setPreferredSize( tableSize );
		scroller.setMaximumSize( tableSize );
		scroller.setMinimumSize( tableSize );
		scroller.setBorder( null );
		
		return scroller;
	}
	
	/***********************************************************
	 * Enable function buttons; CAS505 rewrote
	 */
	private void updateEnableButtons() {
		if (pairTable == null) return;
		
		Mproject[] projects = getSetSelectedPair();  // null if nothing selected
		btnPairParams.setEnabled(projects!=null);  // CAS507 must select one 
		
		int numProj =  pairTable.getRowCount();
		btnAllPairs.setEnabled(numProj>1 && isAlignAllPairs());
		
		int numDone = getNumCompleted(false); // do not ignore self; 
		btnAllChrExp.setEnabled(numDone>0);
		
		numDone = getNumCompleted(true); // ignore selfs
		btnAllQueryView.setEnabled(numDone>0);
		btnAllDotplot.setEnabled(numDone>0);   // this does not work for self-align
		
		if ((projects!=null)) {
			int nRow = pairTable.getSelectedRow();
			int nCol = pairTable.getSelectedColumn();
			String val = (String)pairTable.getValueAt(nRow, nCol);
			
			boolean allDone =  (val!=null && val.contains(TBL_DONE));
			boolean partDone = (val!=null && (!val.contains(TBL_ADONE) || !val.contains(TBL_QDONE)));
			
			if (allDone) btnSelAlign.setText("Selected Pair (Redo)");	
			else 		 btnSelAlign.setText("Selected Pair");	
			
			btnSelAlign.setEnabled(true);
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
	private boolean isPair(int id1, int id2) {
		return pairIdxMap.containsKey(id1) && pairIdxMap.get(id1).contains(id2);
	}
	// is there any not done or partial done
	private boolean isAlignAllPairs() { 
		for (int row = 0;  row < pairTable.getRowCount();  row++) {
			for (int col = 0;  col < pairTable.getColumnCount();  col++) {
				if (col >= (row+1)) continue;
				
				String val = (String)pairTable.getValueAt(row, col);
				if (val==null) return true;
				if (val.contains(TBL_ADONE)) return true;
				if (val.contains(TBL_QDONE)) return true;
			}
		}
		return false;
	}
	// how many are done; first column is project names
	private int getNumCompleted(boolean ignSelf) {
		int count = 0;
		for (int row = 0;  row < pairTable.getRowCount();  row++) {
			for (int col = 0;  col < pairTable.getColumnCount();  col++) {
				if (col > (row+1)) continue;
				if (ignSelf && (row+1)==col) continue;
				
				String val = (String)pairTable.getValueAt(row, col);
				if (val != null && val.contains(TBL_DONE)) count++;
			}
		}	
		return count;
	}
	
	/************* XXX Listeners ********************/
	private ItemListener checkboxListener = new ItemListener() { // left panel change
		public void itemStateChanged(ItemEvent e) {
			ProjectCheckBox cb = (ProjectCheckBox)e.getSource();
			Mproject p = cb.getProject();
			boolean changed = false;
			
			if ( e.getStateChange() == ItemEvent.SELECTED ) {
				if ( !selectedProjVec.contains(p) ) selectedProjVec.add(p);
				cb.setBackground(cellColor); // highlight
				changed = true;
			}
			else if ( e.getStateChange() == ItemEvent.DESELECTED ) {
				selectedProjVec.remove(p);
				cb.setBackground(Color.white); // un-highlight
				changed = true;
			}
			
			if (changed) {
				sortProjDisplay( selectedProjVec );
				if (pairTable != null) pairTable.clearSelection();	

				// Note: recreating each time thrashes heap, but probably negligible performance impact
				if ( selectedProjVec.size() > 0 )
					splitPane.setRightComponent( createSelectedPanel() ); // regenerate since changed
				else
					splitPane.setRightComponent( instructionsPanel );
			}
		}
	};
	private ActionListener showPairProps = new ActionListener() {
		public void actionPerformed(ActionEvent arg0) {
			Mproject[] sel = getSetSelectedPair();
		
			Mpair p = getMpair(sel[0].getIdx(), sel[1].getIdx());
			if (p!=null) {
				ppFrame = new PairParams(p);
				ppFrame.setVisible(true);
			}
		}
	};
		
	private MouseListener doLoadAllProj = new MouseAdapter() {
		public void mouseClicked(MouseEvent e) {
			loadAllProjects();
		}
	};
	private MouseListener doLoad = new MouseAdapter() {
		public void mouseClicked(MouseEvent e) {
			Mproject mproj = ((ProjectLinkLabel)e.getSource()).getProject();
			loadProject(mproj);
		}
	};
	private MouseListener doReloadSeq = new MouseAdapter() {
		public void mouseClicked(MouseEvent e) {
			Mproject mproj = ((ProjectLinkLabel)e.getSource()).getProject();
			reloadProject(mproj);		
		}
	};
	private MouseListener doReloadAnnot = new MouseAdapter() {
		public void mouseClicked(MouseEvent e) {
			Mproject mproj = ((ProjectLinkLabel)e.getSource()).getProject();
			reloadAnno(mproj);	
		}
	};	
	
	private MouseListener doRemove = new MouseAdapter() {
		public void mouseClicked(MouseEvent e) {
			Mproject p = ((ProjectLinkLabel)e.getSource()).getProject();
			new RemoveProj(getInstance(), buildLogLoad()).removeProjectFromDB(p );
			refreshMenu();
		}
	};
	private MouseListener doRemoveDisk = new MouseAdapter() {
		public void mouseClicked(MouseEvent e) {
			new RemoveProj(getInstance(), buildLogLoad()).removeProjectFromDisk( ((ProjectLinkLabel)e.getSource()).getProject() );
			refreshMenu();
		}
	};
	
	private MouseListener doViewProj = new MouseAdapter() { // CAS521 add
		public void mouseClicked(MouseEvent e) {
			Mproject theProject = ((ProjectLinkLabel)e.getSource()).getProject();
			String info = theProject.loadProjectForView();
			
			Utilities.displayInfoMonoSpace(getInstance(), "View " + theProject.getDisplayName(), info, false);	
		}
	};
	
	private MouseListener doReloadParams = new MouseAdapter() {
		public void mouseClicked(MouseEvent e) {
			Mproject mProj = ((ProjectLinkLabel)e.getSource()).getProject();
			
			ProjParams propFrame = new ProjParams(getInstance(), mProj, projVec, 
					true, mProj.hasExistingAlignments());
			propFrame.setVisible(true);
			
			if (propFrame.wasSave()) refreshMenu();		
		}
	};
	private MouseListener doSetParamsNotLoaded = new MouseAdapter() {
		public void mouseClicked(MouseEvent e) {
			Mproject mProj = ((ProjectLinkLabel)e.getSource()).getProject();
			
			ProjParams propFrame = new ProjParams(getInstance(), mProj, projVec, 
					false, mProj.hasExistingAlignments());
			propFrame.setVisible(true);
			
			if (propFrame.wasSave()) refreshMenu(); // CAS504
		}		
	};
		
	// CAS521 an error report on the strColProjName line getting null. Added error checks in all calling methods.
	private Mproject[] getSetSelectedPair() {
		int nRow=-1, nCol=-1;
		try {
			if (pairTable == null) return null;
			
			nRow = pairTable.getSelectedRow(); // Row is >=0 Col is >=1
			nCol = pairTable.getSelectedColumn();
			
			// If none selected, automatically select one if 2 rows (CAS552 does not work with 1 row)
			if (nRow < 0 || nCol <= 0) {
				int n = pairTable.getRowCount(); // CAS541 if n=2, automatically make selected
				if (n!=2) return null;			 // CAS521 change < to <=; this happens on startup
				nCol=1; nRow=1;			 
				
				pairTable.setRowSelectionInterval(nRow, nCol);
				pairTable.setColumnSelectionInterval(nRow, nCol);
			}
				
			String strRowProjName = pairTable.getValueAt(nRow, 0).toString();
			String strColProjName = pairTable.getValueAt(nCol-1, 0).toString();

			Mproject p1 = projObjMap.get( strRowProjName );
			Mproject p2 = projObjMap.get( strColProjName );
			
			if (p1==null) {
				System.out.println(strRowProjName + ": There is an inconsistency with the Display Name; save project params");
				return null;
			}
			if (p2==null) {
				System.out.println(strColProjName + ": There is an inconsistency with the Display Name; save project params");
				return null;
			}
			return orderProjName(p1,p2);
		}
		catch (Exception e) {ErrorReport.print(e, "Could not get row " + nRow + " and column " + nCol); return null;}
	}
	// XXX sort for getting pair indices in right order
	private Mproject[] orderProjName(Mproject p1, Mproject p2){
		if (p1.strDBName.compareToIgnoreCase(p2.strDBName) > 0) // make alphabetic
			return new Mproject[] { p2, p1 }; 
		else
			return new Mproject[] { p1, p2 };
	}
	// Order shown on manager by display name
	private void sortProjDisplay(Vector<Mproject> projVec) {
		Collections.sort( projVec, new Comparator<Mproject>() {
		    public int compare(Mproject p1, Mproject p2) {
		    	return p1.strDisplayName.compareTo( p2.strDisplayName ); 
		    }
	    });
	}
		
	// CAS500 New log file for load
	private boolean first=true;
	private FileWriter buildLogLoad() {
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
	
	private ManagerFrame getInstance() { return this; }
	
	/*******************************************************************/
	private Mpair getMpair(int n1, int n2) {
		String key = n1 + ":" + n2;
		if (pairObjMap.containsKey(key)) return pairObjMap.get(key);
		
		key = n2 + ":" + n1; // CAS547 they can be entered n1>n2
		if (pairObjMap.containsKey(key)) return pairObjMap.get(key);
		
		System.out.println("SyMAP error on " + key);
		for (String keys : pairObjMap.keySet()) {
			System.out.print(keys + " ");
		}
		System.out.println();
		
		return null;
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
	
	public void refreshMenu() {
		Utilities.setCursorBusy(this, true);
		
		initialize(false); // ZZZ recreates project lists on every refresh; easier to modifing projVec etc
		
		splitPane.setLeftComponent( createProjectPanel() );
		if (selectedProjVec.size() > 0)
			splitPane.setRightComponent( createSelectedPanel() );
		else
			splitPane.setRightComponent( instructionsPanel );
		
		Utilities.setCursorBusy(this, false);
	}
	/***********************************************************************/
	private void showChrExp() { // CAS541 moved from SyMAP manager; CAS550 moved build logic to ChrExpInit
		Utilities.setCursorBusy(this, true);
		try {
			ChrExpInit symapExp = new ChrExpInit(frameTitle + " - ChrExp", dbc2, selectedProjVec);
			
			symapExp.getExpFrame().setVisible(true); 
		}
		catch (Exception err) {ErrorReport.print(err, "Show SyMAP graphical window");}
		finally {
			Utilities.setCursorBusy(this, false);
		}
	}
	/*********************************************************************/
	private void showQuery() {
		Utilities.setCursorBusy(this, true);
		try { // CAS532 send project vector 
			Vector <Mproject> pVec = new Vector <Mproject> ();
			for (Mproject p : selectedProjVec) if (p.isLoaded()) pVec.add( p );
			
			boolean useAlgo2=true; // CAS547 add for new "EveryPlus"
			for (int i=0; i<pVec.size()-1; i++) {
				for (int j=i+1; j<pVec.size(); j++) {
					Mpair mp = getMpair(pVec.get(i).getIdx(), pVec.get(j).getIdx());
					if (mp.isSynteny()) {
						if (!mp.isAlgo2(Mpair.DB)) 	{
							useAlgo2=false;
							break;
						}
					}
				}
			}
			SyMAPQueryFrame qFrame = new SyMAPQueryFrame(frameTitle, dbc2, pVec, useAlgo2);
			qFrame.build();
			qFrame.setVisible(true);
		} 
		catch(Exception e) {ErrorReport.print(e, "Show Query");}
		finally {
			Utilities.setCursorBusy(this, false);
		}
	}
	/*****************************************************************/
	private void showDotplot() { 
		Utilities.setCursorBusy(this, true);
		
		Mproject[] p = getSetSelectedPair();
		if (p==null) return;
		
		int projXIdx = p[0].getID();
		int projYIdx = projXIdx; // default to self-alignment
		if (p.length > 1) projYIdx = p[1].getID();
		
		/* Swap x and y projects to match database - no longer necessary after #206
		if (pairMap.containsKey(projXIdx) && pairMap.get(projXIdx).contains(projYIdx)) {
			int temp = projXIdx;projXIdx = projYIdx;projYIdx = temp;}*/
		
		DotPlotFrame frame = new DotPlotFrame(frameTitle+ " - Dot Plot", dbc2, projXIdx, projYIdx);
		frame.setSize( new Dimension(MIN_WIDTH, MIN_HEIGHT) );
		frame.setVisible(true);
		
		Utilities.setCursorBusy(this, false);
	}
	/*****************************************************************/
	private void showBlockView()  { 
		Utilities.setCursorBusy(this, true);
		
		Mproject[] p = getSetSelectedPair();
		if (p==null) return;
		
		Mproject p1=p[0];
		Mproject p2 = (p.length>1) ? p[1] : p[0];
		
		try{
			BlockViewFrame frame = new BlockViewFrame(frameTitle + " - Block", dbc2, p1.getIdx(), p2.getIdx());
			frame.setMinimumSize( new Dimension(MIN_WIDTH, MIN_HEIGHT) );
			frame.setVisible(true);
		}
		catch (Exception e) {ErrorReport.die(e, "Show block view");
		}
		
		Utilities.setCursorBusy(this, false);
	}
	/*****************************************************************/
	private void showSummary(){
		Utilities.setCursorBusy(this, true);
		
		Mproject[] p = getSetSelectedPair();
		if (p==null) return;
		
		int projXIdx = p[0].getIdx();
		int projYIdx = (p.length>1) ? p[1].getIdx() : projXIdx; 
		
		Mpair mp = getMpair(projXIdx, projYIdx);
		if (mp==null) return;
		
		/* Swap x and y projects to match database - no longer necessary after #206; CAS534 no reason for this
		if (pairMap.containsKey(projXIdx) && pairMap.get(projXIdx).contains(projYIdx)) {
			int temp = projXIdx;projXIdx = projYIdx;projYIdx = temp;} */
		// if (projYIdx == projXIdx) projYIdx = 0; // CAS500 allows self summary
				
		new SumFrame(frameTitle + " - Summary", dbc2, mp, inReadOnlyMode); // CAS540 dialog made visible in SumFrame; CAS541 dbuser->dbc2
		
		Utilities.setCursorBusy(this, false);		
	}
	/*****************************************************************/
	private void showCircleView() { 
		Utilities.setCursorBusy(this, true);
		
		Mproject[] p = getSetSelectedPair();
		if (p==null) return;
		
		int projXIdx = p[0].getID();
		int projYIdx = (p.length > 1) ? p[1].getID() : projXIdx; // 2-align : self-align
		boolean hasSelf = (p[0].hasSelf() || p[1].hasSelf()); // CAS552 add for Self-Align
		
		/* Swap x and y projects to match database - no longer necessary after #206; CAS534 remove
		if (pairMap.containsKey(projXIdx) && pairMap.get(projXIdx).contains(projYIdx)) {
			int temp = projXIdx;projXIdx = projYIdx;projYIdx = temp;}*/
		if (projYIdx == projXIdx) projYIdx = 0;
		
		CircFrame frame = new CircFrame(frameTitle + " - Circle", dbc2, projXIdx, projYIdx, hasSelf);
		frame.setSize( new Dimension(MIN_CWIDTH, MIN_CHEIGHT) );
		frame.setVisible(true);
		
		Utilities.setCursorBusy(this, false);
	}
	/*****************************************************************/
	private void showAllDotPlot()	{
		int nProj = 0;
		for (Mproject p : selectedProjVec) {
			if (p.getStatus() != Mproject.STATUS_ON_DISK) {
				nProj++;
			}
		}
		int[] pids = new int[nProj];
		int i = 0;
		for (Mproject p : selectedProjVec) {
			if (p.getStatus() != Mproject.STATUS_ON_DISK) {
				pids[i] = p.getID();
				i++;
			}
		}
		DotPlotFrame frame = new DotPlotFrame(frameTitle + " - Dot Plot", dbc2, pids, null, null, null, true);
		frame.setSize( new Dimension(MIN_WIDTH, MIN_HEIGHT) );
		frame.setVisible(true);
	}
	
	/***********************************************************************
	 * Database CAS541 change to use DBconn2 instead of DBAbsUser
	 **************************************************/
	private DBconn2 makeDBconn()  { 
	// Check variables
		String paramsfile = Globals.MAIN_PARAMS;
		
		if (Utilities.fileExists(paramsfile)) System.out.println("Reading from file " + paramsfile);
		else ErrorReport.die("Configuration file not available: " + paramsfile);
		
		dbProps = new PropertiesReader(new File(paramsfile));
		
		String db_server       = dbProps.getProperty("db_server");
		String db_name         = dbProps.getProperty("db_name");
		String db_adminuser    = dbProps.getProperty("db_adminuser"); // note: user needs write access
		String db_adminpasswd  = dbProps.getProperty("db_adminpasswd");
		String db_clientuser   = dbProps.getProperty("db_clientuser");
		String db_clientpasswd = dbProps.getProperty("db_clientpasswd");
		String cpus 		   = dbProps.getProperty("nCPUs"); // CAS500

		if (db_server != null) 			db_server = db_server.trim();
		if (db_name != null) 			db_name = db_name.trim();
		if (db_adminuser != null) 		db_adminuser = db_adminuser.trim();
		if (db_adminpasswd != null) 	db_adminpasswd = db_adminpasswd.trim();
		if (db_clientuser != null) 		db_clientuser = db_clientuser.trim();
		if (db_clientpasswd != null) 	db_clientpasswd = db_clientpasswd.trim();
		
		if (Utilities.isEmpty(db_server)) db_server = "localhost";
		if (Utilities.isEmpty(db_name))	  db_name = "symap";
		
		dbName = db_name;
		frameTitle += " - " + db_name;
		setTitle(frameTitle);
		System.out.println("SyMAP database " + db_name);

		try { // CAS508 stop nested exception on DatabaseUser calls
			if (inReadOnlyMode) { // CAS500 crashed if config file not present
				if (Utilities.isEmpty(db_clientuser)) {
					if (Utilities.isEmpty(db_adminuser)) ErrorReport.die("No db_adminuser or db_clientuser in " + paramsfile);
					else db_clientuser = db_adminuser;
				}
				if (Utilities.isEmpty(db_clientpasswd)) {
					if (Utilities.isEmpty(db_adminpasswd)) ErrorReport.die("No db_adminpasswd or db_clientpasswd in " + paramsfile);
					else db_clientpasswd = db_adminpasswd;
				}
			}
			else {
				if (Utilities.isEmpty(db_adminuser))   ErrorReport.die("No db_adminuser defined in " + paramsfile);
				if (Utilities.isEmpty(db_adminpasswd)) ErrorReport.die("No db_adminpasswd defined in " + paramsfile);
			}
			
			String mummer 	= dbProps.getProperty("mummer_path"); // CAS508 
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
			
		// Check database exists or create CAS511 add if exist
	        if (inReadOnlyMode) {
	        	if (!DBconn2.existDatabase(db_server, db_name, db_clientuser, db_clientpasswd)) 
		        	ErrorReport.die("*** Database '" + db_name + "' does not exist");
	        }
	        else { 
	        	if (!DBconn2.createDatabase(db_server, db_name, db_adminuser, db_adminpasswd)) 
		        	ErrorReport.die(DB_ERROR_MSG);
	        }
	     
	        String user = (inReadOnlyMode ? db_clientuser   : db_adminuser);
	        String pw   = (inReadOnlyMode ? db_clientpasswd : db_adminpasswd);
	        
	        return new DBconn2("Manager", db_server, db_name, user, pw);
		}
		catch (Exception e) {ErrorReport.die("Error getting connection"); }
		return null;
	}
	
	/*******************************************************************************
	 * load projects for interface
	 ******************************************************************/
	private void loadProjectPairsFromDB() {
	try {
		pairIdxMap = new HashMap<Integer,Vector<Integer>>();
		pairObjMap.clear();
		
		HashMap <Integer, String> pairs = new HashMap <Integer, String> ();
		
		// CAS534 rm " join projects as p1 on p1.idx=pairs.proj1_idx join projects as p2 on p2.idx=pairs.proj2_idx "  +
        ResultSet rs = dbc2.executeQuery("SELECT idx, proj1_idx, proj2_idx FROM pairs where aligned=1");
              
        while (rs.next()) {
        	int pairIdx =  rs.getInt(1);
        	int proj1Idx = rs.getInt(2);
        	int proj2Idx = rs.getInt(3); 
  
        	if (!pairIdxMap.containsKey(proj1Idx)) pairIdxMap.put(proj1Idx, new Vector<Integer>() );
        	pairIdxMap.get(proj1Idx).add(proj2Idx);
        	
        	pairs.put(pairIdx, proj1Idx + ":" + proj2Idx);
        }
        rs.close();
        
        for (int pidx : pairs.keySet()) {
        	String [] tmp = pairs.get(pidx).split(":");
        	int proj1Idx = Utilities.getInt(tmp[0]);
        	int proj2Idx = Utilities.getInt(tmp[1]);
        	
        	Mproject mp1=null, mp2=null;
        	for (Mproject mp : projVec) {
        		if (mp.getIdx() == proj1Idx) mp1 = mp;
        		if (mp.getIdx() == proj2Idx) mp2 = mp;
        	}
        	if (mp1==null || mp2==null) System.out.println("SYMAP error: no " + pidx + " projects");
        	else {
	        	Mpair mp = new Mpair(dbc2, pidx, mp1, mp2, inReadOnlyMode); // reads database
	        	pairObjMap.put(proj1Idx + ":" + proj2Idx, mp);
        	}
        }
	}catch (Exception e) {ErrorReport.print(e,"Load projects pairs"); }
	}
	
	/*****************************************************************/
	private void loadPanelProjsFromDB() { 
	try {
		projVec.clear();
		projObjMap.clear();
		projNameMap.clear();
		
        ResultSet rs = dbc2.executeQuery("SELECT idx, name, annotdate FROM projects");
        while ( rs.next() ) {
        	int nIdx = rs.getInt(1);
        	String dbName = rs.getString(2);
        	String strDate = rs.getString(3);       // CAS513 add
        	
    		Mproject mp = new Mproject(dbc2, nIdx, dbName, strDate);
    		projVec.add(mp);
        }
        rs.close();
  
        for (Mproject mp : projVec) {
        	mp.loadParamsFromDB(); 
        	mp.loadDataFromDB();
        	projObjMap.put(mp.strDisplayName, mp);
        	projNameMap.put(mp.strDBName, mp.strDisplayName);
        }
	}
	catch (Exception e) {ErrorReport.print(e,"Load projects"); }
	}
	
	private void loadPanelProjsFromDisk(String strDataPath, String dirName) {
		File root = new File(strDataPath + dirName); 
		if (root == null || !root.isDirectory()) return;
		
		if (dirName.contentEquals("fpc")) { // CAS520
			System.err.println("FPC is no longer supported starting with v5.2.0; ignore data/fpc directory");
			return;
		}
		for (File f : root.listFiles()) {
			if (f.isDirectory() && !f.getName().startsWith(".")) {
				Mproject mp;
				String dbname = f.getName();
				if (projNameMap.containsKey(dbname))  {
					String display = projNameMap.get(dbname);
					mp = projObjMap.get(display);
				}
				else {
					mp = new Mproject(dbc2, -1, f.getName(), ""); 
					projVec.add( mp );
					projObjMap.put(mp.strDisplayName, mp);
		        	projNameMap.put(mp.strDBName, mp.strDisplayName);
				}
				mp.loadParamsFromDisk(f);
			}
		}
	}
	/*****************************************************************/
	/***************************************************************
	 * XXX LoadProj: CAS535 the load was all in listeners; now its in LoadProj via these methods
	 */
	private void loadAllProjects() {
		try {
			new LoadProj(this, dbc2, buildLogLoad()).loadAllProjects(selectedProjVec); 
			new Version(dbc2).updateReplaceProp();
			refreshMenu();
		}
		catch (Exception e) {ErrorReport.print(e, "load all project");}
	}
	private void loadProject(Mproject mProj) {
		try {
			new LoadProj(this, dbc2, buildLogLoad()).loadProject(mProj);
			new Version(dbc2).updateReplaceProp();
			refreshMenu();
		}
		catch (Exception e) {ErrorReport.print(e, "load project");}
		}
	private void reloadProject(Mproject mProj) {
	try {
		String msg = "Reload project " + mProj.getDisplayName();
		if (!mProj.hasExistingAlignments()) {
			if (!Utilities.showConfirm2("Reload project",msg)) return;
		}
		else {
			int rc = Utilities.showConfirm3("Reload project",msg +
				"\n\nOnly: reload project only" +
				"\nAll: reload project and remove alignments from disk");
			if (rc==0) return;
			
			if (rc==2) {
				System.out.println("Removing " +  mProj.getDisplayName() + " alignments from disk...."); // CAS541 add
				new RemoveProj().removeAllAlignFromDisk(mProj);
			}
		}
		System.out.println("Removing " +  mProj.getDisplayName() + " from database...."); // CAS541 add
		mProj.removeProjectFromDB();
		new LoadProj(this, dbc2, buildLogLoad()).loadProject(mProj); 
		new Version(dbc2).updateReplaceProp();
		refreshMenu();
	}
	catch (Exception e) {ErrorReport.print(e, "reload project");}
	}
	// Reload
	private void reloadAnno(Mproject mProj) {
	try {
		String msg = "Reload annotation " + mProj.getDisplayName();
		if (!Globals.GENEN_ONLY) { 
			if (!Utilities.showConfirm2("Reload annotation", msg +
					"\n\nYou will need to re-run the synteny computations for this project," +      // CAS520 do not remove synteny
					"\nany existing alignment files will be used.")) return; // CAS512 remove MUMmer and BLAT
		}
		else { // CAS519
			if (!Utilities.showConfirm2("Reload annotation", msg +
					"\n\nThe -z flag is set. Only the Gene# assignment algorithm will be run. " +
					"\nNo further action will be necessary.")) return; 
		}
		System.out.println("Removing " +  mProj.getDisplayName() + " annotation from database...."); // CAS541 add
		mProj.removeAnnoFromDB();
		new LoadProj(this, dbc2, buildLogLoad()).reloadAnno(mProj);
		new Version(dbc2).updateReplaceProp();
		refreshMenu();
	}
	catch (Exception e) {ErrorReport.print(e, "reload project");}
	}
	/***************************************************
	 * AlignProjs
	 */
	private void alignAllPairs() { //All Pairs is selected; CAS506 added checkPairs logic
		Cancelled.init();
		if (pairTable == null) return;
		
		Vector <Mpair> todoList = new Vector <Mpair> ();
		
		int nRows = pairTable.getRowCount();
		int nCols = pairTable.getColumnCount();
		if (nRows == 0 || nCols == 0) return;
		
		for (int r = 0; r < nRows; r++) {
			String strRowProjName = pairTable.getValueAt(r, 0).toString();
			if (Cancelled.isCancelled()) break;

			for (int c = 1; c <= r+1; c++) {
				if (Cancelled.isCancelled()) break;

				String strColProjName = pairTable.getValueAt(c-1,0).toString();
				String entry = 	(pairTable.getValueAt(r,c) == null ? "" : pairTable.getValueAt(r,c).toString().trim());
				
				boolean doThis = false;
				if (entry.equals("-")) continue;
				else if (entry.equals(TBL_DONE)) doThis = false;
				else if (entry.equals(TBL_ADONE) || entry.equals(TBL_QDONE)) doThis = true;	
				else if (entry.equals("")) doThis = true;	
				
				if (doThis) {
					Mproject p1 = projObjMap.get( strRowProjName );
					Mproject p2 = projObjMap.get( strColProjName );
					Mproject[] ordered = orderProjName(p1,p2);
					p1 = ordered[0]; p2 = ordered[1];
					if (p1.getIdx() != p2.getIdx()) {
						if (alignCheckOrderAgainst(p1, p2)) { // CAS506 
							Mpair mp = getMpair(p1.getIdx(), p2.getIdx());
							if (mp!=null) todoList.add(mp);
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
		if (!alignCheckProj()) return;
		
		if (!Utilities.showConfirm2("All Pairs", // CAS543 add check
				"Align&Synteny for " + todoList.size() + " pairs")) return;
		System.out.println("\n>>> Start all pairs: processing " + todoList.size() + " project pairs");
		for (Mpair mp : todoList) {
			mp.renewIdx(); // Removed existing
			
			// AlignProj open/close new dbc2 for each thread of align
			new AlgSynMain().run(this, dbc2, mp, true,  maxCPUs, checkCat.isSelected());
		}
		new Version(dbc2).updateReplaceProp();
		System.out.println("All Pairs complete. ");
	}
	private void alignSelectedPair( ) {
		Mproject[] mProjs = getSetSelectedPair();
		if (mProjs==null) return;
		
		if (alignCheckOrderAgainst(mProjs[0], mProjs[1])) {
			int nCPU = getCPUs();
			if (nCPU == -1) nCPU=1;;
			if (!alignCheckProj()) return;
			
			Mpair mp = getMpair(mProjs[0].getIdx(), mProjs[1].getIdx());
			if (mp==null) return;
			
			Mproject[] ordP = orderProjName(mProjs[0],mProjs[1]); // order indices according to DB pairs table
			String resultDir = Constants.getNameAlignDir(ordP[0].getDBName(), ordP[1].getDBName());
			
			boolean bAlign = Utils.checkDoneFile(resultDir);
			String msg =  bAlign ? "Synteny for " : "Align&Synteny for "; // CAS544 be specific
			
			if (mProjs[0].getDisplayName().equals(mProjs[1].getDisplayName())) { // CAS546 add
				msg += mProjs[0].getDisplayName() + " to itself";
				if (mp.isAlgo2(Mpair.FILE)) {
					util.Utilities.showInfoMessage("Cluster Algo", msg +"\nYou must select Algorithm 1 from Parameters");
					return;
				}
			}
			else msg += mProjs[0].getDisplayName() + " and " + mProjs[1].getDisplayName();
			
			String chgMsg = bAlign ? mp.getChangedSynteny() : mp.getChangedParams(Mpair.FILE);  // CAS546 add params
			if (chgMsg!="") msg += "\n" + chgMsg;
			
			if (!Utilities.showConfirm2("Selected Pair", msg)) return;
			
			System.out.println("\n>>> Start Alignment&Synteny");
			mp.renewIdx(); // Remove existing and restart
			
			new AlgSynMain().run(getInstance(), dbc2, mp, false, nCPU, checkCat.isSelected());
			new Version(dbc2).updateReplaceProp();
		}
	}
	// CAS511 create directories if alignment is initiated
	// An alignment may be in the database, yet no /data directory. The files will be rewritten, so the directories are needed.
	private boolean alignCheckProj() { 
		try {
			Utilities.checkCreateDir(DATA_PATH, true);
			Utilities.checkCreateDir(Constants.seqRunDir,  true);
			Utilities.checkCreateDir(Constants.seqDataDir, true); 
			
			return true;
		}
		catch (Exception e){return false;}
	}
	 //CAS506 - add some checks before starting order against project
	private boolean alignCheckOrderAgainst(Mproject mProj1, Mproject mProj2) {
		String n1, n2, o;
		
		String o1 = mProj1.getOrderAgainst();
		String o2 = mProj2.getOrderAgainst();
		
		if (o1==null || o2==null) return true; // CAS506 got null on linux
		if (o1.equals("") && o2.equals("")) return true;
		
		if (!o1.equals("") && !o2.equals("")) {
			String msg = mProj1.getDisplayName() + " and " + mProj2.getDisplayName() + " both have 'ordered against'" +
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
	
	/**************************************************************************/
    private void addNewProjectFromUI(String name) {
    	if (projNameMap.containsKey(name)) {
    		Utilities.showWarningMessage("Project '" + name + "' exists");
    		return;
    	}
    	String dir = DATA_PATH + Constants.seqType + "/" + name;
		Utilities.checkCreateDir(dir, true);
    }
    /***********************************************************************/
    private void dumpPairs() {
    	if (TRACE) return;
    	System.out.println("Pairs: " + pairObjMap.size());
    	for (String key : pairObjMap.keySet()) {
    		Mpair mp = pairObjMap.get(key);
    		System.out.format("key %-5s idx %d p1 %-10s %d p2 %-10s %d\n", 
    				key, mp.getPairIdx(), 
    				mp.getProj1().getDBName(), mp.getProj1Idx(), 
    				mp.getProj2().getDBName(), mp.getProj2Idx());
    	}
    	System.out.println("Projects: " + projObjMap.size());
    	for (String key : projObjMap.keySet()) {
    		Mproject mp = projObjMap.get(key);
    		System.out.format("proj %-10s %d \n", mp.getDBName(), mp.getIdx());
    	}
    }
	/**************************************************************************/
// *** Begin table customizations ******************************************
	public void autofitColumns(JTable tbl) {
        TableModel model = tbl.getModel();
        TableColumn column;
        Component comp;
        int headerWidth;
        int cellWidth;
        TableCellRenderer headerRenderer = tbl.getTableHeader().getDefaultRenderer();
       
        for (int i = 0;  i < tbl.getModel().getColumnCount();  i++) { // for each column
            column = tbl.getColumnModel().getColumn(i);

            comp = headerRenderer.getTableCellRendererComponent(
                      tbl, column.getHeaderValue(), false, false, 0, i);
           
            headerWidth = comp.getPreferredSize().width + 10;
            
            cellWidth = 0;
            for (int j = 0;  j < tbl.getModel().getRowCount();  j++) { // for each row
                comp = tbl.getDefaultRenderer(model.getColumnClass(i)).getTableCellRendererComponent(
                                     tbl, model.getValueAt(j, i),
                                     false, false, j, i);
                cellWidth = Math.max(cellWidth, comp.getPreferredSize().width);
                if(model.getColumnClass(i) == String.class) cellWidth += 5; //Strings need to be adjusted
                if (j > 100) break; // only check beginning rows, for performance reasons
            }

            column.setPreferredWidth(Math.min(Math.max(headerWidth, cellWidth), 200));
        }
    }
	private class MyTable extends JTable {
		public MyTable(Vector <Vector<String>> rowData, Vector <String>columnNames) { // CAS534 add type
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
				updateEnableButtons();
		}
	};	
	private TableColumnModelListener tableColumnListener = new TableColumnModelListener() { // called after tableRowListener
		public void columnAdded(TableColumnModelEvent e) { }
		public void columnMarginChanged(ChangeEvent e) { }
		public void columnMoved(TableColumnModelEvent e) { }
		public void columnRemoved(TableColumnModelEvent e) { }
		public void columnSelectionChanged(ListSelectionEvent e) {
			if (!e.getValueIsAdjusting())
				updateEnableButtons(); 
		}
	
	};
	private class ReadOnlyTableModel extends DefaultTableModel {
		public ReadOnlyTableModel(Vector<Vector<String>> rowData, Vector<String> columnNames)  { super(rowData, columnNames); }
		public boolean isCellEditable(int row, int column) { return false; }
	}
	
	private class MyTableCellRenderer extends DefaultTableCellRenderer {
		public Component getTableCellRendererComponent(JTable table, 
				Object value, boolean isSelected, boolean hasFocus, int row, int column)
		{
			super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			
			if (column == 0 ) {
				setBackground( UIManager.getColor("TableHeader.background") );
			}
			else if (column > row + 1) {
				// CAS42 1/7/18 can't tell this from white setBackground(new Color(245,245,245));
				setBackground(new Color(217,217,217));
			}
			else if (!isSelected)
				setBackground(null);		
			return this;
		}
	}
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
	
	// *** End table customizations ********************************************
	
	/************** Classes *************************************/
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

			JButton btnHelp = Jhtml.createHelpIconSysSm(Jhtml.SYS_GUIDE_URL, Jhtml.create);
			
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
		
		public void reset() { 
			txtName.setText("");
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
			// CAS511 check/create directories here if Add Project
			String strDataPath = DATA_PATH;
			Utilities.checkCreateDir(strDataPath, true);
			Utilities.checkCreateDir(Constants.seqDataDir, true /* bPrt */); 
			Utilities.checkCreateDir(Constants.seqRunDir,  true);
			
			return true;
		}
		private JButton btnAdd = null, btnCancel = null;
		private JTextField txtName = null;
		private JPanel pnlMainPanel = null;
	}

	private class ProjectLinkLabel extends LinkLabel {
		private Mproject project; // associated project
		
		public ProjectLinkLabel(String text, Mproject project, Color c) {
			super(text, c.darker().darker(), c);
			this.project = project;
		}
		
		public Mproject getProject() { return project; }
	}
	
	private class ProjectCheckBox extends JCheckBox {
		private Mproject project = null; // associated project
		
		public ProjectCheckBox(String text, Mproject p) {
			super(text);
			project = p;
			setFocusPainted(false);
			if (selectedProjVec.contains(p)) {
				setSelected(true);
				setBackground( cellColor); // this doesn't seem to do anything, see createPairTable
			}
			else
				setBackground( Color.white );
			addItemListener( checkboxListener );
		}
		public Mproject getProject() { return project; }
	}
}

