package symap.projectmanager.common;

import java.applet.Applet;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.TreeSet;
import java.util.Vector;

//import javax.media.j3d.Canvas3D;
//import javax.media.j3d.RenderingError;
//import javax.media.j3d.RenderingErrorListener;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.WindowConstants;
import javax.swing.border.BevelBorder;

import java.sql.ResultSet;

import backend.Utils;

import symap.SyMAP;
import symap.drawingpanel.DrawingPanel;
import symap.frame.HelpBar;
import symap.frame.HelpListener;
import symap.frame.SyMAPFrame;
import symap.projectmanager.common.CollapsiblePanel;
import symap.projectmanager.common.Project;
import symap.projectmanager.common.TrackCom;
import symap.projectmanager.common.Mapper;
//import symap3D.SyMAPFrameExp.MutexButtonPanel;
//import symap3D.SyMAPFrameExp.MySplitPane;
import util.DatabaseReader;
import util.LinkLabel;
import util.Utilities;
import circview.*;

//import com.sun.j3d.utils.universe.SimpleUniverse;

import dotplot.DotPlotFrame;

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class SyMAPFrameCommon extends JFrame implements HelpListener {
	protected int VIEW_CIRC = 1;
	protected int VIEW_2D = 2;
	protected int VIEW_DP = 3;
	
	protected MutexButtonPanel navControlBar;
	protected MutexButtonPanel viewControlBar;
	protected JSlider sldRotate = null;
	protected JButton btnShow2D, btnShowDotplot, btnShowCircle;
	protected JPanel controlPanel;
	protected JPanel cardPanel;
	protected JSplitPane splitPane;
	
	protected HelpBar helpBar;
//	private Canvas3D canvas3D;
//	private SimpleUniverse universe;
	protected Mapper mapper;
	protected SyMAP symap2D = null;
	protected DotPlotFrame dotplot = null;
	protected Applet applet;
	protected DatabaseReader dbReader;
	
	protected boolean hasInit = false;
	protected boolean isFirst2DView = true;
	protected boolean isFirstDotplotView = true;
	protected int selectedView = 1;
	protected int screenWidth, screenHeight;
	
	//	private static GraphicsConfiguration preferredGraphicsConfig = SimpleUniverse.getPreferredConfiguration();
	
	public SyMAPFrameCommon(Applet applet, DatabaseReader dbReader) {
		super("SyMAP "+SyMAP.VERSION);
		
		this.applet = applet;
		this.dbReader = dbReader;
		
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		
		
		Rectangle screenRect = Utilities.getScreenBounds(applet,this);
		screenWidth  = Math.min(1200, screenRect.width);
		screenHeight = Math.min(900, screenRect.height);
		setSize(screenWidth, screenHeight); // mdb removed 12/31/09 #208
		
		// Using a card layout to switch views fixes the Windows CONTEXT_CREATION_ERROR problem
		cardPanel = new JPanel();
		cardPanel.setLayout( new CardLayout() );
		
        // Create split pane for Control Panel
        splitPane = new MySplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setContinuousLayout(true);
        splitPane.setDividerLocation(screenWidth * 1/4);
        splitPane.setBorder(null);
        splitPane.setRightComponent(cardPanel);
        
        setLayout(new BorderLayout());
        add(splitPane, BorderLayout.CENTER);
        
        helpBar = new HelpBar(500, 130, true, false, false);
        helpBar.setBorder( BorderFactory.createLineBorder(Color.LIGHT_GRAY) );
	}
	public SyMAPFrameCommon(Applet applet, DatabaseReader dbReader, Mapper mapper)
	{
		this(applet, dbReader);
		this.mapper = mapper;
	}
	// mdb added 1/28/10
	public void dispose() { // override
		setVisible(false); // necessary?
//		if (universe != null) universe.cleanup();
//		universe = null;
		if (symap2D != null) symap2D.clear();
		symap2D = null;
		dotplot = null;
		applet = null;
		super.dispose();
	}
	
	private void setView(int viewNum) {
		if (viewNum == VIEW_2D) {
			((CardLayout)cardPanel.getLayout()).show(cardPanel, Integer.toString(VIEW_2D));
		}
		else if (viewNum == VIEW_DP) {
			((CardLayout)cardPanel.getLayout()).show(cardPanel, Integer.toString(VIEW_DP));
		}
		else if (viewNum == VIEW_CIRC) {
			((CardLayout)cardPanel.getLayout()).show(cardPanel, Integer.toString(VIEW_CIRC));
		}

	}

	private JPanel createViewControlBar() {
		viewControlBar = new MutexButtonPanel("Views:", 5);
		viewControlBar.setAlignmentX(Component.LEFT_ALIGNMENT);
		viewControlBar.setBorder(BorderFactory.createCompoundBorder(
				//BorderFactory.createLineBorder(Color.LIGHT_GRAY),
				BorderFactory.createEtchedBorder(),
				BorderFactory.createEmptyBorder(5, 5, 5, 5)));
		
		btnShowCircle = new JButton("Circ");
		btnShowCircle.setEnabled(true);
		btnShowCircle.setSelected(true);
		btnShowCircle.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Utilities.setCursorBusy(getContentPane(), true);
				regenerateCircleView();
				selectedView = viewControlBar.getSelected();
				Utilities.setCursorBusy(getContentPane(), false);
			}
		});		
		viewControlBar.addButton(btnShowCircle);
		helpBar.addHelpListener(btnShowCircle, new HelpListener() {
			public String getHelpText(MouseEvent e) { 
				return "Show Circle View: Click this button to switch to the circle view.";
			}
		});
		
		btnShow2D = new JButton("2D");
		btnShow2D.setEnabled(false);
		btnShow2D.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Utilities.setCursorBusy(getContentPane(), true);
				boolean success = regenerate2DView();
				if (success)
					selectedView = viewControlBar.getSelected();
				else { // revert to previous view
					viewControlBar.setSelected(selectedView);
					viewControlBar.getSelectedButton().setEnabled(true);
				}
				Utilities.setCursorBusy(getContentPane(), false);
			}
		});


		viewControlBar.addButton(btnShow2D);
		helpBar.addHelpListener(btnShow2D, new HelpListener() {
			public String getHelpText(MouseEvent e) { 
				return "Show 2D View: Click this button to switch to the 2D view.";
			}
		});
		
		btnShowDotplot = new JButton("Dotplot");
		btnShowDotplot.setEnabled(false);
		btnShowDotplot.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Utilities.setCursorBusy(getContentPane(), true);
				regenerateDotplotView();
				selectedView = viewControlBar.getSelected();
				Utilities.setCursorBusy(getContentPane(), false);
			}
		});
		viewControlBar.addButton(btnShowDotplot);
		helpBar.addHelpListener(btnShowDotplot, new HelpListener() {
			public String getHelpText(MouseEvent e) { 
				return "Show Dotplot View:  Click this button to switch to the Dotplot view.";
			}
		});
		
		return viewControlBar;
	}

	private ActionListener projectChange = new ActionListener() {
		public void actionPerformed(ActionEvent e) {
			if (mapper.hasChanged()) {
				// Repaint project panels
				//updateShownGroups();
				controlPanel.repaint();
				//cf.cp.revalidate();
				// Regenerate main display
				isFirst2DView = true;
				isFirstDotplotView = true;
				btnShow2D.setEnabled( mapper.getNumVisibleTracks() > 0 );
				btnShowDotplot.setEnabled( mapper.getNumVisibleTracks() > 0 );
				btnShowCircle.setEnabled( mapper.getNumVisibleTracks() > 0 );
				if (viewControlBar.getSelected() == VIEW_CIRC)
				{
					regenerateCircleView();
				}
				//cardPanel.repaint();
			}
		}
	};
	
	private void createControlPanel() {
		// Create project graphical menus
        JPanel projPanel = new JPanel();
        projPanel.setLayout(new BoxLayout(projPanel, BoxLayout.Y_AXIS));
       // projPanel.setMinimumSize(new Dimension(600, 600));
   
        int maxHeight = 0;
		Project[] projects = mapper.getProjects();
		
		// First figure out which groups have synteny in this set
		TreeSet<Integer> grpIdxWithSynteny = new TreeSet<Integer>();
		for (Project p : projects) {
			Block[] blks = mapper.getBlocksForProject(p.getID());
			for (Block blk : blks)
			{
				grpIdxWithSynteny.add(blk.getGroup1Idx());
				grpIdxWithSynteny.add(blk.getGroup2Idx());
			}
		}
		
		for (Project p : projects) {
			ProjectPanelCommon pd = new ProjectPanelCommon(mapper, p, projectChange,grpIdxWithSynteny);
			helpBar.addHelpListener(pd,this);
			
			CollapsiblePanel cp = new CollapsiblePanel(p.getDisplayName(), null, false);
			cp.add(pd);
			cp.expand();
			maxHeight += cp.getMaximumSize().getHeight() + 15;
			
			projPanel.add(cp);
		}
		
//		JScrollPane scroller = new JScrollPane( projPanel, 
//				JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, 
//				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
//		scroller.setBorder(null);
//		scroller.getVerticalScrollBar().setUnitIncrement(5);
//		scroller.setMaximumSize(new Dimension(500, maxHeight));
//		scroller.setAlignmentX(Component.LEFT_ALIGNMENT);

		// Create instructions panel
        LinkLabel helpLink = new LinkLabel("Click for online help");
        helpLink.setFont(new Font(helpLink.getFont().getName(), Font.PLAIN, helpLink.getFont().getSize()));
        helpLink.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				Utilities.setCursorBusy(SyMAPFrameCommon.this, true);
				if ( !Utilities.tryOpenURL(applet,SyMAP.USER_GUIDE_URL) )
					System.err.println("Error opening URL: " + SyMAP.USER_GUIDE_URL);
				Utilities.setCursorBusy(SyMAPFrameCommon.this, false);
			}
		});
        
		CollapsiblePanel helpCollapsiblePanel = new CollapsiblePanel("Instructions", null, false);
		helpCollapsiblePanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
		helpCollapsiblePanel.add( helpBar );
		helpCollapsiblePanel.add( helpLink );
		helpCollapsiblePanel.expand();
		
		// Setup top-level panel
        controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        controlPanel.setBorder( BorderFactory.createEmptyBorder(0, 5, 0, 0) );
		controlPanel.add( projPanel );
		controlPanel.add( helpCollapsiblePanel );
		controlPanel.add( Box.createVerticalStrut(20) );
		controlPanel.add( createViewControlBar() );
		controlPanel.add( Box.createVerticalStrut(10) );
		controlPanel.add(createDownloadBar());
		controlPanel.add( Box.createVerticalStrut(10) );
		controlPanel.add( Box.createVerticalGlue() );
		
		splitPane.setLeftComponent(controlPanel);
		
	}
	
	public void build() { // can't be named "show()" because of override
		if (mapper == null)
			return;
		
		// Initialization is done here and not in constructor because the 
		// necessary data (tracks/blocks) aren't avail until now.
		if (!hasInit) {
			createControlPanel();
			regenerateCircleView();
		}
		
		if (mapper.getNumTracks() == 0) {
			System.err.println("No tracks to display!");
			return;
		}
		
		//setVisible(true); // mdb removed 12/31/09 #208
	}
	
	private void regenerateCircleView()
	{	
		int[] pidxList = new int[mapper.getProjects().length];
		TreeSet<Integer> shownGroups = new TreeSet<Integer>();

		for (int i = 0; i < mapper.getProjects().length; i++)
		{
			int pid = mapper.getProjects()[i].getID();
			pidxList[i] = pid;
			for (TrackCom t : mapper.getTracks(pid))
			{
				if (t.isVisible() || mapper.getReferenceTrack() == t)
				{
					shownGroups.add(t.getGroupIdx());
				}
			}
		}
		
		CircFrame circframe = new CircFrame(applet,dbReader,pidxList,shownGroups,helpBar);
		//cardPanel.removeAll();
		cardPanel.add(circframe.getContentPane(), Integer.toString(VIEW_CIRC)); // ok to add more than once
		
		setView(VIEW_CIRC);
		//circframe.cp.getScrollPane().revalidate();
		//circframe.getContentPane().repaint();
		//circframe.repaint();
		//cardPanel.invalidate();
		//cardPanel.repaint();
	}
	private void regenerateDotplotView() {
		// Get selected projects/groups
		int[] projects = mapper.getVisibleProjectIDs();
		int[] groups = mapper.getVisibleGroupIDs();
		
		// Split into x and y groups
		int[] xGroups = new int[] { groups[0] };
		int[] yGroups = new int[groups.length-1];
		for (int i = 1;  i < groups.length;  i++)
			yGroups[i-1] = groups[i];
		
		// Create dotplot frame
		if (dotplot == null)
			dotplot = new DotPlotFrame(applet, dbReader, projects, xGroups, yGroups, helpBar, false);
		else if (isFirstDotplotView)
			dotplot.getData().initialize(projects, xGroups, yGroups);

		// Switch to dotplot display
		cardPanel.add(dotplot.getContentPane(), Integer.toString(VIEW_DP)); // ok to add more than once
		setView(VIEW_DP);
		
		isFirstDotplotView = false;
	}
		
	private boolean regenerate2DView() {
		try {
			if (symap2D == null)
				symap2D = new SyMAP(applet, dbReader, helpBar, null);
			
			SyMAPFrame frame = symap2D.getFrame();
			if (frame == null) {
				System.err.println("SyMAPFrame3D:  Error creating 2D frame!");
				return false;
			}
			
			DrawingPanel dp = symap2D.getDrawingPanel();
			
			// WMN start the 2D over when they go back and forth
			if (true/*|| isFirst2DView*/) { 
				// Get selected tracks
				TrackCom ref = mapper.getReferenceTrack();
				TrackCom[] selectedTracks = mapper.getVisibleTracks();
				
				if (selectedTracks.length > 4 &&
						JOptionPane.showConfirmDialog(null,"This view may take a while to load and/or cause SyMAP to run out of memory, try anyway?","Warning",
							JOptionPane.YES_NO_OPTION,JOptionPane.ERROR_MESSAGE) != JOptionPane.YES_OPTION)
				{
					return false;
				}
				
				// Disable 2D rendering
				dp.setFrameEnabled(false);
				
				// Clear existing
				dp.resetData(); // clear caches
				symap2D.getHistory().clear(); // clear history
				dp.setMaps(0);
				
				// Setup 2D
				int position = 1;
				for (int i = 0;  i < selectedTracks.length;  i++) {
					TrackCom t = selectedTracks[i];
					
					//System.out.println("position " + position + " " + t.getFullName());
					
					// Add track
					if (t.isPseudo())
						dp.setSequenceTrack( position++, t.getProjIdx(), t.getGroupIdx(), t.getColor() );
					else {
						String contigs = Mapper.getContigListForBlocks(
								mapper.getBlocksForTracks( t.getGroupIdx(), ref.getGroupIdx() ));
						//System.out.println("contigs: "+contigs);
						dp.setBlockTrack( position++, t.getProjIdx(), "Chr " + t.getGroupName(), contigs, t.getColor() );
					}
					
					// Add alternating reference track
					if (selectedTracks.length == 1 || selectedTracks.length-1 != i) { // middle tracks
						if (ref.isPseudo())
							dp.setSequenceTrack( position++, ref.getProjIdx(), ref.getGroupIdx(), ref.getColor() );
						else {
							// Get contigs for left track
							String contigs = Mapper.getContigListForBlocks(
									mapper.getBlocksForTracks( ref.getGroupIdx(), t.getGroupIdx() ));
							// Get contigs for right track if present
							if (i+1 < selectedTracks.length)
								contigs += Mapper.getContigListForBlocks(
										mapper.getBlocksForTracks( ref.getGroupIdx(), selectedTracks[i+1].getGroupIdx() ));
							//System.out.println("contigs: "+contigs);
							dp.setBlockTrack( position++, ref.getProjIdx(), "Chr " + ref.getGroupName(), contigs, ref.getColor() );
						}
					}
				}
				dp.setMaps( position - 2 );
			}
			
			// Enable 2D display
			cardPanel.add(frame.getContentPane(), Integer.toString(VIEW_2D)); // ok to add more than once
			setView(VIEW_2D);
			
			dp.amake(); // redraw and make visible
			
			isFirst2DView = false;
			return true;
		}
		catch (OutOfMemoryError e) { // mdb added 1/29/10
			symap2D = null;
			Utilities.showOutOfMemoryMessage();
		}
		catch (Exception err) {
			err.printStackTrace();
		}
		
		return false;
	}
	
	private class MutexButtonPanel extends JPanel implements ActionListener {
		private GridBagConstraints constraints;
		
//		public MutexButtonPanel() {
//			this(null, 0);
//		}
//		
//		public MutexButtonPanel(String title) {
//			this(title, 0);
//		}
		
		public MutexButtonPanel(String title, int pad) {
			GridBagLayout layout = new GridBagLayout();
			setLayout(layout);
			
			constraints = new GridBagConstraints();
			constraints.fill = GridBagConstraints.HORIZONTAL;
			constraints.gridheight = 1;
			constraints.ipadx = 5;
			constraints.ipady = 8;
			if (pad > 0) constraints.insets = new Insets(0, 0, 0, pad);
			
			JLabel label = new JLabel(title);
			label.setFont(label.getFont().deriveFont(Font.PLAIN));
			((GridBagLayout)getLayout()).setConstraints(label, constraints);
			add(label);
			label.setMinimumSize(label.getPreferredSize());
		}
		
////		public JButton addButton(String strText, String strIconPath) {
////			return addButton(strText, strIconPath, null);
////		}
//		
//		public JButton addButton(String strText, String strIconPath, ActionListener l) {
//			JButton button = new JButton();
//			button.setToolTipText(strText);
//			if (strIconPath != null)
//				button.setIcon( ImageViewer.getImageIcon(strIconPath) );
//			else
//				button.setText(strText);
//			
//			if (l != null) button.addActionListener(l);
//			addButton(button);
//			
//			return button;
//		}
		
		public void addButton(JButton button) {
			button.setMargin(new Insets(0, 0, 0, 0));
			button.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
			button.addActionListener(this);
			if (getComponentCount() == 1) // select first button
				setSelected(button);//button.doClick();
			
			((GridBagLayout)getLayout()).setConstraints(button, constraints);
			add(button);
			
			setMaximumSize(getPreferredSize());
		}
		
		public JButton getSelectedButton() {
			Component[] comps = getComponents();
			for (int i = 0;  i < comps.length;  i++) {
				if (comps[i] instanceof JButton) {
					JButton b = (JButton)comps[i];
					if (b.isSelected())
						return b;
				}
			}
			return null;
		}
		
		public int getSelected() {
			Component[] comps = getComponents();
			for (int i = 0;  i < comps.length;  i++) {
				if (comps[i] instanceof JButton) {
					JButton b = (JButton)comps[i];
					if (b.isSelected())
					{
						return i;
					}
				}
			}
			return -1;
		}
		
		public void setSelected(int n) {
			Component c = getComponent(n);
			if (c != null && c instanceof JButton)
				setSelected((JButton)c);
		}
		
		public void setSelected(JButton button) {
			// De-select all buttons
			for (Component c : getComponents()) {
				if (c instanceof JButton) {
					JButton b = (JButton)c;
					b.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
					b.setSelected(false);
					b.setEnabled(true);
				}
			}
			
			// kludge:
			if (btnShow2D != null) btnShow2D.setEnabled( mapper.getNumVisibleTracks() > 0 );
			if (btnShowDotplot != null) btnShowDotplot.setEnabled( mapper.getNumVisibleTracks() > 0 );
			if (btnShowCircle != null) btnShowCircle.setEnabled( mapper.getNumVisibleTracks() > 0 );
			
			// Select this button
			button.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
			button.setSelected(true);
			button.setEnabled(false);
		}
		
		public void actionPerformed(ActionEvent e) {
			if (e.getSource() != null && e.getSource() instanceof JButton)
				setSelected((JButton)e.getSource());
		}
	}
	
	private class MySplitPane extends JSplitPane {
		public MySplitPane(int newOrientation) {
			super(newOrientation);
		}
		
//		public void setDividerVisible(boolean visible) {
//			((BasicSplitPaneUI)getUI()).getDivider().setVisible(visible);
//		}
	}

	public String getHelpText(MouseEvent e) {
		Object o = e.getSource();
		
		if (o instanceof ProjectPanelCommon)
			return	"Click a chromosome to add/remove it from the scene.\n" +
					"Click the chromosome number to make it the reference.\n";
		
		return null;
	}
	//
	// Duplicated to SyMAPFrame3D!!
	//
	private JPanel createDownloadBar() {
		JPanel pnl = new JPanel();
		pnl.setLayout( new BoxLayout(pnl, BoxLayout.LINE_AXIS) );
		pnl.setAlignmentX(LEFT_ALIGNMENT);
		//pnl.setBackground();
		JButton btn = new JButton("Download Blocks");
		//btn.setBackground(Color.white);
		btn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				downloadBlocks();
			}
		});		
		pnl.add(btn);
		return pnl;
	}
    private void downloadBlocks() {
    	try {
			JFileChooser chooser = new JFileChooser(System.getProperty("user.dir"));
			chooser.setSelectedFile(new File("blocks.tsv"));
			
			if(chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
				if(chooser.getSelectedFile() != null) {
					File f = chooser.getSelectedFile();
					if (f.exists()) {
						if (JOptionPane.YES_OPTION != JOptionPane.showConfirmDialog(null,"The file exists, do you want to overwrite it?", 
								"File exists",JOptionPane.YES_NO_OPTION))
						{
							return;
						}
						f.delete();
					}
					f.createNewFile();
					PrintWriter out = new PrintWriter(new FileWriter(chooser.getSelectedFile()));
					Vector<String> row = new Vector<String>();
					row.add("Species1");
					row.add("Species2");
					row.add("Chr1");
					row.add("Chr2");
					row.add("BlkNum");
					row.add("Start1");
					row.add("End1");
					row.add("Start2");
					row.add("End2");
					row.add("#Hits");
					row.add("Genes1");
					row.add("%Genes1");
					row.add("Genes2");
					row.add("%Genes2");
					row.add("PearsonR");
					out.println(Utils.join(row, "\t"));
					
					Vector<String> projList = new Vector<String>();
					Project[] projects = mapper.getProjects();
					for (Project p : projects) {
						projList.add(String.valueOf(p.getID()));
					}
					String projStr = Utils.join(projList,",");
					String query = "select p1.name, p2.name, g1.name, g2.name, b.blocknum, b.start1, b.end1," +
					" b.start2,b.end2,b.score,b.ngene1,b.genef1,b.ngene2,b.genef2,b.corr " +
					" from blocks as b join groups as g1 on g1.idx=b.grp1_idx join groups as g2 on g2.idx=b.grp2_idx " +
					" join projects as p1 on p1.idx=b.proj1_idx join projects as p2 on p2.idx=b.proj2_idx " +
					" where p1.idx in (" + projStr + ") and p2.idx in (" + projStr + ") and p1.type='pseudo' and p2.type='pseudo' " +
					" order by p1.name asc, p2.name asc, g1.idx asc, g2.idx asc, b.blocknum asc";
					//System.err.println(query);
					ResultSet rs = dbReader.getConnection().createStatement().executeQuery(query);
					int n = 0;
					while (rs.next())
					{
						for(int i = 1; i <= row.size(); i++)
						{
							row.set(i-1, rs.getString(i));
						}
						out.println(Utils.join(row, "\t"));
						n++;
					}
					out.close();
					System.err.println("Wrote " + n + " blocks");
				}
				

			}
		} catch(Exception e) {
			e.printStackTrace();
		}

    }
}

