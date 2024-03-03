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
import java.awt.Insets;
import java.awt.GridBagConstraints;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSlider;
import javax.swing.JRadioButton;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSeparator;

import symap.drawingpanel.DrawingPanel;
import util.Jhtml;

/**
 * Hit Filter Interface: The Mapper filter dialog implementation.
 * CAS520 remove all FPC stuff; fix bugs; rewrite to add collinear, highlights, and allow multiple shows
 * 
 * Many filters are created when the drawing panel is initiated
 * 
 * 1. Add filter here
 * 2. Add to HfilterData
 * 3. Add to PseudoPseudoHits.isHighLightHit() and isFiltered()
 * CAS541 MapperFilter=>Hfilter; CAS542 replace abstract filter with in file methods
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class Hfilter extends JDialog {
	private final String SYN = "Synteny blocks";
	private final String COL = "Collinear sets";
	private final String HIT2 = "Hit =2 genes";
	private final String HIT1 = "Hit =1 gene";
	private final String HIT0 = "Hit =0 genes";
	private final String ALL =  "All hits";
	private final String POPUP ="Hit popup";	// CAS543 stay selected when the hitwire gets a popup
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
	private JCheckBox sPopupCheck	= new JCheckBox(POPUP); // CAS545 change from Radio
	
	// Show - any number can be selected
	private JCheckBox sBlockCheck		= new JCheckBox(SYN);
	private JCheckBox sCsetCheck		= new JCheckBox(COL);
	private JCheckBox sGene2Check		= new JCheckBox(HIT2);
	private JCheckBox sGene1Check		= new JCheckBox(HIT1);
	private JCheckBox sGene0Check		= new JCheckBox(HIT0);
	private JCheckBox sAllCheck			= new JCheckBox(ALL);
	
	// Id
	private JSlider pctidSlider  = new JSlider(JSlider.HORIZONTAL, 0, 100, 0); // 0 to 100, start at 0
	private JLabel  pctidText    = new JLabel("0%");
	
// On popup menu
	private JRadioButtonMenuItem hBlockPopRadio	= new JRadioButtonMenuItem(SYN);
	private JRadioButtonMenuItem hCsetPopRadio	= new JRadioButtonMenuItem(COL);// CAS520 add
	private JRadioButtonMenuItem hGene2PopRadio = new JRadioButtonMenuItem(HIT2);
	private JRadioButtonMenuItem hGene1PopRadio	= new JRadioButtonMenuItem(HIT1);
	private JRadioButtonMenuItem hGene0PopRadio	= new JRadioButtonMenuItem(HIT0);
	private JRadioButtonMenuItem hNonePopRadio	= new JRadioButtonMenuItem(NONE);
	private JRadioButtonMenuItem hPopupPopRadio	= new JRadioButtonMenuItem(POPUP);
	
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
		
	/* Filter dialog */
		// Buttons
		okButton = createButton("Save","Save changes and close");
		okButton.addActionListener(listener);

		cancelButton = createButton("Cancel", "Discard changes and close");
		cancelButton.addActionListener(listener);

		defaultButton = createButton("Defaults", "Reset to defaults");
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
		JPanel sliderPanel = new JPanel(new BorderLayout());
		sliderPanel.add(new JSeparator(),BorderLayout.NORTH);
		JPanel slider = new JPanel();
		slider.add(new JLabel("  Identity   "));
		slider.add(pctidText);
		slider.add(pctidSlider);
		sliderPanel.add(slider,BorderLayout.WEST);
		pctidSlider.addChangeListener(listener); 
		
		// Check boxes
		sBlockCheck.addActionListener(listener); 
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
		
		sPopupCheck.addActionListener(listener);
		
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
		addToGrid(contentPane, gridbag, c1, hCsetRadio,rem); 
		
		addToGrid(contentPane, gridbag, c1, hGene2Radio, 1);
		addToGrid(contentPane, gridbag, c1, hGene1Radio,rem); 
		
		addToGrid(contentPane, gridbag, c1, hGene0Radio, 1); 
		addToGrid(contentPane, gridbag, c1, hNoneRadio,rem); 
		
		addToGrid(contentPane, gridbag, c1, sPopupCheck, rem);
		addToGrid(contentPane, gridbag, c1, new JSeparator(),rem);
		
		// show
		addToGrid(contentPane, gridbag, c1, new JLabel("  Show"),rem);
		addToGrid(contentPane, gridbag, c1, sBlockCheck, 1);
		addToGrid(contentPane, gridbag, c1, sCsetCheck,rem);
		
		addToGrid(contentPane, gridbag, c1, sGene2Check, 1); 
		addToGrid(contentPane, gridbag, c1, sGene1Check,rem); 
		
		addToGrid(contentPane, gridbag, c1, sGene0Check, 1);
		addToGrid(contentPane, gridbag, c1, sAllCheck,rem);
		
		// %id
		addToGrid(contentPane, gridbag, c1, sliderPanel,rem);
		
		// buttons
		addToGrid(contentPane, gridbag, c1, buttonPanel,rem);

		setBackground(Color.white);
		pack();
		setResizable(false);
		setLocationRelativeTo(null); // CAS520
		
	// Popup menu
		popupMenu = new JPopupMenu(); 
		popupMenu.setBackground(Color.white);
		popupMenu.addPopupMenuListener(new MyPopupMenuListener()); 
		popupTitle = new JMenuItem();
		popupTitle.setEnabled(false);
		popupMenu.add(popupTitle);
		popupMenu.addSeparator();
		
		popupTitle.setText("Hit Highlight Options"); // CAS503 
		popupMenu.add(hBlockPopRadio); 
		popupMenu.add(hCsetPopRadio); 			
		popupMenu.add(hGene2PopRadio); 
		popupMenu.add(hGene1PopRadio);
		popupMenu.add(hGene0PopRadio);
		popupMenu.add(hNonePopRadio);
		popupMenu.add(new JSeparator());
		popupMenu.add(hPopupPopRadio);
		
		hBlockPopRadio.addActionListener(listener);
		hCsetPopRadio.addActionListener(listener);
		hGene2PopRadio.addActionListener(listener);
		hGene1PopRadio.addActionListener(listener);	
		hGene0PopRadio.addActionListener(listener);	
		hPopupPopRadio.addActionListener(listener);
		hNonePopRadio.addActionListener(listener);
	}
	// Creates panel
	public void showX() {
		setInit(hitFiltData); // CAS542 fixed display bug; must be before setSliderMaxMin; may have changed since Hfilter created
		setSliderMaxMin();
		lastHitFiltData = hitFiltData.copy("Hfilter showX");
		
		setVisible(true); // CAS512 super.showX();
	}
	public void closeFilter() {
		if (isShowing()) {
			cancelAction();
			setVisible(false); // CAS512 hide();
		}
	}
	public void showPopup(MouseEvent e) { 
		popupMenu.show(e.getComponent(), e.getX(), e.getY());
	}
	public boolean canShow() {return mapper!=null;}
	
	private void addToGrid(Container cp, GridBagLayout layout, GridBagConstraints con, Component comp, int w) {
		con.gridwidth = w;
		layout.setConstraints(comp, con);
		cp.add(comp);
	}
	private JButton createButton(String s, String t) {
		JButton jbutton = new JButton(s);
		jbutton.setMargin(new Insets(1,3,1,3));
		jbutton.setToolTipText(t);
		jbutton.setBackground(Color.white);
		return jbutton;
	}
	
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
			drawingPanel.setUpdateHistory();
			drawingPanel.smake();
			mapper.update();
		}
	}
	
	/*****************************************
	 * set current filter values like the input filter; Cancel, Defaults and save lastHitFilter
	 * CAS520 rewrote because previous not working; FPC removed
	 */
	private void setInit(HfilterData hf) {	
		pctidSlider.setValue(getSliderPctid(hf.getPctid()));
		pctidText.setText(getPctidString(pctidSlider.getValue()));

		sBlockCheck.setSelected(hf.isBlock());
		sCsetCheck.setSelected(hf.isCset());
		sGene2Check.setSelected(hf.is2Gene());
		sGene1Check.setSelected(hf.is1Gene());
		sGene0Check.setSelected(hf.is0Gene());
		sAllCheck.setSelected(hf.isAllHit());
		
		hBlockRadio.setSelected(hf.isHiBlock());
		hCsetRadio.setSelected(hf.isHiCset());
		hGene2Radio.setSelected(hf.isHi2Gene());
		hGene1Radio.setSelected(hf.isHi1Gene());
		hGene0Radio.setSelected(hf.isHi0Gene());
		hNoneRadio.setSelected(hf.isHiNone());
		sPopupCheck.setSelected(hf.isHiPopup()); // CAS544 had wrong init
	}

	private HfilterData getCopyHitFilter() { // ok and refresh
		HfilterData hf = new HfilterData();
// CAS542 was checking for no selections here		
		hf.setHiBlock(hBlockRadio.isSelected()); 
		hf.setHiCset(hCsetRadio.isSelected()); 
		hf.setHi2Gene(hGene2Radio.isSelected());
		hf.setHi1Gene(hGene1Radio.isSelected()); 
		hf.setHi0Gene(hGene0Radio.isSelected()); 
		hf.setHiNone(hNoneRadio.isSelected()); 
		hf.setHiPopup(sPopupCheck.isSelected()); 
		
		hf.setBlock(sBlockCheck.isSelected());
		hf.setCset(sCsetCheck.isSelected());
		hf.set2Gene(sGene2Check.isSelected());
		hf.set1Gene(sGene1Check.isSelected());
		hf.set0Gene(sGene0Check.isSelected()); 
		hf.setAllHit(sAllCheck.isSelected()); 
		
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
			else if (src == hBlockPopRadio) hBlockRadio.setSelected(hBlockPopRadio.isSelected());
			else if (src == hCsetPopRadio) 	hCsetRadio.setSelected(hCsetPopRadio.isSelected()); // CAS520 add
			else if (src == hGene2PopRadio)	hGene2Radio.setSelected(hGene2PopRadio.isSelected());
			else if (src == hGene1PopRadio)	hGene1Radio.setSelected(hGene1PopRadio.isSelected());
			else if (src == hGene0PopRadio)	hGene0Radio.setSelected(hGene0PopRadio.isSelected());
			else if (src == hNonePopRadio)	hNoneRadio.setSelected(hNonePopRadio.isSelected());
			else if (src == hPopupPopRadio)	sPopupCheck.setSelected(hPopupPopRadio.isSelected());
			
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
			setInit(hitFiltData); // CAS542 fixed DotPlot to 2D bug; may have changed since Hfilter created
			
			hBlockPopRadio.setSelected(hitFiltData.isHiBlock());
			hCsetPopRadio.setSelected(hitFiltData.isHiCset());// CAS520 add
			hGene2PopRadio.setSelected(hitFiltData.isHi2Gene());
			hGene1PopRadio.setSelected(hitFiltData.isHi1Gene());
			hGene0PopRadio.setSelected(hitFiltData.isHi0Gene());
			hNonePopRadio.setSelected(hitFiltData.isHiNone());
			hPopupPopRadio.setSelected(hitFiltData.isHiPopup());
		}
	} // end popup listener
	private int     getSliderPctid(double pctid) 	{return (int)Math.round(pctid);}
	private boolean hasSize(JSlider slider) 		{return slider.getMaximum() > slider.getMinimum();}
	private double  getPctid(int slider) 			{return (double)slider;}
	private int     getMinSliderPctid(double min) 	{return (int)Math.floor(min);}
	private int     getMaxSliderPctid(double max) 	{return (int)Math.ceil(max);}
	private String  getPctidString(int slider) 		{return slider + "%";} 
}

