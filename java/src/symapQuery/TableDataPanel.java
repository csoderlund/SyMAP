package symapQuery;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.sql.ResultSet;
import java.util.Iterator;
import java.util.Vector;
import java.util.HashMap;
import java.util.Enumeration;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import database.DBconn2;
import util.ErrorReport;
import util.Jcomp;
import util.Jhtml;
import util.Utilities;
import symap.Globals;
import symap.drawingpanel.SyMAP2d;
import symap.mapper.HfilterData;
import symap.sequence.Sequence;

/**************************************************
 * Perform query and display results in table
 * SyMAPQueryFrame calls QueryPanel which call this on "Run Search" passing it the query. 
 *    The loadDataFromDatabase runs the Query, and DBdata creates the rows.
 *  
 * CAS560 add theLastTable; CAS561 Jcomp graphics, add Group
 */
public class TableDataPanel extends JPanel {
	private static final long serialVersionUID = -3827610586702639105L;
	private static final int ANNO_COLUMN_WIDTH = 100, ANNO_PANEL_WIDTH = 900;
	private static final int GEN_COLUMN_WIDTH = 100;
	private static final int showBLOCK=1, showSET=2, showGRP=3; // showREGION=0, CAS555 add showGrp
	
	static protected SortTable theLastTable = null; //  CAS560 add to keep using last order of columns for session
	
	// called by SymapQueryFrame
	protected TableDataPanel(SyMAPQueryFrame parentFrame, String resultName,  boolean [] selCols,
			String query, String sum, boolean isSingle) {
		this.theParentFrame = parentFrame;
		this.theName 		= resultName;
		
		theQueryPanel 		= theParentFrame.getQueryPanel();
		this.isSingle		= isSingle;
		this.theQuery 		= query;
		this.theSummary 	= sum;
		this.isCollinearSz	= theQueryPanel.isCollinear();
		this.isGroup		= theQueryPanel.isGroup();
		
		if(selCols != null) {
			theOldSelCols = new boolean[selCols.length];
			for(int x=0; x<selCols.length; x++)
				theOldSelCols[x] = selCols[x];
		}
		
		colSelectChange = new ActionListener() { // when a column is selected or deselected
			public void actionPerformed(ActionEvent arg0) {
				setTable(false, false);
		        showTable();
			}
		};
		buildTableStart(theQuery);
	}
	/************************************************************/
	private void buildTableStart(String theQuery) {
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
       
        theAnnoKeys = AnnoData.loadProjAnnoKeywords(theParentFrame);
        
        setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
        setAlignmentY(LEFT_ALIGNMENT);
        showProgress();
		buildThread = new Thread(new Runnable() {
			public void run() {
				try {
					ResultSet rset = loadDataFromDatabase(theQuery); // closed in DBdata
					if(rset != null) {
						buildTable(rset);
					}
					else {
						return;
					}
					setColFromOldSelect();
					showTable();
					finish(); 

					if(isVisible()) { //Makes the table appear
						setVisible(false);
						setVisible(true);
					}
				} catch (Exception e) {ErrorReport.print(e, "Show initial table");}
			}
		});
		buildThread.setPriority(Thread.MIN_PRIORITY);
		buildThread.start();
	}
	private void finish() { // Has to be done from thread, but not in thread
		theParentFrame.updateResultCount(this);
	}
	private void showTable() {
    	removeAll();
    	repaint();
    	loadStatus = null;
    	sPane = new JScrollPane();
    	sPane.setViewportView(theTable);
    	theTable.getTableHeader().setBackground(Color.WHITE);
    	sPane.setColumnHeaderView(theTable.getTableHeader());
    	
    	sPane.getViewport().setBackground(Color.WHITE);
    	sPane.getHorizontalScrollBar().setBackground(Color.WHITE);
    	sPane.getVerticalScrollBar().setBackground(Color.WHITE);
    	sPane.getHorizontalScrollBar().setForeground(Color.WHITE);
    	sPane.getVerticalScrollBar().setForeground(Color.WHITE);
    	
    	if (statsPanel == null) statsPanel = new JPanel();
    	
    	add(tableButtonPanel);	add(Box.createVerticalStrut(10));
    	add(tableStatusPanel);
    	add(sPane);				add(Box.createVerticalStrut(10));
    	add(statsPanel);		add(Box.createVerticalStrut(10));
    	add(columnPanel);		add(Box.createVerticalStrut(10));
    	add(columnButtonPanel);
    	
    	if(theTable != null)
    		if(theTable.getRowCount() > 0)
    			updateTableStatus(theName + ": " + theTable.getRowCount() + " rows   Filter: "  + theSummary);
    		else
    			updateTableStatus(theName + ": No results   Filter: " + theSummary);

    	invalidate();
    	validateTable();
	}
	
	private TableDataPanel getInstance() { return this; }

	/***************************************************************
	 * Build table
	 */
    private void buildTable(ResultSet rs) {
    	// panels added in showTable
		tableButtonPanel = 		createTableButtonPanel();
		tableStatusPanel = 		createTableStatusPanel();
		columnPanel			= 	createColumnsPanel();
		columnButtonPanel 	=	createColumnButtonPanel();
		theTableData 		= 	new TableData("Table" + (nTableID++), this);
		 
		/** CREATE ROWS **/
        if(rs != null) { 
        	HashMap <Integer, Integer> geneCntMap = new HashMap <Integer, Integer> (); 
        	HashMap <String, String> projMap = new HashMap  <String, String> (); // ComputePgeneF & ComputeMulti;; CAS555 int->string
        		
         	Vector  <DBdata> rowsFromDB =  DBdata.loadRowsFromDB(rs, theParentFrame.getProjects(),
         			theParentFrame.getQueryPanel(), theAnnoKeys.getColumns(false /*displayname*/), loadStatus,
         			geneCntMap, projMap); // Inputs for Summary
         				 
        	theTableData.addRowsWithProgress(rowsFromDB, loadStatus);
        		
            SummaryPanel sp = new SummaryPanel(rowsFromDB, theQueryPanel, projMap, geneCntMap);
            statsPanel = sp.getPanel();
            
            rowsFromDB.clear(); // NOTE: IF want to use later, then the Stats need to be removed from DBdata
        }
        	    
        theTableData.setColumnHeaders(theParentFrame.getAbbrevNames(), theAnnoKeys.getColumns(true /*abbrev*/), isSingle);
        theTableData.finalizeX();

        String [] columns = getSelColsUnordered(); 
        TableData tData = TableData.createData(columns, theTableData, this);
		theTable = new SortTable(tData);
        theTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        theTable.autofitColumns();
        	
        ColumnHeaderToolTip header = new ColumnHeaderToolTip(theTable.getColumnModel());
        FieldData theFields = FieldData.getFields(true, true); // flags do not matter here
        header.setToolTips(theFields.getDBFieldDescriptions());
        theTable.setTableHeader(header);

        selListener = new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent arg0) {
				setPanelSelected();
			}
		};
        theTable.getSelectionModel().addListSelectionListener(selListener);

        MultiLineHeaderRenderer renderer = new MultiLineHeaderRenderer();
        Enumeration<TableColumn> en = theTable.getColumnModel().getColumns();
        while (en.hasMoreElements()) {
          ((TableColumn)en.nextElement()).setHeaderRenderer(renderer);
        } 
    }
    private JPanel createTableButtonPanel() {
    	JPanel buttonPanel = Jcomp.createPagePanel();
    	
    	JPanel topRow = Jcomp.createRowPanel();
    	topRow.add(Jcomp.createLabel("Selected: ")); topRow.add(Box.createHorizontalStrut(5));
    	
    // Show CAS520
    	btnShowRow = Jcomp.createButton("Show", "Popup with every value listed"); // CAS520 add to view data in row
 	    btnShowRow.addActionListener(new ActionListener() {
	   		public void actionPerformed(ActionEvent arg0) {
	   			showRow();
	   		}
		});  
 	    topRow.add(btnShowRow);					topRow.add(Box.createHorizontalStrut(6));
 	    
    	btnShowAlign = Jcomp.createButton("Align", "Align selected sequences using MUSCLE"); 
 	    btnShowAlign.addActionListener(new ActionListener() {
    		public void actionPerformed(ActionEvent arg0) {
    			showAlignment();
    		}
 		});  
 	    topRow.add(btnShowAlign);				topRow.add(Box.createHorizontalStrut(6));
	 	    
	    btnShowSynteny = Jcomp.createButton("View 2D", "View in 2D display"); 
	    btnShowSynteny.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				showSynteny();		
			}
		}); 
	    topRow.add(btnShowSynteny);				topRow.add(Box.createHorizontalStrut(1));
	    topRow.add(Jcomp.createLabel("as"));	topRow.add(Box.createHorizontalStrut(1));
	    cmbSynOpts = new JComboBox <String> ();
	    cmbSynOpts.setBackground(Color.WHITE);
	    cmbSynOpts.addItem("Region (kb)");  // showREGION
	    cmbSynOpts.addItem("Synteny Block");// showBLOCK
	    cmbSynOpts.addItem("Collinear Set");// showSET
	    cmbSynOpts.addItem("Group");        // showGRP
	    cmbSynOpts.setSelectedIndex(0);
	    topRow.add(cmbSynOpts);					topRow.add(Box.createHorizontalStrut(1));
	    
		txtMargin = Jcomp.createTextField((Globals.MAX_2D_DISPLAY/1000)+"", 3);
	    topRow.add(txtMargin);
	    topRow.add(Jcomp.createLabel("kb")); 	topRow.add(Box.createHorizontalStrut(2));
	    
	    chkHigh = Jcomp.createCheckBox("High", "If checked, highlight hit on 2D", true);
	    topRow.add(chkHigh); 					topRow.add(Box.createHorizontalStrut(5));
	    
	    btnShowAlign.setEnabled(false);
	    btnShowSynteny.setEnabled(false);
	    btnShowRow.setEnabled(false);
	    cmbSynOpts.setEnabled(false);
	    txtMargin.setEnabled(false);
	    chkHigh.setEnabled(false);
	    
	    btnHelp = Jhtml.createHelpIconQuery(Jhtml.result);
		topRow.add(Box.createHorizontalStrut(15));
		topRow.add(btnHelp);
	    
		topRow.setAlignmentX(LEFT_ALIGNMENT);
		topRow.setMaximumSize(topRow.getPreferredSize());
		
		JPanel botRow = Jcomp.createRowPanel();
		botRow.add(Jcomp.createLabel("Table (or selected): ")); botRow.add(Box.createHorizontalStrut(2));
	
	    btnExport = Jcomp.createButton("Export...", "Export selected rows or all rows");
	    btnExport.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				popupExport();
			}
		});
	   
	    btnUnSelectAll = Jcomp.createButton("Unselect All", "Unselect all selected rows");
	    btnUnSelectAll.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				theTable.getSelectionModel().clearSelection();
				setPanelSelected();
			}
		});     
	   
	    btnReport = Jcomp.createButton("Report...", "Gene Report");
	    btnReport.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				popupReport();
			}
		});     
	    
	    botRow.add(btnExport);		botRow.add(Box.createHorizontalStrut(10));
	    botRow.add(btnUnSelectAll);	botRow.add(Box.createHorizontalStrut(50));
	    botRow.add(btnReport);	
	    
		buttonPanel.add(topRow);
		buttonPanel.add(Box.createVerticalStrut(10));
		buttonPanel.add(botRow);
		buttonPanel.setMaximumSize(buttonPanel.getPreferredSize());
		
	    return buttonPanel;
	}
    private JPanel createColumnsPanel() {
    	columnPanel = Jcomp.createPagePanel();
    	columnPanel.setVisible(false);
	
		generalColSelectPanel = 	createGeneralSelectPanel();
		spLocColSelectPanel = 		createSpLocSelectPanel();
		spAnnoColSelectPanel = 		createSpAnnoSelectPanel();
		
    	columnPanel.add(generalColSelectPanel);	columnPanel.add(Box.createVerticalStrut(10));
    	columnPanel.add(spLocColSelectPanel);	columnPanel.add(Box.createVerticalStrut(10));
    	columnPanel.add(spAnnoColSelectPanel);
    
    	columnPanel.setBorder(BorderFactory.createTitledBorder("Columns"));
    	columnPanel.setMaximumSize(columnPanel.getPreferredSize());
		
		return columnPanel;
    }
    /***********************************************
     * General columns pertaining to hits and blocks
     */
    private JPanel createGeneralSelectPanel() {
    	JPanel retVal = Jcomp.createPagePanel();
    
    	String []  genCols   = FieldData.getGeneralColHead();
    	boolean [] genColDef = FieldData.getGeneralColDefaults();
    	String []  genDesc   = FieldData.getGeneralColDesc();
    	int nCol = FieldData.getGenColumnCount(isSingle);
    	int newRow = (nCol>1) ? genCols.length-FieldData.N_GEN_HIT : 1; 
    	
    	JPanel row=null;
    	chkGeneralFields = new JCheckBox[nCol];
    	for(int x=0; x<nCol; x++) {
    		if (x==0 || x==newRow) {
    			if (x==newRow) retVal.add(row);
    			
    			row = Jcomp.createRowPanel();
    		}
    		else {
    			int width = GEN_COLUMN_WIDTH - chkGeneralFields[x-1].getPreferredSize().width;
        		if (width > 0) row.add(Box.createHorizontalStrut(width));	
        		row.add(Box.createHorizontalStrut(3)); 
    		}
    		chkGeneralFields[x] = Jcomp.createCheckBox(genCols[x], genDesc[x], genColDef[x]);
    		chkGeneralFields[x].addActionListener(colSelectChange);
    		if (x==0) {									
    			chkGeneralFields[x].setSelected(true);
    			chkGeneralFields[x].setEnabled(false); // CAS520 cannot remove because doesn't work to
    		}
    		row.add(chkGeneralFields[x]);
    	}
    	retVal.add(row);
    	
    	retVal.setBorder(BorderFactory.createTitledBorder("General"));
    	retVal.setMaximumSize(retVal.getPreferredSize());

    	return retVal;
    }
    /**************************************************
     * Species: 
     * 		Single: Chr Gstart Gend Glen GStrand Gene#
     * 		Pair: 	Chr Gstart Gend Glen GStrand Gene# Hstart Hend Hlen
     */
    private JPanel createSpLocSelectPanel() {
    	JPanel page = Jcomp.createPagePanel();
    	
    	String [] species = theParentFrame.getAbbrevNames();
    	String [] colHeads = FieldData.getSpeciesColHead(isSingle);
		
		String [] colDesc= FieldData.getSpeciesColDesc(isSingle);
		int cntCol = FieldData.getSpColumnCount(isSingle); // single/pairs do not have the same #columns
		
    	chkSpeciesFields = new JCheckBox[species.length][cntCol];
    	boolean [] colDefs = FieldData.getSpeciesColDefaults(isSingle);
    	
    	for(int x=0; x<species.length; x++) {
    		JPanel row = Jcomp.createRowPanel();
        	
    		row.add(createSpeciesLabel(species[x]));
    		row.add(Box.createHorizontalStrut(5));
    		
    		for (int i=0; i<cntCol; i++) { // CAS561 was two loops
    			chkSpeciesFields[x][i] = Jcomp.createCheckBox(colHeads[i], colDesc[i], colDefs[i]);   
    			chkSpeciesFields[x][i].addActionListener(colSelectChange);
    			row.add(chkSpeciesFields[x][i]);
        		row.add(Box.createHorizontalStrut(4));
    		}
        	page.add(row);
        	if (x < species.length - 1) page.add(new JSeparator());
    	}
    	String loc = (isSingle) ? "Gene" : "Gene&Hit"; 
    	page.setBorder(BorderFactory.createTitledBorder(loc + " Info"));
    	page.setMaximumSize(page.getPreferredSize());

    	return page;
    }
    private JLabel createSpeciesLabel(String name) {
    	JLabel label = new JLabel(name,JLabel.RIGHT);
    	label.setFont(new Font("Monospaced",Font.PLAIN, 11));
    	return label;
    }
    /*******************************************
     *  Annotation from gff file
     */
    private JPanel createSpAnnoSelectPanel() {
    	JPanel retVal = Jcomp.createRowPanel();
    	
    	chkAnnoFields = new Vector<Vector<JCheckBox>> ();
    	
    	int [] speciesIDs = theAnnoKeys.getSpeciesIDList();
    	
    	// Check selected
    	for(int x=0; x<speciesIDs.length; x++) {
    		Vector<JCheckBox> annoCol = new Vector<JCheckBox> ();
    		
    		for(int y=0; y<theAnnoKeys.getNumberAnnosForSpecies(speciesIDs[x]); y++){
    			
    			String annotName = theAnnoKeys.getAnnoIDforSpeciesAt(speciesIDs[x], y);
    			String d = (annotName.contentEquals(Q.All_Anno)) ? 
    					"All annotation from GFF file" : "Column heading is keyword from GFF file";
    			JCheckBox chkCol = Jcomp.createCheckBox(annotName, d, false);
    			
    			chkCol.addActionListener(colSelectChange);
    			annoCol.add(chkCol);
    		}
    		chkAnnoFields.add(annoCol);
    	}
    	
    	JPanel colPanel = Jcomp.createPagePanel();
    	
    	Iterator<Vector<JCheckBox>> spIter = chkAnnoFields.iterator();
    	int pos = 0;
    	while (spIter.hasNext()) {
        	JPanel row = Jcomp.createRowPanel();
        	
        	// Species name
        	String abbrev = theAnnoKeys.getSpeciesAbbrevByID(speciesIDs[pos]);
        	JLabel spLabel = createSpeciesLabel(abbrev);
        	row.add(spLabel); 		row.add(Box.createHorizontalStrut(10));
        	
        	int curWidth = ANNO_COLUMN_WIDTH;
        	Iterator<JCheckBox> annoIter = spIter.next().iterator();
        	while (annoIter.hasNext()) {
        		JCheckBox chkTemp = annoIter.next();
        		
        		if(curWidth + ANNO_COLUMN_WIDTH > ANNO_PANEL_WIDTH) {
        			colPanel.add(row);
        	    	row = Jcomp.createRowPanel();
        	    	
        	    	row.add(Box.createHorizontalStrut(ANNO_COLUMN_WIDTH + 10));
        	    	curWidth = ANNO_COLUMN_WIDTH + 10;
        		}
        		row.add(chkTemp);
        		
        		curWidth += ANNO_COLUMN_WIDTH;
        		row.add(Box.createHorizontalStrut(5));
        	}
        	colPanel.add(row);
        	if(spIter.hasNext())
        		colPanel.add(new JSeparator());
        	pos++;
    	}
    	retVal.add(colPanel);
    	
    	retVal.setBorder(BorderFactory.createTitledBorder("Gene Annotation"));
    	retVal.setMaximumSize(retVal.getPreferredSize());
    	return retVal;
    }
    private JPanel createColumnButtonPanel() {
    	buttonPanel = Jcomp.createRowPanel();
    	
		btnShowCols = Jcomp.createButton("Select Columns", "Displays panel of columns to select");
		btnShowCols.addActionListener(new ActionListener() {
		public void actionPerformed(ActionEvent e) {
			if(columnPanel.isVisible()) {
				btnShowCols.setText("Select Columns");
				columnPanel.setVisible(false);
				btnClearCols.setVisible(false);
				btnDefaultCols.setVisible(false);
				btnGroupCols.setVisible(false);
				btnShowStats.setVisible(true);
			}
			else {
				btnShowCols.setText("Hide Columns");
				columnPanel.setVisible(true);
				btnClearCols.setVisible(true);
				btnDefaultCols.setVisible(true);
				btnGroupCols.setVisible(true);
				btnShowStats.setVisible(false);
			}
			showTable();
		}});
		
		btnClearCols = Jcomp.createButton("Clear", "Clear all selections");
		btnClearCols.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				clearColumns();
		}});
		btnClearCols.setVisible(false);
		
		btnDefaultCols = Jcomp.createButton("Defaults", "Set default columns");
		btnDefaultCols.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				defaultColumns();
		}});
		btnDefaultCols.setVisible(false);
		
		btnGroupCols = Jcomp.createButton("Arrange", "Arrange similar columns together with gene columns first");
		btnGroupCols.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				groupColumns();
		}});
		btnGroupCols.setVisible(false);
	
		btnShowStats = Jcomp.createButton("Hide Stats", "Do not show statistics panel");
		btnShowStats.addActionListener(new ActionListener() {
		public void actionPerformed(ActionEvent e) {
			if(statsPanel.isVisible()) {
				statsPanel.setVisible(false);
				btnShowStats.setText("Show Stats");
			}
			else {
				statsPanel.setVisible(true);
				btnShowStats.setText("Hide Stats");
			}
		}});

		txtStatus = new JTextField(400); 
		txtStatus.setEditable(false);
		txtStatus.setBorder(BorderFactory.createEmptyBorder());
		txtStatus.setBackground(Color.WHITE);
		
    	buttonPanel.add(btnShowCols);    buttonPanel.add(Box.createHorizontalStrut(5));
    	buttonPanel.add(btnClearCols);   buttonPanel.add(Box.createHorizontalStrut(5));
    	buttonPanel.add(btnDefaultCols); buttonPanel.add(Box.createHorizontalStrut(5));
    	buttonPanel.add(btnGroupCols);   buttonPanel.add(Box.createHorizontalStrut(10));
    	buttonPanel.add(btnShowStats);   buttonPanel.add(Box.createHorizontalStrut(10)); //So the resized button doesn't get clipped
    	buttonPanel.add(txtStatus);
    	
    	buttonPanel.setMaximumSize(buttonPanel.getPreferredSize());
    	return buttonPanel;
    }
    //private void setStatus(String desc) {txtStatus.setText(desc);}
    
    private JPanel createTableStatusPanel() {
    	JPanel thePanel = Jcomp.createRowPanel();
    	
    	rowCount = new JTextField(400);
    	rowCount.setBackground(Color.WHITE);
    	rowCount.setBorder(BorderFactory.createEmptyBorder());
    	rowCount.setEditable(false);
    	rowCount.setMaximumSize(rowCount.getPreferredSize());
    	rowCount.setAlignmentX(LEFT_ALIGNMENT);
    
    	thePanel.add(rowCount);
    	thePanel.setMaximumSize(thePanel.getPreferredSize());
	
    	return thePanel;
    }
    private void showProgress() {
    	removeAll();
    	repaint();
    	setBackground(Color.WHITE);
    	loadStatus = new JTextField(40);
    	loadStatus.setBackground(Color.WHITE);
    	loadStatus.setMaximumSize(loadStatus.getPreferredSize());
    	loadStatus.setEditable(false);
    	loadStatus.setBorder(BorderFactory.createEmptyBorder());
    	JButton btnStop = new JButton("Stop");
    	btnStop.setBackground(Color.WHITE);
    	btnStop.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				if(buildThread != null)
					loadStatus.setText("Cancelled");
			}
		});
	    add(loadStatus);
	    add(btnStop);
	    validateTable();
	}
    private void validateTable() {
		validate();
    }
    private void updateTableStatus(String status) {
	    rowCount.setText(status);
    }
    /***********************************************************
     * Column
     */
    private void clearColumns() {
    	for (int i=1; i<chkGeneralFields.length; i++) chkGeneralFields[i].setSelected(false);
    	
    	int cntCol = FieldData.getSpColumnCount(isSingle);
    	for (int i=0; i<chkSpeciesFields.length; i++) {
    		for (int j=0; j<cntCol; j++)
    			chkSpeciesFields[i][j].setSelected(false);
    	}
    	for(int x=0; x<chkAnnoFields.size(); x++) {
    		for(int y=0; y<chkAnnoFields.get(x).size(); y++) {
    			chkAnnoFields.get(x).get(y).setSelected(false);
    		}
    	}
    	setTable(true, false);
    	showTable();
    }
    private void defaultColumns() {// CAS543 add
    	boolean [] genColDef = FieldData.getGeneralColDefaults();
    	for (int i=1; i<chkGeneralFields.length; i++) chkGeneralFields[i].setSelected(genColDef[i]);
    	
    	boolean [] spColDef = FieldData.getSpeciesColDefaults(isSingle);
    	int cntCol = FieldData.getSpColumnCount(isSingle);
    	for (int i=0; i<chkSpeciesFields.length; i++) {
    		for (int j=0; j<cntCol; j++)
    			chkSpeciesFields[i][j].setSelected(spColDef[j]);
    	}
    	for(int x=0; x<chkAnnoFields.size(); x++) {
    		for(int y=0; y<chkAnnoFields.get(x).size(); y++) {
    			chkAnnoFields.get(x).get(y).setSelected(false);
    		}
    	}
    	setTable(true, false);
    	showTable();
    }
    // CAS561 add to group like columns together
    private void groupColumns() {
    	setTable(false, true);
    	showTable();
    }
    
	// Build table, clearColumns, defaultColumns, setColFromOldSelect, actionPerformed (on colChange)
	private void setTable(boolean isFirst, boolean isGroup) {
		String [] uoSelCols = getSelColsUnordered(); // order found in columns panel	
		String [] columns;
		
		// CAS560 add using the same order as the last table created or change of columns
		// theLastTable seems to stay active even when Query is exited and another display is used, but could be GC'ed
		try { 
			if (isGroup) columns = 								TableData.arrangeColumns(uoSelCols, isSingle, theAnnoKeys);
			else if (!isSingle && theLastTable!=null) columns = TableData.orderColumns(theLastTable, uoSelCols);
			else columns = (isFirst) ? uoSelCols :         		TableData.orderColumns(theTable, uoSelCols);
		}
		catch (Exception e) {
			columns = uoSelCols;
		}
		TableData tData = TableData.createData(columns, theTableData, getInstance());
		theTable = new SortTable(tData);
        theTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        theTable.autofitColumns();
    
        theTable.getSelectionModel().addListSelectionListener(selListener);
		
        theTable.setTableHeader(new ColumnHeaderToolTip(theTable.getColumnModel()));

        MultiLineHeaderRenderer renderer = new MultiLineHeaderRenderer();
        Enumeration<TableColumn> en = theTable.getColumnModel().getColumns();
        while (en.hasMoreElements()) {
          ((TableColumn)en.nextElement()).setHeaderRenderer(renderer);
        } 
        if (!isSingle) theLastTable = theTable; // saving the single order is not worth it as the species could change, etc
	}

    private String [] getSelColsUnordered() {
		Vector<String> retVal = new Vector<String> ();
		
		for(int x=0; x<chkGeneralFields.length; x++) {
			if(chkGeneralFields[x].isSelected())
				retVal.add(chkGeneralFields[x].getText());
		}
		String [] species = theParentFrame.getAbbrevNames(); 
		for(int x=0; x<species.length; x++) {
			for(int y=0; y<chkSpeciesFields[x].length; y++) {
				if(chkSpeciesFields[x][y].isSelected()) {
					retVal.add(species[x]+"\n"+chkSpeciesFields[x][y].getText());
				}
			}
		}
		String [] annoColumns = theAnnoKeys.getColumns(true);
		for(int x=0; x<getNumAnnoCheckBoxes(); x++) {
			if(getAnnoCheckBoxAt(x).isSelected()) {
				retVal.add(annoColumns[x]);
			}
		}
		return retVal.toArray(new String[retVal.size()]);
	}
	
    private void setColFromOldSelect() { // called at buildTableStart 
    	if (theOldSelCols == null) return; 
    	
    	int targetPos = 0, oldN = theOldSelCols.length;
    	try {
	    	for(int x=0; x<chkGeneralFields.length  && targetPos<oldN; x++) {
	    		chkGeneralFields[x].setSelected(theOldSelCols[targetPos]);
	    		targetPos++;
	    	}    	
		  	for(int x=0; x<chkSpeciesFields.length; x++) {
	    		for(int y=0; y<chkSpeciesFields[x].length  && targetPos<oldN; y++) {
	    			chkSpeciesFields[x][y].setSelected(theOldSelCols[targetPos]);
	    			targetPos++;
	    		}
	    	}
		   // CAS532 see SyMAPQueryFrame.getLastColumns; add check for targetPos 
	    	for(int x=0; x<chkAnnoFields.size() && targetPos<oldN; x++) {
	    		for(int y=0; y<chkAnnoFields.get(x).size() && targetPos<oldN; y++) {
	    			chkAnnoFields.get(x).get(y).setSelected(theOldSelCols[targetPos]);
	    			targetPos++;
	    		}
	    	}
    	}
    	catch (Exception e) {ErrorReport.print(e, "Selections: " + theOldSelCols.length + " " + targetPos);}
    	
    	theOldSelCols = null;
    	setTable(true, false);
	}
    private JCheckBox getAnnoCheckBoxAt(int pos) {
    	Iterator <Vector <JCheckBox>> spIter = chkAnnoFields.iterator();
    	
    	while (spIter.hasNext()) {
    		Vector<JCheckBox> spAnnoCol = spIter.next();
    		
    		if (spAnnoCol.size() <= pos) pos -= spAnnoCol.size();
    		else return spAnnoCol.get(pos);
    	}
    	return null;
    }
    
    private int getNumAnnoCheckBoxes() {
    	int total = 0;
    	Iterator<Vector<JCheckBox>> spIter = chkAnnoFields.iterator();
    	
    	while(spIter.hasNext()) {
    		total += spIter.next().size();
    	}
    	return total;
    }
    
    protected void setPanelEnabled(boolean enable) {
    	theTable.setEnabled(enable); // does not disabled sorting columns
    	rowCount.setEnabled(enable);
  
    	txtMargin.setEnabled(enable);
    	btnShowSynteny.setEnabled(enable);
    	cmbSynOpts.setEnabled(enable);
    	chkHigh.setEnabled(enable);
    	btnShowRow.setEnabled(enable);
    	btnExport.setEnabled(enable);
    	btnUnSelectAll.setEnabled(enable);
    	btnReport.setEnabled(enable);
    	btnShowAlign.setEnabled(enable);
    	btnShowCols.setEnabled(enable);
    	btnHelp.setEnabled(enable);
    	
    	for(int x=0; x<chkGeneralFields.length; x++)
    		chkGeneralFields[x].setEnabled(enable);
    		
    	for(int x=0; x<chkSpeciesFields.length; x++) {
    		for(int y=0; y<chkSpeciesFields[x].length; y++)
    			chkSpeciesFields[x][y].setEnabled(enable);
    	}
    }
    protected void setPanelSelected() {
    	boolean b = (theTable.getSelectedRowCount() == 1)  ? true : false;
    	btnShowRow.setEnabled(b);
    	
		if (isSingle) return;
	
	 	txtMargin.setEnabled(b);
    	btnShowSynteny.setEnabled(b);
    	cmbSynOpts.setEnabled(b);
    	chkHigh.setEnabled(b);
    	
    	b = (theTable.getSelectedRowCount() >= 1)  ? true : false;
    	btnShowAlign.setEnabled(b);
    }
  
    /***************************************************************
     * Show alignment
     */
    private void showAlignment() {
		if (isSingle) return;
		if (theTable.getSelectedRowCount() == 0)  return;
		
		Thread inThread = new Thread(new Runnable() {
			public void run() {
				try {
					setPanelEnabled(false);
					boolean reset=false;
					if(theTable.getSelectedRowCount() == 0) {
						theTable.selectAll();
						reset = true;
					}
					int [] selRows = theTable.getSelectedRows();
					int selNum = selRows.length;
					if (selNum>500) {
						if (!Utilities.showContinue("Align sequence", 
							"Selected " + selNum + " row to align. This is a slow function. \n" +
							"It may take over a minute to align 500 rows of sequences.")) 
						{
							if(reset)theTable.getSelectionModel().clearSelection();
							setPanelEnabled(true);
							return;
						}
					}
					Vector<String> theTags = new Vector<String> (); 
					Vector<String> theNames = new Vector<String> ();
					Vector<String> theLines = new Vector<String> ();
					Vector<String> theSeqs = new Vector<String> ();
					
					String [] fields = {"", "Hit#", "Species" ,"Chr", "Hit Start", "Hit End", "Gene#"};
					int [] justify =   {1,   0,      1,          0,     0,       0,    1}; // 1 left justify
					int nRow = selRows.length*2;
				    int nCol=  fields.length;
				    String [][] rows = new String[nRow][nCol];
				    int r=0;
				    
					TmpRowData rd = new TmpRowData(getInstance());
					for(int x=0; x<selRows.length; x++) {
						if (!rd.loadRow(selRows[x])) return; // CAS505 was incorrectly x
						
						for (int i=0; i<2; i++) {
							String tag = String.format("%s %s %d %d", rd.spAbbr[i], rd.chrNum[i], rd.start[i], rd.end[i]);
							if(theTags.contains(tag)) continue;
							theTags.add(tag);
							
							String name =  String.format("%d. %s.%s", (r+1), rd.spAbbr[i], rd.chrNum[i]);
							theNames.add(name);
							
							theSeqs.add(theParentFrame.getSequence(rd.start[i], rd.end[i], rd.chrIdx[i]));
							
							int c=0;
							rows[r][c++]=(r+1) + ".";
							rows[r][c++]=rd.hitnum+"";
							rows[r][c++]=rd.spAbbr[i];
							rows[r][c++]=rd.chrNum[i];
							rows[r][c++]=String.format("%,d",rd.start[i]);
							rows[r][c++]=String.format("%,d",rd.end[i]);
							rows[r][c++]=rd.geneTag[i];
							r++;
						}
					}
					String sum= r + " seqs";
					Utilities.makeTable(theLines, nCol, nRow, fields, justify, rows);
			
					theParentFrame.addAlignmentTab(getInstance(), theNames.toArray(new String[0]), 
							theLines.toArray(new String[0]), theSeqs.toArray(new String[0]), sum);
					theNames.clear();
					theLines.clear();
					theSeqs.clear();
					
					setPanelEnabled(true);
				}
				catch(Exception e) {ErrorReport.print(e, "Show alignment");}
			}
		});
		inThread.start();
    }
    /**************************************************************
     * Show Row in popup
     */
    private void showRow() {
		try{
			//if (isSingle) return; // CAS540 it works for singles
			if (theTable.getSelectedRowCount() != 1)  return;
			
			int row = theTable.getSelectedRows()[0];
			TmpRowData rd = new TmpRowData(getInstance());
			String outLine = rd.loadRowAsString(row);
			Utilities.displayInfoMonoSpace(this, "Row#" + (row+1), outLine, false);
		}
    	catch(Exception e) {ErrorReport.print(e, "Show row");}
    }
   
    /**************************************************************
     * Show Synteny
     */
    private void showSynteny() {
		try{
			if (isSingle) return;
			if (theTable.getSelectedRowCount() != 1)  return;
    			
			int row = theTable.getSelectedRows()[0];
			TmpRowData rd = new TmpRowData(getInstance());
			if (!rd.loadRow(row)) return;
				
			// for the get statements
			grpIdxVec.clear();
			
			double track1Start=0, track2Start=0, track2End=0, track1End=0, pad=300.0;
			double [] coords;
			HfilterData hd = new HfilterData (); // CAS520 change from dotplot.FilterData
			
			int item = (cmbSynOpts.getSelectedIndex());
			if (item==showSET) {
				if (rd.collinearN==0) {
					Utilities.showWarningMessage("The selected row does not belong to a collinear set.");
					return;
				}
				coords = loadCollinearCoords(rd.collinearN, rd.chrIdx[0], rd.chrIdx[1]);
				hd.setForQuery(false, true, false);  // block, set, region
			}
			else if (item==showBLOCK) {
				if (rd.blockN==0) {
					Utilities.showWarningMessage("The selected row does not belong to a synteny block.");
					return;
				}
				coords = loadBlockCoords(rd.blockN, rd.chrIdx[0], rd.chrIdx[1]);
				hd.setForQuery(true, false, false);  // block, set, region
			}
			else if (item==showGRP) { // CAS555
				if (rd.groupN==0) {
					Utilities.showWarningMessage("The selected row does not belong to a group.");
					return;
				}
				coords = rd.loadGroup(grpIdxVec); // assigns hits to grpIdxVec
				hd.setForQuery(false, false, true);  // block, set, region
			}
			else {
				coords = new double [4];
				coords[0] = rd.start[0]; coords[1] = rd.end[0];
				coords[2] = rd.start[1]; coords[3] = rd.end[1];
				pad = Double.parseDouble(txtMargin.getText()) * 1000;
				hd.setForQuery(false, false, true);  // block, set, region
			}
			track1Start = coords[0] - pad; if (track1Start<0) track1Start=0;
			track1End   = coords[1] + pad; 
			track2Start = coords[2] - pad; if (track2Start<0) track2Start=0;
			track2End   = coords[3] + pad;
			
			synStart1 = rd.start[0]; synEnd1 = rd.end[0];
			synStart2 = rd.start[1]; synEnd2 = rd.end[1]; // CAS516 was synEnd1

			int p1Idx = (Integer) rd.spIdx[0];
			int p2Idx = (Integer) rd.spIdx[1];

			int grp1Idx = (Integer) rd.chrIdx[0];
			int grp2Idx = (Integer) rd.chrIdx[1];
			
			// create new drawing panel; CAS543 quit setting Sfilter Show_Annotation because is static
			SyMAP2d symap = new SyMAP2d(theParentFrame.getDBC(), getInstance());
			symap.getDrawingPanel().setTracks(2); // CAS550 set exact number
			symap.getDrawingPanel().setHitFilter(1,hd); // template for Mapper HfilterData, which is already created
			
			Sequence s1 = symap.getDrawingPanel().setSequenceTrack(1, p2Idx, grp2Idx, Color.CYAN);
			Sequence s2 = symap.getDrawingPanel().setSequenceTrack(2, p1Idx, grp1Idx, Color.GREEN);
			s1.setAnnotation(); s2.setAnnotation(); // CAS543 was changing static; now changes individual object
			
			symap.getDrawingPanel().setTrackEnds(1, track2Start, track2End);
			symap.getDrawingPanel().setTrackEnds(2, track1Start, track1End);
			symap.getFrame().showX();
		} catch(Exception e) {ErrorReport.print(e, "Create 2D Synteny");}
    }
    /******************************************************
 	 * Next four are called by mapper to display the synteny, which is weird given that
 	 * the values are already sent. Since only the last row is stored here, can only select one row at a time 
 	 * CAS516 change to one call instead of 4
 	 */
     public boolean isHitSelected(int idx, int s1, int e1, int s2, int e2) {
    	 if (!chkHigh.isSelected()) return false;
 
    	 if (grpIdxVec.contains(idx)) return true;	// CAS555 add to highlight all group
    	 if  (s1==synStart1 && e1==synEnd1 && s2==synStart2 && e2==synEnd2) return true;
    	 if  (s2==synStart1 && e2==synEnd1 && s1==synStart2 && e1==synEnd2) return true;
    	 return false;
     }
 	 
    /***************************************************************
     * XXX Database
     */
	private ResultSet loadDataFromDatabase(String theQuery) {
        try {
        	DBconn2 dbc2 = theParentFrame.getDBC();
        	
        	if(loadStatus.getText().equals("Cancelled")) return null; 
        	loadStatus.setText("Performing database search....");
        	
        	try {
        		/**** EXECUTE ***/
        		ResultSet rs = dbc2.executeQuery(theQuery);  
        		if (Globals.TRACE)	{
        			loadDataTESTwrite(theQuery, rs, isSingle); 
        			rs = dbc2.executeQuery(theQuery);	// CAS560 can not longer reset to start of search with new JDBC
        		}
        		return rs;
        	}
        	catch(Exception e)	{ErrorReport.print(e, "Load Data1"); return null;}
        } 
        catch(Exception e) {   ErrorReport.print(e, "Load Data2");  return null;}
	}
	private void loadDataTESTwrite(String strQuery, ResultSet rs, boolean isOrphan) {
		if (strQuery!=null) {
		 	try {
        		BufferedWriter w = new BufferedWriter(new FileWriter("zTest_sql.log"));
        		w.write(theSummary + "\n");
        		w.write("\n" + strQuery+ "\n\n");
        		w.close();
        	} catch(Exception e){};
		}
		if (rs==null) return;
		
		if (isOrphan) {
			try {
        		BufferedWriter w = new BufferedWriter(new FileWriter("zTest_orphan_results.log"));
        		w.write(theSummary + "\n");
        		w.write("Row\tAStart\tAnnoIdx\tID\n");
        		int cnt=0;
        		while (rs.next()) {
        			String n = rs.getString(Q.ANAME);
        			if (n!=null) {
        				int i = n.indexOf(";");
        				if (i>0) n = n.substring(0, i);
        			} else n="--";
        			
        			w.write(cnt + "\t" + rs.getString(Q.ASTART) + "\t" +  rs.getString(Q.AIDX) + "\t" + n  
        					+ "\t" + rs.getInt(Q.ANUMHITS) + "\n");
        			cnt++;
        		}
        		w.close();
        		System.out.println("MySQL Results to zTest_orphan_results.log " + cnt);
        	} catch(Exception e){ErrorReport.print(e, "results to file");	};
		}
		else {
		 	try {
        		BufferedWriter w = new BufferedWriter(new FileWriter("zTest_results.log"));
        		w.write(theSummary + "\n");
        		String x1 = String.format("%4s  %5s (%5s)   %2s,%2s  %2s,%2s %2s   %6s,%6s %6s  %8s\n",
        				"Row","Hnum", "Hidx",  "p1", "p2", "g1", "g2", "ag", "Hanno1", "Hanno2",   "Anno", "Gene");
        		w.write(x1);
        		int cnt=0;
        		while (rs.next()) {
        			String tag = rs.getString(Q.AGENE);
        			if (tag==null) tag = "none";
        			else if (tag.contains("(")) tag = tag.substring(0, tag.indexOf("("));
        			
        			String block = rs.getString(Q.BNUM);
        			if (block==null) block="0";
        			
        			int a1=rs.getInt(Q.ANNOT1IDX), a2=rs.getInt(Q.ANNOT2IDX), a=rs.getInt(Q.AIDX);
        			String star = (a1!=a && a2!=a) ? "***" : "   ";
        			
        			int grp1 = rs.getInt(Q.GRP1IDX), grp2 = rs.getInt(Q.GRP2IDX), grp = rs.getInt(Q.AGIDX);
        			int side = (grp1==grp) ? 1 : 2;
        			
        			String x = String.format("%4d  %5d (%5d)   %2d,%2d  %2d,%2d %2d   %6d,%6d %6d  %8s %s%d",
        					cnt, rs.getInt(Q.HITNUM),   rs.getInt(Q.HITIDX), 
        					rs.getInt(Q.PROJ1IDX),  rs.getInt(Q.PROJ2IDX),
        					grp1,  grp2, grp, 
        					a1, a2, a, tag, star, side);
        					
        			w.write(x + "\n");
        			cnt++;
        		}
        		w.close();
        		
        		System.out.println("MySQL Results to zTest_results.log " + cnt);
        	} catch(Exception e){ErrorReport.print(e, "results to file");	};
		}
	}
	
	/*******************************************************************
	 * 2D-view Block
	 */
	private double [] loadBlockCoords(int block, int idx1, int idx2) {
		double [] coords = null;
		try {
			DBconn2 dbc2 = theParentFrame.getDBC();
			
			ResultSet rs = dbc2.executeQuery("select start1, end1, start2, end2 from blocks "
					+ " where blocknum="+block+ " and grp1_idx=" + idx1 + " and grp2_idx=" + idx2);
			if (rs.next()) {
				coords = new double [4];
				for (int i=0; i<4; i++) coords[i] = rs.getDouble(i+1);
			}

			if (coords==null) { // try opposite way
				rs = dbc2.executeQuery("select start2, end2, start1, end1 from blocks "
						+ " where blocknum="+block+ " and grp1_idx=" + idx2 + " and grp2_idx=" + idx1);
				if (rs.next()) {
					coords = new double [4];
					for (int i=0; i<4; i++) coords[i] = rs.getDouble(i+1);
				}
			}
			rs.close();
			return coords;
		}
		catch(Exception e) {ErrorReport.print(e, "set project annotations"); return null;}
	}
	/*******************************************************************
	 * 2D-view Collinear
	 */
	private double [] loadCollinearCoords(int set, int idx1, int idx2) {
		double [] coords = null;
		try {
			DBconn2 dbc2 = theParentFrame.getDBC();
		
			ResultSet rs = dbc2.executeQuery("select start1, end1, start2, end2 from pseudo_hits "
					+ "where runnum=" + set + " and grp1_idx=" + idx1 + " and grp2_idx=" + idx2);
			double start1=Double.MAX_VALUE, end1=-1, start2=Double.MAX_VALUE, end2=-1, d;
			while (rs.next()) {
				d = rs.getInt(1);	if (d<start1) start1 = d;
				d = rs.getInt(2);	if (d>end1)   end1 = d;
				
				d = rs.getInt(3); 	if (d<start2) start2 = d;
				d = rs.getInt(4);	if (d>end2)   end2 = d;
			}
			if (start1==Double.MAX_VALUE) { // try flipping idx1 and idx2
				rs = dbc2.executeQuery("select start1, end1, start2, end2 from pseudo_hits "
						+ "where runnum=" + set + " and grp1_idx=" + idx2 + " and grp2_idx=" + idx1);
				while (rs.next()) {
					d = rs.getInt(1);	if (d<start2) start2 = d;
					d = rs.getInt(2);	if (d>end2)   end2 = d;
					
					d = rs.getInt(3); 	if (d<start1) start1 = d;
					d = rs.getInt(4);	if (d>end1)   end1 = d;
				}
			}
			if (start1!=Double.MAX_VALUE) {
				coords = new double [4];
				coords[0]=start1; coords[1]=end1; coords[2]=start2; coords[3]=end2;
			}
			rs.close();
			return coords;
		}
		catch(Exception e) {ErrorReport.print(e, "set project annotations"); return null;}
	}
	
    /****************************************************************
     * Public
     */
	protected void sortMasterColumn(String columnName) {
    	int index = theTableData.getColHeadIdx(columnName);
    	theTableData.sortByColumn(index, !theTableData.isAscending(index));
	}
   
    public String getName() { return theName; }
    
    protected int getNumResults() {
		if(theTableData != null) return theTableData.getNumRows();
		return 0;
	}
 
 	protected String [] getSummary() { 
 		String [] retVal = new String[2];
 		retVal[0] = theName + ": " + getNumResults(); // #rows (shown on Results Page)
 		retVal[1] = theSummary;
 		return retVal;
 	}
	protected boolean isSingle() {return isSingle;}
 	protected boolean [] getColumnSelections() {
		try {
			int genColCnt = FieldData.getGenColumnCount(isSingle); 
			int spColCnt = FieldData.getSpColumnCount(isSingle);
	    	int numCols = genColCnt + (chkSpeciesFields.length * spColCnt);
	    	for(int x=0; x<chkAnnoFields.size(); x++) {
	    		numCols += chkAnnoFields.get(x).size();
	    	}
	    	
	    	boolean [] retVal = new boolean[numCols];
	    	int targetPos = 0;
	    	for(int x=0; x<genColCnt; x++) {
	    		retVal[targetPos] = chkGeneralFields[x].isSelected();
	    		targetPos++;
	    	}   	
	    	for(int x=0; x<chkSpeciesFields.length; x++) {
	    		for(int y=0; y<spColCnt; y++) {
	    			retVal[targetPos] = chkSpeciesFields[x][y].isSelected();
	    			targetPos++;
	    		}
	    	}	    	
	    	for(int x=0; x<chkAnnoFields.size(); x++) {
	    		for(int y=0; y<chkAnnoFields.get(x).size(); y++) {
	    			retVal[targetPos] = chkAnnoFields.get(x).get(y).isSelected();
	    			targetPos++;
	    		}
	    	}
	    	return retVal;
		}
		catch (Exception e) {ErrorReport.print(e, "Getting columns"); return null;}
 	}
 	
   /****************************************************************
    * Classes
    */
    private class ColumnHeaderToolTip extends JTableHeader {
		private static final long serialVersionUID = -2417422687456468175L;
		private String [] toolTips = null;
		
    	public ColumnHeaderToolTip(TableColumnModel model) {
    		super(model);
    		addMouseListener(new MouseAdapter() {
            	public void mouseClicked(MouseEvent evt) 
            	{ 
            		SortTable table = (SortTable)((JTableHeader)evt.getSource()).getTable(); 
            		TableColumnModel colModel = table.getColumnModel(); 
            		int vColIndex = colModel.getColumnIndexAtX(evt.getX()); 
            		int mColIndex = table.convertColumnIndexToModel(vColIndex);
                			            		
            		table.sortAtColumn(mColIndex);
                }   
    		});
    	}
    	public void setToolTips(String [] labels) {
    		toolTips = labels;
    	}
    	public String getToolTipText(MouseEvent e) {
    		int column = columnAtPoint(e.getPoint());
    		if(toolTips != null && toolTips.length > column && column >= 0)
    			return toolTips[column];
    		else
    			return super.getToolTipText();
    	}
    }
    
    private class MultiLineHeaderRenderer extends JList <Object> implements TableCellRenderer {// CAS555 add <Object>
		private static final long serialVersionUID = 3118619652018757230L;

		public MultiLineHeaderRenderer() {
    	    setOpaque(true);
    	    setBorder(BorderFactory.createLineBorder(Color.BLACK));
    	    setBackground(Color.WHITE);
    	    ListCellRenderer <Object> renderer = getCellRenderer();
    	    ((JLabel)renderer).setHorizontalAlignment(JLabel.CENTER);
    	    setCellRenderer(renderer);
    	}
    	// called repeatedly; when column is moved, changed, etc; once for each column
    	public Component getTableCellRendererComponent(JTable table, Object value,
    	                   boolean isSelected, boolean hasFocus, int row, int column) {
   
    	    setFont(table.getFont());
    	    String str = (value == null) ? "" : value.toString();
    	    BufferedReader br = new BufferedReader(new StringReader(str));
    	    
    	    String line;
    	    Vector<String> v = new Vector<String>();
    	    try {
    	    	while ((line = br.readLine()) != null) {
    	    		v.addElement(line);
    	    	}
    	    } catch (IOException ex) {ErrorReport.print(ex, "Render table");}
    	    setListData(v);
    	    return this;
    	}
    }
    
    /********************************************************
     * Report popup and files
     */
    private void popupReport() {
    	TableReport rp = new TableReport(getInstance());
    	rp.setVisible(true);
    }
    /***********************************************************************
     ** Export file; CAS556 export put in separate file.
     *********************************************************************/
    private void popupExport() {
		final TableExport ex = new TableExport(this, theParentFrame.title ); // CAS560 add title for 1st line of export
		ex.setVisible(true);
		final int mode = ex.getSelection();
		
		if (mode == ex.ex_cancel) return;
		else if (mode==ex.ex_csv) ex.writeCSV();
		else if (mode==ex.ex_html) ex.writeHTML();
		else if (mode==ex.ex_fasta) ex.writeFASTA();
			
		setPanelSelected();
	}
	
    /**************************************************************
     * Private variable
     */
    protected SyMAPQueryFrame theParentFrame = null;
	protected QueryPanel      theQueryPanel = null;
	
    protected SortTable theTable = null;
    protected TableData theTableData = null;
    private JScrollPane sPane = null;
    private JTextField rowCount = null;
    
    private JTextField loadStatus = null, txtMargin = null;
    private JCheckBox chkHigh = null;
    private JButton btnShowSynteny = null, btnShowAlign = null, btnShowRow = null; 
    private JButton btnUnSelectAll = null, btnExport = null, btnReport = null;
    private JComboBox <String> cmbSynOpts = null;
    
    private ListSelectionListener selListener = null;
    private Thread buildThread = null;
    
    private JCheckBox [] 				chkGeneralFields = null;
    private JCheckBox [][] 				chkSpeciesFields = null;
    private Vector<Vector<JCheckBox>> 	chkAnnoFields = null;
   
    private ActionListener colSelectChange = null;
    private boolean [] theOldSelCols = null; 
    
	private JPanel tableButtonPanel = null, tableStatusPanel = null;
	private JPanel generalColSelectPanel = null, spLocColSelectPanel = null;
	private JPanel spAnnoColSelectPanel = null, statsPanel = null;
	private JPanel buttonPanel = null;
	
	private JPanel columnPanel = null, columnButtonPanel = null;
	private JButton btnShowCols = null, btnClearCols=null, btnDefaultCols=null, btnGroupCols=null;
	private JButton btnShowStats = null, btnHelp = null;
	private JTextField txtStatus = null;
    
	private AnnoData theAnnoKeys = null;
  
	protected String theSummary = "";
	private String theName = "",  theQuery = "";
	protected boolean isSingle = false; // Cannot use theQueryPanel.isOrphan because only good for last query
    
	private static int nTableID = 0;
	
	private int synStart1, synStart2, synEnd1, synEnd2; // showSynteny
	private Vector <Integer> grpIdxVec = new Vector <Integer> (); // CAS555 to highlight groups
	protected boolean isCollinearSz=false, isGroup=false;	// CAS556 for TableReport; the QueryPanel can change, so need to save this
}
