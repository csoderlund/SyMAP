package symap.manager;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.JTextField;

import backend.Constants;
import symap.Globals;
import util.Jcomp;
import util.Jhtml;
import util.Utilities;
import util.Popup;
import util.FileDir;

/**********************************************
 * The parameter window for Alignment&Synteny
 * CAS590 add collapse panels and put parameters in order
 */

public class PairParams extends JDialog {
	private static final long serialVersionUID = 1L;
	protected static String [] mergeOpts = {"None", "Overlap", "Close"};
	protected static String [] coSetNumOpts = {"By Order", "By Size"};
	protected static String [] coSetAlgoOpts = {"Advanced", "Simple"};
	
	// ManagerFrame.alignSelectedPair and updateEnableButtons use similar text
	private String [] actAlt0 =    {"Run MUMmer   ", // blank if not exist;  Selected Pair
			                        "Load MUMmer ", // A if exists and MUMmer selected
			                        "Load Blast  "};  // B exists and Load selected
	
	private String[]  actOptions = {"Load MUMmer  ", // if Check; may be replaced with Load Blast
								    "Synteny Only     ",
								    "Number Pseudo    ",
								    "Collinear Sets   "}; 
	private int actCnS=0, actSyn=1, actPseudo=2, actCoSet=3;
	private String [] actions;
	
	// Order MUST be same in LABELS, SYMBOLS, createMainLabels, setValue, getValue; 
	//                       Mpair.paramKey and Mpair.paramDef
	// Do not change without changing access everywhere else!! (7 places)
	private String [] LABELS = { // Order must be same as createMainPanel
		"Use MUMmer",
		"Concat",  "Mask1", "Mask2", 
		"NUCmer args", "PROmer args", "Self args", "Use NUCmer","Use PROmer", 
		"Load Blast files",  // must start with the word 'Load';  CAS589 new
		"Proteins", "%Identity", "E-value", // CAS591 new; if !protein, then nucleotide
		
		"Number pseudo", 											
		"Algorithm 1 (modified original)", 
		"Top N piles", 
		"Algorithm 2 (exon-intron)",  
		"Gene", "Exon", "Len","G0_Len", 
		"EE", "EI", "En", "II", "In", 
		"Algorithm",				// Order/Size CoSet1/CoSet2 pulldown 
		
		"Min hits", "Strict", "Orient", "Merge:", 
		"None", "Order1", "Order2", 			 
	};
	
	// if change, change in Mpair too; defaults are in Mpair; written to DB and file
	// order the same as above; constant strings are repeated multiple times in MPair
	private static final String [] SYMBOLS = { // Mpairs.paramKey
		// Alignment
		"MUMmer", "concat", "mask1", "mask2",
			"nucmer_args", "promer_args", "self_args", "nucmer_only","promer_only",
		"Blast", 
			"isAA", "pIdentity", "evalue",
		// Cluster
		"number_pseudo",
		"algo1", "topn", 
		"algo2", 
		"gene_scale", "exon_scale","len_scale","g0_scale", 
		"EE_pile", "EI_pile", "En_pile", "II_pile", "In_pile",
		// Collinear sets	
		"CoSet", 	// two booleans together, e.g. 1:0 
		// synteny
		"mindots", "strict_blocks", "same_orient", "merge_blocks",
		// Order
		"order_none", "order_proj1", "order_proj2"	
	};

	private Mpair mp = null;
	private Mproject mProj1 = null, mProj2 = null;
	private ManagerFrame manFrame=null;
	private boolean isAlignDone;	// Could be A, B or check
	private boolean isSelf=false;
	
	/******************************************************************/
	protected PairParams(Mpair mp, ManagerFrame manFrame, boolean alignDone) {
		this.mp = mp;
		this.mProj1 = mp.mProj1;
		this.mProj2 = mp.mProj2;
		this.manFrame = manFrame;
		this.isAlignDone = alignDone;
		isSelf = mProj1.getIdx()==mProj2.getIdx();
		
		createMainLabels();
		createMainPanels();
		setInit();
		setEnabled();
		
		setModal(true);
		setTitle(mProj1.strDBName + Constants.projTo + mProj2.strDBName + Constants.paramsFile); 
	}
	/*********************************************************************/
	private void createMainLabels() {
		int numWidth = 2, decWidth = 2, textWidth = 20;
		
		for (int i=0; i<LABELS.length; i++) {
			if (LABELS[i].equals("Mask1"))       LABELS[i] = "Mask " + mProj1.getdbAbbrev();
			else if (LABELS[i].equals("Mask2"))  LABELS[i] = "Mask " + mProj2.getdbAbbrev();
			else if (LABELS[i].equals("Order1")) LABELS[i] = mProj1.getdbAbbrev() + "->" + mProj2.getdbAbbrev();
			else if (LABELS[i].equals("Order2")) LABELS[i] = mProj2.getdbAbbrev() + "->" + mProj1.getdbAbbrev();
		}
		mainPanel = Jcomp.createPagePanel();
		
		int x=0;	// SAME ORDER AS array
		// alignment
		radMummer = Jcomp.createRadio(LABELS[x++], "Run MUMmer or use existing MUMmer files");
		chkConcat = Jcomp.createCheckBox(LABELS[x++],"Concat 1st project sequences into one file", true);
		String tipm1 = "Gene mask sequence for " + mProj1.strDisplayName + "; there must be a .gff file";
		String tipm2 = "Gene mask sequence for " + mProj2.strDisplayName + "; there must be a .gff file";
		chkMask1  = Jcomp.createCheckBox(LABELS[x++], tipm1, false);
		if (!mProj1.hasGenes()) chkMask1.setEnabled(false);
		chkMask2  = Jcomp.createCheckBox(LABELS[x++], tipm2, false);
		if (!mProj2.hasGenes()) chkMask2.setEnabled(false); 
		
		lblNucMerArgs = Jcomp.createLabel(LABELS[x++], "NUCmer parameters"); txtNucMerArgs = Jcomp.createTextField("",textWidth);
		lblProMerArgs = Jcomp.createLabel(LABELS[x++], "PROmer parameters"); txtProMerArgs = Jcomp.createTextField("",textWidth);
		lblSelfArgs   = Jcomp.createLabel(LABELS[x++], "NUCmer parameters for self-chromosome"); txtSelfArgs = Jcomp.createTextField("",textWidth);
		
		chkNucOnly    = Jcomp.createRadio(LABELS[x++], "Use NUCmer for this alignment");
		chkProOnly    = Jcomp.createRadio(LABELS[x++], "Use PROmer for this alignment");
		chkDef        = Jcomp.createRadio("Defaults", "Use NUCmer for self-synteny, otherwise use PROmer");		// not saved
		
		radBlast  = Jcomp.createRadio(LABELS[x++], "Load one or more Blastp b6 files");
		radAA    = Jcomp.createRadio(LABELS[x++], "The blast file is protein results");
		radNT    = Jcomp.createRadio("Nucleotide", "The blast file is nucleotide results");
		lblpID = Jcomp.createLabel(LABELS[x++],"Percent identity must be >= than this value"); 
		txtpID = Jcomp.createTextField("40",    "Percent identity must be >= than this value", decWidth);
		lblEval = Jcomp.createLabel(LABELS[x++], "E-value must be <= than this value"); 
		txtEval = Jcomp.createTextField("1E-10", "E-value must be <= than this value", decWidth*2);
		
		ButtonGroup xgroup = new ButtonGroup();
		xgroup.add(radMummer); xgroup.add(radBlast); // set selected below
		
		ButtonGroup sgroup = new ButtonGroup();
		sgroup.add(chkNucOnly); sgroup.add(chkProOnly); sgroup.add(chkDef);
		chkDef.setSelected(true);
		
		ButtonGroup bgroup = new ButtonGroup();
		bgroup.add(radAA); bgroup.add(radNT); 
		radAA.setSelected(true);
		
		radBlast.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				setEnabled();
				mp.setHasBlast();
			}
		});
		radMummer.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				setEnabled();
			}
		});
		
	// Cluster
		chkPseudo = Jcomp.createCheckBox(LABELS[x++], "Number pseudo genes (un-annotated hit)", false);
		
		chkAlgo1 = Jcomp.createRadio(LABELS[x++], "Use Algo1");
		lblTopN = Jcomp.createLabel(LABELS[x++]); 
		txtTopN = Jcomp.createTextField("2", "Retain the top N hits of a pile of overlapping hits", numWidth);
		
		chkAlgo2 = Jcomp.createRadio(LABELS[x++], "Use Algo2");
		chkAlgo2.setSelected(!isSelf);
		chkAlgo2.setEnabled(!isSelf);
		String lab = "Scale gene coverage (larger N requires more coverage)";
		lblGene = Jcomp.createLabel(LABELS[x++],lab); 
		txtGene = Jcomp.createTextField("1.0",  lab, decWidth);
		
		lab = "Scale exon coverage (larger N requires more coverage)";
		lblExon = Jcomp.createLabel(LABELS[x++],lab); 
		txtExon = Jcomp.createTextField("1.0",  lab, decWidth);
		
		lab = "Scale G2 and G1 length (Nx" + backend.anchor2.Arg.iPpMinGxLen + ")";
		lblLen = Jcomp.createLabel(LABELS[x++], lab); 
		txtLen = Jcomp.createTextField("1.0",  lab, decWidth);
		
		lab = "Scale G0 length (Nx" + backend.anchor2.Arg.iPpMinG0Len + ")"; 
		lblNoGene = Jcomp.createLabel(LABELS[x++],lab);     
		txtNoGene = Jcomp.createTextField("1.0",lab, decWidth);
		
		chkEEpile = Jcomp.createCheckBox(LABELS[x++],"Keep EE piles", true);  
		chkEIpile = Jcomp.createCheckBox(LABELS[x++],"Keep EI and IE piles", true); 
		chkEnpile = Jcomp.createCheckBox(LABELS[x++],"Keep En and nE piles", true); 
		chkIIpile = Jcomp.createCheckBox(LABELS[x++],"Keep II piles", false); 
		chkInpile = Jcomp.createCheckBox(LABELS[x++],"Keep In and nI piles", false); 

		ButtonGroup agroup = new ButtonGroup();
		agroup.add(chkAlgo1); agroup.add(chkAlgo2);
		
		chkAlgo1.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				setEnabled();
			}
		});
		chkAlgo2.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				setEnabled();
			}
		});
		
	// Collinear set
		lab = "Collinear set algorithm"; 
		lblCset = Jcomp.createLabel(LABELS[x++],lab);    
		cmbCsetNum = Jcomp.createComboBox(coSetNumOpts, "How to number the collinear sets", 0); 
		cmbCsetAlgo = Jcomp.createComboBox(coSetAlgoOpts, "The advanced algorithm tries to resolve overlapped genes collinearity", 0); 
				
	// synteny
		lblMinDots = Jcomp.createLabel(LABELS[x++], "Miniumum hits in a block"); 
		txtMinDots = Jcomp.createTextField("7",     "Miniumum hits in a block", numWidth);
		
		chkStrictBlocks = Jcomp.createCheckBox(LABELS[x++], "Smaller gaps allowed and stricter mixed blocks", true); 
		chkSameOrient  = Jcomp.createCheckBox(LABELS[x++], "Blocks must have hits in same orientation", false); 
		
		lblMerge = Jcomp.createLabel(LABELS[x++], "Merge overlapping and close blocks"); 
		cmbMergeBlocks = Jcomp.createComboBox(mergeOpts, "Each option includes the previous", 0); 
		
		// Order
		String tip1 =  mProj1.strDisplayName + " order against " + mProj2.strDisplayName;
		String tip2 =  mProj2.strDisplayName + " order against " + mProj1.strDisplayName;
		radOrdNone  = Jcomp.createRadio(LABELS[x++], "None");
		radOrdProj1 = Jcomp.createRadio(LABELS[x++], tip1);
		radOrdProj2 = Jcomp.createRadio(LABELS[x++], tip2);
		
		ButtonGroup group = new ButtonGroup();
		group.add(radOrdNone); group.add(radOrdProj1); group.add(radOrdProj2);
		radOrdNone.setSelected(true);
		
		//////////////////////////////////////////////////////////
		// bottom row of bottoms
		
		setActionItems();
		cmbAction = Jcomp.createComboBox(actions, "Action to be performed on 'Selected Pair'", 0); 
		cmbAction.setPrototypeDisplayValue("Load MUM&Synteny "); // sets width
		cmbAction.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				setAction();
			}
		});
		
		btnKeep = Jcomp.createButton("Save", "Save parameters and exit"); 
		btnKeep.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				setSave();
			}
		});
		btnLoadDef = Jcomp.createButton("Defaults", "Set defaults");
		btnLoadDef.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				setDefaults();
			}
		});
		btnDiscard = Jcomp.createButton("Cancel", "Cancel changes and exit");
		btnDiscard.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				setCancel();
			}
		});
		setEnabled();
	}
	/* *********************************************************** */
	private void createMainPanels() {
		// Top Label
		JPanel row = Jcomp.createRowPanel();
		String plab = mProj1.strDisplayName + " (" + mProj1.getdbAbbrev() +  ") x " + 
					  mProj2.strDisplayName + " (" + mProj2.getdbAbbrev() + ")";
		if (isSelf) plab = mProj1.strDisplayName + " (" + mProj1.getdbAbbrev() +  ") x Self"; // CAS589 add
		row.add(Jcomp.createLabel(plab)); 
		
		mainPanel.add(row); mainPanel.add(Box.createVerticalStrut(3)); 
		int indent = 25;
		
	// Alignment
		CollapsiblePanel aCollapse = new CollapsiblePanel("Alignment", "MUMmer and Blast", false);
		
		// mummer
		row = Jcomp.createRowPanel();
		row.add(radMummer); 
		aCollapse.panel.add(row);	
		row.add(Box.createVerticalStrut(5)); 						// vert puts ? icon at end of line
		row.add(Jhtml.createHelpIconSysSm(Jhtml.SYS_HELP_URL, Jhtml.param2Align));
		aCollapse.panel.add(row); aCollapse.panel.add(Box.createVerticalStrut(5));
		
		CollapsiblePanel mumCollapse = new CollapsiblePanel("Mask & MUMmer args", "Masking and PROmer vs NUCmer", true);
			row = Jcomp.createRowPanel(); row.add(Box.createHorizontalStrut(indent));	
			row.add(chkConcat);	row.add(Box.createHorizontalStrut(15));
			row.add(chkMask1);	row.add(Box.createHorizontalStrut(5));	
			if (!isSelf) row.add(chkMask2);	// CAS589 add					
			mumCollapse.panel.add(row);	mumCollapse.panel.add(Box.createVerticalStrut(5));
		
			int colWidth = 100;
			row = Jcomp.createRowPanel(); row.add(Box.createHorizontalStrut(indent+5));
			row.add(lblProMerArgs);
			row.add(Box.createHorizontalStrut(Jcomp.getWidth(colWidth, lblProMerArgs)));
			row.add(txtProMerArgs);
			mumCollapse.panel.add(row);	mumCollapse.panel.add(Box.createVerticalStrut(3));
			
			row = Jcomp.createRowPanel();  row.add(Box.createHorizontalStrut(indent+5));
			row.add(lblNucMerArgs); 
			row.add(Box.createHorizontalStrut(Jcomp.getWidth(colWidth, lblNucMerArgs)));
			row.add(txtNucMerArgs);
			mumCollapse.panel.add(row);	mumCollapse.panel.add(Box.createVerticalStrut(3));
		
			row = Jcomp.createRowPanel();  row.add(Box.createHorizontalStrut(indent+5));
			row.add(lblSelfArgs); 
			row.add(Box.createHorizontalStrut(Jcomp.getWidth(colWidth, lblSelfArgs)));
			row.add(txtSelfArgs);
			mumCollapse.panel.add(row);	mumCollapse.panel.add(Box.createVerticalStrut(5));
	
			row = Jcomp.createRowPanel(); row.add(Box.createHorizontalStrut(indent));
			row.add(chkProOnly); row.add(chkNucOnly); row.add(chkDef);     
			mumCollapse.panel.add(row);  mumCollapse.panel.add(Box.createVerticalStrut(5));
		aCollapse.panel.add(mumCollapse);
		aCollapse.panel.add(Box.createVerticalStrut(5));
		
		// blast
		row = Jcomp.createRowPanel();
		row.add(radBlast);  	
		aCollapse.panel.add(row);  
	
		CollapsiblePanel bltCollapse = new CollapsiblePanel("Blast input and filter", "AA or NT, >%Identity, <E-value", true);
			row = Jcomp.createRowPanel(); row.add(Box.createHorizontalStrut(indent));	
			row.add(Jcomp.createLabel("Input file: ", "Was the input to blast protein or nucleotide files?"));
			row.add(radAA);	row.add(Box.createHorizontalStrut(5));
			row.add(radNT);	row.add(Box.createHorizontalStrut(5));			
			bltCollapse.panel.add(row);	bltCollapse.panel.add(Box.createVerticalStrut(5));
	
			row = Jcomp.createRowPanel(); row.add(Box.createHorizontalStrut(indent+5));
			row.add(lblpID); row.add(Box.createHorizontalStrut(3));
			row.add(txtpID); row.add(Box.createHorizontalStrut(15));
			row.add(lblEval); row.add(Box.createHorizontalStrut(3));
			row.add(txtEval); 
			bltCollapse.panel.add(row);	bltCollapse.panel.add(Box.createVerticalStrut(3));
		aCollapse.panel.add(bltCollapse);
		aCollapse.panel.add(Box.createVerticalStrut(5));
		
		aCollapse.finish();
		aCollapse.panel.add(new JSeparator()); 	
		mainPanel.add(aCollapse);
		
	// Clusters
		CollapsiblePanel cCollapse = new CollapsiblePanel("Cluster MUMmer Hits", "Clustering hits for MUMmer (not Blast hits)", false);

		row = Jcomp.createRowPanel(); 
		row.add(chkPseudo); row.add(Box.createVerticalStrut(5)); // vertical for to put icon at end
		row.add(Jhtml.createHelpIconSysSm(Jhtml.SYS_MUM_URL, Jhtml.mumClust)); // CAS590 changed target
		cCollapse.panel.add(row); cCollapse.panel.add(Box.createVerticalStrut(5));
		
		row = Jcomp.createRowPanel();
		row.add(chkAlgo1);
		cCollapse.panel.add(row);	cCollapse.panel.add(Box.createVerticalStrut(5));
		chkAlgo1.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				boolean b = chkAlgo1.isSelected();
				lblSelfArgs.setEnabled(b);
				txtSelfArgs.setEnabled(b);
			}
		});
		
		row = Jcomp.createRowPanel(); row.add(Box.createHorizontalStrut(indent));
		row.add(lblTopN); row.add(Box.createHorizontalStrut(5));
		row.add(txtTopN);
		cCollapse.panel.add(row);	cCollapse.panel.add(Box.createVerticalStrut(5));
		
		row = Jcomp.createRowPanel();
		row.add(chkAlgo2);
		cCollapse.panel.add(row);	cCollapse.panel.add(Box.createVerticalStrut(5));
		chkAlgo2.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				boolean b = chkAlgo2.isSelected();
				lblSelfArgs.setEnabled(!b);
				txtSelfArgs.setEnabled(!b);
			}
		});
		
		row = Jcomp.createRowPanel(); row.add(Box.createHorizontalStrut(indent));
		JLabel j = Jcomp.createLabel("Scale:", "Increase scale creates less hits, decrease creates more hits");
		row.add(j); row.add(Box.createHorizontalStrut(8)); 
		row.add(lblGene);    row.add(txtGene);   row.add(Box.createHorizontalStrut(5));
		row.add(lblExon);    row.add(txtExon);   row.add(Box.createHorizontalStrut(5)); 
		row.add(lblLen);     row.add(txtLen);    row.add(Box.createHorizontalStrut(5));
		row.add(lblNoGene);  row.add(txtNoGene); row.add(Box.createHorizontalStrut(5));
		if (!isSelf) {cCollapse.panel.add(row);	cCollapse.panel.add(Box.createVerticalStrut(5));}
		
		row = Jcomp.createRowPanel(); row.add(Box.createHorizontalStrut(indent));
		row.add(new JLabel("Keep piles:")); 
		row.add(chkEEpile);   row.add(Box.createHorizontalStrut(5));
		row.add(chkEIpile);   row.add(Box.createHorizontalStrut(5));
		row.add(chkEnpile);   row.add(Box.createHorizontalStrut(5));
		row.add(chkIIpile);   row.add(Box.createHorizontalStrut(5));
		row.add(chkInpile);   row.add(Box.createHorizontalStrut(5));
		if (!isSelf) {cCollapse.panel.add(row);}
		cCollapse.panel.add(new JSeparator()); 
		cCollapse.finish();
		mainPanel.add(cCollapse);
		
	// Collinear Set
		CollapsiblePanel csCollapse = new CollapsiblePanel("Collinear Sets", "Collinear Sets parameters", false);
	
		row = Jcomp.createRowPanel(); 
		row.add(lblCset);   	row.add(Box.createHorizontalStrut(5));
		row.add(cmbCsetNum);   	row.add(Box.createHorizontalStrut(5));
		
		row.add(cmbCsetAlgo);   
		row.add(Box.createVerticalStrut(5)); 						// vert puts ? icon at end of line
		row.add(Jhtml.createHelpIconSysSm(Jhtml.SYS_HELP_URL, Jhtml.paramCoset));
		
		csCollapse.panel.add(row); row.add(Box.createVerticalStrut(3)); // Linux needs a little vertical
		csCollapse.panel.add(new JSeparator()); 
		csCollapse.finish();
		mainPanel.add(csCollapse);
		
	// Synteny
		CollapsiblePanel synCollapse = new CollapsiblePanel("Synteny", "Synteny and ordering parameters", false);
		
		row = Jcomp.createRowPanel();
		row.add(lblMinDots);      row.add(Box.createHorizontalStrut(3));
		row.add(txtMinDots);      row.add(Box.createHorizontalStrut(5));
		row.add(chkStrictBlocks); row.add(Box.createHorizontalStrut(5));
		row.add(chkSameOrient);   row.add(Box.createHorizontalStrut(12));
		row.add(lblMerge); 		  row.add(Box.createHorizontalStrut(2));
		row.add(cmbMergeBlocks);  row.add(Box.createVerticalStrut(5));
		row.add(Jhtml.createHelpIconSysSm(Jhtml.SYS_HELP_URL, Jhtml.param2Syn));
		synCollapse.panel.add(row);	synCollapse.panel.add(Box.createVerticalStrut(6));
		
		// order against
		row = Jcomp.createRowPanel();
		row.add(Jcomp.createLabel("Order against: ", "Order one project against another; the alignment does not need to be redone."));
		row.add(radOrdNone);  row.add(Box.createHorizontalStrut(3));
		row.add(radOrdProj1); row.add(Box.createHorizontalStrut(2));
		row.add(radOrdProj2);
		if (mProj1!=mProj2) {synCollapse.panel.add(row);	synCollapse.panel.add(Box.createVerticalStrut(5));}
		synCollapse.finish();
		mainPanel.add(synCollapse);
		
	// Action & Buttons
		int sp = (Jhtml.isLinux()) ? 7 : 3;
		mainPanel.add(new JSeparator()); 
		JPanel buttonPanel = Jcomp.createPagePanel();
		
		row = Jcomp.createRowPanel();
		int sp2 = (Jhtml.isLinux()) ? 35 : 45;
		row.add(Box.createHorizontalStrut(sp2));
		row.add(cmbAction);
		buttonPanel.add(row); 
		buttonPanel.add(Box.createVerticalStrut(5));
	
		row = Jcomp.createRowPanel();
		row.add(btnKeep);		row.add(Box.createHorizontalStrut(sp));
		row.add(btnLoadDef);	row.add(Box.createHorizontalStrut(sp));
		row.add(btnDiscard);	
		buttonPanel.add(row); 
		buttonPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
		
		row = Jcomp.createRowPanel();
		row.add(Box.createHorizontalGlue());
		row.add(buttonPanel);
		row.add(Box.createHorizontalGlue());
		mainPanel.add(row);
		
		mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		mainPanel.setMaximumSize(mainPanel.getPreferredSize());
		mainPanel.setMinimumSize(mainPanel.getPreferredSize());
		
		add(mainPanel);
		pack();
		setResizable(false);
		setLocationRelativeTo(null); 
	}
	/************************************************************************/
	private String getValue(String symbol) {// ORDER MUST BE SAME AS array
		int x=0;
		
		if (symbol.equals(SYMBOLS[x++]))	return radMummer.isSelected()?"1":"0"; 
		else if (symbol.equals(SYMBOLS[x++])) 	return chkConcat.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++])) 	return chkMask1.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++])) 	return chkMask2.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++]))	return txtNucMerArgs.getText();
		else if (symbol.equals(SYMBOLS[x++]))	return txtProMerArgs.getText();
		else if (symbol.equals(SYMBOLS[x++]))	return txtSelfArgs.getText();
		else if (symbol.equals(SYMBOLS[x++]))	return chkNucOnly.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++]))	return chkProOnly.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++]))	return radBlast.isSelected()?"1":"0"; 
		else if (symbol.equals(SYMBOLS[x++]))	return radAA.isSelected()?"1":"0"; 
		else if (symbol.equals(SYMBOLS[x++]))	return txtpID.getText(); 
		else if (symbol.equals(SYMBOLS[x++]))	return txtEval.getText(); 
		
		else if (symbol.equals(SYMBOLS[x++])) 	return chkPseudo.isSelected()?"1":"0";
		
		else if (symbol.equals(SYMBOLS[x++]))	return chkAlgo1.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++])) 	return txtTopN.getText();
		
		else if (symbol.equals(SYMBOLS[x++]))	return chkAlgo2.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++]))	return txtGene.getText();
		else if (symbol.equals(SYMBOLS[x++]))	return txtExon.getText();
		else if (symbol.equals(SYMBOLS[x++]))	return txtLen.getText();
		else if (symbol.equals(SYMBOLS[x++]))	return txtNoGene.getText();
		
		else if (symbol.equals(SYMBOLS[x++]))	return chkEEpile.isSelected()?"1":"0"; 
		else if (symbol.equals(SYMBOLS[x++]))	return chkEIpile.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++]))	return chkEnpile.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++]))	return chkIIpile.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++]))	return chkInpile.isSelected()?"1":"0";
		
		else if (symbol.equals(SYMBOLS[x++]))	return cmbCsetNum.getSelectedIndex() + ":" + cmbCsetAlgo.getSelectedIndex();
		
		else if (symbol.equals(SYMBOLS[x++])) 	return txtMinDots.getText();
		else if (symbol.equals(SYMBOLS[x++])) 	return chkStrictBlocks.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++])) 	return chkSameOrient.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++]))	return cmbMergeBlocks.getSelectedIndex() + "";
		
		else if (symbol.equals(SYMBOLS[x++])) 	return radOrdNone.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++])) 	return radOrdProj1.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++])) 	return radOrdProj2.isSelected()?"1":"0";
		
		else return "";
	}
	private void setValue(String symbol, String value) {// ORDER MUST BE SAME AS array
		int x=0;
		
		if (symbol.equals(SYMBOLS[x++])) 	radMummer.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	chkConcat.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	chkMask1.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	chkMask2.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	txtNucMerArgs.setText(value);
		else if (symbol.equals(SYMBOLS[x++]))	txtProMerArgs.setText(value);
		else if (symbol.equals(SYMBOLS[x++]))	txtSelfArgs.setText(value);
		else if (symbol.equals(SYMBOLS[x++]))	chkNucOnly.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	chkProOnly.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++])) 	radBlast.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	{
			boolean a = value.equals("1");
			radAA.setSelected(a);  radNT.setSelected(!a);  
		}
		else if (symbol.equals(SYMBOLS[x++]))	txtpID.setText(value); 
		else if (symbol.equals(SYMBOLS[x++]))	txtEval.setText(value); 
		
		else if (symbol.equals(SYMBOLS[x++]))	chkPseudo.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	chkAlgo1.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	txtTopN.setText(value);
		
		else if (symbol.equals(SYMBOLS[x++]))	chkAlgo2.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	txtGene.setText(value);
		else if (symbol.equals(SYMBOLS[x++]))	txtExon.setText(value);
		else if (symbol.equals(SYMBOLS[x++]))	txtLen.setText(value);
		else if (symbol.equals(SYMBOLS[x++]))	txtNoGene.setText(value);
		else if (symbol.equals(SYMBOLS[x++]))	chkEEpile.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	chkEIpile.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	chkEnpile.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	chkIIpile.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	chkInpile.setSelected(value.equals("1"));
		
		else if (symbol.equals(SYMBOLS[x++])) { // collinear
			String [] val = value.split(":");
			if (val.length==2) {
				int num = Utilities.getInt(val[0]);
				cmbCsetNum.setSelectedIndex(num);
				int algo = Utilities.getInt(val[1]);
				cmbCsetAlgo.setSelectedIndex(algo);
			}
		}
		
		else if (symbol.equals(SYMBOLS[x++]))	txtMinDots.setText(value);
		else if (symbol.equals(SYMBOLS[x++]))	chkStrictBlocks.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	chkSameOrient.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	{
			int idx = Utilities.getInt(value);
			if (idx==-1) idx = 0;
			cmbMergeBlocks.setSelectedIndex(idx); // number 0,1,2
		}
		
		else if (symbol.equals(SYMBOLS[x++]))	radOrdNone.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	radOrdProj1.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	radOrdProj2.setSelected(value.equals("1"));
	}
	/************************************************************************/
	private void setParams(HashMap <String, String> valMap) {
		for(int x=0; x<SYMBOLS.length; x++)
			setValue(SYMBOLS[x], valMap.get(SYMBOLS[x]));	
	}
	
	private void setInit() {
		setParams(mp.getFileParams());
		
		if (mp.bSynOnly) 		cmbAction.setSelectedIndex(actSyn);
		else if (mp.bPseudo) 	cmbAction.setSelectedIndex(actPseudo);
		else if (mp.bCoSet) 	cmbAction.setSelectedIndex(actCoSet);
		else 					cmbAction.setSelectedIndex(actCnS);
	}

	private void setEnabled() {
		boolean b = radBlast.isSelected();
		radAA.setEnabled(b); radNT.setEnabled(b);
		txtpID.setEnabled(b); txtEval.setEnabled(b);
		
		b = !b;
		chkConcat.setEnabled(b); chkMask1.setEnabled(b); chkMask2.setEnabled(b);
		lblProMerArgs.setEnabled(b); lblNucMerArgs.setEnabled(b); lblSelfArgs.setEnabled(b);
		chkNucOnly.setEnabled(b); chkProOnly.setEnabled(b); chkDef.setEnabled(b);
		
		b = chkAlgo2.isSelected();
		lblGene.setEnabled(b); txtGene.setEnabled(b);
		lblExon.setEnabled(b); txtExon.setEnabled(b);
		lblLen.setEnabled(b); txtLen.setEnabled(b);
		lblNoGene.setEnabled(b); txtNoGene.setEnabled(b);
		
		chkEEpile.setEnabled(b); chkEIpile.setEnabled(b); chkEnpile.setEnabled(b); 
		chkIIpile.setEnabled(b); chkInpile.setEnabled(b);
		
		setActionItems();
		cmbAction.setModel(new DefaultComboBoxModel<>(actions));
	}
	private void setActionItems() { 
		String align;
		if (radBlast.isSelected())   align = actAlt0[2];  // Load Blast
		else if (mp.hasMUMerFiles()) align = actAlt0[1];  // Load MUMmer
		else                         align = actAlt0[0];  // Run MUMmer
		
		if (!isAlignDone || !mp.isPairInDB()) { 
			actions = new String [1];	
			actions[0] = align;
		}
		else {
			actOptions[0] = align; 
			actions = actOptions;
		}
	}
	private void setAction() {
		int idx = cmbAction.getSelectedIndex();
		if (idx==actCnS)      {
			mp.bPseudo=false; mp.bSynOnly=false; mp.bCoSet=false;
		}
		else if (idx==actSyn) { 
			mp.bPseudo=false; mp.bSynOnly=true; mp.bCoSet=false;
			setSaveSynOnly();
		}
		else if (idx==actPseudo) {
			mp.bPseudo=true;  mp.bSynOnly=false; mp.bCoSet=false;
			setSavePseudo();
		}
		else if (idx==actCoSet) {
			mp.bPseudo=false;  mp.bSynOnly=false; mp.bCoSet=true;
			setSaveCoSet();
		}
	}
	
	/*****************************************************/
	private void setDefaults() {
		setParams(mp.getDefaults());
		
		chkDef.setSelected(true); // Defaults should reset this, but not initial load from file
		
		mp.bPseudo = mp.bSynOnly = mp.bCoSet = false;
		cmbAction.setSelectedIndex(actCnS);
		
		setEnabled();
	}
	private void setCancel() { 
		mp.bPseudo  = bSavPseudo;
		mp.bSynOnly = bSavSynOnly;
		mp.bCoSet = bSavCoSet;
		
		manFrame.updateEnableButtons();

		setVisible(false);
	}
	private void setSave() {
		// Draft and Draft ordered do not work well with bOrient
		if (chkSameOrient.isSelected() || chkStrictBlocks.isSelected()) {
			String [] options = {"Cancel", "Continue"};
			
			String x = Constants.orderDelim;
			if (!radOrdNone.isSelected()) { 
				String msg ="Orient or Strict is selected with 'Order against'" +
							"\n   These options often do not work well with draft sequence\n";
				int ret = JOptionPane.showOptionDialog(this, msg + "\nContinue?", "Pair parameters", // need 'this' to go on top of panel
						  JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
				if (ret == 0) return ;
			}
			else if ((mProj1.getDBName().contains(x) || mProj2.getDBName().contains(x))) {
				String msg = (mProj1.getDBName().contains(x)) ? mProj1.getDBName() : mProj2.getDBName();
				msg += " appears to be generated from draft sequence 'Order against'" + 
						"\n   Orient and Strict do not work well with draft sequence\n";
				int ret = JOptionPane.showOptionDialog(this, msg + "\nContinue?", "Pair parameters", 
						  JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
				if (ret == 0) return ;
			}
		}
		
		if (!checkInt("Min Hits", txtMinDots.getText(), 1)) return; 
		if (!checkInt("Top N piles", txtTopN.getText(), 1)) return;		
		if (!checkDouble("G0_Len", txtNoGene.getText(), 0)) return;		 
		if (!checkDouble("Gene", txtGene.getText(), 0)) return;	
		if (!checkDouble("Exon", txtExon.getText(), 0)) return;	
		if (!checkDouble("Length", txtLen.getText(), 0)) return;	
		
		if (radBlast.isSelected()) { // CAS589
			Constants.checkCreateDataDir();
			
			String dir = Constants.getNameResultsDir(mProj1.getDBName(), mProj2.getDBName());
			FileDir.checkCreateDir(dir, true);
			
			dir = dir + Constants.blastDir;
			FileDir.checkCreateDir(dir, true);
			
			ArrayList <String> files = FileDir.getFilesFromDir(dir);
			if (files == null || files.size()==0) Globals.prt("No files in " + dir);
		}
		HashMap <String, String> fileMap = mp.getFileParams();
		
		for (String key : SYMBOLS) fileMap.put(key, getValue(key));
		
		mp.saveParamsToFile(fileMap); // saves to DB when run
		
		bSavPseudo = mp.bPseudo; bSavSynOnly = mp.bSynOnly; bSavCoSet = mp.bCoSet;
		manFrame.updateEnableButtons(); 		// update Selected pair label
		
		if (mp.bSynOnly) 		setSaveSynOnly();	
		else if (mp.bPseudo) 	setSavePseudo();
		else if (mp.bCoSet) 	setSaveCoSet();
		
		setVisible(false);
	}
	private void setSavePseudo() {
		setParams(mp.getDbParams());
		chkPseudo.setSelected(true);
	}
	private void setSaveCoSet() {
		int num = cmbCsetNum.getSelectedIndex();
		int alg = cmbCsetAlgo.getSelectedIndex();
		
		setParams(mp.getDbParams());
		
		cmbCsetNum.setSelectedIndex(num);
		cmbCsetAlgo.setSelectedIndex(alg);
	}
	private void setSaveSynOnly() {
		boolean isOrient = chkSameOrient.isSelected(); 
		int isMerge = cmbMergeBlocks.getSelectedIndex(); 
		boolean isPrune = chkStrictBlocks.isSelected(); 
		String dot = txtMinDots.getText();
		int order=0;								
		if (radOrdProj1.isSelected()) order=1;
		else if (radOrdProj2.isSelected()) order=2;
		
		setParams(mp.getDbParams()); // The wrong values will be in Summary if this is not done
		
		chkSameOrient.setSelected(isOrient);
		cmbMergeBlocks.setSelectedIndex(isMerge);
		chkStrictBlocks.setSelected(isPrune);
		txtMinDots.setText(dot);
		if (order==0) radOrdNone.setSelected(true);
		else if (order==1) radOrdProj1.setSelected(true);
		else if (order==2) radOrdProj2.setSelected(true);
	}
	/*********************************************/
	private boolean checkInt(String label, String x, int min) {
		if (x.trim().equals("")) x="0"; 
		int i = Utilities.getInt(x.trim());
		if (min==0) {
			if (i<0) {
				Popup.showErrorMessage(label + "=" + x + "\nIs an incorrect integer (" + x + "). Must be >=0.");
				return false;
			}
			return true;
		}
		if (i==-1 || i==0) {
			Popup.showErrorMessage(label + "=" + x + "\nIs an incorrect integer. Must be >0.");
			return false;
		}
		// warnings
		if (label.startsWith("Top") && i>4) {
			Popup.showContinue("Top N piles", "Top N > 4 may produce worse results and take significantly longer.");
		}
	
		return true;
	}
	private boolean checkDouble(String label, String x, double min) {
		if (x.trim().equals("")) x="0.0"; 
		double d = Utilities.getDouble(x.trim());
		if (min==0) {
			if (d<0) {
				Popup.showErrorMessage(label + "=" + x + "\nIs an incorrect decimal (" + x + "). Must be >=0.");
				return false;
			}
			return true;
		}
		if (d==-1 || d==0) {
			Popup.showErrorMessage(label + "=" + x + "\nIs an incorrect decimal. Must be >0.");
			return false;
		}
		return true;
	}
	
	/************************************************************************/
	private JPanel mainPanel = null;

	private JRadioButton radMummer = null;
	private JCheckBox chkConcat = null, chkMask1 = null, chkMask2 = null;
	private JLabel lblNucMerArgs = null, lblProMerArgs = null, lblSelfArgs = null;
	private JTextField txtNucMerArgs = null, txtSelfArgs = null, txtProMerArgs = null;
	private JRadioButton chkNucOnly = null, chkProOnly = null, chkDef = null;
	
	private JRadioButton radBlast = null;
	private JRadioButton radAA=null, radNT=null;
	private JLabel lblpID=null, lblEval=null;
	private JTextField txtpID=null, txtEval=null;
	
	private JRadioButton chkAlgo1,  chkAlgo2;
	
	private JLabel lblTopN = null;
	private JTextField txtTopN = null;
	
	private JLabel lblNoGene, lblGene, lblExon, lblLen;
	private JTextField txtNoGene, txtGene, txtExon, txtLen;
	private JCheckBox chkEEpile = null, chkEIpile = null, chkEnpile = null, chkIIpile = null, chkInpile = null;
	
	private JCheckBox chkPseudo = null;
	
	private JLabel lblMinDots = null;
	private JTextField txtMinDots = null;
	private JCheckBox chkSameOrient = null, chkStrictBlocks = null;
	private JLabel lblMerge = null;
	private JComboBox <String> cmbMergeBlocks = null;
	private JRadioButton radOrdNone=null, radOrdProj1=null, radOrdProj2=null;
	
	private JLabel lblCset = null;
	private JComboBox <String> cmbCsetNum = null, cmbCsetAlgo = null;
	
	private JComboBox <String> cmbAction = null;	
	private JButton btnKeep = null, btnDiscard = null, btnLoadDef = null;
	
    private boolean bSavPseudo=false, bSavSynOnly=false, bSavCoSet=false;
    /*******************************************************************
     * XXX for different sections of Pair Parameters; Copied from frame.CollapsiblePanel CAS580 add 
     */
    public class CollapsiblePanel extends JPanel {
    	private static final long serialVersionUID = 1L;
    	private JPanel panel; // for user-added components
    	private boolean collapsed;
    	private JButton expandButton;
    	private Dimension collapsedSize;
    	
    	private static final ImageIcon expandIcon = createImageIcon("/images/plus.gif");
    	private static final ImageIcon collapseIcon = createImageIcon("/images/minus.gif");
    	private static final ImageIcon expandIconSm = createImageIcon("/images/plusSm.gif");
    	private static final ImageIcon collapseIconSm = createImageIcon("/images/minusSm.gif");
    	private boolean bSmall;
    	
    	protected CollapsiblePanel(String strTitle, String tip, boolean bSmall) {
    		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
    		setBackground(Jcomp.white);
    		this.bSmall = bSmall;
    	
    		expandButton = new JButton(strTitle);
    		expandButton.setToolTipText(tip);
    		expandButton.setBackground(Jcomp.white);
    		expandButton.setBorderPainted(false);
    		expandButton.setFocusPainted(false);
    		expandButton.setContentAreaFilled(false);
    		expandButton.setMargin(new Insets(0, 0, 0, 0));
    		expandButton.setFont(new Font(getFont().getName(), Font.PLAIN, 12));

    		expandButton.addActionListener(
    			new ActionListener( ) {
    				public void actionPerformed(ActionEvent e) {
    					if (collapsed) expand();
    					else           collapse();
    					
    					revalidate();
    		            repaint();
    		            pack(); 
    		}});
    		
    		panel = Jcomp.createPagePanel();
    				
    		collapse();
    		
    		super.add(expandButton);
  
    		super.add(panel);
    		
    		collapsedSize = new Dimension(450, getPreferredSize().height);
    	}
    	
    	private void finish() {
    		setMinimumSize(getPreferredSize());
    		setMaximumSize(getPreferredSize());
    	}
    	private void collapse() {
    		collapsed = true;
    		if (bSmall) expandButton.setIcon(expandIconSm);
    		else        expandButton.setIcon(expandIcon);
    		
    		panel.setVisible(false);
    		setMaximumSize(collapsedSize); // prevent vertical stretching problem
    	}
    	
    	protected void expand() {
    		collapsed = false;
    		if (bSmall) expandButton.setIcon(collapseIconSm);
    		else        expandButton.setIcon(collapseIcon);
    		
    		panel.setVisible(true);
    		setMaximumSize(collapsedSize);
    	}
    	
    	protected void doClick() {
    		expandButton.doClick();
    	}
    	private static ImageIcon createImageIcon(String path) {
    	    java.net.URL imgURL = CollapsiblePanel.class.getResource(path);
    	    if (imgURL == null) {System.err.println("Couldn't find icon: "+path);return null;}
    	    
    	    return new ImageIcon(imgURL);
    	}
    }
}
