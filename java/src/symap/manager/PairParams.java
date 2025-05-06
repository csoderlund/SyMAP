package symap.manager;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.JTextField;

import backend.Constants;
import util.Jcomp;
import util.Jhtml;
import util.Utilities;

/**********************************************
 * The parameter window for Alignment&Synteny
 * CAS501 massive changes to make it a little clearer what parameters apply to a pair
 * CAS532 finished removing fpc; CAS534 changed to use Mpair
 * CAS533 remove: do_synteny, Do Clustering - dead, No Overlapping Blocks - buggy
 */

public class PairParams extends JDialog {
	private static final long serialVersionUID = 1L;
	
	// Order MUST be same in createMainPanel and setValue and getValue; defaults are in Mpair
	// These are NOT in the order displayed, but do not change without changing access everywhere else
	private static final String [] LABELS = { // Order must be same as createMainPanel
		"Min Dots", "Top N piles", "Merge Blocks", 		// CAS548 add 'piles'
		"NUCmer Args", "PROmer Args", "Self Args", "NUCmer Only","PROmer Only",
		"Number Pseudo", // CAS565 add
		"Algorithm 1 (modified original)", "Algorithm 2 (exon-intron)",  // CAS555 change '()' text 
		"G0_Len", "Gene", "Exon", "Len",
		 "EE", "EI", "En", "II", "In"
	};
	
	// if change, change in Mpair too; defaults are in Mpair; written to DB and file
	// order the same as above; constant strings are repeated multiple times in MPair
	private static final String [] SYMBOLS = { 
		"mindots", "topn", "merge_blocks", 
		"nucmer_args", "promer_args", "self_args", "nucmer_only","promer_only",
		"number_pseudo",
		"algo1", "algo2",										
		"g0_scale", "gene_scale", "exon_scale", "len_scale",
		"EE_pile", "EI_pile", "En_pile", "II_pile", "In_pile"
	};
	
	public PairParams(Mpair mp) {
		this.mp = mp;
		
		createMainPanel();
		setDefaults(mp.getFileParams(), false);
		
		setModal(true);
		setTitle(mp.mProj1.strDBName + "-" + mp.mProj2.strDBName + " params file");
	}
	/*********************************************************************/
	private void createMainPanel() {
		int numWidth = 3, decWidth = 2, textWidth = 15;
		
		mainPanel = Jcomp.createPagePanel();
		chkVB = Jcomp.createCheckBox("Verbose   ","Verbose output to symap.log & terminal.", Constants.VERBOSE); // not saved
		chkVB.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				Constants.VERBOSE = chkVB.isSelected();
			}
		});
		
		int x=0;
		
		lblMinDots = Jcomp.createLabel(LABELS[x++]); txtMinDots = Jcomp.createTextField("7", "Miniumum hits in a block", numWidth);
		
		lblTopN = Jcomp.createLabel(LABELS[x++]); txtTopN = Jcomp.createTextField("2", "Retain the top N hits of a pile of overlapping hits", numWidth);
		chkMergeBlocks =Jcomp.createCheckBox(LABELS[x++], "Merge overlapping (or nearby) synteny blocks", false); 
	
		lblNucMerArgs = Jcomp.createLabel(LABELS[x++]); txtNucMerArgs = Jcomp.createTextField("",textWidth);
		lblProMerArgs = Jcomp.createLabel(LABELS[x++]); txtProMerArgs = Jcomp.createTextField("",textWidth);
		lblSelfArgs = Jcomp.createLabel(LABELS[x++]);   txtSelfArgs = Jcomp.createTextField("",textWidth);

		chkNucOnly = Jcomp.createRadio(LABELS[x++]);
		chkProOnly = Jcomp.createRadio(LABELS[x++]);
		chkDef     = Jcomp.createRadio("Defaults");		// not saved
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
		
		String lab; // CAS566 better description
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
		btnKeep = Jcomp.createButton("Keep", "Save parameters and exit");
		btnKeep.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				save();
			}
		});
		btnLoadDef = Jcomp.createButton("Defaults", "Set defaults");
		btnLoadDef.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				setDefaults(mp.getDefaults(), true);
			}
		});
		btnDiscard = Jcomp.createButton("Cancel", "Cancel changes and exit");
		btnDiscard.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				setVisible(false);
			}
		});
		/*-------- Create Layout ------------------*/
		//////////////////////////////////////////////////////////////////////////
		JPanel row = Jcomp.createRowPanel();
		JLabel projLabel = Jcomp.createLabel(mp.mProj1.strDisplayName + " & " + mp.mProj2.strDisplayName);
		row.add(projLabel); row.add(Box.createVerticalStrut(5));
		row.add(chkVB);  
		mainPanel.add(row); mainPanel.add(Box.createVerticalStrut(5));
	
		// Alignment
		row = Jcomp.createRowPanel();
		row.add(Jcomp.createHtmlLabel("Alignment"));  row.add(Box.createVerticalStrut(5)); // vert puts icon at end of line
		row.add(Jhtml.createHelpIconSysSm(Jhtml.SYS_HELP_URL, Jhtml.param2Align));// CAS534 change from Help; CAS546 added
		mainPanel.add(row); mainPanel.add(Box.createVerticalStrut(5));
		
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
		row.add(Jhtml.createHelpIconSysSm(Jhtml.SYS_HELP_URL, Jhtml.param2Clust));// CAS546 added
		mainPanel.add(row); mainPanel.add(Box.createVerticalStrut(5));
		
		row = Jcomp.createRowPanel(); // CAS565 add
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
		row.add(lblExon);    row.add(txtExon);   row.add(Box.createHorizontalStrut(5)); // CAS548 swapped order
		row.add(lblGene);    row.add(txtGene);   row.add(Box.createHorizontalStrut(5));
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
		row.add(Jcomp.createHtmlLabel("Synteny"));  row.add(Box.createVerticalStrut(5));
		row.add(Jhtml.createHelpIconSysSm(Jhtml.SYS_HELP_URL, Jhtml.param2Syn));//  CAS546 added
		mainPanel.add(row); mainPanel.add(Box.createVerticalStrut(5));
		
		row = Jcomp.createRowPanel();
		row.add(lblMinDots); row.add(Box.createHorizontalStrut(5));
		row.add(txtMinDots); row.add(Box.createHorizontalStrut(10));
		
		row.add(chkMergeBlocks);
		mainPanel.add(row);	mainPanel.add(Box.createVerticalStrut(5));
		
		// Buttons
		mainPanel.add(new JSeparator()); mainPanel.add(Box.createVerticalStrut(5)); 
		
		row = Jcomp.createRowPanel();
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));
		buttonPanel.setBackground(Color.WHITE);
		buttonPanel.add(btnKeep);		buttonPanel.add(Box.createHorizontalStrut(5));
		buttonPanel.add(btnLoadDef);	buttonPanel.add(Box.createHorizontalStrut(5));
		buttonPanel.add(btnDiscard);	buttonPanel.add(Box.createHorizontalStrut(5));
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
	}/************************************************************************/
	private String getValue(String symbol) {
		int x=0;
		if (symbol.equals(SYMBOLS[x++])) 		return txtMinDots.getText();
		else if (symbol.equals(SYMBOLS[x++])) 	return txtTopN.getText();
		else if (symbol.equals(SYMBOLS[x++]))	return chkMergeBlocks.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++]))	return txtNucMerArgs.getText();
		else if (symbol.equals(SYMBOLS[x++]))	return txtProMerArgs.getText();
		else if (symbol.equals(SYMBOLS[x++]))	return txtSelfArgs.getText();
		else if (symbol.equals(SYMBOLS[x++]))	return chkNucOnly.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++]))	return chkProOnly.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++])) 	return chkPseudo.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++]))	return chkAlgo1.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++]))	return chkAlgo2.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++]))	return txtNoGene.getText();
		else if (symbol.equals(SYMBOLS[x++]))	return txtGene.getText();
		else if (symbol.equals(SYMBOLS[x++]))	return txtExon.getText();
		else if (symbol.equals(SYMBOLS[x++]))	return txtLen.getText();
		else if (symbol.equals(SYMBOLS[x++]))	return chkEEpile.isSelected()?"1":"0"; 
		else if (symbol.equals(SYMBOLS[x++]))	return chkEIpile.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++]))	return chkEnpile.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++]))	return chkIIpile.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++]))	return chkInpile.isSelected()?"1":"0";
		else return "";
	}
	private void setValue(String symbol, String value) {
		int x=0;
		if (symbol.equals(SYMBOLS[x++]))		txtMinDots.setText(value);
		else if (symbol.equals(SYMBOLS[x++]))	txtTopN.setText(value);
		else if (symbol.equals(SYMBOLS[x++]))	chkMergeBlocks.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	txtNucMerArgs.setText(value);
		else if (symbol.equals(SYMBOLS[x++]))	txtProMerArgs.setText(value);
		else if (symbol.equals(SYMBOLS[x++]))	txtSelfArgs.setText(value);
		else if (symbol.equals(SYMBOLS[x++]))	chkNucOnly.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	chkProOnly.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))		chkPseudo.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	chkAlgo1.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	chkAlgo2.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	txtNoGene.setText(value);
		else if (symbol.equals(SYMBOLS[x++]))	txtGene.setText(value);
		else if (symbol.equals(SYMBOLS[x++]))	txtExon.setText(value);
		else if (symbol.equals(SYMBOLS[x++]))	txtLen.setText(value);
		else if (symbol.equals(SYMBOLS[x++]))	chkEEpile.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	chkEIpile.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	chkEnpile.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	chkIIpile.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	chkInpile.setSelected(value.equals("1"));
	}
	/************************************************************************/
	private void setDefaults(HashMap <String, String> valMap, boolean setDef) {
		for(int x=0; x<SYMBOLS.length; x++)
			setValue(SYMBOLS[x], valMap.get(SYMBOLS[x]));	
		if (setDef) chkDef.setSelected(true); // CAS561 Defaults should reset this, but not initial load from file
	}
	private void save() {
		if (!checkInt("Min Dots", txtMinDots.getText(), 1)) return; 
		if (!checkInt("Top N piles", txtTopN.getText(), 1)) return;		
		if (!checkDouble("G0_Len", txtNoGene.getText(), 0)) return;		 
		if (!checkDouble("Gene", txtGene.getText(), 0)) return;	
		if (!checkDouble("Exon", txtExon.getText(), 0)) return;	
		if (!checkDouble("Length", txtLen.getText(), 0)) return;	
		
		HashMap <String, String> fileMap = mp.getFileParams();
		
		for (String key : SYMBOLS) {
			fileMap.put(key, getValue(key));
		}
		mp.writeFile(fileMap);
		setVisible(false);
	}
	private boolean checkInt(String label, String x, int min) {
		if (x.trim().equals("")) x="0"; // CAS546 
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
		if (label.startsWith("Top") && i>4) {
			return Utilities.showContinue("Top N piles", "Top N > 4 may produce worse results and take significantly longer.");
		}
		return true;
	}
	private boolean checkDouble(String label, String x, double min) {
		if (x.trim().equals("")) x="0.0"; // CAS546 
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
	private Mpair mp = null;
	
	private JLabel lblNucMerArgs = null, lblProMerArgs = null, lblSelfArgs = null;
	private JTextField txtNucMerArgs = null, txtSelfArgs = null, txtProMerArgs = null;
	private JRadioButton chkNucOnly = null, chkProOnly = null, chkDef = null;
	
	private JCheckBox chkVB = null;
	private JRadioButton chkAlgo1,  chkAlgo2;
	
	private JLabel lblTopN = null;
	private JTextField txtTopN = null;
	
	private JLabel lblNoGene, lblGene, lblExon, lblLen;
	private JTextField txtNoGene, txtGene, txtExon, txtLen;
	private JCheckBox chkEEpile = null, chkEIpile = null, chkEnpile = null, chkIIpile = null, chkInpile = null;
	
	private JCheckBox chkPseudo = null;
	
	private JLabel lblMinDots = null;
	private JTextField txtMinDots = null;
	private JCheckBox chkMergeBlocks = null;
	
	private JButton btnKeep = null, btnDiscard = null, btnLoadDef = null;
}
