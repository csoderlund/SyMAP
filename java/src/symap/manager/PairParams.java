package symap.manager;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextField;

import util.Jhtml;
import util.Utilities;

/**********************************************
 * The parameter window for Alignment&Synteny
 * CAS501 massive changes to make it a little clearer what parameters apply to a pair
 * CAS532 finished removing fpc
 * CAS534 changed to use Mpair
 */

public class PairParams extends JDialog {
	private static final long serialVersionUID = 1L;
	
	// CAS533 remove: do_synteny, Do Clustering - dead, No Overlapping Blocks - buggy
	private static final String [] LABELS = { 
		"Min Dots", "Top N", "Merge Blocks", 
		"NUCmer Args", "PROmer Args", "Self Args", "NUCmer Only","PROmer Only" };
	
	// if change, change in Mpair too; defaults are in Mpair
	private static final String [] SYMBOLS = { 
		"mindots", "topn", "merge_blocks", 
		"nucmer_args", "promer_args", "self_args", "nucmer_only","promer_only" };
	
	private static final int LABEL_COLUMN_WIDTH = 100;
	private static final int TEXT_FIELD_WIDTH = 15;
	private static final int NUM_FIELD_WIDTH = 3;
	
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
		if (!checkInt("Min Dots", txtMinDots.getText())) return; // CAS540 add check
		if (!checkInt("Top N", txtTopN.getText())) return;		 // CAS540 add check
		
		HashMap <String, String> fileMap = mp.getFileParams();
		
		for (String key : SYMBOLS) {
			fileMap.put(key, getValue(key));
		}
		mp.writeFile(fileMap);
		setVisible(false);
	}
	private String getValue(String symbol) {
		int x=0;
		if(symbol.equals(SYMBOLS[x++])) 		return txtMinDots.getText();
		else if(symbol.equals(SYMBOLS[x++])) 	return txtTopN.getText();
		else if(symbol.equals(SYMBOLS[x++]))	return chkMergeBlocks.isSelected()?"1":"0";
		else if(symbol.equals(SYMBOLS[x++]))	return txtNucMerArgs.getText();
		else if(symbol.equals(SYMBOLS[x++]))	return txtProMerArgs.getText();
		else if(symbol.equals(SYMBOLS[x++]))	return txtSelfArgs.getText();
		else if(symbol.equals(SYMBOLS[x++]))	return chkNucOnly.isSelected()?"1":"0";
		else if(symbol.equals(SYMBOLS[x++]))	return chkProOnly.isSelected()?"1":"0";
		else return "";
	}
	private boolean checkInt(String label, String x) {
		int i = Utilities.getInt(x);
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
		if(symbol.equals(SYMBOLS[x++]))		    txtMinDots.setText(value);
		else if(symbol.equals(SYMBOLS[x++]))	txtTopN.setText(value);
		else if(symbol.equals(SYMBOLS[x++]))	chkMergeBlocks.setSelected(value.equals("1"));
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

		JButton btnHelp = Jhtml.createHelpIconSysSm(Jhtml.SYS_HELP_URL, Jhtml.param2); // CAS534 change from Help
		
		mainPanel = new JPanel();
		mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.PAGE_AXIS));
		mainPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
		mainPanel.setBackground(Color.WHITE);
		
		mainPanel.add(new JLabel(mp.mProj1.strDisplayName + " & " + mp.mProj2.strDisplayName)); mainPanel.add(Box.createVerticalStrut(5));
		
		// Alignment
		mainPanel.add(createLabel("Alignment")); mainPanel.add(Box.createVerticalStrut(5));
		
		JPanel row = createRowPanel();
		row.add(lblProMerArgs);
		if(lblProMerArgs.getPreferredSize().width < LABEL_COLUMN_WIDTH)
			row.add(Box.createHorizontalStrut(LABEL_COLUMN_WIDTH - lblProMerArgs.getPreferredSize().width));
		row.add(txtProMerArgs);
		mainPanel.add(row);	mainPanel.add(Box.createVerticalStrut(5));
		
		row = createRowPanel();
		row.add(lblNucMerArgs);
		if(lblNucMerArgs.getPreferredSize().width < LABEL_COLUMN_WIDTH)
			row.add(Box.createHorizontalStrut(LABEL_COLUMN_WIDTH - lblNucMerArgs.getPreferredSize().width));
		row.add(txtNucMerArgs);
		mainPanel.add(row);	mainPanel.add(Box.createVerticalStrut(5));
	
		row = createRowPanel();
		row.add(lblSelfArgs);
		if(lblSelfArgs.getPreferredSize().width < LABEL_COLUMN_WIDTH)
			row.add(Box.createHorizontalStrut(LABEL_COLUMN_WIDTH - lblSelfArgs.getPreferredSize().width));
		row.add(txtSelfArgs);
		mainPanel.add(row);	mainPanel.add(Box.createVerticalStrut(5));

		row = createRowPanel();
		row.add(chkProOnly);
		mainPanel.add(row);	mainPanel.add(Box.createHorizontalStrut(20));
		row.add(chkNucOnly);
		mainPanel.add(row);	mainPanel.add(Box.createVerticalStrut(5));

		// Synteny
		mainPanel.add(new JSeparator()); mainPanel.add(Box.createVerticalStrut(5)); // works on linux but not mac
		
		mainPanel.add(createLabel("Synteny")); mainPanel.add(Box.createVerticalStrut(5));
		
		row = createRowPanel();
		row.add(lblMinDots);
		if(lblMinDots.getPreferredSize().width < LABEL_COLUMN_WIDTH)
			row.add(Box.createHorizontalStrut(LABEL_COLUMN_WIDTH - lblMinDots.getPreferredSize().width));
		row.add(txtMinDots);
		mainPanel.add(row);
		mainPanel.add(Box.createVerticalStrut(5));

		row = createRowPanel();
		row.add(lblTopN);
		if(lblTopN.getPreferredSize().width < LABEL_COLUMN_WIDTH)
			row.add(Box.createHorizontalStrut(LABEL_COLUMN_WIDTH - lblTopN.getPreferredSize().width));
		row.add(txtTopN);
		mainPanel.add(row);	mainPanel.add(Box.createVerticalStrut(5));
		
		row = createRowPanel();
		row.add(chkMergeBlocks);
		mainPanel.add(row);	mainPanel.add(Box.createVerticalStrut(5));
		
		mainPanel.add(new JSeparator()); mainPanel.add(Box.createVerticalStrut(5)); 
		
		row = createRowPanel();
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));
		buttonPanel.setBackground(Color.WHITE);
		buttonPanel.add(btnKeep);
		buttonPanel.add(Box.createHorizontalStrut(5));
		/** CAS507 disabled because did not work right
		if (!isGlobal) {
			buttonPanel.add(btnLoadGlobal); 
			buttonPanel.add(Box.createHorizontalStrut(20));
		}
		**/
		buttonPanel.add(btnLoadDef);
		buttonPanel.add(Box.createHorizontalStrut(5));
		buttonPanel.add(btnDiscard);
		buttonPanel.add(Box.createHorizontalStrut(5));
		buttonPanel.add(btnHelp); // CAS532 moved from top to here, like proj_params
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
	
	private JLabel lblMinDots = null;
	private JTextField txtMinDots = null;
	
	private JLabel lblTopN = null;
	private JTextField txtTopN = null;
	
	private JCheckBox chkMergeBlocks = null;
	private JCheckBox chkNucOnly = null;
	private JCheckBox chkProOnly = null;
	
	private JLabel lblNucMerArgs = null;
	private JTextField txtNucMerArgs = null;
	
	private JLabel lblSelfArgs = null;
	private JTextField txtSelfArgs = null;
	
	private JLabel lblProMerArgs = null;
	private JTextField txtProMerArgs = null;
	
	private JButton btnKeep = null, btnDiscard = null, btnLoadDef = null;
	
}
