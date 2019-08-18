package symap.sequence;

import java.awt.Container;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem; // mdb added 3/12/07 #104
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JMenuItem; // mdb added 3/23/07 #104
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.PopupMenuEvent; // mdb added 3/13/07 #104

import number.GenomicsNumber;
import symap.drawingpanel.DrawingPanel;
import symap.filter.Filter;
import util.Utilities;

/**
 * The filter dialog for the Sequence view.
 * 
 * @author Austin Shoemaker
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class SequenceFilter extends Filter {	
	private static final String DEFAULT_ENDS_UNIT = GenomicsNumber.KB;

	private JLabel startLabel;
	private JLabel endLabel;

	private JTextField startText;
	private JTextField endText;

	private JComboBox startCombo;
	private JComboBox endCombo;

	private JRadioButton geneFullRadio;
	private JRadioButton geneMidRadio;

	private JCheckBox frameCheck, geneCheck;
	private JCheckBox gapCheck, centromereCheck;
	private JCheckBox rulerCheck, annotCheck;
	private JCheckBox scoreLineCheck, scoreValueCheck; 	// mdb added 2/22/07 #100
	private JCheckBox ribbonCheck; 						// mdb added 8/7/07 #126
	private JCheckBox flippedCheck; 					// mdb added 7/23/07 #132
	
	private JCheckBoxMenuItem flippedPopupCheck; 						// mdb added 7/23/07 #132
	private JCheckBoxMenuItem framePopupCheck, genePopupCheck; 			// mdb added 3/12/07 #104
	private JCheckBoxMenuItem gapPopupCheck, centromerePopupCheck; 		// mdb added 3/12/07 #104
	private JCheckBoxMenuItem rulerPopupCheck, annotPopupCheck; 		// mdb added 3/12/07 #104
	private JCheckBoxMenuItem scoreLinePopupCheck, scoreValuePopupCheck;// mdb added 3/12/07 #104
	private JCheckBoxMenuItem ribbonPopupCheck; 						// mdb added 8/7/07 #126
	private JMenuItem fullSequencePopupItem; 							// mdb added 3/12/07 #104

	private JButton fullButton;

	private Sequence sequence;

	private String startStr, endStr;
	private int startInd, endInd;
	private boolean frame, gene, gap, centromere, ruler, annot, geneFull;
	private boolean flipped; 				// mdb added 7/23/07 #132
	private boolean scoreLine, scoreValue; 	// mdb added 2/22/07 #100
	private boolean ribbon; 				// mdb added 8/7/07 #126
	private boolean noChange = false;

	public SequenceFilter(Frame owner, DrawingPanel dp, AbstractButton helpButton, Sequence sequence) {
		super(owner,dp,"Sequence Filter",helpButton);
		this.sequence = sequence;

		Container contentPane = getContentPane();
		GridBagLayout gridbag = new GridBagLayout();
		GridBagConstraints constraints = new GridBagConstraints();

		fullButton = new JButton("Full Sequence");
		fullButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setFullSequence();
			}
		});

		startLabel = new JLabel("Start:");
		endLabel = new JLabel("End:");

		startText = new JTextField("",15);
		endText = new JTextField("",15);

		startCombo = new JComboBox(GenomicsNumber.ABS_UNITS);
		endCombo = new JComboBox(GenomicsNumber.ABS_UNITS);

		startCombo.setSelectedItem(DEFAULT_ENDS_UNIT);
		endCombo.setSelectedItem(DEFAULT_ENDS_UNIT);

		startInd = startCombo.getSelectedIndex();
		endInd = startInd;
		
		flippedCheck = new JCheckBox("Flip"); 				// mdb added 7/23/07 #132
		flippedCheck.setSelected(Sequence.DEFAULT_FLIPPED); // mdb added 7/23/07 #132
		
		frameCheck = new JCheckBox("Show Framework Markers");
		geneCheck = new JCheckBox("Show Genes");
		gapCheck = new JCheckBox("Show Gaps");
		centromereCheck = new JCheckBox("Show Centromere");
		rulerCheck = new JCheckBox("Show Ruler");
		annotCheck = new JCheckBox("Show Description for Annotations");

		frameCheck.setSelected(Sequence.DEFAULT_SHOW_FRAME && frameCheck.isEnabled());
		geneCheck.setSelected(Sequence.DEFAULT_SHOW_GENE && geneCheck.isEnabled());
		gapCheck.setSelected(Sequence.DEFAULT_SHOW_GAP && gapCheck.isEnabled());
		centromereCheck.setSelected(Sequence.DEFAULT_SHOW_CENTROMERE && centromereCheck.isEnabled());
		rulerCheck.setSelected(Sequence.DEFAULT_SHOW_RULER);
		annotCheck.setSelected(Sequence.DEFAULT_SHOW_ANNOT  && annotCheck.isEnabled()); 
		
		scoreLineCheck = new JCheckBox("Show Hit Score Bar");			// mdb added 2/22/07 #100
		scoreLineCheck.setSelected(Sequence.DEFAULT_SHOW_SCORE_LINE);	// mdb added 2/22/07 #100

		scoreValueCheck = new JCheckBox("Show Hit Score Value");		// mdb added 3/14/07 #100
		scoreValueCheck.setSelected(Sequence.DEFAULT_SHOW_SCORE_VALUE);	// mdb added 3/14/07 #100
		
		ribbonCheck = new JCheckBox("Show Hit Length");			// mdb added 8/7/07 #126
		ribbonCheck.setSelected(Sequence.DEFAULT_SHOW_RIBBON);	// mdb added 8/7/07 #126
		
		geneFullRadio = new JRadioButton("Length");
		geneMidRadio = new  JRadioButton("Midpoint");
		ButtonGroup geneBG = new ButtonGroup();
		geneBG.add(geneFullRadio);
		geneBG.add(geneMidRadio);

		JPanel geneRadioPanel = new JPanel();
		//geneRadioPanel.setBorder(LineBorder.createBlackLineBorder());
		geneRadioPanel.add(new JLabel("By:"));
		geneRadioPanel.add(geneFullRadio);
		geneRadioPanel.add(geneMidRadio);

		if (Sequence.DEFAULT_SHOW_GENE_FULL) geneFullRadio.setSelected(true);
		else geneMidRadio.setSelected(true);

		if (!Sequence.DEFAULT_SHOW_GENE) {
			geneFullRadio.setEnabled(false);
			geneMidRadio.setEnabled(false);
		}
		
		contentPane.setLayout(gridbag);
		constraints.gridheight = 1;
		constraints.ipadx = 5;
		constraints.ipady = 8;
		constraints.anchor = GridBagConstraints.WEST;
		constraints.fill = GridBagConstraints.HORIZONTAL;

		addToGrid(contentPane, gridbag, constraints, startLabel, 1);
		addToGrid(contentPane, gridbag, constraints, startText, 1);
		addToGrid(contentPane, gridbag, constraints, startCombo, 1);
		addToGrid(contentPane, gridbag, constraints, fullButton, GridBagConstraints.REMAINDER);
		addToGrid(contentPane, gridbag, constraints, new JLabel(), GridBagConstraints.REMAINDER);
		addToGrid(contentPane, gridbag, constraints, endLabel, 1);
		addToGrid(contentPane, gridbag, constraints, endText, 1);
		addToGrid(contentPane, gridbag, constraints, endCombo, 1);
		addToGrid(contentPane, gridbag, constraints, flippedCheck, GridBagConstraints.REMAINDER); // mdb added 7/23/07 #132
		
		addToGrid(contentPane, gridbag, constraints, new JSeparator(), GridBagConstraints.REMAINDER);
		addToGrid(contentPane, gridbag, constraints, geneCheck, 2);
		constraints.fill = GridBagConstraints.NONE;
		addToGrid(contentPane, gridbag, constraints, geneRadioPanel, GridBagConstraints.REMAINDER);
		constraints.fill = GridBagConstraints.HORIZONTAL;
		addToGrid(contentPane, gridbag, constraints, frameCheck, GridBagConstraints.REMAINDER);
		addToGrid(contentPane, gridbag, constraints, gapCheck, GridBagConstraints.REMAINDER);
		addToGrid(contentPane, gridbag, constraints, centromereCheck, GridBagConstraints.REMAINDER);

		addToGrid(contentPane, gridbag, constraints, new JSeparator(), GridBagConstraints.REMAINDER);
		addToGrid(contentPane, gridbag, constraints, annotCheck, GridBagConstraints.REMAINDER);
		addToGrid(contentPane, gridbag, constraints, rulerCheck, GridBagConstraints.REMAINDER);
		addToGrid(contentPane, gridbag, constraints, ribbonCheck, GridBagConstraints.REMAINDER);     // mdb added 8/7/07 #126
		addToGrid(contentPane, gridbag, constraints, scoreLineCheck, GridBagConstraints.REMAINDER);  // mdb added 2/22/07 #100
		addToGrid(contentPane, gridbag, constraints, scoreValueCheck, GridBagConstraints.REMAINDER); // mdb added 3/14/07 #100

		addToGrid(contentPane, gridbag, constraints, buttonPanel, GridBagConstraints.REMAINDER);

		pack();
		setResizable(false);
		
		geneFullRadio.addChangeListener(this);
		geneMidRadio.addChangeListener(this);
		flippedCheck.addChangeListener(this);		// mdb added 7/23/07 #132
		frameCheck.addChangeListener(this);
		geneCheck.addChangeListener(this);
		ribbonCheck.addChangeListener(this);		// mdb added 8/7/07 #126
		gapCheck.addChangeListener(this);
		centromereCheck.addChangeListener(this);
		rulerCheck.addChangeListener(this);
		annotCheck.addChangeListener(this);
		scoreLineCheck.addChangeListener(this);		// mdb added 2/22/07 #100
		scoreValueCheck.addChangeListener(this);	// mdb added 3/14/07 #100
		
		// mdb added 3/12/07 #104 -- BEGIN		
		//popupTitle.setLabel("Sequence Options:"); // mdb removed 7/2/07 #118
		popupTitle.setText("Sequence Options:"); // mdb added 7/2/07 #118
		//SyMAP.enableHelpOnButton(showNavigationHelp,"sequencecontrols"); // mdb removed 4/30/09 #162
		//SyMAP.enableHelpOnButton(showTrackHelp,"sequencetrack"); // mdb removed 4/30/09 #162
		flippedPopupCheck = new JCheckBoxMenuItem("Flip"); // mdb added 7/23/07 #132
		fullSequencePopupItem = new JMenuItem("Show Full Sequence");
		framePopupCheck = new JCheckBoxMenuItem("Show Framework Markers"); 
		genePopupCheck = new JCheckBoxMenuItem("Show Genes"); 
		ribbonPopupCheck = new JCheckBoxMenuItem("Show Hit Length"); // mdb added 8/7/07 #126
		gapPopupCheck = new JCheckBoxMenuItem("Show Gaps"); 
		centromerePopupCheck = new JCheckBoxMenuItem("Show Centromere"); 
		rulerPopupCheck = new JCheckBoxMenuItem("Show Ruler"); 
		annotPopupCheck = new JCheckBoxMenuItem("Show Annotation Descriptions");
		scoreLinePopupCheck = new JCheckBoxMenuItem("Show Hit Score Bar"); 		
		scoreValuePopupCheck = new JCheckBoxMenuItem("Show Hit Score Value"); 	
		popup.add(fullSequencePopupItem);
		popup.add(flippedPopupCheck); // mdb added 7/23/07 #132
		popup.add(framePopupCheck); 
		popup.add(genePopupCheck);
		popup.add(gapPopupCheck); 
		popup.add(centromerePopupCheck); 
		popup.add(annotPopupCheck);
		popup.add(rulerPopupCheck);
		popup.add(ribbonPopupCheck); // mdb added 8/7/07 #126
		popup.add(scoreLinePopupCheck);
		popup.add(scoreValuePopupCheck);
		fullSequencePopupItem.addActionListener(this);
		flippedPopupCheck.addActionListener(this); // mdb added 8/2/07 #132
		framePopupCheck.addActionListener(this);
		genePopupCheck.addActionListener(this);
		gapPopupCheck.addActionListener(this);
		centromerePopupCheck.addActionListener(this);
		rulerPopupCheck.addActionListener(this);
		annotPopupCheck.addActionListener(this);
		scoreLinePopupCheck.addActionListener(this);
		scoreValuePopupCheck.addActionListener(this);
		ribbonPopupCheck.addActionListener(this); // mdb added 8/7/07 #126
		// mdb added 3/12/07 #104 -- END
	}

	public String getHelpID() {
		return "sequencefilter";//Filter.SEQUENCE_FILTER_ID;
	}

	/**
	 * Method <code>canShow</code> returns true if the Sequence has been initialized.
	 *
	 * @return a <code>boolean</code> value
	 * @see Sequence#hasInit()
	 */
	public boolean canShow() {
		if (sequence == null) return false;
		return sequence.hasInit();
	}

	/**
	 * Sets up the sequence dialog based on the current sequence track information before showing then
	 * shows (<code>super.show()</code>).
	 * 
	 */
	public void show() {
		if (!isShowing()) {
			noChange = true;
			
			// mdb added 12/7/09 #204 - disable annotation option if no corresponding annotations
			int[] annotTypeCounts = sequence.getAnnotationTypeCounts(); 
			if (annotTypeCounts[Annotation.FRAMEWORK_INT] == 0)
				frameCheck.setEnabled(false);
			if (annotTypeCounts[Annotation.GENE_INT] == 0 
					&& annotTypeCounts[Annotation.EXON_INT] == 0) 
				geneCheck.setEnabled(false);
			if (annotTypeCounts[Annotation.GAP_INT] == 0) 
				gapCheck.setEnabled(false);
			if (annotTypeCounts[Annotation.CENTROMERE_INT] == 0) 
				centromereCheck.setEnabled(false);
			if (annotTypeCounts[Annotation.GENE_INT] == 0)
				annotCheck.setEnabled(false);

			setStart();
			setEnd();
			startStr = startText.getText();
			endStr = endText.getText();

			frame = sequence.showFrame;
			gene = sequence.showGene;
			geneFull = sequence.showFullGene;

			frameCheck.setSelected(frame && frameCheck.isEnabled());
			geneCheck.setSelected(gene && geneCheck.isEnabled());
			geneFullRadio.setSelected(geneFull);
			geneMidRadio.setSelected(!geneFull);

			centromere = sequence.showCentromere;
			gap = sequence.showGap;

			gapCheck.setSelected(gap && gapCheck.isEnabled());
			centromereCheck.setSelected(centromere && centromereCheck.isEnabled());

			ruler = sequence.showRuler;
			rulerCheck.setSelected(ruler);

			annot = sequence.showAnnot;
			annotCheck.setSelected(annot && annotCheck.isEnabled());
			
			scoreLine = sequence.showScoreLine;		// mdb added 2/22/07 #100
			scoreLineCheck.setSelected(scoreLine);	// mdb added 2/22/07 #100
			
			scoreValue = sequence.showScoreValue;	// mdb added 3/14/07 #100
			scoreValueCheck.setSelected(scoreValue);// mdb added 3/14/07 #100
			
			ribbon = sequence.showRibbon;			// mdb added 8/7/07 #126
			ribbonCheck.setSelected(ribbon);		// mdb added 8/7/07 #126

			geneFullRadio.setEnabled(geneCheck.isSelected());
			geneMidRadio.setEnabled(geneCheck.isSelected());
			
			flipped = sequence.isFlipped(); // mdb added 7/23/07 #132
			flippedCheck.setSelected(flipped); // mdb added 8/7/07 #132

			noChange = false;
		}
		super.show();
	}

	protected void setDefault() {
		if (sequence != null) {
			frameCheck.setSelected(Sequence.DEFAULT_SHOW_FRAME && frameCheck.isEnabled());
			geneCheck.setSelected(Sequence.DEFAULT_SHOW_GENE && geneCheck.isEnabled());
			gapCheck.setSelected(Sequence.DEFAULT_SHOW_GAP && gapCheck.isEnabled());
			centromereCheck.setSelected(Sequence.DEFAULT_SHOW_CENTROMERE && centromereCheck.isEnabled());
			rulerCheck.setSelected(Sequence.DEFAULT_SHOW_RULER);
			annotCheck.setSelected(Sequence.DEFAULT_SHOW_ANNOT && annotCheck.isEnabled());
			scoreLineCheck.setSelected(Sequence.DEFAULT_SHOW_SCORE_LINE); 	// mdb added 2/22/07 #100
			scoreValueCheck.setSelected(Sequence.DEFAULT_SHOW_SCORE_VALUE); // mdb added 3/14/07 #100
			ribbonCheck.setSelected(Sequence.DEFAULT_SHOW_RIBBON); 			// mdb added 8/7/07 #126
			flippedCheck.setSelected(Sequence.DEFAULT_FLIPPED); 			// mdb added 7/23/07 #132

			geneFullRadio.setSelected(Sequence.DEFAULT_SHOW_GENE_FULL);

			startCombo.setSelectedItem(DEFAULT_ENDS_UNIT);
			endCombo.setSelectedItem(DEFAULT_ENDS_UNIT);
			startText.setText(new Double(0).toString());
			endText.setText(new Double(sequence.getValue(sequence.getTrackSize(),endCombo.getSelectedItem().toString())).toString());
		
			// mdb added 3/12/07 #104 -- BEGIN
			framePopupCheck.setState(Sequence.DEFAULT_SHOW_FRAME && framePopupCheck.isEnabled());
			genePopupCheck.setState(Sequence.DEFAULT_SHOW_GENE && genePopupCheck.isEnabled());
			gapPopupCheck.setState(Sequence.DEFAULT_SHOW_GAP && gapPopupCheck.isEnabled());
			centromerePopupCheck.setState(Sequence.DEFAULT_SHOW_CENTROMERE && centromerePopupCheck.isEnabled());
			rulerPopupCheck.setState(Sequence.DEFAULT_SHOW_RULER);
			annotPopupCheck.setState(Sequence.DEFAULT_SHOW_ANNOT && annotPopupCheck.isEnabled());
			scoreLinePopupCheck.setState(Sequence.DEFAULT_SHOW_SCORE_LINE);
			scoreValuePopupCheck.setState(Sequence.DEFAULT_SHOW_SCORE_VALUE);
			// mdb added 3/12/07 #104 -- END
			
			flippedPopupCheck.setState(Sequence.DEFAULT_FLIPPED); // mdb added 8/7/07 #132
			ribbonPopupCheck.setState(Sequence.DEFAULT_SHOW_RIBBON); // mdb added 8/7/07 #126
		}
	}

	private void setStart() {
		if (sequence != null)
			startText.setText(
				new Double(
					sequence.getValue(
						sequence.getStart(),
						startCombo.getSelectedItem().toString())).toString());
	}

	private void setEnd() {
		if (sequence != null)
			endText.setText(new Double(sequence.getValue(sequence.getEnd(),endCombo.getSelectedItem().toString())).toString());
	}

	/**
	 * Method <code>stateChanged</code> handles updating the Sequence track 
	 * when a checkbox changes.
	 *
	 * @param event a <code>ChangeEvent</code> value
	 */
	public void stateChanged(ChangeEvent event) {
		if (sequence != null && !noChange) {
			boolean changed = false;	
			if (event.getSource() == gapCheck)
				changed = sequence.showGap(gapCheck.isSelected());
			else if (event.getSource() == centromereCheck)
				changed = sequence.showCentromere(centromereCheck.isSelected());
			else if (event.getSource() == frameCheck)
				changed = sequence.showFrame(frameCheck.isSelected());
			else if (event.getSource() == geneCheck) {
				changed = sequence.showGene(geneCheck.isSelected());
				geneFullRadio.setEnabled(geneCheck.isSelected());
				geneMidRadio.setEnabled(geneCheck.isSelected());
			}
			else if (event.getSource() == rulerCheck) 
				changed = sequence.showRuler(rulerCheck.isSelected());
			else if (event.getSource() == annotCheck)
				changed = sequence.showAnnotation(annotCheck.isSelected());
			else if (event.getSource() == scoreLineCheck)						// mdb added 2/22/07 #100
				changed = sequence.showScoreLine(scoreLineCheck.isSelected());	// mdb added 2/22/07 #100
			else if (event.getSource() == scoreValueCheck)						// mdb added 3/14/07 #100
				changed = sequence.showScoreValue(scoreValueCheck.isSelected());// mdb added 3/14/07 #100
			else if (event.getSource() == ribbonCheck)							// mdb added 8/7/07 #126
				changed = sequence.showRibbon(ribbonCheck.isSelected());		// mdb added 8/7/07 #126
			else if (event.getSource() == geneFullRadio || event.getSource() == geneMidRadio)
				changed = sequence.showFullGene(geneFullRadio.isSelected());
			else if (event.getSource() == flippedCheck) 			// mdb added 7/23/07 #132
				changed = sequence.flip(flippedCheck.isSelected()); // mdb added 7/23/07 #132
			
			if (changed) drawingPanel.smake();
		}
	}
	
	// mdb added 3/13/07 #104
	public void actionPerformed(ActionEvent event) { // only called for popup menu events
		if (sequence != null) {
			boolean changed = false;	

			if (event.getSource() == fullSequencePopupItem) {
				setFullSequence();
				try { okAction(); } catch (Exception e) { }
				changed = true;
			}
			else if (event.getSource() == flippedPopupCheck) { // mdb added 7/23/07 #132
				flipped = flippedPopupCheck.getState();
				changed = sequence.flip(flipped);
			}
			else if (event.getSource() == gapPopupCheck) {
				gap = gapPopupCheck.getState();
				changed = sequence.showGap(gap);
			}
			else if (event.getSource() == centromerePopupCheck) {
				centromere = centromerePopupCheck.getState();
				changed = sequence.showCentromere(centromere);
			}
			else if (event.getSource() == framePopupCheck) {
				frame = framePopupCheck.getState();
				changed = sequence.showFrame(frame);
			}
			else if (event.getSource() == genePopupCheck) {
				gene = genePopupCheck.getState();
				changed = sequence.showGene(gene);
			}
			else if (event.getSource() == rulerPopupCheck) {
				ruler = rulerPopupCheck.getState();
				changed = sequence.showRuler(ruler);
			}
			else if (event.getSource() == annotPopupCheck) {
				annot = annotPopupCheck.getState();
				changed = sequence.showAnnotation(annot);
			}
			else if (event.getSource() == scoreLinePopupCheck) {
				scoreLine = scoreLinePopupCheck.getState();
				changed = sequence.showScoreLine(scoreLine);
			}
			else if (event.getSource() == scoreValuePopupCheck) {
				scoreValue = scoreValuePopupCheck.getState();
				changed = sequence.showScoreValue(scoreValue);
			}
			else if (event.getSource() == ribbonPopupCheck) { // mdb added 8/7/07 #126
				ribbon = ribbonPopupCheck.getState();
				changed = sequence.showRibbon(ribbon);
			}
				
			if (changed) {
				drawingPanel.setUpdateHistory();
				drawingPanel.smake();	
			}
		}
		super.actionPerformed(event);
	}
	
	// mdb added 3/13/07 #104
	public void popupMenuWillBecomeVisible(PopupMenuEvent event) { 
		// mdb added 12/7/09 #204 - disable annotation option if no corresponding annotations
		int[] annotTypeCounts = sequence.getAnnotationTypeCounts();
		if (annotTypeCounts[Annotation.FRAMEWORK_INT] == 0)
			framePopupCheck.setEnabled(false);
		if (annotTypeCounts[Annotation.GENE_INT] == 0 
				&& annotTypeCounts[Annotation.EXON_INT] == 0) 
			genePopupCheck.setEnabled(false);
		if (annotTypeCounts[Annotation.GAP_INT] == 0) 
			gapPopupCheck.setEnabled(false);
		if (annotTypeCounts[Annotation.CENTROMERE_INT] == 0) 
			centromerePopupCheck.setEnabled(false);
		if (annotTypeCounts[Annotation.GENE_INT] == 0) 
			annotPopupCheck.setEnabled(false);
		
		frame = sequence.showFrame && framePopupCheck.isEnabled();
		gene = sequence.showGene && genePopupCheck.isEnabled();
		gap = sequence.showGap && gapPopupCheck.isEnabled();
		centromere = sequence.showCentromere && centromerePopupCheck.isEnabled();
		annot = sequence.showAnnot && annotPopupCheck.isEnabled();
		ruler = sequence.showRuler;
		scoreLine = sequence.showScoreLine;
		scoreValue = sequence.showScoreValue;
		flipped = sequence.isFlipped();	// mdb added 8/2/07 #132
		ribbon = sequence.showRibbon;	// mdb added 8/7/07 #126
		
		framePopupCheck.setState(frame);
		genePopupCheck.setState(gene);
		gapPopupCheck.setState(gap);
		centromerePopupCheck.setState(centromere);
		annotPopupCheck.setState(annot);
		rulerPopupCheck.setState(ruler);
		scoreLinePopupCheck.setState(scoreLine); 
		scoreValuePopupCheck.setState(scoreValue); 
		ribbonPopupCheck.setState(ribbon); 	 // mdb added 8/7/07 #126
		flippedPopupCheck.setState(flipped); // mdb added 8/2/07 #132
	}
	
	protected boolean okAction() throws Exception {
		if (sequence == null) return false;
		if (!sequence.hasInit()) return true;

		boolean changed = false;
		
		if (endText.getText().length() > 0) {
			try {
			(new Double(endText.getText())).doubleValue();
			} catch (NumberFormatException nfe) {
				Utilities.showErrorMessage(endText.getText() + " is not a valid end point");
				return changed;
			}
		}

		if (startText.getText().length() > 0) {
			try {
			(new Double(startText.getText())).doubleValue();
			} catch (NumberFormatException nfe) {
				Utilities.showErrorMessage(startText.getText() + " is not a valid start point");
				return changed;
			}
		}
		
		if (flipped != flippedCheck.isSelected()) { // mdb added 7/23/07 #132
			flipped = !flipped;
			changed = true;
		}
		if (frame != frameCheck.isSelected()) {
			frame = !frame;
			changed = true;
		}
		if (gene != geneCheck.isSelected()) {
			gene = !gene;
			changed = true;
		}
		if (geneFull != geneFullRadio.isSelected()) {
			geneFull = !geneFull;
			if (gene) changed = true; // don't care if gene isn't selected any more
		}
		if (gap != gapCheck.isSelected()) {
			gap = !gap;
			changed = true;
		}
		if (centromere != centromereCheck.isSelected()) {
			centromere = !centromere;
			changed = true;
		}
		if (ruler != rulerCheck.isSelected()) {
			ruler = !ruler;
			changed = true;
		}
		if (annot != annotCheck.isSelected()) {
			annot = !annot;
			changed = true;
		}
		if (scoreLine != scoreLineCheck.isSelected()) {	// mdb added 2/22/07 #100
			scoreLine = !scoreLine;						// mdb added 2/22/07 #100
			changed = true;								// mdb added 2/22/07 #100
		}
		if (scoreValue != scoreValueCheck.isSelected()) {	// mdb added 3/14/07 #100
			scoreValue = !scoreValue;						// mdb added 3/14/07 #100
			changed = true;									// mdb added 3/14/07 #100
		}
		if (ribbon != ribbonCheck.isSelected()) {	// mdb added 8/7/07 #126
			ribbon = !ribbon;						// mdb added 8/7/07 #126
			changed = true;							// mdb added 8/7/07 #126
		}
		
		String unit;
		double number;
		long tstart, tend;

		unit = GenomicsNumber.ABS_UNITS[startCombo.getSelectedIndex()];
		if (startText.getText().length() > 0) {
			try {
				number = (new Double(startText.getText())).doubleValue();
				if (number < 0) number = 0;
			} catch (NumberFormatException nfe) {
				nfe.printStackTrace();
				throw new Exception("Start value is not a number.");
			}
			try {
				sequence.setStart((long)Math.round(number * GenomicsNumber.getUnitConversion(unit)));
			} catch (IllegalArgumentException e) {
				setStart();
				e.printStackTrace();
				throw e;
			}
		} else {
			sequence.resetStart();
			setStart();
		}

		tstart = sequence.getStart();
		tend = sequence.getEnd();

		unit = GenomicsNumber.ABS_UNITS[endCombo.getSelectedIndex()];
		if (endText.getText().length() > 0) {
			try {
				number = (new Double(endText.getText())).doubleValue();
			} catch (NumberFormatException nfe) {
				nfe.printStackTrace();
				throw new Exception("End value is not a number.");
			}
			double mult = GenomicsNumber.getUnitConversion(unit);
			try {
				mult = sequence.setEnd((long)Math.round(number * mult)) / mult;
			} catch (IllegalArgumentException e) {
				setEnd();
				e.printStackTrace();
				throw e;
			}
			if (mult != number) endText.setText(new Double(mult).toString());
		} else {
			sequence.resetEnd();
			setEnd();
		}
		if (sequence.getStart() == sequence.getEnd()) {
			sequence.setStart(tstart);
			sequence.setEnd(tend);
			throw new Exception("The start and end value must be different.");
		}

		endStr = endText.getText();
		endInd = endCombo.getSelectedIndex();

		startStr = startText.getText();
		startInd = startCombo.getSelectedIndex();

		return !sequence.hasBuilt() || changed;
	}

	protected void cancelAction() {
		frameCheck.setSelected(frame);
		geneCheck.setSelected(gene);
		geneFullRadio.setSelected(geneFull);
		gapCheck.setSelected(gap);
		centromereCheck.setSelected(centromere);
		rulerCheck.setSelected(ruler);
		annotCheck.setSelected(annot);
		scoreLineCheck.setSelected(scoreLine); // mdb added 2/22/07 #100
		scoreValueCheck.setSelected(scoreValue); // mdb added 3/14/07 #100
		ribbonCheck.setSelected(ribbon); // mdb added 8/7/07 #126
		startText.setText(startStr);
		startCombo.setSelectedIndex(startInd);
		endText.setText(endStr);
		endCombo.setSelectedIndex(endInd);
		flippedCheck.setSelected(flipped); // mdb added 7/23/07 #132
	}

	private void setFullSequence() {
		startCombo.setSelectedItem(DEFAULT_ENDS_UNIT);
		endCombo.setSelectedItem(DEFAULT_ENDS_UNIT);
		startText.setText(new Double(0).toString());
		endText.setText(new Double(sequence.getValue(sequence.getTrackSize(),endCombo.getSelectedItem().toString())).toString());
	}
}

