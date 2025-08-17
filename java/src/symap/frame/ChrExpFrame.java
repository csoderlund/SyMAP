package symap.frame;

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
import java.util.TreeSet;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.WindowConstants;
import javax.swing.border.BevelBorder;

import symap.drawingpanel.DrawingPanel;
import symap.drawingpanel.Frame2d;
import symap.drawingpanel.SyMAP2d;
import symap.manager.Mproject;
import util.ErrorReport;
import util.LinkLabel;
import util.Utilities;
import util.Jhtml;
import circview.CircFrame;
import database.DBconn2;
import dotplot.DotPlotFrame;

/*****************************************************
 * Chromosome Explorer: Displays left side and controls right (circle, 2d, dotplot)
 * CAS571 moved Download Blocks to Manager
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class ChrExpFrame extends JFrame implements HelpListener {
	private final int MIN_WIDTH = 1100, MIN_HEIGHT = 900; // cardPanel will be 825; 
	
	private int VIEW_CIRC = 1, VIEW_2D = 2, VIEW_DP = 3;
	
	private MutexButtonPanel viewControlBar;
	private JButton btnShow2D, btnShowDotplot, btnShowCircle;
	private JPanel controlPanel, cardPanel;
	private JSplitPane splitPane;
	
	private HelpBar helpBar;

	private MapLeft mapper;
	private SyMAP2d symap2D = null;
	private DotPlotFrame dotplot = null;
	private CircFrame circframe = null; 
	private DBconn2 tdbc2, circdbc=null;
	
	private int selectedView = 1;
	private int screenWidth, screenHeight;
	
	private ChrInfo[] lastSelectedTracks=null; // for 2d so not to recreate if same
	private ChrInfo lastRef=null;
	
	// called by symap.frame.ChrExpInit
	protected ChrExpFrame(String title, DBconn2 tdbc2, MapLeft mapper) {
		super(title); 
		this.tdbc2 = tdbc2;
		this.mapper = mapper;
		
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		
		Rectangle screenRect = Utilities.getScreenBounds(this);
		screenWidth  = Math.min(MIN_WIDTH, screenRect.width);
		screenHeight = Math.min(MIN_HEIGHT, screenRect.height);
		setSize(screenWidth, screenHeight); 
		setLocationRelativeTo(null); 						
		
		// Using a card layout to switch views between 2D, Circle, Dotplot
		cardPanel = new JPanel();
		cardPanel.setLayout( new CardLayout() );
		
        // Create split pane for Control Panel
        splitPane = new MySplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setContinuousLayout(true); 				// picture redraws continuously
        splitPane.setDividerLocation(screenWidth * 1/4); 	// behaves best using this instead of fix width
        splitPane.setBorder(null);
        splitPane.setRightComponent(cardPanel);
        
        setLayout(new BorderLayout());
        add(splitPane, BorderLayout.CENTER);
        
        helpBar = new HelpBar(425, 130); 					
        helpBar.setBorder( BorderFactory.createLineBorder(Color.LIGHT_GRAY) );
        
        createControlPanel(); // adds to Left side of splitPane;
		showCircleView();	  // adds to cardPanel, which is on Right side
	}

	public void dispose() { // override
		setVisible(false); 
		tdbc2.close();
		if (circframe!=null) circframe.clear();
		circframe=null;
		
		if (symap2D != null) symap2D.clear();
		symap2D = null;
		if (dotplot != null) dotplot.clear(); 				
		dotplot = null;
		super.dispose();
	}
	
	private JPanel createViewControlBar() {
		viewControlBar = new MutexButtonPanel("Views:", 5);
		viewControlBar.setAlignmentX(Component.LEFT_ALIGNMENT);
		viewControlBar.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createEtchedBorder(),
				BorderFactory.createEmptyBorder(5, 5, 5, 5)));
		
		btnShowCircle = new JButton("Circle");
		btnShowCircle.setEnabled(true);
		btnShowCircle.setSelected(true);
		btnShowCircle.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Utilities.setCursorBusy(getContentPane(), true);
				showCircleView();
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
				boolean success = show2DView();
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
		
		btnShowDotplot = new JButton("Dot Plot");
		btnShowDotplot.setEnabled(false);
		btnShowDotplot.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Utilities.setCursorBusy(getContentPane(), true);
				showDotplotView();
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
				controlPanel.repaint();// Repaint project panels
				
				// Regenerate main display
				btnShow2D.setEnabled( mapper.getNumVisibleChrs() > 0 );
				btnShowDotplot.setEnabled( mapper.getNumVisibleChrs() > 0 );
				btnShowCircle.setEnabled( true ); 
				
				if (viewControlBar.getSelected() == VIEW_CIRC) showCircleView();
			}
		}
	};
	
	private void createControlPanel() {
		// Create project graphical menus
        JPanel projPanel = new JPanel();
        projPanel.setLayout(new BoxLayout(projPanel, BoxLayout.Y_AXIS));
    
		Mproject[] projects = mapper.getProjects();
		
		// First figure out which groups have synteny in this set
		TreeSet<Integer> grpIdxWithSynteny = new TreeSet<Integer>();
		for (Mproject p : projects) {
			Block[] blks = mapper.getBlocksForProject(p.getID());
			for (Block blk : blks) {
				grpIdxWithSynteny.add(blk.getGroup1Idx());
				grpIdxWithSynteny.add(blk.getGroup2Idx());
			}
		}
		
		for (Mproject p : projects) {
			ProjIcons pd = new ProjIcons(mapper, p, projectChange, grpIdxWithSynteny);
			helpBar.addHelpListener(pd,this);
			
			CollapsiblePanel cp = new CollapsiblePanel(p.getDisplayName(), null, false);
			cp.add(pd);
			cp.expand();
			
			projPanel.add(cp);
		}

		// Create instructions panel
        LinkLabel infoLink = new LinkLabel("Click for online help");
        infoLink.setFont(new Font(infoLink.getFont().getName(), Font.PLAIN, infoLink.getFont().getSize()));
        infoLink.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				Utilities.setCursorBusy(ChrExpFrame.this, true);
				if ( !Jhtml.tryOpenURL(Jhtml.USER_GUIDE_URL) )
					System.err.println("Error opening URL: " + Jhtml.USER_GUIDE_URL);
				Utilities.setCursorBusy(ChrExpFrame.this, false);
			}
		});
        
		CollapsiblePanel infoCollapsiblePanel = new CollapsiblePanel("Information", null, false); 
		infoCollapsiblePanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
		infoCollapsiblePanel.add( helpBar );
		infoCollapsiblePanel.add( infoLink );
		infoCollapsiblePanel.expand();
		
		// Setup top-level panel
        controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        controlPanel.setBorder( BorderFactory.createEmptyBorder(0, 5, 0, 0) );
		controlPanel.add( projPanel );
		controlPanel.add( infoCollapsiblePanel ); 	controlPanel.add( Box.createVerticalStrut(20) );
		controlPanel.add( createViewControlBar() );	controlPanel.add( Box.createVerticalStrut(10) );
		//controlPanel.add( createDownloadBar());		controlPanel.add( Box.createVerticalStrut(10) );
		controlPanel.add( Box.createVerticalGlue() );
		
		splitPane.setLeftComponent(controlPanel);
	}
	///////////////////////////////////////////////////////////////////
	private void showCircleView(){
		double [] lastParams = (circframe!=null) ? circframe.getLastParams() : null; // reuse settings
	
		int[] pidxList = new int[mapper.getProjects().length];
		TreeSet<Integer> shownGroups = new TreeSet<Integer>();
		boolean hasSelf = false; // do not show self-align is this is none
		
		int refIdx=-1; // have priority colors 
		for (int i = 0; i < mapper.getProjects().length; i++){
			Mproject pg = mapper.getProjects()[i];
			int pid = pg.getID();
			pidxList[i] = pid;
			
			if (pg.hasSelf()) hasSelf=true;
			
			for (ChrInfo t : mapper.getChrs(pid)){
				if (t.isVisible() || mapper.getRefChr() == t) {
					shownGroups.add(t.getGroupIdx());
					if (mapper.getRefChr() == t) refIdx=pid;
				}
			}
		}
		if (circdbc==null) circdbc = new DBconn2("CircleE-" + DBconn2.getNumConn(), tdbc2);
		
		circframe = new CircFrame(circdbc, pidxList, shownGroups, helpBar, refIdx, hasSelf, lastParams); // have to recreate everytime
		
		cardPanel.add(circframe.getContentPane(), Integer.toString(VIEW_CIRC)); // ok to add more than once
		((CardLayout)cardPanel.getLayout()).show(cardPanel, Integer.toString(VIEW_CIRC));
	}
	///////////////////////////////////////////////////////////////////
	private void showDotplotView() {
		// Get selected projects/groups
		int[] projects = mapper.getVisibleProjectIDs();
		int[] groups = mapper.getVisibleGroupIDs();
		
		// Split into x and y groups
		int[] xGroups = new int[] { groups[0] };
		int[] yGroups = new int[groups.length-1];
		for (int i = 1;  i < groups.length;  i++)
			yGroups[i-1] = groups[i];
	
		// Create dotplot frame
		if (dotplot == null) dotplot = new DotPlotFrame("", tdbc2, projects, xGroups, yGroups, helpBar, false);
		else dotplot.getData().initialize(projects, xGroups, yGroups);

		// Switch to dotplot display
		cardPanel.add(dotplot.getContentPane(), Integer.toString(VIEW_DP)); // ok to add more than once
		((CardLayout)cardPanel.getLayout()).show(cardPanel, Integer.toString(VIEW_DP));
	}
	///////////////////////////////////////////////////////////////////////////////
	private boolean show2DView() {
		try {	
			ChrInfo ref = mapper.getRefChr();
			ChrInfo[] selectedTracks = mapper.getVisibleChrs(); // no reference tracks
			
			if (selectedTracks.length > 4 &&
					JOptionPane.showConfirmDialog(null,"This view may take a while to load and/or cause SyMAP to run out of memory, try anyway?","Warning",
						JOptionPane.YES_NO_OPTION,JOptionPane.ERROR_MESSAGE) != JOptionPane.YES_OPTION) 
				return false;
			
			DrawingPanel dp;
			boolean noRedo= (lastRef!=null && ref == lastRef && selectedTracks.length==lastSelectedTracks.length);
			if (noRedo) { 								// do not redo if exact same, otherwise, totally redo 
				for (int i=0; i<selectedTracks.length && noRedo; i++) 
					if (selectedTracks[i]!=lastSelectedTracks[i]) noRedo=false;
			}
			if (!noRedo) {
				if (symap2D != null) symap2D.clearLast();
				symap2D = new SyMAP2d(tdbc2, helpBar, null); // start fresh
				dp = symap2D.getDrawingPanel();
						
				lastRef=ref;
				lastSelectedTracks = selectedTracks;
				
				dp.setTracks(selectedTracks.length+1);		
				
				// Setup 2D
				int position = 1;
				for (int i = 0;  i < selectedTracks.length;  i++) {
					ChrInfo t = selectedTracks[i];
					
					dp.setSequenceTrack(position++, t.getProjIdx(), t.getGroupIdx(), t.getColor());
					if (selectedTracks.length == 1 || selectedTracks.length-1 != i) // Add alternating reference track
					  dp.setSequenceTrack(position++, ref.getProjIdx(), ref.getGroupIdx(), ref.getColor()); 
				}
			}
			else {
				dp = symap2D.getDrawingPanel();
			}
			// Enable 2D display
			Frame2d frame = symap2D.getFrame();
			cardPanel.add(frame.getContentPane(), Integer.toString(VIEW_2D)); // ok to add more than once
			((CardLayout)cardPanel.getLayout()).show(cardPanel, Integer.toString(VIEW_2D));
			
			dp.amake(); 						// draw and make visible
			return true;
		}
		catch (OutOfMemoryError e) { 
			symap2D = null;
			Utilities.showOutOfMemoryMessage();
		}
		catch (Exception err) {ErrorReport.print(err, "2D view");}
		
		return false;
	}
	
	private class MutexButtonPanel extends JPanel implements ActionListener {
		private GridBagConstraints constraints;
		
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
		
		public void addButton(JButton button) {
			button.setMargin(new Insets(0, 0, 0, 0));
			button.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
			button.addActionListener(this);
			if (getComponentCount() == 1) 		// select first button
				setSelected(button);			
			
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
					if (b.isSelected()){
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
			for (Component c : getComponents()) {// De-select all buttons
				if (c instanceof JButton) {
					JButton b = (JButton)c;
					b.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
					b.setSelected(false);
					b.setEnabled(true);
				}
			}
			
			if (btnShow2D != null) 		btnShow2D.setEnabled( mapper.getNumVisibleChrs() > 0 );
			if (btnShowDotplot != null) btnShowDotplot.setEnabled( mapper.getNumVisibleChrs() > 0 );
			if (btnShowCircle != null) 	btnShowCircle.setEnabled(true); //  if click blocks on right, can reduce tracks
			
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
	}

	public String getHelpText(MouseEvent e) {
		Object o = e.getSource();
		
		if (o instanceof ProjIcons)
			return	"Click a chromosome to add/remove it from the scene.\n" +
					"Click the chromosome number to make it the reference.\n";
		
		return null;
	}
}

