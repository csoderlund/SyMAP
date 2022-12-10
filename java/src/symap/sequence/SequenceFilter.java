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

	private JLabel startLabel, endLabel;
	private JTextField startText, endText;
	private JComboBox <String> startCombo, endCombo; // CAS514 add type
	
	private JRadioButton geneFullRadio, geneMidRadio; // CAS520 not displayed - function seems unclear

	private JCheckBox frameCheck, geneCheck, geneLineCheck; // CAS520 add line
	private JCheckBox gapCheck, centromereCheck;
	private JCheckBox rulerCheck, annotCheck;
	private JCheckBox scoreLineCheck, scoreValueCheck; 	
	private JCheckBox hitLenCheck; 						
	private JCheckBox flippedCheck; 					
	
	private JCheckBoxMenuItem flippedPopupCheck; 						
	private JCheckBoxMenuItem framePopupCheck, genePopupCheck, geneLinePopupCheck; // CAS520 add line 			
	private JCheckBoxMenuItem gapPopupCheck, centromerePopupCheck; 		
	private JCheckBoxMenuItem rulerPopupCheck, annotPopupCheck; 		
	private JCheckBoxMenuItem scoreLinePopupCheck, scoreValuePopupCheck;
	private JCheckBoxMenuItem hitLenPopupCheck; 						
	private JMenuItem fullSequencePopupItem; 							

	private JButton fullButton;

	private Sequence sequence;

	// previous settings
	private String startStr, endStr;
	private int startInd, endInd;
	private boolean bFrame, bGene, bGap, bCentromere, bRuler, bAnnot, bGeneFull;
	private boolean bGeneLine;
	private boolean bFlipped; 				
	private boolean bScoreLine, bScoreValue; 	
	private boolean bHitLen; 				
	private boolean bNoChange = false;

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
		endLabel =   new JLabel("End:");

		startText = new JTextField("",10); // CAS520 was 15
		endText =   new JTextField("",10);

		startCombo = new JComboBox <String>(GenomicsNumber.ABS_UNITS);
		endCombo =   new JComboBox <String>(GenomicsNumber.ABS_UNITS);

		startCombo.setSelectedItem(DEFAULT_ENDS_UNIT);
		endCombo.setSelectedItem(DEFAULT_ENDS_UNIT);

		startInd = startCombo.getSelectedIndex();
		endInd =   startInd;
		
		flippedCheck = new JCheckBox("Flip"); 				
		flippedCheck.setSelected(Sequence.DEFAULT_FLIPPED); 
		
		frameCheck 		= new JCheckBox("Show Framework Markers");
		geneCheck 		= new JCheckBox("Show Genes");
		geneLineCheck 	= new JCheckBox("Show Gene Delimiter");
		gapCheck 		= new JCheckBox("Show Gaps");
		centromereCheck = new JCheckBox("Show Centromere");
		rulerCheck 		= new JCheckBox("Show Ruler");
		annotCheck 		= new JCheckBox("Show Annotation");

		frameCheck.setSelected(Sequence.DEFAULT_SHOW_FRAME && frameCheck.isEnabled());
		geneCheck.setSelected(Sequence.DEFAULT_SHOW_GENE && geneCheck.isEnabled());
		geneLineCheck.setSelected(Sequence.DEFAULT_SHOW_GENE_LINE && geneLineCheck.isEnabled());
		gapCheck.setSelected(Sequence.DEFAULT_SHOW_GAP && gapCheck.isEnabled());
		centromereCheck.setSelected(Sequence.DEFAULT_SHOW_CENTROMERE && centromereCheck.isEnabled());
		rulerCheck.setSelected(Sequence.DEFAULT_SHOW_RULER);
		annotCheck.setSelected(Sequence.DEFAULT_SHOW_ANNOT  && annotCheck.isEnabled()); 
		
		scoreLineCheck = new JCheckBox("Show Hit %Id Bar");	// CAS515 Score is %Id		
		scoreLineCheck.setSelected(Sequence.DEFAULT_SHOW_SCORE_LINE);	

		scoreValueCheck = new JCheckBox("Show Hit %Id Value");		
		scoreValueCheck.setSelected(Sequence.DEFAULT_SHOW_SCORE_VALUE);	
		
		hitLenCheck = new JCheckBox("Show Hit Length");			
		hitLenCheck.setSelected(Sequence.DEFAULT_SHOW_RIBBON);	
		
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
		addToGrid(contentPane, gridbag, constraints, geneCheck,  GridBagConstraints.REMAINDER);
		/** CAS520 this option seems useless
		addToGrid(contentPane, gridbag, constraints, geneCheck, 2);
		constraints.fill = GridBagConstraints.NONE;
		addToGrid(contentPane, gridbag, constraints, geneRadioPanel, GridBagConstraints.REMAINDER);
		constraints.fill = GridBagConstraints.HORIZONTAL;
		**/
		addToGrid(contentPane, gridbag, constraints, geneLineCheck, GridBagConstraints.REMAINDER); // CAS520 add
		addToGrid(contentPane, gridbag, constraints, annotCheck, GridBagConstraints.REMAINDER); // CAS503 move
		
		addToGrid(contentPane, gridbag, constraints, gapCheck, GridBagConstraints.REMAINDER);
		addToGrid(contentPane, gridbag, constraints, centromereCheck, GridBagConstraints.REMAINDER);
		
		addToGrid(contentPane, gridbag, constraints, new JSeparator(), GridBagConstraints.REMAINDER);
		
		addToGrid(contentPane, gridbag, constraints, rulerCheck, GridBagConstraints.REMAINDER);
		addToGrid(contentPane, gridbag, constraints, hitLenCheck, GridBagConstraints.REMAINDER);     
		addToGrid(contentPane, gridbag, constraints, scoreLineCheck, GridBagConstraints.REMAINDER);  
		addToGrid(contentPane, gridbag, constraints, scoreValueCheck, GridBagConstraints.REMAINDER); 

		addToGrid(contentPane, gridbag, constraints, buttonPanel, GridBagConstraints.REMAINDER);

		pack();
		setResizable(false);
		setLocationRelativeTo(null); // CAS520
		
		// All check boxes are applied immediately but the start/end only on Apply
		geneFullRadio.addChangeListener(this);
		geneMidRadio.addChangeListener(this);
		flippedCheck.addChangeListener(this);		
		frameCheck.addChangeListener(this);
		geneCheck.addChangeListener(this);
		geneLineCheck.addChangeListener(this);
		hitLenCheck.addChangeListener(this);		
		gapCheck.addChangeListener(this);
		centromereCheck.addChangeListener(this);
		rulerCheck.addChangeListener(this);
		annotCheck.addChangeListener(this);
		scoreLineCheck.addChangeListener(this);		
		scoreValueCheck.addChangeListener(this);	
		
		/** Popup **/
		fullSequencePopupItem 	= new JMenuItem("Full Sequence");
		flippedPopupCheck 		= new JCheckBoxMenuItem("Flip Sequence"); 
		annotPopupCheck			= new JCheckBoxMenuItem("Annotation"); 
		framePopupCheck 		= new JCheckBoxMenuItem("Framework Markers"); 
		genePopupCheck 			= new JCheckBoxMenuItem("Genes"); 
		geneLinePopupCheck 		= new JCheckBoxMenuItem("Genes Delimiter"); 
		hitLenPopupCheck 		= new JCheckBoxMenuItem("Hit Length"); 
		gapPopupCheck 			= new JCheckBoxMenuItem("Gaps"); 
		centromerePopupCheck 	= new JCheckBoxMenuItem("Centromere"); 
		rulerPopupCheck 		= new JCheckBoxMenuItem("Ruler"); 
		scoreLinePopupCheck 	= new JCheckBoxMenuItem("Hit %Id Bar");  // CAS515 Score is the %Id		
		scoreValuePopupCheck 	= new JCheckBoxMenuItem("Hit %Id Value"); 	// ditto
	
	/** Sequence show options **/
		popupTitle.setText("Sequence Show Options:"); 
		popup.add(flippedPopupCheck); 
		
		popup.add(annotPopupCheck); // CAS503 move after genes
		popup.add(genePopupCheck); 
		popup.add(geneLinePopupCheck);
			
		popup.add(gapPopupCheck); 
		popup.add(centromerePopupCheck); 
		
		popup.add(rulerPopupCheck);
		popup.add(hitLenPopupCheck); 
		popup.add(scoreLinePopupCheck);
		popup.add(scoreValuePopupCheck);
		// popup.add(fullSequencePopupItem); CAS530 not working
		
		fullSequencePopupItem.addActionListener(this);
		flippedPopupCheck.addActionListener(this); 
		framePopupCheck.addActionListener(this);
		genePopupCheck.addActionListener(this);
		geneLinePopupCheck.addActionListener(this);
		gapPopupCheck.addActionListener(this);
		centromerePopupCheck.addActionListener(this);
		rulerPopupCheck.addActionListener(this);
		annotPopupCheck.addActionListener(this);
		scoreLinePopupCheck.addActionListener(this);
		scoreValuePopupCheck.addActionListener(this);
		hitLenPopupCheck.addActionListener(this); 
	}

	public String getHelpID() {
		return "sequencefilter";//Filter.SEQUENCE_FILTER_ID;
	}

	public boolean canShow() {
		if (sequence == null) return false;
		return sequence.hasInit();
	}

	/**
	 * Sets up the sequence dialog based on the current sequence track information before showing then
	 * shows (super.show()).
	 */
	public void showX() {
		if (!isShowing()) {
			bNoChange = true;
			
			int[] annotTypeCounts = sequence.getAnnotationTypeCounts(); 
			if (annotTypeCounts[Annotation.FRAMEWORK_INT] == 0) frameCheck.setEnabled(false);
			if (annotTypeCounts[Annotation.GENE_INT] == 0 && annotTypeCounts[Annotation.EXON_INT] == 0) {
				geneCheck.setEnabled(false);
				geneLineCheck.setEnabled(false);
			}
			if (annotTypeCounts[Annotation.GAP_INT] == 0) 		gapCheck.setEnabled(false);
			if (annotTypeCounts[Annotation.CENTROMERE_INT] == 0) centromereCheck.setEnabled(false);
			if (annotTypeCounts[Annotation.GENE_INT] == 0)		annotCheck.setEnabled(false);

			setStart();
			setEnd();
			startStr = startText.getText();
			endStr = endText.getText();

			bFrame = sequence.showFrame;
			bGene = sequence.showGene;
			bGeneLine = sequence.showGeneLine; 
			bGeneFull = sequence.showFullGene;

			frameCheck.setSelected(bFrame && frameCheck.isEnabled());
			geneCheck.setSelected(bGene && geneCheck.isEnabled());
			geneLineCheck.setSelected(bGeneLine && geneLineCheck.isEnabled()); // CAS520 add all geneLine lines
			geneFullRadio.setSelected(bGeneFull);
			geneMidRadio.setSelected(!bGeneFull);

			bCentromere = sequence.showCentromere;
			bGap = sequence.showGap;

			gapCheck.setSelected(bGap && gapCheck.isEnabled());
			centromereCheck.setSelected(bCentromere && centromereCheck.isEnabled());

			bRuler = sequence.showRuler;
			rulerCheck.setSelected(bRuler);

			bAnnot = sequence.showAnnot;
			annotCheck.setSelected(bAnnot && annotCheck.isEnabled());
			
			bScoreLine = sequence.showScoreLine;		
			scoreLineCheck.setSelected(bScoreLine);	
			
			bScoreValue = sequence.showScoreValue;	
			scoreValueCheck.setSelected(bScoreValue);
			
			bHitLen = sequence.showHitLen;			
			hitLenCheck.setSelected(bHitLen);		

			geneFullRadio.setEnabled(geneCheck.isSelected());
			geneMidRadio.setEnabled(geneCheck.isSelected());
			
			bFlipped = sequence.isFlipped(); 
			flippedCheck.setSelected(bFlipped); 

			bNoChange = false;
		}
		super.setVisible(true); // CAS512 super.show();
	}

	protected void setDefault() {
		if (sequence != null) {
			frameCheck.setSelected(Sequence.DEFAULT_SHOW_FRAME && frameCheck.isEnabled());
			geneCheck.setSelected(Sequence.DEFAULT_SHOW_GENE && geneCheck.isEnabled());
			geneLineCheck.setSelected(Sequence.DEFAULT_SHOW_GENE_LINE && geneLineCheck.isEnabled());
			gapCheck.setSelected(Sequence.DEFAULT_SHOW_GAP && gapCheck.isEnabled());
			centromereCheck.setSelected(Sequence.DEFAULT_SHOW_CENTROMERE && centromereCheck.isEnabled());
			rulerCheck.setSelected(Sequence.DEFAULT_SHOW_RULER);
			annotCheck.setSelected(Sequence.DEFAULT_SHOW_ANNOT && annotCheck.isEnabled());
			scoreLineCheck.setSelected(Sequence.DEFAULT_SHOW_SCORE_LINE); 	
			scoreValueCheck.setSelected(Sequence.DEFAULT_SHOW_SCORE_VALUE); 
			hitLenCheck.setSelected(Sequence.DEFAULT_SHOW_RIBBON); 			
			flippedCheck.setSelected(Sequence.DEFAULT_FLIPPED); 			

			geneFullRadio.setSelected(Sequence.DEFAULT_SHOW_GENE_FULL);

			startCombo.setSelectedItem(DEFAULT_ENDS_UNIT);
			endCombo.setSelectedItem(DEFAULT_ENDS_UNIT);
			startText.setText("0");
			long size = sequence.getTrackSize();
			Object item = endCombo.getSelectedItem();
			double end  = sequence.getValue(size, item.toString());
			endText.setText(end + "");
			//CAS512 unwind endText.setText(new Double(sequence.getValue(sequence.getTrackSize(),endCombo.getSelectedItem().toString())).toString());
		
			framePopupCheck.setState(Sequence.DEFAULT_SHOW_FRAME && framePopupCheck.isEnabled());
			genePopupCheck.setState(Sequence.DEFAULT_SHOW_GENE && genePopupCheck.isEnabled());
			geneLinePopupCheck.setState(Sequence.DEFAULT_SHOW_GENE_LINE && geneLinePopupCheck.isEnabled()); // CAS521 1st no Line
			gapPopupCheck.setState(Sequence.DEFAULT_SHOW_GAP && gapPopupCheck.isEnabled());
			centromerePopupCheck.setState(Sequence.DEFAULT_SHOW_CENTROMERE && centromerePopupCheck.isEnabled());
			rulerPopupCheck.setState(Sequence.DEFAULT_SHOW_RULER);
			annotPopupCheck.setState(Sequence.DEFAULT_SHOW_ANNOT && annotPopupCheck.isEnabled());
			scoreLinePopupCheck.setState(Sequence.DEFAULT_SHOW_SCORE_LINE);
			scoreValuePopupCheck.setState(Sequence.DEFAULT_SHOW_SCORE_VALUE);
			
			flippedPopupCheck.setState(Sequence.DEFAULT_FLIPPED); 
			hitLenPopupCheck.setState(Sequence.DEFAULT_SHOW_RIBBON); 
		}
	}

	private void setStart() {
		if (sequence == null) return;
		long start = sequence.getStart();
		Object item = startCombo.getSelectedItem();
		double val = sequence.getValue(start, item.toString()); // PseudoPseudoData.PseudoHitData
		startText.setText(val+"");
		// CAS512 Double startText.setText(new Double(sequence.getValue(sequence.getStart(),startCombo.getSelectedItem().toString())).toString());
	}

	private void setEnd() {
		if (sequence == null) return;
		long size = sequence.getTrackSize();
		Object item = endCombo.getSelectedItem();
		double end  = sequence.getValue(size, item.toString()); // PseudoPseudoData.PseudoHitData
		endText.setText(end + "");
		// CAS512 Double endText.setText(new Double(sequence.getValue(sequence.getEnd(),endCombo.getSelectedItem().toString())).toString());
	}

	/**
	 * stateChanged handles updating the Sequence track when a checkbox changes, but not start/end.
	 */
	public void stateChanged(ChangeEvent event) {
		if (sequence != null && !bNoChange) {
			boolean changed = false;	
			if (event.getSource() == gapCheck)				changed = sequence.showGap(gapCheck.isSelected());
			else if (event.getSource() == centromereCheck)	changed = sequence.showCentromere(centromereCheck.isSelected());
			else if (event.getSource() == frameCheck)		changed = sequence.showFrame(frameCheck.isSelected());
			else if (event.getSource() == geneCheck) {
				changed = sequence.showGene(geneCheck.isSelected());
				//geneFullRadio.setEnabled(geneCheck.isSelected());
				//geneMidRadio.setEnabled(geneCheck.isSelected());
				geneLineCheck.setEnabled(geneCheck.isSelected()); // CAS520
			}
			//else if (event.getSource() == geneFullRadio || event.getSource() == geneMidRadio)
			//	changed = sequence.showFullGene(geneFullRadio.isSelected());
			else if (event.getSource() == geneLineCheck) 	changed = sequence.showGeneLine(geneLineCheck.isSelected());
			else if (event.getSource() == rulerCheck) 		changed = sequence.showRuler(rulerCheck.isSelected());
			else if (event.getSource() == annotCheck)		changed = sequence.showAnnotation(annotCheck.isSelected());
			else if (event.getSource() == scoreLineCheck)	changed = sequence.showScoreLine(scoreLineCheck.isSelected());	
			else if (event.getSource() == scoreValueCheck)	changed = sequence.showScoreValue(scoreValueCheck.isSelected());
			else if (event.getSource() == hitLenCheck)		changed = sequence.showHitLen(hitLenCheck.isSelected());		
			else if (event.getSource() == flippedCheck) 	changed = sequence.flip(flippedCheck.isSelected()); 
			
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
				bFlipped = flippedPopupCheck.getState();
				changed = sequence.flip(bFlipped);
			}
			else if (event.getSource() == gapPopupCheck) {
				bGap = gapPopupCheck.getState();
				changed = sequence.showGap(bGap);
			}
			else if (event.getSource() == centromerePopupCheck) {
				bCentromere = centromerePopupCheck.getState();
				changed = sequence.showCentromere(bCentromere);
			}
			else if (event.getSource() == framePopupCheck) {
				bFrame = framePopupCheck.getState();
				changed = sequence.showFrame(bFrame);
			}
			else if (event.getSource() == genePopupCheck) {
				bGene = genePopupCheck.getState();
				changed = sequence.showGene(bGene);
				geneLinePopupCheck.setEnabled(bGene);
			}
			else if (event.getSource() == geneLinePopupCheck) {
				bGeneLine = geneLinePopupCheck.getState();
				changed = sequence.showGeneLine(bGeneLine);
			}
			else if (event.getSource() == rulerPopupCheck) {
				bRuler = rulerPopupCheck.getState();
				changed = sequence.showRuler(bRuler);
			}
			else if (event.getSource() == annotPopupCheck) {
				bAnnot = annotPopupCheck.getState();
				changed = sequence.showAnnotation(bAnnot);
			}
			else if (event.getSource() == scoreLinePopupCheck) {
				bScoreLine = scoreLinePopupCheck.getState();
				changed = sequence.showScoreLine(bScoreLine);
			}
			else if (event.getSource() == scoreValuePopupCheck) {
				bScoreValue = scoreValuePopupCheck.getState();
				changed = sequence.showScoreValue(bScoreValue);
			}
			else if (event.getSource() == hitLenPopupCheck) { 
				bHitLen = hitLenPopupCheck.getState();
				changed = sequence.showHitLen(bHitLen);
			}
				
			if (changed) {
				drawingPanel.setUpdateHistory();
				drawingPanel.smake();	
			}
		}
		super.actionPerformed(event);
	}
	
	public void popupMenuWillBecomeVisible(PopupMenuEvent event) { 
		int[] annotTypeCounts = sequence.getAnnotationTypeCounts();
		if (annotTypeCounts[Annotation.FRAMEWORK_INT] == 0) 		framePopupCheck.setEnabled(false);
		if (annotTypeCounts[Annotation.GENE_INT] == 0 && annotTypeCounts[Annotation.EXON_INT] == 0) 	{
			genePopupCheck.setEnabled(false);
			geneLinePopupCheck.setEnabled(false);
		}
		if (annotTypeCounts[Annotation.GAP_INT] == 0) 			gapPopupCheck.setEnabled(false);
		if (annotTypeCounts[Annotation.CENTROMERE_INT] == 0) 	centromerePopupCheck.setEnabled(false);
		if (annotTypeCounts[Annotation.GENE_INT] == 0) 			annotPopupCheck.setEnabled(false);
		
		bFrame = 	sequence.showFrame && framePopupCheck.isEnabled();
		bGene = 	sequence.showGene && genePopupCheck.isEnabled();
		bGeneLine = sequence.showGeneLine && geneLinePopupCheck.isEnabled(); // CAS520 add
		
		bGap = 		sequence.showGap && gapPopupCheck.isEnabled();
		bCentromere = sequence.showCentromere && centromerePopupCheck.isEnabled();
		bAnnot = 	sequence.showAnnot && annotPopupCheck.isEnabled();
		bRuler = 	sequence.showRuler;
		bScoreLine = sequence.showScoreLine;
		bScoreValue = sequence.showScoreValue;
		bFlipped = 	sequence.isFlipped();	
		bHitLen = 	sequence.showHitLen;	
		
		framePopupCheck.setState(bFrame);
		genePopupCheck.setState(bGene);
		geneLinePopupCheck.setState(bGeneLine);
		
		gapPopupCheck.setState(bGap);
		centromerePopupCheck.setState(bCentromere);
		annotPopupCheck.setState(bAnnot);
		rulerPopupCheck.setState(bRuler);
		scoreLinePopupCheck.setState(bScoreLine); 
		scoreValuePopupCheck.setState(bScoreValue); 
		hitLenPopupCheck.setState(bHitLen); 	 
		flippedPopupCheck.setState(bFlipped); 
	}
	// CAS514 would throw exception which stopped symap
	// CAS521 fixed problem where Apply would show the full chromosome, but keep start/end the same
	protected boolean okAction()  {
		if (sequence == null) return false;
		if (!sequence.hasInit()) return true;

		boolean changed = false;
		
	// Start/End  CAS521 evaluate first - rewritten; fixed bug that Apply would set it back if nothing set
		double tstart=0, tend=0;
		
		int eInd = 		endCombo.getSelectedIndex();
		String eStr = 	endText.getText();
		int sInd = 		startCombo.getSelectedIndex();
		String sStr = 	startText.getText();
		
		boolean numChgE = (eInd!=endInd || !eStr.contentEquals(endStr));
		boolean numChgS = (sInd!=startInd || !sStr.contentEquals(startStr));
		
		if (numChgS || numChgE) {
			try {
				tend = Double.parseDouble(eStr); // CAS512 (new Double(endText.getText())).doubleValue();
			} catch (NumberFormatException nfe) {
				Utilities.showErrorMessage(eStr + " is not a valid end point");
				return false;
			}
			tend = tend * GenomicsNumber.getUnitConversion(GenomicsNumber.ABS_UNITS[eInd]);
		
			try {
				tstart = Double.parseDouble(sStr); // CAS512 (new Double(startText.getText())).doubleValue();
			} catch (NumberFormatException nfe) {
				Utilities.showErrorMessage(sStr + " is not a valid start point");
				return changed;
			}
			tstart = tstart * GenomicsNumber.getUnitConversion(GenomicsNumber.ABS_UNITS[sInd]);
		
			if (tstart>=tend) {
				Utilities.showErrorMessage("The start (" + tstart + ") must be less than end (" + tend + ")");
				return false;
			}
			endStr = endText.getText();
			endInd = endCombo.getSelectedIndex();
			sequence.setEnd((long)tend);
			
			startStr = startText.getText();
			startInd = startCombo.getSelectedIndex();
			sequence.setStart((long)tstart);
			
			changed=true;
		}
		
	// Check boxes
		if (bFlipped != flippedCheck.isSelected()) { 
			bFlipped = !bFlipped;
			changed = true;
		}
		if (bFrame != frameCheck.isSelected()) {
			bFrame = !bFrame;
			changed = true;
		}
		if (bGene != geneCheck.isSelected()) {
			bGene = !bGene;
			changed = true;
		}
		if (bGeneLine != geneLineCheck.isSelected()) {
			bGeneLine = !bGeneLine;
			changed = true;
		}
		if (bGeneFull != geneFullRadio.isSelected()) {
			bGeneFull = !bGeneFull;
			if (bGene) changed = true; // don't care if gene isn't selected any more
		}
		if (bGap != gapCheck.isSelected()) {
			bGap = !bGap;
			changed = true;
		}
		if (bCentromere != centromereCheck.isSelected()) {
			bCentromere = !bCentromere;
			changed = true;
		}
		if (bRuler != rulerCheck.isSelected()) {
			bRuler = !bRuler;
			changed = true;
		}
		if (bAnnot != annotCheck.isSelected()) {
			bAnnot = !bAnnot;
			changed = true;
		}
		if (bScoreLine != scoreLineCheck.isSelected()) {	
			bScoreLine = !bScoreLine;						
			changed = true;								
		}
		if (bScoreValue != scoreValueCheck.isSelected()) {	
			bScoreValue = !bScoreValue;						
			changed = true;									
		}
		if (bHitLen != hitLenCheck.isSelected()) {	
			bHitLen = !bHitLen;						
			changed = true;							
		}
		
		return !sequence.hasBuilt() || changed;
	}

	protected void cancelAction() {
		frameCheck.setSelected(bFrame);
		geneCheck.setSelected(bGene);
		geneLineCheck.setSelected(bGeneLine);
		geneFullRadio.setSelected(bGeneFull);
		gapCheck.setSelected(bGap);
		centromereCheck.setSelected(bCentromere);
		rulerCheck.setSelected(bRuler);
		annotCheck.setSelected(bAnnot);
		scoreLineCheck.setSelected(bScoreLine); 
		scoreValueCheck.setSelected(bScoreValue); 
		hitLenCheck.setSelected(bHitLen); 
		startText.setText(startStr);
		startCombo.setSelectedIndex(startInd);
		endText.setText(endStr);
		endCombo.setSelectedIndex(endInd);
		flippedCheck.setSelected(bFlipped); 
	}

	private void setFullSequence() {
		startCombo.setSelectedItem(DEFAULT_ENDS_UNIT);
		endCombo.setSelectedItem(DEFAULT_ENDS_UNIT);
		startText.setText("0"); // CAS512 new Double(0).toString()
		long size = sequence.getTrackSize();
		Object item = endCombo.getSelectedItem();
		double end  = sequence.getValue(size, item.toString());
		endText.setText(end + "");
		// CAS512 endText.setText(new Double(sequence.getValue(sequence.getTrackSize(),endCombo.getSelectedItem().toString())).toString());
	}
}

