package symap.projectmanager.common;

/**********************************************
 * The parameter window for Alignment&Synteny
 * CAS501 massive changes to make it a little clearer what parameters apply to a pair
 */
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import util.Utilities;
import backend.SyProps;

public class PairPropertyFrame extends JDialog {
	private static final long serialVersionUID = 1L;
	private static final String [] LABELS = { // CAS511 put FPC in front of Blat, order does not matter
		"Min Dots", "Top N", "Merge Blocks", 
		"Do Synteny", "Do Clustering", "No Overlapping Blocks", 
		"FPC Blat Args", "NUCmer Args", "PROmer Args", "Self Args", "NUCmer Only","PROmer Only" };
	
	// if change, change in SyProps too
	private static final String [] SYMBOLS = { 
		"mindots", "topn", "merge_blocks", 
		"do_synteny", "do_clustering", "no_overlapping_blocks", 
		"blat_args", "nucmer_args", "promer_args", "self_args", "nucmer_only","promer_only" };
	
	private static final int LABEL_COLUMN_WIDTH = 150;
	private static final int TEXT_FIELD_WIDTH = 15;
	private static final int NUM_FIELD_WIDTH = 3;
	
	private String title;
	private boolean isGlobal=false, hasFPC=false;
	
	// glProps = current set of properties, i.e., either defaults or the saved global parameters
	// dbProps = the database-stored properties for the selected pair, if any. 
	public PairPropertyFrame(SyProps glProps, SyProps dbProps, Project p1, Project p2, ProjectManagerFrameCommon parent) {
		this.globalProps = glProps; 
		this.p1 = p1;
		this.p2 = p2;
		this.parent = parent;
		
		if (p1==null) isGlobal=true;
		
		if (isGlobal) title="Global Pair Parameters";
		else          title="Selected Pair Parameters";
		
		// CAS520 always false now if (Utilities.pathExists(Constants.fpcDataDir)) hasFPC=true; // CAS511
		
		createMainPanel();
		
		setDefaults(dbProps);
		
		setModal(true);
		
		setTitle(title);
	}
	
	private void setDefaults(SyProps props) {
		SyProps tempProps = (props == null ? new SyProps() : props);

		for(int x=0; x<SYMBOLS.length; x++)
			setValue(SYMBOLS[x], tempProps.getProperty(SYMBOLS[x]));
	}
	
	private String getValue(String symbol) {
		int x=0;
		if(symbol.equals(SYMBOLS[x++])) 		return txtMinDots.getText();
		else if(symbol.equals(SYMBOLS[x++])) return txtTopN.getText();
		else if(symbol.equals(SYMBOLS[x++]))	return chkMergeBlocks.isSelected()?"1":"0";
		else if(symbol.equals(SYMBOLS[x++]))	return chkDoSynteny.isSelected()?"1":"0";
		else if(symbol.equals(SYMBOLS[x++]))	return chkDoClustering.isSelected()?"1":"0";
		else if(symbol.equals(SYMBOLS[x++]))	return chkNoOverlappingBlocks.isSelected()?"1":"0";
		else if(symbol.equals(SYMBOLS[x++]))	return txtBlatArgs.getText();
		else if(symbol.equals(SYMBOLS[x++]))	return txtNucMerArgs.getText();
		else if(symbol.equals(SYMBOLS[x++]))	return txtProMerArgs.getText();
		else if(symbol.equals(SYMBOLS[x++]))	return txtSelfArgs.getText();
		else if(symbol.equals(SYMBOLS[x++]))	return chkNucOnly.isSelected()?"1":"0";
		else if(symbol.equals(SYMBOLS[x++]))	return chkProOnly.isSelected()?"1":"0";
		else 
			return "";
	}
	
	private void setValue(String symbol, String value) {
		int x=0;
		if(symbol.equals(SYMBOLS[x++]))		txtMinDots.setText(value);
		else if(symbol.equals(SYMBOLS[x++]))	txtTopN.setText(value);
		else if(symbol.equals(SYMBOLS[x++]))	chkMergeBlocks.setSelected(value.equals("1"));
		else if(symbol.equals(SYMBOLS[x++]))	chkDoSynteny.setSelected(value.equals("1"));
		else if(symbol.equals(SYMBOLS[x++]))	chkDoClustering.setSelected(value.equals("1"));
		else if(symbol.equals(SYMBOLS[x++]))	chkNoOverlappingBlocks.setSelected(value.equals("1"));
		else if(symbol.equals(SYMBOLS[x++]))	txtBlatArgs.setText(value);
		else if(symbol.equals(SYMBOLS[x++]))	txtNucMerArgs.setText(value);
		else if(symbol.equals(SYMBOLS[x++]))	txtProMerArgs.setText(value);
		else if(symbol.equals(SYMBOLS[x++]))	txtSelfArgs.setText(value);
		else if(symbol.equals(SYMBOLS[x++]))	chkNucOnly.setSelected(value.equals("1"));
		else if(symbol.equals(SYMBOLS[x++]))	chkProOnly.setSelected(value.equals("1"));
	}
	
	private void createMainPanel() {
		int x=0;
		lblMinDots = new JLabel(LABELS[x++]);
		txtMinDots = new JTextField(NUM_FIELD_WIDTH);
		txtMinDots.setMaximumSize(txtMinDots.getPreferredSize());
		txtMinDots.setMinimumSize(txtMinDots.getPreferredSize());
		
		lblTopN = new JLabel(LABELS[x++]);
		txtTopN = new JTextField(NUM_FIELD_WIDTH);
		txtTopN.setMaximumSize(txtTopN.getPreferredSize());
		txtTopN.setMinimumSize(txtTopN.getPreferredSize());

		chkMergeBlocks = new JCheckBox(LABELS[x++]);
		chkMergeBlocks.setBackground(Color.WHITE);
		chkMergeBlocks.setMaximumSize(chkMergeBlocks.getPreferredSize());
		chkMergeBlocks.setMinimumSize(chkMergeBlocks.getPreferredSize());
		
		chkDoSynteny = new JCheckBox(LABELS[x++]);
		chkDoSynteny.setBackground(Color.WHITE);
		chkDoSynteny.setMaximumSize(chkDoSynteny.getPreferredSize());
		chkDoSynteny.setMinimumSize(chkDoSynteny.getPreferredSize());
		
		chkDoClustering = new JCheckBox(LABELS[x++]);
		chkDoClustering.setBackground(Color.WHITE);
		chkDoClustering.setMaximumSize(chkDoClustering.getPreferredSize());
		chkDoClustering.setMinimumSize(chkDoClustering.getPreferredSize());
		
		chkNoOverlappingBlocks = new JCheckBox(LABELS[x++]);
		chkNoOverlappingBlocks.setBackground(Color.WHITE);
		chkNoOverlappingBlocks.setMaximumSize(chkNoOverlappingBlocks.getPreferredSize());
		chkNoOverlappingBlocks.setMinimumSize(chkNoOverlappingBlocks.getPreferredSize());
		
		lblBlatArgs = new JLabel(LABELS[x++]);
		txtBlatArgs = new JTextField(TEXT_FIELD_WIDTH);
		txtBlatArgs.setMaximumSize(txtBlatArgs.getPreferredSize());
		txtBlatArgs.setMinimumSize(txtBlatArgs.getPreferredSize());
		
		lblNucMerArgs = new JLabel(LABELS[x++]);
		txtNucMerArgs = new JTextField(TEXT_FIELD_WIDTH);
		txtNucMerArgs.setMaximumSize(txtNucMerArgs.getPreferredSize());
		txtNucMerArgs.setMinimumSize(txtNucMerArgs.getPreferredSize());
		
		lblProMerArgs = new JLabel(LABELS[x++]);
		txtProMerArgs = new JTextField(TEXT_FIELD_WIDTH);
		txtProMerArgs.setMaximumSize(txtProMerArgs.getPreferredSize());
		txtProMerArgs.setMinimumSize(txtProMerArgs.getPreferredSize());

		lblSelfArgs = new JLabel(LABELS[x++]);
		txtSelfArgs = new JTextField(TEXT_FIELD_WIDTH);
		txtSelfArgs.setMaximumSize(txtSelfArgs.getPreferredSize());
		txtSelfArgs.setMinimumSize(txtSelfArgs.getPreferredSize());

		chkNucOnly = new JCheckBox(LABELS[x++]);
		chkNucOnly.setBackground(Color.WHITE);
		chkNucOnly.setMaximumSize(chkNucOnly.getPreferredSize());
		chkNucOnly.setMinimumSize(chkNucOnly.getPreferredSize());

		chkProOnly = new JCheckBox(LABELS[x++]);
		chkProOnly.setBackground(Color.WHITE);
		chkProOnly.setMaximumSize(chkProOnly.getPreferredSize());
		chkProOnly.setMinimumSize(chkProOnly.getPreferredSize());
		
		btnKeep = new JButton("Save");
		btnKeep.setBackground(Color.WHITE);
		btnKeep.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				SyProps p = new SyProps();
				for (String key : SYMBOLS) {
					p.setProperty(key, getValue(key));
				}
				parent.setPairProps(p, p1, p2);
				setVisible(false);
			}
		});
			
		btnLoadGlobal = new JButton("Globals");
		btnLoadGlobal.setBackground(Color.WHITE);
		btnLoadGlobal.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				setDefaults(globalProps);
			}
		});
		if (globalProps == null) btnLoadGlobal.setEnabled(false);
		
		btnLoadDef = new JButton("Defaults");
		btnLoadDef.setBackground(Color.WHITE);
		btnLoadDef.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				setDefaults(null);
			}
		});
		
		btnDiscard = new JButton("Cancel");
		btnDiscard.setBackground(Color.WHITE);
		btnDiscard.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				setVisible(false);
			}
		});

		JButton btnHelp = new JButton("Help");
		btnHelp.setVisible(true);
		btnHelp.setEnabled(true);
		btnHelp.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				showHelp();
			}
		} );

		mainPanel = new JPanel();
		mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.PAGE_AXIS));
		mainPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
		mainPanel.setBackground(Color.WHITE);
		
		JPanel row = createRowPanel();		
		row.add(new JLabel(title + " for Alignment & Synteny"));

		row.add(Box.createHorizontalStrut(150));
		row.add(btnHelp);
		mainPanel.add(row);
		mainPanel.add(Box.createVerticalStrut(10));
		
		row = createRowPanel();
		row.add(lblMinDots);
		if(lblMinDots.getPreferredSize().width < LABEL_COLUMN_WIDTH)
			row.add(Box.createHorizontalStrut(LABEL_COLUMN_WIDTH - lblMinDots.getPreferredSize().width));
		row.add(txtMinDots);
		mainPanel.add(row);
		mainPanel.add(Box.createVerticalStrut(10));

		row = createRowPanel();
		row.add(lblTopN);
		if(lblTopN.getPreferredSize().width < LABEL_COLUMN_WIDTH)
			row.add(Box.createHorizontalStrut(LABEL_COLUMN_WIDTH - lblTopN.getPreferredSize().width));
		row.add(txtTopN);
		mainPanel.add(row);
		mainPanel.add(Box.createVerticalStrut(10));
		
		row = createRowPanel();
		row.add(chkMergeBlocks);
		mainPanel.add(row);
		mainPanel.add(Box.createVerticalStrut(10));
		
		row = createRowPanel();
		row.add(chkNoOverlappingBlocks);
		mainPanel.add(row);
		mainPanel.add(Box.createVerticalStrut(10));

		row = createRowPanel();
		row.add(chkProOnly);
		mainPanel.add(row);
		mainPanel.add(Box.createVerticalStrut(10));
		
		row = createRowPanel();
		row.add(chkNucOnly);
		mainPanel.add(row);
		mainPanel.add(Box.createVerticalStrut(10));

		row = createRowPanel();
		row.add(lblProMerArgs);
		if(lblProMerArgs.getPreferredSize().width < LABEL_COLUMN_WIDTH)
			row.add(Box.createHorizontalStrut(LABEL_COLUMN_WIDTH - lblProMerArgs.getPreferredSize().width));
		row.add(txtProMerArgs);
		mainPanel.add(row);	
		mainPanel.add(Box.createVerticalStrut(10));
		
		row = createRowPanel();
		row.add(lblNucMerArgs);
		if(lblNucMerArgs.getPreferredSize().width < LABEL_COLUMN_WIDTH)
			row.add(Box.createHorizontalStrut(LABEL_COLUMN_WIDTH - lblNucMerArgs.getPreferredSize().width));
		row.add(txtNucMerArgs);
		mainPanel.add(row);
		mainPanel.add(Box.createVerticalStrut(10));
	
		row = createRowPanel();
		row.add(lblSelfArgs);
		if(lblSelfArgs.getPreferredSize().width < LABEL_COLUMN_WIDTH)
			row.add(Box.createHorizontalStrut(LABEL_COLUMN_WIDTH - lblSelfArgs.getPreferredSize().width));
		row.add(txtSelfArgs);
		mainPanel.add(row);	
		mainPanel.add(Box.createVerticalStrut(10));

		if (hasFPC) { // CAS511 move label and only add if data/fpc exists
			row = createRowPanel();
			row.add(lblBlatArgs);
			if(lblBlatArgs.getPreferredSize().width < LABEL_COLUMN_WIDTH)
				row.add(Box.createHorizontalStrut(LABEL_COLUMN_WIDTH - lblBlatArgs.getPreferredSize().width));
			row.add(txtBlatArgs);
			mainPanel.add(row);
			mainPanel.add(Box.createVerticalStrut(10));
		}
		mainPanel.add(Box.createVerticalStrut(10));
		
		row = createRowPanel();
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));
		buttonPanel.setBackground(Color.WHITE);
		buttonPanel.add(btnKeep);
		buttonPanel.add(Box.createHorizontalStrut(20));
		/** CAS507 disabled because did not work right
		if (!isGlobal) {
			buttonPanel.add(btnLoadGlobal); 
			buttonPanel.add(Box.createHorizontalStrut(20));
		}
		**/
		buttonPanel.add(btnLoadDef);
		buttonPanel.add(Box.createHorizontalStrut(20));
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
	}
	
	private JPanel createRowPanel() {
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.LINE_AXIS));
		row.setBackground(Color.WHITE);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		
		return row;
	}
	private void showHelp() 
	{
		Utilities.showHTMLPage( this,"Alignment & Synteny Parameter Help", "/html/PairParamHelp.html");
	}	
	
	
	private JPanel mainPanel = null;
	
	private JLabel lblMinDots = null;
	private JTextField txtMinDots = null;
	
	private JLabel lblTopN = null;
	private JTextField txtTopN = null;
	
	private JCheckBox chkMergeBlocks = null;
	private JCheckBox chkDoSynteny = null;
	private JCheckBox chkDoClustering = null;	
	private JCheckBox chkNoOverlappingBlocks = null;
	private JCheckBox chkNucOnly = null;
	private JCheckBox chkProOnly = null;
	
	private JLabel lblBlatArgs = null;
	private JTextField txtBlatArgs = null;
	
	private JLabel lblNucMerArgs = null;
	private JTextField txtNucMerArgs = null;
	
	private JLabel lblSelfArgs = null;
	private JTextField txtSelfArgs = null;
	
	private JLabel lblProMerArgs = null;
	private JTextField txtProMerArgs = null;
	
	private JButton btnKeep = null;
	private JButton btnDiscard = null;
	private JButton btnLoadGlobal = null;
	private JButton btnLoadDef = null;
	
	private SyProps globalProps = null;
	private Project p1, p2;
	private ProjectManagerFrameCommon parent;
}
