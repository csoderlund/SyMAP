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
import javax.swing.JCheckBoxMenuItem; 
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JMenuItem; 
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.PopupMenuEvent; 

import number.GenomicsNumber;
import symap.drawingpanel.DrawingPanel;
import symap.filter.Filter;
import util.Utilities;

/**
 * The filter dialog for the Sequence view.
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
	private JCheckBox scoreLineCheck, scoreValueCheck; 	
	private JCheckBox ribbonCheck; 						
	private JCheckBox flippedCheck; 					
	
	private JCheckBoxMenuItem flippedPopupCheck; 						
	private JCheckBoxMenuItem framePopupCheck, genePopupCheck; 			
	private JCheckBoxMenuItem gapPopupCheck, centromerePopupCheck; 		
	private JCheckBoxMenuItem rulerPopupCheck, annotPopupCheck; 		
	private JCheckBoxMenuItem scoreLinePopupCheck, scoreValuePopupCheck;
	private JCheckBoxMenuItem ribbonPopupCheck; 						
	private JMenuItem fullSequencePopupItem; 							

	private JButton fullButton;

	private Sequence sequence;

	private String startStr, endStr;
	private int startInd, endInd;
	private boolean frame, gene, gap, centromere, ruler, annot, geneFull;
	private boolean flipped; 				
	private boolean scoreLine, scoreValue; 	
	private boolean ribbon; 				
	private boolean noChange = false;

	public SequenceFilter(Frame owner, DrawingPanel dp, AbstractButton helpButton, Sequence sequence) {
	/** Menu **/
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
		
		flippedCheck = new JCheckBox("Flip"); 				
		flippedCheck.setSelected(Sequence.DEFAULT_FLIPPED); 
		
		frameCheck = new JCheckBox("Show Framework Markers");
		geneCheck = new JCheckBox("Show Genes");
		gapCheck = new JCheckBox("Show Gaps");
		centromereCheck = new JCheckBox("Show Centromere");
		rulerCheck = new JCheckBox("Show Ruler");
		annotCheck = new JCheckBox("Show Annotation Descriptions");

		frameCheck.setSelected(Sequence.DEFAULT_SHOW_FRAME && frameCheck.isEnabled());
		geneCheck.setSelected(Sequence.DEFAULT_SHOW_GENE && geneCheck.isEnabled());
		gapCheck.setSelected(Sequence.DEFAULT_SHOW_GAP && gapCheck.isEnabled());
		centromereCheck.setSelected(Sequence.DEFAULT_SHOW_CENTROMERE && centromereCheck.isEnabled());
		rulerCheck.setSelected(Sequence.DEFAULT_SHOW_RULER);
		annotCheck.setSelected(Sequence.DEFAULT_SHOW_ANNOT  && annotCheck.isEnabled()); 
		
		scoreLineCheck = new JCheckBox("Show Hit Score Bar");			
		scoreLineCheck.setSelected(Sequence.DEFAULT_SHOW_SCORE_LINE);	

		scoreValueCheck = new JCheckBox("Show Hit Score Value");		
		scoreValueCheck.setSelected(Sequence.DEFAULT_SHOW_SCORE_VALUE);	
		
		ribbonCheck = new JCheckBox("Show Hit Length");			
		ribbonCheck.setSelected(Sequence.DEFAULT_SHOW_RIBBON);	
		
		geneFullRadio = new JRadioButton("Length");
		geneMidRadio = new  JRadioButton("Midpoint");
		ButtonGroup geneBG = new ButtonGroup();
		geneBG.add(geneFullRadio);
		geneBG.add(geneMidRadio);

		JPanel geneRadioPanel = new JPanel();
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
		addToGrid(contentPane, gridbag, constraints, flippedCheck, GridBagConstraints.REMAINDER); 
		
		addToGrid(contentPane, gridbag, constraints, new JSeparator(), GridBagConstraints.REMAINDER);
		addToGrid(contentPane, gridbag, constraints, geneCheck, 2);
		constraints.fill = GridBagConstraints.NONE;
		addToGrid(contentPane, gridbag, constraints, geneRadioPanel, GridBagConstraints.REMAINDER);
		constraints.fill = GridBagConstraints.HORIZONTAL;
		addToGrid(contentPane, gridbag, constraints, annotCheck, GridBagConstraints.REMAINDER); // CAS503 move
		
		addToGrid(contentPane, gridbag, constraints, gapCheck, GridBagConstraints.REMAINDER);
		addToGrid(contentPane, gridbag, constraints, centromereCheck, GridBagConstraints.REMAINDER);
		addToGrid(contentPane, gridbag, constraints, frameCheck, GridBagConstraints.REMAINDER);

		addToGrid(contentPane, gridbag, constraints, new JSeparator(), GridBagConstraints.REMAINDER);
		
		addToGrid(contentPane, gridbag, constraints, rulerCheck, GridBagConstraints.REMAINDER);
		addToGrid(contentPane, gridbag, constraints, ribbonCheck, GridBagConstraints.REMAINDER);     
		addToGrid(contentPane, gridbag, constraints, scoreLineCheck, GridBagConstraints.REMAINDER);  
		addToGrid(contentPane, gridbag, constraints, scoreValueCheck, GridBagConstraints.REMAINDER); 

		addToGrid(contentPane, gridbag, constraints, buttonPanel, GridBagConstraints.REMAINDER);

		pack();
		setResizable(false);
		
		geneFullRadio.addChangeListener(this);
		geneMidRadio.addChangeListener(this);
		flippedCheck.addChangeListener(this);		
		frameCheck.addChangeListener(this);
		geneCheck.addChangeListener(this);
		ribbonCheck.addChangeListener(this);		
		gapCheck.addChangeListener(this);
		centromereCheck.addChangeListener(this);
		rulerCheck.addChangeListener(this);
		annotCheck.addChangeListener(this);
		scoreLineCheck.addChangeListener(this);		
		scoreValueCheck.addChangeListener(this);	
		
		/** Popup **/
		fullSequencePopupItem = new JMenuItem("Full Sequence");
		flippedPopupCheck = new JCheckBoxMenuItem("Flip Sequence"); 
		annotPopupCheck = new JCheckBoxMenuItem("Annotation Descriptions"); 
		framePopupCheck = new JCheckBoxMenuItem("Framework Markers"); 
		genePopupCheck = new JCheckBoxMenuItem("Genes"); 
		ribbonPopupCheck = new JCheckBoxMenuItem("Hit Length"); 
		gapPopupCheck = new JCheckBoxMenuItem("Gaps"); 
		centromerePopupCheck = new JCheckBoxMenuItem("Centromere"); 
		rulerPopupCheck = new JCheckBoxMenuItem("Ruler"); 
		scoreLinePopupCheck = new JCheckBoxMenuItem("Hit Score Bar"); 		
		scoreValuePopupCheck = new JCheckBoxMenuItem("Hit Score Value"); 	
	
	/** Sequence show options **/
		popupTitle.setText("Sequence Show Options:"); 
		popup.add(flippedPopupCheck); 
		
		popup.add(annotPopupCheck); // CAS503 move after genes
		popup.add(genePopupCheck); 
			
		popup.add(gapPopupCheck); 
		popup.add(centromerePopupCheck); 
		popup.add(framePopupCheck); 
		
		popup.add(rulerPopupCheck);
		popup.add(ribbonPopupCheck); 
		popup.add(scoreLinePopupCheck);
		popup.add(scoreValuePopupCheck);
		popup.add(fullSequencePopupItem);
		
		fullSequencePopupItem.addActionListener(this);
		flippedPopupCheck.addActionListener(this); 
		framePopupCheck.addActionListener(this);
		genePopupCheck.addActionListener(this);
		gapPopupCheck.addActionListener(this);
		centromerePopupCheck.addActionListener(this);
		rulerPopupCheck.addActionListener(this);
		annotPopupCheck.addActionListener(this);
		scoreLinePopupCheck.addActionListener(this);
		scoreValuePopupCheck.addActionListener(this);
		ribbonPopupCheck.addActionListener(this); 
	}

	public String getHelpID() {
		return "sequencefilter";//Filter.SEQUENCE_FILTER_ID;
	}

	/**
	 * Method <code>canShow</code> returns true if the Sequence has been initialized.
	 */
	public boolean canShow() {
		if (sequence == null) return false;
		return sequence.hasInit();
	}

	/**
	 * Sets up the sequence dialog based on the current sequence track information before showing then
	 * shows (<code>super.show()</code>).
	 */
	public void show() {
		if (!isShowing()) {
			noChange = true;
			
			int[] annotTypeCounts = sequence.getAnnotationTypeCounts(); 
			if (annotTypeCounts[Annotation.FRAMEWORK_INT] == 0) frameCheck.setEnabled(false);
			if (annotTypeCounts[Annotation.GENE_INT] == 0 
					&& annotTypeCounts[Annotation.EXON_INT] == 0) geneCheck.setEnabled(false);
			if (annotTypeCounts[Annotation.GAP_INT] == 0) 		gapCheck.setEnabled(false);
			if (annotTypeCounts[Annotation.CENTROMERE_INT] == 0) centromereCheck.setEnabled(false);
			if (annotTypeCounts[Annotation.GENE_INT] == 0)		annotCheck.setEnabled(false);

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
			
			scoreLine = sequence.showScoreLine;		
			scoreLineCheck.setSelected(scoreLine);	
			
			scoreValue = sequence.showScoreValue;	
			scoreValueCheck.setSelected(scoreValue);
			
			ribbon = sequence.showRibbon;			
			ribbonCheck.setSelected(ribbon);		

			geneFullRadio.setEnabled(geneCheck.isSelected());
			geneMidRadio.setEnabled(geneCheck.isSelected());
			
			flipped = sequence.isFlipped(); 
			flippedCheck.setSelected(flipped); 

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
			scoreLineCheck.setSelected(Sequence.DEFAULT_SHOW_SCORE_LINE); 	
			scoreValueCheck.setSelected(Sequence.DEFAULT_SHOW_SCORE_VALUE); 
			ribbonCheck.setSelected(Sequence.DEFAULT_SHOW_RIBBON); 			
			flippedCheck.setSelected(Sequence.DEFAULT_FLIPPED); 			

			geneFullRadio.setSelected(Sequence.DEFAULT_SHOW_GENE_FULL);

			startCombo.setSelectedItem(DEFAULT_ENDS_UNIT);
			endCombo.setSelectedItem(DEFAULT_ENDS_UNIT);
			startText.setText(new Double(0).toString());
			endText.setText(new Double(sequence.getValue(sequence.getTrackSize(),endCombo.getSelectedItem().toString())).toString());
		
			framePopupCheck.setState(Sequence.DEFAULT_SHOW_FRAME && framePopupCheck.isEnabled());
			genePopupCheck.setState(Sequence.DEFAULT_SHOW_GENE && genePopupCheck.isEnabled());
			gapPopupCheck.setState(Sequence.DEFAULT_SHOW_GAP && gapPopupCheck.isEnabled());
			centromerePopupCheck.setState(Sequence.DEFAULT_SHOW_CENTROMERE && centromerePopupCheck.isEnabled());
			rulerPopupCheck.setState(Sequence.DEFAULT_SHOW_RULER);
			annotPopupCheck.setState(Sequence.DEFAULT_SHOW_ANNOT && annotPopupCheck.isEnabled());
			scoreLinePopupCheck.setState(Sequence.DEFAULT_SHOW_SCORE_LINE);
			scoreValuePopupCheck.setState(Sequence.DEFAULT_SHOW_SCORE_VALUE);
			
			flippedPopupCheck.setState(Sequence.DEFAULT_FLIPPED); 
			ribbonPopupCheck.setState(Sequence.DEFAULT_SHOW_RIBBON); 
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
			else if (event.getSource() == scoreLineCheck)						
				changed = sequence.showScoreLine(scoreLineCheck.isSelected());	
			else if (event.getSource() == scoreValueCheck)						
				changed = sequence.showScoreValue(scoreValueCheck.isSelected());
			else if (event.getSource() == ribbonCheck)							
				changed = sequence.showRibbon(ribbonCheck.isSelected());		
			else if (event.getSource() == geneFullRadio || event.getSource() == geneMidRadio)
				changed = sequence.showFullGene(geneFullRadio.isSelected());
			else if (event.getSource() == flippedCheck) 			
				changed = sequence.flip(flippedCheck.isSelected()); 
			
			if (changed) drawingPanel.smake();
		}
	}
	
	public void actionPerformed(ActionEvent event) { // only called for popup menu events
		if (sequence != null) {
			boolean changed = false;	

			if (event.getSource() == fullSequencePopupItem) {
				setFullSequence();
				try { okAction(); } catch (Exception e) { }
				changed = true;
			}
			else if (event.getSource() == flippedPopupCheck) { 
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
			else if (event.getSource() == ribbonPopupCheck) { 
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
	// 
	public void popupMenuWillBecomeVisible(PopupMenuEvent event) { 
		int[] annotTypeCounts = sequence.getAnnotationTypeCounts();
		if (annotTypeCounts[Annotation.FRAMEWORK_INT] == 0) 		framePopupCheck.setEnabled(false);
		if (annotTypeCounts[Annotation.GENE_INT] == 0 
				&& annotTypeCounts[Annotation.EXON_INT] == 0) 	genePopupCheck.setEnabled(false);
		if (annotTypeCounts[Annotation.GAP_INT] == 0) 			gapPopupCheck.setEnabled(false);
		if (annotTypeCounts[Annotation.CENTROMERE_INT] == 0) 	centromerePopupCheck.setEnabled(false);
		if (annotTypeCounts[Annotation.GENE_INT] == 0) 			annotPopupCheck.setEnabled(false);
		
		frame = sequence.showFrame && framePopupCheck.isEnabled();
		gene = sequence.showGene && genePopupCheck.isEnabled();
		gap = sequence.showGap && gapPopupCheck.isEnabled();
		centromere = sequence.showCentromere && centromerePopupCheck.isEnabled();
		annot = sequence.showAnnot && annotPopupCheck.isEnabled();
		ruler = sequence.showRuler;
		scoreLine = sequence.showScoreLine;
		scoreValue = sequence.showScoreValue;
		flipped = sequence.isFlipped();	
		ribbon = sequence.showRibbon;	
		
		framePopupCheck.setState(frame);
		genePopupCheck.setState(gene);
		gapPopupCheck.setState(gap);
		centromerePopupCheck.setState(centromere);
		annotPopupCheck.setState(annot);
		rulerPopupCheck.setState(ruler);
		scoreLinePopupCheck.setState(scoreLine); 
		scoreValuePopupCheck.setState(scoreValue); 
		ribbonPopupCheck.setState(ribbon); 	 
		flippedPopupCheck.setState(flipped); 
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
		
		if (flipped != flippedCheck.isSelected()) { 
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
		if (scoreLine != scoreLineCheck.isSelected()) {	
			scoreLine = !scoreLine;						
			changed = true;								
		}
		if (scoreValue != scoreValueCheck.isSelected()) {	
			scoreValue = !scoreValue;						
			changed = true;									
		}
		if (ribbon != ribbonCheck.isSelected()) {	
			ribbon = !ribbon;						
			changed = true;							
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
		scoreLineCheck.setSelected(scoreLine); 
		scoreValueCheck.setSelected(scoreValue); 
		ribbonCheck.setSelected(ribbon); 
		startText.setText(startStr);
		startCombo.setSelectedIndex(startInd);
		endText.setText(endStr);
		endCombo.setSelectedIndex(endInd);
		flippedCheck.setSelected(flipped); 
	}

	private void setFullSequence() {
		startCombo.setSelectedItem(DEFAULT_ENDS_UNIT);
		endCombo.setSelectedItem(DEFAULT_ENDS_UNIT);
		startText.setText(new Double(0).toString());
		endText.setText(new Double(sequence.getValue(sequence.getTrackSize(),endCombo.getSelectedItem().toString())).toString());
	}
}

