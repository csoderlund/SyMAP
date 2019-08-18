package symap.projectmanager.common;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Frame;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import util.Utilities;

import java.io.InputStream;
import java.util.Properties;

import backend.SyProps;

public class PairPropertyFrame extends JDialog {
	private static final String [] LABELS = { "Min Dots", "Top N", "Merge Blocks", 
						"Do Synteny", "Do Clustering", 
						"No Overlapping Blocks", 
							"Blat Args", "NUCmer Args", "PROmer Args","NUCmer Only","PROmer Only" };
	private static final String [] SYMBOLS = { "mindots", "topn", "merge_blocks", 
							"do_synteny", "do_clustering", 
							"no_overlapping_blocks", 
								"blat_args", "nucmer_args", "promer_args","nucmer_only","promer_only" };
	
	private static final int LABEL_COLUMN_WIDTH = 150;
	private static final int TEXT_FIELD_WIDTH = 15;
	private static final int NUM_FIELD_WIDTH = 3;
	
	// props = current set of properties, i.e., either defaults or the last ones saved from this dialog
	// prevProps = the database-stored properties for the selected pair, if any. Used for the load-previous button.
	public PairPropertyFrame(SyProps props, SyProps prevProps, ActionListener kl) {
		assert(kl != null);
		keepListener = kl;
		this.prevProps = prevProps;
		createMainPanel();
		setDefaults(props);
		setModal(true);
		setTitle("Parameters");
		//setVisible(true);
	}
	
	public void setDefaults(SyProps props) {
		SyProps tempProps = (props == null ? new SyProps() : props);

		for(int x=0; x<SYMBOLS.length; x++)
			setValue(SYMBOLS[x], tempProps.getProperty(SYMBOLS[x]));
	}
	
	public String getValue(String symbol) {
		if(symbol.equals(SYMBOLS[0]))
			return txtMinDots.getText();
		else if(symbol.equals(SYMBOLS[1]))
			return txtTopN.getText();
		else if(symbol.equals(SYMBOLS[2]))
			return chkMergeBlocks.isSelected()?"1":"0";
		else if(symbol.equals(SYMBOLS[3]))
			return chkDoSynteny.isSelected()?"1":"0";
		else if(symbol.equals(SYMBOLS[4]))
			return chkDoClustering.isSelected()?"1":"0";
		else if(symbol.equals(SYMBOLS[5]))
			return chkNoOverlappingBlocks.isSelected()?"1":"0";
		else if(symbol.equals(SYMBOLS[6]))
			return txtBlatArgs.getText();
		else if(symbol.equals(SYMBOLS[7]))
			return txtNucMerArgs.getText();
		else if(symbol.equals(SYMBOLS[8]))
			return txtProMerArgs.getText();
		else if(symbol.equals(SYMBOLS[9]))
			return chkNucOnly.isSelected()?"1":"0";
		else if(symbol.equals(SYMBOLS[10]))
			return chkProOnly.isSelected()?"1":"0";
		
		else 
			return "";
	}
	public void fillValues(Properties p) {
		for (String key : SYMBOLS) {
			p.setProperty(key, getValue(key));
		}
	}
	public void setValue(String symbol, String value) {
		if(symbol.equals(SYMBOLS[0]))
			txtMinDots.setText(value);
		else if(symbol.equals(SYMBOLS[1]))
			txtTopN.setText(value);
		else if(symbol.equals(SYMBOLS[2]))
			chkMergeBlocks.setSelected(value.equals("1"));
		else if(symbol.equals(SYMBOLS[3]))
			chkDoSynteny.setSelected(value.equals("1"));
		else if(symbol.equals(SYMBOLS[4]))
			chkDoClustering.setSelected(value.equals("1"));
		else if(symbol.equals(SYMBOLS[5]))
			chkNoOverlappingBlocks.setSelected(value.equals("1"));
		else if(symbol.equals(SYMBOLS[6]))
			txtBlatArgs.setText(value);
		else if(symbol.equals(SYMBOLS[7]))
			txtNucMerArgs.setText(value);
		else if(symbol.equals(SYMBOLS[8]))
			txtProMerArgs.setText(value);
		else if(symbol.equals(SYMBOLS[3]))
			chkNucOnly.setSelected(value.equals("1"));
		else if(symbol.equals(SYMBOLS[4]))
			chkProOnly.setSelected(value.equals("1"));

	}
	
	public void addKeepListener(ActionListener l) { btnKeep.addActionListener(l); }
	
	private void createMainPanel() {
		lblMinDots = new JLabel(LABELS[0]);
		txtMinDots = new JTextField(NUM_FIELD_WIDTH);
		txtMinDots.setMaximumSize(txtMinDots.getPreferredSize());
		txtMinDots.setMinimumSize(txtMinDots.getPreferredSize());
		
		lblTopN = new JLabel(LABELS[1]);
		txtTopN = new JTextField(NUM_FIELD_WIDTH);
		txtTopN.setMaximumSize(txtTopN.getPreferredSize());
		txtTopN.setMinimumSize(txtTopN.getPreferredSize());

		chkMergeBlocks = new JCheckBox(LABELS[2]);
		chkMergeBlocks.setBackground(Color.WHITE);
		chkMergeBlocks.setMaximumSize(chkMergeBlocks.getPreferredSize());
		chkMergeBlocks.setMinimumSize(chkMergeBlocks.getPreferredSize());
		
		chkDoSynteny = new JCheckBox(LABELS[3]);
		chkDoSynteny.setBackground(Color.WHITE);
		chkDoSynteny.setMaximumSize(chkDoSynteny.getPreferredSize());
		chkDoSynteny.setMinimumSize(chkDoSynteny.getPreferredSize());
		
		chkDoClustering = new JCheckBox(LABELS[4]);
		chkDoClustering.setBackground(Color.WHITE);
		chkDoClustering.setMaximumSize(chkDoClustering.getPreferredSize());
		chkDoClustering.setMinimumSize(chkDoClustering.getPreferredSize());
		
		chkNoOverlappingBlocks = new JCheckBox(LABELS[5]);
		chkNoOverlappingBlocks.setBackground(Color.WHITE);
		chkNoOverlappingBlocks.setMaximumSize(chkNoOverlappingBlocks.getPreferredSize());
		chkNoOverlappingBlocks.setMinimumSize(chkNoOverlappingBlocks.getPreferredSize());
		
		lblBlatArgs = new JLabel(LABELS[6]);
		txtBlatArgs = new JTextField(TEXT_FIELD_WIDTH);
		txtBlatArgs.setMaximumSize(txtBlatArgs.getPreferredSize());
		txtBlatArgs.setMinimumSize(txtBlatArgs.getPreferredSize());
		
		lblNucMerArgs = new JLabel(LABELS[7]);
		txtNucMerArgs = new JTextField(TEXT_FIELD_WIDTH);
		txtNucMerArgs.setMaximumSize(txtNucMerArgs.getPreferredSize());
		txtNucMerArgs.setMinimumSize(txtNucMerArgs.getPreferredSize());
		
		lblProMerArgs = new JLabel(LABELS[8]);
		txtProMerArgs = new JTextField(TEXT_FIELD_WIDTH);
		txtProMerArgs.setMaximumSize(txtProMerArgs.getPreferredSize());
		txtProMerArgs.setMinimumSize(txtProMerArgs.getPreferredSize());

		chkNucOnly = new JCheckBox(LABELS[9]);
		chkNucOnly.setBackground(Color.WHITE);
		chkNucOnly.setMaximumSize(chkNucOnly.getPreferredSize());
		chkNucOnly.setMinimumSize(chkNucOnly.getPreferredSize());

		chkProOnly = new JCheckBox(LABELS[10]);
		chkProOnly.setBackground(Color.WHITE);
		chkProOnly.setMaximumSize(chkProOnly.getPreferredSize());
		chkProOnly.setMinimumSize(chkProOnly.getPreferredSize());
		
		btnKeep = new JButton("Keep");
		btnKeep.setBackground(Color.WHITE);
		btnKeep.addActionListener(keepListener);
		btnKeep.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				setVisible(false);
			}
		});
			
		btnLoad = new JButton("Load Previous");
		btnLoad.setBackground(Color.WHITE);
		btnLoad.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				setDefaults(prevProps);
			}
		});
		if (prevProps == null) btnLoad.setEnabled(false);
		
		btnLoadDef = new JButton("Defaults");
		btnLoadDef.setBackground(Color.WHITE);
		btnLoadDef.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				setDefaults(null);
			}
		});
		
		btnDiscard = new JButton("Discard");
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
		row.add(new JLabel("Alignment & Synteny Parameters"));
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
		
/*		row = createRowPanel();
		row.add(chkDoSynteny);
		mainPanel.add(row);
		mainPanel.add(Box.createVerticalStrut(10));
		
		row = createRowPanel();
		row.add(chkDoClustering);
		mainPanel.add(row);
		mainPanel.add(Box.createVerticalStrut(10));
*/		
		row = createRowPanel();
		row.add(chkNoOverlappingBlocks);
		mainPanel.add(row);
		mainPanel.add(Box.createVerticalStrut(10));

		row = createRowPanel();
		row.add(chkNucOnly);
		mainPanel.add(row);
		mainPanel.add(Box.createVerticalStrut(10));

		row = createRowPanel();
		row.add(chkProOnly);
		mainPanel.add(row);
		mainPanel.add(Box.createVerticalStrut(10));

		row = createRowPanel();
		row.add(lblBlatArgs);
		if(lblBlatArgs.getPreferredSize().width < LABEL_COLUMN_WIDTH)
			row.add(Box.createHorizontalStrut(LABEL_COLUMN_WIDTH - lblBlatArgs.getPreferredSize().width));
		row.add(txtBlatArgs);
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
		row.add(lblProMerArgs);
		if(lblProMerArgs.getPreferredSize().width < LABEL_COLUMN_WIDTH)
			row.add(Box.createHorizontalStrut(LABEL_COLUMN_WIDTH - lblProMerArgs.getPreferredSize().width));
		row.add(txtProMerArgs);
		mainPanel.add(row);	
		mainPanel.add(Box.createVerticalStrut(20));


/*		row = createRowPanel();
		row.add(btnLoad);
		mainPanel.add(row);		
		mainPanel.add(Box.createVerticalStrut(10));*/

		row = createRowPanel();
		
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));
		buttonPanel.setBackground(Color.WHITE);
		buttonPanel.add(btnLoad);
		buttonPanel.add(Box.createHorizontalStrut(20));
		buttonPanel.add(btnLoadDef);
		buttonPanel.add(Box.createHorizontalStrut(20));
		buttonPanel.add(btnKeep);
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
	private JPanel createHelpSection(String label, String descr)
	{
		JPanel row = createRowPanel();
		JLabel lbl = new JLabel(label);
		row.add(lbl);
		row.add(Box.createHorizontalStrut(200 - lbl.getPreferredSize().width));
		row.add(new JLabel("<html>" + descr + "</html>"));
		return row;
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
	
	private JLabel lblProMerArgs = null;
	private JTextField txtProMerArgs = null;
	
	private JButton btnKeep = null;
	private JButton btnDiscard = null;
	private JButton btnLoad = null;
	private JButton btnLoadDef = null;
	private ActionListener keepListener = null;
	private SyProps prevProps = null;
	private Frame parent = null;
	
	private static final String HELP_TITLE = "Alignment & Synteny Parameter Help";
	

	

}
