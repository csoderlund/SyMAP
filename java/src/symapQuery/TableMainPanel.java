package symapQuery;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.Point;
import java.awt.Rectangle;
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

/**************************************************
 * Perform query and display results in table
 * QueryFrame calls QueryPanel which call this on "Run Query" passing it the query. 
 *    The loadDataFromDatabase runs the Query, and DBdata creates the rows.
 *  
 * CAS560 add theLastTable; CAS561 Jcomp graphics, add Group; CAS563/4 move Align to UtilSelect; 
 * CAS564 add Search; move 2D to UtilSelect; implement Stop
 */
public class TableMainPanel extends JPanel {
	private static final long serialVersionUID = -3827610586702639105L;
	private static final int ANNO_COLUMN_WIDTH = 100, ANNO_PANEL_WIDTH = 900;
	private static final int GEN_COLUMN_WIDTH = 100;
	protected static final int showREGION=0, showSET=1, showBLOCK=2,  showGRP=3; // CAS555 add showGrp
	
	static protected TableSort theLastTable = null; //  CAS560 add to keep using last order of columns for session
	
	// called by QueryFrame
	protected TableMainPanel(QueryFrame parentFrame, String tabName,  boolean [] selCols,
			String query, String sum, boolean isSingle) {
		this.queryFrame = parentFrame;
		this.theTabName 	= tabName;
		
		queryPanel 			= queryFrame.getQueryPanel();
		this.isSingle		= isSingle;
		this.theQuery 		= query;
		this.theSummary 	= sum;
		this.isCollinear	= queryPanel.isCollinear();
		this.isMultiN		= queryPanel.isMultiN();
		this.isClustN		= queryPanel.isClustN();
		
		if(selCols != null) {
			theOldSelCols = new boolean[selCols.length];
			for(int x=0; x<selCols.length; x++)
				theOldSelCols[x] = selCols[x];
		}
		colSelectChange = new ActionListener() { // when a column is selected or deselected
			public void actionPerformed(ActionEvent arg0) {
				searchPanel = null;
		
				Point viewPosition = sPane.getViewport().getViewPosition(); // CAS564 add before all setTable
	            int row = theTable.rowAtPoint(viewPosition);
	                
				setTable(false, false);
		        displayTable(row);
			}
		};
		buildTableThread(theQuery);
	}
	/************************************************************/
	private void buildTableThread(String theQuery) {
		
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
        setAlignmentY(LEFT_ALIGNMENT);
        
        createLoadStatus();
        
        bStopped = bDone = false;
        theAnnoKeys = AnnoData.loadProjAnnoKeywords(queryFrame);
        
		buildThread = new Thread(new Runnable() {
			public void run() {
				try {
					ResultSet rs = loadDataFromDatabase(theQuery); // closed in DBdata
					if (rs==null) return;
					if (bStopped) {rs.close(); return;} // usually rs.close in DBdata load in buildTable
					
					buildTable(rs); 				    // calls DBdata which does rs.close	
					
					if (bStopped) return;	
					
					setColFromOldSelect();
					displayTable(-1);		// -1 do not scroll to a rwo
					buildFinish(); 			

					if (isVisible()) { 		//Makes the table appear
						setVisible(false);
						setVisible(true);
					}
				} 
				catch (Exception e) {ErrorReport.print(e, "Show initial table");}
			}
		});
		buildThread.setPriority(Thread.MIN_PRIORITY);
		buildThread.start();
	}
	/** replace tab: add to result table;  Has to be done from thread, but not in thread **/
	private void buildFinish() { 					// CAS564 replace table specific with general
		int numRows =  (theTableData != null) ? theTableData.getNumRows() : 0;
		
		String oldTab = theTabName+":"; 			// same as QueryFrame.makeTabTable
		String newTab = theTabName + ": " + numRows;
		
		String [] resultVal = new String[2];
 		resultVal[0] = newTab; 						// left of result table panel
 		resultVal[1] = theSummary;					// right of result table panel with query filters
 		
		queryFrame.updateTabText(oldTab, newTab, resultVal);
		
		queryFrame.getQueryPanel().setQueryButton(true);
	}
	/** Called when User clicks Stop; CAS564 was not doing anything **/
	private void buildStop() { // 
			if (buildThread==null || bDone) return;
		
			queryPanel.setQueryButton(true);
			
			queryFrame.removeResult(this); 		// remove from left side
	
			loadStatus.setText(Q.stop); // looks for this word in ComputeClust/Multi
			bStopped=true;				// others check this variable; set before interrupt
			
			try {
				buildThread.interrupt(); 
			}
			catch (Exception e) {};
	}
	
	/***************************************************************
	 * Build table
	 */
    private void buildTable(ResultSet rs) {
    	// panels added in showTable
		tableButtonPanel = 		createTopButtonPanel();
		tableStatusPanel = 		createTableStatusPanel();
		columnPanel			= 	createColumnsPanel();
		columnButtonPanel 	=	createColumnButtonPanel();
		theTableData 		= 	new TableData("Table" + (nTableID++), this);
		 
		/** CREATE ROWS **/
        if(rs != null) { 
        	HashMap <Integer, Integer> geneCntMap = new HashMap <Integer, Integer> (); 
        	HashMap <String, String> projMap = new HashMap  <String, String> (); // ComputePgeneF & ComputeMulti;; CAS555 int->string
        		
         	Vector  <DBdata> rowsFromDB =  DBdata.loadRowsFromDB(getInstance(), rs, queryFrame.getProjects(),
         			queryFrame.getQueryPanel(), theAnnoKeys.getColumns(false /*displayname*/), loadStatus,
         			geneCntMap, projMap); 				// Inputs for Summary
         
         	bDone=true; 								// make sure the timing does not let them Stop now (unless too late)
         	
         	if (bStopped) {if (rowsFromDB!=null) rowsFromDB.clear(); return;} // may be too late
         	
        	theTableData.addRowsWithProgress(rowsFromDB, loadStatus);
        	
            SummaryPanel sp = new SummaryPanel(rowsFromDB, queryPanel, projMap, geneCntMap);
            statsPanel = sp.getPanel();
            
            rowsFromDB.clear(); // NOTE: IF want to use later, then the Stats need to be removed from DBdata
        }
        if (bStopped) return;
        
        theTableData.setColumnHeaders(queryFrame.getAbbrevNames(), theAnnoKeys.getColumns(true /*abbrev*/), isSingle);
        theTableData.finalizeX();

        String [] columns = getSelColsUnordered(); 
        TableData tData = TableData.createData(columns, theTableData, this);
		theTable = new TableSort(tData);
        theTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        theTable.autofitColumns();
        	
        ColumnHeaderToolTip header = new ColumnHeaderToolTip(theTable.getColumnModel());
        TableColumns theFields = TableColumns.getFields(true, true); // flags do not matter here
        header.setToolTips(theFields.getDBFieldDescriptions());
        theTable.setTableHeader(header);

        selListener = new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent arg0) {
				setRowSelected();
			}
		};
        theTable.getSelectionModel().addListSelectionListener(selListener);

        MultiLineHeaderRenderer renderer = new MultiLineHeaderRenderer();
        Enumeration<TableColumn> en = theTable.getColumnModel().getColumns();
        while (en.hasMoreElements()) {
          ((TableColumn)en.nextElement()).setHeaderRenderer(renderer);
        } 
    }
    /**********************************************************************/
	private void displayTable(int rowIndex) {
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
    	
		if (theTable.getRowCount() > 0)
			updateTableStatus(theTabName + ": " + theTable.getRowCount() + " rows   Filter: "  + theSummary);
		else
			updateTableStatus(theTabName + ": No results   Filter: " + theSummary);
    		
    	if (rowIndex>0) {// CAS564 add retain table position
    		Rectangle cellRect = theTable.getCellRect(rowIndex, 0, true);       
    	    theTable.scrollRectToVisible(cellRect);
    	}
    	invalidate();
    	validateTable();
	}
	
	private TableMainPanel getInstance() { return this; }

    private JPanel createTopButtonPanel() {
    	JPanel buttonPanel = Jcomp.createPagePanel();
    
    // Top row
    	JPanel topRow = Jcomp.createRowPanel();
    	topRow.add(Jcomp.createLabel("Selected: ")); topRow.add(Box.createHorizontalStrut(4));
    	 
    	btnShowRow = Jcomp.createButton("Show", "Popup with every value listed"); // view data in row
 	    btnShowRow.addActionListener(new ActionListener() {
	   		public void actionPerformed(ActionEvent arg0) {
	   			showRow();
	   		}
		});  
 	    topRow.add(btnShowRow);					topRow.add(Box.createHorizontalStrut(3));
 	    
    	btnShowMSA = Jcomp.createButton("MSA... ", "Align selected sequences using MUSCLE or MAFFT"); // setMsaButton
 	    btnShowMSA.addActionListener(new ActionListener() {
    		public void actionPerformed(ActionEvent arg0) {
    			showMSA();
    		}
 		});  
 	    topRow.add(btnShowMSA);				topRow.add(Box.createHorizontalStrut(8));// CAS563 5->8 for linux
	 	 
 	    // View 2D
 	    topRow.add(new JSeparator(JSeparator.VERTICAL)); topRow.add(Box.createHorizontalStrut(2)); // Needs box on Linux
	    btnShowSynteny = Jcomp.createButton("View 2D", "View in 2D display"); 
	    btnShowSynteny.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				showSynteny();		
			}
		}); 
	    topRow.add(btnShowSynteny);				topRow.add(Box.createHorizontalStrut(1));
	    lblSynAs = Jcomp.createLabel("as", "Drop-box defines coordinates shown", false);
	    topRow.add(lblSynAs);	topRow.add(Box.createHorizontalStrut(1));
	    cmbSynOpts = new JComboBox <String> ();  cmbSynOpts.setBackground(Color.WHITE);
	    cmbSynOpts.addItem("Region");  		// showREGION=0
	    cmbSynOpts.addItem("Collinear");	// showSET=1
	    cmbSynOpts.addItem("Block");		// showBLOCK=2
	    cmbSynOpts.addItem("Group(chr)");   // showGRP=3
	    cmbSynOpts.setSelectedIndex(0);
	    topRow.add(cmbSynOpts);					topRow.add(Box.createHorizontalStrut(1));
	    cmbSynOpts.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				boolean b = (cmbSynOpts.getSelectedIndex()==showREGION);
				txtSynRegion.setEnabled(b);
			}
		}); 
	    
		txtSynRegion = Jcomp.createTextField((Globals.MAX_2D_DISPLAY/1000)+"","Distance on both sides of selected hit", 2);
	    topRow.add(txtSynRegion); topRow.add(Box.createHorizontalStrut(1));
	    lblSynKb = Jcomp.createLabel("kb", "Number * 1000", false);
	    topRow.add(lblSynKb); 	topRow.add(Box.createHorizontalStrut(2));
	    
	    chkSynHigh = Jcomp.createCheckBox("High", "If checked, highlight hit on 2D", true);
	    topRow.add(chkSynHigh); 				topRow.add(Box.createHorizontalStrut(1));
	    
	    chkSynGn = Jcomp.createCheckBox("Gene#", "If checked, show Gene#, else show Annotation", true);
	    topRow.add(chkSynGn); 				topRow.add(Box.createHorizontalStrut(3));
	    
	    btnShowRow.setEnabled(false); btnShowMSA.setEnabled(false); btnShowSynteny.setEnabled(false);
	    cmbSynOpts.setEnabled(false); txtSynRegion.setEnabled(false); chkSynHigh.setEnabled(false); chkSynGn.setEnabled(false);
	    
	    topRow.add(new JSeparator(JSeparator.VERTICAL)); topRow.add(Box.createHorizontalStrut(15));
	    
	    // Help
	    btnHelp = Jhtml.createHelpIconQuery(Jhtml.result); 
		topRow.add(btnHelp);
	    
		topRow.setAlignmentX(LEFT_ALIGNMENT);
		topRow.setMaximumSize(topRow.getPreferredSize());
		
	// 2nd row
		JPanel botRow = Jcomp.createRowPanel();
		botRow.add(Jcomp.createLabel("Table (or selected): ")); botRow.add(Box.createHorizontalStrut(2));
	
	    btnExport = Jcomp.createButton("Export...", "Export selected rows or all rows");
	    btnExport.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				showExport();
			}
		});   
	    btnUnSelectAll = Jcomp.createButton("Unselect All", "Unselect all selected rows");
	    btnUnSelectAll.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				theTable.getSelectionModel().clearSelection();
				setRowSelected();
			}
		});  
	   
	    // title repeated in setBtnReport
	    if (isCollinear)  btnReport = Jcomp.createButton("Collinear Report...", "Create a collinear gene report from the rows. Display a popup or write to file.");
	    else if (isMultiN)  btnReport = Jcomp.createButton("Multi-hit Report...", "Create a multi-hit gene report from the rows. Display a popup or write to file.");
	    else if (isClustN)  btnReport = Jcomp.createButton("Cluster Report...", "Create a cluster gene report from the rows. Display a popup or write to file.");
	    else btnReport = Jcomp.createButton("Gene Report...", "Create a gene pair report from the rows. Display a popup or write to file.");
	    btnReport.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				showReport();
			}
		});     
	    
	    btnSearch = Jcomp.createButton("Search...", "Search for string and go to that row.");
	    btnSearch.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				showSearch();
			}
		}); 
	    
	    botRow.add(btnExport);		botRow.add(Box.createHorizontalStrut(5));
	    botRow.add(btnUnSelectAll);	botRow.add(Box.createHorizontalStrut(70));
	    botRow.add(btnReport);		botRow.add(Box.createHorizontalStrut(8));
	    botRow.add(btnSearch);
	    
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
    
    	String []  genCols   = TableColumns.getGeneralColHead();
    	boolean [] genColDef = TableColumns.getGeneralColDefaults();
    	String []  genDesc   = TableColumns.getGeneralColDesc();
    	int nCol = TableColumns.getGenColumnCount(isSingle);
    	int newRow = (nCol>1) ? genCols.length-TableColumns.N_GEN_HIT : 1; 
    	
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
    	
    	String [] species = queryFrame.getAbbrevNames();
    	String [] colHeads = TableColumns.getSpeciesColHead(isSingle);
		
		String [] colDesc= TableColumns.getSpeciesColDesc(isSingle);
		int cntCol = TableColumns.getSpColumnCount(isSingle); // single/pairs do not have the same #columns
		
    	chkSpeciesFields = new JCheckBox[species.length][cntCol];
    	boolean [] colDefs = TableColumns.getSpeciesColDefaults(isSingle);
    	
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
			displayTable(-1);
		}});
		
		btnClearCols = Jcomp.createButton("Clear", "Clear all selections");
		btnClearCols.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				searchPanel=null;
				clearColumns();
		}});
		btnClearCols.setVisible(false);
		
		btnDefaultCols = Jcomp.createButton("Defaults", "Set default columns");
		btnDefaultCols.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				searchPanel=null;
				defaultColumns();
		}});
		btnDefaultCols.setVisible(false);
		
		btnGroupCols = Jcomp.createButton("Arrange", "Arrange similar columns together with gene columns first");
		btnGroupCols.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				searchPanel=null;
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
    private void createLoadStatus() {
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
				buildStop();
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
    	
    	int cntCol = TableColumns.getSpColumnCount(isSingle);
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
    	displayTable(-1);
    }
    private void defaultColumns() {// CAS543 add
    	boolean [] genColDef = TableColumns.getGeneralColDefaults();
    	for (int i=1; i<chkGeneralFields.length; i++) chkGeneralFields[i].setSelected(genColDef[i]);
    	if (isCollinear) chkGeneralFields[TableColumns.COLLINEAR].setSelected(true); // CAS563 add for Set Default
    	if (isMultiN || isClustN) chkGeneralFields[TableColumns.GROUP].setSelected(true);
    	
    	boolean [] spColDef = TableColumns.getSpeciesColDefaults(isSingle);
    	int cntCol = TableColumns.getSpColumnCount(isSingle);
    	for (int i=0; i<chkSpeciesFields.length; i++) {
    		for (int j=0; j<cntCol; j++)
    			chkSpeciesFields[i][j].setSelected(spColDef[j]);
    	}
    	for(int x=0; x<chkAnnoFields.size(); x++) {
    		for(int y=0; y<chkAnnoFields.get(x).size(); y++) {
    			chkAnnoFields.get(x).get(y).setSelected(false);
    		}
    	}
    	int row=-1;
    	if (sPane!=null) {
    		Point viewPosition = sPane.getViewport().getViewPosition();
    		row = theTable.rowAtPoint(viewPosition);
    	}    
    	setTable(true, false);
    	displayTable(row);
    }
    // group like columns together; CAS561 add
    private void groupColumns() {
    	int row=-1;
    	if (sPane!=null) {
    		Point viewPosition = sPane.getViewport().getViewPosition();
    		row = theTable.rowAtPoint(viewPosition);
    	} 
    	setTable(false, true);
    	displayTable(row);
    }
    
	// Build table, clearColumns, defaultColumns, setColFromOldSelect, actionPerformed (on colChange)
	private void setTable(boolean isFirst, boolean isGroup) {
		if (bStopped) return; // to be sure...
		
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
		theTable = new TableSort(tData);
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
		String [] species = queryFrame.getAbbrevNames(); 
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
		   // see QueryFrame.getLastColumns; add check for targetPos;  CAS532 
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
    // This disables doing anything with table while something is running
    protected void setPanelEnabled(boolean enable) {
    	theTable.setEnabled(enable); // does not disabled sorting columns
    	rowCount.setEnabled(enable);
  
    	txtSynRegion.setEnabled(enable);
    	btnShowSynteny.setEnabled(enable);
    	cmbSynOpts.setEnabled(enable);
    	chkSynHigh.setEnabled(enable); chkSynGn.setEnabled(enable);
    	btnShowRow.setEnabled(enable);
    	btnExport.setEnabled(enable);
    	btnUnSelectAll.setEnabled(enable);
    	btnReport.setEnabled(enable);
    	if (!bMSArun) btnShowMSA.setEnabled(enable);
    	btnShowCols.setEnabled(enable);
    	btnHelp.setEnabled(enable);
    	
    	for(int x=0; x<chkGeneralFields.length; x++)
    		chkGeneralFields[x].setEnabled(enable);
    		
    	for(int x=0; x<chkSpeciesFields.length; x++) {
    		for(int y=0; y<chkSpeciesFields[x].length; y++)
    			chkSpeciesFields[x][y].setEnabled(enable);
    	}
    	setRowSelected(); // CAS563 add
    }
  
    protected void setRowSelected() {
    	// assume 2 species
    	boolean b1 = (theTable.getSelectedRowCount() == 1)  ? true : false;
    	btnShowRow.setEnabled(b1);
		if (isSingle) return;
		
		btnShowSynteny.setEnabled(b1);
    	cmbSynOpts.setEnabled(b1);	 lblSynAs.setEnabled(b1);
    	txtSynRegion.setEnabled(b1); lblSynKb.setEnabled(b1);
    	chkSynHigh.setEnabled(b1);	 chkSynGn.setEnabled(b1);
		if (b1) { // cannot easily disable items
			if (cmbSynOpts.getSelectedIndex()!=showREGION) txtSynRegion.setEnabled(false);
		}
    	boolean bn = (theTable.getSelectedRowCount() >= 1)  ? true : false;
    	if (!bMSArun) btnShowMSA.setEnabled(bn);
    	
    	if (b1 && !bn) return;
    	
    	// can work for 2 or 3 species
    	boolean b2 = (theTable.getSelectedRowCount() == 2)  ? true : false;
    	if (!b2) return;
    	if (getSharedRef(true)==null) return;
    	
    	btnShowSynteny.setEnabled(b2);
    	cmbSynOpts.setEnabled(b2);	lblSynAs.setEnabled(b2);
    	txtSynRegion.setEnabled(b2);lblSynKb.setEnabled(b2);
    	chkSynHigh.setEnabled(b2);  chkSynGn.setEnabled(b2);
    	if (cmbSynOpts.getSelectedIndex()!=showREGION) txtSynRegion.setEnabled(false);
    }
   
    /****************************************
     * Buttons functions and associated methods; CAS564 tried to make them more consistent  
     **********************************/
    /**************************************************************
     * Show Row in popup
     */
    private void showRow() {
		try{
			if (theTable.getSelectedRowCount() != 1)  return;
			
			int row = theTable.getSelectedRows()[0];
			TmpRowData rd = new TmpRowData(getInstance());
			String outLine = rd.loadRowAsString(row);
			
			Utilities.displayInfoMonoSpace(this, "Row#" + (row+1), outLine, false);
		}
    	catch(Exception e) {ErrorReport.print(e, "Show row");}
    }
    /***************************************************************
     * Show MSA - threaded; calls setPanelEnabled(false) before comp and setPanelEnabled(true) after;
     */
    private void showMSA() { 
		if (isSingle) return;
		if (theTable.getSelectedRowCount() == 0)  return;
		
		new UtilSelect(this).msaAlign();	
    }
    public void setMsaButton(boolean b) {// stops 2 from running at once; set F in UtilSelect, set T in MsaMainPanel.buildFinish
    	if (b) {btnShowMSA.setText("MSA... "); bMSArun=false; btnShowMSA.setEnabled(true);}
    	else   {btnShowMSA.setText("Running"); bMSArun=true; btnShowMSA.setEnabled(false);}
    }
    /**************************************************************
     * Show 2D - CAS564 moved most of the methods to UtilSelect
     */
    private void showSynteny() {
		try{
			hitNum1 = hitNum2 = 0;
			grpIdxVec.clear();// for the get statements
			
			if (isSingle) return;
		
			int numRows = theTable.getSelectedRowCount();
			if (numRows==0 || numRows>2) return;
			
			btnShowSynteny.setEnabled(false);		// CAS563 add
			
			int selIndex = cmbSynOpts.getSelectedIndex();
			int pad = (selIndex==showREGION) ? (int) Double.parseDouble(txtSynRegion.getText()) * 1000 : 30;
			
			UtilSelect uObj = new UtilSelect(this);
			uObj.synteny2D(numRows, selIndex,  pad, chkSynGn.isSelected(), grpIdxVec); // hitNum1, hitNum2 get set
			
			btnShowSynteny.setEnabled(true);
		} 
		catch(Exception e) {ErrorReport.print(e, "Create 2D Synteny");}
    }
    /* can 2d-3col be used? if so, what columns;  CAS562 add for synteny */
    protected int [] getSharedRef(boolean bCheck) { // bCheck=T in setRowSelected; bCheck=F in UtilSelect.2D
    	TmpRowData row0 = new TmpRowData(getInstance());
		if (!row0.loadRow(theTable.getSelectedRows()[0])) return null;
	
		TmpRowData row1 = new TmpRowData(getInstance());
		if (!row1.loadRow(theTable.getSelectedRows()[1])) return null;
		
		String r0tag0 = row0.geneTag[0], r0tag1 = row0.geneTag[1]; // Chr.tag or Chr.-
		String r1tag0 = row1.geneTag[0], r1tag1 = row1.geneTag[1];
		
		if ((r0tag0.endsWith(Q.dash) || r1tag0.endsWith(Q.dash)) &&
			(r0tag1.endsWith(Q.dash) || r1tag1.endsWith(Q.dash))) return null;	// CAS563 middle ||->&&
		
		int r0sp0 = row0.spIdx[0], r0sp1 = row0.spIdx[1];			// tags are not unique, spIdx is
		int r1sp0 = row1.spIdx[0], r1sp1 = row1.spIdx[1];
		
		int ref=0;
	         if (r0sp0==r1sp0 && r0tag0.equals(r1tag0)) {ref=1;} 
	    else if (r0sp1==r1sp1 && r0tag1.equals(r1tag1)) {ref=2;}
	    else if (r0sp0==r1sp1 && r0tag0.equals(r1tag1)) {ref=3;}
	    else if (r0sp1==r1sp0 && r0tag1.equals(r1tag0)) {ref=4;}
	        
	    if (ref==0) return null;
	    if (bCheck) return new int[1]; // check first

	    String tag="";
		int [] c = {0,0, 0,0};       
		     if (ref==1) {c[0]=1; c[1]=0;  c[2]=0;  c[3]=1; tag=r0tag0;}
		else if (ref==2) {c[0]=0; c[1]=1;  c[2]=1;  c[3]=0; tag=r0tag1;}
		else if (ref==3) {c[0]=1; c[1]=0;  c[2]=1;  c[3]=0; tag=r0tag0;}
		else if (ref==4) {c[0]=0; c[1]=1;  c[2]=0;  c[3]=1; tag=r0tag1;}
		Globals.tprt("Share ref " + tag);
		return c;
    }
    /* For highlighting View2D hit */
     public boolean isHitSelected(int idx, boolean bHit1) {
    	 if (!chkSynHigh.isSelected()) return false;
 
    	 if (grpIdxVec.contains(idx)) return true;	// highlight all group;  CAS555
   	 
    	 if (bHit1  && idx==hitNum1) return true;
    	 if (!bHit1 && idx==hitNum2) return true;
    	 
    	 return false;
     }
     /***********************************************************************
      ** Export file; CAS556 export put in separate file.
      *  calls setPanelEnabled(false) before comp and setPanelEnabled(true) after; FASTA is threaded
      *********************************************************************/
     private void showExport() { 
		final UtilExport ex = new UtilExport(this, queryFrame.title ); // CAS560 add title for 1st line of export
		ex.setVisible(true);
		final int mode = ex.getSelection();
		
		if (mode != ex.ex_cancel) {
			if (mode==ex.ex_csv)        ex.writeCSV();
			else if (mode==ex.ex_html)  ex.writeHTML();
			else if (mode==ex.ex_fasta) ex.writeFASTA();
		}	
 	}
   
     /********************************************************
      * Report popup and files; re-use last allows using last parameters
      * calls setBtnReport before/after threaded computation
      */
     private void showReport() {
     	if (isClustN) {							// CAS563 better for clusters to have a non-ref report
     		if (reportNoRefPanel==null) reportNoRefPanel = new UtilReportNR(getInstance()); 
 	    	reportNoRefPanel.setVisible(true); 
     	}
     	else {
 	    	if (reportRefPanel==null) reportRefPanel = new UtilReport(getInstance()); // CAS563 Saves last values
 	    	reportRefPanel.setVisible(true); 	
     	}
     }
     protected void setBtnReport(boolean done) {// Called from UtilReport when computation starts/stops
     	if (!done) {
     		btnReport.setText("Computing...");
         	setPanelEnabled(false); 
     	}
     	else {// titles repeated in createTopButtons
     		String title = "Gene Report...";
     		if (isCollinear)  title = "Collinear Report...";
     		else if (isMultiN) title = "Multi-hit Report...";
     		else if (isClustN) title = "Cluster Report...";
     		btnReport.setText(title);
         	setPanelEnabled(true);
     	}
     }
    
 	/**********************************************************
 	 * Search visible columns for a string
 	 * popup of selected valid; ok searches that column and set rowIndex
 	 * Gene# for each species; All_anno for each species; Block, Collinear, Hit, Group
 	 */
     private void showSearch() {
		 btnSearch.setEnabled(false);
			
    	 String [] cols = getSelColsUnordered();
    	 if (searchPanel==null) searchPanel = new UtilSearch(this, cols, theAnnoKeys);
    	 searchPanel.setVisible(true);	
    
    	 int rowIndex = searchPanel.getRowIndex(); 
 	 
    	 if (rowIndex>=0 && rowIndex<theTable.getRowCount()) {
     		Rectangle cellRect = theTable.getCellRect(rowIndex, 0, true);       
     	    theTable.scrollRectToVisible(cellRect);
     	    theTable.setRowSelectionInterval(rowIndex, rowIndex); 
     	    
     	    setRowSelected();
     	 }
		 btnSearch.setEnabled(true);
     }
    /***************************************************************
     * XXX Database
     */
	private ResultSet loadDataFromDatabase(String theQuery) {
        try {
        	DBconn2 dbc2 = queryFrame.getDBC();
        	
        	if (bStopped) return null; 
        	loadStatus.setText("Performing database search....");
        	
        	try {
        		/**** EXECUTE ***/
        		ResultSet rs = dbc2.executeQuery(theQuery); 
        	
        		if (bStopped) {rs.close(); return null; }
        		
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
	
    /****************************************************************
     * Public
     */
	protected void sortMasterColumn(String columnName) {
    	int index = theTableData.getColHeadIdx(columnName);
    	theTableData.sortByColumn(index, !theTableData.isAscending(index));
	}
   
    protected String getTabName() { return theTabName; } // CAS564 was getName 
    
	protected boolean isSingle() {return isSingle;}
 	protected boolean [] getColumnSelections() {
		try {
			if (chkSpeciesFields==null) return null; // CAS563 linux query crash caused this to crash too
			
			int genColCnt = TableColumns.getGenColumnCount(isSingle); 
			int spColCnt = TableColumns.getSpColumnCount(isSingle);
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
            		TableSort table = (TableSort)((JTableHeader)evt.getSource()).getTable(); 
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
    /**************************************************************
     * Private variable
     */
    protected QueryFrame queryFrame = null;
	protected QueryPanel queryPanel = null;
	
	private UtilReport reportRefPanel = null;
	private UtilReportNR reportNoRefPanel = null;
	private UtilSearch searchPanel = null;
	   
    protected TableSort theTable = null;
    protected TableData theTableData = null;
    private JScrollPane sPane = null;
    private JTextField rowCount = null; 
    private JTextField loadStatus = null;
    
    private JLabel lblSynAs=null, lblSynKb=null;
    private JTextField txtSynRegion = null;
    private JCheckBox chkSynHigh = null,  chkSynGn = null;
    private JButton btnShowSynteny = null, btnShowMSA = null, btnShowRow = null; 
    private JButton btnUnSelectAll = null, btnExport = null, btnReport = null, btnSearch = null;
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
	private String theTabName = "",  theQuery = "";
	protected boolean isSingle = false; // Cannot use theQueryPanel.isOrphan because only good for last query
    
	private static int nTableID = 0;
	
	protected int hitNum1=0, hitNum2=0; // Highlight 2D; set in UtilSelect; CAS562 change from start/end to hitnum
	private Vector <Integer> grpIdxVec = new Vector <Integer> (); 			// CAS555 to highlight groups
	protected boolean isCollinear=false, isMultiN=false, isClustN=false;	// CAS556 for UtilReport; the QueryPanel can change, so need to save this
	
	// for Stop 
	private boolean bMSArun=false; 					// MSA can be running, and the rest of the buttons enabled.
	protected boolean bStopped=false; 				// used locally and DBdata; others search loadStatus.equals(Q.stop)
	protected boolean bDone=false;					// Will not Stop if bDone
}
