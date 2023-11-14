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

import util.Jhtml;
import util.Utilities;

/**********************************************
 * The parameter window for Alignment&Synteny
 * CAS501 massive changes to make it a little clearer what parameters apply to a pair
 * CAS507 disabled Global args because did not work right
 * CAS532 finished removing fpc; CAS534 changed to use Mpair
 * CAS533 remove: do_synteny, Do Clustering - dead, No Overlapping Blocks - buggy
 */

public class PairParams extends JDialog {
	private static final long serialVersionUID = 1L;
	
	// Order must be same in createMainPanel and setValue and getValue
	private static final String [] LABELS = { // Order must be same as createMainPanel
		"Min Dots", "Top N", "Merge Blocks", 
		"NUCmer Args", "PROmer Args", "Self Args", "NUCmer Only","PROmer Only",
		"Algorithm 1 (original)", "Algorithm 2 (gene-centric)", 
		"Intergenic", "Intron", "Exon",
		 "EE", "EI", "En", "II", "In"
		};
	
	// if change, change in Mpair too; defaults are in Mpair
	private static final String [] SYMBOLS = { 
		"mindots", "topn", "merge_blocks", 
		"nucmer_args", "promer_args", "self_args", "nucmer_only","promer_only", 
		"algo1", "algo2",
		"g0_match", "gintron_match", "gexon_match",
		"EE_pile", "EI_pile", "En_pile", "II_pile", "In_pile"
		};
	
	private Mpair mp;
	
	public PairParams(Mpair mp) {
		this.mp = mp;
		
		createMainPanel();
		setDefaults(mp.getFileParams());
		
		setModal(true);
		setTitle(mp.mProj1.strDBName + "-" + mp.mProj2.strDBName + " params file");
	}
	
	private void setDefaults(HashMap <String, String> valMap) {
		for(int x=0; x<SYMBOLS.length; x++)
			setValue(SYMBOLS[x], valMap.get(SYMBOLS[x]));
	}
	private void save() {
		if (!checkInt("Min Dots", txtMinDots.getText(), 1)) return; // CAS540 add check
		if (!checkInt("Top N", txtTopN.getText(), 1)) return;		 // CAS540 add check
		if (!checkInt("Intergenic", txtNoGene.getText(), 0)) return;		 
		if (!checkInt("Intron", txtIntron.getText(), 0)) return;	
		if (!checkInt("Exon", txtExon.getText(), 0)) return;	
		
		HashMap <String, String> fileMap = mp.getFileParams();
		
		for (String key : SYMBOLS) {
			fileMap.put(key, getValue(key));
		}
		mp.writeFile(fileMap);
		setVisible(false);
	}
	
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
		else if (symbol.equals(SYMBOLS[x++]))	return chkAlgo1.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++]))	return chkAlgo2.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++]))	return txtNoGene.getText();
		else if (symbol.equals(SYMBOLS[x++]))	return txtIntron.getText();
		else if (symbol.equals(SYMBOLS[x++]))	return txtExon.getText();
		else if (symbol.equals(SYMBOLS[x++]))	return chkEEpile.isSelected()?"1":"0"; 
		else if (symbol.equals(SYMBOLS[x++]))	return chkEIpile.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++]))	return chkEnpile.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++]))	return chkIIpile.isSelected()?"1":"0";
		else if (symbol.equals(SYMBOLS[x++]))	return chkInpile.isSelected()?"1":"0";
		else return "";
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
			return Utilities.showContinue("Top N", "Top N > 4 may produce worse results and take significantly longer.");
		}
		return true;
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
		else if (symbol.equals(SYMBOLS[x++]))	chkAlgo1.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	chkAlgo2.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	txtNoGene.setText(value);
		else if (symbol.equals(SYMBOLS[x++]))	txtIntron.setText(value);
		else if (symbol.equals(SYMBOLS[x++]))	txtExon.setText(value);
		else if (symbol.equals(SYMBOLS[x++]))	chkEEpile.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	chkEIpile.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	chkEnpile.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	chkIIpile.setSelected(value.equals("1"));
		else if (symbol.equals(SYMBOLS[x++]))	chkInpile.setSelected(value.equals("1"));
	}
	
	private void createMainPanel() {
		int numWidth = 3, textWidth = 15;
		
		int x=0;
		lblMinDots = new JLabel(LABELS[x++]);
		txtMinDots = new JTextField(numWidth);
		txtMinDots.setMaximumSize(txtMinDots.getPreferredSize());
		txtMinDots.setMinimumSize(txtMinDots.getPreferredSize());
		
		lblTopN = new JLabel(LABELS[x++]);
		txtTopN = new JTextField(numWidth);
		txtTopN.setMaximumSize(txtTopN.getPreferredSize());
		txtTopN.setMinimumSize(txtTopN.getPreferredSize());

		chkMergeBlocks = new JCheckBox(LABELS[x++]); chkMergeBlocks.setBackground(Color.WHITE);
		chkMergeBlocks.setMaximumSize(chkMergeBlocks.getPreferredSize());
		chkMergeBlocks.setMinimumSize(chkMergeBlocks.getPreferredSize());
		
		lblNucMerArgs = new JLabel(LABELS[x++]);
		txtNucMerArgs = new JTextField(textWidth);
		txtNucMerArgs.setMaximumSize(txtNucMerArgs.getPreferredSize());
		txtNucMerArgs.setMinimumSize(txtNucMerArgs.getPreferredSize());
		
		lblProMerArgs = new JLabel(LABELS[x++]);
		txtProMerArgs = new JTextField(textWidth);
		txtProMerArgs.setMaximumSize(txtProMerArgs.getPreferredSize());
		txtProMerArgs.setMinimumSize(txtProMerArgs.getPreferredSize());

		lblSelfArgs = new JLabel(LABELS[x++]);
		txtSelfArgs = new JTextField(textWidth);
		txtSelfArgs.setMaximumSize(txtSelfArgs.getPreferredSize());
		txtSelfArgs.setMinimumSize(txtSelfArgs.getPreferredSize());

		chkNucOnly = new JRadioButton(LABELS[x++]); chkNucOnly.setBackground(Color.WHITE);
		chkNucOnly.setMaximumSize(chkNucOnly.getPreferredSize());
		chkNucOnly.setMinimumSize(chkNucOnly.getPreferredSize());

		chkProOnly = new JRadioButton(LABELS[x++]); chkProOnly.setBackground(Color.WHITE);
		chkProOnly.setMaximumSize(chkProOnly.getPreferredSize());
		chkProOnly.setMinimumSize(chkProOnly.getPreferredSize());
		
		chkDef = new JRadioButton("Default"); chkDef.setBackground(Color.WHITE); // not saved
		chkDef.setMaximumSize(chkDef.getPreferredSize());
		chkDef.setMinimumSize(chkDef.getPreferredSize());
		
		ButtonGroup sgroup = new ButtonGroup();
		sgroup.add(chkNucOnly); sgroup.add(chkProOnly); sgroup.add(chkDef);
		chkDef.setSelected(true);
		
		chkAlgo1 = new JRadioButton(LABELS[x++]); chkAlgo1.setBackground(Color.WHITE);
		chkAlgo1.setMaximumSize(chkAlgo1.getPreferredSize());
		chkAlgo1.setMinimumSize(chkAlgo1.getPreferredSize());

		chkAlgo2 = new JRadioButton(LABELS[x++]); chkAlgo2.setBackground(Color.WHITE);
		chkAlgo2.setMaximumSize(chkAlgo2.getPreferredSize());
		chkAlgo2.setMinimumSize(chkAlgo2.getPreferredSize());
		
		ButtonGroup agroup = new ButtonGroup();
		agroup.add(chkAlgo1); agroup.add(chkAlgo2);
		chkAlgo2.setSelected(true);
		
		lblNoGene = new JLabel(LABELS[x++]);
		txtNoGene = new JTextField(numWidth);
		txtNoGene.setMaximumSize(txtNoGene.getPreferredSize());
		txtNoGene.setMinimumSize(txtNoGene.getPreferredSize());
		
		lblIntron = new JLabel(LABELS[x++]);
		txtIntron = new JTextField(numWidth);
		txtIntron.setMaximumSize(txtIntron.getPreferredSize());
		txtIntron.setMinimumSize(txtIntron.getPreferredSize());
		
		lblExon = new JLabel(LABELS[x++]);
		txtExon = new JTextField(numWidth);
		txtExon.setMaximumSize(txtExon.getPreferredSize());
		txtExon.setMinimumSize(txtExon.getPreferredSize());
		
		chkEEpile = new JCheckBox(LABELS[x++]); chkEEpile.setSelected(true); 
		chkEIpile = new JCheckBox(LABELS[x++]); chkEIpile.setSelected(true); 
		chkEnpile = new JCheckBox(LABELS[x++]); chkEnpile.setSelected(true); 
		chkIIpile = new JCheckBox(LABELS[x++]); chkIIpile.setSelected(false);
		chkInpile = new JCheckBox(LABELS[x++]); chkInpile.setSelected(false);
		chkEEpile.setBackground(Color.WHITE); chkEIpile.setBackground(Color.WHITE);//CAS547 add for linux
		chkEnpile.setBackground(Color.WHITE); chkIIpile.setBackground(Color.WHITE);chkInpile.setBackground(Color.WHITE);
		
		btnKeep = new JButton("Save");
		btnKeep.setBackground(Color.WHITE);
		btnKeep.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				save();
			}
		});
			
		btnLoadDef = new JButton("Defaults");
		btnLoadDef.setBackground(Color.WHITE);
		btnLoadDef.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				setDefaults(mp.getDefaults());
			}
		});
		
		btnDiscard = new JButton("Cancel");
		btnDiscard.setBackground(Color.WHITE);
		btnDiscard.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				setVisible(false);
			}
		});

		
		mainPanel = new JPanel();
		mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.PAGE_AXIS));
		mainPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
		mainPanel.setBackground(Color.WHITE);
		
		mainPanel.add(new JLabel(mp.mProj1.strDisplayName + " & " + mp.mProj2.strDisplayName)); mainPanel.add(Box.createVerticalStrut(5));
		
		// Alignment
		JPanel row = createRowPanel();
		row.add(createLabel("Alignment"));  row.add(Box.createVerticalStrut(5));
		row.add(Jhtml.createHelpIconSysSm(Jhtml.SYS_HELP_URL, Jhtml.param2Align));// CAS534 change from Help; CAS546 added
		mainPanel.add(row); mainPanel.add(Box.createVerticalStrut(5));
		
		int colWidth = 100;
		
		row = createRowPanel();
		row.add(lblProMerArgs);
		if (lblProMerArgs.getPreferredSize().width < colWidth) row.add(Box.createHorizontalStrut(colWidth - lblProMerArgs.getPreferredSize().width));
		row.add(txtProMerArgs);
		mainPanel.add(row);	mainPanel.add(Box.createVerticalStrut(5));
		
		row = createRowPanel();
		row.add(lblNucMerArgs);
		if (lblNucMerArgs.getPreferredSize().width < colWidth) row.add(Box.createHorizontalStrut(colWidth - lblNucMerArgs.getPreferredSize().width));
		row.add(txtNucMerArgs);
		mainPanel.add(row);	mainPanel.add(Box.createVerticalStrut(5));
	
		row = createRowPanel();
		row.add(lblSelfArgs);
		if (lblSelfArgs.getPreferredSize().width < colWidth) row.add(Box.createHorizontalStrut(colWidth - lblSelfArgs.getPreferredSize().width));
		row.add(txtSelfArgs);
		mainPanel.add(row);	mainPanel.add(Box.createVerticalStrut(5));

		row = createRowPanel();
		row.add(chkProOnly); mainPanel.add(row);mainPanel.add(Box.createHorizontalStrut(20));
		row.add(chkNucOnly); mainPanel.add(row);mainPanel.add(Box.createHorizontalStrut(20));
		row.add(chkDef);     mainPanel.add(row);mainPanel.add(Box.createVerticalStrut(5));
		
		// Clusters
		mainPanel.add(new JSeparator()); mainPanel.add(Box.createVerticalStrut(5)); 
		row = createRowPanel();
		row.add(createLabel("Cluster Hits"));  row.add(Box.createVerticalStrut(5));
		row.add(Jhtml.createHelpIconSysSm(Jhtml.SYS_HELP_URL, Jhtml.param2Clust));// CAS534 change from Help; CAS546 added
		mainPanel.add(row); mainPanel.add(Box.createVerticalStrut(5));
		
		row = createRowPanel();
		row.add(chkAlgo1);
		mainPanel.add(row);	mainPanel.add(Box.createVerticalStrut(5));
		chkAlgo1.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				boolean b = chkAlgo1.isSelected();
				lblSelfArgs.setEnabled(b);
				txtSelfArgs.setEnabled(b);
			}
		});
		
		row = createRowPanel();
		row.add(lblTopN); row.add(Box.createHorizontalStrut(5));
		row.add(txtTopN);
		mainPanel.add(row);	mainPanel.add(Box.createVerticalStrut(5));
		
		row = createRowPanel();
		row.add(chkAlgo2);
		mainPanel.add(row);	mainPanel.add(Box.createVerticalStrut(5));
		chkAlgo2.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				boolean b = chkAlgo2.isSelected();
				lblSelfArgs.setEnabled(!b);
				txtSelfArgs.setEnabled(!b);
			}
		});
		
		mainPanel.add(new JLabel("Minimum matched bases:")); mainPanel.add(Box.createVerticalStrut(5));
		
		row = createRowPanel(); row.add(Box.createHorizontalStrut(10));
		row.add(lblNoGene);  row.add(txtNoGene);   row.add(Box.createHorizontalStrut(5));
		row.add(lblIntron);  row.add(txtIntron);  row.add(Box.createHorizontalStrut(5));
		row.add(lblExon);    row.add(txtExon); row.add(Box.createHorizontalStrut(5));
		mainPanel.add(row);	mainPanel.add(Box.createVerticalStrut(5));
		
		row = createRowPanel();
		row.add(new JLabel("Keep piles:")); 
		row.add(chkEEpile);   row.add(Box.createHorizontalStrut(5));
		row.add(chkEIpile);   row.add(Box.createHorizontalStrut(5));
		row.add(chkEnpile);   row.add(Box.createHorizontalStrut(5));
		row.add(chkIIpile);   row.add(Box.createHorizontalStrut(5));
		row.add(chkInpile);   row.add(Box.createHorizontalStrut(5));
		mainPanel.add(row);	mainPanel.add(Box.createVerticalStrut(5));
		
		// Synteny
		mainPanel.add(new JSeparator()); mainPanel.add(Box.createVerticalStrut(5)); 
		row = createRowPanel();
		row.add(createLabel("Synteny"));  row.add(Box.createVerticalStrut(5));
		row.add(Jhtml.createHelpIconSysSm(Jhtml.SYS_HELP_URL, Jhtml.param2Syn));// CAS534 change from Help; CAS546 added
		mainPanel.add(row); mainPanel.add(Box.createVerticalStrut(5));
		
		row = createRowPanel();
		row.add(lblMinDots); row.add(Box.createHorizontalStrut(5));
		row.add(txtMinDots); row.add(Box.createHorizontalStrut(10));
		
		row.add(chkMergeBlocks);
		mainPanel.add(row);	mainPanel.add(Box.createVerticalStrut(5));
		
		// Buttons
		mainPanel.add(new JSeparator()); mainPanel.add(Box.createVerticalStrut(5)); 
		
		row = createRowPanel();
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
		setLocationRelativeTo(null); // CAS532
	}
	private JLabel createLabel(String text) {
		String html = "<html><b><i>" + text + "</i></b></html";
		JLabel l = new JLabel(html);
		return l;
	}
	private JPanel createRowPanel() {
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.LINE_AXIS));
		row.setBackground(Color.WHITE);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		
		return row;
	}	
	
	private JPanel mainPanel = null;
	
	private JLabel lblNucMerArgs = null, lblProMerArgs = null, lblSelfArgs = null;
	private JTextField txtNucMerArgs = null, txtSelfArgs = null, txtProMerArgs = null;
	private JRadioButton chkNucOnly = null, chkProOnly = null, chkDef = null;
	
	private JRadioButton chkAlgo1,  chkAlgo2;
	
	private JLabel lblTopN = null;
	private JTextField txtTopN = null;
	
	private JLabel lblNoGene, lblIntron, lblExon;
	private JTextField txtNoGene, txtIntron, txtExon;
	private JCheckBox chkEEpile = null, chkEIpile = null, chkEnpile = null, chkIIpile = null, chkInpile = null;
	
	private JLabel lblMinDots = null;
	private JTextField txtMinDots = null;
	private JCheckBox chkMergeBlocks = null;
	
	private JButton btnKeep = null, btnDiscard = null, btnLoadDef = null;
	
}
