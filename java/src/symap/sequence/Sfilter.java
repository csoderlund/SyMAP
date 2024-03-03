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
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import symap.Globals;
import symap.drawingpanel.DrawingPanel;
import util.ErrorReport;
import util.Jhtml;
import util.Utilities;

/**
 * The filter dialog for the Sequence view; 
 * 	it is associated with given sequence object, but if that seqObj is a assigned to a different track...
 * Known problem: if Save, then change assignments of tracks, it gets Checks from track; problem since beginning...
 * Also see FilterHandler.java, Sequence.java, TrackData.java
 * CAS542 stopped using the abstract Filter, and cleaned up 
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class Sfilter extends JDialog {	
	private static final String DEFAULT_ENDS_UNIT = BpNumber.KB;
	
	// CAS542 moved from Sequence to here; current state is in Sequence variables; state on open is in this file.
	// CAS543 after rewrite; was being changed from Query, but applied to all 2D displays so just quit doing
	// CAS545 add selectGene and Conserved, both are special cases because they highlight genes (and hits) 
	// WHEN ALTER these, alter in Sequence and TrackData!
	static public final boolean bDefRuler=true, bDefGene=true, bDefGap=true, bDefCentromere=true;
	static public final boolean bDefScoreLine=true, bDefHitLen=true, bDefGeneHigh=true, bDefNoText=true;
	static public final boolean bDefScoreText=false, bDefHitNumText=false, bDefBlockText=false, bDefCsetText=false;
	static public final boolean bDefFlipped=false,  bDefAnnot=false, bDefGeneLine=false;
	static public final boolean bDefSelectGene=false, bDefConserved=false;
	
	private JButton okButton, cancelButton, defaultButton, helpButton;
	private JPanel buttonPanel;
	private JPopupMenu popup=null; 
	private JMenuItem popupTitle; 
	
	private JButton fullButton;
	private JLabel geneLabel;
	private JTextField startText, endText, geneText;
	private JComboBox <String> startCombo, endCombo; // CAS514 add type
	
	//CAS520 removed geneFullRadio, geneMidRadio, frameCheck (FPC)
	private JCheckBox flippedCheck, rulerCheck, annotCheck, geneHighCheck, conservedCheck; 
	private JCheckBox geneCheck, geneLineCheck, gapCheck, centromereCheck; // CAS520 add line
	private JCheckBox scoreLineCheck, hitLenCheck; 						
	private JRadioButton scoreTextRadio, hitNumTextRadio, blockTextRadio, csetTextRadio, noTextRadio; // CAS531 add hitNum
	
	private JCheckBoxMenuItem flippedPopup, rulerPopup, annotPopup; 						
	private JCheckBoxMenuItem geneLinePopup, geneHighPopup; // CAS520 add line ; CAS544 rm gap, cent, lines; add highPopup			 			
	private JRadioButtonMenuItem scoreTextPopup, hitNumTextPopup, blockTextPopup, csetTextPopup, noTextPopup; 						
	//private JMenuItem fullSequencePopupItem; 							

	private Sequence seqObj;
	private DrawingPanel drawingPanel;

	private String savStartStr, savEndStr, savGeneStr;
	private int savStartInd, savEndInd;
	private boolean	bSavFlipped, bSavRuler, bSavAnnot, bSavGeneHigh; 
	private boolean bSavGene, bSavGeneLine, bSavGap, bSavCentromere;
	private boolean bSavScoreLine, bSavHitLen;
	private boolean bSavScoreText, bSavHitNumText; 	
	private boolean bSavBlockText, bSavCsetText, bSavNoText;
	private boolean bSavConserved;
	
	// Created when 2d is displayed, one for each sequence; its associated with the track, but the track can change...
	public Sfilter(Frame owner, DrawingPanel dp,  Sequence sequence) { // CAS542 removed helpBut 
		super(owner,"Sequence Filter", true); // CAS532 added help
		this.seqObj = sequence;
		this.drawingPanel = dp;		
		FilterListener listener = new FilterListener();
		
	/* Filter dialog */
		// Buttons
		okButton = createButton("Save","Apply coordinate changes, save all changes, and close");
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
		
		// Coordinate options; takes effect on Apply
		JLabel applyLabel = new JLabel("        Save to apply changes");
		JLabel startLabel = new JLabel("            Start"); 
		JLabel endLabel =   new JLabel("            End  ");
		geneLabel =    		new JLabel("         or Gene#"); 
		
		startText = new JTextField("",10); // CAS520 was 15
		startCombo = new JComboBox <String>(BpNumber.ABS_UNITS);
		startCombo.setSelectedItem(DEFAULT_ENDS_UNIT);
		JPanel startPanel = new JPanel(); startPanel.add(startText); startPanel.add(startCombo);
		startPanel.setLayout(new BoxLayout(startPanel, BoxLayout.LINE_AXIS));
		startPanel.setMaximumSize(startPanel.getPreferredSize());startPanel.setMinimumSize(startPanel.getPreferredSize());
				 
		endText =   new JTextField("",10);
		endCombo =  new JComboBox <String>(BpNumber.ABS_UNITS);
		endCombo.setSelectedItem(DEFAULT_ENDS_UNIT);
		JPanel endPanel = new JPanel(); endPanel.add(endText); endPanel.add(endCombo);
		endPanel.setLayout(new BoxLayout(endPanel, BoxLayout.LINE_AXIS));
		endPanel.setMaximumSize(endPanel.getPreferredSize());endPanel.setMinimumSize(endPanel.getPreferredSize());

		geneText =   new JTextField("",10); // CAS545 add
		JLabel x = new JLabel(" Region " + Globals.MAX_RANGE/1000 + "KB  ");
		JPanel genePanel = new JPanel(); 
		genePanel.add(geneText); genePanel.add(x);
		genePanel.setLayout(new BoxLayout(genePanel, BoxLayout.LINE_AXIS));
		genePanel.setMaximumSize(genePanel.getPreferredSize());genePanel.setMinimumSize(genePanel.getPreferredSize());
		
		// Checkbox options; takes effect immediately 
		flippedCheck = new JCheckBox("Flip sequence    "); 			flippedCheck.addChangeListener(listener);
		fullButton = createButton("Full", "Full Sequence");
		fullButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setFullSequence();
			}
		});
		JPanel flipPanel = new JPanel(); flipPanel.add(flippedCheck); flipPanel.add(fullButton);
		flipPanel.setLayout(new BoxLayout(flipPanel, BoxLayout.LINE_AXIS));
		flipPanel.setMaximumSize(flipPanel.getPreferredSize());flipPanel.setMinimumSize(flipPanel.getPreferredSize());
				
		annotCheck 		= new JCheckBox("Annotation (zoom in)");	annotCheck.addChangeListener(listener);
		geneCheck 		= new JCheckBox("Genes");    				geneCheck.addChangeListener(listener);     
		geneLineCheck 	= new JCheckBox("Gene delimiter");			geneLineCheck.addChangeListener(listener);
		geneHighCheck 	= new JCheckBox("Gene popup"); 				geneHighCheck.addChangeListener(listener);
		conservedCheck 	= new JCheckBox("Conserved genes"); 		conservedCheck.addChangeListener(listener);
		
		rulerCheck 		= new JCheckBox("Ruler");					rulerCheck.addChangeListener(listener);
		gapCheck 		= new JCheckBox("Gaps");					gapCheck.addChangeListener(listener);		
		centromereCheck = new JCheckBox("Centromere");				centromereCheck.addChangeListener(listener);	
				
		hitLenCheck 	= new JCheckBox("Hit length");				hitLenCheck.addChangeListener(listener);		
		scoreLineCheck 	= new JCheckBox("Hit %Id bar");				scoreLineCheck.addChangeListener(listener);	
		
		blockTextRadio 		= new JRadioButton("Block#");			blockTextRadio.addChangeListener(listener);	
		csetTextRadio 		= new JRadioButton("Collinear#");		csetTextRadio.addChangeListener(listener);
		scoreTextRadio 		= new JRadioButton("Hit %Id");			scoreTextRadio.addChangeListener(listener);
		hitNumTextRadio 	= new JRadioButton("Hit#");				hitNumTextRadio.addChangeListener(listener);
		noTextRadio 		= new JRadioButton("None");				noTextRadio.addChangeListener(listener);
		ButtonGroup geneBG = new ButtonGroup();
		geneBG.add(scoreTextRadio);
		geneBG.add(hitNumTextRadio);
		geneBG.add(blockTextRadio);
		geneBG.add(csetTextRadio);
		geneBG.add(noTextRadio); noTextRadio.setSelected(true);
		
		JPanel text1Panel = new JPanel();
		text1Panel.add(blockTextRadio);
		text1Panel.add(csetTextRadio);
		text1Panel.setLayout(new BoxLayout(text1Panel, BoxLayout.LINE_AXIS));
		
		JPanel text2Panel = new JPanel();
		text2Panel.add(hitNumTextRadio);
		text2Panel.add(scoreTextRadio);
		text2Panel.add(noTextRadio);
		text2Panel.setLayout(new BoxLayout(text2Panel, BoxLayout.LINE_AXIS));		
		
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
		 
		if (seqObj.isRef()) { // this only know the location of the track
			addToGrid(contentPane, gridbag, constraints, new JLabel("  Highlight"), rem); 
			addToGrid(contentPane, gridbag, constraints, geneHighCheck, 1); 
			addToGrid(contentPane, gridbag, constraints, conservedCheck, rem); 
		}
		else {
			addToGrid(contentPane, gridbag, constraints, new JLabel("  Highlight"), 1); 
			addToGrid(contentPane, gridbag, constraints, geneHighCheck, rem); 
		}
		
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
		addToGrid(contentPane, gridbag, constraints, text1Panel, rem);
		addToGrid(contentPane, gridbag, constraints, new JLabel("               "), 1); 
		addToGrid(contentPane, gridbag, constraints, text2Panel, rem);
		
		addToGrid(contentPane, gridbag, constraints, new JSeparator(),rem);
		
		addToGrid(contentPane, gridbag, constraints, new JLabel("  Coordinates   "), 1);
		addToGrid(contentPane, gridbag, constraints, flipPanel, rem);
		
		addToGrid(contentPane, gridbag, constraints, applyLabel, rem);
		
		addToGrid(contentPane, gridbag, constraints, startLabel, 1);
		addToGrid(contentPane, gridbag, constraints, startPanel, rem);
		
		addToGrid(contentPane, gridbag, constraints, endLabel, 1);
		addToGrid(contentPane, gridbag, constraints, endPanel, rem);
		
		addToGrid(contentPane, gridbag, constraints, geneLabel, 1);
		addToGrid(contentPane, gridbag, constraints, genePanel, rem);

		addToGrid(contentPane, gridbag, constraints, buttonPanel, rem);

		pack();
		setResizable(false);
		setLocationRelativeTo(null); // CAS520
		
	/** Popup **/
		popup = new JPopupMenu(); 
		popup.setBackground(Color.white);
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
		
		scoreTextPopup 		= new JRadioButtonMenuItem("Hit %Id"); 	// ditto
		hitNumTextPopup 	= new JRadioButtonMenuItem("Hit# "); 	// CAS531 add
		blockTextPopup 		= new JRadioButtonMenuItem("Block# "); 	// CAS545 add
		csetTextPopup 		= new JRadioButtonMenuItem("Collinear# "); 	
		noTextPopup 		= new JRadioButtonMenuItem("None"); 
		JLabel text = new JLabel("   Show Text"); text.setEnabled(false);
		
		popupTitle.setText("Sequence Show Options"); 
		popup.add(flippedPopup); 	flippedPopup.addActionListener(listener);
		popup.add(rulerPopup);		rulerPopup.addActionListener(listener);
		popup.add(annotPopup); 		annotPopup.addActionListener(listener);
		popup.add(geneLinePopup);	geneLinePopup.addActionListener(listener);	
		
		popup.add(new JSeparator()); popup.add(text);
		popup.add(blockTextPopup);  blockTextPopup.addActionListener(listener);
		popup.add(csetTextPopup);   csetTextPopup.addActionListener(listener);
		popup.add(hitNumTextPopup); hitNumTextPopup.addActionListener(listener);
		popup.add(scoreTextPopup);	scoreTextPopup.addActionListener(listener);
		popup.add(noTextPopup);     noTextPopup.addActionListener(listener);
		
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
		return seqObj.hasLoad();
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
		if (isShowing()) { // this seems to always be false, so the below is redone everytime
			super.setVisible(true);
			return;
		}	
		int[] annotTypeCounts = seqObj.getAnnotationTypeCounts(); 
		
		boolean b=true; // CAS545 old bug: when track is reused, disabled checks stay 
		b = !(annotTypeCounts[Annotation.GAP_INT] == 0);			gapCheck.setEnabled(b);
		b = !(annotTypeCounts[Annotation.CENTROMERE_INT] == 0); 	centromereCheck.setEnabled(b);
		b = !(annotTypeCounts[Annotation.GENE_INT] == 0);		 	annotCheck.setEnabled(b);
		b = (seqObj.isRef()); 										conservedCheck.setEnabled(b);	
		
		b = !(annotTypeCounts[Annotation.GENE_INT] == 0 && annotTypeCounts[Annotation.EXON_INT] == 0);
		geneCheck.setEnabled(b);
		geneLineCheck.setEnabled(b);
		geneHighCheck.setEnabled(b); 
		annotCheck.setEnabled(b);
		geneLabel.setEnabled(b);
		geneText.setEnabled(b);
		conservedCheck.setEnabled(b);
		
 		saveSettingsFromSeq();
		
 		geneText.setText("");
		setStart();
		setEnd();
		
		flippedCheck.setSelected(bSavFlipped); 
		rulerCheck.setSelected(bSavRuler);
		
		annotCheck.setSelected(bSavAnnot && annotCheck.isEnabled());
		geneCheck.setSelected(bSavGene 	&& geneCheck.isEnabled());
		geneLineCheck.setSelected(bSavGeneLine && geneLineCheck.isEnabled()); // CAS520 add all geneLine lines
		geneHighCheck.setSelected(bSavGeneHigh && geneHighCheck.isEnabled()); 
		conservedCheck.setSelected(bSavConserved && conservedCheck.isEnabled()); 
		
		gapCheck.setSelected(bSavGap && gapCheck.isEnabled());
		centromereCheck.setSelected(bSavCentromere && centromereCheck.isEnabled());

		scoreLineCheck.setSelected(bSavScoreLine);	
		hitLenCheck.setSelected(bSavHitLen);	
		
		blockTextRadio.setSelected(bSavBlockText);
		csetTextRadio.setSelected(bSavCsetText);
		scoreTextRadio.setSelected(bSavScoreText);
		hitNumTextRadio.setSelected(bSavHitNumText);
		noTextRadio.setSelected(bSavNoText);		
			
		super.setVisible(true); // CAS512 super.show();
	}
	
	private void saveSettingsFromSeq() {
		savGeneStr = geneText.getText();	
		savStartStr = startText.getText();
		savEndStr =   endText.getText();
		
		savStartInd = startCombo.getSelectedIndex();
		savEndInd =   endCombo.getSelectedIndex();

		bSavFlipped = 	  seqObj.isFlipped();	
		
		bSavRuler = 	  seqObj.bShowRuler;
		bSavGap = 		  seqObj.bShowGap        && gapCheck.isEnabled();
		bSavCentromere =  seqObj.bShowCentromere && centromereCheck.isEnabled();
		bSavGene = 	  	  seqObj.bShowGene       && geneCheck.isEnabled();
		bSavAnnot = 	  seqObj.bShowAnnot     && annotPopup.isEnabled();
		bSavGeneLine =    seqObj.bShowGeneLine  && geneLinePopup.isEnabled(); 
		bSavGeneHigh =    seqObj.bHighGenePopup && geneHighPopup.isEnabled(); 
		bSavConserved =   seqObj.bHighConserved && conservedCheck.isEnabled(); 
		
		bSavScoreLine =   seqObj.bShowScoreLine;
		bSavHitLen = 	  seqObj.bShowHitLen;	
		
		bSavScoreText =   seqObj.bShowScoreText;
		bSavHitNumText =  seqObj.bShowHitNumText;
		bSavBlockText =   seqObj.bShowBlockText;
		bSavCsetText =    seqObj.bShowCsetText;
		bSavNoText =      seqObj.bShowNoText;
	}
	protected void cancelAction() {
		geneText.setText(savGeneStr);  
		startText.setText(savStartStr); startCombo.setSelectedIndex(savStartInd);
		endText.setText(savEndStr);     endCombo.setSelectedIndex(savEndInd);
		flippedCheck.setSelected(bSavFlipped); 
		
		annotCheck.setSelected(bSavAnnot);
		geneCheck.setSelected(bSavGene);
		geneLineCheck.setSelected(bSavGeneLine);
		geneHighCheck.setSelected(bSavGeneHigh);
		conservedCheck.setSelected(bSavConserved);
		
		gapCheck.setSelected(bSavGap);
		centromereCheck.setSelected(bSavCentromere);
		rulerCheck.setSelected(bSavRuler);
		scoreLineCheck.setSelected(bSavScoreLine); 
		hitLenCheck.setSelected(bSavHitLen); 
		
		blockTextRadio.setSelected(bSavBlockText); 
		csetTextRadio.setSelected(bSavCsetText); 
		scoreTextRadio.setSelected(bSavScoreText); 
		hitNumTextRadio.setSelected(bSavHitNumText);
		noTextRadio.setSelected(bSavNoText);
	}
	protected void setDefault() {
		if (seqObj == null) return;
		
		geneText.setText(""); 
		startCombo.setSelectedItem(DEFAULT_ENDS_UNIT);
		endCombo.setSelectedItem(DEFAULT_ENDS_UNIT);
		startText.setText("0");
		long size = seqObj.getTrackSize();
		Object item = endCombo.getSelectedItem();
		double end  = seqObj.getValue(size, item.toString());
		endText.setText(end + "");
		
		// set in Sequence also
		rulerCheck.setSelected(bDefRuler);
		geneCheck.setSelected(bDefGene 	&& geneCheck.isEnabled());
		gapCheck.setSelected(bDefGap 	&& gapCheck.isEnabled());
		centromereCheck.setSelected(bDefCentromere && centromereCheck.isEnabled());
		 	
		flippedCheck.setSelected(bDefFlipped); 	
		annotCheck.setSelected(bDefAnnot);
		geneLineCheck.setSelected(bDefGeneLine);
		geneHighCheck.setSelected(bDefGeneHigh);
		conservedCheck.setSelected(bDefConserved);
		
		hitLenCheck.setSelected(bDefHitLen); 
		scoreLineCheck.setSelected(bDefScoreLine);
		
		scoreTextRadio.setSelected(bDefScoreText);
		hitNumTextRadio.setSelected(bDefHitNumText);
		blockTextRadio.setSelected(bDefBlockText);
		csetTextRadio.setSelected(bDefCsetText);
		noTextRadio.setSelected(bDefNoText);
		
		// popup
		flippedPopup.setState(bDefFlipped);
		rulerPopup.setSelected(bDefRuler);
		annotPopup.setState(bDefAnnot);
		geneLinePopup.setState(bDefGeneLine); 								// CAS521 1st no Line
		
		scoreTextPopup.setSelected(bDefScoreText);
		hitNumTextPopup.setSelected(bDefHitNumText);
		blockTextPopup.setSelected(bDefBlockText);
		csetTextPopup.setSelected(bDefCsetText);
		noTextPopup.setSelected(bDefNoText);
		
		geneHighPopup.setState(bDefGeneHigh);
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
		geneText.setText(""); 
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
	protected boolean saveAction()  {
		if (seqObj == null) return false;
		if (!seqObj.hasLoad()) return true;
		double tstart=0, tend=0;
		boolean bChg=false;
		
		String gStr = geneText.getText(); // gene# takes precedence
		if (!Utilities.isEmpty(gStr)) {
			if (!Utilities.isValidGenenum(gStr)) {
				Utilities.showErrorMessage(gStr + " is not a valid Gene#");
				return false;
			}
			if (!gStr.contains(".")) gStr += ".";
			int mid = seqObj.getMidCoordForGene(gStr); // highlight if found
			if (mid<0) {
				Utilities.showErrorMessage(gStr + " is not found on this chromosome.");
				return false;
			}
			tstart = Math.max(0,mid-Globals.MAX_RANGE);
			tend   = Math.min(seqObj.getTrackSize(), mid+Globals.MAX_RANGE);

			startText.setText((tstart/1000)+"");startCombo.setSelectedItem(BpNumber.KB);
			endText.setText((tend/1000)+"");    endCombo.setSelectedItem(BpNumber.KB);
			
			bChg=true;
		}
		else {
			seqObj.noSelectedGene();
			
			// Start/End  CAS521 evaluate first - rewritten; fixed bug that Apply would set it back if nothing set
			int eInd = 		endCombo.getSelectedIndex();
			String eStr = 	endText.getText();
			int sInd = 		startCombo.getSelectedIndex();
			String sStr = 	startText.getText();
			
			boolean numChgE = (eInd!=savEndInd   || !eStr.contentEquals(savEndStr));
			boolean numChgS = (sInd!=savStartInd || !sStr.contentEquals(savStartStr));
			
			if (numChgS || numChgE) {
				try {
					tstart = Double.parseDouble(sStr); // CAS512 (new Double(startText.getText())).doubleValue();
				} catch (NumberFormatException nfe) {
					Utilities.showErrorMessage(sStr + " is not a valid start point");
					return false;
				}
				tstart = tstart * BpNumber.getUnitConversion(BpNumber.ABS_UNITS[sInd]);
			
				try {
					tend = Double.parseDouble(eStr); // CAS512 (new Double(endText.getText())).doubleValue();
				} catch (NumberFormatException nfe) {
					Utilities.showErrorMessage(eStr + " is not a valid end point");
					return false;
				}
				tend = tend * BpNumber.getUnitConversion(BpNumber.ABS_UNITS[eInd]);
				
				if (tstart>=tend) {
					Utilities.showErrorMessage("The start (" + tstart + ") must be less than end (" + tend + ")");
					return false;
				}
				bChg=true;
			}
		}
		if (bChg) {
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
			if (seqObj == null) return;
			
			Object src = event.getSource();
			boolean bDiff = false;
			
			if (src == flippedCheck) 		bDiff = seqObj.flipSeq(flippedCheck.isSelected());
			else if (src == annotCheck)		bDiff = seqObj.showAnnotation(annotCheck.isSelected());
			else if (src == geneCheck) {
				boolean b = geneCheck.isSelected();
				bDiff = seqObj.showGene(b);
				geneLineCheck.setEnabled(b); // CAS520
				geneHighCheck.setEnabled(b);
				conservedCheck.setEnabled(b);
			}
			else if (src == geneLineCheck) 	bDiff = seqObj.showGeneLine(geneLineCheck.isSelected());
			else if (src == geneHighCheck)	bDiff = seqObj.highGenePopup(geneHighCheck.isSelected());
			
			else if (src == rulerCheck) 	bDiff = seqObj.showRuler(rulerCheck.isSelected());
			else if (src == gapCheck)		bDiff = seqObj.showGap(gapCheck.isSelected());
			else if (src == centromereCheck)bDiff = seqObj.showCentromere(centromereCheck.isSelected());
			
			else if (src == scoreLineCheck)	bDiff = seqObj.showScoreLine(scoreLineCheck.isSelected());	
			else if (src == hitLenCheck)	bDiff = seqObj.showHitLen(hitLenCheck.isSelected());	
			
			else if (src == blockTextRadio) bDiff = seqObj.showBlockText(blockTextRadio.isSelected());
			else if (src == csetTextRadio)  bDiff = seqObj.showCsetText(csetTextRadio.isSelected());
			else if (src == hitNumTextRadio)bDiff = seqObj.showHitNumText(hitNumTextRadio.isSelected());
			else if (src == scoreTextRadio) bDiff = seqObj.showScoreText(scoreTextRadio.isSelected());
			else if (src == hitNumTextRadio)bDiff = seqObj.showHitNumText(hitNumTextRadio.isSelected());
			else if (src == noTextRadio)	bDiff = seqObj.showNoText(noTextRadio.isSelected());
			
			else if (src == conservedCheck) bDiff = seqObj.highConserved(conservedCheck.isSelected());
			
			if (bDiff) {
				// sets drawingPanel.setUpdateHistory() on Save
				drawingPanel.smake();
			}
		}
				
		// called for panel ok/cancel/default buttons and popup menu events
		public void actionPerformed(ActionEvent event) { 
			if (seqObj == null) {
				ErrorReport.print("No sequence with filter");
				return;
			}
			
			Object src = event.getSource();
			boolean bDiff = false;	

			if (src == okButton) { // changed already made except of for sequence
				saveAction();
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
			else if (src == scoreTextPopup) 	bDiff = seqObj.showScoreText(scoreTextPopup.isSelected());
			else if (src == hitNumTextPopup) 	bDiff = seqObj.showHitNumText(hitNumTextPopup.isSelected());
			else if (src == blockTextPopup) 	bDiff = seqObj.showBlockText(blockTextPopup.isSelected());
			else if (src == csetTextPopup) 		bDiff = seqObj.showCsetText(csetTextPopup.isSelected());
			else if (src == noTextPopup) 		bDiff = seqObj.showNoText(noTextPopup.isSelected());
				
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

			int[] annotTypeCounts = seqObj.getAnnotationTypeCounts(); // CAS545 add, else get wrong if showX is not done
			boolean b = !(annotTypeCounts[Annotation.GENE_INT] == 0 && annotTypeCounts[Annotation.EXON_INT] == 0);
			
			annotPopup.setEnabled(b);
			geneLinePopup.setEnabled(b);
			geneHighPopup.setEnabled(b);
			
			flippedPopup.setSelected(bSavFlipped); 
			rulerPopup.setSelected(bSavRuler);
			annotPopup.setSelected(bSavAnnot);
			geneLinePopup.setSelected(bSavGeneLine);
			
			scoreTextPopup.setSelected(bSavScoreText); 
			hitNumTextPopup.setSelected(bSavHitNumText);
			blockTextPopup.setSelected(bSavBlockText);
			csetTextPopup.setSelected(bSavCsetText);
			noTextPopup.setSelected(bSavNoText);
			
			geneHighPopup.setSelected(bSavGeneHigh);	
		}
	} // end popup listener
}

