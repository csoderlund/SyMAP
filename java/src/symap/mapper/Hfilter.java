package symap.mapper;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.Frame;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.JRadioButton;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSeparator;

import symap.drawingpanel.DrawingPanel;
import util.Jcomp;
import util.Jhtml;
import util.Utilities;

/**
 * Hit Filter Interface: The Mapper filter dialog implementation.
 * 1. Add filter here
 * 2. Add to HfilterData
 * 3. Add to SeqHits.isHighLightHit() and isFiltered()
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class Hfilter extends JDialog {
	private final String SYN = "Synteny blocks";
	private final String COL = "Collinear sets";
	private final String HIT2 = "Hit =2 genes";
	private final String HIT1 = "Hit =1 genes";
	private final String HIT0 = "Hit =0 genes";
	private final String ALL =  "All hits";
	private final String POPUP ="Hit popup (or Query)";	
	private final String NONE = "None";
	
	// On Menu panel
	private JButton okButton, cancelButton, defaultButton, helpButton;
	private JPanel buttonPanel;
	private JPopupMenu popupMenu; 
	private JMenuItem popupTitle; 
	
	// highlight - only one can be selected
	private JRadioButton hBlockRadio	= new JRadioButton(SYN);
	private JRadioButton hCsetRadio		= new JRadioButton(COL); 
	private JRadioButton hGene2Radio 	= new JRadioButton(HIT2); 
	private JRadioButton hGene1Radio 	= new JRadioButton(HIT1);
	private JRadioButton hGene0Radio	= new JRadioButton(HIT0); 
	private JRadioButton hNoneRadio		= new JRadioButton(NONE);
	private JCheckBox 	 hPopupCheck	= new JCheckBox(POPUP);
	
	// Show - any number can be selected
	private JCheckBox sBlockCheck		= Jcomp.createCheckBoxGray(SYN, "Show all synteny hits");
	private JRadioButton blockAndRadio	= Jcomp.createRadioGray("And", "Hits shown must pass all checks");
	private JRadioButton blockOrRadio	= Jcomp.createRadioGray("Or", "Hits shown must pass any checks");
	
	private JCheckBox sCsetCheck	= Jcomp.createCheckBoxGray(COL, "Show all collinear sets");
	private JCheckBox sGene2Check	= Jcomp.createCheckBoxGray(HIT2, "Show all hits aligning to genes on both sides");
	private JCheckBox sGene1Check	= Jcomp.createCheckBoxGray(HIT1, "Show all hits aligning to genes on one sides");
	private JCheckBox sGene0Check	= Jcomp.createCheckBoxGray(HIT0, "Show all hits that do not align to any gene");
	private JCheckBox sAllCheck		= Jcomp.createCheckBoxGray(ALL, "Show all hits - overrides all but identity"); // CAS567 add
	
	// Id
	private JSlider pctidSlider  = new JSlider(JSlider.HORIZONTAL, 0, 100, 0); // 0 to 100, start at 0
	private JLabel  pctidText    = new JLabel("0%");
	
	private JCheckBox  blockOnlyCheck = Jcomp.createCheckBoxGray("Block #", "Only show this block's hits");  // CAS567 add
	private JTextField blockOnlyText = Jcomp.createTextField("0", "Block number, hit return for immediate effect", 2);
	
// On popup menu
	private JRadioButtonMenuItem hBlockPopRadio	= new JRadioButtonMenuItem(SYN);
	private JRadioButtonMenuItem hCsetPopRadio	= new JRadioButtonMenuItem(COL);
	private JRadioButtonMenuItem hGene2PopRadio = new JRadioButtonMenuItem(HIT2);
	private JRadioButtonMenuItem hGene1PopRadio	= new JRadioButtonMenuItem(HIT1);
	private JRadioButtonMenuItem hGene0PopRadio	= new JRadioButtonMenuItem(HIT0);
	private JRadioButtonMenuItem hNonePopRadio	= new JRadioButtonMenuItem(NONE);
	private JCheckBoxMenuItem    hPopupPopCheck	= new JCheckBoxMenuItem(POPUP);
	private JCheckBoxMenuItem    sAllPopCheck   = new JCheckBoxMenuItem("Show " + ALL); 
	
	private Mapper mapper;
	private HfilterData hitFiltData;    // current settings
	private HfilterData lastHitFiltData; // last settings
	protected DrawingPanel drawingPanel;
	
	public Hfilter(Frame owner, DrawingPanel dp, Mapper map) {
		super(owner,"Hit Filter", true); 
		
		this.drawingPanel = dp;
		this.mapper = map;
		hitFiltData = mapper.getHitFilter();
		setInit(hitFiltData);
		
		FilterListener listener = new FilterListener();
		
		createFilterDialog(listener);
		createPopup(listener);
	}
	/* Filter dialog */
	private void createFilterDialog(FilterListener listener) {
		// Buttons
		okButton = Jcomp.createMonoButton("Save","Save changes and close");
		okButton.addActionListener(listener);

		cancelButton = Jcomp.createMonoButton("Cancel", "Discard changes and close");
		cancelButton.addActionListener(listener);

		defaultButton = Jcomp.createMonoButton("Defaults", "Reset to defaults");
		defaultButton.addActionListener(listener);
		
		helpButton = Jhtml.createHelpIconUserSm(Jhtml.hitfilter);
		
		buttonPanel = new JPanel(new BorderLayout());
		buttonPanel.add(new JSeparator(),BorderLayout.NORTH);
		JPanel innerPanel = new JPanel();
		innerPanel.add(okButton);
		innerPanel.add(cancelButton);
		innerPanel.add(defaultButton);
		innerPanel.add(helpButton);
		buttonPanel.add(innerPanel,BorderLayout.CENTER);
		
		// Slider
		JPanel sliderRow = Jcomp.createGrayRowPanel();
		sliderRow.add(new JLabel("  Identity   ")); // want gray background
		sliderRow.add(pctidText); sliderRow.add(pctidSlider);
		pctidSlider.addChangeListener(listener); 
		
		JPanel rowOnly = Jcomp.createGrayRowPanel();
		blockOnlyCheck.addActionListener(listener); 
		blockOnlyText.addActionListener(listener); 
		rowOnly.add(blockOnlyCheck); rowOnly.add(Box.createHorizontalStrut(2)); rowOnly.add(blockOnlyText);
		
		// Check boxes for menu
		sBlockCheck.addActionListener(listener); 
		blockAndRadio.addActionListener(listener);
		blockOrRadio.addActionListener(listener);
		
		ButtonGroup b2Group = new ButtonGroup();
		b2Group.add(blockAndRadio); b2Group.add(blockOrRadio); blockAndRadio.setSelected(true);
		
		sCsetCheck.addActionListener(listener);
		sGene2Check.addActionListener(listener);
		sGene1Check.addActionListener(listener);
		sGene0Check.addActionListener(listener);
		sAllCheck.addActionListener(listener);	
		
		hBlockRadio.addActionListener(listener);
		hCsetRadio.addActionListener(listener);
		hGene2Radio.addActionListener(listener); 
		hGene1Radio.addActionListener(listener); 
		hGene0Radio.addActionListener(listener); 
		hNoneRadio.addActionListener(listener);
		ButtonGroup highGroup = new ButtonGroup();
		highGroup.add(hBlockRadio); highGroup.add(hCsetRadio); 
		highGroup.add(hGene2Radio); highGroup.add(hGene1Radio); 
		highGroup.add(hGene0Radio); highGroup.add(hNoneRadio); 
		
		hPopupCheck.addActionListener(listener);
		
		Container contentPane = getContentPane();
		GridBagLayout gridbag = new GridBagLayout();
		GridBagConstraints c1 = new GridBagConstraints();

		contentPane.removeAll();

		contentPane.setLayout(gridbag);
		c1.fill = GridBagConstraints.HORIZONTAL;
		c1.gridheight = 1;
		c1.ipadx = 4;
		c1.ipady = 7;

		// highlight
		int rem=GridBagConstraints.REMAINDER;
		addToGrid(contentPane, gridbag, c1, new JLabel("  Highlight"), rem);
		addToGrid(contentPane, gridbag, c1, hBlockRadio, 1);
		addToGrid(contentPane, gridbag, c1, hNoneRadio,rem); 
		
		addToGrid(contentPane, gridbag, c1, hCsetRadio,1); 
		addToGrid(contentPane, gridbag, c1, hGene2Radio, rem);
		
		addToGrid(contentPane, gridbag, c1, hGene1Radio, 1); 
		addToGrid(contentPane, gridbag, c1, hGene0Radio, rem); 
		
		addToGrid(contentPane, gridbag, c1, hPopupCheck, rem);
		addToGrid(contentPane, gridbag, c1, new JSeparator(),rem);
		
		// show
		addToGrid(contentPane, gridbag, c1, new JLabel("  Show"),rem);
		addToGrid(contentPane, gridbag, c1, sBlockCheck, 1);
		addToGrid(contentPane, gridbag, c1, blockAndRadio, 2); 
		addToGrid(contentPane, gridbag, c1, blockOrRadio, rem); 
		
		addToGrid(contentPane, gridbag, c1, sCsetCheck, 1);
		addToGrid(contentPane, gridbag, c1, sGene2Check, rem); 
		
		addToGrid(contentPane, gridbag, c1, sGene1Check,1); 
		addToGrid(contentPane, gridbag, c1, sGene0Check, rem);
		
		addToGrid(contentPane, gridbag, c1, sAllCheck, rem); 
		
		// %id & block#
		addToGrid(contentPane, gridbag, c1, new JSeparator(),rem);
		addToGrid(contentPane, gridbag, c1, sliderRow,rem);
		
		addToGrid(contentPane, gridbag, c1, rowOnly, 1); 
		addToGrid(contentPane, gridbag, c1, new JLabel(""), rem); // only way to force to right
		
		// buttons
		addToGrid(contentPane, gridbag, c1, buttonPanel,rem);

		setBackground(Color.white);
		pack();
		setResizable(false);
		setLocationRelativeTo(null); 
	}
	private void addToGrid(Container cp, GridBagLayout layout, GridBagConstraints con, Component comp, int w) {
		con.gridwidth = w;
		layout.setConstraints(comp, con);
		cp.add(comp);
	}
	
	// Popup menu
	private void createPopup(FilterListener listener) {
		popupMenu = new JPopupMenu(); 
		popupMenu.setBackground(Color.white);
		popupMenu.addPopupMenuListener(new MyPopupMenuListener()); 
		
		popupTitle = new JMenuItem("Hit Options"); popupTitle.setEnabled(false);
		popupMenu.add(popupTitle);
		
		popupMenu.addSeparator();
		JLabel gtext = new JLabel("   Highlight"); gtext.setEnabled(false);
		popupMenu.add(gtext);
		popupMenu.add(hBlockPopRadio); 
		popupMenu.add(hCsetPopRadio); 			
		popupMenu.add(hGene2PopRadio); 
		popupMenu.add(hGene1PopRadio);
		popupMenu.add(hGene0PopRadio);
		popupMenu.add(hNonePopRadio); 
		ButtonGroup grp = new ButtonGroup();
		grp.add(hBlockPopRadio); grp.add(hCsetPopRadio); grp.add(hGene2PopRadio); 
		grp.add(hGene1PopRadio);  grp.add(hGene0PopRadio);  grp.add(hNonePopRadio); 
		hNonePopRadio.setSelected(true);
		
		popupMenu.add(new JSeparator());
		popupMenu.add(sAllPopCheck);		
		
		popupMenu.add(new JSeparator());
		popupMenu.add(hPopupPopCheck);
		
		hBlockPopRadio.addActionListener(listener);
		hCsetPopRadio.addActionListener(listener);
		hGene2PopRadio.addActionListener(listener);
		hGene1PopRadio.addActionListener(listener);	
		hGene0PopRadio.addActionListener(listener);	
		hNonePopRadio.addActionListener(listener);
		
		hPopupPopCheck.addActionListener(listener);
		sAllPopCheck.addActionListener(listener);		
		
		popupMenu.setMaximumSize(popupMenu.getPreferredSize()); popupMenu.setMinimumSize(popupMenu.getPreferredSize());
	}
	// Creates panel
	public void showHitFilter() {
		setInit(hitFiltData); // must be before setSliderMaxMin; may have changed since Hfilter created
		setSliderMaxMin();
		lastHitFiltData = hitFiltData.copy("Hfilter showX");
		
		setVisible(true); 
	}
	public void closeFilter() {
		if (isShowing()) {
			cancelAction();
			setVisible(false); 
		}
	}
	public void showPopup(MouseEvent e) { 
		popupMenu.show(e.getComponent(), e.getX(), e.getY());
	}
	public boolean canShow() {return mapper!=null;}
	
	private boolean okAction() {
		return lastHitFiltData.setChanged(getCopyHitFilter(), "Hfilter okAction");
	}
	private void cancelAction() {
		setInit(lastHitFiltData);
	}
	private void setDefault() { 
		setInit(new HfilterData());
	}
	private void refresh() {
		if (hitFiltData.setChanged(getCopyHitFilter(), "Hfilter refresh")) {
			mapper.setTrackBuild();
			drawingPanel.setReplaceHistory(); 
			drawingPanel.smake("Hf: refresh");
			mapper.update();
		}
	}
	
	/*****************************************
	 * set current filter values like the input filter; Cancel, Defaults and save lastHitFilter
	 */
	private void setInit(HfilterData hf) {	
		pctidSlider.setValue(getSliderPctid(hf.getPctid()));
		pctidText.setText(getPctidString(pctidSlider.getValue()));

		sBlockCheck.setSelected(hf.isBlock());
		blockAndRadio.setSelected(hf.isBlockAnd());
		blockOrRadio.setSelected(hf.isBlockOr());
		blockOnlyCheck.setSelected(hf.isBlockOnly());
		blockOnlyText.setText(hf.getBlock()+"");
		
		sCsetCheck.setSelected(hf.isCset());
		sGene2Check.setSelected(hf.is2Gene());
		sGene1Check.setSelected(hf.is1Gene());
		sGene0Check.setSelected(hf.is0Gene());
		sAllCheck.setSelected(hf.isAll()); 
		
		hBlockRadio.setSelected(hf.isHiBlock());
		hCsetRadio.setSelected(hf.isHiCset());
		hGene2Radio.setSelected(hf.isHi2Gene());
		hGene1Radio.setSelected(hf.isHi1Gene());
		hGene0Radio.setSelected(hf.isHi0Gene());
		hNoneRadio.setSelected(hf.isHiNone());
		hPopupCheck.setSelected(hf.isHiPopup()); 
	}

	private HfilterData getCopyHitFilter() { // ok and refresh
		HfilterData hf = new HfilterData();	
		hf.setHiBlock(hBlockRadio.isSelected()); 
		
		hf.setHiCset(hCsetRadio.isSelected()); 
		hf.setHi2Gene(hGene2Radio.isSelected());
		hf.setHi1Gene(hGene1Radio.isSelected()); 
		hf.setHi0Gene(hGene0Radio.isSelected()); 
		hf.setHiNone(hNoneRadio.isSelected()); 
		hf.setHiPopup(hPopupCheck.isSelected()); 
		
		hf.setBlock(sBlockCheck.isSelected());
		hf.setBlockAnd(blockAndRadio.isSelected());
		hf.setBlockOr(blockOrRadio.isSelected());
		hf.setBlockOnly(blockOnlyCheck.isSelected()); 
		int n = Utilities.getInt(blockOnlyText.getText());
		if (n==-1) n=0;
		hf.setBlockNum(n);
		
		hf.setCset(sCsetCheck.isSelected());
		hf.set2Gene(sGene2Check.isSelected());
		hf.set1Gene(sGene1Check.isSelected());
		hf.set0Gene(sGene0Check.isSelected()); 
		
		hf.setAll(sAllCheck.isSelected()); 	
		
		hf.setPctid(getPctid(pctidSlider.getValue()));
		
		return hf;
	}

	private void setSliderMaxMin() {
		pctidSlider.setMinimum(getMinSliderPctid(hitFiltData.getMinPctid()));
		pctidSlider.setMaximum(getMaxSliderPctid(hitFiltData.getMaxPctid()));
		
		pctidSlider.setEnabled(true);
	}
	
	private class FilterListener implements ActionListener,  ChangeListener {
		private FilterListener() { }
		public void actionPerformed(ActionEvent event) { 
			Object src = event.getSource();
			
			if (src == okButton) { // changed already made
				okAction();
				setVisible(false); 
			}
			else if (src == cancelButton) {
				cancelAction();
				setVisible(false); 
			}
			else if (src == defaultButton) {
				setDefault();
			}
			else if (src == blockOnlyText) {
				int n = Utilities.getInt(blockOnlyText.getText());
				if (n>1) blockOnlyCheck.setSelected(true);
			}
			// set filter with popup value
			else if (src == hBlockPopRadio) hBlockRadio.setSelected(hBlockPopRadio.isSelected());
			else if (src == hCsetPopRadio) 	hCsetRadio.setSelected(hCsetPopRadio.isSelected()); 
			else if (src == hGene2PopRadio)	hGene2Radio.setSelected(hGene2PopRadio.isSelected());
			else if (src == hGene1PopRadio)	hGene1Radio.setSelected(hGene1PopRadio.isSelected());
			else if (src == hGene0PopRadio)	hGene0Radio.setSelected(hGene0PopRadio.isSelected());
			else if (src == hNonePopRadio)	hNoneRadio.setSelected(hNonePopRadio.isSelected());
			else if (src == hPopupPopCheck)	hPopupCheck.setSelected(hPopupPopCheck.isSelected()); 
			else if (src == sAllPopCheck)	sAllCheck.setSelected(sAllPopCheck.isSelected()); 
 			refresh();
		}
		
		public void stateChanged(ChangeEvent event) {
			if (mapper==null) return;
			
			double pctid = hitFiltData.getPctid();
			
			if (hasSize(pctidSlider)) {
				int xpctid = pctidSlider.getValue();
				
				if (xpctid != (int) pctid) {
					String pid = getPctidString(xpctid);
					pctidText.setText(pid);
					setSliderMaxMin();
					refresh();
				}
			}
		}
	} // end listener
	
	// called when popup become visible
	class MyPopupMenuListener implements PopupMenuListener {
		public void popupMenuCanceled(PopupMenuEvent popupMenuEvent) {}

		public void popupMenuWillBecomeInvisible(PopupMenuEvent popupMenuEvent) {}

		public void popupMenuWillBecomeVisible(PopupMenuEvent event) { 
			setInit(hitFiltData); 
			
			hBlockPopRadio.setSelected(hitFiltData.isHiBlock());
			hCsetPopRadio.setSelected(hitFiltData.isHiCset());
			hGene2PopRadio.setSelected(hitFiltData.isHi2Gene());
			hGene1PopRadio.setSelected(hitFiltData.isHi1Gene());
			hGene0PopRadio.setSelected(hitFiltData.isHi0Gene());
			hNonePopRadio.setSelected(hitFiltData.isHiNone());
			hPopupPopCheck.setSelected(hitFiltData.isHiPopup());
			sAllPopCheck.setSelected(hitFiltData.isAll());		
		}
	} // end popup listener
	private int     getSliderPctid(double pctid) 	{return (int)Math.round(pctid);}
	private boolean hasSize(JSlider slider) 		{return slider.getMaximum() > slider.getMinimum();}
	private double  getPctid(int slider) 			{return (double)slider;}
	private int     getMinSliderPctid(double min) 	{return (int)Math.floor(min);}
	private int     getMaxSliderPctid(double max) 	{return (int)Math.ceil(max);}
	private String  getPctidString(int slider) 		{return slider + "%";} 
}

