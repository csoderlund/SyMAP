package symap.mapper;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Frame;
import java.awt.Container;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;

import javax.swing.AbstractButton;
import javax.swing.event.ChangeEvent;
import javax.swing.event.PopupMenuEvent;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.JRadioButton;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSeparator;

import symap.Globals;
import symap.drawingpanel.DrawingPanel;
import symap.filter.Filter;

/**
 * Hit Filter Interface: The Mapper filter dialog implementation.
 * CAS520 remove all FPC stuff; fix bugs; rewrite to add collinear, highlights, and allow multiple shows
 * 
 * Many filters are created when the drawing panel is initiated
 * 
 * 1. Add filter here
 * 2. Add to HitFilter
 * 3. Add to PseudoPseudoHits.isHighLightHit() and isFiltered()
 * CAS541 MapperFilter=>Mfilter
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class Hfilter extends Filter {

	private final String SYN = "Synteny Blocks";
	private final String COL = "Collinear Sets";
	private final String HIT2 = "Hit =2 Genes";
	private final String HIT1 = "Hit =1 Gene";
	private final String HIT0 = "Hit =0 Genes";
	private final String ALL =  "All Hits";
	private final String NONE = "None";
// On Menu panel
	
	// highlight - only one can be selected
	private JRadioButton hBlockRadio		= new JRadioButton(SYN);
	private JRadioButton hSetRadio		= new JRadioButton(COL); 
	private JRadioButton hGene2Radio 	= new JRadioButton(HIT2); 
	private JRadioButton hGene1Radio 	= new JRadioButton(HIT1);
	private JRadioButton hGene0Radio		= new JRadioButton(HIT0); 
	private JRadioButton hNoneRadio		= new JRadioButton(NONE);
	
	// Show - any number can be selected
	private JCheckBox sBlockCheck		= new JCheckBox(SYN);
	private JCheckBox sSetCheck			= new JCheckBox(COL);
	private JCheckBox sGene2Check		= new JCheckBox(HIT2);
	private JCheckBox sGene1Check		= new JCheckBox(HIT1);
	private JCheckBox sGene0Check		= new JCheckBox(HIT0);
	private JCheckBox sAllCheck			= new JCheckBox(ALL);
	
	// Id
	private JSlider pctidSlider  = new JSlider(JSlider.HORIZONTAL, 0, 100, 0); // 0 to 100, start at 0
	private JLabel  pctidText  = new JLabel("0%");
	
// On popup menu
	private JRadioButtonMenuItem hBlockPopRadio	= new JRadioButtonMenuItem(SYN);
	private JRadioButtonMenuItem hSetPopRadio	= new JRadioButtonMenuItem(COL);// CAS520 add
	private JRadioButtonMenuItem hGene2PopRadio 	= new JRadioButtonMenuItem(HIT2);
	private JRadioButtonMenuItem hGene1PopRadio	= new JRadioButtonMenuItem(HIT1);
	private JRadioButtonMenuItem hGene0PopRadio	= new JRadioButtonMenuItem(HIT0);
	private JRadioButtonMenuItem hGonePopRadio	= new JRadioButtonMenuItem(NONE);
	
	private Mapper mapper;
	private HfilterData hitfilter;    // current settings
	private HfilterData lastHitFilter; // last settings

	private volatile boolean nochange = false;

	public Hfilter(Frame owner, DrawingPanel dp, AbstractButton helpButton, Mapper map) {
		super(owner,dp,"Hit Filter", util.Jhtml.hitfilter);
		this.mapper = map;
		this.hitfilter = mapper.getHitFilter();
		
	// Menu panel
		ActionListener rbl = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (mapper != null && !nochange) {
					javax.swing.SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							setCursor(Globals.WAIT_CURSOR);
							
							if (hitfilter.set(getCopyHitFilter())) {
								setSliderMaxMin();
								mapper.clearTrackBuild();
								refresh();
							}
							setCursor(Globals.DEFAULT_CURSOR);
						}
					});
				}
			}
		};
		sBlockCheck.addActionListener(rbl); 
		sSetCheck.addActionListener(rbl);
		sGene2Check.addActionListener(rbl);
		sGene1Check.addActionListener(rbl);
		sGene0Check.addActionListener(rbl);
		sAllCheck.addActionListener(rbl);
		
		hBlockRadio.addActionListener(rbl);
		hSetRadio.addActionListener(rbl);
		hGene2Radio.addActionListener(rbl); 
		hGene1Radio.addActionListener(rbl); 
		hGene0Radio.addActionListener(rbl); 
		hNoneRadio.addActionListener(rbl); 
		
		ButtonGroup highGroup = new ButtonGroup();
		highGroup.add(hBlockRadio);
		highGroup.add(hSetRadio); 
		highGroup.add(hGene2Radio); 
		highGroup.add(hGene1Radio); 
		highGroup.add(hGene0Radio); 	
		highGroup.add(hNoneRadio); 
		
		pctidSlider.addChangeListener(this); 
		
	// Popup menu
		popupTitle.setText("Hit Highlight options:"); // CAS503 
		popup.add(hBlockPopRadio); 
		popup.add(hSetPopRadio); 			
		popup.add(hGene2PopRadio); 
		popup.add(hGene1PopRadio);
		popup.add(hGene0PopRadio);
		popup.add(hGonePopRadio);
		
		ActionListener prbl = new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				if (mapper != null) {				
					setCursor(Globals.WAIT_CURSOR);
					// transfer from popup to menu
					if (event.getSource() == hBlockPopRadio) 		hBlockRadio.setSelected(hBlockPopRadio.isSelected());
					else if (event.getSource() == hSetPopRadio) 		hSetRadio.setSelected(hSetPopRadio.isSelected()); // CAS520 add
					else if (event.getSource() == hGene2PopRadio)	hGene2Radio.setSelected(hGene2PopRadio.isSelected());
					else if (event.getSource() == hGene1PopRadio)	hGene1Radio.setSelected(hGene1PopRadio.isSelected());
					else if (event.getSource() == hGene0PopRadio)	hGene0Radio.setSelected(hGene0PopRadio.isSelected());
					else if (event.getSource() == hGonePopRadio)		hNoneRadio.setSelected(hGonePopRadio.isSelected());
					
					if (hitfilter.set(getCopyHitFilter())) {
						mapper.clearTrackBuild();
						if (drawingPanel != null) drawingPanel.smake();
					}
					setCursor(Globals.DEFAULT_CURSOR);
				}
			}
		};
		hBlockPopRadio.addActionListener(prbl);
		hSetPopRadio.addActionListener(prbl);
		hGene2PopRadio.addActionListener(prbl);
		hGene1PopRadio.addActionListener(prbl);	
		hGene0PopRadio.addActionListener(prbl);	
		hGonePopRadio.addActionListener(prbl);
	}
	// Creates panel
	public void showX() {
		nochange = true;
		
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
		addToGrid(contentPane, gridbag, c1, new JLabel("  Highlight"), GridBagConstraints.REMAINDER);
		addToGrid(contentPane, gridbag, c1, hBlockRadio, 1);
		addToGrid(contentPane, gridbag, c1, hSetRadio, GridBagConstraints.REMAINDER); 
		
		addToGrid(contentPane, gridbag, c1, hGene2Radio, 1);
		addToGrid(contentPane, gridbag, c1, hGene1Radio, GridBagConstraints.REMAINDER); 
		
		addToGrid(contentPane, gridbag, c1, hGene0Radio, 1);
		addToGrid(contentPane, gridbag, c1, hNoneRadio, GridBagConstraints.REMAINDER); 
		
		addToGrid(contentPane, gridbag, c1, new JSeparator(), GridBagConstraints.REMAINDER);
		
		// show
		addToGrid(contentPane, gridbag, c1, new JLabel("  Show"), GridBagConstraints.REMAINDER);
		addToGrid(contentPane, gridbag, c1, sBlockCheck, 1);
		addToGrid(contentPane, gridbag, c1, sSetCheck, GridBagConstraints.REMAINDER);
		
		addToGrid(contentPane, gridbag, c1, sGene2Check, 1); 
		addToGrid(contentPane, gridbag, c1, sGene1Check, GridBagConstraints.REMAINDER); 
		
		addToGrid(contentPane, gridbag, c1, sGene0Check, 1);
		addToGrid(contentPane, gridbag, c1, sAllCheck, GridBagConstraints.REMAINDER);
		addToGrid(contentPane, gridbag, c1, new JSeparator(), GridBagConstraints.REMAINDER);
		
		// %id
		setSliderMaxMin();
		addToGrid(contentPane, gridbag, c1, new JLabel("  Identity"), 1);
		addToGrid(contentPane, gridbag, c1, pctidText, 1);
		addToGrid(contentPane, gridbag, c1, pctidSlider, GridBagConstraints.REMAINDER);
		
		// buttons
		addToGrid(contentPane, gridbag, c1, buttonPanel, GridBagConstraints.REMAINDER);

		pack();
		setResizable(false);
		setLocationRelativeTo(null); // CAS520
		
		lastHitFilter = hitfilter.copy();
		setInput(lastHitFilter);
		nochange = false;
		super.setVisible(true); // CAS512 super.showX();
	}
		
	// abstract methods
	public String getHelpID() {return "sequencefilter";} //Filter.SEQUENCE_FILTER_ID;
	
	public boolean canShow() {return mapper != null;}

	protected boolean okAction() throws Exception {
		return lastHitFilter.set(getCopyHitFilter());
	}
	protected void cancelAction() {
		setInput(lastHitFilter);
	}
	protected void setDefault() { 
		setInput(new HfilterData());
	}
	
	public void stateChanged(ChangeEvent event) {
		if (mapper != null && !nochange) {
			if (hitfilter.set(getCopyHitFilter())) {
				if (hasSize(pctidSlider)) {
					String pid = getPctidString(pctidSlider.getValue());
					pctidText.setText(pid);
				}
				nochange = true;
				//setSliderMaxMin();
				nochange = false;
				mapper.clearTrackBuild();
				refresh();
			}
		}
	}
	
	// Initialize popup radio item values based on filter values	
	public void popupMenuWillBecomeVisible(PopupMenuEvent event) { 
		hBlockPopRadio.setSelected(hitfilter.isHiBlock());
		hSetPopRadio.setSelected(hitfilter.isHiSet());// CAS520 add
		hGene2PopRadio.setSelected(hitfilter.isHi2Gene());
		hGene1PopRadio.setSelected(hitfilter.isHi1Gene());
		hGene0PopRadio.setSelected(hitfilter.isHi0Gene());	
		hGonePopRadio.setSelected(hitfilter.isHiNone());
	}
	/*****************************************
	 * set current filter values like the input filter; Cancel, Defaults and save lastHitFilter
	 * CAS520 rewrote because previous not working; FPC removed
	 */
	private void setInput(HfilterData hf) {	
		pctidSlider.setValue(getSliderPctid(hf.getPctid()));
		pctidText.setText(getPctidString(pctidSlider.getValue()));

		sBlockCheck.setSelected(hf.isBlock());
		sSetCheck.setSelected(hf.isSet());
		sGene2Check.setSelected(hf.is2Gene());
		sGene1Check.setSelected(hf.is1Gene());
		sGene0Check.setSelected(hf.is0Gene());
		sAllCheck.setSelected(hf.isAllHit());
		
		hBlockRadio.setSelected(hf.isHiBlock());
		hSetRadio.setSelected(hf.isHiSet());
		hGene2Radio.setSelected(hf.isHi2Gene());
		hGene1Radio.setSelected(hf.isHi1Gene());
		hGene0Radio.setSelected(hf.isHi0Gene());
		hNoneRadio.setSelected(hf.isHiNone());
		
		stateChanged(new ChangeEvent(hBlockRadio)); // event not used so does not matter
	}

	private HfilterData getCopyHitFilter() {
		HfilterData hf = new HfilterData();
		
		hf.setHiBlock(hBlockRadio.isSelected()); 
		hf.setHiSet(hSetRadio.isSelected()); 
		hf.setHi2Gene(hGene2Radio.isSelected());
		hf.setHi1Gene(hGene1Radio.isSelected()); 
		hf.setHi0Gene(hGene0Radio.isSelected()); 
		hf.setHiNone(hNoneRadio.isSelected()); 
		
		// if changed on popup before view, then nothing is set for display
		if (!sBlockCheck.isSelected() && !sSetCheck.isSelected()   && !sAllCheck.isSelected() &&
			!sGene2Check.isSelected() && !sGene1Check.isSelected() && !sGene0Check.isSelected()) 
		{
			sBlockCheck.setSelected(true); // CAS541 FIXME - does not work right when regions selected
			hf.setBlock(true);
		}
		else {
			hf.setBlock(sBlockCheck.isSelected());
			hf.setSet(sSetCheck.isSelected());
			hf.set2Gene(sGene2Check.isSelected());
			hf.set1Gene(sGene1Check.isSelected());
			hf.set0Gene(sGene0Check.isSelected()); 
			hf.setAllHit(sAllCheck.isSelected()); 
		}
		
		hf.setPctid(getPctid(pctidSlider.getValue()));
		return hf;
	}

	private void setSliderMaxMin() {
		pctidSlider.setMinimum(getMinSliderPctid(hitfilter.getMinPctid()));
		pctidSlider.setMaximum(getMaxSliderPctid(hitfilter.getMaxPctid()));
		
		pctidSlider.setEnabled(true);
	}
	private int getSliderPctid(double pctid) {return (int)Math.round(pctid);}
	private boolean hasSize(JSlider slider) {return slider.getMaximum() > slider.getMinimum();}
	private double getPctid(int slider) {return (double)slider;}
	private int getMinSliderPctid(double min) {return (int)Math.floor(min);}
	private int getMaxSliderPctid(double max) {return (int)Math.ceil(max);}
	private String getPctidString(int slider) {return slider + "%";} // CAS512 new Integer(slider).toString()+"%";
}

