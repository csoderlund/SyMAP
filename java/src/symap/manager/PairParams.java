package symap.manager;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSeparator;
import javax.swing.JTextField;

import backend.Constants;
import util.Jcomp;
import util.Jhtml;
import util.Utilities;

/**********************************************
 * The parameter window for Alignment&Synteny
 * CAS565 Pseudo, CAS567 Orient, Concat (from Main); CAS568 Order, Mask(from ProjParam); 
 * CAS571 Clust&Syn, CAS572 strict, extended merge
 */

public class PairParams extends JDialog {
	private static final long serialVersionUID = 1L;
	protected static String [] mergeOpts = {"None", "Overlap", "Close"};
	
	// ManagerFrame.alignSelectedPair and updateEnableButtons use similar text
	private String opt0Alt =       "Align&Synteny"; // Manager button Selected Pair
	private String[] actOptions = {"Clust&Synteny", // Manager button Selected Redo
								   "Synteny Only","Number Pseudo"}; // Manager button sets from mp.bSynOnly...
	  
	// Order MUST be same in createMainPanel and setValue and getValue; defaults are in Mpair
	// These are NOT in the order displayed, but do not change without changing access everywhere else
	private String [] LABELS = { // Order must be same as createMainPanel
		"Min hits", "Strict", "Orient", "Merge:", 
		"None", "Order1", "Order2", 			 
		"Concat",  "Mask1", "Mask2", 		
		"NUCmer args", "PROmer args", "Self args", "NUCmer only","PROmer only", // mummer:
		"Number pseudo", 											
		"Algorithm 1 (modified original)", "Algorithm 2 (exon-intron)",  
		"Top N piles", 
		"Gene", "Exon", "Len","G0_Len", 
		"EE", "EI", "En", "II", "In"
	};
	
	// if change, change in Mpair too; defaults are in Mpair; written to DB and file
	// order the same as above; constant strings are repeated multiple times in MPair
	private static final String [] SYMBOLS = { // Mpairs.paramKey
		"mindots",  "strict_blocks", "same_orient", "merge_blocks",
		"order_none", "order_proj1", "order_proj2",			// none, proj1, proj2
		"concat","mask1", "mask2", 
		"nucmer_args", "promer_args", "self_args", "nucmer_only","promer_only",
		"number_pseudo", "algo1", "algo2",
		"topn",					
		"gene_scale", "exon_scale", "len_scale","g0_scale",
		"EE_pile", "EI_pile", "En_pile", "II_pile", "In_pile"
	};
	
	private Mpair mp = null;
	private Mproject mProj1 = null, mProj2 = null;
	private ManagerFrame manFrame=null;
	private boolean isAlignDone;	// Could be A or check; mp.hasSynteny() determines which
	
	protected PairParams(Mpair mp, ManagerFrame manFrame, boolean alignDone) {
		this.mp = mp;
		this.mProj1 = mp.mProj1;
		this.mProj2 = mp.mProj2;
		this.manFrame = manFrame;
		this.isAlignDone = alignDone;
		
		createMainPanel();
		setInit();
		
		setModal(true);
		setTitle(mProj1.strDBName + Constants.projTo + mProj2.strDBName + Constants.paramsFile); 
	}
	/*********************************************************************/
	private void createMainPanel() {
		int numWidth = 2, decWidth = 2, textWidth = 15;
		
		for (int i=0; i<LABELS.length; i++) {
			if (LABELS[i].equals("Mask1"))       LABELS[i] = "Mask " + mProj1.getdbAbbrev();
			else if (LABELS[i].equals("Mask2"))  LABELS[i] = "Mask " + mProj2.getdbAbbrev();
			else if (LABELS[i].equals("Order1")) LABELS[i] = mProj1.getdbAbbrev() + "->" + mProj2.getdbAbbrev();
			else if (LABELS[i].equals("Order2")) LABELS[i] = mProj2.getdbAbbrev() + "->" + mProj1.getdbAbbrev();
		}
		mainPanel = Jcomp.createPagePanel();
		
		int x=0;	// SAME ORDER AS array
		// synteny
		lblMinDots = Jcomp.createLabel(LABELS[x++], "Miniumum hits in a block"); 
		txtMinDots = Jcomp.createTextField("7",     "Miniumum hits in a block", numWidth);
		
		chkStrictBlocks = Jcomp.createCheckBox(LABELS[x++], "Smaller gaps allowed and stricter mixed blocks", true); 
		chkSameOrient  = Jcomp.createCheckBox(LABELS[x++], "Blocks must have hits in same orientation", false); 
		
	
		lblMerge = Jcomp.createLabel(LABELS[x++], "Merge overlapping and close blocks"); 
		cmbMergeBlocks = Jcomp.createComboBox(mergeOpts, "Each option includes the previous", 0); 
		
		String tip1 =  mProj1.strDisplayName + " order against " + mProj2.strDisplayName;
		String tip2 =  mProj2.strDisplayName + " order against " + mProj1.strDisplayName;
		radOrdNone  = Jcomp.createRadio(LABELS[x++], "None");
		radOrdProj1 = Jcomp.createRadio(LABELS[x++], tip1);
		radOrdProj2 = Jcomp.createRadio(LABELS[x++], tip2);
		
		ButtonGroup group = new ButtonGroup();
		group.add(radOrdNone); group.add(radOrdProj1); group.add(radOrdProj2);
		radOrdNone.setSelected(true);
		
		// prepare
		chkConcat = Jcomp.createCheckBox(LABELS[x++],"Concat 1st project sequences into one file", false);
		
		String tipm1 = "Gene mask sequence for " + mProj1.strDisplayName + "; there must be a .gff file";
		String tipm2 = "Gene mask sequence for " + mProj2.strDisplayName + "; there must be a .gff file";
		chkMask1  = Jcomp.createCheckBox(LABELS[x++], tipm1, false);
		if (!mProj1.hasGenes()) chkMask1.setEnabled(false);
		chkMask2  = Jcomp.createCheckBox(LABELS[x++], tipm2, false);
		if (!mProj2.hasGenes()) chkMask2.setEnabled(false); 
		
		// mummer
		lblNucMerArgs = Jcomp.createLabel(LABELS[x++]); txtNucMerArgs = Jcomp.createTextField("",textWidth);
		lblProMerArgs = Jcomp.createLabel(LABELS[x++]); txtProMerArgs = Jcomp.createTextField("",textWidth);
		lblSelfArgs   = Jcomp.createLabel(LABELS[x++]); txtSelfArgs = Jcomp.createTextField("",textWidth);
		chkNucOnly    = Jcomp.createRadio(LABELS[x++]);
		chkProOnly    = Jcomp.createRadio(LABELS[x++]);
		chkDef        = Jcomp.createRadio("Defaults");		// not saved
		ButtonGroup sgroup = new ButtonGroup();
		sgroup.add(chkNucOnly); sgroup.add(chkProOnly); sgroup.add(chkDef);
		chkDef.setSelected(true);
		
		// Cluster
		chkPseudo = Jcomp.createCheckBox(LABELS[x++], "Number pseudo genes (un-annotated hit)", false);
		
		chkAlgo1 = Jcomp.createRadio(LABELS[x++]);
		chkAlgo2 = Jcomp.createRadio(LABELS[x++]);
		ButtonGroup agroup = new ButtonGroup();
		agroup.add(chkAlgo1); agroup.add(chkAlgo2);
		chkAlgo2.setSelected(true);
		
		lblTopN = Jcomp.createLabel(LABELS[x++]); txtTopN = Jcomp.createTextField("2", "Retain the top N hits of a pile of overlapping hits", numWidth);
		
		String lab; 
		lab = "Scale gene coverage (larger N requires more coverage)";
		lblGene = Jcomp.createLabel(LABELS[x++],lab); 
		txtGene = Jcomp.createTextField("1.0",  lab, decWidth);
		
		lab = "Scale exon coverage (larger N requires more coverage)";
		lblExon = Jcomp.createLabel(LABELS[x++],lab); 
		txtExon = Jcomp.createTextField("1.0",  lab, decWidth);
		
		lab = "Scale G2 and G1 length (Nx" + backend.anchor2.Arg.iPpMinGxLen + ")";
		lblLen = Jcomp.createLabel(LABELS[x++], lab); txtLen = 
		Jcomp.createTextField("1.0",  lab, decWidth);
		
		lab = "Scale G0 length (Nx" + backend.anchor2.Arg.iPpMinG0Len + ")"; 
		lblNoGene = Jcomp.createLabel(LABELS[x++],lab);     
		txtNoGene = Jcomp.createTextField("1.0",lab, decWidth);
		
		chkEEpile = Jcomp.createCheckBox(LABELS[x++],"Keep EE piles", true);  
		chkEIpile = Jcomp.createCheckBox(LABELS[x++],"Keep EI and IE piles", true); 
		chkEnpile = Jcomp.createCheckBox(LABELS[x++],"Keep En and nE piles", true); 
		chkIIpile = Jcomp.createCheckBox(LABELS[x++],"Keep II piles", false); 
		chkInpile = Jcomp.createCheckBox(LABELS[x++],"Keep In and nI piles", false); 
		
		// bottom row
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
		btnActPop = Jcomp.createButton("tmp label", "Click button for menu of options; Must be 'Save' to take effect.");
		createPopup();
		
		/*-------- Create Layout ------------------*/
		//////////////////////////////////////////////////////////////////////////
		JPanel row = Jcomp.createRowPanel();
		String plab = mProj1.strDisplayName + " (" + mProj1.getdbAbbrev() +  ") & " + 
					  mProj2.strDisplayName + " (" + mProj2.getdbAbbrev() + ")";
		row.add(Jcomp.createLabel(plab)); 
		mainPanel.add(row); mainPanel.add(Box.createVerticalStrut(5)); 
	
		// Alignment
		row = Jcomp.createRowPanel();
		row.add(Jcomp.createHtmlLabel("Alignment"));  row.add(Box.createVerticalStrut(5)); // vert puts icon at end of line
		row.add(Jhtml.createHelpIconSysSm(Jhtml.SYS_HELP_URL, Jhtml.param2Align));
		mainPanel.add(row); mainPanel.add(Box.createVerticalStrut(5));
		
		row = Jcomp.createRowPanel();
		row.add(chkConcat);								
		row.add(Box.createHorizontalStrut(15));
														
		row.add(chkMask1);	row.add(Box.createHorizontalStrut(5));	
		row.add(chkMask2);						
		mainPanel.add(row);	mainPanel.add(Box.createVerticalStrut(5));
		
		int colWidth = 100;
		row = Jcomp.createRowPanel();
		row.add(lblProMerArgs);
		if (lblProMerArgs.getPreferredSize().width < colWidth) row.add(Box.createHorizontalStrut(colWidth - lblProMerArgs.getPreferredSize().width));
		row.add(txtProMerArgs);
		mainPanel.add(row);	mainPanel.add(Box.createVerticalStrut(5));
		
		row = Jcomp.createRowPanel();
		row.add(lblNucMerArgs);
		if (lblNucMerArgs.getPreferredSize().width < colWidth) row.add(Box.createHorizontalStrut(colWidth - lblNucMerArgs.getPreferredSize().width));
		row.add(txtNucMerArgs);
		mainPanel.add(row);	mainPanel.add(Box.createVerticalStrut(5));
	
		row = Jcomp.createRowPanel();
		row.add(lblSelfArgs);
		if (lblSelfArgs.getPreferredSize().width < colWidth) row.add(Box.createHorizontalStrut(colWidth - lblSelfArgs.getPreferredSize().width));
		row.add(txtSelfArgs);
		mainPanel.add(row);	mainPanel.add(Box.createVerticalStrut(5));

		row = Jcomp.createRowPanel();
		row.add(chkProOnly); mainPanel.add(row);mainPanel.add(Box.createHorizontalStrut(20));
		row.add(chkNucOnly); mainPanel.add(row);mainPanel.add(Box.createHorizontalStrut(20));
		row.add(chkDef);     mainPanel.add(row);mainPanel.add(Box.createVerticalStrut(5));
		
		// Clusters
		int indent=25;
		mainPanel.add(new JSeparator()); mainPanel.add(Box.createVerticalStrut(5)); 
		
		row = Jcomp.createRowPanel();
		row.add(Jcomp.createHtmlLabel("Cluster Hits"));  
		row.add(Jhtml.createHelpIconSysSm(Jhtml.SYS_HELP_URL, Jhtml.param2Clust));
		mainPanel.add(row); mainPanel.add(Box.createVerticalStrut(5));
		
		row = Jcomp.createRowPanel(); 
		row.add(chkPseudo);
		mainPanel.add(row); mainPanel.add(Box.createVerticalStrut(5));
		
		row = Jcomp.createRowPanel();
		row.add(chkAlgo1);
		mainPanel.add(row);	mainPanel.add(Box.createVerticalStrut(5));
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
		mainPanel.add(row);	mainPanel.add(Box.createVerticalStrut(5));
		
		row = Jcomp.createRowPanel();
		row.add(chkAlgo2);
		mainPanel.add(row);	mainPanel.add(Box.createVerticalStrut(5));
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
		mainPanel.add(row);	mainPanel.add(Box.createVerticalStrut(5));
		
		row = Jcomp.createRowPanel(); row.add(Box.createHorizontalStrut(indent));
		row.add(new JLabel("Keep piles:")); 
		row.add(chkEEpile);   row.add(Box.createHorizontalStrut(5));
		row.add(chkEIpile);   row.add(Box.createHorizontalStrut(5));
		row.add(chkEnpile);   row.add(Box.createHorizontalStrut(5));
		row.add(chkIIpile);   row.add(Box.createHorizontalStrut(5));
		row.add(chkInpile);   row.add(Box.createHorizontalStrut(5));
		mainPanel.add(row);	mainPanel.add(Box.createVerticalStrut(5));
		
	// Synteny
		mainPanel.add(new JSeparator()); mainPanel.add(Box.createVerticalStrut(5)); 
		row = Jcomp.createRowPanel();
		row.add(Jcomp.createHtmlLabel("Synteny Blocks"));  row.add(Box.createVerticalStrut(5));
		row.add(Jhtml.createHelpIconSysSm(Jhtml.SYS_HELP_URL, Jhtml.param2Syn));
		mainPanel.add(row); mainPanel.add(Box.createVerticalStrut(5));
		
		row = Jcomp.createRowPanel();
		row.add(lblMinDots);      row.add(Box.createHorizontalStrut(3));
		row.add(txtMinDots);      row.add(Box.createHorizontalStrut(5));
		row.add(chkStrictBlocks); row.add(Box.createHorizontalStrut(5));
		row.add(chkSameOrient);   row.add(Box.createHorizontalStrut(12));
		row.add(lblMerge); 		  row.add(Box.createHorizontalStrut(2));
		row.add(cmbMergeBlocks);
	
		mainPanel.add(row);	mainPanel.add(Box.createVerticalStrut(3));
		
		// order against
		mainPanel.add(new JSeparator()); mainPanel.add(Box.createVerticalStrut(3)); 
		row = Jcomp.createRowPanel();
		row.add(Jcomp.createLabel("Order against: ", "Order one project against another; the alignment does not need to be redone."));
		row.add(radOrdNone);  row.add(Box.createHorizontalStrut(3));
		row.add(radOrdProj1); row.add(Box.createHorizontalStrut(2));
		row.add(radOrdProj2);
		if (mProj1!=mProj2) {mainPanel.add(row);	mainPanel.add(Box.createVerticalStrut(5));}// CAS572
		
		// Buttons
		mainPanel.add(new JSeparator()); mainPanel.add(Box.createVerticalStrut(5)); 
		
		row = Jcomp.createRowPanel();
		JPanel buttonPanel = Jcomp.createRowPanel();
		buttonPanel.add(btnActPop);		buttonPanel.add(Box.createHorizontalStrut(14));
		buttonPanel.add(btnKeep);		buttonPanel.add(Box.createHorizontalStrut(3));
		buttonPanel.add(btnLoadDef);	buttonPanel.add(Box.createHorizontalStrut(3));
		buttonPanel.add(btnDiscard);		
		
		buttonPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
		
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
	/******************************************************/
	private void createPopup() {
		if (!isAlignDone) actOptions[0] = opt0Alt; 
		act0Radio	= new JRadioButtonMenuItem(actOptions[0]);
		act1Radio	= new JRadioButtonMenuItem(actOptions[1]);
		act2Radio	= new JRadioButtonMenuItem(actOptions[2]);
		    
		PopupListener listener = new PopupListener();
		act0Radio.addActionListener(listener);
		act1Radio.addActionListener(listener);
		act2Radio.addActionListener(listener);
		
    	JPopupMenu popupMenu = new JPopupMenu();
		popupMenu.setBackground(symap.Globals.white);
		
		JMenuItem popupTitle = new JMenuItem();
		popupTitle.setText("Select Task");
		popupTitle.setEnabled(false);
		popupMenu.add(popupTitle);
		popupMenu.addSeparator();
		
		popupMenu.add(act0Radio);
		popupMenu.add(act1Radio);
		popupMenu.add(act2Radio);
		
		if (!mp.hasSynteny()) {
			act1Radio.setEnabled(false);
			act2Radio.setEnabled(false);
		}
		
		ButtonGroup grp = new ButtonGroup();
		grp.add(act0Radio); grp.add(act1Radio); grp.add(act2Radio); 
		
		btnActPop.setComponentPopupMenu(popupMenu); // zoomPopupButton create in main
		btnActPop.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                popupMenu.show(e.getComponent(), e.getX(), e.getY());
            }
        });
		Dimension d = new Dimension(120, 30); // w,d
		btnActPop.setPreferredSize(d); btnActPop.setMaximumSize(d); btnActPop.setMinimumSize(d);
		
		if (mp.bSynOnly) setPopup(act1Radio);
		else if (mp.bPseudo) setPopup(act2Radio);
		else setPopup(act0Radio);
	}
	/************************************************************************/
	private String getValue(String symbol) {// ORDER MUST BE SAME AS array
		int x=0;
		
		if (symbol.equals(SYMBOLS[x++])) 		return txtMinDots.getText();
		else if (symbol.equals(SYMBOLS[x++])) 	return chkStrictBlocks.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++])) 	return chkSameOrient.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++]))	return cmbMergeBlocks.getSelectedIndex() + "";
		
		else if (symbol.equals(SYMBOLS[x++])) 	return radOrdNone.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++])) 	return radOrdProj1.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++])) 	return radOrdProj2.isSelected()?"1":"0";
		
		else if (symbol.equals(SYMBOLS[x++])) 	return chkConcat.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++])) 	return chkMask1.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++])) 	return chkMask2.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++]))	return txtNucMerArgs.getText();
		else if (symbol.equals(SYMBOLS[x++]))	return txtProMerArgs.getText();
		else if (symbol.equals(SYMBOLS[x++]))	return txtSelfArgs.getText();
		else if (symbol.equals(SYMBOLS[x++]))	return chkNucOnly.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++]))	return chkProOnly.isSelected()?"1":"0";
		
		else if (symbol.equals(SYMBOLS[x++])) 	return chkPseudo.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++]))	return chkAlgo1.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++]))	return chkAlgo2.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++])) 	return txtTopN.getText();
		
		else if (symbol.equals(SYMBOLS[x++]))	return txtGene.getText();
		else if (symbol.equals(SYMBOLS[x++]))	return txtExon.getText();
		else if (symbol.equals(SYMBOLS[x++]))	return txtLen.getText();
		else if (symbol.equals(SYMBOLS[x++]))	return txtNoGene.getText();
		
		else if (symbol.equals(SYMBOLS[x++]))	return chkEEpile.isSelected()?"1":"0"; 
		else if (symbol.equals(SYMBOLS[x++]))	return chkEIpile.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++]))	return chkEnpile.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++]))	return chkIIpile.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++]))	return chkInpile.isSelected()?"1":"0";
		else return "";
	}
	private void setValue(String symbol, String value) {// ORDER MUST BE SAME AS array
		int x=0;
		
		if (symbol.equals(SYMBOLS[x++]))		txtMinDots.setText(value);
		else if (symbol.equals(SYMBOLS[x++]))	chkStrictBlocks.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	chkSameOrient.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	{
			int idx = Utilities.getInt(value);
			if (idx==-1) idx = 0;
			cmbMergeBlocks.setSelectedIndex(idx); // number 0,1,2; Merge CAS572
		}
		else if (symbol.equals(SYMBOLS[x++]))	radOrdNone.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	radOrdProj1.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	radOrdProj2.setSelected(value.equals("1"));
		
		else if (symbol.equals(SYMBOLS[x++]))	chkConcat.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	chkMask1.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	chkMask2.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	txtNucMerArgs.setText(value);
		else if (symbol.equals(SYMBOLS[x++]))	txtProMerArgs.setText(value);
		else if (symbol.equals(SYMBOLS[x++]))	txtSelfArgs.setText(value);
		else if (symbol.equals(SYMBOLS[x++]))	chkNucOnly.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	chkProOnly.setSelected(value.equals("1"));
		
		else if (symbol.equals(SYMBOLS[x++]))	chkPseudo.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	chkAlgo1.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	chkAlgo2.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	txtTopN.setText(value);
		
		else if (symbol.equals(SYMBOLS[x++]))	txtGene.setText(value);
		else if (symbol.equals(SYMBOLS[x++]))	txtExon.setText(value);
		else if (symbol.equals(SYMBOLS[x++]))	txtLen.setText(value);
		else if (symbol.equals(SYMBOLS[x++]))	txtNoGene.setText(value);
		
		else if (symbol.equals(SYMBOLS[x++]))	chkEEpile.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	chkEIpile.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	chkEnpile.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	chkIIpile.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	chkInpile.setSelected(value.equals("1"));
	}
	/************************************************************************/
	
	private void setInit() {
		setParams(mp.getFileParams());
		
		if (mp.bSynOnly) setPopup(act1Radio);
		else if (mp.bPseudo) setPopup(act2Radio);
		else setPopup(act0Radio);
	}
	private void setParams(HashMap <String, String> valMap) {
		for(int x=0; x<SYMBOLS.length; x++)
			setValue(SYMBOLS[x], valMap.get(SYMBOLS[x]));	
	}
	private void setPopup(Object src) {
		int idx=0;
		if (src==act0Radio)      {
			idx=0; mp.bPseudo=false; mp.bSynOnly=false;
		}
		else if (src==act1Radio) { 
			idx=1; mp.bPseudo=false; mp.bSynOnly=true;
			setSaveSynOnly();
		}
		else if (src==act2Radio) {
			idx=2; mp.bPseudo=true;  mp.bSynOnly=false; 
			setSavePseudo();
		}
		else return;
		
		btnActPop.setText(actOptions[idx]);
	}
	private class PopupListener implements ActionListener {
		private PopupListener() { }
		public void actionPerformed(ActionEvent event) { 
			Object src = event.getSource();
			setPopup(src);
		}
	 }
	/*****************************************************/
	private void setDefaults() {
		setParams(mp.getDefaults());
		
		chkDef.setSelected(true); // Defaults should reset this, but not initial load from file
		
		mp.bPseudo = mp.bSynOnly = false;
		setPopup(act0Radio);
	}
	private void setCancel() { 
		mp.bPseudo  = bSavPseudo;
		mp.bSynOnly = bSavSynOnly;
		
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
		
		HashMap <String, String> fileMap = mp.getFileParams();
		
		for (String key : SYMBOLS) fileMap.put(key, getValue(key));
		
		mp.saveParamsToFile(fileMap); // saves to DB when run
		
		bSavPseudo = mp.bPseudo; bSavSynOnly = mp.bSynOnly;
		manFrame.updateEnableButtons(); // update Selected pair label; CAS571
		
		if (mp.bSynOnly) setSaveSynOnly();	
		else if (mp.bPseudo) setSavePseudo();
		
		setVisible(false);
	}
	private void setSavePseudo() {
		setParams(mp.getDbParams());
		chkPseudo.setSelected(true);
	}
	private void setSaveSynOnly() {
		boolean isOrient = chkSameOrient.isSelected(); 
		int isMerge = cmbMergeBlocks.getSelectedIndex(); 
		boolean isPrune = chkStrictBlocks.isSelected(); 
		String dot = txtMinDots.getText();
		int order=0;								// CAS572 add
		if (radOrdProj1.isSelected()) order=1;
		else if (radOrdProj2.isSelected()) order=2;
		
		setParams(mp.getDbParams()); // The wrong values will be in Summary if this is not done; CAS571
		
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
				Utilities.showErrorMessage(label + "=" + x + "\nIs an incorrect integer (" + x + "). Must be >=0.");
				return false;
			}
			return true;
		}
		if (i==-1 || i==0) {
			Utilities.showErrorMessage(label + "=" + x + "\nIs an incorrect integer. Must be >0.");
			return false;
		}
		// warnings
		if (label.startsWith("Top") && i>4) {
			Utilities.showContinue("Top N piles", "Top N > 4 may produce worse results and take significantly longer.");
		}
	
		return true;
	}
	private boolean checkDouble(String label, String x, double min) {
		if (x.trim().equals("")) x="0.0"; 
		double d = Utilities.getDouble(x.trim());
		if (min==0) {
			if (d<0) {
				Utilities.showErrorMessage(label + "=" + x + "\nIs an incorrect decimal (" + x + "). Must be >=0.");
				return false;
			}
			return true;
		}
		if (d==-1 || d==0) {
			Utilities.showErrorMessage(label + "=" + x + "\nIs an incorrect decimal. Must be >0.");
			return false;
		}
		return true;
	}

	/************************************************************************/
	private JPanel mainPanel = null;
	
	private JCheckBox chkConcat = null, chkMask1 = null, chkMask2 = null;
	private JLabel lblNucMerArgs = null, lblProMerArgs = null, lblSelfArgs = null;
	private JTextField txtNucMerArgs = null, txtSelfArgs = null, txtProMerArgs = null;
	private JRadioButton chkNucOnly = null, chkProOnly = null, chkDef = null;
	
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
	
	private JButton btnKeep = null, btnDiscard = null, btnLoadDef = null;
	
	private JButton btnActPop;  						
    private JRadioButtonMenuItem act0Radio	= null;
    private JRadioButtonMenuItem act1Radio	= null;
    private JRadioButtonMenuItem act2Radio	= null;
    private boolean bSavPseudo=false, bSavSynOnly=false;
}
