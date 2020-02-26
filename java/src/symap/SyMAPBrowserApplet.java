package symap;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JApplet;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;

import blockview.BlockViewFrame;
import circview.CircFrame;
import dotplot.ControlPanel;
import dotplot.Data;
import dotplot.Plot;
import symap.frame.HelpBar;
import symap.pool.DatabaseUser;
import symap.projectmanager.common.SumFrame;
import symap.projectmanager.common.SyMAPFrameCommon;
import symapCE.SyMAPExp;
import symapQuery.SyMAPQueryFrame;
import symap.SyMAP;
import symap.SyMAPConstants;
import util.DatabaseReader;
import util.LinkLabel;
import util.Utilities;

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class SyMAPBrowserApplet extends JApplet implements MouseMotionListener
{
	// Parameters in the html file that get passed in
	private static final String DATABASE_URL  = "database";
	private static final String USERNAME      = "username";
	private static final String PASSWORD      = "password";
	private static final String PROJECTS      = "projects";
	private static final String TITLEPROP     = "title";
	private static final String DESCRIPTION	= "description"; // CAS42 12/26/17
	
	DatabaseReader db;
	
	Vector<String> projList = new Vector<String>();
	TreeMap<String,JCheckBox> name2chk = new TreeMap<String,JCheckBox>();
	TreeMap<String,Integer>   name2idx = new TreeMap<String,Integer>();
	TreeSet<String> pairs = new TreeSet<String>(); // CAS42 12/26/17 keep processed pairs
	TreeSet<String> fpcs = new TreeSet<String>();
	
	JButton expBtn, circBtn, dotBtn, sumBtn, blkBtn, qryBtn;
	JPanel helpPanel;
	JTextArea txtHelp;
	HashMap<JButton,String> btnText; // because tooltips come up outside the browser window
	
	static String TITLE="SyMAP Project Browser";
	static String PROJS="<html><b><font size=3>&nbsp;Projects</font></b></html>";
	static String SEQS="<html><b><font size=3>&nbsp;Sequence Projects</font></b></html>";
	static String FPCS="<html><b><font size=3>&nbsp;FPC Projects</font></b></html>";
	
	static String HELP_START="Select one or more projects, " +
			"which will enable the valid functions for the selection.";
	static String HELP_EXP="Show the Chromosome Explorer from the selected projects. This also allows you " +
			 " view dotplots and circle plots for subsets of chromosomes, as well as close-up " +
			" alignments.";
	static String HELP_CIRC="Circle plot including all chromosomes of all selected projects.";
	static String HELP_DOT="Dot plot including all chromosomes of all selected projects. " +
			" You can zoom in to selected regions to see a close-up alignment.";
	static String HELP_BLK="Show a map of the synteny blocks for two species. Click to zoom in to " +
			" a close-up view of a particular block.";
	static String HELP_SUM="Show a summary of the alignment between two selected projects. " +
			"This takes a few seconds to compute, so please be patient.";
	static String HELP_QRY="Show orthologous gene groups, determined by synteny, between two or more species." +
			" You can also perform general queries for annotation keywords, find " +
			" regions of synteny which lack current annotation, or find orphan genes.";
	
	static String CITATION="SyMAP Citation:  C. Soderlund, M. Bomhoff, and W. Nelson (2011) " +
			 "SyMAP v3.4: a turnkey synteny system with application to plant genomes " +
			 " Nucleic Acids Research 39(10):e68 ";
	static String delim = "-";
	
	boolean ready=false;
	public void init() {
		super.init();

		try 
		{
			SyMAP.printVersion();
			if (!SyMAP.checkJavaSupported(this)) return;

		// Read parameters and set globals and strings
			if (!getProjectsFromDB()) return;
			
			String title = getParameter(TITLEPROP);
			if (title == null) title = TITLE;
			
			String desc = getParameter(DESCRIPTION); // CAS42 12/26/17
			if (desc==null) desc="";
			
			String strPairs = "Computed synteny: " + pairs.size();
			
		// Initial panels, etc
			btnText = new HashMap<JButton,String>();
			
			JPanel toptop = new JPanel();
			toptop.setLayout(new BoxLayout(toptop,BoxLayout.Y_AXIS));
			toptop.setBackground(Color.white);
			toptop.setBorder(null);
			
			JPanel titlePanel = new JPanel();
			titlePanel.setLayout(new BoxLayout(titlePanel,BoxLayout.X_AXIS));
			titlePanel.setBackground(Color.white);
			titlePanel.setBorder(null);
			JLabel lblTitle = new JLabel(title);
			Font curFont = lblTitle.getFont();
			lblTitle.setFont(curFont.deriveFont( Font.BOLD, curFont.getSize() + 4 ) );
			lblTitle.setAlignmentX(CENTER_ALIGNMENT);
			lblTitle.setMaximumSize(lblTitle.getPreferredSize());
			titlePanel.add(Box.createHorizontalGlue());
			titlePanel.add(lblTitle);
			titlePanel.add(Box.createHorizontalGlue());
			
			JPanel top = new JPanel();
			top.setLayout(new BoxLayout(top,BoxLayout.X_AXIS));
			top.setBackground(Color.white);
			top.setBorder(null);
						
			JPanel left = new JPanel();
			left.setLayout(new BoxLayout(left,BoxLayout.Y_AXIS));
			left.setBackground(Color.white);
			left.setAlignmentX(LEFT_ALIGNMENT);
			left.setAlignmentY(TOP_ALIGNMENT);
			left.setBorder(null);
			
			JPanel right = new JPanel();
			right.setLayout(new BoxLayout(right,BoxLayout.Y_AXIS));
			right.setBackground(Color.white);
			right.setAlignmentY(TOP_ALIGNMENT);
			right.setAlignmentX(LEFT_ALIGNMENT);
			right.setBorder(null);
			right.add(Box.createVerticalStrut(20));

			txtHelp = new JTextArea(HELP_START, 8,20);
			txtHelp.setLineWrap( true );
			txtHelp.setWrapStyleWord( true );
			txtHelp.setEditable(false);
			Border border = BorderFactory.createEtchedBorder(EtchedBorder.LOWERED);
			txtHelp.setBorder(BorderFactory.createCompoundBorder(border, 
		            BorderFactory.createEmptyBorder(10, 10, 10, 10)));

			helpPanel = new JPanel();
			helpPanel.setLayout(new BoxLayout(helpPanel,BoxLayout.Y_AXIS));
			helpPanel.setBackground(Color.white);
			helpPanel.setBorder(null);
			helpPanel.add(Box.createVerticalStrut(20));
			helpPanel.add(txtHelp);
			helpPanel.setAlignmentX(LEFT_ALIGNMENT);
			helpPanel.setAlignmentY(TOP_ALIGNMENT);
			helpPanel.setMaximumSize(helpPanel.getPreferredSize());
			helpPanel.add(Box.createVerticalStrut(30));

			LinkLabel onlineLink = new LinkLabel("Click for online help");
	        onlineLink.setFont(new Font(onlineLink.getFont().getName(), Font.PLAIN, onlineLink.getFont().getSize()));
	        onlineLink.addMouseListener(new MouseAdapter() 
	        {
				public void mouseClicked(MouseEvent e) 
				{
					try
					{
						Utilities.setCursorBusy(SyMAPBrowserApplet.this, true);
						SyMAPBrowserApplet.this.getAppletContext().showDocument(new URL(SyMAP.USER_GUIDE_URL), "_blank" );
						Utilities.setCursorBusy(SyMAPBrowserApplet.this, false);
					}
					catch(Exception ex)
					{
						ex.printStackTrace();
					}
				}
			});	
	        onlineLink.setMaximumSize(onlineLink.getPreferredSize());
	        onlineLink.setAlignmentX(CENTER_ALIGNMENT);
	        
	        JPanel linkPanel = new JPanel();
	        linkPanel.setLayout(new BoxLayout(linkPanel,BoxLayout.X_AXIS));
	        linkPanel.add(Box.createHorizontalGlue());
	        linkPanel.add(onlineLink);
	        linkPanel.add(Box.createHorizontalGlue());
	        
	        helpPanel.add(onlineLink);
	        helpPanel.add(Box.createVerticalGlue());

	        String text = desc + "\n\n" + strPairs + "\n\n" + CITATION; // CAS42 12/26/17
			JTextArea txtCite = new JTextArea(text, 10, 60);	// 2,60		
			txtCite.setLineWrap(true);
			txtCite.setWrapStyleWord(true);
			txtCite.setMaximumSize(txtCite.getPreferredSize());
			txtCite.setAlignmentY(TOP_ALIGNMENT);
			txtCite.setAlignmentX(CENTER_ALIGNMENT);
			txtCite.setEditable(false);
			txtCite.setFont(txtCite.getFont().deriveFont(12.0F));
			
			JPanel citePanel = new JPanel();
			citePanel.setLayout(new BoxLayout(citePanel,BoxLayout.X_AXIS));
			citePanel.setBackground(Color.white);
			citePanel.setBorder(null);
			citePanel.add(Box.createHorizontalGlue());
			citePanel.add(txtCite);
			citePanel.add(Box.createHorizontalGlue());
			
	// Create project check boxes
			JPanel projPanel = new JPanel();
			projPanel.setLayout(new BoxLayout(projPanel,BoxLayout.Y_AXIS));
			projPanel.setBackground(Color.white);
			projPanel.setBorder(null);
			
			if (fpcs.size() > 0)
			{
				projPanel.add(new JLabel(SEQS));
			}
			else
			{
				projPanel.add(new JLabel(PROJS));				
			}
			projPanel.add(Box.createVerticalStrut(10));
			
			// add Sequence projects
			for (String name : projList) // use projList to keep the order specified in tag (if any)
			{
				if (!fpcs.contains(name))
				{
					projPanel.add(name2chk.get(name));
					projPanel.add(Box.createVerticalStrut(5));
				}
			}
			// add FPC projects
			if (fpcs.size() > 0) 
			{
				projPanel.add(Box.createVerticalStrut(5));
				projPanel.add(new JLabel(FPCS));
				projPanel.add(Box.createVerticalStrut(10));
			
				for (String name : projList)
				{
					if (fpcs.contains(name))
					{
						projPanel.add(name2chk.get(name));
						projPanel.add(Box.createVerticalStrut(5));
					}
				}
			}
			projPanel.add(Box.createVerticalGlue());
			JScrollPane listS = new JScrollPane(projPanel);
			listS.setBorder(null);
			left.add(listS);
			left.add(Box.createVerticalGlue());
		
	// Create function buttons
			expBtn = new JButton("Chromosome Explorer");
			expBtn.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent arg0) {
					new Thread(new Runnable() {
						public void run() {
							expBtn.setBackground(Color.BLUE); expBtn.setEnabled(false);
							txtHelp.setText("Starting Chromosome Explorer....");
							runExplorer();
							txtHelp.setText("");
							expBtn.setBackground(Color.white);  expBtn.setEnabled(true);
						}
					}).start();
				}
			});
			expBtn.addMouseMotionListener(this);
			btnText.put(expBtn,HELP_EXP);
			right.add(expBtn);
			right.add(Box.createVerticalStrut(7));
			
			circBtn = new JButton("Circle View");
			circBtn.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent arg0) {
					new Thread(new Runnable() {
						public void run() {
							circBtn.setBackground(Color.blue); circBtn.setEnabled(false);
							txtHelp.setText("Starting Circle View....");
							runCircle();
							txtHelp.setText("");
							circBtn.setBackground(Color.white);  circBtn.setEnabled(true);
						}
					}).start();
				}
			});
			circBtn.addMouseMotionListener(this);
			btnText.put(circBtn,HELP_CIRC);
			right.add(circBtn);
			right.add(Box.createVerticalStrut(7));

			dotBtn = new JButton("Dot Plot View");
			dotBtn.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent arg0) {
					new Thread(new Runnable() {
						public void run() {
							dotBtn.setBackground(Color.blue); dotBtn.setEnabled(false);
							txtHelp.setText("Starting Dot Plot View....");
							runDots();
							txtHelp.setText("");
							dotBtn.setBackground(Color.white); dotBtn.setEnabled(true);
						}
					}).start();
				}
			});
			dotBtn.addMouseMotionListener(this);
			btnText.put(dotBtn,HELP_DOT);
			right.add(dotBtn);
			right.add(Box.createVerticalStrut(7));

			blkBtn = new JButton("Block View");
			blkBtn.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent arg0) {
					blkBtn.setBackground(Color.blue); blkBtn.setEnabled(false);
					txtHelp.setText("Starting Block View...");
					runBlocks();
					txtHelp.setText("");
					blkBtn.setBackground(Color.white); blkBtn.setEnabled(true);
				}
			});
			blkBtn.addMouseMotionListener(this);
			btnText.put(blkBtn,HELP_BLK);
			right.add(blkBtn);
			right.add(Box.createVerticalStrut(7));

			qryBtn = new JButton("Ortholog Queries");
			qryBtn.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent arg0) {
					new Thread(new Runnable() {
						public void run() {
							qryBtn.setBackground(Color.blue); qryBtn.setEnabled(false);
							txtHelp.setText("Starting Queries...");
							runQuery();
							txtHelp.setText("");
							qryBtn.setBackground(Color.white); qryBtn.setEnabled(true);
						}
					}).start();
				}
			});
			qryBtn.addMouseMotionListener(this);
			btnText.put(qryBtn,HELP_QRY);
			right.add(qryBtn);
			right.add(Box.createVerticalStrut(7));

			sumBtn = new JButton("Summary");
			sumBtn.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent arg0) {
					new Thread(new Runnable() {
						public void run() {
							sumBtn.setBackground(Color.blue); sumBtn.setEnabled(false);
							txtHelp.setText("Computing summary, please wait...");
							runSum();
							txtHelp.setText("");
							sumBtn.setBackground(Color.white); sumBtn.setEnabled(true);
						}
					}).start();
				}
			});
			right.add(sumBtn);
			btnText.put(sumBtn,HELP_SUM);
			sumBtn.addMouseMotionListener(this);
			right.add(Box.createVerticalStrut(7));
			
			enableBtns();

			right.add(Box.createVerticalGlue());
		
	 // Put it together
			top.add(Box.createHorizontalStrut(10));
			top.add(left);
			top.add(Box.createHorizontalStrut(20));
			top.add(right);
			top.add(Box.createHorizontalStrut(20));
			top.add(helpPanel);
			top.add(Box.createHorizontalGlue());
			top.setAlignmentX(LEFT_ALIGNMENT);
			top.setAlignmentY(TOP_ALIGNMENT);
			
			toptop.add(titlePanel);
			toptop.add(Box.createVerticalStrut(20));
				
			toptop.add(top);
			toptop.add(Box.createVerticalStrut(5));
			
			citePanel.setAlignmentX(LEFT_ALIGNMENT);
			citePanel.setAlignmentY(TOP_ALIGNMENT);
			toptop.add(citePanel);
			toptop.add(Box.createVerticalGlue());
			
			toptop.addMouseMotionListener(this);

			Container cp = getContentPane();
			cp.setBackground(Color.white);
			cp.add(toptop, BorderLayout.PAGE_START);
			
			ready = true;
		
			System.out.println("Initialization done, applet is ready.");
		}
		catch(Exception e) {e.printStackTrace();}
	}
	
	// Populates global structures: projList, name2idx, name2chk, fpcs and idx2idx
	private boolean getProjectsFromDB() {
		try {
			db = getDatabaseReader();
			if (db == null)
			{
				System.err.println("Unable to connect to database using parameters given");
				return false;
			}
			
			// First get the projects they want to show, and retain the order they gave
			String projects = getParameter(PROJECTS);
			
			if (projects != null)
			{
				System.out.println("projects:" + projects);
				for (String proj : projects.split(","))
				{
					projList.add(proj.trim().toLowerCase());
				}
			}
			
			Statement s = db.getConnection().createStatement();
			ResultSet rs = s.executeQuery("select projects.name, type, value, idx from projects, proj_props" +
					" where proj_props.name='display_name' and proj_props.proj_idx=idx ");
			while (rs.next())
			{
				String name = rs.getString(1).toLowerCase();
				String type = rs.getString(2);
				String dispname = rs.getString(3);
				int idx = rs.getInt(4);
				
				if (projList.size() > 0)
				{
					if (!projList.contains(name)) continue;
				}
					
				JCheckBox chk = new JCheckBox(dispname);
				chk.setBackground(Color.white);
				chk.setSelected(false);
				chk.addActionListener( new ActionListener() {
					public void actionPerformed(ActionEvent arg0) {
						enableBtns();
					}
				});
				name2chk.put(name, chk);
				name2idx.put(name, idx);
				
				if (type.equals("fpc"))
				{
					fpcs.add(name);
				}
			}
			if (projList.size() == 0)
			{
				projList.addAll(name2chk.keySet());
				System.out.println("Number of projects: " + projList.size());
			}
				
			// determine computed pairs
			rs = s.executeQuery("select proj1_idx, proj2_idx from pairs ");
			while (rs.next())
			{
				int i1 = rs.getInt(1);
				int i2 = rs.getInt(2);
				String name1=null, name2=null;
				
				for (String name : name2idx.keySet()) {
					int idx = name2idx.get(name);
					if (idx==i1) name1=name;
					if (idx==i2) name2=name;
						
					if (name1!=null && name2!=null) {
						pairs.add(name1 + delim + name2);
						break;
					}
				}
			}
			return true;
		}
		catch(Exception e) {e.printStackTrace(); return false;}
	}
	public boolean isReady() {
		return ready;
	}

	/**
	 * Method <code>start</code> calls <code>showFrame()</code> if the
	 * applet is to be shown (i.e. show parameter was not set to false).
	 *
	 * @see #showFrame()
	 */
	public void start() {
		//System.out.println("Entering SyMAPApplet.start()");
	}

	public void stop() {
		 //System.out.println("Entering SyMAPApplet.stop()");
	}

	public void destroy() {
		db.close();
		//System.out.println("Entering SyMAPApplet.destroy()");
	}
	public void runSum()
	{
		Vector<Integer> idxs = new Vector<Integer>();
		for (String proj : name2chk.keySet())
		{
			if (name2chk.get(proj).isSelected())
			{
				idxs.add(name2idx.get(proj));
			}
		}
		if (idxs.size() > 2)
		{
			System.err.println("More than two projects selected!");
			return;
		}
		if (idxs.size() == 1)
		{
			idxs.add(idxs.get(0));
		}
		try
		{
			SumFrame frame = new SumFrame(db,idxs.get(0),idxs.get(1));
			frame.setPreferredSize(new Dimension(1100,450));
			frame.setMinimumSize(new Dimension(1100,450));
			frame.toFront();
			frame.setVisible(true);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return;
		}		
	}
	public void runQuery()
	{
        SyMAPQueryFrame symapQ = new SyMAPQueryFrame(this,db, false);
        
		Utilities.setResClass(this.getClass());
		Utilities.setHelpParentFrame(symapQ);

		for (String proj : name2chk.keySet())
		{
			if (fpcs.contains(proj)) continue;
			if (name2chk.get(proj).isSelected())
			{
				String type = (fpcs.contains(proj) ? "fpc" : "pseudo");
				if (!symapQ.hasProject(proj, type)) 
				{
					if ( !symapQ.addProject(proj, type) ) 
					{
						System.err.println("Failed to add project (" + proj + ") to query page");
						return;
					}
				}
			}
		}
		
		symapQ.build();
		symapQ.toFront();
		symapQ.setVisible(true);
	}
	public void runDots()
	{
		HelpBar helpBar = new HelpBar(-1, 17, true, false, false); 
		
		// Start data download
		Data data = new Data(this);
		Plot plot = new Plot(data, helpBar);
		ControlPanel controls = new ControlPanel(this, data, plot, helpBar);

		Vector<String> projList = new Vector<String>();
		for (String proj : name2chk.keySet())
		{
			if (name2chk.get(proj).isSelected())
			{
				projList.add(proj);
			}
		}

		data.initialize( projList.toArray(new String[0]) );
		controls.setProjects( data.getProjects() ); 

		JFrame topFrame = new JFrame();
		topFrame.setPreferredSize(new Dimension(900,800));
		topFrame.setMinimumSize(new Dimension(900,800));
		topFrame.add(controls, BorderLayout.NORTH); // CAS42 1/10/18
		topFrame.add(plot.getScrollPane(), BorderLayout.CENTER);
		topFrame.add(helpBar, 			 BorderLayout.SOUTH); 
		topFrame.toFront();
		topFrame.setVisible(true);
	}
	public void runCircle()
	{
		HelpBar helpBar = new HelpBar(-1, 17, true, false, false); 
		TreeSet<Integer> pidxs = new TreeSet<Integer>();
		for (String proj : name2chk.keySet())
		{
			if (name2chk.get(proj).isSelected())
			{
				pidxs.add(name2idx.get(proj));
			}
		}
		int[] pidxList = new int[pidxs.size()];
		int i = 0;
		for (int idx : pidxs)
		{
			pidxList[i] = idx;
			i++;
		}
		CircFrame frame = new CircFrame(null, db, pidxList, null,helpBar); 
		frame.setVisible(true);
		frame.toFront();
	}
	public void runExplorer()
	{
		int numProjects = 0;
		for (String proj : name2chk.keySet())
		{
			if (!name2chk.get(proj).isSelected()) continue;
			numProjects++;
		}
		if (numProjects > SyMAP.MAX_PROJECTS)
		{
			System.err.println("Too many projects selected (max=" + SyMAP.MAX_PROJECTS + ")");
			Utilities.showErrorMessage("Too many projects selected (max=" + SyMAP.MAX_PROJECTS + ")");
			return;
		}
		try
		{
			SyMAPExp symapExp = new SyMAPExp(this,db);
			for (String proj : name2chk.keySet())
			{
				if (!name2chk.get(proj).isSelected()) continue;
				String type = (fpcs.contains(proj) ? "fpc" : "pseudo");
				if (!symapExp.hasProject(proj, type)) 
				{
					if ( !symapExp.addProject(proj, type) ) 
					{
						return;
					}
				}
			}
			SyMAPFrameCommon frame = symapExp.getFrame();
			symapExp.build();
			frame.build();
			frame.setVisible(true);
			frame.toFront();
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	void runBlocks()
	{
		Vector<Integer> idxs = new Vector<Integer>();
		for (String proj : name2chk.keySet())
		{
			if (name2chk.get(proj).isSelected())
			{
				idxs.add(name2idx.get(proj));
			}
		}
		if (idxs.size() > 2)
		{
			System.err.println("More than two projects selected!");
			return;
		}
		if (idxs.size() == 1)
		{
			idxs.add(idxs.get(0));
		}
		try
		{
			BlockViewFrame bvframe = new BlockViewFrame(db,idxs.get(0),idxs.get(1));
			bvframe.toFront();
			bvframe.setVisible(true);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return;
		}
	}
	void enableBtns()
	{
		expBtn.setEnabled(false);
		circBtn.setEnabled(false);
		dotBtn.setEnabled(false);
		sumBtn.setEnabled(false);
		blkBtn.setEnabled(false);
		qryBtn.setEnabled(false);
		
		// get checked list
		int nTot = 0, nSeq = 0;
		Vector <String> chkName = new Vector <String>();
		for (String name : name2chk.keySet())
		{
			if (name2chk.get(name).isSelected())
			{
				nTot++;
				if (!fpcs.contains(name)) nSeq++;
				chkName.add(name);
			}
		}
		if (nTot==0) return;
		
		// any pairs in checked list
		int npairs=0, nself=0;
		for (int i=0; i<chkName.size(); i++) { 
			String n1 = chkName.get(i);
			if (pairs.contains(n1+delim+n1)) {npairs++; nself++;} // self
		}
		for (int i=0; i<chkName.size()-1; i++) { 
			String n1 = chkName.get(i);
			
			for (int j=i+1; j<chkName.size(); j++) {
				String n2 = chkName.get(j);
				if (pairs.contains(n1+delim+n2) || pairs.contains(n2+delim+n1)) npairs++;
			}
		}

		if (npairs==0) return;
		
		if (nTot > 0 || nself>0) {// explore, dotplot, circle 
			expBtn.setEnabled(true);
			dotBtn.setEnabled(true);
			circBtn.setEnabled(true);
		}
		if (nTot==2 || (nTot==1 && nself==1)) { // must have two seq projects or self-synteny
			blkBtn.setEnabled(true);
		}
		if (nSeq==2 && nTot==2) {			// must have two seq projects
			qryBtn.setEnabled(true);
			sumBtn.setEnabled(true);
		}
	}
	private DatabaseReader getDatabaseReader() {
			return DatabaseUser.getDatabaseReader(SyMAPConstants.DB_CONNECTION_SYMAP_APPLET,
				getParameter(DATABASE_URL),
				getParameter(USERNAME),
				getParameter(PASSWORD),
				Utilities.getHost(this));
	}

	public void mouseMoved(MouseEvent e)   
	{ 
		Object src = e.getSource();
		if (src instanceof JButton)
		{
			txtHelp.setText(btnText.get(((JButton)src)));
		}
		else
		{
			txtHelp.setText(HELP_START);			
		}
	}
	public void mouseDragged(MouseEvent e)   
	{ 
	}	
}
