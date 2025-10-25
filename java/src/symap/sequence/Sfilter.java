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
 * CAS570 add showG2xNCheck and update G2xN options; CAS571 add GeneNum with hit and Annot with Hit options
 * CAS572 add Block# 1st; CAS573 NC for Linux and change Gene# to have checkMark instead of "clear"
 * CAS575 some changes to how the Gene and Annotation are arranged
 */
public class Sfilter extends JDialog {	
	private static final long serialVersionUID = 1L;

	private static final String DEFAULT_UNIT_KB = BpNumber.KB;
	
	// WHEN ALTER these, alter in initValues and TrackData!
	static private final boolean bDefRuler=true, bDefGap=true, bDefCentromere=true;
	static private final boolean bDefGene=true, bDefScoreLine=false, bDefHitLen=true;
	static private final boolean bDefGeneNum=false, bDefAnnot=false, bDefGeneLine=false;
	static private final boolean bDefHighG2xN=false, bDefGeneHigh=true;
	static private final boolean bDefScoreText=false, bDefHitNumText=false, 
								 bDefBlockText=false, bDefBlock1stText=false,
			                     bDefCsetText=false, bDefNoText=true;
	static private final boolean bDefFlipped=false;
	
	// Current values - many shared between Filter and Popup; accessed in Sequence and TrackData
	// Copied in trackdata for history back/forth; this is serious rats nest...
	protected boolean bShowRuler, bShowGap, bShowCentromere, bShowGene, bShowScoreLine, bShowHitLen;
	protected boolean bShowGeneNum, bShowGeneNumHit,  bShowAnnotHit, bShowAnnot, bShowGeneLine, bHighGenePopup; 	
	protected boolean bShowScoreText, bShowHitNumText; 				
	protected boolean bShowBlockText, bShowBlock1stText, bShowCsetText, bShowNoText;    
	protected boolean bFlipped;
	protected boolean bGeneNCheck;
	protected boolean bHighG2x2, bHighG2x1, bHighG2x0, bShowG2xN; // These highlight gene and hit

	// Save values for Cancel
	private boolean	bSavRuler, bSavGap, bSavCentromere, bSavGene, bSavScoreLine, bSavHitLen;
	private boolean bSavGeneNum, bSavGeneNumHit, bSavAnnot, bSavAnnotHit, bSavGeneLine, bSavGeneHigh;
	private boolean bSavScoreText, bSavHitNumText, bSavBlockText, bSavBlock1stText, bSavCsetText, bSavNoText, bSavFlipped;
	private boolean bSavHighG2x2, bSavHighG2x1, bSavHighG2x0, bSavShowG2xN;
	
	protected boolean bSavGeneNCheck=false;
	protected Annotation savSelectGeneObj=null; 
	protected String savGeneNStr="";
	
	private String  savStartStr="", savEndStr="";
	private int     savStartInd=0, savEndInd=0;
	
	// Filter panel
	private JButton okButton, cancelButton, defaultButton, helpButton;
	private JPanel buttonPanel;
	private JPopupMenu popup=null; 
	private JMenuItem popupTitle; 
	
	private JCheckBox flippedCheck; 
	private JButton fullButton;
	private JLabel startLabel, endLabel;
	private JTextField startText, endText;
	private JComboBox <String> startCombo, endCombo; 
	
	private JCheckBox geneNCheck; 
	private JTextField geneNText;
	protected Annotation selectGeneObj=null; // setting this sets the startText/startEnd and 	
	
	private JCheckBox rulerCheck, gapCheck, centromereCheck, geneCheck, scoreLineCheck, hitLenCheck;
	private JCheckBox geneLineCheck, geneHighCheck; // delimiter, gene popup highlight gene
	private JCheckBox geneNumCheck, annotCheck,  geneNumHitCheck, annotHitCheck;					
	private JRadioButton scoreTextRadio, hitNumTextRadio, blockTextRadio,block1stTextRadio, csetTextRadio, noTextRadio; 
	
	private JRadioButton highG2x2Radio, highG2x1Radio, highG2x0Radio;  // set in Ref track, highlights genes/hits in L/Ref/R-tracks	
	private JCheckBox showG2xNCheck; // only show g2xN hits
	
	// Filter Popup
	private JMenuItem fullSeqPopup;
	private JCheckBoxMenuItem annotPopup, geneNumPopup, geneLinePopup, flippedPopup, annotHitPopup, geneNumHitPopup; 						 		 			
	private JRadioButtonMenuItem scoreTextPopup, hitNumTextPopup, blockTextPopup, block1stTextPopup, csetTextPopup, noTextPopup; 												

	// Other
	private Sequence seqObj;
	private DrawingPanel drawingPanel;
	private boolean bReplace=false; // Filter window text change in stateChanged; save in Save
	private boolean bNoListen=false;
	private int numGenes;
	
	// Created when 2d is displayed, one for each sequence and track 
	public Sfilter(Frame owner, DrawingPanel dp,  Sequence sequence) { 
		super(owner,"Sequence Filter #" + sequence.position, true); 
		this.drawingPanel = dp;	
		this.seqObj = sequence;
		seqObj.setFilter(this); // access values from here
		numGenes = seqObj.allAnnoVec.size();
		
		initValues();

		FilterListener listener = new FilterListener();
		
		createFilterPanel(listener);
		createPopup(listener);
		setEnables(); 
	}
/** Startup **/
	// Filter dialog 
	private void createFilterPanel(FilterListener listener) {		
		String b1="   ", b2="      ";
		int textLen=7;
		
	// Checkbox options; takes effect immediately; but lost on cancel (must use save)
		rulerCheck 		= Jcomp.createCheckNC("Ruler", "Show ruler along side of track"); 
						rulerCheck.addChangeListener(listener);
		gapCheck 		= Jcomp.createCheckNC("Gaps", "Show gaps sympbol along sequence"); 
						gapCheck.addChangeListener(listener);		
		centromereCheck = Jcomp.createCheckNC("Centromere", "Show centromere symbol along sequence"); 
						centromereCheck.addChangeListener(listener);	
		geneCheck 		= Jcomp.createCheckNC("Genes", "Show gene graphics along sequence"); 
						geneCheck.addChangeListener(listener);    
		hitLenCheck 	= Jcomp.createCheckNC("Hit length", "Show hit length line along the inner boundary of the sequence");			
						hitLenCheck.addChangeListener(listener);		
		scoreLineCheck 	= Jcomp.createCheckNC("Hit %Id bar", "Placement of Hit length is proportional to the %Identity");			
						scoreLineCheck.addChangeListener(listener);
		geneHighCheck 	= Jcomp.createCheckNC("Gene popup", "Highlight the gene when its Gene Popup is displayed"); 
						geneHighCheck.addChangeListener(listener);
		annotCheck 		= Jcomp.createCheckNC("Annotation", "Show the annotation in a grey box on the side of the track (zoom in to show)");	
						annotCheck.addChangeListener(listener);
		geneNumCheck 	= Jcomp.createCheckNC("Gene#", "Show the Gene# beside the gene");    
						geneNumCheck.addChangeListener(listener);  
		annotHitCheck 	= Jcomp.createCheckNC("Annotation", "Only show the annotation if the gene has a hit (zoom in to show)");	
						annotHitCheck.addChangeListener(listener);
		geneNumHitCheck = Jcomp.createCheckNC("Gene#", "Only show the Gene# if it has a hit");   
						geneNumHitCheck.addChangeListener(listener); 
		geneLineCheck 	= Jcomp.createCheckNC("Delimiter", "Seperate gene graphics with a horizonal bar");	
						geneLineCheck.addChangeListener(listener);
				
		block1stTextRadio = Jcomp.createRadioNC("Block# 1st", "Show the Block# beside the 1st hit of block");	
						block1stTextRadio.addChangeListener(listener);	
		blockTextRadio 	= Jcomp.createRadioNC("Block# All", "Show the Block# beside the each hit");	
						blockTextRadio.addChangeListener(listener);	
		csetTextRadio 	= Jcomp.createRadioNC("Collinear#", "Show the Collinear# beside the hit");	
						csetTextRadio.addChangeListener(listener);
		scoreTextRadio 	= Jcomp.createRadioNC("Hit %Id", "Show the %Identity beside the hit");	
						scoreTextRadio.addChangeListener(listener);
		hitNumTextRadio = Jcomp.createRadioNC("Hit#", "Show the Hit# beside the hit");			
						hitNumTextRadio.addChangeListener(listener);
		noTextRadio 	= Jcomp.createRadioNC("None", "Do not show any text beside the hit");	
						noTextRadio.addChangeListener(listener);
		ButtonGroup geneBG = new ButtonGroup();
		geneBG.add(block1stTextRadio);geneBG.add(blockTextRadio);
		geneBG.add(scoreTextRadio); geneBG.add(hitNumTextRadio);
		geneBG.add(csetTextRadio); geneBG.add(noTextRadio); 	
			
		// Ref only when 3-track
		highG2x2Radio = Jcomp.createRadioNC("g2x2", "Conserved gene: both L and R have g2 hit"); 
						highG2x2Radio.addChangeListener(listener);
		highG2x1Radio = Jcomp.createRadioNC("g2x1", "Not conserved: only L or R has g1 hit");    
						highG2x1Radio.addChangeListener(listener);
		highG2x0Radio = Jcomp.createRadioNC("None", "Ignore"); 		      
						highG2x0Radio.addChangeListener(listener);
		ButtonGroup hitBG = new ButtonGroup();
		hitBG.add(highG2x2Radio); hitBG.add(highG2x1Radio); hitBG.add(highG2x0Radio); 
		showG2xNCheck = Jcomp.createCheckNC("High only", "Only show the highlighted hit-wires");		
		showG2xNCheck.addChangeListener(listener);
		
	// Coordinate options; takes effect on Apply; only specific listeners
		flippedCheck = Jcomp.createCheckNC("Flip sequence    ", "Flip sequence so coordinates are Descending order"); 	
		
		fullButton = Jcomp.createMonoButtonSmNC("Full", "Set start/end to full sequence");
		fullButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setFullSequence();
			}
		});
		JPanel flipPanel = Jcomp.createGrayRowPanel(); 
		flipPanel.add(flippedCheck); flipPanel.add(fullButton);
			
		startText = new JTextField("0.0",textLen); 
		startText.setMaximumSize(startText.getPreferredSize()); startText.setMinimumSize(startText.getPreferredSize()); 
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
		startLabel = Jcomp.createLabelNC(b2+"Start", "Enter start coordinate for display"); startLabel.setEnabled(true);
		coordPanel.add(startLabel); coordPanel.add(startText); coordPanel.add(startCombo);
		endLabel = Jcomp.createLabelNC(b2+"End", "Enter end coordinate for display"); endLabel.setEnabled(true);
		coordPanel.add(endLabel);   coordPanel.add(endText); coordPanel.add(endCombo);
		
		JPanel genePanel = Jcomp.createGrayRowPanel(); 
		genePanel.add(new JLabel(b2+ "or   "));
		geneNCheck =  Jcomp.createCheckNC("Gene#", "Enter Gene# to search on"); 
		geneNCheck.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				geneNText.setEnabled(geneNCheck.isSelected());
				setCoordsEnable();
			}
		});
		geneNText =   new JTextField("",7); 
		geneNText.setMaximumSize(geneNText.getPreferredSize()); geneNText.setMinimumSize(geneNText.getPreferredSize());
		JLabel reg = Jcomp.createMonoLabelNC(" (region " + Globals.MAX_2D_DISPLAY_K + ")",
				"Center on gene; gene take precedences over start/end"); 
		genePanel.add(geneNCheck); genePanel.add(geneNText); genePanel.add(reg); 
		
	// Lower button panel
		okButton = Jcomp.createButtonNC("Save","Apply coordinate changes, save all changes, and close");
		okButton.addActionListener(listener);

		cancelButton = Jcomp.createButtonNC("Cancel", "Discard changes and close");
		cancelButton.addActionListener(listener);

		defaultButton = Jcomp.createButtonNC("Defaults", "Reset to defaults");
		defaultButton.addActionListener(listener);
		
		helpButton = Jhtml.createHelpIconUserSm(Jhtml.seqfilter);
		
		JButton helpButtonSm=  Jcomp.createIconButton(null,null,null,"/images/info.png", "Quick Help Popup");
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
		JLabel retain = Jcomp.createMonoLabelNC(" Save to retain changes", "Does not create new history event");
		addToGrid(contentPane, gbl, gbc, retain, rem);
		
		addToGrid(contentPane, gbl, gbc, new JLabel(b1+"Highlight"), 1); 
		addToGrid(contentPane, gbl, gbc, geneHighCheck, rem); 

		addToGrid(contentPane, gbl, gbc, new JLabel(b1+"Show Gene"), 1); 
		addToGrid(contentPane, gbl, gbc, geneLineCheck, 1); 
		addToGrid(contentPane, gbl, gbc, geneNumCheck,  1); 
		addToGrid(contentPane, gbl, gbc, annotCheck, rem); 
		
		addToGrid(contentPane, gbl, gbc, new JLabel(b1+"  "), 1); 
		addToGrid(contentPane, gbl, gbc, new JLabel(" Has hit"), 1); // CAS575 move this before others; Changed some labels.
		addToGrid(contentPane, gbl, gbc, geneNumHitCheck,  1); 
		addToGrid(contentPane, gbl, gbc, annotHitCheck, rem); 
		
		// section 2
		addToGrid(contentPane, gbl, gbc, new JSeparator(), rem);
		addToGrid(contentPane, gbl, gbc, new JLabel(b1+"Show Text"), 1); 
		addToGrid(contentPane, gbl, gbc, block1stTextRadio, 1);
		addToGrid(contentPane, gbl, gbc, blockTextRadio, 1);
		addToGrid(contentPane, gbl, gbc, csetTextRadio, rem);
		
		addToGrid(contentPane, gbl, gbc, new JLabel("               "), 1); 
		addToGrid(contentPane, gbl, gbc, scoreTextRadio, 1);
		addToGrid(contentPane, gbl, gbc, hitNumTextRadio, 1);
		addToGrid(contentPane, gbl, gbc, noTextRadio, rem);
				
		// section 3
		addToGrid(contentPane, gbl, gbc, new JSeparator(), rem);
		addToGrid(contentPane, gbl, gbc, new JLabel(b1+"Graphics"), 1); 
		addToGrid(contentPane, gbl, gbc, rulerCheck, 1);
		addToGrid(contentPane, gbl, gbc, gapCheck, 1);
		addToGrid(contentPane, gbl, gbc, centromereCheck, rem);
		
		addToGrid(contentPane, gbl, gbc, new JLabel("   "), 1); 
		addToGrid(contentPane, gbl, gbc, geneCheck, 1);
		addToGrid(contentPane, gbl, gbc, hitLenCheck, 1);
		addToGrid(contentPane, gbl, gbc, scoreLineCheck, rem);
		
		
		
		// section 4
		addToGrid(contentPane, gbl, gbc, new JSeparator(),rem);
		JLabel apply = Jcomp.createMonoLabelNC(" Save to apply changes", "Create new history event");
		addToGrid(contentPane, gbl, gbc, apply, rem);
		
		addToGrid(contentPane, gbl, gbc, new JLabel(b1+"Coordinates   "), 1);
		addToGrid(contentPane, gbl, gbc, flipPanel, rem);
		addToGrid(contentPane, gbl, gbc, coordPanel, rem);
		addToGrid(contentPane, gbl, gbc, genePanel, rem);

		if (seqObj.isRef() && seqObj.is3Track() && numGenes>0) { 
			addToGrid(contentPane, gbl, gbc, new JSeparator(),rem);
			
			JLabel refOnly = Jcomp.createMonoLabelNC(" Reference only", "Does not create new history event");
			addToGrid(contentPane, gbl, gbc, refOnly, rem);
			
			addToGrid(contentPane, gbl, gbc, new JLabel(b1+"Highlight"), 1); 
			addToGrid(contentPane, gbl, gbc, highG2x2Radio, 1); 
			addToGrid(contentPane, gbl, gbc, highG2x1Radio, 1); 
			addToGrid(contentPane, gbl, gbc, highG2x0Radio, rem); 
			
			addToGrid(contentPane, gbl, gbc, new JLabel(b1+"Show"), 1); 
			addToGrid(contentPane, gbl, gbc, showG2xNCheck, rem); 
		}
		
		addToGrid(contentPane, gbl, gbc, buttonPanel, rem);

		pack();
		setResizable(false);
		setLocationRelativeTo(null); 
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
		
		flippedPopup 		= new JCheckBoxMenuItem("  Flip sequence"); 
		fullSeqPopup 		= new JCheckBoxMenuItem("  Full"); 
	
		// if geneGraphicsCheck is turned off, these still show but do nothing - breaks when try to make it conditional
		annotPopup			= new JCheckBoxMenuItem("  Annotation all"); 
		annotHitPopup		= new JCheckBoxMenuItem("  Annotation with hit"); 
		geneNumPopup 		= new JCheckBoxMenuItem("  Gene# all"); 
		geneNumHitPopup 	= new JCheckBoxMenuItem("  Gene# with hit"); 
		geneLinePopup 		= new JCheckBoxMenuItem("  Delimiter"); 

		scoreTextPopup 		= new JRadioButtonMenuItem("  Hit %Id"); 	
		hitNumTextPopup 	= new JRadioButtonMenuItem("  Hit# "); 	
		block1stTextPopup 	= new JRadioButtonMenuItem("  Block# 1st"); 	
		blockTextPopup 		= new JRadioButtonMenuItem("  Block# All"); 	
		csetTextPopup 		= new JRadioButtonMenuItem("  Collinear# "); 	
		noTextPopup 		= new JRadioButtonMenuItem("  None"); 
		ButtonGroup grp = new ButtonGroup();
		grp.add(scoreTextPopup); grp.add(hitNumTextPopup); grp.add(blockTextPopup); grp.add(block1stTextPopup);
		grp.add(csetTextPopup);  grp.add(noTextPopup);
		noTextPopup.setSelected(true);
		
		popupTitle = new JMenuItem("Sequence Options"); popupTitle.setEnabled(false);
		popup.add(popupTitle);
		
		popup.addSeparator();
		JMenuItem stext = new JMenuItem("Coordinate changes"); stext.setEnabled(false); // do not use JLabel; results in white space on right
		popup.add(stext);
		popup.add(flippedPopup); 	flippedPopup.addActionListener(listener);
		popup.add(fullSeqPopup); 	fullSeqPopup.addActionListener(listener);
		
		popup.add(new JSeparator()); 
		JMenuItem gtext = new JMenuItem("Show gene (toggle)"); gtext.setEnabled(false);
		popup.add(gtext);
		popup.add(geneNumHitPopup); geneNumHitPopup.addActionListener(listener); 
		popup.add(geneNumPopup); 	geneNumPopup.addActionListener(listener);
		popup.add(geneLinePopup);	geneLinePopup.addActionListener(listener);
		JMenuItem atext = new JMenuItem("  Zoom in to view"); atext.setEnabled(false);
		popup.add(atext);
		popup.add(annotHitPopup); 	annotHitPopup.addActionListener(listener);
		popup.add(annotPopup); 		annotPopup.addActionListener(listener);
		
		popup.add(new JSeparator()); 
		JMenuItem text = new JMenuItem("Show text (one only)"); text.setEnabled(false);
		popup.add(text);
		popup.add(block1stTextPopup);  block1stTextPopup.addActionListener(listener);
		popup.add(blockTextPopup);  blockTextPopup.addActionListener(listener);
		popup.add(csetTextPopup);   csetTextPopup.addActionListener(listener);
		popup.add(hitNumTextPopup); hitNumTextPopup.addActionListener(listener);
		popup.add(scoreTextPopup);	scoreTextPopup.addActionListener(listener);
		popup.add(noTextPopup);     noTextPopup.addActionListener(listener);
		
		popup.setMaximumSize(popup.getPreferredSize()); 
		popup.setMinimumSize(popup.getPreferredSize());
	}
	// disable if not exist 
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
		geneNumCheck.setEnabled(b); 	geneNumHitCheck.setEnabled(b);
		geneLineCheck.setEnabled(b);
		annotCheck.setEnabled(b);		annotHitCheck.setEnabled(b);
		highG2x2Radio.setEnabled(b);	
		highG2x1Radio.setEnabled(b);	
		geneHighCheck.setEnabled(b); 
		
		geneNCheck.setEnabled(b);		geneNText.setEnabled(b);
		
		annotPopup.setEnabled(b); 		annotHitPopup.setEnabled(b);
		geneNumPopup.setEnabled(b);		geneNumHitPopup.setEnabled(b);
		geneLinePopup.setEnabled(b);
		
		bNoListen=false;
	}
/** End Startup **/
	
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
			actionCancel();
			setVisible(false); 
		}
	}
	/***********************************************************/
	/*** XXX Called when Sequence Filter button clicked ***/
	public void displaySeqFilter() {
		if (isShowing()) { // this seems to always be false..
			super.setVisible(true);
			return;
		}	
		saveCurrentSettings(); 
		
		// values can be changed in popup
		flippedCheck.setSelected(bSavFlipped); 
		geneNumCheck.setSelected(bSavGeneNum && geneNumCheck.isEnabled());
		geneNumHitCheck.setSelected(bSavGeneNumHit && geneNumHitCheck.isEnabled());
		annotCheck.setSelected(bSavAnnot && annotCheck.isEnabled());
		annotHitCheck.setSelected(bSavAnnotHit && annotHitCheck.isEnabled());
		geneLineCheck.setSelected(bSavGeneLine && geneLineCheck.isEnabled()); 
		
		block1stTextRadio.setSelected(bSavBlock1stText);
		blockTextRadio.setSelected(bSavBlockText);
		csetTextRadio.setSelected(bSavCsetText);
		scoreTextRadio.setSelected(bSavScoreText);
		hitNumTextRadio.setSelected(bSavHitNumText);
		noTextRadio.setSelected(bSavNoText);		
					
		// values only changed in filter window
		rulerCheck.setSelected(bSavRuler);
		gapCheck.setSelected(bSavGap 			&& gapCheck.isEnabled());
		centromereCheck.setSelected(bSavCentromere && centromereCheck.isEnabled());
		geneCheck.setSelected(bSavGene   		&& geneCheck.isEnabled());
		geneHighCheck.setSelected(bSavGeneHigh 	&& geneHighCheck.isEnabled());
		hitLenCheck.setSelected(bSavHitLen);
		scoreLineCheck.setSelected(bSavScoreLine);
		
		geneNCheck.setSelected(bSavGeneNCheck);
		if (bSavGeneNCheck && selectGeneObj!=null) {
			geneNText.setText(selectGeneObj.getFullGeneNum());
			seqObj.setSelectGene(selectGeneObj); 
		}
		else geneNText.setText("");
		setCoordsEnable();
		
		highG2x2Radio.setSelected(bSavHighG2x2 && highG2x2Radio.isEnabled()); 
		highG2x1Radio.setSelected(bSavHighG2x1 && highG2x1Radio.isEnabled()); 
		highG2x0Radio.setSelected(bSavHighG2x0 && highG2x0Radio.isEnabled()); 
		showG2xNCheck.setSelected(bSavShowG2xN && showG2xNCheck.isEnabled());
		
		super.setVisible(true); 
	}
	
	private void saveCurrentSettings() { // showSeqFilter (Button) and popup
		bSavRuler = 	  bShowRuler;
		bSavGap = 		  bShowGap        && gapCheck.isEnabled();
		bSavCentromere =  bShowCentromere && centromereCheck.isEnabled();
		bSavGene = 		  bShowGene       && geneCheck.isEnabled();
		bSavHitLen = 	  bShowHitLen;
		bSavScoreLine =   bShowScoreLine;
		
		bSavGeneHigh =    bHighGenePopup && geneHighCheck.isEnabled(); 
		bSavGeneNum = 	  bShowGeneNum   && geneNumCheck.isEnabled();
		bSavGeneNumHit =  bShowGeneNumHit&& geneNumHitCheck.isEnabled();
		bSavAnnot = 	  bShowAnnot     && annotPopup.isEnabled();
		bSavAnnotHit = 	  bShowAnnotHit  && annotHitPopup.isEnabled();
		bSavGeneLine =    bShowGeneLine  && geneLinePopup.isEnabled(); 
	
		bSavScoreText =   bShowScoreText;
		bSavHitNumText =  bShowHitNumText;
		bSavBlock1stText = bShowBlock1stText;
		bSavBlockText =   bShowBlockText;
		bSavCsetText =    bShowCsetText;
		bSavNoText =      bShowNoText;
		
		bSavFlipped = 	  bFlipped;	
		
		bSavGeneNCheck 	 = bGeneNCheck;
		savGeneNStr 	 = geneNText.getText();
		savSelectGeneObj = selectGeneObj;
		
		bSavHighG2x2 = bHighG2x2; 
		bSavHighG2x1 = bHighG2x1;
		bSavHighG2x0 = bHighG2x0;
		bSavShowG2xN = bShowG2xN;
		
		// values may have changed from mouse drag, so these are current
		int start   = seqObj.getStart();
		Object item = startCombo.getSelectedItem();
		double val =  seqObj.getValue(start, item.toString()); 
		startText.setText(val+"");
		
		int end = seqObj.getEnd(); 
		item = endCombo.getSelectedItem();
		val  = seqObj.getValue(end, item.toString()); 
		endText.setText(val + "");
		
		savStartStr = 	  startText.getText().trim();
		savEndStr =   	  endText.getText().trim();
		savStartInd = 	  startCombo.getSelectedIndex();
		savEndInd =   	  endCombo.getSelectedIndex();
	}
	/***************************************************************************/
	// The change in coords get processed here on Save
	// Check boxes (except geneNCheck and flippedCheck) are processed immediately; 
	private boolean actionSave()  {
		if (!seqObj.hasLoad()) return true;
		double tstart=0, tend=0;
		boolean bChg=false;
		
		String gStr = geneNText.getText().trim(); // gene# takes precedence
		if (gStr.equals("") && geneNCheck.isSelected()) {
			geneNCheck.setSelected(false);
			bGeneNCheck = false;
		}
		
		if (geneNCheck.isSelected()) {
			if (!Utilities.isValidGenenum(gStr)) {
				Utilities.showWarning(gStr + " is not a valid Gene#");
				return false;
			}
			if (!gStr.equals(savGeneNStr)) { 
				if (!gStr.contains(".")) gStr += ".";
				
				int mid = seqObj.getSelectGeneCoord(gStr); // highlight if found
				if (mid<0) {
					Utilities.showWarning(gStr + " is not found on this chromosome."); 
					return false;
				}
				bGeneNCheck = true;
				
				tstart = Math.max(0,mid-Globals.MAX_2D_DISPLAY);
				tend   = Math.min(seqObj.getTrackSize(), mid+Globals.MAX_2D_DISPLAY); 
	
				startText.setText((tstart/1000)+"");startCombo.setSelectedItem(BpNumber.KB);
				endText.setText((tend/1000)+"");    endCombo.setSelectedItem(BpNumber.KB);
				
				seqObj.setStart((int)tstart);
				seqObj.setEnd((int)tend);
				
				bChg = true; 
			}
		}
		else {
			bChg = applyCoordChanges(); // uses entered values
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

	protected void actionCancel() {
		if (bSavHighG2x2 || bSavHighG2x1) xDefG2xN(); 
		
		gapCheck.setSelected(bSavGap);
		centromereCheck.setSelected(bSavCentromere);
		rulerCheck.setSelected(bSavRuler);
		geneCheck.setSelected(bSavGene);
		hitLenCheck.setSelected(bSavHitLen);
		scoreLineCheck.setSelected(bSavScoreLine);
		
		annotCheck.setSelected(bSavAnnot);     annotHitCheck.setSelected(bSavAnnotHit);
		geneNumCheck.setSelected(bSavGeneNum); geneNumHitCheck.setSelected(bSavGeneNumHit);
		geneLineCheck.setSelected(bSavGeneLine);
		geneHighCheck.setSelected(bSavGeneHigh);
		highG2x2Radio.setSelected(bSavHighG2x2);
		highG2x1Radio.setSelected(bSavHighG2x1);
		highG2x0Radio.setSelected(bSavHighG2x0);
		showG2xNCheck.setSelected(bSavShowG2xN);
		
		block1stTextRadio.setSelected(bSavBlock1stText);
		blockTextRadio.setSelected(bSavBlockText); 
		csetTextRadio.setSelected(bSavCsetText); 
		scoreTextRadio.setSelected(bSavScoreText); 
		hitNumTextRadio.setSelected(bSavHitNumText);
		noTextRadio.setSelected(bSavNoText);
		
		flippedCheck.setSelected(bSavFlipped);
		
		seqObj.setSelectGene(savSelectGeneObj);
		geneNCheck.setSelected(bSavGeneNCheck);
		geneNText.setText(savGeneNStr);  
		
		startText.setText(savStartStr); startCombo.setSelectedIndex(savStartInd);
		endText.setText(savEndStr);     endCombo.setSelectedIndex(savEndInd);
	}
	private void actionDefault() { // Default button; 
		xDefG2xN(); 
		
		rulerCheck.setSelected(bDefRuler);
		gapCheck.setSelected(bDefGap && gapCheck.isEnabled());
		centromereCheck.setSelected(bDefCentromere && centromereCheck.isEnabled());
		geneCheck.setSelected(bDefGene && geneCheck.isEnabled());
		hitLenCheck.setSelected(bDefHitLen);
		scoreLineCheck.setSelected(bDefScoreLine);
		
		annotCheck.setSelected(bDefAnnot);     annotHitCheck.setSelected(bDefAnnot);
		geneNumCheck.setSelected(bDefGeneNum); geneNumHitCheck.setSelected(bDefGeneNum);
		geneLineCheck.setSelected(bDefGeneLine);
		geneHighCheck.setSelected(bDefGeneHigh);
		highG2x2Radio.setSelected(bDefHighG2xN);
		highG2x1Radio.setSelected(bDefHighG2xN);
		highG2x0Radio.setSelected(!bDefHighG2xN);
		
		scoreTextRadio.setSelected(bDefScoreText);
		hitNumTextRadio.setSelected(bDefHitNumText);
		block1stTextRadio.setSelected(bDefBlock1stText);
		blockTextRadio.setSelected(bDefBlockText);
		csetTextRadio.setSelected(bDefCsetText);
		noTextRadio.setSelected(bDefNoText);
		
		flippedCheck.setSelected(bDefFlipped); 	
		
		geneNText.setText(""); geneNCheck.setSelected(false);
		seqObj.setSelectGene(null);
		selectGeneObj = null;
		
		startCombo.setSelectedItem(DEFAULT_UNIT_KB);
		startText.setText("0.0");
		
		endCombo.setSelectedItem(DEFAULT_UNIT_KB);
		int size = seqObj.getTrackSize();
		double end  = seqObj.getValue(size, DEFAULT_UNIT_KB);
		endText.setText(end + "");
		endCombo.setSelectedItem(DEFAULT_UNIT_KB);
	}

	private void setCoordsEnable() {
		boolean b = !geneNCheck.isSelected();
		startLabel.setEnabled(b); startText.setEnabled(b);
		endLabel.setEnabled(b); endText.setEnabled(b);
		fullButton.setEnabled(b); flippedCheck.setEnabled(b);
	}
	
	private void setFullSequence() {
		geneNCheck.setSelected(false); 
		
		startText.setText("0.0"); 
		startCombo.setSelectedItem(DEFAULT_UNIT_KB);
		
		int size = seqObj.getTrackSize();
		double end  = seqObj.getValue(size, DEFAULT_UNIT_KB);
		endText.setText(end + "");
		endCombo.setSelectedItem(DEFAULT_UNIT_KB);
	}
	private boolean applyCoordChanges() { // Full Coordinates and actionSave
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
	
	/************************************************************************************/
	private class FilterListener implements ActionListener, ChangeListener, ItemListener {
		private FilterListener() { }

		// Called for panel ok/cancel/default buttons and popup menu events
		public void actionPerformed(ActionEvent event) { 
			Object src = event.getSource();
			boolean bUp = false, bRep = false;	

			if (src == okButton) { // changed already made except of for sequence
				actionSave();
				setVisible(false); 
			}
			else if (src == cancelButton) {
				actionCancel();
				setVisible(false); 
			}
			else if (src == defaultButton) {
				actionDefault(); 
			}
			// The rest are for the popup and happen immediately
			else if (src == fullSeqPopup) { 
				setFullSequence();
				bUp = applyCoordChanges();
			}
			else if (src == flippedPopup) 		bUp =  xFlipSeq(!bSavFlipped);
			else if (src == annotPopup) 		bRep = xShowAnnot(annotPopup.getState());
			else if (src == annotHitPopup) 		bRep = xShowAnnotHit(annotHitPopup.getState());
			else if (src == geneNumPopup) 		bRep = xShowGeneNum(geneNumPopup.getState());
			else if (src == geneNumHitPopup) 	bRep = xShowGeneNumHit(geneNumHitPopup.getState());
			else if (src == geneLinePopup) 		bRep = xShowGeneLine(geneLinePopup.getState());
			
			else if (src == scoreTextPopup) 	bRep = xShowScoreText(scoreTextPopup.isSelected());
			else if (src == hitNumTextPopup) 	bRep = xShowHitNumText(hitNumTextPopup.isSelected());
			else if (src == block1stTextPopup) 	bRep = xShowBlock1stText(block1stTextPopup.isSelected());
			else if (src == blockTextPopup) 	bRep = xShowBlockText(blockTextPopup.isSelected());
			else if (src == csetTextPopup) 		bRep = xShowCsetText(csetTextPopup.isSelected());
			else if (src == noTextPopup) 		bRep = xShowNoText(noTextPopup.isSelected());
				
			if (bUp || bRep) {
				drawingPanel.smake("Sf: filterlistener actionperformed");	
				if (bUp) drawingPanel.updateHistory();
				else drawingPanel.replaceHistory();
			}
		}
		// From Filter Panel, all events that use immediate change (not flipped or geneNCheck)
		public void stateChanged(ChangeEvent event) {
			if (bNoListen) return;
			
			Object src = event.getSource();
			boolean bDiff = false;
	
			if (src == geneCheck || numGenes==0) {// no anno shown/exist 
				bDiff = xShowGene(geneCheck.isSelected());
				boolean b = geneCheck.isSelected();
				annotCheck.setEnabled(b);	annotHitCheck.setEnabled(b);
				geneNumCheck.setEnabled(b);	geneNumHitCheck.setEnabled(b);
				geneLineCheck.setEnabled(b); 
				geneHighCheck.setEnabled(b);
				highG2x2Radio.setEnabled(b);
				highG2x1Radio.setEnabled(b);
				highG2x0Radio.setEnabled(b);
				showG2xNCheck.setEnabled(highG2x1Radio.isSelected() || highG2x2Radio.isSelected());
			}
			else if (src == annotCheck)		{
				bDiff = xShowAnnot(annotCheck.isSelected());
				if (annotCheck.isSelected()) annotHitCheck.setSelected(false);
			}
			else if (src == annotHitCheck)	{
				bDiff = xShowAnnotHit(annotHitCheck.isSelected());
				if (annotHitCheck.isSelected()) annotCheck.setSelected(false);
			}
			else if (src == geneNumCheck) 	{
				bDiff = xShowGeneNum(geneNumCheck.isSelected());
				if (geneNumCheck.isSelected()) geneNumHitCheck.setSelected(false);
			}
			else if (src == geneNumHitCheck) {
				bDiff = xShowGeneNumHit(geneNumHitCheck.isSelected());
				if (geneNumHitCheck.isSelected()) geneNumCheck.setSelected(false);
			}
			else if (src == geneLineCheck) 	bDiff = xShowGeneLine(geneLineCheck.isSelected());
			else if (src == geneHighCheck)	bDiff = xHighGenePopup(geneHighCheck.isSelected());
		
			else if (src == block1stTextRadio) bDiff = xShowBlock1stText(block1stTextRadio.isSelected());
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
			
			else if (src == highG2x2Radio) bDiff = xHighG2xN(2, highG2x2Radio.isSelected());	
			else if (src == highG2x1Radio) bDiff = xHighG2xN(1, highG2x1Radio.isSelected());
			else if (src == highG2x0Radio) bDiff = xHighG2xN(0, highG2x0Radio.isSelected());
			else if (src == showG2xNCheck) bDiff = xShowG2xN(showG2xNCheck.isSelected());
			
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
			annotHitPopup.setSelected(bSavAnnotHit);
			geneNumPopup.setSelected(bSavGeneNum);
			geneNumHitPopup.setSelected(bSavGeneNumHit);
			geneLinePopup.setSelected(bSavGeneLine);
			
			scoreTextPopup.setSelected(bSavScoreText); 
			hitNumTextPopup.setSelected(bSavHitNumText);
			block1stTextPopup.setSelected(bSavBlock1stText);
			blockTextPopup.setSelected(bSavBlockText);
			csetTextPopup.setSelected(bSavCsetText);
			noTextPopup.setSelected(bSavNoText);
			
			flippedPopup.setSelected(bSavFlipped); 
		}
	} // end popup listener
	
	//////////////////////////////////////////////////////////////////////
	private void initValues() {
		bFlipped 		= bDefFlipped; 
		bShowRuler      = bDefRuler;
		bShowGap        = bDefGap;
		bShowCentromere = bDefCentromere;
		bShowGene 		= bDefGene;
		bShowHitLen 	= bDefHitLen;
		bShowScoreLine  = bDefScoreLine;
	
		bShowGeneNum    = bShowGeneNumHit = bDefGeneNum;
		bShowAnnot      = bShowAnnotHit = bDefAnnot;
		bShowGeneLine   = bDefGeneLine;
		bHighGenePopup  = bDefGeneHigh;
		
		bShowScoreText 	= bDefScoreText; 	
		bShowHitNumText = bDefHitNumText;
		bShowBlock1stText = bDefBlock1stText;
		bShowBlockText 	= bDefBlockText;
		bShowCsetText 	= bDefCsetText;
		bShowNoText 	= bDefNoText;
		
		bHighG2x2 		= bDefHighG2xN;
		bHighG2x1 		= bDefHighG2xN;
		bHighG2x0		= !bDefHighG2xN;
		bShowG2xN		= bDefHighG2xN;
		
		bGeneNCheck 	= false;
		selectGeneObj 	= null;
		
		// Start and end get initialized from seqObj when filter window popups up
	}
	
	protected boolean xFlipSeq(boolean flip) { // track & filter
		if (bFlipped != flip) {bFlipped = flip;	seqObj.setTrackBuild(); return true;}
		return false;
	}
	private boolean xShowRuler(boolean show) {
		if (bShowRuler != show) {bShowRuler = show; seqObj.setTrackBuild();return true;}
		return false;
	}
	private boolean xShowGap(boolean show) {
		if (bShowGap != show) {bShowGap = show; seqObj.setTrackBuild();return true;}
		return false;
	}
	private boolean xShowCentromere(boolean show) {
		if (bShowCentromere != show) {bShowCentromere = show; seqObj.setTrackBuild();return true;}
		return false;
	}
	private boolean xShowGene(boolean show) { 
		if (bShowGene != show) {bShowGene = show; seqObj.setTrackBuild(); return true;}
		return false;
	}
	private boolean xShowHitLen(boolean show) { 
		if (bShowHitLen != show) {bShowHitLen = show; seqObj.setTrackBuild(); return true; }
		return false;
	}
	private boolean xShowScoreLine(boolean show) { 
		if (bShowScoreLine != show) {bShowScoreLine = show; seqObj.setTrackBuild(); return true;}
		return false;
	}
	private boolean xShowAnnot(boolean show) {
		if (seqObj.allAnnoVec.size() == 0) {bShowAnnot = false; return false;}
		
		if (bShowAnnot != show)  {
			bShowAnnot = show; 
			if (show) bShowAnnotHit = false;
			seqObj.setTrackBuild();
			return true;
		}
		return false;
	}
	private boolean xShowAnnotHit(boolean show) {
		if (seqObj.allAnnoVec.size() == 0) {bShowAnnotHit = false; return false;}
		
		if (bShowAnnotHit != show)  {
			bShowAnnotHit = show; 
			if (show) bShowAnnot = false;
			seqObj.setTrackBuild();
			return true;
		}
		return false;
	}
	private boolean xShowGeneNum(boolean show) {  
		if (bShowGeneNum != show) {
			bShowGeneNum = show; 
			if (show) bShowGeneNumHit = false;
			seqObj.setTrackBuild();
			return true;
		}
		return false;
	}
	private boolean xShowGeneNumHit(boolean show) {  
		if (bShowGeneNumHit != show) {
			bShowGeneNumHit = show; 
			if (show) bShowGeneNum = false;
			seqObj.setTrackBuild();
			return true;
		}
		return false;
	}
	private boolean xShowGeneLine(boolean show) {
		if (bShowGeneLine != show) {bShowGeneLine = show; seqObj.setTrackBuild(); return true;}
		return false;
	}
	private boolean xHighGenePopup(boolean high) { 
		if (bHighGenePopup != high) { 
			bHighGenePopup = high; seqObj.setTrackBuild();
			if (!high) 
				for (Annotation aObj : seqObj.allAnnoVec) aObj.setIsPopup(false); // exons are highlighted to, so use all
			return true;
		}
		return false;
	}
	private boolean xShowScoreText(boolean show) { 
		if (bShowScoreText != show) {xSetText(false, false, false, false, show); seqObj.setTrackBuild(); return true;}
		return false;
	}
	private boolean xShowHitNumText(boolean show) { 
		if (bShowHitNumText != show) {xSetText(false, false, false, show, false); seqObj.setTrackBuild(); return true; }
		return false;
	}
	private boolean xShowBlock1stText(boolean show) { 
		if (bShowBlock1stText != show) {xSetText(show, false, false, false, false); seqObj.setTrackBuild(); return true; }
		return false;
	}
	private boolean xShowBlockText(boolean show) { 
		if (bShowBlockText != show) {xSetText(false, show, false, false, false); seqObj.setTrackBuild(); return true; }
		return false;
	}
	private boolean xShowCsetText(boolean show) { 
		if (bShowCsetText != show) {xSetText(false, false, show, false, false); seqObj.setTrackBuild(); return true;}
		return false;
	}
	private boolean xShowNoText(boolean show) { 
		if (bShowNoText != show) {xSetText(false, false, false, false, false); seqObj.setTrackBuild(); return true; }
		return false;
	}
	private void xSetText(boolean b1, boolean b, boolean c, boolean h, boolean s) {
		bShowBlock1stText = b1;
		bShowBlockText	= b;
		bShowCsetText	= c;
		bShowHitNumText	= h;
		bShowScoreText	= s;
	
		if (!b1 && !b && !c && !s && !h) bShowNoText=true;
		else bShowNoText=false;
	}
	/*  g2xN methods reference only */
	private boolean xHighG2xN(int which, boolean high) {
		if (which==2) {
			if (bHighG2x2==high) return false;
			bHighG2x2 = high; 
		}
		else if (which==1) {
			if (bHighG2x1==high) return false;
			bHighG2x1 = high;
		}
		else if (which==0) {
			if (bHighG2x0==high) return false;
			bHighG2x0 = high;
		}
		showG2xNCheck.setEnabled(bHighG2x2 || bHighG2x1);
		
		seqObj.refSetG2xN(which, high); // sets the genes.hit highlighted in L-Ref-R tracks (very messy)
		
		return true;
	}
	private boolean xShowG2xN(boolean high) {
		if (bShowG2xN==high) return false;
		
		bShowG2xN=high;
		highG2x0Radio.setEnabled(!high); // have to uncheck Show before select another
		highG2x1Radio.setEnabled(!high);
		highG2x2Radio.setEnabled(!high);
		
		seqObj.showG2xN(high); // turns special flag on/off
		
		return true;
	}
	private void xDefG2xN() {
		if (bHighG2x2)      xHighG2xN(2, false);
		else if (bHighG2x1) xHighG2xN(1, false);
		
		xShowG2xN(false);
		
		showG2xNCheck.setSelected(false); showG2xNCheck.setEnabled(false);
		highG2x0Radio.setSelected(true);
	}
	
	///////////////////////////////////////////////////////////////
	private void popupHelp() {
		String msg = "The check boxes and radio buttons for the top 3 sections"
				+ "\n   take immediate effect, but are only saved on Save"
				+ "\n   and do not create a history event."
				
				+ "\n\nCoordinate changes only take effect on Save,"
				+ "\n   and do create a history event."
				+ "\nChecking Gene# changed the coordinates and highlights the gene."
				
				+ "\n\nHistory Event: uses the < and > icons on the control bar."
				
				+ "\n\nClick in white space of a track for a subset of the filters."
				;
		if (seqObj.isRef() && seqObj.is3Track()) {
			msg +="\n\nReference 3-track only:" 
				+  "\n  g2x2: Both left and right are g2 to a reference gene (conserved) "
				+  "\n  g2x1: Only left or right is g2 to a reference gene (unique)"
				+  "\n        where g2 is a gene on both ends of the hit"
				+  "\n  High only: only show the highlighted hit-wires"
				+  "\nNOTE: all chromosomes MUST be annotated, or there are no results."
				+  "\n  Highlights, etc are often lost when view is changed."
				;
		}
		util.Utilities.displayInfoMonoSpace(this, "Sequence Filter Quick Help", 
				msg, false);
	}
}
