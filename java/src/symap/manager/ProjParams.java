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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import symap.Globals;
import util.ErrorReport;
import util.Jcomp;
import util.Jhtml;
import util.Popup;
import util.FileDir;

/*******************************************
 * Displays the project parameter window and saves them to file
 * This goes with Mproject.
 */
public class ProjParams extends JDialog {
	private static final long serialVersionUID = -8007681805846455592L;
	static public String lastSeqDir=null; 
	static protected String catUncat = "Uncategorized"; // puts this as last if there are no occurrences
	
	private final int abbrevLen = Globals.abbrevLen;
	private final int INITIAL_WIDTH = 610, INITIAL_HEIGHT = 660;
	
	private final String displayHead = 		"Display";
	private final String loadProjectHead =  "Load project";
	private final String loadAnnoHead = 	"Load annotation";
	
	private int idxGrpPrefix=0; 
	private Mproject mProj;
	private Vector <Mproject> mProjVec;
	private String [] catArr;
	private Pattern pat = Pattern.compile("^[a-zA-Z0-9_\\.\\-]+$");	// category, display name, abbrev
	private String patMsg = "\nEntry can only contain letters, digit, '-', '_', '.'";
	
	public ProjParams(Frame parentFrame,  Mproject mProj, 
			Vector <Mproject> projVec, String [] catArr, boolean isLoaded, boolean existAlign) { 
		
		super(parentFrame, Constants.seqDataDir + mProj.getDBName() + "/params file"); // title
		
		this.mProj = mProj;
		this.mProjVec = projVec;
		this.catArr = catArr;
		
		theDBName =  	mProj.getDBName();
		savDname = 		mProj.getDisplayName();
		savAbbrev = 	mProj.getdbAbbrev();
		
		bIsLoaded = 	isLoaded;
		bExistAlign = 	existAlign;
		
		getContentPane().setLayout(new BoxLayout(getContentPane(), BoxLayout.PAGE_AXIS));
		getContentPane().setBackground(Color.WHITE);
		
		theListener = new CaretListener() {
			public void caretUpdate(CaretEvent arg0) {}
		};
		createMainPanel();
		setFieldsFromProj();
		
		setInitValues(); // Save initial values to see if they change
		
		setSize(INITIAL_WIDTH, INITIAL_HEIGHT);
		setMinimumSize(new Dimension(INITIAL_WIDTH, INITIAL_HEIGHT));
		
		setModal(true);
		
		setLocationRelativeTo(parentFrame); 
	}
	public boolean wasSave() {return bWasSave;} // ManagerFrame.doReloadParams, doSetParamsNotLoaded
	
	private ProjParams getInstance() { return this; }
	
	/*******************************************************
	 * If any parameters are changed, change also in manager.Mproject
	 * NOTE: The descriptions, etc are in Mproject
	 */
	private void createMainPanel() {
		int startAnno=0, startLoad=0, i=0;
		boolean bReload=true, bReAlign=true;
		
		theFields = new Field[11]; // If change #fields, change!
		theFields[i++] = new Field(mProj.sCategory, catArr, 1, 	!bReload, !bReAlign, true);// true -> add text field
		
		theFields[i++] = new Field(mProj.sDisplay, 1, 	!bReload, !bReAlign);
		theFields[i++] = new Field(mProj.sAbbrev, 1, 	!bReload, !bReAlign); 
		theFields[i++] = new Field(mProj.sGrpType, 1, 	!bReload, !bReAlign);
		theFields[i++] = new Field(mProj.sDesc, 2, 		!bReload, !bReAlign);	// 2 is multiText
		theFields[i++] = new Field(mProj.sANkeyCnt,  1, !bReload, !bReAlign);	
		
		startLoad = i-1;
		idxGrpPrefix = i; 								// used for saveGrpPrefix special message
		theFields[i++] = new Field(mProj.lGrpPrefix,  1, !bReload, !bReAlign); 
		theFields[i++] = new Field(mProj.lMinLen, 1, 	  bReload,  bReAlign); 
		theFields[i++] = new Field(mProj.lSeqFile,		  bReload,  bReAlign, false);
		
		startAnno=i-1; 
		theFields[i++] = new Field(mProj.lANkeyword, 1,  bReload, !bReAlign);  	
		theFields[i++] = new Field(mProj.lAnnoFile, 	 bReload, !bReAlign, false);
		
		JPanel fieldPanel = Jcomp.createPagePanel();
		fieldPanel.add(Jcomp.createHtmlLabel(displayHead, "Parameters that can be changed at any time")); 
	
		for(int x=0; x<theFields.length; x++) {
			fieldPanel.add(Box.createVerticalStrut(5));
			theFields[x].setTextListener(theListener);
			fieldPanel.add(theFields[x]);
			
			if (x==startAnno) {
				fieldPanel.add(Box.createVerticalStrut(5));
				fieldPanel.add(new JSeparator()); // works on linux but not mac; but the createLabel is better on Mac
				fieldPanel.add(Box.createVerticalStrut(5));
				fieldPanel.add(Jcomp.createHtmlLabel(loadAnnoHead, "Parameters changed for Load Annotation (or Project)")); // <i><b>
			}
			else if (x==startLoad) {
				fieldPanel.add(Box.createVerticalStrut(5));
				fieldPanel.add(new JSeparator()); 
				fieldPanel.add(Box.createVerticalStrut(5));
				fieldPanel.add(Jcomp.createHtmlLabel(loadProjectHead, "Parameters changed for Load Project"));
			}
		}
		
		JPanel mainPanel = Jcomp.createPagePanel();
		
		mainPanel.add(fieldPanel);
		mainPanel.add(Box.createVerticalStrut(10));
		
		JPanel buttons = createButtonPanel();
		mainPanel.add(buttons);
		
		mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		mainPanel.setMaximumSize(fieldPanel.getPreferredSize());
		mainPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
			
		getContentPane().add(mainPanel);
	}
	
	/******************************************************************/
	private JPanel createButtonPanel() {
		btnSave = Jcomp.createButton("Save", "Save parameters to file");
		btnSave.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				bWasSave=true;
				save();
			}
		});
		btnCancel = Jcomp.createButton("Cancel", "Exit window without saving any changes");
		btnCancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)  {
				bWasSave = false;
				closeDialog();
			}
		});
		
		JButton btnInfo = Jcomp.createIconButton("/images/info.png", "Quick Help" );
		btnInfo.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)  {
				popupHelp();
			}
		});
		
		JButton btnHelp = Jhtml.createHelpIconSysSm(Jhtml.SYS_HELP_URL, Jhtml.param1); 
		
		JPanel buttonPanel = Jcomp.createRowPanel();
		
		buttonPanel.add(btnSave);		buttonPanel.add(Box.createHorizontalStrut(10));
		buttonPanel.add(btnCancel);		buttonPanel.add(Box.createHorizontalStrut(20));
		buttonPanel.add(btnHelp);		buttonPanel.add(Box.createHorizontalStrut(10));
		buttonPanel.add(btnInfo);
		
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
	 */
	private void save() {
		if (!FileDir.dirExists(Constants.dataDir)) {
			util.Popup.showWarningMessage("The /data directory does not exist, cannot save parameters.");
			return;
		}
		if (!saveCheckFields()) return;
		
		if (!saveIsRequired())	return;
		
		if (!saveGrpPrefix())	return;
		
		writeParamsFile();	
		
		mProj.loadParamsFromDisk();
		mProj.saveParams(mProj.xDisplay);
		
		if (bChgGrpPrefix) mProj.updateGrpPrefix(); // change grp prefix after load
	
		closeDialog();
	}
	/********************************************************************/
	private boolean saveCheckFields() {
	try {
		String msg=null;
		
		for(int x=0; x<theFields.length; x++) {
			int index = theFields[x].index;
			String val = theFields[x].getValue().trim();
			String lab = theFields[x].getLabel();
		
			if (index == mProj.sDisplay) {	
				if (val.length()>12 && !val.equals(savDname)) {
					String xmsg = "Display name '" + val + "' is greater than 12 characters.\n" +
							"This is allowed, but is not recommended as it causes crowded displays.\n" +
							"\nTip: Use Description for full information about the species.";
					if (!util.Popup.showContinue("Display name",xmsg)) return false;
					savDname = val;
				}
				if (val.length() == 0) msg = lab + " must have a value.";
				else {
					Matcher m = pat.matcher(val);
					if (!m.matches()) msg = lab + " '" + val + "' is illegal." + patMsg;
				}
				if (msg==null) {
					String dir = mProj.getDBName();
					for (Mproject mp : mProjVec) {// works if check directory name, which can't change; CAS578
						if (!mp.getDBName().equalsIgnoreCase(dir) && val.equalsIgnoreCase(mp.getDisplayName())) { 
							msg = "The display name '" + val + "' has been used by project: \n";
							String desc = (mp.getdbDesc().length()<50) ? mp.getdbDesc() : mp.getdbDesc().subSequence(0,48) + "...";  
							msg += String.format("   %-12s: %s\n   %-12s: %s\n   %-12s: %s\n   %-12s: %s\n",
									"Directory", mp.getDBName(), "Display", mp.getDisplayName(), 
									"Description", desc, "Abbreviation", mp.getdbAbbrev());
							msg += "\nDuplicate display names are not allows (these are case-insenitive).";
							break;
						}
					}
				}
			}
			else if (index == mProj.sAbbrev) {
				if (val.length()>abbrevLen) {
					msg = lab + " must be <= " + abbrevLen + " characters. Value '" + val + "' is " + val.length() + ".";
				}
				else {
					Matcher m = pat.matcher(val);
					if (!m.matches()) msg = lab + " '" + val + "' is illegal." + patMsg;
				}
				// since this is a warning, do not want to keep checking, so only check if changed
				if (msg==null && !val.equals(savAbbrev)) {	// works if check directory name, which can't change; CAS578
					String dir = mProj.getDBName();
					for (Mproject mp : mProjVec) {  // also checked in QueryFrame 
						if (!mp.getDBName().equalsIgnoreCase(dir) && val.equalsIgnoreCase(mp.getdbAbbrev())) { 
							msg = "The abbreviation '" + val + "' has been used by project: \n";
							msg += String.format("   %-12s: %s\n   %-12s: %s\n   %-12s: %s\n   %-12s: %s\n",
								"Directory", mp.getDBName(), "Display", mp.getDisplayName(), 
								"Category", mp.getdbCat(), "Abbreviation", mp.getdbAbbrev());
							msg += "\nDuplicate abbreviations are allowed, \n"
								+ "but Queries will not work between two projects with the same abbreviation.";
							util.Popup.showMonoWarning(this, msg);
							msg=null;
							break;
						}
					}
					savAbbrev = val; 
				}
			}
			else if (index == mProj.sCategory) {
				val = val.trim();				// getValue gets the text or drop-down value
				Matcher m = pat.matcher(val);
				if (!m.matches()) msg = lab + " '" + val + "' is illegal." + patMsg;
			}
			else if (index == mProj.sGrpType) {
				if (val.length() == 0) msg = lab + " must have a value.";
			}
			else if (index == mProj.sDesc) {
				if (!val.isEmpty() && val.contains("/") || val.contains("#") || val.contains("\"")) 
					msg = "Description cannot contains backslash, quotes or #";
			}
			else if (index == mProj.lMinLen) {
				msg = checkInt(lab, val);
			}
			else if (index == mProj.sANkeyCnt) {
				msg = checkInt(lab, val);
			}
			
			// only shows the first warning - returns for user to fix, then test again
			if (msg!=null) { 
				util.Popup.showMonoError(this, msg);
				return false;
			}
		}
		return true;
	}
	catch (Exception e) {ErrorReport.print(e, "Checking project parameters"); return false;}
	}
	private String checkInt(String lab, String ix) {
		try {
			if (ix.contains(",")) ix = ix.replace(",", "");
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
		FileDir.checkCreateDir(dir, true); 
		
		File pfile = new File(dir,Constants.paramsFile);
		if (!pfile.exists()) // should not happen here because written on startup in Mproject
			System.out.println("Create parameter file " + dir + Constants.paramsFile);
		
		try {
			PrintWriter out = new PrintWriter(pfile);
			
			out.println("#  Directory " + theDBName + " project parameter file");
			out.println("#  Note: changes MUST be made in SyMAP parameter window");
			out.println("#");
			
			for(int x=0; x<theFields.length; x++) {
				String val = theFields[x].getValue();
				if (val.equals("null")) val=""; 						// this happens for anno_file
				String label  = mProj.getParam(theFields[x].getLabel());
				if (label==null) {
					System.out.println("SyMAP error on label: " + label);
					continue;
				}
				if(val.length() > 0) { 
					String v = val.replace('\n', ' ');
					v = v.replace("\"", " ");
					v = v.replace("\'", " ");
					v = v.trim();
					out.println(label + " = " + v);
				}
				else {
					out.println(label + " = "); 					// save empty ones too
				}
			}
			out.close();
		} catch(Exception e) {ErrorReport.print(e, "Creating params file");}
	}
	
	private void closeDialog() {
		dispose();
	}
	
	private void popupHelp() {
		String msg = "Display: These parameters can be changed at anytime.\n\n";
		msg += "Load Project: Use xToSymap to format your files for input into Symap.\n\n"
				
				+ "  If your sequences are all chromosomes, and the '>' line starts with 'Chr',\n"
				+ "  enter that for 'Group prefix'. Otherwise, read the 'Load Project' section\n"
				+ "  of 'Parameters and Interface' on-line document to determine how to set\n"
				+ "  the 'Group Prefix' and 'Minimum Length'.\n\n"
				
				+"View: Select this link for the Project Manager to check the results of the load.\n"
				+ "  MUMmer can take a long time to run, so you want to have the input sequences\n"
				+ "  correct before running it.";
		Popup.displayInfoMonoSpace(this, "Quick Help", msg, false);
	}
	/***************************************************************
	 * Each parameter has a field object
	 ************************************************************/
	public class Field extends JPanel{
		private static final long serialVersionUID = -6643140570421332451L;
		
		private static final int MODE_TEXT = 0, MODE_COMBO = 1, MODE_MULTI = 2, MODE_FILE = 3;
		
		private static final int LABEL_WIDTH = 150;
		private static final int FIELD_LEN = 10;
		
		//Used for a Add file/directory
		private Field(int index, boolean needsReload, boolean needsRealign, boolean b) {
			this.index = index;
			String label = mProj.getLab(index);
			String desc = mProj.getDesc(index);	// was getLab CAS578
			
			bdoReload = needsReload;
			bdoRealign = needsRealign;
			
			nMode = MODE_FILE;
			
			theComp = new FileTable(b);
			theLabel = Jcomp.createLabel(label, desc);

			setLayout();
		}
		// Used for plain text properties
		private Field(int index, int numLines, boolean needsReload, boolean needsRealign) {
			this.index = index;
			String label = mProj.getLab(index);
			String initValue = mProj.getDef(index);
			String desc = mProj.getDesc(index);	
			
			bdoReload = needsReload;
			bdoRealign = needsRealign;
			
			if(numLines == 1) {
				nMode = MODE_TEXT;
				theComp = new JTextField(FIELD_LEN);
				((JTextField)theComp).setBorder(BorderFactory.createLineBorder(Color.BLACK));
			}
			else {
				nMode = MODE_MULTI;
				theComp = new JTextArea(numLines, FIELD_LEN);
				((JTextArea)theComp).setBorder(BorderFactory.createLineBorder(Color.BLACK));
				((JTextArea)theComp).setLineWrap(true);
				((JTextArea)theComp).setWrapStyleWord(true);
				((JTextArea)theComp).setMinimumSize(((JTextArea)theComp).getPreferredSize());
			}
			theLabel = Jcomp.createLabel(label, desc);
			setValue(initValue);
			setLayout();
		} 
		
		// Used for properties that require a combo box
		private Field(int index, String [] options, int selection, 
				boolean needsReload, boolean needsRealign, boolean bHasText) {
			this.index = index;
			String label = mProj.getLab(index); 
			String desc = mProj.getDesc(index);	
			
			bdoReload = needsReload;
			bdoRealign = needsRealign;
			
			nMode = MODE_COMBO;
			comboBox = new JComboBox <String> (options);
			comboBox.setSelectedIndex(selection);
			comboBox.setBackground(Color.WHITE);
			comboBox.setMinimumSize(comboBox.getPreferredSize());
			comboBox.setMaximumSize(comboBox.getPreferredSize());
			
			theLabel = Jcomp.createLabel(label, desc);
			theComp = (JComboBox <String>) comboBox;
			
			if (bHasText) {
				theComboText = new JTextField(5);
				((JTextField)theComboText).setBorder(BorderFactory.createLineBorder(Color.BLACK));
			}
			else theComboText=null;

			setLayout();
		}
		
		private String getLabel() { return theLabel.getText(); }
		private String getValue() {
			if( nMode == MODE_TEXT)  return ((JTextField)theComp).getText();
			if (nMode == MODE_MULTI) return ((JTextArea) theComp).getText();
			if (nMode == MODE_FILE)  return ((FileTable) theComp).getValue();
			if (nMode == MODE_COMBO) {
				if (theComboText!=null) {
					String val = ((JTextField)theComboText).getText();
					if (!val.trim().equals("")) return val;
				}
				return (String) comboBox.getSelectedItem();
			}
			return null;
		}
		
		private void setValue(String value) {
			if(nMode == MODE_TEXT)   ((JTextField)theComp).setText(value);
			else if(nMode == MODE_MULTI) ((JTextArea) theComp).setText(value);
			else if(nMode == MODE_FILE) ((FileTable) theComp).setValue(value);
			else { // MODE_COMBO
				for (int i=0; i<comboBox.getItemCount(); i++) {
					String val = comboBox.getItemAt(i);
					if (val.equalsIgnoreCase(value)) {
						comboBox.setSelectedIndex(i);
						break;
					}
				}
			}
		}
	
		private boolean isAlignField() { return bdoRealign; }
		private boolean isLoadField()  { return bdoReload; }
		
		private void setTextListener(CaretListener l) {
			if (theComboText!=null && theComboText instanceof JTextField) {
				((JTextField)theComboText).addCaretListener(l);
			}
			else if (theComp instanceof JTextField) {
				((JTextField)theComp).addCaretListener(l);
			}
			else if(theComp instanceof JTextArea) {
				((JTextArea)theComp).addCaretListener(l);
			}
		}
		
		private void setLayout() {
			removeAll();
			setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
			setAlignmentX(Component.LEFT_ALIGNMENT);
			setBackground(Color.WHITE);
			
			theLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			theComp.setAlignmentX(Component.LEFT_ALIGNMENT);
			
			Dimension d = theLabel.getPreferredSize();
			
			add(theLabel);
			if (LABEL_WIDTH - d.width > 0)
				add(Box.createHorizontalStrut(LABEL_WIDTH - d.width));
			add(theComp);
			
			if (theComboText!=null) {
				add(Box.createHorizontalStrut(3));
				add(theComboText);
			}
		}
		
		protected int index=0;
		private JComponent theComp = null, theComboText = null; 
		private JComboBox <String> comboBox = null; 
		private JLabel theLabel = null;
		private boolean bdoReload = false, bdoRealign = false;
		private int nMode = -1;
	} // end Fields class
	
	/*******************************************************/
	private class FileTable extends JPanel {
		private static final long serialVersionUID = 1L;

		private FileTable(boolean singleSelect) {
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
	        
	        btnAddFile = Jcomp.createButton("Add File", "Add selected file");
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
	        
	        btnAddDir = Jcomp.createButton("Add Directory", "Add selected directory");
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
	        
	        btnRemove = Jcomp.createButton("Remove", "Remove selected item");
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
	        
	        JPanel buttonRow = Jcomp.createRowPanel();
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
				if (FileDir.pathExists(defDir+theDBName)) defDir += theDBName;
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
		private String getValue() {
			String retVal = "";
			if(theModel.getRowCount() > 0)
				retVal = (String)theModel.getValueAt(0, 1);
			
			for(int x=1; x<theModel.getRowCount(); x++)
				retVal += "," + theModel.getValueAt(x, 1);
			
			return retVal;
		}
		private void setValue(String value) {
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
		
		/**********************************************************************/
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
	private String savAbbrev = "", savDname = ""; // this can popup a warning, do not want to keep repeating if not changed
	private String theDBName = "";
	
	private boolean bIsLoaded = false, bExistAlign = false;
	private boolean bChgGrpPrefix = false;
	private boolean bWasSave = false;
	private Field [] theFields;
	private String [] prevArr = null;
	private JButton btnSave = null, btnCancel = null;
	private CaretListener theListener = null;
}
