package symap.projectmanager.common;
/*******************************************
 * Displays the project parameter window and saves them to file
 * 	They are loaded into the proj_props DB table on Load
 * 
 * CAS513 reorder a bunch and some renaming; added grp_prefix update
 */
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
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

import util.DatabaseReader;
import util.ErrorReport;
import util.PropertiesReader;
import util.Utilities;

public class PropertyFrame extends JDialog {
	private static final long serialVersionUID = -8007681805846455592L;

	public static final int MODE_FPC = 1;
	public static final int MODE_PSEUDO = 2;
	
	private static final String [] PSEUDO_MASK_GENES = { "yes", "no" };
	
	private static final Color HELP_BUTTON_COLOR = new Color(0xEEFFEE);

	private static int INITIAL_WIDTH = 640;
	private static int INITIAL_HEIGHT = 600;
	
	public static String lastFPCDir=null, lastSeqDir=null; // CAS500
	private int idxGrpPrefix=0, idxMinKWcnt=0; 
	
	public PropertyFrame(Frame parentFrame, boolean isPseudo, 
			String displayName, String dbName, DatabaseReader dbReader, 
			boolean isLoadedProject, String pathName) {
		
		super(parentFrame, displayName + " parameters");
		
		theDisplayName = displayName;
		theDBName = dbName;
		bIsLoaded = isLoadedProject;
		thePathName = pathName;
		
		if (!isPseudo) theMode = MODE_FPC;
		else theMode = MODE_PSEUDO;
		
		getContentPane().setLayout(new BoxLayout(getContentPane(), BoxLayout.PAGE_AXIS));
		getContentPane().setBackground(Color.WHITE);
		
		theListener = new CaretListener() {
			public void caretUpdate(CaretEvent arg0) {
				boolean showSave = true;			
				for(int x=0; x<theFields.length && showSave; x++) {
					if(theFields[x].getLabel().equals("display_name")) {
						if(theFields[x].getValue().length() == 0) showSave = false; // must have a value
					}
					else if(theFields[x].getLabel().equals("grp_type")) {
						if(theFields[x].getValue().length() == 0) showSave = false; // must have a value
					}
				}
				btnSave.setEnabled(showSave);
			}
		};
		thePseudoProjectNames = getSeqProjectNames(dbReader); // Used by OrderBy drop-down
		buildMainPanel();
		
		if(!updateFieldsFromDB(dbReader)) { // if loaded 
			updateFieldsFromFile(); 		// not loaded
		}
		
		setInitValues(); // Save initial values to see if they change
		
		setSize(INITIAL_HEIGHT, INITIAL_WIDTH);
		setMinimumSize(new Dimension(INITIAL_HEIGHT, INITIAL_WIDTH));
		
		setModal(true);
		
		setLocationRelativeTo(parentFrame); // CAS513 put on top of parent
	}
	
	public PropertyFrame getInstance() { return this; }
	
	private void buildMainPanel() {
		// Different PropertyComponent for File, DropDown and other
		int startLoad=0, i=0;
		boolean bReload=true, bReAlign=true;
		
		if(theMode == MODE_FPC) {
			theFields = new PropertyComponent[9];
		
			theFields[i++] = new PropertyComponent("category", "Uncategorized", 1, 		!bReload, !bReAlign);
			theFields[i++] = new PropertyComponent("display_name", theDisplayName, 1, 	!bReload, !bReAlign);
			theFields[i++] = new PropertyComponent("grp_type", "Chromosome", 1, 		!bReload, !bReAlign);
			theFields[i++] = new PropertyComponent("description", "", 2, 				!bReload, !bReAlign);	
			
			startLoad=i;
			theFields[i++] = new PropertyComponent("grp_prefix", "", 1, !bReload, !bReAlign);
			theFields[i++] = new PropertyComponent("cbsize", "1200", 1, !bReload, !bReAlign);	
			
			theFields[i++] = new PropertyComponent("fpc_file", "",     bReload, bReAlign, true);
			theFields[i++] = new PropertyComponent("bes_files", "",    bReload, bReAlign, false);
			theFields[i++] = new PropertyComponent("marker_files", "", bReload, bReAlign, false);
		} 
		else { //MODE_PSEUDO CAS42 1/4/17 reordered them
			theFields = new PropertyComponent[13];
			theFields[i++] = new PropertyComponent("category", "Uncategorized", 1, 		!bReload, !bReAlign);
			theFields[i++] = new PropertyComponent("display_name", theDisplayName, 1, 	!bReload, !bReAlign);
			theFields[i++] = new PropertyComponent("grp_type", "Chromosome", 1, 		!bReload, !bReAlign);
			theFields[i++] = new PropertyComponent("description", "", 2, 				!bReload, !bReAlign);	
			theFields[i++] = new PropertyComponent("min_display_size_bp", "0", 1, 		!bReload, !bReAlign);	
			
			// special cases
			idxMinKWcnt=i;
			theFields[i++] = new PropertyComponent("annot_kw_mincount", "50", 1, 	!bReload, !bReAlign);	// CAS513 2 lines->1, moved
			idxGrpPrefix = i;
			startLoad=i-1; // put >Load before this field 
			theFields[i++] = new PropertyComponent("grp_prefix", "Chr", 1, 			!bReload, !bReAlign);
			
			theFields[i++] = new PropertyComponent("min_size", "100000", 1, bReload, !bReAlign); // Hardcoded in Projects too
			// CAS42 1/4/17 this is in the Help, but not here
			//theFields[4] = new PropertyComponent("grp_sort", PSEUDO_GRP_SORT, 0, true, false);
			theFields[i++] = new PropertyComponent("annot_keywords", "", 2, bReload, !bReAlign); // CAS512 was false for Reload		
			
			theFields[i++] = new PropertyComponent("order_against", getOrderBySelections(), 0, !bReload, bReAlign);
			theFields[i++] = new PropertyComponent("mask_all_but_genes", PSEUDO_MASK_GENES, 1, !bReload, bReAlign);
			
			// CAS500 swap the order of these two
			theFields[i++] = new PropertyComponent("sequence_files", "", 	bReload, bReAlign, false);
			theFields[i++] = new PropertyComponent("anno_files", "", 		bReload, bReAlign, false);
		}
			
		JPanel tempPanel = new JPanel();
		tempPanel.setLayout(new BoxLayout(tempPanel, BoxLayout.PAGE_AXIS));
		tempPanel.setBackground(Color.WHITE);
		tempPanel.add(new JLabel(">Summary and Display: " + thePathName)); // CAS512 add theDirName
	
		for(int x=0; x<theFields.length; x++) {
			tempPanel.add(Box.createVerticalStrut(5));
			theFields[x].setTextListener(theListener);
			tempPanel.add(theFields[x]);
			if ((theMode != MODE_FPC && x==startLoad) || (theMode == MODE_FPC && x==startLoad)) {
				tempPanel.add(Box.createVerticalStrut(15));
				tempPanel.add(new JLabel(">Load, Computation and Display:"));
			}
		}
		tempPanel.add(Box.createVerticalStrut(20));
		tempPanel.add(createButtonPanel());
		tempPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		tempPanel.setMaximumSize(tempPanel.getPreferredSize());
		tempPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
			
		getContentPane().add(tempPanel);
	}
	
	/******************************************************************/
	private JPanel createButtonPanel() {
		JPanel retVal = new JPanel();
		retVal.setLayout(new BoxLayout(retVal, BoxLayout.PAGE_AXIS));
		retVal.setAlignmentX(Component.LEFT_ALIGNMENT);
		retVal.setBackground(Color.WHITE);
		
		JPanel buttonPanel = new JPanel();
		buttonPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));
		
		btnSave = new JButton("Save");
		btnSave.setBackground(Color.WHITE);
		btnSave.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				save();
			}
		});
		btnCancel = new JButton("Cancel");
		btnCancel.setBackground(Color.WHITE);
		btnCancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				bSaveToDB = false;
				closeDialog();
			}
		});
		
		btnHelp = new JButton("Help");
		btnHelp.setBackground(HELP_BUTTON_COLOR);
		btnHelp.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				showHelp();
			}
		});
		buttonPanel.add(btnSave);		buttonPanel.add(Box.createHorizontalStrut(10));
		buttonPanel.add(btnCancel);		buttonPanel.add(Box.createHorizontalStrut(100));
		buttonPanel.add(btnHelp);
		buttonPanel.setBackground(Color.WHITE);
		buttonPanel.setMaximumSize(buttonPanel.getPreferredSize());

		retVal.add(buttonPanel);

		return retVal;
	}
	
	/*******************************************************************/
	private String [] getOrderBySelections() {
		Vector<String> retVal = new Vector<String> ();
		
		retVal.add("None");
		
		if (thePseudoProjectNames != null) {
			for (int x=0; x< thePseudoProjectNames.length; x++) {
				if (!theDBName.equals(thePseudoProjectNames[x])) {
					retVal.add(thePseudoProjectNames[x]);
				}
			}
		}
		return retVal.toArray(new String[retVal.size()]);
	}
	private String [] getSeqProjectNames(DatabaseReader dbReader) { 
		String [] retVal = null;			// assigned to thePseudoProjectNames
		try {
			Connection conn = dbReader.getConnection();
			Statement  stmt = conn.createStatement();
			
			Vector<String> names = new Vector<String> ();
			ResultSet rset = stmt.executeQuery("SELECT projects.name FROM projects " +
					"WHERE type='pseudo' ORDER BY projects.name ASC");
			while(rset.next()) {
				names.add(rset.getString("projects.name"));
			}
			
			if(!names.isEmpty())
				retVal = names.toArray(new String[names.size()]);
			
			rset.close();	stmt.close(); conn.close();

			return retVal;
		} catch(Exception e) {ErrorReport.print(e, "Reading projects from databse"); return null;} 
	}
	
	/**********************************************************************
	 * Get/Set fields
	 ***************************************************************/
	// Read the database and sets theField interface value
	private boolean updateFieldsFromDB(DatabaseReader dbReader) {
		boolean retVal = false;
		try {
			Connection conn = dbReader.getConnection();
			Statement  stmt = conn.createStatement();
			
			ResultSet rset = stmt.executeQuery("SELECT proj_props.name, proj_props.value " +
					"FROM proj_props " +
					"JOIN projects on proj_props.proj_idx = projects.idx " +
					"WHERE projects.name = '" + theDBName + "'" );
			while(rset.next()) {
				retVal = true;
				setField(rset.getString(1), rset.getString(2));
			}
			rset.close();	stmt.close(); conn.close();

			return retVal;
		} catch(Exception e) { 
			ErrorReport.print(e, "Reading parameters from databse");
			return false;
		}
	}
	// Only called if params not gotten from DB.
	private boolean updateFieldsFromFile() {
		String dir = (theMode == MODE_PSEUDO) ? 
				(Constants.seqDataDir + theDBName) : (Constants.fpcDataDir + theDBName);
		
		File pfile = new File(dir, Constants.paramsFile);
		if (pfile.isFile()) {
			System.out.println("Get params from " + dir +  Constants.paramsFile);
			PropertiesReader props = new PropertiesReader( pfile);
			for (Object obj : props.keySet()) {
				String pname = obj.toString().trim();
				String value = props.getProperty(pname).trim();
				
				setField(pname, value);
			}
			return true;
		}
		return false;
	}
	private boolean setField(String fieldName, String fieldValue) {
		for(int x=0; x<theFields.length; x++) {
			if(fieldName.equals(theFields[x].getLabel())) {
				if(fieldName.equals("mask_all_but_genes")) {
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
		valueBuffer = new String[theFields.length];
		
		for(int x=0; x<valueBuffer.length; x++)
			valueBuffer[x] = theFields[x].getValue();
	}
	/***************************************************************
	 * XXX Save
	 * CAS513 changed the logic on this part and added ChgGrpPrefix
	 */
	public boolean isSaveToDB() 	{ return bSaveToDB; } // if true, reads from file and writes to DB (if loaded)
	public boolean isChgGrpPrefix() { return bChgGrpPrefix;}
	
	private void save() {
		boolean bLoad =  isReQuiredReload();	if (!bLoad) return;
		
		boolean bAlign = isRequiredRealign(); 	if (!bAlign) return;
		
		boolean bGrp  =  isRequiredGrpPrefix();	if (!bGrp) return;
		
		isRequiredMinKWcnt();
	
		//if (bIsLoaded) 	System.out.println("Write params to file and database for " + theDisplayName);
		//else 			System.out.println("Write params to file for " + theDisplayName);
		
		writeParamsFile();	
		bSaveToDB = true;   // will only do this if already loaded
		
		closeDialog();
	}
	 /** Realign parameters **/
	private boolean isRequiredRealign() {
		String params="";
		boolean required = false;
		for(int x=0; x<valueBuffer.length; x++) {
			if(theFields[x].isRealignField() 
					&& !theFields[x].getValue().equals(valueBuffer[x]) 
					&& !theFields[x].isDefaultValue())
			{
				params += theFields[x].getLabel() + ",";
				required = true;
			}
		}
		if (!required) return  true;
		if (!bIsLoaded) return true;  // write to file only
		
		params = params.substring(0, params.length()-1); // chop final ,
		
		String msg = "Realign\nThe change to the following parameter(s): " + params + 
				  	"\n will take effect on the next alignment." +
			   		"\n\nProceed with save?";
		int n = JOptionPane.showConfirmDialog( this, msg , "", JOptionPane.YES_NO_OPTION);
		
		if (n == JOptionPane.YES_OPTION) return true;
		return false;
	}
	/** Reload parameters **/
	private boolean isReQuiredReload() { // Change any parameter that requires reload
		boolean required = false;
		String params="";
		
		for(int x=0; x<valueBuffer.length; x++) {
			if(theFields[x].isReloadField() 
					&& !theFields[x].getValue().equals(valueBuffer[x]) 
					&& !theFields[x].isDefaultValue())
			{
				params += theFields[x].getLabel() + ",";
				required = true;
			}
		}
		if (!required) return true;
		if (!bIsLoaded) return true;	// write to file only
		
		params = params.substring(0, params.length()-1); // chop final ,
		
		String msg = "Reload\nThe change to the following parameter(s): " +  params + 
				"\n will take effect on the next Reload of the project." +
				"\n\nProceed with save?";
		
		int n = JOptionPane.showConfirmDialog( this, msg,"", JOptionPane.YES_NO_OPTION);
		
		if (n == JOptionPane.YES_OPTION) return true;
		return false;
	}
	 /** Grp Prefix **/
	private boolean isRequiredGrpPrefix() { 
		bChgGrpPrefix = false;
		String val = theFields[idxGrpPrefix].getValue();
		
		if (val.equals(valueBuffer[idxGrpPrefix])) 	return true;
		if (!bIsLoaded) 							return true; 
		
		if (val.contentEquals("")) {
			String msg ="grp_prefix set to blank has no effect on a loaded project. "
					+ "\nOn a reload, there will be no change to the group names. (See Help)";
			int n = JOptionPane.showConfirmDialog( this, msg, "", JOptionPane.YES_NO_OPTION);
			
			if (n == JOptionPane.YES_OPTION) return true;
			else return false;
		}
		
		String msg = "The grp_prefix '" + val +"' will be removed from all group display labels."+
				"\n   This is not reversible (i.e. it cannot be added back). \n\nProceed with save?";
		int n = JOptionPane.showConfirmDialog( this, msg, "", JOptionPane.YES_NO_OPTION);
		
		if (n == JOptionPane.YES_OPTION) {
			bChgGrpPrefix=true;
			return true;
		}
		return false;
	}
	/** annot_kw_mincnt **/
	private boolean isRequiredMinKWcnt() { 
		String val = theFields[idxMinKWcnt].getValue();
		
		if (val.equals(valueBuffer[idxMinKWcnt])) 	return true;
		if (!bIsLoaded) 							return true; 
		
		JOptionPane.showMessageDialog(this, 
				"Changing the annot_kw_mincount number may change \nthe Annotation columns for SyMAP Query.");
		return true;
	}
	/*******************************************************
	 * Write Params file
	 * 	the params file is read in symap.projectmanager.common.Project
	 * 	if the sequence files are not in the /data directory, 
	 * 		the /data/seq/<p>/params is created anyway for the parameters
	 */
	private void writeParamsFile() {	
		String dir = (theMode == MODE_PSEUDO) ? 
				(Constants.seqDataDir + theDBName) : (Constants.fpcDataDir + theDBName);
		Utilities.checkCreateDir(dir, true); // CAS500 
		
		File pfile = new File(dir,Constants.paramsFile);
		if (!pfile.exists())
			System.out.println("Create parameter file " + dir + Constants.paramsFile);
		try {
			PrintWriter out = new PrintWriter(pfile);
			
			out.println("#");
			out.println("#  " + theDisplayName + " project parameter file");
			out.println("#  Note: changes MUST be made in SyMAP parameter window");
			
			for(int x=0; x<theFields.length; x++) {
				String val = theFields[x].getValue();
				if (val.equals("null")) val=""; // CAS500 this happens for anno_file
				String label  = theFields[x].getLabel();
				
				if(val.length() > 0) {
					//special case for conversion
					if(label.equals("mask_all_but_genes")) {
						if(val.equals("yes")) 	out.println("mask_all_but_genes = 1");
						else					out.println("mask_all_but_genes = 0");
					}
					else if(label.equals("order_against")) {
						if(!val.equals("None"))	out.println("order_against = " + val);
					}
					else out.println(label + " = " + val.replace('\n', ' '));
				}
				else {
						 out.println(label + " = "); // save empty ones too
				}
			}
			out.close();
		} catch(Exception e) {ErrorReport.print(e, "Creating params file");}
	}
	
	private void showHelp() {
		Utilities.showHTMLPage(this, "Project Parameter Help", 
				(theMode == MODE_FPC ? "/html/FpcParamHelp.html" : "/html/ProjParamHelp.html"));	
	}
	private void closeDialog() {
		dispose();
	}
	
	/***************************************************************/
	public class PropertyComponent extends JPanel{
		private static final long serialVersionUID = -6643140570421332451L;
		
		private static final int MODE_TEXT = 0;
		private static final int MODE_LIST = 1;
		private static final int MODE_MULTI_LINE = 2;
		private static final int MODE_FILE_LIST = 3;
		
		private static final int LABEL_WIDTH = 150;
		private static final int FIELD_COLUMNS = 10;
		
		//Used for a Add file/directory
		public PropertyComponent(String label, String values, boolean needsReload, boolean needsRealign, boolean singleSelect) {
			bReload = needsReload;
			bRealign = needsRealign;
			
			nMode = MODE_FILE_LIST;
			
			theComponent = new FileListTable(singleSelect);
			theLabel = new JLabel(label);
			theLabel.setBackground(Color.WHITE);

			setLayout();
		}
		//Used for plain text properties
		public PropertyComponent(String label, String initValue, int numLines, boolean needsReload, boolean needsRealign) {
			bReload = needsReload;
			bRealign = needsRealign;
			
			if(numLines == 1) {
				nMode = MODE_TEXT;
				theComponent = new JTextField(FIELD_COLUMNS);
				((JTextField)theComponent).setBorder(BorderFactory.createLineBorder(Color.BLACK));
			}
			else {
				nMode = MODE_MULTI_LINE;
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
		public PropertyComponent(String label, String [] options, int selection, boolean needsReload, boolean needsRealign) {
			bReload = needsReload;
			bRealign = needsRealign;
			
			nMode = MODE_LIST;
			theComponent = new JComboBox(options);
			((JComboBox)theComponent).setSelectedIndex(selection);
			((JComboBox)theComponent).setBackground(Color.WHITE);
			theLabel = new JLabel(label);
			theLabel.setBackground(Color.WHITE);
			setLayout();
		}
		
		public String getLabel() { return theLabel.getText(); }
		public String getValue() {
			if(nMode == MODE_TEXT)       return ((JTextField)   theComponent).getText();
			if(nMode == MODE_MULTI_LINE) return ((JTextArea)    theComponent).getText();
			if(nMode == MODE_FILE_LIST)  return ((FileListTable)theComponent).getValue();
			//MODE_LIST
			return (String)((JComboBox)theComponent).getSelectedItem();
		}
		
		public void setValue(String value) {
			isDefault = false;
			if(nMode == MODE_TEXT)            ((JTextField)   theComponent).setText(value);
			else if(nMode == MODE_MULTI_LINE) ((JTextArea)    theComponent).setText(value);
			else if(nMode == MODE_FILE_LIST)  ((FileListTable)theComponent).setValue(value);
			else { //MODE_LIST
				((JComboBox)theComponent).setSelectedItem(value);
				if(!((String)((JComboBox)theComponent).getSelectedItem()).equals(value)) {
					if (!value.equals("")) // CAS42 1/4/18 get this message when order_against=NONE
						System.out.println("Cannot select " + value + " for " + theLabel.getText() + " defaulting to " + ((String)((JComboBox)theComponent).getSelectedItem()));
					isDefault = true;
				}
			}
		}
		
		public boolean isRealignField() { return bRealign; }
		public boolean isReloadField()  { return bReload; }
		public boolean isDefaultValue() { return isDefault; }
		
		public void setTextListener(CaretListener l) {
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
		private JLabel theLabel = null;
		private boolean bReload = false;
		private boolean bRealign = false;
		private boolean isDefault = true;
		private int nMode = -1;
	} // end PropertyComponent class
	
	/*******************************************************/
	private class FileListTable extends JPanel {
		private static final long serialVersionUID = 1L;

		public FileListTable(boolean singleSelect) {
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
			if (theMode == MODE_FPC) {
				if (lastFPCDir==null) {
					defDir = Constants.fpcDataDir;
					if (Utilities.pathExists(defDir+theDBName)) defDir += theDBName;
				}
				else defDir=lastFPCDir;
			}
			else {
				if (lastSeqDir==null) {
					defDir = Constants.seqDataDir;
					if (Utilities.pathExists(defDir+theDBName)) defDir += theDBName;
				}
				else defDir=lastSeqDir;
			}
			return defDir;
		}
		private void setDefaultDir(String path) {
			if (path==null || path.length()==0) return;
			
			String upPath = path;
			int last = path.lastIndexOf("/");
			if (last>1) 
				upPath = path.substring(0, last);
			
			if (theMode == MODE_FPC) lastFPCDir = upPath;
			else lastSeqDir=upPath;
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
	    		if(columnIndex == 0)
	    			return FILE_TYPE;
	    		return FILE_HEADER;
	    	}
			public boolean isCellEditable (int row, int column) { return false; }
			
			private Vector<String> theFiles = null;		
		}
	} // end File class
	
	/**************************************************************************/
	private int theMode = -1;
	private String thePathName = ""; // CAS512
	private String theDisplayName = "";
	private String theDBName = "";
	private boolean bSaveToDB = true;
	private boolean bIsLoaded = false;
	private boolean bChgGrpPrefix = false;
	private PropertyComponent [] theFields;
	private String [] valueBuffer = null;
	private String [] thePseudoProjectNames = null;
	private JButton btnSave = null, btnCancel = null, btnHelp = null;
	private CaretListener theListener = null;
}
