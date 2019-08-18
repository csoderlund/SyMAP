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
import symap.contig.Contig;

/**
 * The Mapper filter dialog implementation.
 * 
 * @author Austin Shoemaker
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class MapperFilter extends Filter {
	public static final boolean SHOW_NONREPETITIVE = false;
	private static final boolean SHOW_COLOR_STRAND_BOX = false;

	private static final String ANY = "Any";

	private JLabel markerLabel    = new JLabel("Marker Hits");
	private JLabel besLabel       = new JLabel("BES Hits");
	private JLabel fpLabel        = new JLabel("Fingerprint Hits");

	private JLabel mrkEvalueLabel = new JLabel("E-Value:");
	private JLabel mrkPctidLabel  = new JLabel("% Identity:");
	private JLabel besEvalueLabel = new JLabel("E-Value:");
	private JLabel besPctidLabel  = new JLabel("% Identity:");

	private JLabel mrkPctidText  = new JLabel("0%");
	private JLabel mrkEvalueText = new JLabel(ANY);

	private JLabel besPctidText  = new JLabel("0%");
	private JLabel besEvalueText = new JLabel(ANY);

	private JLabel fpEvalueLabel = new JLabel("E-Value:");
	private JLabel fpEvalueText  = new JLabel(ANY);

	private JCheckBox onlySharedBox = new JCheckBox("Show Only Shared Marker Hits");
	private JCheckBox joinDotBox    = new JCheckBox("Show Marker Join Dot");
	
	private JCheckBox mrkHideBox = new JCheckBox("Hide");
	private JCheckBox besHideBox = new JCheckBox("Hide");
	private JCheckBox fpHideBox  = new JCheckBox("Hide");

	private JCheckBox colorByStrandBox = new JCheckBox("Color strand information");

	private JSlider mrkEvalueSlider = new JSlider(JSlider.HORIZONTAL, 0, 200, 0);
	private JSlider mrkPctidSlider  = new JSlider(JSlider.HORIZONTAL, 0, 100, 0);
	private JSlider besEvalueSlider = new JSlider(JSlider.HORIZONTAL, 0, 200, 0);
	private JSlider besPctidSlider  = new JSlider(JSlider.HORIZONTAL, 0, 100, 0);
	private JSlider fpEvalueSlider  = new JSlider(JSlider.HORIZONTAL, 0, 200, 0); 

	private JRadioButton blockHitRadio	= new JRadioButton("Show Only Synteny Hits");
	private JRadioButton containedGeneHitRadio = new JRadioButton("Show Only Hits to Genes"); 	// mdb added 3/7/07 #101
	//private JRadioButton overlapGeneHitRadio = new JRadioButton("Show Only Gene Hits that Extend Gene >100"); // mdb added 3/7/07 #101 // mdb removed 4/9/08 #158
	private JRadioButton nonGeneHitRadio	= new JRadioButton("Show Only Non-Gene Hits"); 		// mdb added 3/7/07 #101
	private JRadioButton nonrepetitiveHitRadio = new JRadioButton("Show Only Nonrepetitive Hits");
	private JRadioButton allHitRadio    = new JRadioButton("Show All Hits");

	// mdb added 3/13/07 #104
	private JRadioButtonMenuItem blockHitPopupRadio	= new JRadioButtonMenuItem("Show Only Synteny Hits");
	private JRadioButtonMenuItem containedGeneHitPopupRadio = new JRadioButtonMenuItem("Show Only Hits to Genes");
	//private JRadioButtonMenuItem overlapGeneHitPopupRadio = new JRadioButtonMenuItem("Show Only Gene Hits that Extend Gene >100"); // mdb removed 4/9/08 #158
	private JRadioButtonMenuItem nonGeneHitPopupRadio	= new JRadioButtonMenuItem("Show Only Non-Gene Hits");
	private JRadioButtonMenuItem nonrepetitiveHitPopupRadio = new JRadioButtonMenuItem("Show Only Nonrepetitive Hits");
	private JRadioButtonMenuItem allHitPopupRadio    = new JRadioButtonMenuItem("Show All Hits");
	
	private Mapper mapper;
	private HitFilter hitfilter;
	private HitFilter myHitFilter;

	private int mapType = -1;

	private volatile boolean nochange = false;

	public MapperFilter(Frame owner, DrawingPanel dp, AbstractButton helpButton, Mapper map) {
		super(owner,dp,"Hit Filter",helpButton);
		this.mapper = map;
		this.hitfilter = mapper.getHitFilter();

		ButtonGroup buttonGroup = new ButtonGroup();
		buttonGroup.add(blockHitRadio);
		buttonGroup.add(containedGeneHitRadio); // mdb added 3/7/07 #101
		//buttonGroup.add(overlapGeneHitRadio); // mdb added 3/7/07 #101 // mdb removed 4/9/08 #158
		buttonGroup.add(nonGeneHitRadio); 		// mdb added 3/7/07 #101
		buttonGroup.add(nonrepetitiveHitRadio);
		buttonGroup.add(allHitRadio);

		ActionListener rbl = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (mapper != null && !nochange) {
					javax.swing.SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							setCursor(SyMAPConstants.WAIT_CURSOR);
							if (hitfilter.set(getHitFilter())) {
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
		blockHitRadio.addActionListener(rbl);
		containedGeneHitRadio.addActionListener(rbl); // mdb added 3/7/07 #101
		//overlapGeneHitRadio.addActionListener(rbl); // mdb added 3/7/07 #101 // mdb removed 4/9/08 #158
		nonGeneHitRadio.addActionListener(rbl); 	  // mdb added 3/7/07 #101
		nonrepetitiveHitRadio.addActionListener(rbl);
		allHitRadio.addActionListener(rbl);

		besPctidSlider.addChangeListener(this);
		mrkPctidSlider.addChangeListener(this);
		besEvalueSlider.addChangeListener(this);
		mrkEvalueSlider.addChangeListener(this);
		besHideBox.addChangeListener(this);
		mrkHideBox.addChangeListener(this);
		onlySharedBox.addChangeListener(this);
		joinDotBox.addChangeListener(this);
		fpHideBox.addChangeListener(this);
		fpEvalueSlider.addChangeListener(this);
		colorByStrandBox.addChangeListener(this);
		
		// mdb added 3/13/07 #104 -- BEGIN
		//popupTitle.setLabel("Hit Options:"); // mdb removed 7/2/07 #118
		popupTitle.setText("Hit Options:"); // mdb added 7/2/07 #118
		//SyMAP.enableHelpOnButton(showNavigationHelp,"hitcontrols"); // mdb removed 4/30/09 #162
		//SyMAP.enableHelpOnButton(showTrackHelp,""); 
		//popup.remove(showTrackHelp); // mdb removed 12/11/09 #162
		popup.add(blockHitPopupRadio); 
		popup.add(containedGeneHitPopupRadio); 
		//popup.add(overlapGeneHitPopupRadio); // mdb removed 4/9/08 #158 
		popup.add(nonGeneHitPopupRadio); 
		if (SHOW_NONREPETITIVE)
			popup.add(nonrepetitiveHitPopupRadio); 
		popup.add(allHitPopupRadio); 
		ActionListener prbl = new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				if (mapper != null) {				
					//javax.swing.SwingUtilities.invokeLater(new Runnable() {
						//public void run() {
							setCursor(SyMAPConstants.WAIT_CURSOR);
							if (event.getSource() == blockHitPopupRadio)
								blockHitRadio.setSelected(blockHitPopupRadio.isSelected());
							else if (event.getSource() == containedGeneHitPopupRadio)
								containedGeneHitRadio.setSelected(containedGeneHitPopupRadio.isSelected());
// mdb removed 4/9/08 #158							
//							else if (event.getSource() == overlapGeneHitPopupRadio)
//								overlapGeneHitRadio.setSelected(overlapGeneHitPopupRadio.isSelected());
							else if (event.getSource() == nonGeneHitPopupRadio)
								nonGeneHitRadio.setSelected(nonGeneHitPopupRadio.isSelected());
							else if (event.getSource() == nonrepetitiveHitPopupRadio)
								nonrepetitiveHitRadio.setSelected(nonrepetitiveHitPopupRadio.isSelected());
							else if (event.getSource() == allHitPopupRadio)
								allHitRadio.setSelected(allHitPopupRadio.isSelected());		
							if (hitfilter.set(getHitFilter())) {
								mapper.clearTrackBuild();
								if (drawingPanel != null) drawingPanel.smake();
							}
							setCursor(SyMAPConstants.DEFAULT_CURSOR);
						//}
					//});
				}
			}
		};
		blockHitPopupRadio.addActionListener(prbl);
		containedGeneHitPopupRadio.addActionListener(prbl);
		//overlapGeneHitPopupRadio.addActionListener(prbl); // mdb removed 4/9/08 #158
		nonGeneHitPopupRadio.addActionListener(prbl);
		nonrepetitiveHitPopupRadio.addActionListener(prbl);
		allHitPopupRadio.addActionListener(prbl);
		// mdb added 3/13/07 #104 -- END
	}

	public void show() {
		nochange = true;

		setSliderMaxMin();

		if (mapType != mapper.getMapType()) {
			mapType = mapper.getMapType();

			Container contentPane = getContentPane();
			GridBagLayout gridbag = new GridBagLayout();
			GridBagConstraints constraints = new GridBagConstraints();

			contentPane.removeAll();

			contentPane.setLayout(gridbag);
			constraints.fill = GridBagConstraints.HORIZONTAL;
			constraints.gridheight = 1;
			constraints.ipadx = 5;
			constraints.ipady = 8;

			if (mapType != Mapper.PSEUDO2PSEUDO) { // mdb added if 7/16/07 #121
			addToGrid(contentPane, gridbag, constraints, markerLabel, 1);
			addToGrid(contentPane, gridbag, constraints, new JLabel(), 1);
			addToGrid(contentPane, gridbag, constraints, mrkHideBox, GridBagConstraints.REMAINDER);
			}

			if (mapType == Mapper.FPC2PSEUDO) {
				addToGrid(contentPane, gridbag, constraints, mrkEvalueLabel, 1);
				addToGrid(contentPane, gridbag, constraints, mrkEvalueSlider, 1);
				addToGrid(contentPane, gridbag, constraints, mrkEvalueText, GridBagConstraints.REMAINDER);

				addToGrid(contentPane, gridbag, constraints, mrkPctidLabel, 1);
				addToGrid(contentPane, gridbag, constraints, mrkPctidSlider, 1);
				addToGrid(contentPane, gridbag, constraints, mrkPctidText, GridBagConstraints.REMAINDER);

				if (drawingPanel.getNumMaps() > 1)
					addToGrid(contentPane, gridbag, constraints, onlySharedBox, GridBagConstraints.REMAINDER);

				addToGrid(contentPane, gridbag, constraints, joinDotBox, GridBagConstraints.REMAINDER);
			}

			addToGrid(contentPane, gridbag, constraints, new JSeparator(), GridBagConstraints.REMAINDER);

			if (mapType == Mapper.FPC2PSEUDO) { // FPC to PSEUDO
				addToGrid(contentPane, gridbag, constraints, besLabel, 1);
				addToGrid(contentPane, gridbag, constraints, new JLabel(), 1);
				addToGrid(contentPane, gridbag, constraints, besHideBox, GridBagConstraints.REMAINDER);

				addToGrid(contentPane, gridbag, constraints, besEvalueLabel, 1);
				addToGrid(contentPane, gridbag, constraints, besEvalueSlider, 1);
				addToGrid(contentPane, gridbag, constraints, besEvalueText, GridBagConstraints.REMAINDER);

				addToGrid(contentPane, gridbag, constraints, besPctidLabel, 1);
				addToGrid(contentPane, gridbag, constraints, besPctidSlider, 1);
				addToGrid(contentPane, gridbag, constraints, besPctidText, GridBagConstraints.REMAINDER);
				
				if (SHOW_COLOR_STRAND_BOX) {
					addToGrid(contentPane, gridbag, constraints, new JSeparator(), GridBagConstraints.REMAINDER);
					addToGrid(contentPane, gridbag, constraints, colorByStrandBox, GridBagConstraints.REMAINDER);
				}
			}
			else if (mapType == Mapper.FPC2FPC) { // FPC to FPC
				addToGrid(contentPane, gridbag, constraints, fpLabel, 1);
				addToGrid(contentPane, gridbag, constraints, new JLabel(), 1);
				addToGrid(contentPane, gridbag, constraints, fpHideBox, GridBagConstraints.REMAINDER);

				addToGrid(contentPane, gridbag, constraints, fpEvalueLabel, 1);
				addToGrid(contentPane, gridbag, constraints, fpEvalueSlider, 1);
				addToGrid(contentPane, gridbag, constraints, fpEvalueText, GridBagConstraints.REMAINDER);
			}

			addToGrid(contentPane, gridbag, constraints, new JSeparator(), GridBagConstraints.REMAINDER);

			addToGrid(contentPane, gridbag, constraints, blockHitRadio, GridBagConstraints.REMAINDER);
			addToGrid(contentPane, gridbag, constraints, containedGeneHitRadio, GridBagConstraints.REMAINDER);	// mdb added 3/7/07 #101
			//addToGrid(contentPane, gridbag, constraints, overlapGeneHitRadio, GridBagConstraints.REMAINDER);	// mdb added 3/7/07 #101 // mdb removed 4/9/08 #158
			addToGrid(contentPane, gridbag, constraints, nonGeneHitRadio, GridBagConstraints.REMAINDER); 		// mdb added 3/7/07 #101
			if (SHOW_NONREPETITIVE)
				addToGrid(contentPane, gridbag, constraints, nonrepetitiveHitRadio, GridBagConstraints.REMAINDER);
			addToGrid(contentPane, gridbag, constraints, allHitRadio, GridBagConstraints.REMAINDER);

			addToGrid(contentPane, gridbag, constraints, buttonPanel, GridBagConstraints.REMAINDER);

			pack();
			setResizable(false);
		}

		joinDotBox.setEnabled(mapper.getTrack1() instanceof Contig 
							  || mapper.getTrack2() instanceof Contig);
		myHitFilter = hitfilter.copy();
		setInput(myHitFilter);
		nochange = false;
		super.show();
	}

	public String getHelpID() {
		return "sequencefilter";//Filter.SEQUENCE_FILTER_ID;
	}

	protected void setDefault() {
		setInput(new HitFilter());
	}

	public boolean canShow() {
		return mapper != null;
	}

	protected boolean okAction() throws Exception {
		return myHitFilter.set(getHitFilter());
	}

	protected void cancelAction() {
		setInput(myHitFilter);
	}

	public void stateChanged(ChangeEvent event) {
		if (mapper != null && !nochange) {
			if (hitfilter.set(getHitFilter())) {
				if (hasSize(besPctidSlider))
					besPctidText.setText(getPctidString(besPctidSlider.getValue()));
				if (hasSize(besEvalueSlider))
					besEvalueText.setText(getEvalueString(besEvalueSlider.getValue()));
				if (hasSize(mrkPctidSlider))
					mrkPctidText.setText(getPctidString(mrkPctidSlider.getValue()));
				if (hasSize(mrkEvalueSlider))
					mrkEvalueText.setText(getEvalueString(mrkEvalueSlider.getValue()));		
				if (hasSize(fpEvalueSlider))
					fpEvalueText.setText(getEvalueString(fpEvalueSlider.getValue()));
				nochange = true;
				setSliderMaxMin();
				nochange = false;
				mapper.clearTrackBuild();
				refresh();
			}
		}
	}
	
	public void popupMenuWillBecomeVisible(PopupMenuEvent event) { // mdb added 3/13/07 #104
		// Initialize radio item values based on filter values	
		blockHitPopupRadio.setSelected(hitfilter.getBlock());
		containedGeneHitPopupRadio.setSelected(hitfilter.getGeneContained());
		//overlapGeneHitPopupRadio.setSelected(hitfilter.getGeneOverlap()); // mdb removed 4/9/08 #158	
		nonGeneHitPopupRadio.setSelected(hitfilter.getNonGene());
		nonrepetitiveHitPopupRadio.setSelected(hitfilter.getNonRepetitive());
		allHitPopupRadio.setSelected(
			!hitfilter.getBlock() 			&& 
			!hitfilter.getGeneContained() 	&& 
			!hitfilter.getGeneOverlap() 	&& 
			!hitfilter.getNonGene() 		&&
			!hitfilter.getNonRepetitive());
	}
	
	private void setInput(HitFilter filter) {
		if (filter.getBlock() && !blockHitRadio.isSelected()) {
			blockHitRadio.setSelected(true);
			stateChanged(new ChangeEvent(blockHitRadio));
		}
		else if (filter.getGeneContained() && !containedGeneHitRadio.isSelected()) { // mdb added 3/7/07 #101
			containedGeneHitRadio.setSelected(true);
			stateChanged(new ChangeEvent(containedGeneHitRadio));
		}	
// mdb removed 4/9/08 #158		
//		else if (filter.getGeneOverlap() && !overlapGeneHitRadio.isSelected()) { // mdb added 3/7/07 #101
//			overlapGeneHitRadio.setSelected(true);
//			stateChanged(new ChangeEvent(overlapGeneHitRadio));
//		}
		else if (filter.getNonGene() && !nonGeneHitRadio.isSelected()) { // mdb added 3/7/07 #101
			nonGeneHitRadio.setSelected(true);
			stateChanged(new ChangeEvent(nonGeneHitRadio));
		}
		else if (filter.getNonRepetitive() && !nonrepetitiveHitRadio.isSelected()) {
			nonrepetitiveHitRadio.setSelected(true);
			stateChanged(new ChangeEvent(nonrepetitiveHitRadio));
		}
		else if (!filter.getBlock() 		&& 
				 !filter.getGeneContained() && // mdb added 3/7/07 #101
				 !filter.getGeneOverlap() 	&& // mdb added 3/7/07 #101
				 !filter.getNonGene() 		&& // mdb added 3/7/07 #101
				 !filter.getNonRepetitive() && 
				 !allHitRadio.isSelected()) 
		{ 
			allHitRadio.setSelected(true);
			stateChanged(new ChangeEvent(allHitRadio));
		}

		fpEvalueSlider.setValue(getSliderEvalue(filter.getFpEvalue()));
		mrkEvalueSlider.setValue(getSliderEvalue(filter.getMrkEvalue()));
		besEvalueSlider.setValue(getSliderEvalue(filter.getBesEvalue()));
		mrkPctidSlider.setValue(getSliderPctid(filter.getMrkPctid()));
		besPctidSlider.setValue(getSliderPctid(filter.getBesPctid()));

		fpEvalueText.setText(getEvalueString(fpEvalueSlider.getValue()));
		mrkEvalueText.setText(getEvalueString(mrkEvalueSlider.getValue()));
		besEvalueText.setText(getEvalueString(besEvalueSlider.getValue()));

		mrkPctidText.setText(getPctidString(mrkPctidSlider.getValue()));	
		besPctidText.setText(getPctidString(besPctidSlider.getValue()));	

		fpHideBox.setSelected(filter.getFpHide());
		mrkHideBox.setSelected(filter.getMrkHide());
		besHideBox.setSelected(filter.getBesHide());
		onlySharedBox.setSelected(filter.getOnlyShared());
		joinDotBox.setSelected(filter.getShowJoinDot());

		colorByStrandBox.setSelected(filter.getColorByStrand());
	}

	private HitFilter getHitFilter() {
		HitFilter hf = new HitFilter();
		hf.setMrkHide(mrkHideBox.isSelected());
		hf.setBesHide(besHideBox.isSelected());
		hf.setBesPctid(getPctid(besPctidSlider.getValue()));
		hf.setMrkPctid(getPctid(mrkPctidSlider.getValue()));
		hf.setBesEvalue(getEvalue(besEvalueSlider.getValue()));
		hf.setMrkEvalue(getEvalue(mrkEvalueSlider.getValue()));
		hf.setBlock(blockHitRadio.isSelected());
		hf.setNonRepetitive(nonrepetitiveHitRadio.isSelected());
		hf.setGeneContained(containedGeneHitRadio.isSelected());// mdb added 3/7/07 #101
		//hf.setGeneOverlap(overlapGeneHitRadio.isSelected()); 	// mdb added 3/7/07 #101 // mdb removed 4/9/08 #158
		hf.setNonGene(nonGeneHitRadio.isSelected()); 			// mdb added 3/7/07 #101
		hf.setShowJoinDot(joinDotBox.isSelected());
		hf.setOnlySharedHits(onlySharedBox.isSelected());

		hf.setFpHide(fpHideBox.isSelected());
		hf.setFpEvalue(getEvalue(fpEvalueSlider.getValue()));

		hf.setColorByStrand(colorByStrandBox.isSelected());

		return hf;
	}

	private void setSliderMaxMin() {
		if (mapper.getMapType() == Mapper.FPC2PSEUDO) {
			mrkEvalueSlider.setMinimum(getMinSliderEvalue(hitfilter.getMaxMrkEvalue()));
			mrkEvalueSlider.setMaximum(getMaxSliderEvalue(hitfilter.getMinMrkEvalue()));
			besEvalueSlider.setMinimum(getMinSliderEvalue(hitfilter.getMaxBesEvalue()));
			besEvalueSlider.setMaximum(getMaxSliderEvalue(hitfilter.getMinBesEvalue()));

			mrkPctidSlider.setMinimum(getMinSliderPctid(hitfilter.getMinMrkPctid()));
			mrkPctidSlider.setMaximum(getMaxSliderPctid(hitfilter.getMaxMrkPctid()));
			besPctidSlider.setMinimum(getMinSliderPctid(hitfilter.getMinBesPctid()));
			besPctidSlider.setMaximum(getMaxSliderPctid(hitfilter.getMaxBesPctid()));
		}
		else {
			fpEvalueSlider.setMinimum(getMinSliderEvalue(hitfilter.getMaxFpEvalue()));
			fpEvalueSlider.setMaximum(getMaxSliderEvalue(hitfilter.getMinFpEvalue()));
		}

		mrkEvalueSlider.setEnabled(hasSize(mrkEvalueSlider));
		besEvalueSlider.setEnabled(hasSize(besEvalueSlider));
		fpEvalueSlider.setEnabled(hasSize(fpEvalueSlider));
		mrkPctidSlider.setEnabled(hasSize(mrkPctidSlider));
		besPctidSlider.setEnabled(hasSize(besPctidSlider));
	}

	private boolean hasSize(JSlider slider) {
		return slider.getMaximum() > slider.getMinimum();
	}

	private int getSliderEvalue(double evalue) {
		return (int)Math.round(-(Math.log(evalue)/Math.log(10)));
	}

	private int getSliderPctid(double pctid) {
		return (int)Math.round(pctid);
	}

	private double getEvalue(int slider) {
		return Math.pow(10,-slider);
	}

	private double getPctid(int slider) {
		return (double)slider;
	}

	private int getMaxSliderEvalue(double min) {
		if (min <= 0) return 400;
		return (int)Math.ceil(-(Math.log(min)/Math.log(10)));
	}

	private int getMinSliderEvalue(double max) {
		return (int)Math.floor(-(Math.log(max)/Math.log(10)));
	}

	private int getMinSliderPctid(double min) {
		return (int)Math.floor(min);
	}

	private int getMaxSliderPctid(double max) {
		return (int)Math.ceil(max);
	}

	private String getPctidString(int slider) {
		return new Integer(slider).toString()+"%";
	}

	private String getEvalueString(int slider) {
		return "1 E-"+slider;
	} 
}

