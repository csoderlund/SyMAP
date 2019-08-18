package symap.projectmanager.common;

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

import util.DatabaseReader;
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
	
	public PropertyFrame(Frame parentFrame, boolean isPseudo, String displayName, String dbName, DatabaseReader dbReader, boolean isLoadedProject) {
		super(parentFrame, displayName + " parameters");
		
		theDisplayName = displayName;
		theDBName = dbName;
		bIsLoaded = isLoadedProject;
		
		if(!isPseudo) theMode = MODE_FPC;
		else theMode = MODE_PSEUDO;
		
		getContentPane().setLayout(new BoxLayout(getContentPane(), BoxLayout.PAGE_AXIS));
		getContentPane().setBackground(Color.WHITE);
		
		theListener = new CaretListener() {
			public void caretUpdate(CaretEvent arg0) {
				boolean showSave = true;
				for(int x=0; x<theFields.length && showSave; x++) {
					if(theFields[x].getLabel().equals("display_name")) {
						if(theFields[x].getValue().length() == 0)
							showSave = false;
					}
					else if(theFields[x].getLabel().equals("grp_type")) {
						if(theFields[x].getValue().length() == 0)
							showSave = false;
					}
				}
				btnSave.setEnabled(showSave);
			}
		};
		thePseudoProjectNames = getPseudoProjectNames(dbReader);
		buildMainPanel();
		
		if(!updateFieldsFromDB(dbReader)) {
			updateFieldsFromFile();
		}
		
		//Record initial values to see if they change
		setBuffer();
		
		setSize(INITIAL_HEIGHT, INITIAL_WIDTH);
		setMinimumSize(new Dimension(INITIAL_HEIGHT, INITIAL_WIDTH));
		
		setModal(true);
	}
	
	public PropertyFrame getInstance() { return this; }
	
	public boolean isDisarded() { return bIsDiscarded; }
		
	private void buildMainPanel() {
		//TODO add lists for sequence/FPC
		
		if(theMode == MODE_FPC) {
			theFields = new PropertyComponent[9];
			
			theFields[0] = new PropertyComponent("category", "Uncategorized", 1, false, false);
			theFields[1] = new PropertyComponent("display_name", theDisplayName, 1, false, false);
			theFields[2] = new PropertyComponent("grp_type", "Chromosome", 1, false, false);
			theFields[3] = new PropertyComponent("description", "", 2, false, false);	
			
			theFields[4] = new PropertyComponent("grp_prefix", "", 1, false, false);
			theFields[5] = new PropertyComponent("cbsize", "1200", 1, false, false);			
			theFields[6] = new PropertyComponent("fpc_file", "", true, true, true);
			theFields[7] = new PropertyComponent("bes_files", "", true, true, false);
			theFields[8] = new PropertyComponent("marker_files", "", true, true, false);
		} else { //MODE_PSEUDO CAS 1/4/17 reordered them
			theFields = new PropertyComponent[13];
			
			theFields[0] = new PropertyComponent("category", "Uncategorized", 1, false, false);
			theFields[1] = new PropertyComponent("display_name", theDisplayName, 1, false, false);
			theFields[2] = new PropertyComponent("grp_type", "Chromosome", 1, false, false);
			theFields[3] = new PropertyComponent("description", "", 2, false, false);	
			theFields[4] = new PropertyComponent("min_display_size_bp", "0", 1, false, false);	
		
			theFields[5] = new PropertyComponent("grp_prefix", "Chr", 1, false, false);
			theFields[6] = new PropertyComponent("min_size", "100000", 1, true, false);
			// CAS 1/4/17 this is in the Help, but not here
			//theFields[4] = new PropertyComponent("grp_sort", PSEUDO_GRP_SORT, 0, true, false);
			
			theFields[7] = new PropertyComponent("annot_keywords", "", 2, false, false);			
			theFields[8] = new PropertyComponent("annot_kw_mincount", "50", 2, false, false);	
			theFields[9] = new PropertyComponent("order_against", getProjectSelections(), 0, false, true);
			theFields[10] = new PropertyComponent("mask_all_but_genes", PSEUDO_MASK_GENES, 1, false, true);
			
			theFields[11] = new PropertyComponent("anno_files", "", true, true, false);
			theFields[12] = new PropertyComponent("sequence_files", "", true, true, false);
		}
			
		JPanel tempPanel = new JPanel();
		tempPanel.setLayout(new BoxLayout(tempPanel, BoxLayout.PAGE_AXIS));
		tempPanel.setBackground(Color.WHITE);
		tempPanel.add(new JLabel(">Summary and display:"));
	
		for(int x=0; x<theFields.length; x++) {
			tempPanel.add(Box.createVerticalStrut(5));
			theFields[x].setTextListener(theListener);
			tempPanel.add(theFields[x]);
			if ((theMode != MODE_FPC && x==4) || (theMode == MODE_FPC && x==3)) {
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
	
	private String [] getProjectSelections() {
		Vector<String> retVal = new Vector<String> ();
		
		retVal.add("None");
		
		if(thePseudoProjectNames != null) {
			for(int x=0; x< thePseudoProjectNames.length; x++) {
				if(!theDBName.equals(thePseudoProjectNames[x])) {
					retVal.add(thePseudoProjectNames[x]);
				}
			}
		}
		
		return retVal.toArray(new String[retVal.size()]);
	}
	
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
				if(checkUpdateNeeded()) {
					writeFile();
					bIsDiscarded = false;
					closeDialog();
				}
			}
		});
		btnCancel = new JButton("Discard Changes");
		btnCancel.setBackground(Color.WHITE);
		btnCancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				bIsDiscarded = true;
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
		buttonPanel.add(btnSave);
		buttonPanel.add(Box.createHorizontalStrut(10));
		buttonPanel.add(btnCancel);
		buttonPanel.add(Box.createHorizontalStrut(100));
		buttonPanel.add(btnHelp);
		buttonPanel.setBackground(Color.WHITE);
		buttonPanel.setMaximumSize(buttonPanel.getPreferredSize());
		
/*		JPanel helpPanel = new JPanel();
		helpPanel.setLayout(new BoxLayout(helpPanel, BoxLayout.LINE_AXIS));
		helpPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
		helpPanel.setBackground(Color.WHITE);
		

		helpPanel.add(btnHelp);
		helpPanel.setMaximumSize(helpPanel.getPreferredSize());*/

		retVal.add(buttonPanel);
//		retVal.add(Box.createVerticalStrut(10));
//	retVal.add(helpPanel);
		
		return retVal;
	}
	
	private boolean updateFieldsFromFile() {
		String dir = "data/" + (theMode == MODE_PSEUDO ? "pseudo/" : "fpc/") + theDBName;
		
		File pfile = new File(dir,"params");
		if (pfile.isFile())
		{
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
	
	private String [] getPseudoProjectNames(DatabaseReader dbReader) {
		Connection conn = null;
		Statement stmt = null;
		ResultSet rset = null;
		
		String [] retVal = null;
		try {
			conn = dbReader.getConnection();
			stmt = conn.createStatement();
			
			Vector<String> names = new Vector<String> ();
			rset = stmt.executeQuery("SELECT projects.name FROM projects WHERE type='pseudo' ORDER BY projects.name ASC");
			while(rset.next()) {
				names.add(rset.getString("projects.name"));
			}
			
			if(!names.isEmpty())
				retVal = names.toArray(new String[names.size()]);
			
			rset.close();
			stmt.close();
			conn.close();

			return retVal;
		} catch(Exception e) {
			return null;
		} 
	}
	
	private boolean updateFieldsFromDB(DatabaseReader dbReader) {
		boolean retVal = false;
		Connection conn = null;
		Statement stmt = null;
		ResultSet rset = null;
		try {
			conn = dbReader.getConnection();
			stmt = conn.createStatement();
			
			rset = stmt.executeQuery("SELECT proj_props.name, proj_props.value FROM proj_props JOIN projects on proj_props.proj_idx = projects.idx WHERE projects.name = '" + theDBName + "'" );
			while(rset.next()) {
				retVal = true;
				setField(rset.getString("proj_props.name"), rset.getString("proj_props.value"));
			}
			rset.close();
			stmt.close();
			conn.close();

			return retVal;
		} catch(Exception e) {
			return false;
		} finally {
			try {
				rset.close();
				stmt.close();
				conn.close();
				
				return retVal;
			} catch(Exception e) {
			}
		}
	}
	
	private void closeDialog() {
		dispose();
	}
	
	private void writeFile() {
		String dir = "data/" + (theMode == MODE_PSEUDO ? "pseudo/" : "fpc/") + theDBName;
		
		File testDir = new File(dir);
		if(!testDir.exists())
			testDir.mkdir();
		
		File pfile = new File(dir,"params");
		try {
			PrintWriter out = new PrintWriter(pfile);
			
			out.println("#");
			out.println("#  " + theDisplayName + " parameter file");
			out.println("#  Note: lines preceded by # are ignored");
			out.println("#");
			
			for(int x=0; x<theFields.length; x++)
				if(theFields[x].getValue().length() > 0) {
					//special case for conversion
					if(theFields[x].getLabel().equals("mask_all_but_genes")) {
						if(theFields[x].getValue().equals("yes"))
							out.println("mask_all_but_genes = 1");
						else
							out.println("mask_all_but_genes = 0");
					}
					else if(theFields[x].getLabel().equals("order_against")) {
						if(!theFields[x].getValue().equals("None"))
							out.println("order_against = " + theFields[x].getValue());
					}
					else
						out.println(theFields[x].getLabel() + " = " + theFields[x].getValue().replace('\n', ' '));
				}
				else {
					out.println(theFields[x].getLabel() + " = "); // WN it should save empty ones too
				}
			
			out.close();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	private boolean setField(String fieldName, String fieldValue) {
		for(int x=0; x<theFields.length; x++) {
			if(fieldName.equals(theFields[x].getLabel())) {
				if(fieldName.equals("mask_all_but_genes")) {
					if(fieldValue.equals("1") || fieldValue.equals("yes"))
						fieldValue = "yes";
					else
						fieldValue = "no";
				}
				theFields[x].setValue(fieldValue);
				return true;
			}
		}
		return false;
	}
	
	private void setBuffer() {
		valueBuffer = new String[theFields.length];
		
		for(int x=0; x<valueBuffer.length; x++)
			valueBuffer[x] = theFields[x].getValue();
	}
	
	private boolean checkUpdateNeeded() {
		StringBuffer alignParms = new StringBuffer("");
		StringBuffer loadParms = new StringBuffer("");
		boolean bAlign = isRealignmentRequired(alignParms);
		boolean bLoad = isReloadRequired(loadParms);
		if (bLoad) {
			if(!checkReloads(alignParms.toString(),loadParms.toString()))
				return false;
		}
		else if (bAlign) 
		{
			if(!checkRealignments(alignParms.toString()))
				return false;	
		}
		return true;
	}
	
	private boolean checkRealignments(String parms) {

		int n = JOptionPane.showConfirmDialog(
			    this,
			    "The following parameter changes will take effect for subsequent alignments: " + parms + " .\nProceed with save?",
			    "",
			    JOptionPane.YES_NO_OPTION);
		
		if(n == JOptionPane.YES_OPTION)
			return true;

		
		return false;
	}
	
	private boolean checkReloads(String alignparms, String loadparms) {

		String msg = "The following parameter changes will not take effect until the project is reloaded: " + loadparms + ".";
		if (!Utilities.isStringEmpty(alignparms) )
		{
			msg += "\nAlso the following parameter changes will be used for subsequent alignments: " + alignparms + ".";
		}
		msg +=  "\nProceed with save?";
		int n = JOptionPane.showConfirmDialog(
			    this,
			    msg,"",
			    JOptionPane.YES_NO_OPTION);
		
		if(n == JOptionPane.YES_OPTION)
			return true;
	
		return false;
	}

	private void showHelp() {
		Utilities.showHTMLPage(this, "Project Parameter Help", 
				(theMode == MODE_FPC ? "/html/FpcParamHelp.html" : "/html/ProjParamHelp.html"));
/*			JOptionPane.showMessageDialog(
				    this,
				    (theMode == MODE_FPC ? HELP_TEXT_FPC : HELP_TEXT_PSEUDO),
				    "Help",
				    JOptionPane.PLAIN_MESSAGE);	*/		
	}

	private boolean isRealignmentRequired(StringBuffer parms) {
		if(!bIsLoaded) return false;
		boolean required = false;
		for(int x=0; x<valueBuffer.length; x++) {
			if(theFields[x].isRealignField() && !theFields[x].getValue().equals(valueBuffer[x]) && !theFields[x].isDefaultValue())
			{
				parms.append(theFields[x].getLabel() + ",");
				required = true;
			}
		}
		if (required) parms = parms.deleteCharAt(parms.length()-1); // chop final ,
		return required;
	}
	
	private boolean isReloadRequired(StringBuffer parms) {
		if(!bIsLoaded) return false;
		boolean required = false;
		for(int x=0; x<valueBuffer.length; x++) {
			if(theFields[x].isReloadField() && !theFields[x].getValue().equals(valueBuffer[x]) && !theFields[x].isDefaultValue())
			{
				parms.append(theFields[x].getLabel() + ",");
				required = true;
			}
		}
		if (required) parms = parms.deleteCharAt(parms.length()-1); // chop final ,
		return required;
	}
	
	private int theMode = -1;
	private String theDisplayName = "";
	private String theDBName = "";
	private boolean bIsDiscarded = true;
	private boolean bIsLoaded = false;
	private PropertyComponent [] theFields;
	private String [] valueBuffer = null;
	private String [] thePseudoProjectNames = null;
	private JButton btnSave = null, btnCancel = null, btnHelp = null;
	private CaretListener theListener = null;
	
	public class PropertyComponent extends JPanel{
		private static final long serialVersionUID = -6643140570421332451L;
		
		private static final int MODE_TEXT = 0;
		private static final int MODE_LIST = 1;
		private static final int MODE_MULTI_LINE = 2;
		private static final int MODE_FILE_LIST = 3;
		
		private static final int LABEL_WIDTH = 150;
		private static final int FIELD_COLUMNS = 10;
		
		//Used for a table list of values
		public PropertyComponent(String label, String values, boolean needsReload, boolean needsRealign, boolean singleSelect) {
			bReload = needsReload;
			bRealign = needsRealign;
			bSingleSelect = singleSelect;
			
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
//				((JTextArea)theComponent).setMaximumSize(((JTextArea)theComponent).getPreferredSize());
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
			if(nMode == MODE_TEXT)
				return ((JTextField)theComponent).getText();
			if(nMode == MODE_MULTI_LINE)
				return ((JTextArea)theComponent).getText();
			if(nMode == MODE_FILE_LIST)
				return ((FileListTable)theComponent).getValue();
			//MODE_LIST
			return (String)((JComboBox)theComponent).getSelectedItem();
		}
		
		public void setValue(String value) {
			isDefault = false;
			if(nMode == MODE_TEXT)
				((JTextField)theComponent).setText(value);
			else if(nMode == MODE_MULTI_LINE)
				((JTextArea)theComponent).setText(value);
			else if(nMode == MODE_FILE_LIST)
				((FileListTable)theComponent).setValue(value);
			else { //MODE_LIST
				((JComboBox)theComponent).setSelectedItem(value);
				if(!((String)((JComboBox)theComponent).getSelectedItem()).equals(value)) {
					if (!value.equals("")) // CAS 1/4/18 get this message when order_against=NONE
						System.out.println("Cannot select " + value + " for " + theLabel.getText() + " defaulting to " + ((String)((JComboBox)theComponent).getSelectedItem()));
					isDefault = true;
				}
			}
		}
		
		public boolean isRealignField() { return bRealign; }
		public boolean isReloadField() { return bReload; }
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
		private boolean bSingleSelect = false;
		private int nMode = -1;
	}
	
	private class FileListTable extends JPanel {
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
					JFileChooser cf = new JFileChooser();
					cf.setMultiSelectionEnabled(false);
					if(theMode == MODE_FPC)
						cf.setCurrentDirectory(new File(ProjectManagerFrameCommon.DATA_PATH + "fpc/" + theDBName));
					else
						cf.setCurrentDirectory(new File(ProjectManagerFrameCommon.DATA_PATH + "pseudo/" + theDBName));
					if(cf.showOpenDialog(getInstance()) == JFileChooser.APPROVE_OPTION) {
						String path = cf.getSelectedFile().getAbsolutePath().trim();
						if(path.length() > 0)
							theModel.addRow(path);
						theTable.setVisible(false);
						theTable.setVisible(true);
					}
					updateButtons();
				}
			});
	        
	        btnAddDir = new JButton("Add Directory");
	        btnAddDir.setBackground(Color.WHITE);
	        btnAddDir.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					JFileChooser cf = new JFileChooser();
					cf.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
					cf.setMultiSelectionEnabled(false);
					if(theMode == MODE_FPC)
						cf.setCurrentDirectory(new File(ProjectManagerFrameCommon.DATA_PATH + "fpc/" + theDBName));
					else
						cf.setCurrentDirectory(new File(ProjectManagerFrameCommon.DATA_PATH + "pseudo/" + theDBName));
					if(cf.showOpenDialog(getInstance()) == JFileChooser.APPROVE_OPTION) {
						String path = cf.getSelectedFile().getAbsolutePath().trim();
						if(path.length() > 0)
							theModel.addRow(path + "/");
						theTable.setVisible(false);
						theTable.setVisible(true);
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
	        
	        buttonRow.add(btnAddFile);
	        buttonRow.add(Box.createHorizontalStrut(20));
	        buttonRow.add(btnAddDir);
	        buttonRow.add(Box.createHorizontalStrut(20));
	        buttonRow.add(btnRemove);
	        
	        add(sPane);
	        add(buttonRow);
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
	}
}
