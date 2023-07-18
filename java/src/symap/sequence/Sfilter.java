package symap.sequence;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.event.ActionEvent;

import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem; 
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import number.GenomicsNumber;
import symap.drawingpanel.DrawingPanel;
import util.ErrorReport;
import util.Jhtml;
import util.Utilities;

/**
 * The filter dialog for the Sequence view.
 * Also see FilterHandler.java, Sequence.java, SequenceTrackData.java
 * CAS542 stopped using the abstract Filter, and cleaned up 
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class Sfilter extends JDialog {	
	//private static boolean DEBUG = Globals.DEBUG;
	private static final String DEFAULT_ENDS_UNIT = GenomicsNumber.KB;
	
	// CAS542 moved from Sequence to here; current state is in Sequence variables; state on open is in this file.
	// CAS543 after rewrite; was being changed from Query, but applied to all 2D displays so just quit doing
	public static final boolean DEFAULT_FLIPPED = false;
	public static final boolean DEFAULT_SHOW_ANNOT  	= false;  
	public static final boolean DEFAULT_SHOW_GENE       = true; 
	public static final boolean DEFAULT_SHOW_GENE_LINE  = false; 
	public static final boolean DEFAULT_HIGH_GENE = true;	
	public static final boolean DEFAULT_SHOW_RULER      = true;
	public static final boolean DEFAULT_SHOW_GAP        = true;
	public static final boolean DEFAULT_SHOW_CENTROMERE = true;
	public static final boolean DEFAULT_SHOW_SCORE_VALUE= false;
	public static final boolean DEFAULT_SHOW_HIT_NUM = false;	
	public static final boolean DEFAULT_SHOW_SCORE_LINE	= true;  
	public static final boolean DEFAULT_SHOW_HITLEN		= true;	 
	public static final boolean DEFAULT_SHOW_GENE_FULL  = true; 

	private JButton okButton, cancelButton, defaultButton, helpButton;
	private JPanel buttonPanel;
	private JPopupMenu popup; 
	private JMenuItem popupTitle; 
	
	private JButton fullButton;
	private JLabel startLabel, endLabel;
	private JTextField startText, endText;
	private JComboBox <String> startCombo, endCombo; // CAS514 add type
	
	//CAS520 removed geneFullRadio, geneMidRadio, frameCheck (FPC)
	private JCheckBox flippedCheck, rulerCheck, annotCheck, geneHighCheck; 
	private JCheckBox geneCheck, geneLineCheck, gapCheck, centromereCheck; // CAS520 add line
	private JCheckBox scoreLineCheck, hitLenCheck; 						
	private JRadioButton scoreTextRadio, hitNumTextRadio, noneTextRadio; // CAS531 add hitNum
	
	private JCheckBoxMenuItem flippedPopup, rulerPopup, annotPopup; 						
	private JCheckBoxMenuItem geneLinePopup, geneHighPopup; // CAS520 add line ; CAS544 rm gap, cent, lines; add highPopup			 			
	private JCheckBoxMenuItem scoreTextPopup, hitNumTextPopup; 						
	//private JMenuItem fullSequencePopupItem; 							

	private Sequence seqObj;
	protected DrawingPanel drawingPanel;

	private String savStartStr, savEndStr;
	private int savStartInd, savEndInd;
	private boolean bSavFlipped=false, bSavRuler=true, bSavAnnot=true, bSavGeneHigh=false; 
	private boolean bSavGene=true, bSavGeneLine=false, bSavGap=true, bSavCentromere=true;
	private boolean bSavScoreLine=true, bSavHitLen=true;
	private boolean bSavScoreText=false, bSavHitNumText=false; 				
	private boolean bNoChange = false;

	// Created when 2d is displayed, one for each track
	public Sfilter(Frame owner, DrawingPanel dp,  Sequence sequence) { // CAS542 removed helpBut 
		super(owner,"Sequence Filter", true); // CAS532 added help
		this.seqObj = sequence;
		this.drawingPanel = dp;
		
		FilterListener listener = new FilterListener();
		
	/* Filter dialog */
		// Buttons
		okButton = createButton("Apply","Apply coordinate changes and close");
		okButton.addActionListener(listener);

		cancelButton = createButton("Cancel", "Discard changes and close");
		cancelButton.addActionListener(listener);

		defaultButton = createButton("Defaults", "Reset to defaults");
		defaultButton.addActionListener(listener);
		
		helpButton = Jhtml.createHelpIconUserSm(Jhtml.seqfilter);

		buttonPanel = new JPanel(new BorderLayout());
		buttonPanel.add(new JSeparator(),BorderLayout.NORTH);
		JPanel innerPanel = new JPanel();
		innerPanel.add(okButton);
		innerPanel.add(cancelButton);
		innerPanel.add(defaultButton);
		innerPanel.add(helpButton);
		buttonPanel.add(innerPanel,BorderLayout.CENTER);
		
		// Coordinate options; takes effect on Ok
		fullButton = new JButton("Full sequence");
		fullButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setFullSequence();
			}
		});
		JPanel fullPanel = new JPanel(); fullPanel.add(fullButton); fullPanel.add(new JLabel("        (Apply)"));
		fullPanel.setLayout(new BoxLayout(fullPanel, BoxLayout.LINE_AXIS));
		fullPanel.setMaximumSize(fullPanel.getPreferredSize());

		startLabel = new JLabel("            Start"); startText = new JTextField("",10); // CAS520 was 15
		startCombo = new JComboBox <String>(GenomicsNumber.ABS_UNITS);
		startCombo.setSelectedItem(DEFAULT_ENDS_UNIT);
		JPanel startPanel = new JPanel(); startPanel.add(startText); startPanel.add(startCombo);
		startPanel.setLayout(new BoxLayout(startPanel, BoxLayout.LINE_AXIS));
		startPanel.setMaximumSize(startPanel.getPreferredSize());startPanel.setMinimumSize(startPanel.getPreferredSize());
		
		endLabel =   new JLabel("            End  "); endText =   new JTextField("",10);
		endCombo =   new JComboBox <String>(GenomicsNumber.ABS_UNITS);
		endCombo.setSelectedItem(DEFAULT_ENDS_UNIT);
		JPanel endPanel = new JPanel(); endPanel.add(endText); endPanel.add(endCombo);
		endPanel.setLayout(new BoxLayout(endPanel, BoxLayout.LINE_AXIS));
		endPanel.setMaximumSize(endPanel.getPreferredSize());endPanel.setMinimumSize(endPanel.getPreferredSize());

		// Checkbox options; takes effect immediately 
		flippedCheck = new JCheckBox("Flip sequence"); 	
				
		annotCheck 		= new JCheckBox("Annotation (zoom in)");	
		geneCheck 		= new JCheckBox("Genes");         
		geneLineCheck 	= new JCheckBox("Gene delimiter");
		geneHighCheck = new JCheckBox("Highlight gene popup"); 
		
		rulerCheck 		= new JCheckBox("Ruler");	
		gapCheck 		= new JCheckBox("Gaps");			
		centromereCheck = new JCheckBox("Centromere");		
				
		hitLenCheck 	= new JCheckBox("Hit length");		
		scoreLineCheck 	= new JCheckBox("Hit %Id bar");	
			
		scoreTextRadio 		= new JRadioButton("Hit %Id");
		hitNumTextRadio 	= new  JRadioButton("Hit#");
		noneTextRadio 		= new  JRadioButton("None");
		ButtonGroup geneBG = new ButtonGroup();
		geneBG.add(scoreTextRadio);
		geneBG.add(hitNumTextRadio);
		geneBG.add(noneTextRadio); noneTextRadio.setSelected(true);
		JPanel textPanel = new JPanel();
		textPanel.add(scoreTextRadio);
		textPanel.add(hitNumTextRadio);
		textPanel.add(noneTextRadio);
		textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.LINE_AXIS));

		Container contentPane = getContentPane();
		GridBagLayout gridbag = new GridBagLayout();
		GridBagConstraints constraints = new GridBagConstraints();

		contentPane.setLayout(gridbag);
		constraints.gridheight = 1;
		constraints.ipadx = 5;
		constraints.ipady = 8;
		constraints.anchor = GridBagConstraints.WEST;
		constraints.fill = GridBagConstraints.HORIZONTAL;

		int rem = GridBagConstraints.REMAINDER;
		addToGrid(contentPane, gridbag, constraints, flippedCheck, 1); 
		addToGrid(contentPane, gridbag, constraints, geneHighCheck, rem); 
		
		addToGrid(contentPane, gridbag, constraints, new JSeparator(), rem);
		addToGrid(contentPane, gridbag, constraints, new JLabel("  Show Graphics"), rem); 
		addToGrid(contentPane, gridbag, constraints, rulerCheck, 1);
		addToGrid(contentPane, gridbag, constraints, annotCheck, rem); // CAS503 move
		
		addToGrid(contentPane, gridbag, constraints, geneCheck,  1);
		addToGrid(contentPane, gridbag, constraints, geneLineCheck, rem); // CAS520 add
		
		addToGrid(contentPane, gridbag, constraints, gapCheck, 1);
		addToGrid(contentPane, gridbag, constraints, centromereCheck, rem);
		
		addToGrid(contentPane, gridbag, constraints, hitLenCheck, 1);     
		addToGrid(contentPane, gridbag, constraints, scoreLineCheck, rem); 
		
		addToGrid(contentPane, gridbag, constraints, new JSeparator(), rem);
		addToGrid(contentPane, gridbag, constraints, new JLabel("  Show Text    "), 1); 
		addToGrid(contentPane, gridbag, constraints, textPanel, rem);
		
		addToGrid(contentPane, gridbag, constraints, new JSeparator(),rem);
		addToGrid(contentPane, gridbag, constraints, new JLabel("  Coordinates   "), 1);
		addToGrid(contentPane, gridbag, constraints, fullPanel, rem);
		
		addToGrid(contentPane, gridbag, constraints, startLabel, 1);
		addToGrid(contentPane, gridbag, constraints, startPanel, rem);
		
		addToGrid(contentPane, gridbag, constraints, endLabel, 1);
		addToGrid(contentPane, gridbag, constraints, endPanel, rem);

		addToGrid(contentPane, gridbag, constraints, buttonPanel, rem);

		pack();
		setResizable(false);
		setLocationRelativeTo(null); // CAS520
		
		// All check boxes are applied immediately but the start/end only on Apply
		flippedCheck.addChangeListener(listener);
		geneHighCheck.addChangeListener(listener);
		rulerCheck.addChangeListener(listener);
		annotCheck.addChangeListener(listener);
		geneCheck.addChangeListener(listener);
		geneLineCheck.addChangeListener(listener);
		gapCheck.addChangeListener(listener);
		centromereCheck.addChangeListener(listener);
		scoreLineCheck.addChangeListener(listener);		
		hitLenCheck.addChangeListener(listener);	
		scoreTextRadio.addChangeListener(listener);	
		hitNumTextRadio.addChangeListener(listener);
		noneTextRadio.addChangeListener(listener);
		
	/** Popup **/
		popup = new JPopupMenu(); 
		popup.addPopupMenuListener(new MyPopupMenuListener()); 
		popupTitle = new JMenuItem();
		popupTitle.setEnabled(false);
		popup.add(popupTitle);
		popup.addSeparator();
		
		//fullSequencePopupItem 	= new JMenuItem("Full Sequence");
		flippedPopup 		= new JCheckBoxMenuItem("Flip sequence"); 
		rulerPopup 			= new JCheckBoxMenuItem("Ruler"); 
		annotPopup			= new JCheckBoxMenuItem("Annotation (zoom in)"); 
		geneLinePopup 		= new JCheckBoxMenuItem("Gene delimiter"); 
		geneHighPopup 		= new JCheckBoxMenuItem("Highlight gene popup"); 
		scoreTextPopup 		= new JCheckBoxMenuItem("Hit %Id text"); 	// ditto
		hitNumTextPopup 	= new JCheckBoxMenuItem("Hit# text"); 	// CAS531 add
	
		popupTitle.setText("Sequence Show Options:"); 
		popup.add(flippedPopup); 	flippedPopup.addActionListener(listener);
		popup.add(rulerPopup);		rulerPopup.addActionListener(listener);
		popup.add(annotPopup); 		annotPopup.addActionListener(listener);
		popup.add(geneLinePopup);	geneLinePopup.addActionListener(listener);	
		popup.add(scoreTextPopup);	scoreTextPopup.addActionListener(listener);
		popup.add(hitNumTextPopup); hitNumTextPopup.addActionListener(listener);
		popup.add(new JSeparator());
		popup.add(geneHighPopup); 	geneHighPopup.addActionListener(listener);
		//popup.add(fullSequencePopupItem); not working...fullSequencePopupItem.addActionListener(listener);
	}
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
	public void showPopup(MouseEvent e) { 
		popup.show(e.getComponent(), e.getX(), e.getY());
	}	
	public boolean canShow() {
		if (seqObj == null) return false;
		return seqObj.hasInit();
	}
	
	public void closeFilter() {
		if (isShowing()) {
			cancelAction();
			setVisible(false); // CAS512 hide();
		}
	}

	/**
	 * Called when Sequence Filter button clicked
	 */
	public void showX() {
		if (isShowing()) { // always false
			super.setVisible(true);
			return;
		}
		int[] annotTypeCounts = seqObj.getAnnotationTypeCounts(); 
		if (annotTypeCounts[Annotation.GENE_INT] == 0 && annotTypeCounts[Annotation.EXON_INT] == 0) {
			geneCheck.setEnabled(false);
			geneLineCheck.setEnabled(false);
			geneHighCheck.setEnabled(false);
			annotCheck.setEnabled(false);
		}
		if (annotTypeCounts[Annotation.GAP_INT] == 0) 		 gapCheck.setEnabled(false);
		if (annotTypeCounts[Annotation.CENTROMERE_INT] == 0) centromereCheck.setEnabled(false);
		if (annotTypeCounts[Annotation.GENE_INT] == 0)		 annotCheck.setEnabled(false);

		saveSettingsFromSeq();
		
		setStart();
		setEnd();
		
		flippedCheck.setSelected(bSavFlipped); 
		rulerCheck.setSelected(bSavRuler);
		
		annotCheck.setSelected(bSavAnnot && annotCheck.isEnabled());
		geneCheck.setSelected(bSavGene && geneCheck.isEnabled());
		geneLineCheck.setSelected(bSavGeneLine && geneLineCheck.isEnabled()); // CAS520 add all geneLine lines
		geneHighCheck.setSelected(bSavGeneHigh && geneHighCheck.isEnabled()); 
		
		gapCheck.setSelected(bSavGap && gapCheck.isEnabled());
		centromereCheck.setSelected(bSavCentromere && centromereCheck.isEnabled());

		scoreLineCheck.setSelected(bSavScoreLine);	
		hitLenCheck.setSelected(bSavHitLen);	
		
		scoreTextRadio.setSelected(bSavScoreText);
		hitNumTextRadio.setSelected(bSavHitNumText);
		noneTextRadio.setSelected(bSavScoreText==false && bSavHitNumText==false);		
			
		super.setVisible(true); // CAS512 super.show();
	}
	
	private void saveSettingsFromSeq() {
		savStartStr = startText.getText();
		savEndStr =   endText.getText();
		
		savStartInd = startCombo.getSelectedIndex();
		savEndInd =   endCombo.getSelectedIndex();

		bSavFlipped = 	  seqObj.isFlipped();	
		
		bSavAnnot = 	  seqObj.bShowAnnot    && annotPopup.isEnabled();
		bSavGene = 	  	  seqObj.bShowGene     && geneCheck.isEnabled();
		bSavGeneLine =    seqObj.bShowGeneLine && geneLinePopup.isEnabled(); 
		bSavGeneHigh =    seqObj.bHighGene     && geneHighPopup.isEnabled(); 
		
		bSavRuler = 	  seqObj.bShowRuler;
		bSavGap = 		  seqObj.bShowGap        && gapCheck.isEnabled();
		bSavCentromere =  seqObj.bShowCentromere && centromereCheck.isEnabled();
		
		bSavScoreLine =   seqObj.bShowScoreLine;
		bSavHitLen = 	  seqObj.bShowHitLen;	
		bSavScoreText =   seqObj.bShowScoreText;
		bSavHitNumText =  seqObj.bShowHitNumText;
	}
	protected void cancelAction() {
		startText.setText(savStartStr); startCombo.setSelectedIndex(savStartInd);
		endText.setText(savEndStr);     endCombo.setSelectedIndex(savEndInd);
		flippedCheck.setSelected(bSavFlipped); 
		
		annotCheck.setSelected(bSavAnnot);
		geneCheck.setSelected(bSavGene);
		geneLineCheck.setSelected(bSavGeneLine);
		geneHighCheck.setSelected(bSavGeneHigh);
		
		gapCheck.setSelected(bSavGap);
		centromereCheck.setSelected(bSavCentromere);
		rulerCheck.setSelected(bSavRuler);
		scoreLineCheck.setSelected(bSavScoreLine); 
		hitLenCheck.setSelected(bSavHitLen); 
		scoreTextRadio.setSelected(bSavScoreText); 
		hitNumTextRadio.setSelected(bSavHitNumText);
		noneTextRadio.setSelected(!bSavScoreText && !bSavHitNumText);
	}
	protected void setDefault() {
		if (seqObj == null) return;
		
		startCombo.setSelectedItem(DEFAULT_ENDS_UNIT);
		endCombo.setSelectedItem(DEFAULT_ENDS_UNIT);
		
		startText.setText("0");
		
		long size = seqObj.getTrackSize();
		Object item = endCombo.getSelectedItem();
		double end  = seqObj.getValue(size, item.toString());
		endText.setText(end + "");
		
		flippedCheck.setSelected(false); 	
		rulerCheck.setSelected(DEFAULT_SHOW_RULER);
		
		annotCheck.setSelected(DEFAULT_SHOW_ANNOT);
		geneCheck.setSelected(DEFAULT_SHOW_GENE 			&& geneCheck.isEnabled());
		geneLineCheck.setSelected(DEFAULT_SHOW_GENE_LINE);
		geneHighCheck.setSelected(DEFAULT_HIGH_GENE 		&& geneHighCheck.isEnabled());
		
		gapCheck.setSelected(DEFAULT_SHOW_GAP 				&& gapCheck.isEnabled());
		centromereCheck.setSelected(DEFAULT_SHOW_CENTROMERE && centromereCheck.isEnabled());
		
		scoreLineCheck.setSelected(DEFAULT_SHOW_SCORE_LINE); 	
		hitLenCheck.setSelected(DEFAULT_SHOW_HITLEN); 
		
		scoreTextRadio.setSelected(false);
		hitNumTextRadio.setSelected(false);
		noneTextRadio.setSelected(true);
		
		flippedPopup.setState(DEFAULT_FLIPPED);
		
		annotPopup.setState(DEFAULT_SHOW_ANNOT);
		geneLinePopup.setState(DEFAULT_SHOW_GENE_LINE); 								// CAS521 1st no Line
		geneHighPopup.setState(DEFAULT_HIGH_GENE         && geneHighPopup.isEnabled());
		
		rulerPopup.setState(DEFAULT_SHOW_RULER);
		
		scoreTextPopup.setState(DEFAULT_SHOW_SCORE_VALUE);
		hitNumTextPopup.setState(false);
	}

	private void setStart() {
		if (seqObj == null) return;
		long start = seqObj.getStart();
		Object item = startCombo.getSelectedItem();
		double val = seqObj.getValue(start, item.toString()); 
		startText.setText(val+"");
	}
	private void setEnd() {
		if (seqObj == null) return;
		long end = seqObj.getEnd(); // CAS543 sequence.getTrackSize();
		Object item = endCombo.getSelectedItem();
		double val  = seqObj.getValue(end, item.toString()); 
		endText.setText(val + "");
	}
	private void setFullSequence() {
		startCombo.setSelectedItem(DEFAULT_ENDS_UNIT);
		endCombo.setSelectedItem(DEFAULT_ENDS_UNIT);
		startText.setText("0"); 
		long size = seqObj.getTrackSize();
		Object item = endCombo.getSelectedItem();
		double end  = seqObj.getValue(size, item.toString());
		endText.setText(end + "");
	}
	
	// CAS514 would throw exception which stopped symap
	// CAS521 fixed problem where Apply would show the full chromosome, but keep start/end the same
	// Check boxes are already processed; the change in coords get processed on OK
	protected boolean okAction()  {
		if (seqObj == null) return false;
		if (!seqObj.hasInit()) return true;

	// Start/End  CAS521 evaluate first - rewritten; fixed bug that Apply would set it back if nothing set
		
		int eInd = 		endCombo.getSelectedIndex();
		String eStr = 	endText.getText();
		int sInd = 		startCombo.getSelectedIndex();
		String sStr = 	startText.getText();
		
		boolean numChgE = (eInd!=savEndInd   || !eStr.contentEquals(savEndStr));
		boolean numChgS = (sInd!=savStartInd || !sStr.contentEquals(savStartStr));
		
		if (numChgS || numChgE) {
			double tstart=0, tend=0;
			
			try {
				tstart = Double.parseDouble(sStr); // CAS512 (new Double(startText.getText())).doubleValue();
			} catch (NumberFormatException nfe) {
				Utilities.showErrorMessage(sStr + " is not a valid start point");
				return false;
			}
			tstart = tstart * GenomicsNumber.getUnitConversion(GenomicsNumber.ABS_UNITS[sInd]);
		
			try {
				tend = Double.parseDouble(eStr); // CAS512 (new Double(endText.getText())).doubleValue();
			} catch (NumberFormatException nfe) {
				Utilities.showErrorMessage(eStr + " is not a valid end point");
				return false;
			}
			tend = tend * GenomicsNumber.getUnitConversion(GenomicsNumber.ABS_UNITS[eInd]);
			
			if (tstart>=tend) {
				Utilities.showErrorMessage("The start (" + tstart + ") must be less than end (" + tend + ")");
				return false;
			}
			
			seqObj.setStart((long)tstart);// saved in Track
			seqObj.setEnd((long)tend);
			
			drawingPanel.setUpdateHistory();
			drawingPanel.smake();	
		}
		return true;
	}
	
	/************************************************************************************/
	private class FilterListener implements ActionListener, ChangeListener, ItemListener {
		private FilterListener() { }

		// updating the Sequence track when a checkbox changes, but not start/end.
		public void stateChanged(ChangeEvent event) {
			if (seqObj == null || bNoChange) return;
			
			Object src = event.getSource();
			boolean bDiff = false;
			
			if (src == flippedCheck) 		bDiff = seqObj.flipSeq(flippedCheck.isSelected());
			
			else if (src == annotCheck)		bDiff = seqObj.showAnnotation(annotCheck.isSelected());
			else if (src == geneCheck) {
				boolean b = geneCheck.isSelected();
				bDiff = seqObj.showGene(b);
				geneLineCheck.setEnabled(b); // CAS520
				geneHighCheck.setEnabled(b);
			}
			else if (src == geneLineCheck) 	bDiff = seqObj.showGeneLine(geneLineCheck.isSelected());
			else if (src == geneHighCheck)	bDiff = seqObj.highGenePopup(geneHighCheck.isSelected());
			
			else if (src == rulerCheck) 	bDiff = seqObj.showRuler(rulerCheck.isSelected());
			else if (src == gapCheck)		bDiff = seqObj.showGap(gapCheck.isSelected());
			else if (src == centromereCheck)bDiff = seqObj.showCentromere(centromereCheck.isSelected());
			
			else if (src == scoreLineCheck)	bDiff = seqObj.showScoreLine(scoreLineCheck.isSelected());	
			else if (src == hitLenCheck)	bDiff = seqObj.showHitLen(hitLenCheck.isSelected());		
			 
			else if (src == scoreTextRadio) bDiff = seqObj.showScoreText(scoreTextRadio.isSelected());
			else if (src == hitNumTextRadio)bDiff = seqObj.showHitNumText(hitNumTextRadio.isSelected());
			else if (src == noneTextRadio)	{
				boolean isSet = noneTextRadio.isSelected();
				if (isSet) {
					bDiff   	= seqObj.showScoreText(false);
					boolean b 	= seqObj.showHitNumText(false);
					if (!bDiff) bDiff=b;
				} 
			}
			
			if (bDiff) {
				drawingPanel.setUpdateHistory();
				drawingPanel.smake();
			}
		}
				
		// only called for buttons and popup menu events
		public void actionPerformed(ActionEvent event) { 
			if (seqObj == null) {
				ErrorReport.print("No sequence with filter");
				return;
			}
			Object src = event.getSource();
			boolean bDiff = false;	

			if (src == okButton) { // changed already made except of for sequence
				okAction();
				setVisible(false); 
			}
			else if (src == cancelButton) {
				cancelAction();
				setVisible(false); 
			}
			else if (src == defaultButton) {
				setDefault();
				bDiff = true;; 
			}
			// popup
			else if (src == flippedPopup) 		bDiff = seqObj.flipSeq(flippedPopup.getState());
			else if (src == geneLinePopup) 		bDiff = seqObj.showGeneLine(geneLinePopup.getState());
			else if (src == geneHighPopup) 		bDiff = seqObj.highGenePopup(geneHighPopup.getState());
			else if (src == annotPopup) 		bDiff = seqObj.showAnnotation(annotPopup.getState());
			else if (src == rulerPopup) 		bDiff = seqObj.showRuler(rulerPopup.getState());
			else if (src == scoreTextPopup) {
				boolean b = scoreTextPopup.getState();
				bDiff = seqObj.showScoreText(b);
				if (b)  seqObj.showHitNumText(false);
			}
			else if (src == hitNumTextPopup) {
				boolean b = hitNumTextPopup.getState();
				bDiff = seqObj.showHitNumText(b);
				if (b)  seqObj.showScoreText(false);
			}
			if (bDiff) {
				drawingPanel.setUpdateHistory();
				drawingPanel.smake();	
			}
		}
		public void itemStateChanged(ItemEvent evt) {}
	} // end listener
	
	// called when popup become visible
	class MyPopupMenuListener implements PopupMenuListener {
		  public void popupMenuCanceled(PopupMenuEvent popupMenuEvent) {}

		  public void popupMenuWillBecomeInvisible(PopupMenuEvent popupMenuEvent) {}

		  public void popupMenuWillBecomeVisible(PopupMenuEvent event) { 
			saveSettingsFromSeq();

			annotPopup.setEnabled(bSavGene);
			geneLinePopup.setEnabled(bSavGene);
			geneHighPopup.setEnabled(bSavGene);
			
			flippedPopup.setState(bSavFlipped); 
			rulerPopup.setState(bSavRuler);
			annotPopup.setState(bSavAnnot);
			geneLinePopup.setState(bSavGeneLine);
			geneHighPopup.setState(bSavGeneHigh); 
			scoreTextPopup.setState(bSavScoreText); 
			hitNumTextPopup.setState(bSavHitNumText);
		}
	} // end popup listener
}

