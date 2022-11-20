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

import symap.SyMAPConstants;
import symap.drawingpanel.DrawingPanel;
import symap.filter.Filter;

/**
 * HitFilter Interface: The Mapper filter dialog implementation.
 * CAS520 remove all FPC stuff; fix bugs; rewrite to add collinear, highlights, and allow multiple shows
 * 
 * Many filters are created when the drawing panel is initiated
 * 
 * 1. Add filter here
 * 2. Add to HitFilter
 * 3. Add to PseudoPseudoHits.isHighLightHit() and isFiltered()
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class MapperFilter extends Filter {

	private final String SYN = "Synteny Blocks";
	private final String COL = "Collinear Sets";
	private final String HIT2 = "Hit =2 Genes";
	private final String HIT1 = "Hit =1 Gene";
	private final String HIT0 = "Hit =0 Genes";
	private final String ALL =  "All Hits";
	private final String NONE = "None";
// On Menu panel
	
	// highlight - only one can be selected
	private JRadioButton blockRadio		= new JRadioButton(SYN);
	private JRadioButton setRadio		= new JRadioButton(COL); 
	private JRadioButton gene2Radio 	= new JRadioButton(HIT2); 
	private JRadioButton gene1Radio 	= new JRadioButton(HIT1);
	private JRadioButton gene0Radio		= new JRadioButton(HIT0); 
	private JRadioButton noneRadio		= new JRadioButton(NONE);
	
	// Show - any number can be selected
	private JCheckBox blockCheck		= new JCheckBox(SYN);
	private JCheckBox setCheck			= new JCheckBox(COL);
	private JCheckBox gene2Check		= new JCheckBox(HIT2);
	private JCheckBox gene1Check		= new JCheckBox(HIT1);
	private JCheckBox gene0Check		= new JCheckBox(HIT0);
	private JCheckBox allCheck			= new JCheckBox(ALL);
	
	// Id
	private JSlider pctidSlider  = new JSlider(JSlider.HORIZONTAL, 0, 100, 0); // 0 to 100, start at 0
	private JLabel  pctidText  = new JLabel("0%");
	
// On popup menu
	private JRadioButtonMenuItem blockPopRadio	= new JRadioButtonMenuItem(SYN);
	private JRadioButtonMenuItem setPopRadio	= new JRadioButtonMenuItem(COL);// CAS520 add
	private JRadioButtonMenuItem gene2PopRadio 	= new JRadioButtonMenuItem(HIT2);
	private JRadioButtonMenuItem gene1PopRadio	= new JRadioButtonMenuItem(HIT1);
	private JRadioButtonMenuItem gene0PopRadio	= new JRadioButtonMenuItem(HIT0);
	private JRadioButtonMenuItem nonePopRadio	= new JRadioButtonMenuItem(NONE);
	
	private Mapper mapper;
	private HitFilter hitfilter;    // current settings
	private HitFilter lastHitFilter; // last settings

	private int mapType = -1;

	private volatile boolean nochange = false;

	public MapperFilter(Frame owner, DrawingPanel dp, AbstractButton helpButton, Mapper map) {
		super(owner,dp,"Hit Filter",helpButton);
		this.mapper = map;
		this.hitfilter = mapper.getHitFilter();
		
	// Menu panel
		ActionListener rbl = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (mapper != null && !nochange) {
					javax.swing.SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							setCursor(SyMAPConstants.WAIT_CURSOR);
							
							if (hitfilter.set(getCopyHitFilter())) {
								setSliderMaxMin();
								mapper.clearTrackBuild();
								refresh();
							}
							setCursor(SyMAPConstants.DEFAULT_CURSOR);
						}
					});
				}
			}
		};
		blockCheck.addActionListener(rbl); 
		setCheck.addActionListener(rbl);
		gene2Check.addActionListener(rbl);
		gene1Check.addActionListener(rbl);
		gene0Check.addActionListener(rbl);
		allCheck.addActionListener(rbl);
		
		blockRadio.addActionListener(rbl);
		setRadio.addActionListener(rbl);
		gene2Radio.addActionListener(rbl); 
		gene1Radio.addActionListener(rbl); 
		gene0Radio.addActionListener(rbl); 
		noneRadio.addActionListener(rbl); 
		
		ButtonGroup highGroup = new ButtonGroup();
		highGroup.add(blockRadio);
		highGroup.add(setRadio); 
		highGroup.add(gene2Radio); 
		highGroup.add(gene1Radio); 
		highGroup.add(gene0Radio); 	
		highGroup.add(noneRadio); 
		
		pctidSlider.addChangeListener(this); 
		
	// Popup menu
		popupTitle.setText("Hit Highlight options:"); // CAS503 
		popup.add(blockPopRadio); 
		popup.add(setPopRadio); 			
		popup.add(gene2PopRadio); 
		popup.add(gene1PopRadio);
		popup.add(gene0PopRadio);
		popup.add(nonePopRadio);
		
		ActionListener prbl = new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				if (mapper != null) {				
					setCursor(SyMAPConstants.WAIT_CURSOR);
					// transfer from popup to menu
					if (event.getSource() == blockPopRadio) 		blockRadio.setSelected(blockPopRadio.isSelected());
					else if (event.getSource() == setPopRadio) 		setRadio.setSelected(setPopRadio.isSelected()); // CAS520 add
					else if (event.getSource() == gene2PopRadio)	gene2Radio.setSelected(gene2PopRadio.isSelected());
					else if (event.getSource() == gene1PopRadio)	gene1Radio.setSelected(gene1PopRadio.isSelected());
					else if (event.getSource() == gene0PopRadio)	gene0Radio.setSelected(gene0PopRadio.isSelected());
					else if (event.getSource() == nonePopRadio)		noneRadio.setSelected(nonePopRadio.isSelected());
					
					if (hitfilter.set(getCopyHitFilter())) {
						mapper.clearTrackBuild();
						if (drawingPanel != null) drawingPanel.smake();
					}
					setCursor(SyMAPConstants.DEFAULT_CURSOR);
				}
			}
		};
		blockPopRadio.addActionListener(prbl);
		setPopRadio.addActionListener(prbl);
		gene2PopRadio.addActionListener(prbl);
		gene1PopRadio.addActionListener(prbl);	
		gene0PopRadio.addActionListener(prbl);	
		nonePopRadio.addActionListener(prbl);
	}
	// Creates panel
	public void showX() {
		nochange = true;
		
		if (mapType != mapper.getMapType()) {
			mapType = mapper.getMapType();
			
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
			addToGrid(contentPane, gridbag, c1, blockRadio, 1);
			addToGrid(contentPane, gridbag, c1, setRadio, GridBagConstraints.REMAINDER); 
			
			addToGrid(contentPane, gridbag, c1, gene2Radio, 1);
			addToGrid(contentPane, gridbag, c1, gene1Radio, GridBagConstraints.REMAINDER); 
			
			addToGrid(contentPane, gridbag, c1, gene0Radio, 1);
			addToGrid(contentPane, gridbag, c1, noneRadio, GridBagConstraints.REMAINDER); 
			
			addToGrid(contentPane, gridbag, c1, new JSeparator(), GridBagConstraints.REMAINDER);
			
			// show
			addToGrid(contentPane, gridbag, c1, new JLabel("  Show"), GridBagConstraints.REMAINDER);
			addToGrid(contentPane, gridbag, c1, blockCheck, 1);
			addToGrid(contentPane, gridbag, c1, setCheck, GridBagConstraints.REMAINDER);
			
			addToGrid(contentPane, gridbag, c1, gene2Check, 1); 
			addToGrid(contentPane, gridbag, c1, gene1Check, GridBagConstraints.REMAINDER); 
			
			addToGrid(contentPane, gridbag, c1, gene0Check, 1);
			addToGrid(contentPane, gridbag, c1, allCheck, GridBagConstraints.REMAINDER);
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
		}
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
		setInput(new HitFilter());
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
		blockPopRadio.setSelected(hitfilter.isHiBlock());
		setPopRadio.setSelected(hitfilter.isHiSet());// CAS520 add
		gene2PopRadio.setSelected(hitfilter.isHi2Gene());
		gene1PopRadio.setSelected(hitfilter.isHi1Gene());
		gene0PopRadio.setSelected(hitfilter.isHi0Gene());	
		nonePopRadio.setSelected(hitfilter.isHiNone());
	}
	/*****************************************
	 * set current filter values like the input filter; Cancel, Defaults and save lastHitFilter
	 * CAS520 rewrote because previous not working; FPC removed
	 */
	private void setInput(HitFilter hf) {	
		pctidSlider.setValue(getSliderPctid(hf.getPctid()));
		pctidText.setText(getPctidString(pctidSlider.getValue()));

		blockCheck.setSelected(hf.isBlock());
		setCheck.setSelected(hf.isSet());
		gene2Check.setSelected(hf.is2Gene());
		gene1Check.setSelected(hf.is1Gene());
		gene0Check.setSelected(hf.is0Gene());
		allCheck.setSelected(hf.isAllHit());
		
		blockRadio.setSelected(hf.isHiBlock());
		setRadio.setSelected(hf.isHiSet());
		gene2Radio.setSelected(hf.isHi2Gene());
		gene1Radio.setSelected(hf.isHi1Gene());
		gene0Radio.setSelected(hf.isHi0Gene());
		noneRadio.setSelected(hf.isHiNone());
		
		stateChanged(new ChangeEvent(blockRadio)); // event not used so does not matter
	}

	private HitFilter getCopyHitFilter() {
		HitFilter hf = new HitFilter();
		
		hf.setHiBlock(blockRadio.isSelected()); 
		hf.setHiSet(setRadio.isSelected()); 
		hf.setHi2Gene(gene2Radio.isSelected());
		hf.setHi1Gene(gene1Radio.isSelected()); 
		hf.setHi0Gene(gene0Radio.isSelected()); 
		hf.setHiNone(noneRadio.isSelected()); 
		
		// if changed on popup before view, then nothing is set for display
		if (!blockCheck.isSelected() && !setCheck.isSelected() && !gene2Check.isSelected() && !gene1Check.isSelected()
				                     && !gene0Check.isSelected() && !allCheck.isSelected()) {
			blockCheck.setSelected(true);
			hf.setBlock(true);
		}
		else {
			hf.setBlock(blockCheck.isSelected());
			hf.setSet(setCheck.isSelected());
			hf.set2Gene(gene2Check.isSelected());
			hf.set1Gene(gene1Check.isSelected());
			hf.set0Gene(gene0Check.isSelected()); 
			hf.setAllHit(allCheck.isSelected()); 
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

