package toSymap;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.util.Vector;

import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.WindowConstants;

import util.ErrorReport;
import util.Jcomp;
import util.Jhtml;
import util.Utilities;

/******************************************************
 * Interface to run convertNCBI, convertEnsembl, and getLengths
 */
public class ToFrame extends JDialog implements WindowListener {
	private static final long serialVersionUID = 1L;
	private final String rootDir = backend.Constants.seqDataDir; // data/seq/
	private final String convertDir = backend.Constants.seqSeqDataDir; //sequence
	
	protected final int defGapLen = 30000;
	protected final int lj_width = 360;
	
	public ToFrame() {
		addWindowListener(this);
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);	
		setTitle("Input files for SyMAP");
		parent = this;
		
		createMainPanel();
		add(mainPanel);
		
		pack();
		setResizable(false);
		setLocationRelativeTo(null); 
		setVisible(true);
	}
	private void createMainPanel() {
		mainPanel = Jcomp.createPagePanel();
		
		createFileSelect();	 mainPanel.add(new JSeparator());
		
		createConvert(); 	 mainPanel.add(new JSeparator());
    	
    	createButtonPanel();
    	
    	setDisable();
	}
	private void createFileSelect() {
		JPanel panel = Jcomp.createPagePanel();
		
		JPanel trow = Jcomp.createRowPanel();
		JLabel lbl = Jcomp.createLabel("Project directory");
		trow.add(lbl);
		
		txtDir = Jcomp.createTextField(rootDir, 20);
		trow.add(txtDir);
		
		JButton btnGetFile = Jcomp.createButton("...", "Select the project directory");
		btnGetFile.addActionListener(new ActionListener()  {
			public void actionPerformed(ActionEvent arg0) {
				String fname = fileChooser();
				if (fname!=null && fname!="") {
					txtDir.setText(fname);
					
					setDisable();
					
				}
			}
		});
		trow.add(btnGetFile);
		
		panel.add(trow);   panel.add(Box.createVerticalStrut(5));
		
		JPanel crow = Jcomp.createRowPanel();
		btnSummary = Jcomp.createBoldButton("Summarize", 
				"Print/log information about FASTA and GFF files");
		btnSummary.addActionListener(new ActionListener()  {
			public void actionPerformed(ActionEvent arg0) {	// XXX
				String dir = txtDir.getText().trim();
				if (dir.equals(rootDir) || dir.isEmpty()) {
					Utilities.showInfoMessage("Project", "Please select a project directory");
					return;
				}
				boolean b = chkSumConvert.isSelected() && chkSumConvert.isEnabled();
				new Summary(dir).runCheck(chkSumVerbose.isSelected(), b);
			}
		});
		crow.add(btnSummary); crow.add(Box.createHorizontalStrut(10));
		
		chkSumVerbose = Jcomp.createCheckBox("Verbose", "Write extra information to the terminal/log", false);
		crow.add(chkSumVerbose); crow.add(Box.createHorizontalStrut(10));
		
		chkSumConvert = Jcomp.createCheckBox("Converted", "After Convert; use the /sequence and /annotation files", false);
		crow.add(chkSumConvert); crow.add(Box.createHorizontalStrut(10));
		
		panel.add(crow);
		
		mainPanel.add(panel);
	}
	private void createConvert() {
		JPanel panel = Jcomp.createPagePanel();
		
		createParamsBoth(panel);  panel.add(Box.createVerticalStrut(20));
    	
    	createParamsShared(panel);   panel.add(Box.createVerticalStrut(20));
    	
        JPanel row = Jcomp.createRowPanel();
        btnExec = Jcomp.createBoldButton("Convert", "Covert FASTA and GFF to SyMAP input");
		btnExec.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				convert();
			}
		});
		row.add(btnExec);	row.add(Box.createHorizontalStrut(5));
		
		lblExec = new JLabel("NCBI files");
		row.add(lblExec);	
		
		panel.add(row);
		mainPanel.add(panel);
	}
	private void createParamsBoth(JPanel cpanel) {
		JPanel row0 = Jcomp.createRowPanel();
		radNCBI = Jcomp.createRadio("NCBI", "When Convert is selected, the NCBI conversion will be run.");
		radNCBI.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				radDisable();
			}
		});
		row0.add(radNCBI); 
		if (lj_width > radNCBI.getPreferredSize().width) 
			row0.add(Box.createHorizontalStrut(lj_width-radNCBI.getPreferredSize().width));
		
		JButton help1 = Jcomp.createIconButton("/images/info.png", "NCBI Quick Help");
		help1.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				helpNCBI();
			}
		});
		//row0.add(help1, BorderLayout.EAST);
		cpanel.add(row0); cpanel.add(Box.createVerticalStrut(5));
		
		JPanel row1 = Jcomp.createRowPanel(); row1.add(Box.createHorizontalStrut(15));
		chkNmask = Jcomp.createCheckBox("Hard mask", "Hard mask the sequence when creating the new sequence file.", false);
		chkNp = Jcomp.createCheckBox("1st protein",  "Create a new gene attribute: protein=1st CDS ID", false);
		chkNp.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				if (chkNp.isSelected()) chkNpa.setSelected(false);
			}
		});
		chkNpa = Jcomp.createCheckBox("All proteins","Create a new gene attribute: protein=1st CDS ID, 2nd CDS ID....",false);
		chkNpa.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				if (chkNpa.isSelected()) chkNp.setSelected(false);
			}
		});
		row1.add(chkNmask);    row1.add(Box.createHorizontalStrut(5));
		row1.add(chkNp);    row1.add(Box.createHorizontalStrut(5));
		row1.add(chkNpa);   row1.add(Box.createHorizontalStrut(5)); 
		cpanel.add(row1); cpanel.add(Box.createVerticalStrut(5));
		
		////
		JPanel row2 = Jcomp.createRowPanel();
		radEns = Jcomp.createRadio("Ensembl", "When Convert is selected, the Ensembl conversion will be run.");
		radEns.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				radDisable();
			}
		});
		row2.add(radEns); 
		if (lj_width > radEns.getPreferredSize().width) 
			row2.add(Box.createHorizontalStrut(lj_width-radEns.getPreferredSize().width));
		JButton help2 = Jcomp.createIconButton("/images/info.png", "Ensembl Quick Help");
		help2.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				helpEns();
			}
		});
		//row2.add(help2);
		
		cpanel.add(row2); cpanel.add(Box.createVerticalStrut(5));
		
		JPanel row3 = Jcomp.createRowPanel(); row3.add(Box.createHorizontalStrut(15));
		chkEonlyNum = Jcomp.createCheckBox("Only #, X, Y, I", "Only output sequences identified with a number, X, Y, or Roman numerals", false);
		row3.add(chkEonlyNum);
		
		cpanel.add(row3); cpanel.add(Box.createVerticalStrut(5));
		
		ButtonGroup grp = new ButtonGroup();
		grp.add(radNCBI);
        grp.add(radEns); 
        
		radNCBI.setSelected(true);
	}
	private void createParamsShared(JPanel cpanel) {
		JPanel row0 = Jcomp.createRowPanel();
		
		lblShare = Jcomp.createLabel("NCBI and Ensembl", "Options for both");
		row0.add(lblShare);
		cpanel.add(row0); cpanel.add(Box.createVerticalStrut(5));
		
		JPanel row1 = Jcomp.createRowPanel(); row1.add(Box.createHorizontalStrut(15));
		lblInclude = Jcomp.createLabel("Include", "Include scaffolds or Mt/Pt if checked.");
		row1.add(lblInclude); row1.add(Box.createHorizontalStrut(2));
		chkScaf = Jcomp.createCheckBox("Scaffolds", "Include scaffolds in the output", false);
		chkMtPt = Jcomp.createCheckBox("Mt/Pt", "Include Mt/Pt in the output", false);
		row1.add(chkScaf); row1.add(Box.createHorizontalStrut(1));
		row1.add(chkMtPt); row1.add(Box.createHorizontalStrut(12));
		
		chkOnlyPrefix = Jcomp.createCheckBox("Only prefix", "Only output sequences with this prefix", false);
		chkOnlyPrefix.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				boolean b = chkOnlyPrefix.isSelected();
				txtPrefix.setEnabled(b);
			}
		});
		txtPrefix = Jcomp.createTextField("", 5);
		txtPrefix.setEnabled(false);
		row1.add(chkOnlyPrefix); row1.add(Box.createHorizontalStrut(12));
		row1.add(txtPrefix);
		
		cpanel.add(row1); cpanel.add(Box.createVerticalStrut(5));
		
		JPanel row2 = Jcomp.createRowPanel(); row2.add(Box.createHorizontalStrut(20));
		lblGap = Jcomp.createLabel("Gap size >=", "Consecutive n's of this length will be identified in the gap.gff file."); 
		row2.add(lblGap);  row2.add(Box.createHorizontalStrut(2)); 
		
		txtGap = Jcomp.createTextField(defGapLen+"", 5);
		row2.add(txtGap); row2.add(Box.createHorizontalStrut(5)); 
		
		chkVerbose = Jcomp.createCheckBox("Verbose", "Write extra information to the terminal/log", false);
		row2.add(chkVerbose);
		
		cpanel.add(row2);
	}
	
	private void createButtonPanel() {
		JPanel panel = Jcomp.createPagePanel();
		
		JPanel row = Jcomp.createRowPanel();
		
		btnLen = Jcomp.createBoldButton("Lengths", "Output to terminal the sequence lengths and summary");
		btnLen.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) { // XXX
				String dir = txtDir.getText().trim();
				if (dir.equals(rootDir) || dir.isEmpty()) {
					Utilities.showInfoMessage("Project", "Please select a project directory");
					return;
				}
				new Lengths(dir);
			}
		});
		row.add(btnLen);	row.add(Box.createHorizontalStrut(100));	
		
		btnExit = Jcomp.createButton("Exit", "Exit xToSyMAP");;
		btnExit.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)  {
				setVisible(false);
				System.exit(0);
			}
		});
		row.add(btnExit);		row.add(Box.createHorizontalStrut(20));
		
		JButton btnHelp = Jhtml.createHelpIconSysSm(Jhtml.CONVERT_GUIDE_URL, ""); 
		row.add(btnHelp);
		
		panel.add(row);
		panel.add(Box.createVerticalStrut(10));
		
		mainPanel.add(panel);
	}
	/*******************************************************/
	private void convert() {
		String dir = txtDir.getText().trim();
		if (dir.equals(rootDir) || dir.isEmpty()) {
			Utilities.showInfoMessage("Project", "Please select a project directory");
			return;
		}
		Vector <String> args = new Vector <String> ();
		args.add(dir);
		
		String g = txtGap.getText().trim();
		int gapLen=30000;
		try {
			gapLen = Integer.parseInt(g);
			if (gapLen<100) {
				Utilities.showInfoMessage("Gap size", "Gap size must at least be 100, i.e. " + g +" is too small. Using 100.");
				g = "100";
				gapLen=100;
				return;
			}
		}
		catch (Exception e) {
			Utilities.showInfoMessage("Gap size", "Gap size must be an integer.");
			return;
		}
		
		if (chkOnlyPrefix.isSelected() && txtPrefix.getText().trim().isEmpty()) {
			Utilities.showInfoMessage("Only prefix", "When 'Only prefix' is checked, a prefix must be entered.");
			return;
		}
		String prefix = (chkOnlyPrefix.isSelected()) ? txtPrefix.getText().trim() : null;
		
		args.add(g);
		if (chkVerbose.isSelected()) args.add("-v");
		if (chkScaf.isSelected())    args.add("-s");
		if (chkMtPt.isSelected())    args.add("-t");
		
		if (radNCBI.isSelected()) {
			if (chkNmask.isSelected())   args.add("-m");
			if (chkNp.isSelected())      args.add("-p");
			if (chkNpa.isSelected())     args.add("-pa");
			
			String [] s = args.toArray(new String [args.size()]);
			new ConvertNCBI(s, gapLen, prefix);
		}	
		else {
			if (chkEonlyNum.isSelected() && prefix!=null) {
				Utilities.showInfoMessage("Only options", 
						 "Select either 'Only #, X, Y, I' or 'Only prefix', not both.");
				return;
			}
			String [] s = args.toArray(new String [args.size()]);
			new ConvertEnsembl(s, gapLen, prefix, chkEonlyNum.isSelected());
		}
		setDisable();
	}

	/********************************************************/
	
	private String fileChooser() {
		try {
			final JFileChooser fc = new JFileChooser();
			fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			fc.setCurrentDirectory(new File(rootDir));
			if(fc.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
				String filePath = fc.getSelectedFile().getCanonicalPath();
				String cur = System.getProperty("user.dir");
				
				if(filePath.contains(cur)) filePath = filePath.replace(cur, ".");
				
				return filePath;
			}
		}
		catch (Exception e) {ErrorReport.print(e, "Problems getting file");}
		return null;
	}
	
	public void windowClosed(WindowEvent arg0) {System.exit(0);}
	public void windowActivated(WindowEvent arg0) {}
	public void windowClosing(WindowEvent arg0) {}
	public void windowDeactivated(WindowEvent arg0) {}
	public void windowDeiconified(WindowEvent arg0) {}
	public void windowIconified(WindowEvent arg0) {}
	public void windowOpened(WindowEvent arg0) {}
	
	/***************************************************/
	private void helpNCBI() {
		String msg="";
		util.Utilities.displayInfoMonoSpace(this, "NCBI Quick Help", msg, false);
	}
	private void helpEns() {
		String msg="";
		util.Utilities.displayInfoMonoSpace(this, "Ensembl Quick Help", msg, false);
	}
	private void radDisable() {
		boolean bNCBI = radNCBI.isSelected();
		
		if (bNCBI) lblExec.setText("NCBI files");
		else       lblExec.setText("Ensembl files");
		
		chkNmask.setEnabled(bNCBI);  
		chkNp.setEnabled(bNCBI); chkNpa.setEnabled(bNCBI); 
		chkEonlyNum.setEnabled(!bNCBI);
	}
	private void setDisable() {
		String fname = txtDir.getText().trim();
		boolean b = !(fname.equals(rootDir));
		btnSummary.setEnabled(b); chkSumVerbose.setEnabled(b); chkSumConvert.setEnabled(b);
		 
		radNCBI.setEnabled(b); chkNp.setEnabled(b); chkNpa.setEnabled(b); chkNmask.setEnabled(b);
		radEns.setEnabled(b);  chkEonlyNum.setEnabled(b);
		
		lblShare.setEnabled(b); lblInclude.setEnabled(b); chkMtPt.setEnabled(b);
 		chkScaf.setEnabled(b); chkVerbose.setEnabled(b); chkOnlyPrefix.setEnabled(b);
		lblGap.setEnabled(b);  txtGap.setEnabled(b);
		
		lblExec.setEnabled(b); btnExec.setEnabled(b); btnLen.setEnabled(b);
		
		if (b) { // these two need /sequence directory
			b = new File(fname + convertDir).exists();
			chkSumConvert.setEnabled(b); if (!b) chkSumConvert.setSelected(false);
			btnLen.setEnabled(b);
		}
	}
	/*************************************************/
	private JPanel mainPanel = null;
	private JTextField txtDir = null;
	private JButton btnSummary = null;
	private JCheckBox chkSumVerbose = null, chkSumConvert = null;
	
	private JRadioButton radNCBI = null;
	private JCheckBox chkNmask = null, chkNp = null, chkNpa = null;
	
	private JRadioButton radEns = null;
	private JCheckBox chkEonlyNum = null;
	
	// shared
	private JLabel lblShare = null, lblInclude = null;
	private JCheckBox chkScaf = null,  chkMtPt = null, chkVerbose = null;
	private JCheckBox chkOnlyPrefix = null;
	private JLabel lblGap = null;
	private JTextField txtGap = null, txtPrefix = null;
	
	private JLabel lblExec = null ;
	private JButton btnExec = null;
	
	private JButton btnLen = null, btnExit = null;

	private ToFrame parent;
}
