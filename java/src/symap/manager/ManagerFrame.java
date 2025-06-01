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

import backend.Constants;	// CAS566 replace .* imports with precise ones 
import backend.Utils;
import backend.DoLoadProj;
import backend.DoAlignSynPair;

import blockview.BlockViewFrame;
import circview.CircFrame;
import dotplot.DotPlotFrame;
import props.PropertiesReader;

import symap.Globals;
import symap.frame.ChrExpInit;
import symapQuery.QueryFrame;

import database.DBconn2;
import database.Version;

import util.Cancelled;
import util.ErrorReport;
import util.Utilities;
import util.Jhtml;
import util.Jcomp;
import util.LinkLabel;

/*****************************************************
 * The manager main frame that displays the projects on the left and the pair table on the right
 * 
 * Mprojects - have proj_idx numbered in the order they are loaded
 * The left side and pair table has them alphanumerically ordered by Display Name
 * Mpair - have proj1_idx and proj2_idx ordered according to how they were first entered into DB as a pair
 * 	hence, getMpair has to check the key=proj1_idx:proj2_idx or proj2_idx:proj1_idx
 * Note that the Display name can change along with the order; that does not change the DB pairs record.
 * 
 * CAS567 ended up moving stuff around and cleaning up more stuff while sorting out DB vs file params
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class ManagerFrame extends JFrame implements ComponentListener {
	/************************************************
	 * Command line arguments; set in symapCE.SyMAPmanager main 
	 */
	protected static boolean inReadOnlyMode = false;// set from viewSymap script with -r
	
	// On ManagerFrame panel
	public static int maxCPU=-1;			// SyMAPmanager -p; used in mummer and symapQuery
	
	private final String DB_ERROR_MSG = "A database error occurred, please see the Troubleshooting Guide at:\n" + Jhtml.TROUBLE_GUIDE_URL;	
	private final String DATA_PATH = Constants.dataDir;
	private final int MIN_CWIDTH = 825, MIN_CHEIGHT = 900;  // Circle; (same as 2D)
	private final int MIN_WIDTH = 850, MIN_HEIGHT = 600;	// Manager; 
	private final int MIN_DIVIDER_LOC = 220; 				
	private final int LEFT_PANEL = 450;      
	
	private static final String HTML = "/html/ProjMgrInstruct.html";
	
	private final Color cellColor = new Color(85,200,100,85); // pale green; for selected box 
	private final Color textColor = new Color(0,0,170,255);   // deep blue;  for selected text
	
	// If changed - don't make one a substring of another!!
	private final String TBL_DONE = "\u2713", TBL_ADONE = "A", TBL_QDONE = "?";
	private final String symapLegend = "Table Legend:\n"
			+ TBL_DONE 	+ " : synteny has been computed, ready to view.\n"
			+ TBL_ADONE + " : alignment is done, synteny needs to be computed.\n"
			+ TBL_QDONE + "  : alignment is partially done.";
	private final String selectPairText = "Select a Pair by clicking\ntheir shared table cell.";// Click a lower diagonal cell to select the project pair.
		
	protected String frameTitle="SyMAP " + Globals.VERSION; // all frames use same title
	protected DBconn2  dbc2 = null; // master, used for local loads, and used as template for processes
	
	// See initProjects
	private Vector<Mproject>           projVec    = new Vector <Mproject> (); // projects on db and disk (!viewSymap)
	private HashMap <String, Mproject> projObjMap = new HashMap <String, Mproject> (); // displayName, same set as projVec
	private HashMap <String, String>  projNameMap = new HashMap <String, String> ();   // DBname to displayName
	
	protected Vector<Mproject> selectedProjVec = new Vector<Mproject>();
	
	private HashMap<String, Mpair> pairObjMap = new HashMap <String, Mpair> (); // proj1_idx:proj2_idx is key 
	
	// CAS567 use pairObjMap only; private HashMap<Integer,Vector<Integer>> pairIdxMap = null; // project idx mapping from database
	
	private PairParams ppFrame = null; 	// only !null if opened
	private PropertiesReader dbProps;
	private String dbName = null; 		// append to LOAD.log
	
	private int totalCPUs=0;
	
	private JButton btnAllChrExp, btnSelDotplot, btnAllDotplot, btnAllPairs, btnSelClearPair,
		btnSelBlockView, btnSelCircView, btnAllQueryView, btnSelSummary,
	    btnAddProject = null, btnSelAlign, btnPairParams;
	
	private JSplitPane splitPane;
	private JComponent instructionsPanel;
	private JTable pairTable;
	
	private AddProjectPanel addProjectPanel = null;
	
	private JTextField txtCPUs =null;
	private JCheckBox checkVB=null; // CAS567 move checkVB from PairParams and checkCat to pairparams
	
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
        
		initProjects(Globals.bMySQL);
		
		instructionsPanel = Jhtml.createInstructionsPanel(this.getClass().getResourceAsStream(HTML), getBackground());
		JPanel projPanel = createProjectPanel();
		
		splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, projPanel, instructionsPanel );
		splitPane.setDividerLocation(MIN_DIVIDER_LOC);
		
		add(splitPane);
		
		setSize(MIN_WIDTH, MIN_HEIGHT);
		setLocationRelativeTo(null); 
		
		addComponentListener(this); // hack to enforce minimum frame size
	}
		
	/***********************************************************************
	 * Called at startup and refreshMenu (after any project changes)
	 */
	private void initProjects(boolean bSQL) {
		try {
			if (dbc2 == null) { // 1st init mysql
				dbc2 = makeDBconn();
				
				new Version(dbc2);
				
				if (bSQL) dbc2.checkVariables(true); 
			}
			Jhtml.setResClass(this.getClass());
			Jhtml.setHelpParentFrame(this);
			
			/*****************************************************/
			loadProjectsFromDB();
			loadPairsFromDB(); // used results of loadProjectsFromDB
			
			if (!inReadOnlyMode) { 											// read remaining projects from disk
				String strDataPath = Constants.dataDir;
				
				if (Utilities.dirExists(strDataPath)) { 					
					Utilities.checkCreateDir(Constants.logDir,true/*bPrt*/);
					
					loadProjectsFromDisk(strDataPath,  Constants.seqType); 	// must come after loadFromDB
				}
			}
			sortProjDisplay(projVec);
			
			Vector<Mproject> newSelectedProjects = new Vector<Mproject>();
			for (Mproject p1 : selectedProjVec) {
				for (Mproject p2 : projVec) {
					if (p1.equals(p2)) {
						newSelectedProjects.add(p2);
						break;
					}
				}
			}
			selectedProjVec = newSelectedProjects;
		}
		catch (Exception e) {ErrorReport.die(e, DB_ERROR_MSG);}
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
	/*******************************************************************/
	public void refreshMenu() {
		Utilities.setCursorBusy(this, true);
		
		initProjects(false); // recreates project lists on every refresh; easier then modifing projVec etc
		
		splitPane.setLeftComponent( createProjectPanel() );
		if (selectedProjVec.size() > 0)
			splitPane.setRightComponent( createSelectedPanel() );
		else
			splitPane.setRightComponent( instructionsPanel );
		
		Utilities.setCursorBusy(this, false);
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
		
		// TreeSet so alphabetical display
		TreeMap<String, TreeSet<Mproject>> cat2Proj = new TreeMap<String, TreeSet<Mproject>>();
		for (Mproject p : projVec) {
			String category = p.getdbCat();
			if (category == null || category.length() == 0) category = ProjParams.catUncat;
			if (!cat2Proj.containsKey(category)) cat2Proj.put(category, new TreeSet<Mproject>());
			cat2Proj.get(category).add(p);
		}
		
		// Figure out what to show 
		Set <String> categories = cat2Proj.keySet();
		TreeMap <String, Integer> showMap = new TreeMap <String, Integer> ();
		for (String cat : categories) {
			for (Mproject p : cat2Proj.get(cat)) {
				if (inReadOnlyMode && p.getCntSyntenyStr().equals("")) continue; // nothing to view
				
				if (showMap.containsKey(cat)) showMap.put(cat, showMap.get(cat)+1);
				else 						  showMap.put(cat, 1);
			}
		}
		for (String cat : showMap.keySet()) {
			if (showMap.get(cat)==0) continue;
			
			subPanel.add( new JLabel(cat) );
			
			for (Mproject p : cat2Proj.get(cat)) {
				if (inReadOnlyMode && p.getCntSyntenyStr().equals("")) continue; // nothing to view
					
				ProjectCheckBox cb = null;
				String name = p.getDisplayName();
				boolean bLoaded = (p.getStatus() != Mproject.STATUS_ON_DISK);
				if (bLoaded) 
					name += " (" + p.getLoadDate() + ") " + p.getCntSyntenyStr(); 
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
		
		panel.setMinimumSize( new Dimension(MIN_DIVIDER_LOC-20, MIN_HEIGHT) ); 
		
		return panel;
	}
	/***** Left panel ***********************************************/
	private JPanel createSelectedPanel() { 
		JPanel mainPanel = new JPanel();
		mainPanel.setLayout( new BoxLayout ( mainPanel, BoxLayout.Y_AXIS ) );
		mainPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 0));
		
	// Top area
		JLabel lblTitle = Jcomp.createLabel("Selected", Font.BOLD, 18);
		JButton btnHelp;
		if (!inReadOnlyMode)btnHelp = Jhtml.createHelpIconSysSm(Jhtml.SYS_HELP_URL, Jhtml.build); 
		else 				btnHelp = Jhtml.createHelpIconUserSm(Jhtml.view); 
		mainPanel.add( Jcomp.createHorizPanel( new Component[] { lblTitle,btnHelp}, LEFT_PANEL)); 
		
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
			
			// line 1
			String label1 = "Directory: " + mProj.getDBName() + "    Abbrev: " + mProj.getdbAbbrev(); 
			
			// CAS568 moved to Pair; String o = mProj.getOrderAgainst();
			//if (o != null && o.length() > 0) label1 += "    Order against: " + o;
			// CAS568 moved to Pair; if (mProj.isMasked()) label1 += "    Mask all but genes";
			
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
				if (Utilities.isEmpty(group)) group = "Chromosome"; 
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
				btnRemoveFromDisk.addMouseListener( doRemoveProjDisk );

				btnLoadOrRemove = new ProjectLinkLabel("Load project", mProj, Color.red);
				btnLoadOrRemove.addMouseListener( doLoad );

				btnReloadParams = new ProjectLinkLabel("Parameters", mProj, Color.blue);
				btnReloadParams.addMouseListener( doSetParamsNotLoaded ); 
			}
			else { 
				btnLoadOrRemove = new ProjectLinkLabel("Remove from database", mProj, Color.blue);
				btnLoadOrRemove.addMouseListener( doRemoveProjDB );

				btnReloadSeq = new ProjectLinkLabel("Reload project", mProj, Color.blue);
				btnReloadSeq.addMouseListener( doReloadSeq );
				
				btnReloadAnnot = new ProjectLinkLabel("Reload annotation", mProj, Color.blue);
				btnReloadAnnot.addMouseListener( doReloadAnnot );
				
				btnReloadParams = new ProjectLinkLabel("Parameters", mProj, Color.blue);
				btnReloadParams.addMouseListener( doReloadParams );

				btnView = new ProjectLinkLabel("View", mProj, Color.blue); 
				btnView.addMouseListener( doViewProj );
			}
			if (mProj.getStatus() == Mproject.STATUS_ON_DISK) {
				if (!inReadOnlyMode) 
					subPanel.add( Jcomp.createHorizPanel( new Component[] 
						{ lblTitle, btnRemoveFromDisk, btnLoadOrRemove, btnReloadParams }, 15 ) );				
			}
			else {
				if (!inReadOnlyMode) { 
					subPanel.add( Jcomp.createHorizPanel( new Component[] 
					{ lblTitle, btnLoadOrRemove, btnReloadSeq, btnReloadAnnot, btnReloadParams, btnView}, 10 ) );						
				}
				else { 
					subPanel.add( Jcomp.createHorizPanel( new Component[] { lblTitle, btnView}, 15));
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
		subScroller.setMaximumSize(new Dimension(MIN_WIDTH, 290)); 
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
			
			JTextArea text2 = new JTextArea(selectPairText,2,1);
			text2.setBackground( getBackground() );
			text2.setEditable(false);
			text2.setLineWrap(false);
			text2.setAlignmentX(Component.LEFT_ALIGNMENT);
			text2.setForeground(textColor); 
		
			String l = "Selected Pair";
			if (Constants.PSEUDO_ONLY) 		l = "Pseudo only"; // CAS565 make specific
			else if (Constants.CoSET_ONLY) 	l = "Collinear only";
		
			btnSelAlign = new JButton(l);
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
					removeClearPair();
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
			btnAllQueryView = new JButton("Queries"); 
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
			JLabel lbl4 = new JLabel("                     "); 
			Dimension d = lbl2.getPreferredSize();
			lbl1.setPreferredSize(d); lbl3.setPreferredSize(d); lbl4.setPreferredSize(d);
			
			totalCPUs = Runtime.getRuntime().availableProcessors(); 
			if (maxCPU<1) maxCPU = totalCPUs;
			
			txtCPUs = new JTextField(2);
			txtCPUs.setMaximumSize(txtCPUs.getPreferredSize());
			txtCPUs.setMinimumSize(txtCPUs.getPreferredSize());
			txtCPUs.setText("" + maxCPU);
			
			checkVB = Jcomp.createCheckBoxGray("Verbose","For A&S, Verbose output to symap.log & terminal."); // not saved
			checkVB.setSelected(Constants.VERBOSE);
			checkVB.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent arg0) {
					Constants.VERBOSE = checkVB.isSelected();
				}
			});
			
			mainPanel.add( new JSeparator() );
			mainPanel.add( Box.createVerticalStrut(15) );
			mainPanel.add(instructText); 
			mainPanel.add( Box.createVerticalStrut(15) );
			if (!inReadOnlyMode) {
				Component cpu = Jcomp.createHorizPanel( new Component[] 
						{ tableScroll, new JLabel("   CPUs:"), txtCPUs, new JLabel("  "), checkVB},5 );
				mainPanel.add(cpu);			
			}
			else {	
				mainPanel.add( Jcomp.createHorizPanel( new Component[] { tableScroll, new JLabel(" ")}, 5 ));			
			}
			btnPairParams = new JButton("Parameters");
			btnPairParams.addActionListener(showPairProps);
			
			if (!inReadOnlyMode) {
				mainPanel.add( Box.createRigidArea(new Dimension(0,15)) );	
				mainPanel.add( Jcomp.createHorizPanel( new Component[] { lbl1,
					btnAllPairs, btnSelAlign, btnSelClearPair,  btnPairParams}, 5) ); 
			}
			mainPanel.add( Box.createRigidArea(new Dimension(0,15))  );  
			mainPanel.add( Jcomp.createHorizPanel( new Component[] {lbl2, btnSelCircView, btnSelDotplot, btnSelBlockView,  btnSelSummary }, 5) );

			mainPanel.add( Box.createRigidArea(new Dimension(0,15))  );
			mainPanel.add( Jcomp.createHorizPanel( new Component[] {lbl3, btnAllChrExp, btnAllDotplot, btnAllQueryView }, 5) );

			mainPanel.add( Box.createRigidArea(new Dimension(0,15))  );
			mainPanel.add( Jcomp.createHorizPanel( new Component[] {lbl4 }, 5) );
		} else {
			mainPanel.add( Box.createVerticalGlue() );
			mainPanel.add( Jcomp.createTextArea("",getBackground(), false) ); // kludge to fix layout problem			
		}
				
		updateEnableButtons();
		
		return mainPanel;
	}
	
	private JComponent createPairTable() { 
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
				
				Mpair mp = getMpair(id1, id2);
				if (mp!=null && mp.pairIdx>0) {	
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
						mp = new Mpair(dbc2, -1, ordP[0], ordP[1], inReadOnlyMode);
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
	 * Enable function buttons; 
	 */
	private void updateEnableButtons() {
		if (pairTable == null) return;
		
		Mproject[] projects = getSelectedPair();  // null if nothing selected
		btnPairParams.setEnabled(projects!=null);  
		
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
			
			if (Constants.PSEUDO_ONLY) 		btnSelAlign.setText("Pseudo only"); // CAS565 make specific
			else if (Constants.CoSET_ONLY) 	btnSelAlign.setText("Collinear only");
			else if (allDone) 				btnSelAlign.setText("Selected Pair (Redo)");	
			else 							btnSelAlign.setText("Selected Pair");	
			
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
	
	/*****************************************************************
	 * Create projVec, projObjMap, projNameMap - from DB and disk
	 */
	private void loadProjectsFromDB() { 
	try {
		projVec.clear();
		projObjMap.clear();
		projNameMap.clear();
		
        ResultSet rs = dbc2.executeQuery("SELECT idx, name, annotdate FROM projects");
        while ( rs.next() ) {
        	int nIdx = rs.getInt(1);
        	String dbName = rs.getString(2);
        	String strDate = rs.getString(3);       
        	
    		Mproject mp = new Mproject(dbc2, nIdx, dbName, strDate);
    		projVec.add(mp);
        }
        rs.close();
        
        for (Mproject mp : projVec) {
        	mp.loadParamsFromDB(); 			// load from DB and then overwrites from file
        	mp.loadDataFromDB();
        	projObjMap.put(mp.strDisplayName, mp);
        	projNameMap.put(mp.strDBName, mp.strDisplayName);
        }
	}
	catch (Exception e) {ErrorReport.print(e,"Load projects"); }
	}
	private void loadProjectsFromDisk(String strDataPath, String dirName) {
		File root = new File(strDataPath + dirName); 
		if (root == null || !root.isDirectory()) return;
		
		for (File f : root.listFiles()) {
			if (f.isDirectory() && !f.getName().startsWith(".")) {
				String dbname = f.getName();
				if (projNameMap.containsKey(dbname)) continue; // loaded project
				
				Mproject mp = new Mproject(dbc2, -1, dbname, ""); 
				mp.loadParamsFromDisk(f); 
				
				if (!projObjMap.containsKey(mp.strDisplayName))  {
					 projVec.add(mp);
					 projObjMap.put(mp.strDisplayName, mp);
					 projNameMap.put(mp.strDBName, mp.strDisplayName);
				} 
				else { // e.g. dbname=Arab, and there is dbname=zarab with Display name = Arab
					String msg=dbname + ": Display name '" + mp.strDisplayName + "' exists; cannot not display";
					Utilities.showContinue("Duplicate name", msg); // CAS568 make obvious
					Globals.prt(msg);
				}
			}
		}
	}
	private void loadPairsFromDB() {
	try {
		pairObjMap.clear();
		
		HashMap <Integer, String> pairs = new HashMap <Integer, String> ();
		
		// aligned is always 1; pair is not in database until Synteny step; to see if alignment is one, check file all.done
        ResultSet rs = dbc2.executeQuery("SELECT idx, proj1_idx, proj2_idx FROM pairs where aligned=1");
              
        while (rs.next()) {
        	int pairIdx =  rs.getInt(1);
        	int proj1Idx = rs.getInt(2);
        	int proj2Idx = rs.getInt(3); 
  
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
	        	pairObjMap.put(proj1Idx + ":" + proj2Idx, mp); // do not know if proj1Idx>proj2Idx or proj1Idx<proj2Idx
        	}
        }
	}catch (Exception e) {ErrorReport.print(e,"Load projects pairs"); }
	}
	
	private String [] getCatArr() {
		TreeSet <String> catSet = new TreeSet<String> ();
		for (Mproject mp : projVec) {
			if (mp.hasCat()) {
				String cat = mp.getdbCat();
				if (!catSet.contains(cat)) catSet.add(cat);
			}
		}
		int len = (!catSet.contains(ProjParams.catUncat)) ? catSet.size()+1 : catSet.size();
		String [] catArr = new String [len];
		int i=0;
		for (String cat : catSet) catArr[i++] = cat;
		if (!catSet.contains(ProjParams.catUncat)) catArr[i] = ProjParams.catUncat; // default
		
		return catArr;
	}
	// Get Mpair from two projects
	private Mpair getMpair(int idx1, int idx2) { // projIdx
		String key = idx1 + ":" + idx2;
		if (pairObjMap.containsKey(key)) return pairObjMap.get(key);
		
		key = idx2 + ":" + idx1; // order can be either
		if (pairObjMap.containsKey(key)) return pairObjMap.get(key);

		return null;		// it is okay to be null; n1 and n2 do not have pair in database
	}
	// Pair selected in available synteny table
	private Mproject[] getSelectedPair() {
		int nRow=-1, nCol=-1;
		try {
			if (pairTable == null) return null;		
			nRow = pairTable.getSelectedRow(); // Row is >=0 Col is >=1
			nCol = pairTable.getSelectedColumn();
			
			// If none selected, automatically select one if 2 rows (does not work with 1 row)
			if (nRow < 0 || nCol <= 0) {
				int n = pairTable.getRowCount(); // if n=2, automatically make selected
				if (n!=2) return null;
				
				nCol=1; nRow=1;
				pairTable.setRowSelectionInterval(nRow, nCol);
				pairTable.setColumnSelectionInterval(nRow, nCol);
			}
				
			String strRowProjName = pairTable.getValueAt(nRow, 0).toString();
			String strColProjName = pairTable.getValueAt(nCol-1, 0).toString();

			Mproject p1 = projObjMap.get(strRowProjName);
			Mproject p2 = projObjMap.get(strColProjName);
			
			if (p1==null || p1.getIdx()==-1) {
				System.out.println(strRowProjName + ": Inconsistency with the Display Name; try save project params");
				if (p1!=null) p1.prtInfo();
				return null;
			}
			if (p2==null || p2.getIdx()==-1) {
				System.out.println(strColProjName + ": Inconsistency with the Display Name; try save project params");
				if (p2!=null) p2.prtInfo();
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
	/***************************************************************/
	// log file for load
	private boolean first=true;
	private FileWriter openLoadLog() {
		FileWriter ret = null;
		try {
			String name = dbName + "_" + Constants.loadLog; 
			 
			File pd = new File(Constants.logDir);
			if (!pd.isDirectory()) pd.mkdir();
			
			File lf = new File(pd,name);
			if (!lf.exists())
				System.out.println("Create log file: " + Constants.logDir + name);
			else if (first) {
				System.out.println("Append to log file: " + Constants.logDir + name + 
						" (Length: " + Utilities.kMText(lf.length()) + ")"); 
				first=false;
			}
			ret = new FileWriter(lf, true);	
		}
		catch (Exception e) {ErrorReport.print(e, "Creating log file");}
		return ret;
	}
	
	private ManagerFrame getInstance() { return this; }
	
	
	/***********************************************************************
	**************************************************/
	private DBconn2 makeDBconn()  { 
	// Check variables
		String paramsfile = Globals.MAIN_PARAMS;
		
		dbProps = new PropertiesReader(new File(paramsfile)); // already checked in SyMAPmanager
		
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
		System.out.println("   SyMAP database: " + db_name); // CAS568 comes after Configuration file printed in SyMAPmanager.setArch

		try { 
			if (inReadOnlyMode) { 
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
			
			if (cpus!=null && maxCPU<=0) { // -p takes precedence
				try {
					maxCPU = Integer.parseInt(cpus.trim()); 
					totalCPUs = Runtime.getRuntime().availableProcessors();
					System.out.println("   Max CPUs: " + maxCPU + " (out of " + totalCPUs + " available)");
				}
				catch (Exception e){ System.err.println("nCPUs: " + cpus + " is not an integer. Ignoring.");}
			}
			
		// Check database exists or create
			int rc=1; // 0 create, 1 exists 
	        if (inReadOnlyMode) {
	        	if (!DBconn2.existDatabase(db_server, db_name, db_clientuser, db_clientpasswd)) 
		        	ErrorReport.die("*** Database '" + db_name + "' does not exist");
	        }
	        else { 
	        	rc = DBconn2.createDatabase(db_server, db_name, db_adminuser, db_adminpasswd);
		        if (rc == -1) ErrorReport.die(DB_ERROR_MSG);
	        }
	     
	        String user = (inReadOnlyMode ? db_clientuser   : db_adminuser);
	        String pw   = (inReadOnlyMode ? db_clientpasswd : db_adminpasswd);
	        
	        DBconn2 dbc = new DBconn2("Manager", db_server, db_name, user, pw);
	        if (rc==0) dbc.checkVariables(true);
	        
	        return dbc;
		}
		catch (Exception e) {ErrorReport.die("Error getting connection"); }
		return null;
	}
	
	/***********************************************************************/
	private void showChrExp() { 
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
		try { 
			Vector <Mproject> pVec = new Vector <Mproject> ();
			for (Mproject p : selectedProjVec) if (p.isLoaded()) pVec.add( p );
			
			boolean useAlgo2=true; //  used for new "EveryPlus"
			int cntUsePseudo=0; // Instructions; CAS565
			int hasSynteny=0;
			
			Vector <Mproject> synVec = new Vector <Mproject> (); // Only projects with synteny; CAS567 add
			for (int i=0; i<pVec.size()-1; i++) {
				for (int j=i+1; j<pVec.size(); j++) {
					Mpair mp = getMpair(pVec.get(i).getIdx(), pVec.get(j).getIdx());
					if (mp.pairIdx<0) continue;
					
					if (!mp.isAlgo2(Mpair.DB)) 	  useAlgo2=false;
					if (mp.isNumPseudo(Mpair.DB)) cntUsePseudo++;
					hasSynteny++;
					
					if (!synVec.contains(pVec.get(i))) synVec.add(pVec.get(i));
					if (!synVec.contains(pVec.get(j))) synVec.add(pVec.get(j));
				}
			}
			QueryFrame qFrame = new QueryFrame(frameTitle, dbc2, synVec, useAlgo2, cntUsePseudo, hasSynteny);
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
		
		Mproject[] p = getSelectedPair();
		if (p==null) return;
		
		int projXIdx = p[0].getID();
		int projYIdx = projXIdx; // default to self-alignment
		if (p.length > 1) projYIdx = p[1].getID();
		
		DotPlotFrame frame = new DotPlotFrame(frameTitle+ " - Dot Plot", dbc2, projXIdx, projYIdx);
		frame.setSize( new Dimension(MIN_WIDTH, MIN_HEIGHT) );
		frame.setVisible(true);
		
		Utilities.setCursorBusy(this, false);
	}
	/*****************************************************************/
	private void showBlockView()  { 
		Utilities.setCursorBusy(this, true);
		
		Mproject[] p = getSelectedPair();
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
		
		Mproject[] p = getSelectedPair();
		if (p==null) return;
		
		int projXIdx = p[0].getIdx();
		int projYIdx = (p.length>1) ? p[1].getIdx() : projXIdx; 
		
		Mpair mp = getMpair(projXIdx, projYIdx);
		if (mp==null) return;
				
		new SumFrame(frameTitle + " - Summary", dbc2, mp, inReadOnlyMode); // dialog made visible in SumFrame; 
		
		Utilities.setCursorBusy(this, false);		
	}
	/*****************************************************************/
	private void showCircleView() { 
		Utilities.setCursorBusy(this, true);
		
		Mproject[] p = getSelectedPair();
		if (p==null) return;
		
		int projXIdx = p[0].getID();
		int projYIdx = (p.length > 1) ? p[1].getID() : projXIdx; // 2-align : self-align
		boolean hasSelf = (p[0].hasSelf() || p[1].hasSelf()); // CAS552 add for Self-Align
		
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
			Mproject[] sel = getSelectedPair();
		
			Mpair p = getMpair(sel[0].getIdx(), sel[1].getIdx());
			if (p!=null) {
				ppFrame = new PairParams(p);
				ppFrame.setVisible(true);
			}
			else Globals.eprt("SyMAP error: cannot get pair " + sel[0].getIdx() + " " + sel[1].getIdx() );
		}
	};
	/***************************************************************
	 * Project links
	 */
	private MouseListener doRemoveProjDB = new MouseAdapter() {
		public void mouseClicked(MouseEvent e) {
			Mproject mp = ((ProjectLinkLabel)e.getSource()).getProject();
			new RemoveProj(openLoadLog()).removeProjectDB(mp);
			refreshMenu();
		}
	};
	private MouseListener doRemoveProjDisk = new MouseAdapter() {
		public void mouseClicked(MouseEvent e) {
			Mproject mp = ((ProjectLinkLabel)e.getSource()).getProject();
			new RemoveProj(openLoadLog()).removeProjectDisk(mp);
			refreshMenu();
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
	
	private MouseListener doReloadParams = new MouseAdapter() {
		public void mouseClicked(MouseEvent e) {
			Mproject mProj = ((ProjectLinkLabel)e.getSource()).getProject();
			
			ProjParams propFrame = new ProjParams(getInstance(), mProj, projVec, getCatArr(),
					true, mProj.hasExistingAlignments(true)); // only exist true if /align
			propFrame.setVisible(true);
			
			if (propFrame.wasSave()) refreshMenu();		
		}
	};
	private MouseListener doSetParamsNotLoaded = new MouseAdapter() {
		public void mouseClicked(MouseEvent e) {
			Mproject mProj = ((ProjectLinkLabel)e.getSource()).getProject();
			
			ProjParams propFrame = new ProjParams(getInstance(), mProj, projVec, getCatArr(),
					false, mProj.hasExistingAlignments(true)); // only exist true if /align
			propFrame.setVisible(true);
			
			if (propFrame.wasSave()) refreshMenu(); 
		}		
	};
	
	private MouseListener doViewProj = new MouseAdapter() { 
		public void mouseClicked(MouseEvent e) {
			Mproject theProject = ((ProjectLinkLabel)e.getSource()).getProject();
			String info = theProject.loadProjectForView();
			
			Utilities.displayInfoMonoSpace(getInstance(), "View " + theProject.getDisplayName(), info, false);	
		}
	};
	private void removeClearPair() {
		Mproject[] mprojs = getSelectedPair();
		if (mprojs==null) return;
		
		Mpair mp = getMpair(mprojs[0].getIdx(),mprojs[1].getIdx());
		RemoveProj rpObj = new RemoveProj(openLoadLog());
		rpObj.removeClearPair(mprojs[0], mprojs[1], mp);
		
		refreshMenu();
	}
	/*****************************************************************/
	/***************************************************************
	 * XXX LoadProj: 
	 */
	private void loadAllProjects() {
		try {
			DoLoadProj lpObj = new DoLoadProj(this, dbc2, openLoadLog());
			lpObj.loadAllProjects(selectedProjVec); // All
			
			new Version(dbc2).updateReplaceProp();
			refreshMenu();
		}
		catch (Exception e) {ErrorReport.print(e, "load all project");}
	}
	private void loadProject(Mproject mProj) {
		try {
			DoLoadProj lpObj = new DoLoadProj(this, dbc2, openLoadLog());
			lpObj.loadProject(mProj);				// just this one
			
			new Version(dbc2).updateReplaceProp();
			refreshMenu();
		}
		catch (Exception e) {ErrorReport.print(e, "load project");}
	}
	private void reloadProject(Mproject mProj) {
	try {
		RemoveProj rpObj = new RemoveProj(openLoadLog());
		if (!rpObj.reloadProject(mProj)) return;				// CAS568 moved removal to RemoveProj
		
		DoLoadProj lpObj= new DoLoadProj(this, dbc2, openLoadLog());
		lpObj.loadProject(mProj); 
		
		new Version(dbc2).updateReplaceProp();
		refreshMenu();
	}
	catch (Exception e) {ErrorReport.print(e, "reload project");}
	}
	// Reload
	private void reloadAnno(Mproject mProj) {
	try {
		String msg = "Reload annotation " + mProj.getDisplayName();
		
		if (!Utilities.showConfirm2("Reload annotation", msg +							
			"\n\nYou will need to re-run the synteny computations for this project," +  
			"\nany existing alignment files will be used.")) return; 					
		
		System.out.println("Removing " +  mProj.getDisplayName() + " annotation from database...."); 
		mProj.removeAnnoFromDB();
		
		DoLoadProj lpObj= new DoLoadProj(this, dbc2, openLoadLog());
		lpObj.reloadAnno(mProj);
		
		new Version(dbc2).updateReplaceProp();
		refreshMenu();
	}
	catch (Exception e) {ErrorReport.print(e, "reload project");}
	}
	/***************************************************
	 * Align all pairs;
	 */
	private void alignAllPairs() { 
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
					Mpair mp = getMpair(p1.getIdx(), p2.getIdx());
					
					Mproject[] ordered = orderProjName(p1,p2);
					p1 = ordered[0]; p2 = ordered[1];
					if (p1.getIdx() != p2.getIdx()) {
						if (alignCheckOrderAgainst(mp)) { 
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
		if (!alignCheckProjDir()) return;
		
		// Confirm
		String msg = "Align&Synteny for " + todoList.size() + " pairs";
		msg += "\nCPUs " + maxCPUs + ";  ";						// CAS567 add CPU
		if (Constants.VERBOSE) msg += "Verbose on;  "; else msg += "Verbose off;  ";
		
		if (!Utilities.showConfirm2("All Pairs", msg)) return; 
		
	/*-------- Alignment, Clustering, Synteny -----------------*/
		System.out.println("\n>>> Start all pairs: processing " + todoList.size() + " project pairs");
		
		for (Mpair mp : todoList) { // Open/close new dbc2 for each thread of align; CAS568 mv renew to DoAlign
			new DoAlignSynPair().run(this, dbc2, mp, true,  maxCPUs, false); // assume align has not been done
		}
		new Version(dbc2).updateReplaceProp();
		System.out.println("All Pairs complete. ");
	}
	/*****************************************************
	 * Align selected pair
	 */
	private void alignSelectedPair( ) {
		Mproject[] mProjs = getSelectedPair();
		if (mProjs==null) return;
		
		int nCPU = getCPUs();
		if (nCPU == -1) return;           // it says to enter valid number in getCPU; CAS567 after 1st submission
		if (!alignCheckProjDir()) return; // error written in calling routine
	
		Mpair mp = getMpair(mProjs[0].getIdx(), mProjs[1].getIdx());
		if (mp==null) return;
		
		// Special cases
		if (backend.Constants.CoSET_ONLY || backend.Constants.PSEUDO_ONLY)  { //*** CAS565 remove bAlignDone; tries to do ONLY anyway
			if (backend.Constants.PSEUDO_ONLY && !mp.isNumPseudo(Mpair.FILE)) {
				Utilities.showInfoMsg("Pseudo", "Please  open Parameters and check 'Number Pseudo',\nwhich is under 'Cluster Hits'");
				return;
			}
			String msg = (backend.Constants.CoSET_ONLY) ? "Collinear only" : "Pseudo only";
			
			if (!Utilities.showConfirm2("Selected Pair", msg)) return;
			
			new DoAlignSynPair().run(getInstance(), dbc2, mp, false, nCPU, true); // align has been done
			new Version(dbc2).updateReplaceProp();
			return;
		}
		
		if (!alignCheckOrderAgainst(mp)) return; 
		
		Mproject[] ordP  = orderProjName(mProjs[0],mProjs[1]); // order indices according to DB pairs table
		String resultDir = Constants.getNameAlignDir(ordP[0].getDBName(), ordP[1].getDBName());
		
		// Compose confirm popup
		boolean bAlignDone = Utils.checkDoneFile(resultDir);
		String msg =  bAlignDone ? "Synteny for " : "Align&Synteny for "; 
		if (mProjs[0].getDisplayName().equals(mProjs[1].getDisplayName())) { 
			msg += mProjs[0].getDisplayName() + " to itself";
			
			if (mp.isAlgo2(Mpair.FILE)) {
				util.Utilities.showInfoMessage("Cluster Algo", msg +"\nYou must select Algorithm 1 from Parameters");
				return;
			}
		}
		else msg += mProjs[0].getDisplayName() + " and " + mProjs[1].getDisplayName();
		
		String chgMsg = bAlignDone ? mp.getChangedSynteny(Mpair.FILE) : mp.getChangedParams(Mpair.FILE);  
		msg += "\n" + chgMsg;
		msg += "CPUs " + nCPU + ";  ";						// CAS567 add CPU; was maxCPU - fixed after 1st submission
		if (Constants.VERBOSE) msg += "Verbose on"; else msg += "Verbose off";
		
		if (!Utilities.showConfirm2("Selected Pair", msg)) return;
		
	/*--- Do Alignment, Clustering, Synteny (alignments will not happen if exists) ----*/
		// mp.renewIdx(); 						// CAS568 mv to DoAlignSynPair
		
		new DoAlignSynPair().run(getInstance(), dbc2, mp, false, nCPU, bAlignDone);
		
		new Version(dbc2).updateReplaceProp();
	}

	private int getCPUs() {
		try { maxCPU = Integer.parseInt(txtCPUs.getText()); }
		catch (Exception e) {
			Utilities.showErrorMessage("Please enter a valid value for number of CPUs to use.");
			return -1;
		}
		if (maxCPU <= 0) maxCPU = 1;
		return maxCPU;
	}
	// Create directories if alignment is initiated; 
	// An alignment may be in the database, yet no /data directory. The files will be rewritten, so the directories are needed.
	private boolean alignCheckProjDir() { 
		try {
			Utilities.checkCreateDir(DATA_PATH, true);
			Utilities.checkCreateDir(Constants.seqRunDir,  true);
			Utilities.checkCreateDir(Constants.seqDataDir, true); 
			
			return true;
		}
		catch (Exception e){return false;}
	}
	 // Checks before starting order against project; 
	private boolean alignCheckOrderAgainst(Mpair mp) {
		if (!mp.isOrder1(Mpair.FILE) && !mp.isOrder2(Mpair.FILE)) return true;
		
		try {
			String ordProjName = Constants.getOrderDir(mp); 
			if (ordProjName==null) return true;
			
			String ordDirName =  Constants.seqDataDir + ordProjName;
			File   ordDir = new File(ordDirName);
	
			if (ordDir.exists()) {
				String msg = "Directory exists: " + ordDirName  + "\nIt will be over-written.\n"; 
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
		public MyTable(Vector <Vector<String>> rowData, Vector <String>columnNames) { 
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
			btnAdd.setEnabled(true); 
			
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
