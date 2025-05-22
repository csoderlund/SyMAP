package symap.sequence;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.event.ActionEvent;

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
import util.Jcomp;
import util.Jhtml;
import util.Utilities;

/**
 * Sequence filter
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class Sfilter extends JDialog {	
	private static final String DEFAULT_UNIT_KB = BpNumber.KB;
	
	// WHEN ALTER these, alter in initValues and TrackData!
	static private final boolean bDefRuler=true, bDefGap=true, bDefCentromere=true;
	static private final boolean bDefGene=true, bDefScoreLine=false, bDefHitLen=true;
	
	static private final boolean bDefGeneNum=false, bDefAnnot=false, bDefGeneLine=false;
	static private final boolean bDefHitHigh=false, bDefGeneHigh=true;
	
	static private final boolean bDefScoreText=false, bDefHitNumText=false, bDefBlockText=false, 
			                     bDefCsetText=false, bDefNoText=true;
	static private final boolean bDefFlipped=false;
	
	// Current values - many shared between Filter and Popup (could just always set and read from Filter)
	protected boolean bShowRuler, bShowGap, bShowCentromere, bShowGene, bShowScoreLine, bShowHitLen;
	protected boolean bShowGeneNum, bShowAnnot, bShowGeneLine; 
	protected boolean bHighGenePopup, bHitHighg2x2, bHitHighg2x1; 	
	protected boolean bShowScoreText, bShowHitNumText; 				
	protected boolean bShowBlockText, bShowCsetText, bShowNoText;   
	protected boolean bFlipped;
	
	// Save values for Cancel
	private boolean	bSavRuler, bSavGap, bSavCentromere, bSavGene, bSavScoreLine, bSavHitLen;
	private boolean bSavGeneNum, bSavGeneLine, bSavAnnot, bSavGeneHigh, bSavHitHighg2x2, bSavHitHighg2x1;
	private boolean bSavScoreText, bSavHitNumText, bSavBlockText, bSavCsetText, bSavNoText, bSavFlipped;
	private String  savStartStr="", savEndStr="";
	private int     savStartInd=0, savEndInd=0;
	protected String savGeneStr="";
		
	// Filter
	private JButton okButton, cancelButton, defaultButton, helpButton;
	private JPanel buttonPanel;
	private JPopupMenu popup=null; 
	private JMenuItem popupTitle; 
	
	private JCheckBox flippedCheck;
	private JButton fullButton;
	private JLabel geneLabel;
	private JLabel startLabel, endLabel;
	private JTextField startText, endText, geneText;
	private JComboBox <String> startCombo, endCombo; 
	
	private JCheckBox rulerCheck, gapCheck, centromereCheck, geneCheck, scoreLineCheck, hitLenCheck;
	private JCheckBox geneNumCheck, geneLineCheck, annotCheck, geneHighCheck, hitHighg2x2Check, hitHighg2x1Check; 						
	
	private JRadioButton scoreTextRadio, hitNumTextRadio, blockTextRadio, csetTextRadio, noTextRadio; 
	
	// Popup
	private JMenuItem fullSeqPopup;
	private JCheckBoxMenuItem annotPopup,  geneNumPopup, geneLinePopup, flippedPopup; 						 		 			
	private JRadioButtonMenuItem scoreTextPopup, hitNumTextPopup, blockTextPopup, csetTextPopup, noTextPopup; 												

	// Other
	private Sequence seqObj;
	private DrawingPanel drawingPanel;
	private boolean bReplace=false; // Filter window text change in stateChanged; save in Save
	private boolean bNoListen=false;
	
	// Created when 2d is displayed, one for each sequence and track 
	public Sfilter(Frame owner, DrawingPanel dp,  Sequence sequence) { 
		super(owner,"Sequence Filter #" + sequence.position, true); 
		this.drawingPanel = dp;	
		this.seqObj = sequence;
		seqObj.setFilter(this); // access values from here
		initValues();

		FilterListener listener = new FilterListener();
		
		createFilterPanel(listener);
		createPopup(listener);
		setEnables(); 
	}
/** Startup **/
	// Filter dialog 
	private void createFilterPanel(FilterListener listener) {		
		// Buttons
		okButton = Jcomp.createMonoButton("Save","Apply coordinate changes, save all changes, and close");
		okButton.addActionListener(listener);

		cancelButton = Jcomp.createMonoButton("Cancel", "Discard changes and close");
		cancelButton.addActionListener(listener);

		defaultButton = Jcomp.createMonoButton("Defaults", "Reset to defaults");
		defaultButton.addActionListener(listener);
		
		helpButton = Jhtml.createHelpIconUserSm(Jhtml.seqfilter);
		JButton helpButtonSm=  Jcomp.createIconButton(null,null,null,"/images/info.png",
				"Quick Help Popup");
		helpButtonSm.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				popupHelp();
			}
		});

		buttonPanel = new JPanel(new BorderLayout());
		buttonPanel.add(new JSeparator(),BorderLayout.NORTH);
		JPanel innerPanel = new JPanel();
		innerPanel.add(okButton);
		innerPanel.add(cancelButton);
		innerPanel.add(defaultButton);
		innerPanel.add(helpButton);
		innerPanel.add(helpButtonSm);
		buttonPanel.add(innerPanel,BorderLayout.CENTER);
		
		String b1="   ", b2="      ";
		int textLen=7;
		
		// Coordinate options; takes effect on Apply
		startText = new JTextField("0.0",textLen); 
		startText.setMaximumSize(startText.getPreferredSize()); startText.setMinimumSize(startText.getPreferredSize()); // CAS551 add
		startCombo = new JComboBox <String>(BpNumber.ABS_UNITS);
		startCombo.setSelectedItem(DEFAULT_UNIT_KB);
		startCombo.setMaximumSize(startCombo.getPreferredSize()); startCombo.setMinimumSize(startCombo.getPreferredSize());

		String v = seqObj.getValue(seqObj.getEnd(), DEFAULT_UNIT_KB)+"";
		endText =   new JTextField(v,textLen); 
		endText.setMaximumSize(endText.getPreferredSize()); endText.setMinimumSize(endText.getPreferredSize());
		endCombo =  new JComboBox <String>(BpNumber.ABS_UNITS);
		endCombo.setSelectedItem(DEFAULT_UNIT_KB);
		endCombo.setMaximumSize(endCombo.getPreferredSize()); endCombo.setMinimumSize(endCombo.getPreferredSize());
		
		JPanel coordPanel = Jcomp.createGrayRowPanel(); 
		startLabel = new JLabel(b2+"Start");
		coordPanel.add(startLabel); coordPanel.add(startText); coordPanel.add(startCombo);
		endLabel = new JLabel(b2+"End");
		coordPanel.add(endLabel);   coordPanel.add(endText); coordPanel.add(endCombo);
		
		JPanel genePanel = Jcomp.createGrayRowPanel(); 
		geneLabel =  new JLabel(b2+"or Gene#"); 
		geneText =   new JTextField("",7); 
		geneText.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setCoordsEnable();
			}
		});
		geneText.setMaximumSize(geneText.getPreferredSize()); geneText.setMinimumSize(geneText.getPreferredSize());
		JLabel reg = Jcomp.createMonoLabel(" Region " + Globals.MAX_2D_DISPLAY_K,
				"Center on gene; gene take precedences over start/end"); 
		JButton clear = Jcomp.createMonoButtonSm("Clear", "Clear gene# value");
		clear.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				geneText.setText("");
				setCoordsEnable();
			}
		});
		genePanel.add(geneLabel); genePanel.add(geneText); genePanel.add(reg); 
		genePanel.add(new JLabel(" ")); genePanel.add(clear);
		
		flippedCheck = new JCheckBox("Flip sequence    "); 	
		
		fullButton = Jcomp.createMonoButtonSm("Full", "Set start/end to full sequence");
		fullButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setFullSequence();
			}
		});
		JPanel flipPanel = Jcomp.createGrayRowPanel(); 
		flipPanel.add(flippedCheck); flipPanel.add(fullButton);
			
		// Checkbox options; takes effect immediately; but lost on cancel (must use save)
		rulerCheck 		= new JCheckBox("Ruler");				rulerCheck.addChangeListener(listener);
		gapCheck 		= new JCheckBox("Gaps");				gapCheck.addChangeListener(listener);		
		centromereCheck = new JCheckBox("Centromere");			centromereCheck.addChangeListener(listener);	
		geneCheck 		= new JCheckBox("Genes");    			geneCheck.addChangeListener(listener);    
		hitLenCheck 	= new JCheckBox("Hit length");			hitLenCheck.addChangeListener(listener);		
		scoreLineCheck 	= new JCheckBox("Hit %Id bar");			scoreLineCheck.addChangeListener(listener);
		
		geneHighCheck 	= new JCheckBox("Gene popup"); 			geneHighCheck.addChangeListener(listener);
		hitHighg2x2Check = new JCheckBox("Hit g2x2"); 			hitHighg2x2Check.addChangeListener(listener);
		hitHighg2x1Check = new JCheckBox("Hit g2x1"); 			hitHighg2x1Check.addChangeListener(listener);
		annotCheck 		= new JCheckBox("Annotation");			annotCheck.addChangeListener(listener);
		geneNumCheck 	= new JCheckBox("Gene#");    			geneNumCheck.addChangeListener(listener);     
		geneLineCheck 	= new JCheckBox("Gene delimiter");		geneLineCheck.addChangeListener(listener);
				
		blockTextRadio 	= new JRadioButton("Block#");			blockTextRadio.addChangeListener(listener);	
		csetTextRadio 	= new JRadioButton("Collinear#");		csetTextRadio.addChangeListener(listener);
		scoreTextRadio 	= new JRadioButton("Hit %Id");			scoreTextRadio.addChangeListener(listener);
		hitNumTextRadio = new JRadioButton("Hit#");				hitNumTextRadio.addChangeListener(listener);
		noTextRadio 	= new JRadioButton("None");				noTextRadio.addChangeListener(listener);
		ButtonGroup geneBG = new ButtonGroup();
		geneBG.add(scoreTextRadio);
		geneBG.add(hitNumTextRadio);
		geneBG.add(blockTextRadio);
		geneBG.add(csetTextRadio);
		geneBG.add(noTextRadio); 		
		
		JPanel text1Panel = Jcomp.createGrayRowPanel();
		text1Panel.add(blockTextRadio);
		text1Panel.add(csetTextRadio);
		
		JPanel text2Panel = Jcomp.createGrayRowPanel();
		text2Panel.add(hitNumTextRadio);
		text2Panel.add(scoreTextRadio);
		text2Panel.add(noTextRadio);	
		
	/// Gridbag
		Container contentPane = getContentPane();
		GridBagLayout gbl = new GridBagLayout();
		GridBagConstraints gbc = new GridBagConstraints();

		contentPane.setLayout(gbl);
		gbc.gridheight = 1;
		gbc.ipadx = 5;
		gbc.ipady = 8;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		int rem = GridBagConstraints.REMAINDER;
		 
		// section 1
		JLabel retain = Jcomp.createMonoLabel(" Save to retain changes", "Does not create new history event");
		addToGrid(contentPane, gbl, gbc, retain, rem);
		addToGrid(contentPane, gbl, gbc, new JLabel(b1+"Highlight"), 1); 
		if (seqObj.isRef() && seqObj.is3Track()) { // CAS555 add 3Track check
			addToGrid(contentPane, gbl, gbc, geneHighCheck, 1); 
			addToGrid(contentPane, gbl, gbc, hitHighg2x2Check, 1); 
			addToGrid(contentPane, gbl, gbc, hitHighg2x1Check, rem); 
		}
		else {
			addToGrid(contentPane, gbl, gbc, geneHighCheck, rem); 
		}
		addToGrid(contentPane, gbl, gbc, new JLabel(b1+"Show"), 1); 
		addToGrid(contentPane, gbl, gbc, annotCheck, 1); 
		addToGrid(contentPane, gbl, gbc, geneNumCheck,  1); 
		addToGrid(contentPane, gbl, gbc, geneLineCheck, rem); 
		
		// section 2
		addToGrid(contentPane, gbl, gbc, new JSeparator(), rem);
		addToGrid(contentPane, gbl, gbc, new JLabel(b1+"Graphics"), 1); 
		addToGrid(contentPane, gbl, gbc, rulerCheck, 1);
		addToGrid(contentPane, gbl, gbc, gapCheck, 1);
		addToGrid(contentPane, gbl, gbc, centromereCheck, rem);
		
		addToGrid(contentPane, gbl, gbc, new JLabel("   "), 1); 
		addToGrid(contentPane, gbl, gbc, geneCheck, 1);
		addToGrid(contentPane, gbl, gbc, hitLenCheck, 1);
		addToGrid(contentPane, gbl, gbc, scoreLineCheck, rem);
		
		// section 3
		addToGrid(contentPane, gbl, gbc, new JSeparator(), rem);
		addToGrid(contentPane, gbl, gbc, new JLabel(b1+"Hit Text"), 1); 
		addToGrid(contentPane, gbl, gbc, blockTextRadio, 1);
		addToGrid(contentPane, gbl, gbc, csetTextRadio, rem);
		addToGrid(contentPane, gbl, gbc, new JLabel("               "), 1); 
		addToGrid(contentPane, gbl, gbc, scoreTextRadio, 1);
		addToGrid(contentPane, gbl, gbc, hitNumTextRadio, 1);
		addToGrid(contentPane, gbl, gbc, noTextRadio, rem);
		
		// section 4
		addToGrid(contentPane, gbl, gbc, new JSeparator(),rem);
		JLabel apply = Jcomp.createMonoLabel(" Save to apply changes", "Create new history event");
		addToGrid(contentPane, gbl, gbc, apply, rem);
		addToGrid(contentPane, gbl, gbc, new JLabel(b1+"Coordinates   "), 1);
		addToGrid(contentPane, gbl, gbc, flipPanel, rem);
		addToGrid(contentPane, gbl, gbc, coordPanel, rem);
		addToGrid(contentPane, gbl, gbc, genePanel, rem);

		addToGrid(contentPane, gbl, gbc, buttonPanel, rem);

		pack();
		setResizable(false);
		setLocationRelativeTo(null); // CAS520
	}
	private void addToGrid(Container cp, GridBagLayout layout, GridBagConstraints con, Component comp, int w) {
		con.gridwidth = w;
		layout.setConstraints(comp, con);
		cp.add(comp);
	}
	
	// Popup 
	private void createPopup(FilterListener listener) {
		popup = new JPopupMenu(); 
		popup.setBackground(Color.white);
		popup.addPopupMenuListener(new MyPopupMenuListener()); 
		
		flippedPopup 		= new JCheckBoxMenuItem("Flip sequence"); 
		fullSeqPopup 		= new JMenuItem("Full"); 
		annotPopup			= new JCheckBoxMenuItem("Annotation (zoom in)"); 
		geneNumPopup 		= new JCheckBoxMenuItem("Gene#"); 
		geneLinePopup 		= new JCheckBoxMenuItem("Gene delimiter"); 
		
		scoreTextPopup 		= new JRadioButtonMenuItem("Hit %Id"); 	
		hitNumTextPopup 	= new JRadioButtonMenuItem("Hit# "); 	
		blockTextPopup 		= new JRadioButtonMenuItem("Block# "); 	
		csetTextPopup 		= new JRadioButtonMenuItem("Collinear# "); 	
		noTextPopup 		= new JRadioButtonMenuItem("None"); 
		ButtonGroup grp = new ButtonGroup();
		grp.add(scoreTextPopup); grp.add(hitNumTextPopup); grp.add(blockTextPopup);
		grp.add(csetTextPopup);  grp.add(noTextPopup);
		noTextPopup.setSelected(true);
		
		popupTitle = new JMenuItem("Sequence Options"); popupTitle.setEnabled(false);
		popup.add(popupTitle);
		
		popup.addSeparator();
		JLabel stext = new JLabel("   Coordinate changes"); stext.setEnabled(false);
		popup.add(stext);
		popup.add(flippedPopup); 	flippedPopup.addActionListener(listener);
		popup.add(fullSeqPopup); 	fullSeqPopup.addActionListener(listener);
		
		popup.add(new JSeparator()); 
		JLabel gtext = new JLabel("   Show gene"); gtext.setEnabled(false);
		popup.add(gtext);
		popup.add(annotPopup); 		annotPopup.addActionListener(listener);
		popup.add(geneNumPopup); 	geneNumPopup.addActionListener(listener);
		popup.add(geneLinePopup);	geneLinePopup.addActionListener(listener);	
		
		popup.add(new JSeparator()); 
		JLabel text = new JLabel("   Hit text"); text.setEnabled(false);
		popup.add(text);
		popup.add(blockTextPopup);  blockTextPopup.addActionListener(listener);
		popup.add(csetTextPopup);   csetTextPopup.addActionListener(listener);
		popup.add(hitNumTextPopup); hitNumTextPopup.addActionListener(listener);
		popup.add(scoreTextPopup);	scoreTextPopup.addActionListener(listener);
		popup.add(noTextPopup);     noTextPopup.addActionListener(listener);
		
		popup.setMaximumSize(popup.getPreferredSize()); popup.setMinimumSize(popup.getPreferredSize());
	}
	// disable if not exist (this used to have to be done over&over since tracks/filters were re-used).
	private void setEnables() {
		bNoListen=true;
		int[] annotTypeCounts = seqObj.getAnnotationTypeCounts(); 
		boolean b=true; 
		b = !(annotTypeCounts[Annotation.GAP_INT] == 0);			
		gapCheck.setEnabled(b);
		
		b = !(annotTypeCounts[Annotation.CENTROMERE_INT] == 0); 	
		centromereCheck.setEnabled(b);
			
		b = !(annotTypeCounts[Annotation.GENE_INT] == 0 && annotTypeCounts[Annotation.EXON_INT] == 0);
		geneCheck.setEnabled(b);
		geneNumCheck.setEnabled(b);
		geneLineCheck.setEnabled(b);
		annotCheck.setEnabled(b);
		hitHighg2x2Check.setEnabled(b);	
		hitHighg2x1Check.setEnabled(b);	
		geneHighCheck.setEnabled(b); 
		
		geneLabel.setEnabled(b);
		geneText.setEnabled(b);
		
		annotPopup.setEnabled(b);
		geneLinePopup.setEnabled(b);
		geneNumPopup.setEnabled(b);
		
		bNoListen=false;
	}
/** End Startup **/
	
	private void setCoordsEnable() {
		String val = geneText.getText().trim();
		boolean b = val.equals("");
		startLabel.setEnabled(b); startText.setEnabled(b);
		endLabel.setEnabled(b); endText.setEnabled(b);
	}
	
	// Used by drawingpanel.FilterHandle 
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
			setVisible(false); 
		}
	}

	// Called when Sequence Filter button clicked 
	public void showSeqFilter() {
		if (isShowing()) { // this seems to always be false..
			super.setVisible(true);
			return;
		}	
		saveCurrentSettings(); 
		
		// values only changed in filter window
		geneText.setText(savGeneStr);
		setCoordsEnable();
		
		hitHighg2x2Check.setSelected(bSavHitHighg2x2 && hitHighg2x2Check.isEnabled()); 
		hitHighg2x1Check.setSelected(bSavHitHighg2x1 && hitHighg2x1Check.isEnabled()); 
		geneHighCheck.setSelected(bSavGeneHigh && geneHighCheck.isEnabled()); 	
				
		rulerCheck.setSelected(bSavRuler);
		gapCheck.setSelected(bSavGap && gapCheck.isEnabled());
		centromereCheck.setSelected(bSavCentromere && centromereCheck.isEnabled());
		geneCheck.setSelected(bSavGene   && geneCheck.isEnabled());
		hitLenCheck.setSelected(bSavHitLen);
		scoreLineCheck.setSelected(bSavScoreLine);
		
		// values can be changed in popup
		flippedCheck.setSelected(bSavFlipped); 
		geneNumCheck.setSelected(bSavGeneNum   && geneNumCheck.isEnabled());
		geneLineCheck.setSelected(bSavGeneLine && geneLineCheck.isEnabled()); 
		annotCheck.setSelected(bSavAnnot && annotCheck.isEnabled());
		
		blockTextRadio.setSelected(bSavBlockText);
		csetTextRadio.setSelected(bSavCsetText);
		scoreTextRadio.setSelected(bSavScoreText);
		hitNumTextRadio.setSelected(bSavHitNumText);
		noTextRadio.setSelected(bSavNoText);		
			
		super.setVisible(true); 
	}
	
	private void saveCurrentSettings() { // showSeqFilter (Button) and popup
		bSavRuler = 	  bShowRuler;
		bSavGap = 		  bShowGap        && gapCheck.isEnabled();
		bSavCentromere =  bShowCentromere && centromereCheck.isEnabled();
		bSavGene = 		  bShowGene       && geneCheck.isEnabled();
		bSavHitLen = 	  bShowHitLen;
		bSavScoreLine =   bShowScoreLine;
		
		bSavAnnot = 	  bShowAnnot     && annotPopup.isEnabled();
		bSavGeneNum = 	  bShowGeneNum   && geneNumCheck.isEnabled();
		bSavGeneLine =    bShowGeneLine  && geneLinePopup.isEnabled(); 
		bSavHitHighg2x2 = bHitHighg2x2   && hitHighg2x2Check.isEnabled(); 
		bSavHitHighg2x1 = bHitHighg2x1   && hitHighg2x1Check.isEnabled();
		bSavGeneHigh =    bHighGenePopup && geneHighCheck.isEnabled(); 	
			
		bSavScoreText =   bShowScoreText;
		bSavHitNumText =  bShowHitNumText;
		bSavBlockText =   bShowBlockText;
		bSavCsetText =    bShowCsetText;
		bSavNoText =      bShowNoText;
		
		bSavFlipped = 	  bFlipped;	
		
		// values may have changed from mouse drag, so these are current
		int start   = seqObj.getStart();
		Object item = startCombo.getSelectedItem();
		double val =  seqObj.getValue(start, item.toString()); 
		startText.setText(val+"");
		
		int end = seqObj.getEnd(); 
		item = endCombo.getSelectedItem();
		val  = seqObj.getValue(end, item.toString()); 
		endText.setText(val + "");
				
		savGeneStr =  	  geneText.getText().trim();	
		savStartStr = 	  startText.getText().trim();
		savEndStr =   	  endText.getText().trim();
		savStartInd = 	  startCombo.getSelectedIndex();
		savEndInd =   	  endCombo.getSelectedIndex();
	}
	protected void cancelAction() {
		gapCheck.setSelected(bSavGap);
		centromereCheck.setSelected(bSavCentromere);
		rulerCheck.setSelected(bSavRuler);
		geneCheck.setSelected(bSavGene);
		hitLenCheck.setSelected(bSavHitLen);
		scoreLineCheck.setSelected(bSavScoreLine);
		
		annotCheck.setSelected(bSavAnnot);
		geneNumCheck.setSelected(bSavGeneNum);
		geneLineCheck.setSelected(bSavGeneLine);
		geneHighCheck.setSelected(bSavGeneHigh);
		hitHighg2x2Check.setSelected(bSavHitHighg2x2);
		hitHighg2x1Check.setSelected(bSavHitHighg2x1);
			
		blockTextRadio.setSelected(bSavBlockText); 
		csetTextRadio.setSelected(bSavCsetText); 
		scoreTextRadio.setSelected(bSavScoreText); 
		hitNumTextRadio.setSelected(bSavHitNumText);
		noTextRadio.setSelected(bSavNoText);
		
		flippedCheck.setSelected(bSavFlipped);
		geneText.setText(savGeneStr);  
		startText.setText(savStartStr); startCombo.setSelectedIndex(savStartInd);
		endText.setText(savEndStr);     endCombo.setSelectedIndex(savEndInd);
	}
	private void setDefault() { // Default button; 
		rulerCheck.setSelected(bDefRuler);
		gapCheck.setSelected(bDefGap && gapCheck.isEnabled());
		centromereCheck.setSelected(bDefCentromere && centromereCheck.isEnabled());
		geneCheck.setSelected(bDefGene && geneCheck.isEnabled());
		hitLenCheck.setSelected(bDefHitLen);
		scoreLineCheck.setSelected(bDefScoreLine);
		
		annotCheck.setSelected(bDefAnnot);
		geneNumCheck.setSelected(bDefGeneNum);
		geneLineCheck.setSelected(bDefGeneLine);
		geneHighCheck.setSelected(bDefGeneHigh);
		hitHighg2x2Check.setSelected(bDefHitHigh);
		hitHighg2x1Check.setSelected(bDefHitHigh);
		
		scoreTextRadio.setSelected(bDefScoreText);
		hitNumTextRadio.setSelected(bDefHitNumText);
		blockTextRadio.setSelected(bDefBlockText);
		csetTextRadio.setSelected(bDefCsetText);
		noTextRadio.setSelected(bDefNoText);
		
		flippedCheck.setSelected(bDefFlipped); 	
		
		geneText.setText(""); 
		
		startCombo.setSelectedItem(DEFAULT_UNIT_KB);
		startText.setText("0.0");
		
		endCombo.setSelectedItem(DEFAULT_UNIT_KB);
		int size = seqObj.getTrackSize();
		double end  = seqObj.getValue(size, DEFAULT_UNIT_KB);
		endText.setText(end + "");
		endCombo.setSelectedItem(DEFAULT_UNIT_KB);
	}

	private void setFullSequence() {
		geneText.setText(""); 
		
		startText.setText("0.0"); 
		startCombo.setSelectedItem(DEFAULT_UNIT_KB);
		
		int size = seqObj.getTrackSize();
		double end  = seqObj.getValue(size, DEFAULT_UNIT_KB);
		endText.setText(end + "");
		endCombo.setSelectedItem(DEFAULT_UNIT_KB);
	}
	private boolean applyCoordChanges() {
		double tstart=0, tend=0;
		int eInd = 		endCombo.getSelectedIndex();
		String eStr = 	endText.getText();
		int sInd = 		startCombo.getSelectedIndex();
		String sStr = 	startText.getText();
		
		boolean numChgE = (eInd!=savEndInd   || !eStr.contentEquals(savEndStr)); 
		boolean numChgS = (sInd!=savStartInd || !sStr.contentEquals(savStartStr));
		
		if (!numChgS && !numChgE) return false;
		
		try {
			tstart = Double.parseDouble(sStr); 
		} catch (NumberFormatException nfe) {
			Utilities.showErrorMessage(sStr + " is not a valid start point");
			return false;
		}
		tstart = tstart * BpNumber.getUnitConversion(BpNumber.ABS_UNITS[sInd]);
	
		try {
			tend = Double.parseDouble(eStr);
		} catch (NumberFormatException nfe) {
			Utilities.showErrorMessage(eStr + " is not a valid end point");
			return false;
		}
		tend = tend * BpNumber.getUnitConversion(BpNumber.ABS_UNITS[eInd]);
		
		if (tstart>=tend) {
			Utilities.showErrorMessage("The start (" + tstart + ") must be less than end (" + tend + ")");
			return false;
		}
		seqObj.setStart((int)tstart);
		seqObj.setEnd((int)tend);
		
		return true;		
	}
	// Check boxes are processed immediately; the change in coords get processed here on Save
	
	protected boolean saveAction()  {
		if (!seqObj.hasLoad()) return true;
		double tstart=0, tend=0;
		boolean bChg=false;
		
		String gStr = geneText.getText(); // gene# takes precedence
		if (!Utilities.isEmpty(gStr)) {
			if (!Utilities.isValidGenenum(gStr)) {
				Utilities.showErrorMessage(gStr + " is not a valid Gene#");
				return false;
			}
			if (!gStr.equals(savGeneStr)) { 
				if (!gStr.contains(".")) gStr += ".";
				savGeneStr = gStr;
				
				int mid = seqObj.getMidCoordForGene(gStr); // highlight if found
				if (mid<0) {
					Utilities.showErrorMessage(gStr + " is not found on this chromosome.");
					return false;
				}
				tstart = Math.max(0,mid-Globals.MAX_2D_DISPLAY);
				tend   = Math.min(seqObj.getTrackSize(), mid+Globals.MAX_2D_DISPLAY); 
	
				startText.setText((tstart/1000)+"");startCombo.setSelectedItem(BpNumber.KB);
				endText.setText((tend/1000)+"");    endCombo.setSelectedItem(BpNumber.KB);
				
				seqObj.setStart((int)tstart);// saved in Track
				seqObj.setEnd((int)tend);
				
				bChg=true;
			}
		}
		else {
			if (savGeneStr!="") {
				seqObj.noSelectedGene();
				savGeneStr="";
			}
			bChg = applyCoordChanges();
		}
		
		if (bChg) { // will do replace and flip changes too
			drawingPanel.setUpdateHistory();
			drawingPanel.smake("Sf: saveaction update bchg");
		}
		else if (xFlipSeq(flippedCheck.isSelected())) { 
			drawingPanel.setUpdateHistory();
			drawingPanel.smake("Sf: saveaction update flip");
		}
		else if (bReplace) { 
			drawingPanel.setReplaceHistory();
			drawingPanel.smake("Sf: saveaction replace");
		}
	
		return true;
	}

	/************************************************************************************/
	private class FilterListener implements ActionListener, ChangeListener, ItemListener {
		private FilterListener() { }

		// Action: called for panel ok/cancel/default buttons and popup menu events
		public void actionPerformed(ActionEvent event) { 
			Object src = event.getSource();
			boolean bUp = false, bRep = false;	

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
			}
			// popup
			else if (src == fullSeqPopup) { 
				setFullSequence();
				bUp = applyCoordChanges();
			}
			else if (src == flippedPopup) 		bUp =  xFlipSeq(!bSavFlipped);
			else if (src == annotPopup) 		bRep = xShowAnnotation(annotPopup.getState());
			else if (src == geneLinePopup) 		bRep = xShowGeneLine(geneLinePopup.getState());
			else if (src == geneNumPopup) 		bRep = xShowGeneNum(geneNumPopup.getState());
			
			else if (src == scoreTextPopup) 	bRep = xShowScoreText(scoreTextPopup.isSelected());
			else if (src == hitNumTextPopup) 	bRep = xShowHitNumText(hitNumTextPopup.isSelected());
			else if (src == blockTextPopup) 	bRep = xShowBlockText(blockTextPopup.isSelected());
			else if (src == csetTextPopup) 		bRep = xShowCsetText(csetTextPopup.isSelected());
			else if (src == noTextPopup) 		bRep = xShowNoText(noTextPopup.isSelected());
				
			if (bUp || bRep) {
				drawingPanel.smake("Sf: filterlistener actionperformed");	
				if (bUp) drawingPanel.updateHistory();
				else drawingPanel.replaceHistory();
			}
		}
		// State: Filter Panel replace changes.
		public void stateChanged(ChangeEvent event) {
			if (bNoListen) return;
			
			Object src = event.getSource();
			boolean bDiff = false;
	
			if (src == geneCheck) {
				bDiff = xShowGene(geneCheck.isSelected());
				boolean b = geneCheck.isSelected();
				geneNumCheck.setEnabled(b);
				geneLineCheck.setEnabled(b); 
				geneHighCheck.setEnabled(b);
				hitHighg2x2Check.setEnabled(b);
				hitHighg2x1Check.setEnabled(b);
			}
			else if (src == annotCheck)		bDiff = xShowAnnotation(annotCheck.isSelected());
			else if (src == geneNumCheck) 	bDiff = xShowGeneNum(geneNumCheck.isSelected());
			else if (src == geneLineCheck) 	bDiff = xShowGeneLine(geneLineCheck.isSelected());
			else if (src == geneHighCheck)	bDiff = xHighGenePopup(geneHighCheck.isSelected());
			else if (src == hitHighg2x2Check) {
				if (hitHighg2x2Check.isSelected() && hitHighg2x1Check.isSelected()) {
					hitHighg2x1Check.setSelected(false);
					seqObj.highHitg2x1(false);
				}
				bDiff = seqObj.highHitg2x2(hitHighg2x2Check.isSelected());
				
			}
			else if (src == hitHighg2x1Check) {
				if (hitHighg2x2Check.isSelected() && hitHighg2x1Check.isSelected()) {
					hitHighg2x2Check.setSelected(false);
					seqObj.highHitg2x2(false);
				}
				bDiff = seqObj.highHitg2x1(hitHighg2x1Check.isSelected());
			}
			else if (src == blockTextRadio) bDiff = xShowBlockText(blockTextRadio.isSelected());
			else if (src == csetTextRadio)  bDiff = xShowCsetText(csetTextRadio.isSelected());
			else if (src == hitNumTextRadio)bDiff = xShowHitNumText(hitNumTextRadio.isSelected());
			else if (src == scoreTextRadio) bDiff = xShowScoreText(scoreTextRadio.isSelected());
			else if (src == hitNumTextRadio)bDiff = xShowHitNumText(hitNumTextRadio.isSelected());
			else if (src == noTextRadio)	bDiff = xShowNoText(noTextRadio.isSelected());
			
			else if (src == rulerCheck) 	bDiff = xShowRuler(rulerCheck.isSelected());
			else if (src == gapCheck)		bDiff = xShowGap(gapCheck.isSelected());
			else if (src == centromereCheck)bDiff = xShowCentromere(centromereCheck.isSelected());
			else if (src == hitLenCheck)	bDiff = xShowHitLen(hitLenCheck.isSelected());
			else if (src == scoreLineCheck) bDiff = xShowScoreLine(scoreLineCheck.isSelected());
			
			if (bDiff) {
				drawingPanel.smake("Sf: filterlistener state changed " + src.hashCode());// sets drawingPanel.setUpdateHistory() on Save
				bReplace = true;     
			}
		}
				
		
		public void itemStateChanged(ItemEvent evt) {}
	} // end listener
	
	// called when popup become visible
	class MyPopupMenuListener implements PopupMenuListener {
		  public void popupMenuCanceled(PopupMenuEvent popupMenuEvent) {}

		  public void popupMenuWillBecomeInvisible(PopupMenuEvent popupMenuEvent) {}

		  public void popupMenuWillBecomeVisible(PopupMenuEvent event) { 
			saveCurrentSettings();

			annotPopup.setSelected(bSavAnnot);
			geneNumPopup.setSelected(bSavGeneNum);
			geneLinePopup.setSelected(bSavGeneLine);
			
			scoreTextPopup.setSelected(bSavScoreText); 
			hitNumTextPopup.setSelected(bSavHitNumText);
			blockTextPopup.setSelected(bSavBlockText);
			csetTextPopup.setSelected(bSavCsetText);
			noTextPopup.setSelected(bSavNoText);
			
			flippedPopup.setSelected(bSavFlipped); 
		}
	} // end popup listener
	
	//////////////////////////////////////////////////////////////////////
	// CAS552 move all sequence filter stuff to here; it can access here.
	private void initValues() {
		bFlipped 		= bDefFlipped; 
		bShowRuler      = bDefRuler;
		bShowGap        = bDefGap;
		bShowCentromere = bDefCentromere;
		bShowGene 		= bDefGene;
		bShowHitLen 	= bDefHitLen;
		bShowScoreLine  = bDefScoreLine;
	
		bShowGeneNum    = bDefGeneNum;
		bShowAnnot      = bDefAnnot;
		bShowGeneLine   = bDefGeneLine;
		bHighGenePopup  = bDefGeneHigh;
		bHitHighg2x2 	= bDefHitHigh;
		bHitHighg2x1 	= bDefHitHigh;
		
		bShowScoreText 	= bDefScoreText; 	
		bShowHitNumText = bDefHitNumText;
		bShowBlockText 	= bDefBlockText;
		bShowCsetText 	= bDefCsetText;
		bShowNoText 	= bDefNoText;
		// Start and end get initialized from seqObj when filter window popups up
	}
	protected String xHitg2() { 
		return (bHitHighg2x2) ? "Conserved g2x2: " : "Conserved g2x1: ";
	}
	protected boolean xFlipSeq(boolean flip) {
		if (bFlipped != flip) {bFlipped = flip;	seqObj.setTrackBuild(); return true;}
		return false;
	}
	protected boolean xShowRuler(boolean show) {
		if (bShowRuler != show) {bShowRuler = show; seqObj.setTrackBuild();return true;}
		return false;
	}
	protected boolean xShowGap(boolean show) {
		if (bShowGap != show) {bShowGap = show; seqObj.setTrackBuild();return true;}
		return false;
	}
	protected boolean xShowCentromere(boolean show) {
		if (bShowCentromere != show) {bShowCentromere = show; seqObj.setTrackBuild();return true;}
		return false;
	}
	protected boolean xShowGene(boolean show) { 
		if (bShowGene != show) {bShowGene = show; seqObj.setTrackBuild(); return true;}
		return false;
	}
	protected boolean xShowHitLen(boolean show) { 
		if (bShowHitLen != show) {bShowHitLen = show; seqObj.setTrackBuild(); return true; }
		return false;
	}
	protected boolean xShowScoreLine(boolean show) { 
		if (bShowScoreLine != show) {bShowScoreLine = show; seqObj.setTrackBuild(); return true;}
		return false;
	}
	protected boolean xShowAnnotation(boolean show) {
		if (seqObj.allAnnoVec.size() == 0) {bShowAnnot = false; return false;}
		
		if (bShowAnnot != show)  {bShowAnnot = show; seqObj.setTrackBuild();return true;}
		return false;
	}
	protected boolean xShowGeneNum(boolean show) {  
		if (bShowGeneNum != show) {bShowGeneNum = show; seqObj.setTrackBuild();return true;}
		return false;
	}
	protected boolean xShowGeneLine(boolean show) {
		if (bShowGeneLine != show) {bShowGeneLine = show; seqObj.setTrackBuild(); return true;}
		return false;
	}
	protected boolean xHighGenePopup(boolean high) { 
		if (bHighGenePopup != high) { 
			bHighGenePopup = high; seqObj.setTrackBuild();
			if (!high) 
				for (Annotation aObj : seqObj.allAnnoVec) aObj.setIsPopup(false); // exons are highlighted to, so use all
			return true;
		}
		return false;
	}
	protected boolean xShowScoreText(boolean show) { 
		if (bShowScoreText != show) {xSetText(false, false, false, show); seqObj.setTrackBuild(); return true;}
		return false;
	}
	protected boolean xShowHitNumText(boolean show) { 
		if (bShowHitNumText != show) {xSetText(false, false, show, false); seqObj.setTrackBuild(); return true; }
		return false;
	}
	protected boolean xShowBlockText(boolean show) { 
		if (bShowBlockText != show) {xSetText(show, false, false, false); seqObj.setTrackBuild(); return true; }
		return false;
	}
	protected boolean xShowCsetText(boolean show) { 
		if (bShowCsetText != show) {xSetText(false, show, false, false); seqObj.setTrackBuild(); return true;}
		return false;
	}
	protected boolean xShowNoText(boolean show) { 
		if (bShowNoText != show) {xSetText(false, false, false, false); seqObj.setTrackBuild(); return true; }
		return false;
	}
	private void xSetText(boolean b, boolean c, boolean h, boolean s) {
		bShowBlockText	= b;
		bShowCsetText	= c;
		bShowHitNumText	= h;
		bShowScoreText	= s;
	
		if (!b && !c && !s && !h) bShowNoText=true;
		else bShowNoText=false;
	}
	private void popupHelp() {
		String msg = "The check boxes and radio buttons for the top 3 sections"
				+ "\n   take immediate effect, but are only saved on Save"
				+ "\n   and do not create a history event."
				
				+ "\n\nCoordinate changes only take effect on Save,"
				+ "\n   and do create a history event."
				
				+ "\n\nHistory Event: uses the < and > icons on the control bar."
				
				+ "\n\nGene# takes precedence over a change of coordinates,"
				+ "\n   Clear only clears the gene text box and enables the start/end."
				
				+ "\n\nClick in white space of a track for a subset of the filters."
				;
		if (seqObj.isRef() && seqObj.is3Track()) {
			msg +="\n\nReference 3-track only:" 
				+  "\n  g2x2: highlight hit-wires and genes that have a gene match to both "
				+  "\n        the left and right chromosomes."
				+  "\n  g2x1: highlight hit-wires and genes that have a gene match to ONLY "
				+  "\n        the left or right chromosome."
				+  "\nNOTE: all chromosomes MUST be annotated, or there are no results."
				;
		}
		util.Utilities.displayInfoMonoSpace(this, "Sequence Filter Quick Help", 
				msg, false);
	}
}

