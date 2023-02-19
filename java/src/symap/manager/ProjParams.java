package symap.manager;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;

import backend.Constants;
import util.ErrorReport;
import util.Jcomp;
import util.Utilities;
import util.Jhtml;

/*******************************************
 * Displays the project parameter window and saves them to file 
 * 
 * CAS513 reorder a bunch and some renaming; added grp_prefix update
 * CAS522 removed FPC
 * CAS534 rewrote to work with manager.Project and make all parameter code shared between these 2 files.
 */
public class ProjParams extends JDialog {
	private static final long serialVersionUID = -8007681805846455592L;
	public static String lastSeqDir=null; // CAS500
	
	private final String [] MASK_GENES = { "yes", "no" };
	private final int INITIAL_WIDTH = 610, INITIAL_HEIGHT = 660;
	
	private final String selectedHead = 	"Display";
	private final String loadProjectHead =  "Load project";
	private final String loadAnnoHead = 	"Load annotation";
	private final String alignHead = 		"Alignment&Synteny";
	
	private int idxGrpPrefix=0; 
	private Mproject mProj;
	
	public ProjParams(Frame parentFrame,  Mproject mProj, 
			Vector <Mproject> projVec, boolean isLoaded, boolean existAlign) {
		
		super(parentFrame, mProj.getDBName() + " params file");
		
		this.mProj = mProj;
		
		theDisplayName =	mProj.getDisplayName();
		theDBName =  		mProj.getDBName();
		bIsLoaded = 		isLoaded;
		bExistAlign = 		existAlign;
		theProjectNames = 	getAddNoneSelections(projVec);
		
		getContentPane().setLayout(new BoxLayout(getContentPane(), BoxLayout.PAGE_AXIS));
		getContentPane().setBackground(Color.WHITE);
		
		theListener = new CaretListener() {
			public void caretUpdate(CaretEvent arg0) {
				// nothing right now
			}
		};
		createMainPanel();
		setFieldsFromProj();
		
		setInitValues(); // Save initial values to see if they change
		
		setSize(INITIAL_WIDTH, INITIAL_HEIGHT);
		setMinimumSize(new Dimension(INITIAL_WIDTH, INITIAL_HEIGHT));
		
		setModal(true);
		
		setLocationRelativeTo(parentFrame); // CAS513 put on top of parent
	}
	public boolean wasSave() {return bWasSave;}
	
	private ProjParams getInstance() { return this; }
	
	/*******************************************************
	 * If any parameters are changed, change also in manager.Project and SyProps
	 */
	private void createMainPanel() {
		int startAnno=0, startLoad=0, startAlign=0, i=0;
		boolean bReload=true, bReAlign=true;
		
		theFields = new Field[13]; // Change if change fields!
		theFields[i++] = new Field(mProj.sCategory, 1, 	!bReload, !bReAlign);
		theFields[i++] = new Field(mProj.sDisplay, 1, 	!bReload, !bReAlign);
		theFields[i++] = new Field(mProj.sAbbrev, 1, 	!bReload, !bReAlign); // CAS519 add for SyMAP Query
		theFields[i++] = new Field(mProj.sGrpType, 1, 	!bReload, !bReAlign);
		theFields[i++] = new Field(mProj.sDesc, 2, 		!bReload, !bReAlign);	
		// theFields[i++] = new Field(mProj.sDPsize, 1, 	!bReload, !bReAlign);	// CAS534 isn't working
		theFields[i++] = new Field(mProj.sANkeyCnt,  1, !bReload, !bReAlign);	// CAS513 2 lines->1, moved
		
		startLoad = i-1;
		idxGrpPrefix = i; // special case
		theFields[i++] = new Field(mProj.lGrpPrefix,  1, !bReload, !bReAlign); // has its own message
		theFields[i++] = new Field(mProj.lMinLen, 1, 	  bReload,  bReAlign); 
		theFields[i++] = new Field(mProj.lSeqFile,		  bReload,  bReAlign, false);
		
		startAnno=i-1; 
		theFields[i++] = new Field(mProj.lANkeyword, 1,  bReload, !bReAlign);  // CAS512 was false for Reload		
		theFields[i++] = new Field(mProj.lAnnoFile, 	 bReload, !bReAlign, false);
		
		startAlign = i-1;
		theFields[i++] = new Field(mProj.aOrderAgainst, theProjectNames, 0,  !bReload, bReAlign);
		theFields[i++] = new Field(mProj.aMaskNonGenes, MASK_GENES, 1,!bReload, bReAlign);
		
		
		JPanel fieldPanel = new JPanel();
		fieldPanel.setLayout(new BoxLayout(fieldPanel, BoxLayout.PAGE_AXIS));
		fieldPanel.setBackground(Color.WHITE);
		fieldPanel.add(createLabel(selectedHead)); 
	
		for(int x=0; x<theFields.length; x++) {
			fieldPanel.add(Box.createVerticalStrut(5));
			theFields[x].setTextListener(theListener);
			fieldPanel.add(theFields[x]);
			
			if (x==startAnno) {
				fieldPanel.add(Box.createVerticalStrut(5));
				fieldPanel.add(new JSeparator()); // works on linux but not mac; but the createLabel is better on Mac
				fieldPanel.add(Box.createVerticalStrut(5));
				fieldPanel.add(createLabel(loadAnnoHead));
			}
			else if (x==startLoad) {
				fieldPanel.add(Box.createVerticalStrut(5));
				fieldPanel.add(new JSeparator()); // works on linux but not mac; needs to be on mainPanel
				fieldPanel.add(Box.createVerticalStrut(5));
				fieldPanel.add(createLabel(loadProjectHead));
			}
			else if (x==startAlign) {
				fieldPanel.add(Box.createVerticalStrut(5));
				fieldPanel.add(new JSeparator());
				fieldPanel.add(Box.createVerticalStrut(5));
				fieldPanel.add(createLabel(alignHead));
			}
		}
		
		JPanel mainPanel = new JPanel();
		mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.PAGE_AXIS));
		mainPanel.setBackground(Color.WHITE);
		
		mainPanel.add(fieldPanel);
		mainPanel.add(Box.createVerticalStrut(10));
		
		JPanel buttons = createButtonPanel();
		mainPanel.add(buttons);
		
		mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		mainPanel.setMaximumSize(fieldPanel.getPreferredSize());
		mainPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
			
		getContentPane().add(mainPanel);
	}
	/*******************************************************************/
	private String [] getAddNoneSelections(Vector <Mproject> projVec) { // adds None
		Vector<String> retVal = new Vector<String> ();
		
		retVal.add("None");
		
		for (Mproject mp : projVec) {
			if (mp!=mProj && mp.isLoaded()) retVal.add(mp.getDBName());
		}
		
		return retVal.toArray(new String[retVal.size()]);
	}
	private JLabel createLabel(String text) {
		String html = "<html><b><i>" + text + "</i></b></html";
		JLabel l = new JLabel(html);
		return l;
	}
	/******************************************************************/
	private JPanel createButtonPanel() {
		btnSave = new JButton("Save");
		btnSave.setBackground(Color.WHITE);
		btnSave.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				bWasSave=true;
				save();
			}
		});
		btnCancel = new JButton("Cancel");
		btnCancel.setBackground(Color.WHITE);
		btnCancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)  {
				bWasSave = false;
				closeDialog();
			}
		});
		
		JButton btnHelp = Jhtml.createHelpIconSysSm(Jhtml.SYS_HELP_URL, Jhtml.param1); // CAS534 change from Help
		
		JPanel buttonPanel = Jcomp.createRowPanel();
		
		buttonPanel.add(btnSave);		buttonPanel.add(Box.createHorizontalStrut(10));
		buttonPanel.add(btnCancel);		buttonPanel.add(Box.createHorizontalStrut(20));
		buttonPanel.add(btnHelp);
		
		buttonPanel.setBackground(Color.WHITE);
		buttonPanel.setMaximumSize(buttonPanel.getPreferredSize());
		buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		
		JPanel row = Jcomp.createRowPanel();
		row.add(Box.createHorizontalGlue());
		row.add(buttonPanel);
		row.add(Box.createHorizontalGlue());

		return row;
	}
	
	/**********************************************************************
	 * Get/Set fields
	 ***************************************************************/
	
	private void setFieldsFromProj() {
		HashMap <String, String> pMap = mProj.getParams();
		for (String label : pMap.keySet()) {
			setField(label, pMap.get(label));
		}
	}
	private boolean setField(String fieldName, String fieldValue) {
		for (int x=0; x<theFields.length; x++) {
			if (fieldName.equals(theFields[x].getLabel())) {
				if (fieldName.equals(mProj.getLab(mProj.aMaskNonGenes))) {
					if(fieldValue.equals("1") || fieldValue.equals("yes")) 	fieldValue = "yes";
					else													fieldValue = "no";
				}
				theFields[x].setValue(fieldValue);
				return true;
			}
		}
		return false;
	}
	
	// The Field values after they have been set from DB or file
	private void setInitValues() {
		prevArr = new String[theFields.length];
		
		for(int x=0; x<prevArr.length; x++)
			prevArr[x] = theFields[x].getValue();
	}
	
	/***************************************************************
	 * XXX Save
	 * CAS513 changed the logic on this part and added ChgGrpPrefix
	 */
	
	private void save() {
		if (!Utilities.dirExists(Constants.dataDir)) {
			Utilities.showWarningMessage("The /data directory does not exist, cannot save parameters.");
			return;
		}
		
		if (!saveCheckFields()) return;
		
		if (!saveIsRequired())	return;
		
		if (!saveGrpPrefix())	return;
		
		writeParamsFile();	
		
		mProj.loadParamsFromDisk();
		mProj.saveParams(mProj.xDisplay);
		
		if (bChgGrpPrefix) { // CAS513 add the ability to change GrpPrefix after load
			mProj.updateGrpPrefix();
		}
	
		closeDialog();
	}
	private boolean saveCheckFields() {
	try {
		String msg=null;
		
		for(int x=0; x<theFields.length; x++) {
			String val = theFields[x].getValue();
			String lab = theFields[x].getLabel();
			int len = val.length();
			
			// check for values that must exist
			if(lab.equals(mProj.getLab(mProj.sDisplay))) {	
				if (len == 0) msg = lab + " must have a value.";
			}
			else if (lab.equals(mProj.getLab(mProj.sGrpType))) {
				if (len == 0) msg = lab + " must have a value.";
			}
			
			// check for integers
			else if (lab.equals(mProj.getLab(mProj.lMinLen))) {
				msg = checkInt(lab, val);
			}
			else if (lab.equals(mProj.getLab(mProj.sDPsize))) {
				msg = checkInt(lab, val);
			}
			else if (lab.equals(mProj.getLab(mProj.sANkeyCnt))) {
				msg = checkInt(lab, val);
			}
			
			// abbrev
			else if (lab.equals(mProj.getLab(mProj.sAbbrev))) {
				if (len!=4) msg = lab + " must be exactly 4 characters.";
			}
			
			if (msg!=null) {
				Utilities.showWarningMessage(msg);
				return false;
			}
		}
		return true;
	}
	catch (Exception e) {ErrorReport.print(e, "Checking project parameters"); return false;}
	}
	private String checkInt(String lab, String ix) {
		try {
			int x = Integer.parseInt(ix);
			if (x<0) return lab + " must be >0.";
			return null; 
		}
		catch (Exception e) {}
		
		try {
			long x = Long.parseLong(ix);
			return lab + " value '" + x + "' is too large, maximum is " + Integer.MAX_VALUE ;
		}
		catch (Exception e) {return lab + " value '" + ix + "' is not an integer.";}
	}
	 /** Check parameters for change; note that bExistAlign does not mean with project to be aligned with**/
	private boolean saveIsRequired() {
	try {
		if (!bIsLoaded) return true;  // write to file only
	
		for(int x=0; x<prevArr.length; x++) {
			if (theFields[x].getValue().equals(prevArr[x])) continue;
			
			if (theFields[x].isLoadField() && theFields[x].isAlignField()) { // project
				String m1 = (bExistAlign) 
						? "Reload Project (remove existing alignments) and run A&S."
						: "Reload Project";
				String msg = "Changed: " + theFields[x].getLabel() 
						+ "\n\n" + m1 
						+ "\n\nProceed with save?";
				int n = JOptionPane.showConfirmDialog( this, msg , loadProjectHead, JOptionPane.YES_NO_OPTION);
				
				return (n == JOptionPane.YES_OPTION);
			}
			
			if (theFields[x].isLoadField()) { // anno
				String m1 = (bExistAlign) 
						? "Reload Annotation and rerun A&S (existing alignments will be used)."
						: "Reload Annotation";
				String msg =  "Changed parameter: " + theFields[x].getLabel() 
						 + "\n\n" + m1 + "\n\nProceed with save?";
				int n = JOptionPane.showConfirmDialog( this, msg , loadAnnoHead, JOptionPane.YES_NO_OPTION);
				
				return (n == JOptionPane.YES_OPTION);
			}
			
			if  (theFields[x].isAlignField() && bExistAlign) { // A&S
				String label = mProj.getLab(mProj.aOrderAgainst);
				boolean b1 = (theFields[x].getLabel().contentEquals(label));
				String m1 = (b1) 
						? "If alignments exist with the select project, they may be reused for A&S"
						: "Clear Pair (remove existing alignments) and rerun A&S";
				String msg = "Changed parameter: " + theFields[x].getLabel() 
						+ "\n\n" + m1 + "\n\nProceed with save?";
				int n = JOptionPane.showConfirmDialog( this, msg , alignHead, JOptionPane.YES_NO_OPTION);
				
				return (n == JOptionPane.YES_OPTION);
			}
		}
		return true;
	}
	catch (Exception e) {ErrorReport.print(e, "Checking project parameters required"); return false;}
	}
	
	 /** Grp Prefix **/
	private boolean saveGrpPrefix() { 
		bChgGrpPrefix = false;
		String val = theFields[idxGrpPrefix].getValue();
		
		if (val.equals(prevArr[idxGrpPrefix])) 	return true;
		if (!bIsLoaded) 						return true; 
		
		if (val.contentEquals("")) {
			String msg ="Group prefix set to blank has no effect on a loaded project. "
					+ "\nOn a reload, all input prefixes will be retained.";
			int n = JOptionPane.showConfirmDialog( this, msg, "", JOptionPane.YES_NO_OPTION);
			
			if (n == JOptionPane.YES_OPTION) return true;
			else return false;
		}
		
		String msg = "The Group prefix '" + val +"' will be removed from all group display labels."
				+ "\n   This is not reversible (i.e. it cannot be added back). "
				+ "\n   Note, if you are going to Reload, ignore this message."
				+ "\n\nProceed with save?";
		int n = JOptionPane.showConfirmDialog( this, msg, "", JOptionPane.YES_NO_OPTION);
		
		if (n == JOptionPane.YES_OPTION) {
			bChgGrpPrefix=true;
			return true;
		}
		return false;
	}
	
	/*******************************************************
	 * Write Params file
	 * 	the params file is read in manager.Mproject
	 * 	if the sequence files are not in the /data directory, 
	 * 		the /data/seq/<p>/params is created anyway for the parameters
	 */
	private void writeParamsFile() {	
		String dir = Constants.seqDataDir + theDBName;
		Utilities.checkCreateDir(dir, true); // CAS500 
		
		File pfile = new File(dir,Constants.paramsFile);
		if (!pfile.exists()) // should not happen here because written on startup in Mproject
			System.out.println("Create parameter file " + dir + Constants.paramsFile);
		
		try {
			PrintWriter out = new PrintWriter(pfile);
			
			out.println("#");
			out.println("#  " + theDisplayName + " project parameter file");
			out.println("#  Note: changes MUST be made in SyMAP parameter window");
			
			for(int x=0; x<theFields.length; x++) {
				String val = theFields[x].getValue();
				if (val.equals("null")) val=""; // CAS500 this happens for anno_file
				String label  = mProj.getParam(theFields[x].getLabel());
				if (label==null) {
					System.out.println("SyMAP error on label: " + label);
					continue;
				}
				if(val.length() > 0) {
					if (label.equals(mProj.getKey(mProj.aMaskNonGenes))) {
						if(val.equals("yes")) 	out.println(label + " = 1");
						else					out.println(label + " = 0");
					}
					else if (label.equals(mProj.getKey(mProj.aOrderAgainst))) {
						if (!val.equals("None")) out.println(label + " = " + val);
						else out.println(label + " = ");
					}
					else {
						String v = val.replace('\n', ' ');
						v = v.replace("\"", " ");
						v = v.replace("\'", " ");
						v = v.trim();
						out.println(label + " = " + v);
					}
				}
				else {
					out.println(label + " = "); // save empty ones too
				}
			}
			out.close();
		} catch(Exception e) {ErrorReport.print(e, "Creating params file");}
	}
	
	private void closeDialog() {
		dispose();
	}
	
	/***************************************************************/
	public class Field extends JPanel{
		private static final long serialVersionUID = -6643140570421332451L;
		
		private static final int MODE_TEXT = 0, MODE_COMBO = 1, MODE_MULTI = 2, MODE_FILE = 3;
		
		private static final int LABEL_WIDTH = 150;
		private static final int FIELD_COLUMNS = 10;
		
		//Used for a Add file/directory
		private Field(int index, boolean needsReload, boolean needsRealign, boolean b) {
			String label = mProj.getLab(index);
			
			bdoReload = needsReload;
			bdoRealign = needsRealign;
			
			nMode = MODE_FILE;
			
			theComponent = new FileTable(b);
			theLabel = new JLabel(label);
			theLabel.setBackground(Color.WHITE);

			setLayout();
		}
		//Used for plain text properties
		private Field(int index, int numLines, boolean needsReload, boolean needsRealign) {
			String label = mProj.getLab(index);
			String initValue = mProj.getDef(index);
			
			bdoReload = needsReload;
			bdoRealign = needsRealign;
			
			if(numLines == 1) {
				nMode = MODE_TEXT;
				theComponent = new JTextField(FIELD_COLUMNS);
				((JTextField)theComponent).setBorder(BorderFactory.createLineBorder(Color.BLACK));
			}
			else {
				nMode = MODE_MULTI;
				theComponent = new JTextArea(numLines, FIELD_COLUMNS);
				((JTextArea)theComponent).setBorder(BorderFactory.createLineBorder(Color.BLACK));
				((JTextArea)theComponent).setLineWrap(true);
				((JTextArea)theComponent).setWrapStyleWord(true);
				((JTextArea)theComponent).setMinimumSize(((JTextArea)theComponent).getPreferredSize());
			}
			theLabel = new JLabel(label);
			theLabel.setBackground(Color.WHITE);
			setValue(initValue);
			setLayout();
		} 
		
		//Used for properties that require a combo box
		private Field(int index, String [] options, int selection, boolean needsReload, boolean needsRealign) {
			String label = mProj.getLab(index);
			
			bdoReload = needsReload;
			bdoRealign = needsRealign;
			
			nMode = MODE_COMBO;
			comboBox = new JComboBox <String> (options);
			comboBox.setSelectedIndex(selection);
			comboBox.setBackground(Color.WHITE);
			theLabel = new JLabel(label);
			theLabel.setBackground(Color.WHITE);
			theComponent = (JComboBox <String>) comboBox;
			setLayout();
		}
		
		private String getLabel() { return theLabel.getText(); }
		private String getValue() {
			if( nMode == MODE_TEXT)  return ((JTextField)theComponent).getText();
			if (nMode == MODE_MULTI) return ((JTextArea) theComponent).getText();
			if (nMode == MODE_FILE)  return ((FileTable) theComponent).getValue();
			if (nMode == MODE_COMBO) return (String) comboBox.getSelectedItem();
			return null;
		}
		
		private void setValue(String value) {
			if(nMode == MODE_TEXT)       ((JTextField)theComponent).setText(value);
			else if(nMode == MODE_MULTI) ((JTextArea) theComponent).setText(value);
			else if(nMode == MODE_FILE)  ((FileTable) theComponent).setValue(value);
			else { //MODE_COMBO
				boolean found=false;
				for (int i=0; i<comboBox.getItemCount(); i++) {
					String val = comboBox.getItemAt(i);
					if (val.equalsIgnoreCase(value)) {
						comboBox.setSelectedIndex(i);
						found = true;
						break;
					}
				}
				if (!found && !value.contentEquals("")) // it has to be order against, as its that or yes/no
					Utilities.showWarningMessage("Order against '" + value + "' is not a loaded project");
			}
		}
		
	
		private boolean isAlignField() { return bdoRealign; }
		private boolean isLoadField()  { return bdoReload; }
		
		private void setTextListener(CaretListener l) {
			if(theComponent instanceof JTextField) {
				((JTextField)theComponent).addCaretListener(l);
			}
			else if(theComponent instanceof JTextArea) {
				((JTextArea)theComponent).addCaretListener(l);
			}
		}
		
		private void setLayout() {
			removeAll();
			setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
			setAlignmentX(Component.LEFT_ALIGNMENT);
			setBackground(Color.WHITE);
			
			theLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			theComponent.setAlignmentX(Component.LEFT_ALIGNMENT);
			
			Dimension d = theLabel.getPreferredSize();
			
			add(theLabel);
			if(LABEL_WIDTH - d.width > 0)
				add(Box.createHorizontalStrut(LABEL_WIDTH - d.width));
			add(theComponent);
		}
		
		private JComponent theComponent = null;
		private JComboBox <String> comboBox = null; // CAS534 added to get rid of comboBox warnings
		private JLabel theLabel = null;
		private boolean bdoReload = false, bdoRealign = false;
		// private boolean isDefault = true; CAS534 this was always false on getValue
		private int nMode = -1;
	} // end Fields class
	
	/*******************************************************/
	private class FileTable extends JPanel {
		private static final long serialVersionUID = 1L;

		public FileTable(boolean singleSelect) {
			bSingleSelect = singleSelect;
			
			theTable = new JTable();
	    	theModel = new FileTableModel();
	    	
	        theTable.setAutoCreateColumnsFromModel( true );
	        theTable.setColumnSelectionAllowed( false );
	        theTable.setCellSelectionEnabled( false );
	        theTable.setRowSelectionAllowed( true );
	        theTable.setShowHorizontalLines( false );
	        theTable.setShowVerticalLines( true );	
	        theTable.setIntercellSpacing ( new Dimension ( 1, 0 ) );
	        theTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
	        theTable.setOpaque(true);

	        theTable.setModel(theModel);
	        theTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
				public void valueChanged(ListSelectionEvent arg0) {
					btnRemove.setEnabled(theTable.getSelectedRowCount() > 0);
					if(bSingleSelect)
						btnAddFile.setEnabled(theTable.getRowCount() == 0);
				}
			});
	        theTable.getTableHeader().setBackground(Color.WHITE);
	        sPane = new JScrollPane(theTable);
	        sPane.getViewport().setBackground(Color.WHITE);
	       	
	        TableColumn column = theTable.getColumnModel().getColumn(0);
	        column.setMaxWidth(column.getPreferredWidth());
	        column.setMinWidth(column.getPreferredWidth());
	        
	        btnAddFile = new JButton("Add File");
	        btnAddFile.setBackground(Color.WHITE);
	        btnAddFile.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					String path=null;
					
					String defaultDir = getDefaultDir();
					JFileChooser cf = new JFileChooser();
					cf.setMultiSelectionEnabled(false);
					cf.setCurrentDirectory(new File(defaultDir));
					
					if(cf.showOpenDialog(getInstance()) == JFileChooser.APPROVE_OPTION) {
						path = cf.getSelectedFile().getAbsolutePath().trim();
						if (path.length() > 0) theModel.addRow(path);
						theTable.setVisible(false);
						theTable.setVisible(true);
						setDefaultDir(path);
					}
					updateButtons();
				}
			});
	        
	        btnAddDir = new JButton("Add Directory");
	        btnAddDir.setBackground(Color.WHITE);
	        btnAddDir.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					String path=null;
					String defaultDir = getDefaultDir();
					
					JFileChooser cf = new JFileChooser();
					cf.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
					cf.setMultiSelectionEnabled(false);
					cf.setCurrentDirectory(new File(defaultDir));
					
					if(cf.showOpenDialog(getInstance()) == JFileChooser.APPROVE_OPTION) {
						path = cf.getSelectedFile().getAbsolutePath().trim();
						if (path.length() > 0) theModel.addRow(path + "/");
						theTable.setVisible(false);
						theTable.setVisible(true);
						setDefaultDir(path);
					}
					updateButtons();
				}
			});
	        
	        btnRemove = new JButton("Remove");
	        btnRemove.setBackground(Color.WHITE);
	        btnRemove.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent arg0) {
					theModel.removeRow(theTable.getSelectedRow());
					theTable.getSelectionModel().clearSelection();
					theTable.setVisible(false);
					theTable.setVisible(true);
				}
			});
	        btnRemove.setEnabled(false);
	        
	        setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
	        setBackground(Color.WHITE);
	        
	        JPanel buttonRow = new JPanel();
	        buttonRow.setLayout(new BoxLayout(buttonRow, BoxLayout.LINE_AXIS));
	        buttonRow.setBackground(Color.WHITE);
	        
	        buttonRow.add(btnAddFile);	buttonRow.add(Box.createHorizontalStrut(20));
	        buttonRow.add(btnAddDir);	buttonRow.add(Box.createHorizontalStrut(20));
	        buttonRow.add(btnRemove);
	        
	        add(sPane);
	        add(buttonRow);
		}
		private String getDefaultDir() {
			String defDir;
			
			if (lastSeqDir==null) {
				defDir = Constants.seqDataDir;
				if (Utilities.pathExists(defDir+theDBName)) defDir += theDBName;
			}
			else defDir=lastSeqDir;
			return defDir;
		}
		private void setDefaultDir(String path) {
			if (path==null || path.length()==0) return;
			
			String upPath = path;
			int last = path.lastIndexOf("/");
			if (last>1) 
				upPath = path.substring(0, last);
			
			lastSeqDir=upPath;
		}
		public String getValue() {
			String retVal = "";
			if(theModel.getRowCount() > 0)
				retVal = (String)theModel.getValueAt(0, 1);
			
			for(int x=1; x<theModel.getRowCount(); x++)
				retVal += "," + theModel.getValueAt(x, 1);
			
			return retVal;
		}
		public void setValue(String value) {
			String [] vals = value.split(",");
			theModel.clearAll();
			for(int x=0; x<vals.length; x++) {
				if(vals[x] != null && vals[x].trim().length() > 0)
					theModel.addRow(vals[x]);
			}
		}
		private void updateButtons() {
			if(bSingleSelect && theTable.getRowCount() > 0) {
				btnAddFile.setEnabled(false);
				btnAddDir.setEnabled(false);
			}
			else {
				btnAddFile.setEnabled(true);
				btnAddDir.setEnabled(true);
			}
		}
		private JScrollPane sPane = null;
		private JTable theTable = null;
		private FileTableModel theModel = null;
		private boolean bSingleSelect = false;
		
		private JButton btnAddFile = null, btnAddDir = null, btnRemove = null; 
		
		private class FileTableModel extends AbstractTableModel {
			private static final long serialVersionUID = 1L;
			private final static String FILE_HEADER = "File name/directory";
			private final static String FILE_TYPE = "Type";
			
			public FileTableModel() {
				theFiles = new Vector<String> ();
			}
			
			public int getColumnCount() { return 2; }
			public int getRowCount() { return theFiles.size(); }
			public Object getValueAt(int row, int col) {
				if(col == 0) {
					if(theFiles.get(row).endsWith("/"))
						return "Dir";
					return "File";
				}
				return theFiles.get(row); 
			}
			
			public void addRow(String filename) { theFiles.add(filename); }
			public void removeRow(int row) { theFiles.remove(row); }
			public void clearAll() { theFiles.clear(); }
			public Class<?> getColumnClass(int column) { return String.class; }
			
	    	public String getColumnName(int columnIndex) {
	    		if(columnIndex == 0) return FILE_TYPE;
	    		return FILE_HEADER;
	    	}
			public boolean isCellEditable (int row, int column) { return false; }
			
			private Vector<String> theFiles = null;		
		}
	} // end File class
	
	/**************************************************************************/

	private String theDisplayName = "";
	private String theDBName = "";
	private boolean bIsLoaded = false, bExistAlign = false;
	private boolean bChgGrpPrefix = false;
	private boolean bWasSave = false;
	private Field [] theFields;
	private String [] prevArr = null;
	private String [] theProjectNames = null;
	private JButton btnSave = null, btnCancel = null;
	private CaretListener theListener = null;
}
