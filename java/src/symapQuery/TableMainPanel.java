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
import javax.swing.JOptionPane;
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
public class TableMainPanel extends JPanel {
	private static final long serialVersionUID = -3827610586702639105L;
	private static final int ANNO_COLUMN_WIDTH = 100, ANNO_PANEL_WIDTH = 900;
	private static final int GEN_COLUMN_WIDTH = 100;
	private static final int showREGION=0, showSET=1, showBLOCK=2,  showGRP=3; // CAS555 add showGrp
	
	static protected TableSort theLastTable = null; //  CAS560 add to keep using last order of columns for session
	
	// called by SymapQueryFrame
	protected TableMainPanel(SyMAPQueryFrame parentFrame, String resultName,  boolean [] selCols,
			String query, String sum, boolean isSingle) {
		this.queryFrame = parentFrame;
		this.theName 		= resultName;
		
		queryPanel 		= queryFrame.getQueryPanel();
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
				setTable(false, false);
		        displayTable();
			}
		};
		buildTableStart(theQuery);
	}
	/************************************************************/
	private void buildTableStart(String theQuery) {
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
       
        theAnnoKeys = AnnoData.loadProjAnnoKeywords(queryFrame);
        
        setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
        setAlignmentY(LEFT_ALIGNMENT);
        displayProgress();
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
					displayTable();
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
		queryFrame.updateResultCount(this);
	}
	private void displayTable() {
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
	
	private TableMainPanel getInstance() { return this; }

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
        		
         	Vector  <DBdata> rowsFromDB =  DBdata.loadRowsFromDB(rs, queryFrame.getProjects(),
         			queryFrame.getQueryPanel(), theAnnoKeys.getColumns(false /*displayname*/), loadStatus,
         			geneCntMap, projMap); // Inputs for Summary
         				 
        	theTableData.addRowsWithProgress(rowsFromDB, loadStatus);
        		
            SummaryPanel sp = new SummaryPanel(rowsFromDB, queryPanel, projMap, geneCntMap);
            statsPanel = sp.getPanel();
            
            rowsFromDB.clear(); // NOTE: IF want to use later, then the Stats need to be removed from DBdata
        }
        	    
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
 	    
    	btnShowAlign = Jcomp.createButton("MSA...", "Align selected sequences using MUSCLE or MAFFT"); 
 	    btnShowAlign.addActionListener(new ActionListener() {
    		public void actionPerformed(ActionEvent arg0) {
    			showAlign();
    		}
 		});  
 	    topRow.add(btnShowAlign);				topRow.add(Box.createHorizontalStrut(8));// CAS563 5->8 for linux
	 	 
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
	    
	    btnShowRow.setEnabled(false); btnShowAlign.setEnabled(false); btnShowSynteny.setEnabled(false);
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
				popupExport();
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
	    if (isCollinear)  btnReport = Jcomp.createPlainButton("Collinear Report...", "Create a collinear gene report from the rows. Display a popup or write to file.");
	    else if (isMultiN)  btnReport = Jcomp.createPlainButton("Multi-hit Report...", "Create a multi-hit gene report from the rows. Display a popup or write to file.");
	    else if (isClustN)  btnReport = Jcomp.createPlainButton("Cluster Report...", "Create a cluster gene report from the rows. Display a popup or write to file.");
	    else btnReport = Jcomp.createPlainButton("Gene Report...", "Create a gene pair report from the rows. Display a popup or write to file.");
	    btnReport.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				showReport();
			}
		});     
	    
	    botRow.add(btnExport);		botRow.add(Box.createHorizontalStrut(8));
	    botRow.add(btnUnSelectAll);	botRow.add(Box.createHorizontalStrut(60));
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
			displayTable();
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
    private void displayProgress() {
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
    	displayTable();
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
    	setTable(true, false);
    	displayTable();
    }
    // CAS561 add to group like columns together
    private void groupColumns() {
    	setTable(false, true);
    	displayTable();
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
    	btnShowAlign.setEnabled(enable);
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
    	btnShowAlign.setEnabled(bn);
    	
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
   
    /***************************************************************
     * Show alignment
     * Gene:     o Gene coords  o Gene exons  o Merge Hits  o Ignore
     * Non-gene: o Merge overlapping coords o Ignore
     */
    private void showAlign() {
		if (isSingle) return;
		int selNum = theTable.getSelectedRowCount();
		if (selNum == 0)  return;
		new UtilSelect(this).msaAlign();
		
    }
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
   
    /**************************************************************
     * Show 2D 
     */
    private void showSynteny() {
		try{
			grpIdxVec.clear();// for the get statements
			
			if (isSingle) return;
		
			if (theTable.getSelectedRowCount() == 2)  {
				showSyntenyfor3();
				return;
			}
			if (theTable.getSelectedRowCount() != 1)  return;
    		
			btnShowSynteny.setEnabled(false);		// CAS563 add
			
			TmpRowData rd = new TmpRowData(getInstance());
			if (!rd.loadRow(theTable.getSelectedRows()[0])) {
				btnShowSynteny.setEnabled(true);
				return;
			}
				
			int pad=30;
			int track1Start=0, track2Start=0, track2End=0, track1End=0;
			int [] coords;
			HfilterData hd = new HfilterData (); 
			
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
				coords = rd.loadGroup(grpIdxVec); 	 // assigns hits to grpIdxVec
				hd.setForQuery(false, false, true);  // block, set, region
			}
			else {
				coords = new int [4];
				coords[0] = rd.start[0]; coords[1] = rd.end[0];
				coords[2] = rd.start[1]; coords[3] = rd.end[1];
				pad = (int) Double.parseDouble(txtSynRegion.getText()) * 1000;
				hd.setForQuery(false, false, true);  // block, set, region
			}
			track1Start = coords[0] - pad; if (track1Start<0) track1Start=0;
			track1End   = coords[1] + pad; 
			track2Start = coords[2] - pad; if (track2Start<0) track2Start=0;
			track2End   = coords[3] + pad;
			
			hitNum1 = rd.hitnum; hitNum2 = 0; 			// CAS562 was using start/end
			
			int p1Idx = rd.spIdx[0];
			int p2Idx = rd.spIdx[1];

			int grp1Idx = rd.chrIdx[0];
			int grp2Idx = rd.chrIdx[1];
			
			// create new drawing panel; 				// CAS543 quit setting Sfilter Show_Annotation because is static
			SyMAP2d symap = new SyMAP2d(queryFrame.getDBC(), getInstance());
			symap.getDrawingPanel().setTracks(2); 		// CAS550 set exact number
			symap.getDrawingPanel().setHitFilter(1,hd); 
			
			Sequence s1 = symap.getDrawingPanel().setSequenceTrack(1, p2Idx, grp2Idx, Color.CYAN);
			Sequence s2 = symap.getDrawingPanel().setSequenceTrack(2, p1Idx, grp1Idx, Color.GREEN);
			if (chkSynGn.isSelected()) {s1.setGeneNum(); s2.setGeneNum();} 			// CAS562 was setAnnotation; CAS543 was changing static; now changes individual object
			else                       {s1.setAnnotation(); s2.setAnnotation();} 
			
			symap.getDrawingPanel().setTrackEnds(1, track2Start, track2End);
			symap.getDrawingPanel().setTrackEnds(2, track1Start, track1End);
			symap.getFrame().showX();
			btnShowSynteny.setEnabled(true);
		} 
		catch(Exception e) {ErrorReport.print(e, "Create 2D Synteny");}
    }
    private void showSyntenyfor3() {// CAS562 add; this is partially redundant with above, but easier...
 		try{
 			int [] col = getSharedRef(false); 
 			if (col == null) return;
 			
 			btnShowSynteny.setEnabled(false);		// CAS563 add
 			TmpRowData [] rd = new TmpRowData [2];
 			
 			rd[0] = new TmpRowData(getInstance());
 			if (!rd[0].loadRow(theTable.getSelectedRows()[0])) {
 				btnShowSynteny.setEnabled(true); Globals.tprt("No load 0");return;
 			}
 			rd[1] = new TmpRowData(getInstance());
 			if (!rd[1].loadRow(theTable.getSelectedRows()[1])) {
 				btnShowSynteny.setEnabled(true); Globals.tprt("No load 1");return;
 			}
 				
 			int r0t1 = col[0], r0t2 = col[1],  
 				               r1t2 = col[2], r1t3 = col[3];
 			int pad=30;
 			int sL=0, eL=1, sR=2, eR=3;
 			int [] coordsRow0 = new int [4]; 
 			int [] coordsRow1 = new int [4];
 			HfilterData hd0 = new HfilterData (); 
 			HfilterData hd1 = new HfilterData (); 
 			
 			int item = (cmbSynOpts.getSelectedIndex());
 			if (item==showSET) {
 				if (rd[0].collinearN==0 || rd[1].collinearN==0) {
 					Utilities.showWarningMessage("The selected rows do not belong to a collinear set.");
 					return;
 				}
 				int [] coords0 = loadCollinearCoords(rd[0].collinearN, rd[0].chrIdx[0], rd[0].chrIdx[1]);// ret sL,eL,sR,eR
 				int [] coords1 = loadCollinearCoords(rd[1].collinearN, rd[1].chrIdx[0], rd[1].chrIdx[1]);
 				hd0.setForQuery(false, true, false);  // block, set, region
 				hd1.setForQuery(false, true, false);  // block, set, region
 				
 				if (r0t1==0 && r0t2==1) {
 					coordsRow0[sL] = coords0[0]; coordsRow0[eL] = coords0[1]; // row0
 					coordsRow0[sR] = coords0[2]; coordsRow0[eR] = coords0[3];
 				}
 				else {
 					coordsRow0[sL] = coords0[2]; coordsRow0[eL] = coords0[3]; 
 					coordsRow0[sR] = coords0[0]; coordsRow0[eR] = coords0[1];
 				}
 				if (r1t2==0 && r1t3==1) {
 					coordsRow1[sL] = coords1[0]; coordsRow1[eL] = coords1[1]; // row1
 					coordsRow1[sR] = coords1[2]; coordsRow1[eR] = coords1[3];
 				}
 				else {
 					coordsRow1[sL] = coords1[2]; coordsRow1[eL] = coords1[3]; 
 					coordsRow1[sR] = coords1[0]; coordsRow1[eR] = coords1[1];
 				}
 			}
 			else if (item==showREGION) {
 				pad = (int) Double.parseDouble(txtSynRegion.getText()) * 1000;
 				
 				coordsRow0[sL] = rd[0].start[r0t1]; coordsRow0[eL] = rd[0].end[r0t1]; // row0
 				coordsRow0[sR] = rd[0].start[r0t2]; coordsRow0[eR] = rd[0].end[r0t2];
 				coordsRow1[sL] = rd[1].start[r1t2]; coordsRow1[eL] = rd[1].end[r1t2]; 
 				coordsRow1[sR] = rd[1].start[r1t3]; coordsRow1[eR] = rd[1].end[r1t3];

 				hd0.setForQuery(false, false, true);  // block, set, region
 				hd1.setForQuery(false, false, true);  
 			}
 			else {// CAS563 not use Utilities since prt to term
 				JOptionPane.showMessageDialog(null, "3-track display only works with Region or Collinear Set.", 
 						"Warning", JOptionPane.WARNING_MESSAGE);
				return;
 			}
 			int [] tStart = new int [3]; int [] tEnd   = new int [3]; 
 			int [] spIdx  = new int [3]; int [] chrIdx = new int [3];
 		
			spIdx[0] = rd[0].spIdx[r0t1]; chrIdx[0] = rd[0].chrIdx[r0t1];
			spIdx[1] = rd[0].spIdx[r0t2]; chrIdx[1] = rd[0].chrIdx[r0t2];	// ref
			spIdx[2] = rd[1].spIdx[r1t3]; chrIdx[2] = rd[1].chrIdx[r1t3];
			
			tStart[0] = coordsRow0[sL] - pad; if (tStart[0]<0) tStart[0]=0;
 			tEnd[0]   = coordsRow0[eL] + pad; 
 			
 			int startRef = (coordsRow0[sR]<coordsRow1[sL]) ? coordsRow0[sR] : coordsRow1[sL];
			int endRef   = (coordsRow0[eR]>coordsRow1[eL]) ? coordsRow0[eR] : coordsRow1[eL];
			tStart[1] = startRef - pad; if (tStart[1]<0) tStart[1]=0;	// CAS563 bug fix: the check was on [2]
 			tEnd[1]   = endRef + pad; 
			
			tStart[2] = coordsRow1[sR] - pad; if (tStart[2]<0) tStart[2]=0;
 			tEnd[2]   = coordsRow1[eR] + pad; 		 
 			hitNum1 = rd[0].hitnum; hitNum2 = rd[1].hitnum;
 			
 			// create new drawing panel; 
 			SyMAP2d symap = new SyMAP2d(queryFrame.getDBC(), getInstance());
 			symap.getDrawingPanel().setTracks(3); 
 			symap.getDrawingPanel().setHitFilter(1,hd0); // template for Mapper HfilterData, which is already created
 			symap.getDrawingPanel().setHitFilter(2,hd1);
 			
 			Sequence s1 = symap.getDrawingPanel().setSequenceTrack(1, spIdx[0], chrIdx[0], Color.CYAN);
 			Sequence s2 = symap.getDrawingPanel().setSequenceTrack(2, spIdx[1], chrIdx[1], Color.GREEN); // ref
 			Sequence s3 = symap.getDrawingPanel().setSequenceTrack(3, spIdx[2], chrIdx[2], Color.GREEN);
 			if (chkSynGn.isSelected()) {s1.setGeneNum(); s2.setGeneNum();s3.setGeneNum();} 			
			else                       {s1.setAnnotation(); s2.setAnnotation(); s3.setAnnotation();} 
			
 			symap.getDrawingPanel().setTrackEnds(1, tStart[0], tEnd[0]);
 			symap.getDrawingPanel().setTrackEnds(2, tStart[1], tEnd[1]); // ref
 			symap.getDrawingPanel().setTrackEnds(3, tStart[2], tEnd[2]);
 			symap.getFrame().showX();
 			btnShowSynteny.setEnabled(true);		// CAS563 add
 		} 
 		catch(Exception e) {ErrorReport.print(e, "Create 2D Synteny");}
     }
    protected int [] getSharedRef(boolean bCheck) { // only for 2 rows; CAS562 add
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
    /******************************************************
 	 * For highlighting View2D hit
 	 */
     public boolean isHitSelected(int idx, boolean bHit1) {
    	 if (!chkSynHigh.isSelected()) return false;
 
    	 if (grpIdxVec.contains(idx)) return true;	// highlight all group;  CAS555
    	 //if  (s1==synStart1 && e1==synEnd1 && s2==synStart2 && e2==synEnd2) return true; // CAS562 chg to hitnum
    	 //if  (s2==synStart1 && e2==synEnd1 && s1==synStart2 && e1==synEnd2) return true;
    	 
    	 if (bHit1 && idx==hitNum1) return true;
    	 if (!bHit1 && idx==hitNum2) return true;
    	 
    	 return false;
     }
 	 
    /***************************************************************
     * XXX Database
     */
	private ResultSet loadDataFromDatabase(String theQuery) {
        try {
        	DBconn2 dbc2 = queryFrame.getDBC();
        	
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
	private int [] loadBlockCoords(int block, int idx1, int idx2) {
		int [] coords = null;
		try {
			DBconn2 dbc2 = queryFrame.getDBC();
			
			ResultSet rs = dbc2.executeQuery("select start1, end1, start2, end2 from blocks "
					+ " where blocknum="+block+ " and grp1_idx=" + idx1 + " and grp2_idx=" + idx2);
			if (rs.next()) {
				coords = new int [4];
				for (int i=0; i<4; i++) coords[i] = rs.getInt(i+1);
			}

			if (coords==null) { // try opposite way
				rs = dbc2.executeQuery("select start2, end2, start1, end1 from blocks "
						+ " where blocknum="+block+ " and grp1_idx=" + idx2 + " and grp2_idx=" + idx1);
				if (rs.next()) {
					coords = new int [4];
					for (int i=0; i<4; i++) coords[i] = rs.getInt(i+1);
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
	private int [] loadCollinearCoords(int set, int idx1, int idx2) {
		int [] coords = null;		// it was Double CAS562
		try {
			DBconn2 dbc2 = queryFrame.getDBC();
		
			ResultSet rs = dbc2.executeQuery("select start1, end1, start2, end2 from pseudo_hits "
					+ "where runnum=" + set + " and grp1_idx=" + idx1 + " and grp2_idx=" + idx2);
			int start1=Integer.MAX_VALUE, end1=-1, start2=Integer.MAX_VALUE, end2=-1, d;
			while (rs.next()) {
				d = rs.getInt(1);	if (d<start1) start1 = d;
				d = rs.getInt(2);	if (d>end1)   end1 = d;
				
				d = rs.getInt(3); 	if (d<start2) start2 = d;
				d = rs.getInt(4);	if (d>end2)   end2 = d;
			}
			if (start1==Integer.MAX_VALUE) { // try flipping idx1 and idx2
				rs = dbc2.executeQuery("select start1, end1, start2, end2 from pseudo_hits "
						+ "where runnum=" + set + " and grp1_idx=" + idx2 + " and grp2_idx=" + idx1);
				while (rs.next()) {
					d = rs.getInt(1);	if (d<start2) start2 = d;
					d = rs.getInt(2);	if (d>end2)   end2 = d;
					
					d = rs.getInt(3); 	if (d<start1) start1 = d;
					d = rs.getInt(4);	if (d>end1)   end1 = d;
				}
			}
			if (start1!=Integer.MAX_VALUE) {
				int pad = 8;				// CAS562 add pad
				coords = new int [4];
				coords[0]=start1-pad; if (coords[0]<0) coords[0]=0;
				coords[1]=end1+pad; 
				coords[2]=start2-pad; if (coords[3]<0) coords[0]=0;
				coords[3]=end2+pad;
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
    
    /********************************************************
     * Report popup and files
     */
    private void showReport() {
    	if (isClustN) {// CAS563 better for clusters to have a non-ref report
    		if (reportnrPanel==null) 
	    		reportnrPanel = new UtilReportNR(getInstance()); 
	    	reportnrPanel.setVisible(true); 
    	}
    	else {
	    	if (reportPanel==null) 
	    		reportPanel = new UtilReport(getInstance()); // CAS563 Saves last values
	    	reportPanel.setVisible(true); 	
    	}
    }
    protected void setBtnReport(boolean done) {
    	if (!done) {
    		btnReport.setText("Computing...");
        	btnReport.setBackground(Color.BLUE);
        	setPanelEnabled(false); 
    	}
    	else {// titles repeated in createTopButtons
    		String title = "Gene Report...";
    		if (isCollinear)  title = "Collinear Report...";
    		else if (isMultiN) title = "Multi-hit Report...";
    		else if (isClustN) title = "Cluster Report...";
    		btnReport.setText(title);
        	btnReport.setBackground(Color.WHITE);
        	setPanelEnabled(true);
    	}
    }
    /***********************************************************************
     ** Export file; CAS556 export put in separate file.
     *********************************************************************/
    private void popupExport() {
		final UtilExport ex = new UtilExport(this, queryFrame.title ); // CAS560 add title for 1st line of export
		ex.setVisible(true);
		final int mode = ex.getSelection();
		
		if (mode != ex.ex_cancel) {
			if (mode==ex.ex_csv)        ex.writeCSV();
			else if (mode==ex.ex_html)  ex.writeHTML();
			else if (mode==ex.ex_fasta) ex.writeFASTA();
		}	
		setRowSelected();
	}
	
    /**************************************************************
     * Private variable
     */
    protected SyMAPQueryFrame queryFrame = null;
	protected QueryPanel      queryPanel = null;
	
    protected TableSort theTable = null;
    protected TableData theTableData = null;
    private JScrollPane sPane = null;
    private JTextField rowCount = null;
    private UtilReport reportPanel = null;
    private UtilReportNR reportnrPanel = null;
   
    private JTextField loadStatus = null;
    
    private JLabel lblSynAs=null, lblSynKb=null;
    private JTextField txtSynRegion = null;
    private JCheckBox chkSynHigh = null,  chkSynGn = null;
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
	
	private int hitNum1=0, hitNum2=0; // Highlight 2D; CAS562 change from start/end to hitnum
	private Vector <Integer> grpIdxVec = new Vector <Integer> (); // CAS555 to highlight groups
	protected boolean isCollinear=false, isMultiN=false, isClustN=false;	// CAS556 for UtilReport; the QueryPanel can change, so need to save this
}
