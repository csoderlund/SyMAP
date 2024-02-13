package symapQuery;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
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
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JRadioButton;
import javax.swing.ButtonGroup;
import javax.swing.ListCellRenderer;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import database.DBconn2;
import util.ErrorReport;
import util.Jhtml;
import util.Utilities;
import symap.Globals;
import symap.drawingpanel.SyMAP2d;
import symap.mapper.HfilterData;
import symap.sequence.Sequence;

/**************************************************
 * Perform query and display results in table
 * CAS504 many changes for this release, most are not commented
 * CAS513 made panel smaller and columns w/o scroll; the query/summary are passed in 
 * 		  the annoKeywords are checks for annot_kw_mincount
 */
public class TableDataPanel extends JPanel {
	private static final long serialVersionUID = -3827610586702639105L;
	private static final int ANNO_COLUMN_WIDTH = 100, ANNO_PANEL_WIDTH = 900;
	private static final int GEN_COLUMN_WIDTH = 100;
	private static final int showREGION=0, showBLOCK=1, showSET=2;
	
	// called by SymapQueryFrame
	public TableDataPanel(SyMAPQueryFrame parentFrame, String resultName,  boolean [] selections,
			String query, String sum, boolean isSingle) {
		this.theParentFrame = parentFrame;
		this.theName 		= resultName;
		
		theQueryPanel 		= theParentFrame.getQueryPanel();
		this.isSingle		= isSingle;
		this.theQuery 		= query;
		this.theSummary 	= sum;
		
		if(selections != null) {
			theOldSelections = new boolean[selections.length];
			for(int x=0; x<selections.length; x++)
				theOldSelections[x] = selections[x];
		}
		
		colSelectChange = new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				setTable(false);
		        showTable();
			}
		};
		buildTableStart(theQuery);
	}
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
					setColumnSelections();
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
        	HashMap <String,Integer> proj2regions = new HashMap  <String, Integer> (); // ComputePgeneF
        		
         	Vector  <DBdata> rowsFromDB =  DBdata.loadRowsFromDB(rs, theParentFrame.getProjects(),
         			theParentFrame.getQueryPanel(), theAnnoKeys.getColumns(false /*displayname*/), loadStatus,
         			geneCntMap, proj2regions); // Inputs for Summary
         				 
        	theTableData.addRowsWithProgress(rowsFromDB, loadStatus);
        		
            SummaryPanel sp = new SummaryPanel(rowsFromDB, theQueryPanel, proj2regions, geneCntMap);
            statsPanel = sp.getPanel();
            
            rowsFromDB.clear(); // NOTE: IF want to use later, then the Stats need to be removed from DBdata
        }
        	    
        theTableData.setColumnHeaders(theParentFrame.getAbbrevNames(), theAnnoKeys.getColumns(true /*abbrev*/), isSingle);
        theTableData.finalizeX();

		theTable = new SortTable(TableData.createModel(getSelectedColumns(), theTableData, this));
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
    	JPanel buttonPanel = new JPanel();
    	buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.PAGE_AXIS));
    	buttonPanel.setBackground(Color.WHITE);
    	buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
    	
    	JPanel topRow = new JPanel();
    	topRow.setLayout(new BoxLayout(topRow, BoxLayout.LINE_AXIS));
    	topRow.setBackground(Color.WHITE);
    	topRow.setAlignmentX(Component.LEFT_ALIGNMENT);
    	
    	topRow.add(new JLabel("Selected: ")); topRow.add(Box.createHorizontalStrut(5));
    	
    // Show CAS520
    	btnShowRow = new JButton("Show"); // CAS520 add to view data in row
 	    btnShowRow.setAlignmentX(Component.LEFT_ALIGNMENT);
 	    btnShowRow.setBackground(Color.WHITE);
 	    btnShowRow.addActionListener(new ActionListener() {
	   		public void actionPerformed(ActionEvent arg0) {
	   			showRow();
	   		}
		});  
 	  
 	    topRow.add(btnShowRow);	topRow.add(Box.createHorizontalStrut(6));
 	    
    	btnShowAlign = new JButton("Align"); 
 	    btnShowAlign.setAlignmentX(Component.LEFT_ALIGNMENT);
 	    btnShowAlign.setBackground(Color.WHITE);
 	    btnShowAlign.addActionListener(new ActionListener() {
    		public void actionPerformed(ActionEvent arg0) {
    			showAlignment();
    		}
 		});  
 	    topRow.add(btnShowAlign);			topRow.add(Box.createHorizontalStrut(6));
	 	    
	    btnShowSynteny = new JButton("View 2D"); 
	    btnShowSynteny.setAlignmentX(Component.LEFT_ALIGNMENT);
	    btnShowSynteny.setBackground(Color.WHITE);
	    btnShowSynteny.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				showSynteny();		
			}
		}); 
	    topRow.add(btnShowSynteny);		topRow.add(Box.createHorizontalStrut(1));
	    topRow.add(new JLabel("as"));	topRow.add(Box.createHorizontalStrut(1));
	    cmbSynOpts = new JComboBox <String> ();
	    cmbSynOpts.setBackground(Color.WHITE);
	    cmbSynOpts.addItem("Region (kb)");  // showREGION
	    cmbSynOpts.addItem("Synteny Block");// showBLOCK
	    cmbSynOpts.addItem("Collinear Set");// showSET
	    cmbSynOpts.setSelectedIndex(0);
	    topRow.add(cmbSynOpts);			topRow.add(Box.createHorizontalStrut(1));
	    
		txtMargin = new JTextField(3);
	    txtMargin.setText((Globals.MAX_CLOSEUP_BP/1000)+""); // CAS548 changed from MAX_RANGE
	    txtMargin.setMinimumSize(txtMargin.getPreferredSize());
	    topRow.add(txtMargin);
	    topRow.add(new JLabel("kb")); 	topRow.add(Box.createHorizontalStrut(2));
	    
	    // CAS521 I wasted 3hrs trying to add Selected to the HitFilter, should have been easy. OO is the worst!
	    chkHigh = new JCheckBox("High");
	    chkHigh.setSelected(true);
	    chkHigh.setBackground(Color.WHITE);
	    topRow.add(chkHigh); 	topRow.add(Box.createHorizontalStrut(5));
	    
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
		
		JPanel botRow = new JPanel();
		botRow.setLayout(new BoxLayout(botRow, BoxLayout.LINE_AXIS));
		botRow.setBackground(Color.WHITE);
		botRow.setAlignmentX(Component.LEFT_ALIGNMENT);
    	
		botRow.add(new JLabel("Table (or selected): ")); botRow.add(Box.createHorizontalStrut(2));
	
	    btnExport = new JButton("Export...");
	    btnExport.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				exportPopup();
			}
		});
	   
	    btnUnSelectAll = new JButton("Unselect All");
	    btnUnSelectAll.setAlignmentX(Component.LEFT_ALIGNMENT);
	    btnUnSelectAll.setBackground(Color.WHITE);
	    btnUnSelectAll.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				theTable.getSelectionModel().clearSelection();
				setPanelSelected();
			}
		});     
	   
	    botRow.add(btnExport);		botRow.add(Box.createHorizontalStrut(10));
	    botRow.add(btnUnSelectAll);	botRow.add(Box.createHorizontalStrut(5));
	   
		buttonPanel.add(topRow);
		buttonPanel.add(Box.createVerticalStrut(10));
		buttonPanel.add(botRow);
		buttonPanel.setMaximumSize(buttonPanel.getPreferredSize());
		
	    return buttonPanel;
	}
    private JPanel createColumnsPanel() {
    	columnPanel = new JPanel();
		columnPanel.setLayout(new BoxLayout(columnPanel, BoxLayout.PAGE_AXIS));
		columnPanel.setBackground(Color.WHITE);
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
    	JPanel retVal = new JPanel();
    	retVal.setLayout(new BoxLayout(retVal, BoxLayout.PAGE_AXIS));
    	retVal.setAlignmentX(Component.LEFT_ALIGNMENT);
    	retVal.setBackground(Color.WHITE);
    
    	String [] genCols = FieldData.getGeneralColHead();
    	boolean [] genColDef = FieldData.getGeneralColDefaults();
    	String [] genDesc= FieldData.getGeneralColDesc();
    	int nCol = FieldData.getGenColumnCount(isSingle);
    	int newRow = (nCol>1) ? genCols.length-FieldData.N_GEN_HIT : 1; // CAS520, CAS540 hit score, CAS546 N_GENERAL
    	
    	JPanel row=null;
    	chkGeneralFields = new JCheckBox[nCol];
    	for(int x=0; x<nCol; x++) {
    		if (x==0 || x==newRow) {
    			if (x==newRow) retVal.add(row);
    			
    			row = new JPanel();
    	    	row.setLayout(new BoxLayout(row, BoxLayout.LINE_AXIS));
    	    	row.setBackground(Color.WHITE);
    	    	row.setAlignmentX(Component.LEFT_ALIGNMENT);
    		}
    		else {
    			int width = GEN_COLUMN_WIDTH - chkGeneralFields[x-1].getPreferredSize().width;
        		if (width > 0) row.add(Box.createHorizontalStrut(width));	
        		row.add(Box.createHorizontalStrut(3)); 
    		}
    		chkGeneralFields[x] = new JCheckBox(genCols[x]);
    		chkGeneralFields[x].setAlignmentX(Component.LEFT_ALIGNMENT);
    		chkGeneralFields[x].setBackground(Color.WHITE);
    		chkGeneralFields[x].addActionListener(colSelectChange);
    		chkGeneralFields[x].setToolTipText(genDesc[x]);
    		/** CAS541 replace with tool tip
    		final String desc = genDesc[x];			
    		chkGeneralFields[x].addMouseListener(new MouseAdapter() {	// CAS519 add
        		public void mouseEntered(MouseEvent e) {setStatus(desc);}
        		public void mouseExited(MouseEvent e)  {setStatus("");}
        	});
        	**/
    		chkGeneralFields[x].setSelected(genColDef[x]);
    		if (x==0) {// CAS540x i was able to uncheck the row, don't know how...
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
    	JPanel page = new JPanel();
    	page.setLayout(new BoxLayout(page, BoxLayout.PAGE_AXIS));
    	page.setAlignmentX(Component.LEFT_ALIGNMENT);
    	page.setBackground(Color.WHITE);
        	
    	String [] species = theParentFrame.getAbbrevNames();
    	String [] colHeads = FieldData.getSpeciesColHead(isSingle);
		
		String [] colDesc= FieldData.getSpeciesColDesc(isSingle);
		int cntCol = FieldData.getSpColumnCount(isSingle); // CAS519 add because single/pairs no longer have the same #columns
		
    	chkSpeciesFields = new JCheckBox[species.length][cntCol];
    	boolean [] colDefs = FieldData.getSpeciesColDefaults(isSingle);
    	
    	for(int x=0; x<species.length; x++) {
    		JPanel row = new JPanel();
        	row.setLayout(new BoxLayout(row, BoxLayout.LINE_AXIS));
        	row.setAlignmentX(Component.LEFT_ALIGNMENT);
        	row.setBackground(Color.WHITE);
    		
    		row.add(createSpeciesLabel(species[x]));
    		row.add(Box.createHorizontalStrut(5));
    		
    		for (int i=0; i<cntCol; i++) {
    			chkSpeciesFields[x][i] = new JCheckBox(colHeads[i]);   
    			chkSpeciesFields[x][i].setSelected(colDefs[i]);
    		}
    		
        	for(int y=0; y<cntCol; y++) {
        		chkSpeciesFields[x][y].setAlignmentX(Component.LEFT_ALIGNMENT);
        		chkSpeciesFields[x][y].setBackground(Color.WHITE);
        		chkSpeciesFields[x][y].addActionListener(colSelectChange);
        		chkSpeciesFields[x][y].setToolTipText(colDesc[y]);
        		/** CAS541 replace with tooltip
        		final String desc = colDesc[y];
        		chkSpeciesFields[x][y].addMouseListener(new MouseAdapter() { // CAS519 add
            		public void mouseEntered(MouseEvent e) {setStatus(desc);}
            		public void mouseExited(MouseEvent e)  {setStatus("");}
            	});
            	**/
        		row.add(chkSpeciesFields[x][y]);
        		
        		row.add(Box.createHorizontalStrut(4));
        	}
        	page.add(row);
        	if (x < species.length - 1) page.add(new JSeparator());
    	}
    	String loc = (isSingle) ? "Gene" : "Gene&Hit"; // CAS503, CAS519 Hit->Gene&Hit
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
    	JPanel retVal = new JPanel();
    	retVal.setLayout(new BoxLayout(retVal, BoxLayout.LINE_AXIS));
    	retVal.setAlignmentX(Component.LEFT_ALIGNMENT);
    	retVal.setBackground(Color.WHITE);

    	chkAnnoFields = new Vector<Vector<JCheckBox>> ();
    	
    	int [] speciesIDs = theAnnoKeys.getSpeciesIDList();
    	
    	// Check selected
    	for(int x=0; x<speciesIDs.length; x++) {
    		Vector<JCheckBox> annoCol = new Vector<JCheckBox> ();
    		
    		for(int y=0; y<theAnnoKeys.getNumberAnnosForSpecies(speciesIDs[x]); y++){
    			
    			String annotName = theAnnoKeys.getAnnoIDforSpeciesAt(speciesIDs[x], y);
    			
    			JCheckBox chkCol = new JCheckBox(annotName);
    			chkCol.setBackground(Color.WHITE);
    			chkCol.addActionListener(colSelectChange);
    			String d = (annotName.contentEquals(Q.All_Anno)) ? 
    					"All annotation from GFF file" : "Column heading is keyword from GFF file";
    			chkCol.setToolTipText(d);
    			/** CAS541 replace with tooltip
    			final String desc = d;
        		chkCol.addMouseListener(new MouseAdapter() { // CAS519 add
            		public void mouseEntered(MouseEvent e) {setStatus(desc);}
            		public void mouseExited(MouseEvent e) {setStatus("");}
            	});
            	**/
    			/* CAS532 quit setting now that we have save
    			String lc = annotName.toLowerCase(); // CAS513 caseIgnore
    			if (lc.equals("description") || lc.equals("product") || lc.equals("note") || lc.equals("desc"))	{
    				chkCol.setSelected(true);
    			}
    			*/
    			annoCol.add(chkCol);
    		}
    		chkAnnoFields.add(annoCol);
    	}
    	
    	JPanel colPanel = new JPanel();
    	colPanel.setLayout(new BoxLayout(colPanel, BoxLayout.PAGE_AXIS));
    	colPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
    	colPanel.setBackground(Color.WHITE);
    	
    	Iterator<Vector<JCheckBox>> spIter = chkAnnoFields.iterator();
    	int pos = 0;
    	while (spIter.hasNext()) {
        	JPanel row = new JPanel();
        	row.setLayout(new BoxLayout(row, BoxLayout.LINE_AXIS));
        	row.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        	row.setBackground(Color.WHITE);
        	
        	// Species name
        	// CAS519 use abbrev JLabel spLabel = new JLabel(theAnnoKeys.getSpeciesNameByID(speciesIDs[pos]));
        	//int width = ANNO_COLUMN_WIDTH - spLabel.getPreferredSize().width;
        	//if(width > 0) row.add(Box.createHorizontalStrut(width));
        	String abbrev = theAnnoKeys.getSpeciesAbbrevByID(speciesIDs[pos]);
        	JLabel spLabel = createSpeciesLabel(abbrev);
        	row.add(spLabel); row.add(Box.createHorizontalStrut(10));
        	
        	int curWidth = ANNO_COLUMN_WIDTH;
        	Iterator<JCheckBox> annoIter = spIter.next().iterator();
        	while (annoIter.hasNext()) {
        		JCheckBox chkTemp = annoIter.next();
        		
        		if(curWidth + ANNO_COLUMN_WIDTH > ANNO_PANEL_WIDTH) {
        			colPanel.add(row);
        	    	row = new JPanel();
        	    	row.setLayout(new BoxLayout(row, BoxLayout.LINE_AXIS));
        	    	row.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        	    	row.setBackground(Color.WHITE);
        	    	
        	    	row.add(Box.createHorizontalStrut(ANNO_COLUMN_WIDTH + 10));
        	    	curWidth = ANNO_COLUMN_WIDTH + 10;
        		}
        		row.add(chkTemp);
        		
        		curWidth += ANNO_COLUMN_WIDTH;
        		//width = ANNO_COLUMN_WIDTH - chkTemp.getPreferredSize().width;
        		//if (width > 0) row.add(Box.createHorizontalStrut(width));
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
    	buttonPanel = new JPanel();
    	buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));
    	buttonPanel.setBackground(Color.WHITE);
    	buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
    	
		btnShowColumns = new JButton("Select Columns");
		btnShowColumns.setBackground(Color.WHITE);
		btnShowColumns.addActionListener(new ActionListener() {
		public void actionPerformed(ActionEvent e) {
			if(columnPanel.isVisible()) {
				btnShowColumns.setText("Select Columns");
				columnPanel.setVisible(false);
				btnClearColumns.setVisible(false);
				btnDefaultColumns.setVisible(false);
				btnShowStats.setVisible(true);
			}
			else {
				btnShowColumns.setText("Hide Columns");
				columnPanel.setVisible(true);
				btnClearColumns.setVisible(true);
				btnDefaultColumns.setVisible(true);
				btnShowStats.setVisible(false);
			}
			showTable();
		}});
		
		btnClearColumns = new JButton("Clear");
		btnClearColumns.setBackground(Color.WHITE);
		btnClearColumns.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				clearColumns();
		}});
		btnClearColumns.setVisible(false);
		
		btnDefaultColumns = new JButton("Defaults");
		btnDefaultColumns.setBackground(Color.WHITE);
		btnDefaultColumns.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				defaultColumns();
		}});
		btnDefaultColumns.setVisible(false);
	
		btnShowStats = new JButton("Hide Stats");
		btnShowStats.setBackground(Color.WHITE);
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

		txtStatus = new JTextField(400); // CAS519 new; CAS541 replaced with tooltip
		txtStatus.setEditable(false);
		txtStatus.setBorder(BorderFactory.createEmptyBorder());
		txtStatus.setBackground(Color.WHITE);
		
    	buttonPanel.add(btnShowColumns);  buttonPanel.add(Box.createHorizontalStrut(5));
    	buttonPanel.add(btnClearColumns); buttonPanel.add(Box.createHorizontalStrut(5));
    	buttonPanel.add(btnDefaultColumns); buttonPanel.add(Box.createHorizontalStrut(10));
    	buttonPanel.add(btnShowStats);    buttonPanel.add(Box.createHorizontalStrut(10)); //So the resized button doesn't get clipped
    	buttonPanel.add(txtStatus);
    	
    	buttonPanel.setMaximumSize(buttonPanel.getPreferredSize());
    	return buttonPanel;
    }
    //private void setStatus(String desc) {txtStatus.setText(desc);}
    
    private JPanel createTableStatusPanel() {
    	JPanel thePanel = new JPanel();
    	thePanel.setLayout(new BoxLayout(thePanel, BoxLayout.LINE_AXIS));
    	thePanel.setBackground(Color.WHITE);

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
    	setTable(true);
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
    	setTable(true);
    	showTable();
    }
    
	// Column change
	private void setTable(boolean isFirst) {
		if(theTable != null) theTable.removeListeners();
		
		String [] selCols = getSelectedColumns(); // CAS532 orderColumns adds to, so for new table, it was adding to defaults
		String [] columns = (isFirst) ? selCols : TableData.orderColumns(theTable, selCols);
		theTable = 	new SortTable(TableData.createModel(columns, theTableData, getInstance()));
		
        theTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        theTable.autofitColumns();
        	
        theTable.getSelectionModel().addListSelectionListener(selListener);
		
        theTable.setTableHeader(new ColumnHeaderToolTip(theTable.getColumnModel()));

        MultiLineHeaderRenderer renderer = new MultiLineHeaderRenderer();
        Enumeration<TableColumn> en = theTable.getColumnModel().getColumns();
        while (en.hasMoreElements()) {
          ((TableColumn)en.nextElement()).setHeaderRenderer(renderer);
        } 
	}

    private String [] getSelectedColumns() {
		Vector<String> retVal = new Vector<String> ();
		
		for(int x=0; x<chkGeneralFields.length; x++) {
			if(chkGeneralFields[x].isSelected())
				retVal.add(chkGeneralFields[x].getText());
		}
		String [] species = theParentFrame.getAbbrevNames(); // CAS532 anno was before species
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
	
    private void setColumnSelections() {
    	if (theOldSelections == null) return; 
    	
    	int targetPos = 0, oldN=theOldSelections.length;
    	try {
	    	for(int x=0; x<chkGeneralFields.length  && targetPos<oldN; x++) {
	    		chkGeneralFields[x].setSelected(theOldSelections[targetPos]);
	    		targetPos++;
	    	}    	
		  	for(int x=0; x<chkSpeciesFields.length; x++) {
	    		for(int y=0; y<chkSpeciesFields[x].length  && targetPos<oldN; y++) {
	    			chkSpeciesFields[x][y].setSelected(theOldSelections[targetPos]);
	    			targetPos++;
	    		}
	    	}
		   // CAS532 see SyMAPQueryFrame.getLastColumns; add check for targetPos 
	    	for(int x=0; x<chkAnnoFields.size() && targetPos<oldN; x++) {
	    		for(int y=0; y<chkAnnoFields.get(x).size() && targetPos<oldN; y++) {
	    			chkAnnoFields.get(x).get(y).setSelected(theOldSelections[targetPos]);
	    			targetPos++;
	    		}
	    	}
    	}
    	catch (Exception e) {ErrorReport.print(e, "Selections: " + theOldSelections.length + " " + targetPos);}
    	
    	theOldSelections = null;
    	setTable(true);
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
    
    private void setPanelEnabled(boolean enable) {
    	theTable.setEnabled(enable);
    	rowCount.setEnabled(enable);
  
    	txtMargin.setEnabled(enable);
    	btnShowSynteny.setEnabled(enable);
    	cmbSynOpts.setEnabled(enable);
    	chkHigh.setEnabled(enable);
    	btnShowRow.setEnabled(enable);
    	btnExport.setEnabled(enable);
    	btnUnSelectAll.setEnabled(enable);
    	btnShowAlign.setEnabled(enable);
    	btnShowColumns.setEnabled(enable);
    	btnHelp.setEnabled(enable);
    	
    	for(int x=0; x<chkGeneralFields.length; x++)
    		chkGeneralFields[x].setEnabled(enable);
    		
    	for(int x=0; x<chkSpeciesFields.length; x++) {
    		for(int y=0; y<chkSpeciesFields[x].length; y++)
    			chkSpeciesFields[x][y].setEnabled(enable);
    	}
    }
    private void setPanelSelected() {
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
				    
					RowData rd = new RowData ();
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
							rows[r][c++]=rd.genenum[i];
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
			RowData rd = new RowData();
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
			RowData rd = new RowData();
			if (!rd.loadRow(row)) return;
				
			// for the get statements
			double track1Start=0, track2Start=0, track2End=0, track1End=0;
			int item = (cmbSynOpts.getSelectedIndex());
			if (item>0) {
				double [] coords;
				if (item==2) {
					if (rd.collinear==0) {
						Utilities.showWarningMessage("The selected row does not belong to a collinear set.");
						return;
					}
					coords = loadCollinearCoords(rd.collinear, rd.chrIdx[0], rd.chrIdx[1]);
				}
				else  {
					if (rd.block==0) {
						Utilities.showWarningMessage("The selected row does not belong to a synteny block.");
						return;
					}
					coords = loadBlockCoords(rd.block, rd.chrIdx[0], rd.chrIdx[1]);
				}
				if (coords==null) {
					Utilities.showWarningMessage("SyMAP could not get the coordinates");
					return;
				}
				double pad=20.0;
				track1Start = coords[0] - pad; if (track1Start<0) track1Start=0;
				track1End   = coords[1] + pad; 
				track2Start = coords[2] - pad; if (track2Start<0) track2Start=0;
				track2End   = coords[3] + pad;
			}
			else { // region
				double padding = Double.parseDouble(txtMargin.getText()) * 1000;
				
				track1Start = (Integer) rd.start[0];
				track1Start = Math.max(0, track1Start - padding);
				
				track2Start = (Integer) rd.start[1];
				track2Start = Math.max(0, track2Start - padding);
				
				track1End = (Integer) rd.end[0] + padding;
				track2End = (Integer) rd.end[1] + padding;
			}
			synStart1 = rd.start[0]; synEnd1 = rd.end[0];
			synStart2 = rd.start[1]; synEnd2 = rd.end[1]; // CAS516 was synEnd1

			int p1Idx = (Integer) rd.spIdx[0];
			int p2Idx = (Integer) rd.spIdx[1];

			int grp1Idx = (Integer) rd.chrIdx[0];
			int grp2Idx = (Integer) rd.chrIdx[1];
			
			// create new drawing panel; CAS543 quit setting Sfilter Show_Annotation because is static
			SyMAP2d symap = new SyMAP2d(theParentFrame.getDBC(), getInstance());
		
			symap.getDrawingPanel().setMaps(1);
			symap.getHistory().clear(); 
			
			HfilterData hd = new HfilterData (); // CAS520 change from dotplot.FilterData
			if (item==showREGION) 		hd.setForQuery(false, false, true);  // block, set, region
			else if (item==showBLOCK) 	hd.setForQuery(true, false, false);  
			else if (item==showSET)		hd.setForQuery(false, true, false);
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
 	 * the values are already sent. Since only the last row is stored here, can only select one
 	 * row at a time
 	 * CAS516 change to one call instead of 4
 	 */
     public boolean isHitSelected(int s1, int e1, int s2, int e2) {
    	 if (!chkHigh.isSelected()) return false;
    	 if  (s1==synStart1 && e1==synEnd1 && s2==synStart2 && e2==synEnd2) return true;
    	 if  (s2==synStart1 && e2==synEnd1 && s1==synStart2 && e1==synEnd2) return true;
    	 return false;
     }
 	 private int synStart1, synStart2, synEnd1, synEnd2; 
	    
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
        		if (Q.TEST_TRACE)	loadDataTESTwrite(theQuery, rs, isSingle);
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
        		rs.beforeFirst();
        		System.out.println("MySQL Results " + cnt);
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
        		rs.beforeFirst();
        		System.out.println("MySQL Results " + cnt);
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
	public void sortMasterColumn(String columnName) {
    	int index = theTableData.getColHeadIdx(columnName);
    	theTableData.sortByColumn(index, !theTableData.isAscending(index));
	}
   
    public String getName() { return theName; }
    
    public int getNumResults() {
		if(theTableData != null) return theTableData.getNumRows();
		return 0;
	}
 
 	public String [] getSummary() { 
 		String [] retVal = new String[2];
 		retVal[0] = theName + ": " + getNumResults(); // CAS513 added #rows (shown on Results Page)
 		retVal[1] = theSummary;
 		return retVal;
 	}
	public boolean isSingle() {return isSingle;}
 	public boolean [] getColumnSelections() {
		try {
			int genColCnt = FieldData.getGenColumnCount(isSingle); // CAS519 adjust for single/pairs
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
    
    private class MultiLineHeaderRenderer extends JList implements TableCellRenderer {
		private static final long serialVersionUID = 3118619652018757230L;

		public MultiLineHeaderRenderer() {
    	    setOpaque(true);
    	    setBorder(BorderFactory.createLineBorder(Color.BLACK));
    	    setBackground(Color.WHITE);
    	    ListCellRenderer renderer = getCellRenderer();
    	    ((JLabel)renderer).setHorizontalAlignment(JLabel.CENTER);
    	    setCellRenderer(renderer);
    	}
    	 
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
    	    } catch (IOException ex) {
    	       ErrorReport.print(ex, "Render table");
    	    }
    	    setListData(v);
    	    return this;
    	  }
    }
    /***************************************************
     * Gets row data for Show Synteny, Align Sequences, or WriteFasta
     * CAS504 added this class to take the place of using hidden columns to get data
     * theTableData.getRowData(row) returns the chr,start,end of all species in line,
     * where some can be blank. CAS520 also return block and collinear
     * CAS521 also return hit# and Gene# for Align; use short name for species
     */
    private class RowData {
		public RowData () {}
		
		public boolean loadRow(int row) {
			try {
    			HashMap <String, Object> colHeadVal = theTableData.getRowLocData(row); // only needed columns are returned
    			HashMap <String, Integer> sp2x = new HashMap <String, Integer> ();
    			int isp=0;
    			
    			for (String colHead : colHeadVal.keySet()) {
    				Object colVal = colHeadVal.get(colHead);
    				if (colVal instanceof String) {
    					String str = (String) colVal;
    					if (str.equals("") || str.equals(Q.empty)) continue; // the blanks species
    				}  
    				if (colHead.contentEquals(Q.blockCol)) { // CAS520 add block and collinear
    					String x = (String) colVal;
    					x =  (x.contains(".")) ? x.substring(x.lastIndexOf(".")+1) : "0";
    					block = Integer.parseInt(x);
    					continue;
    				}
    				if (colHead.contentEquals(Q.runCol)) {
    					String x = (String) colVal;
    					x =  (x.contains(".")) ? x.substring(x.lastIndexOf(".")+1) : "0";
    					collinear = Integer.parseInt(x);
    					continue;
    				}
    				if (colHead.contentEquals(Q.hitCol)) {	// CAS521
    					String x = String.valueOf(colVal);
    					hitnum = Integer.parseInt(x);
    					continue;
    				}
				
    				String [] field = colHead.split(Q.delim); // speciesName\nChr or Start or End
    				if (field.length!=2) continue;
    				String species=field[0];
    				String col=field[1];
    				
    				String sVal="";
    				int iVal=0;
    				if (col.equals(Q.chrCol)) {
    					if (colVal instanceof Integer) sVal = String.valueOf(colVal);
    					else                      sVal = (String) colVal;
    				}
    				else if (colVal instanceof Integer) { iVal = (Integer) colVal; }
    				else if (colVal instanceof String)  { sVal = (String)  colVal; }
    				else {
    					System.out.println("Symap error: Row Data " + colHead + " " + colVal + " is not type string or integer");
    					return false;
    				}
    				
    				int i0or1=0;						// only two species, none blank
    				if (sp2x.containsKey(species)) 
    					i0or1 = sp2x.get(species);
    				else {
    					sp2x.put(species, isp);
    					spAbbr[isp] = species;
    					i0or1 = isp;
    					isp++;
    					if (isp>2) {System.err.println("Symap Error: species " + isp); break;} // should not happen
    				}
    				if (col.equals(Q.chrCol)) 		  chrNum[i0or1] = sVal;
    				else if (col.equals(Q.hStartCol)) start[i0or1] = iVal;
    				else if (col.equals(Q.hEndCol))   end[i0or1] = iVal;
    				else if (col.equals(Q.gNCol))	  genenum[i0or1] = sVal;	// CAS521
    			}
    			SpeciesSelectPanel spPanel = theQueryPanel.getSpeciesPanel();
    			
    			if (spAbbr[1]==null || spAbbr[1]=="") {
    				Utilities.showWarningMessage("The abbrev_names are the same, cannot continue...");
    				return false;
    			}
    			spName[0] = theParentFrame.getDisplayFromAbbrev(spAbbr[0]);
    			spName[1] = theParentFrame.getDisplayFromAbbrev(spAbbr[1]);
    			
    			for (isp=0; isp<2; isp++) {
    				spIdx[isp] =  spPanel.getSpIdxFromSpName(spName[isp]);
    				chrIdx[isp] = spPanel.getChrIdxFromChrNumSpIdx(chrNum[isp], spIdx[isp]);
    			}
    			return true;
    		} catch (Exception e) {ErrorReport.print(e, "Getting row data"); return false;}
		}
		public String loadRowAsString(int row) {
			try {
				String outLine = theTableData.getRowData(row); // colName, value
    			return outLine;
    		} catch (Exception e) {ErrorReport.print(e, "Getting row data"); return "error";}
		}
		
		String [] spName = {"",""}; 
		String [] spAbbr = {"",""}; 
		int [] spIdx = {0,0};
		int [] chrIdx = {0,0};
		
		String [] chrNum = {"",""};
		int [] start = {0,0};
		int [] end = {0,0};
		String [] genenum = {"",""};
		
		int hitnum=0;				
		int block=0;
		int collinear=0;
    }
    /***********************************************************************
     ** Export file
     *********************************************************************/
    private void exportPopup() {
		final ExportFile ex = new ExportFile();
		ex.setVisible(true);
		final int mode = ex.getSelection();
		
		if (mode == ex.ex_cancel) return;
		else if (mode==ex.ex_csv) ex.writeCSV();
		else if (mode==ex.ex_html) ex.writeHTML();
		else if (mode==ex.ex_fasta) ex.writeFASTA();
			
		setPanelSelected();
	}
	
    private class ExportFile extends JDialog {
		private static final long serialVersionUID = 1L;
		public final int ex_csv = 0, ex_html = 1, ex_fasta= 2, ex_cancel= 3;
	   
    	public ExportFile() {
    		setModal(true);
    		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    		setTitle("Export Table Rows");
    		
    	// Files
    		JRadioButton btnCSV = new JRadioButton("CSV"); btnCSV.setBackground(Color.white);
    		btnCSV.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent arg0) {
					nMode = ex_csv;
				}
			});
    		JRadioButton btnHTML =  new JRadioButton("HTML");btnHTML.setBackground(Color.white); //CAS547 white for linux
    		btnHTML.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent arg0) {
					nMode = ex_html;
				}
			});
    		
    		JPanel rowPanel = createPanel(true);
            rowPanel.add(Box.createHorizontalStrut(15));
            rowPanel.add(new JLabel("Include Row column:")); rowPanel.add(Box.createHorizontalStrut(5));
            
            btnYes = new JRadioButton("Yes"); btnYes.setBackground(Color.white);
            JRadioButton btnNo = new JRadioButton("No");btnNo.setBackground(Color.white);
            ButtonGroup inc = new ButtonGroup();
    		inc.add(btnYes);
    		inc.add(btnNo);
    		btnYes.setSelected(true);
    		rowPanel.add(btnYes); rowPanel.add(Box.createHorizontalStrut(5));
    		rowPanel.add(btnNo);
    		
    		JRadioButton btnFASTA = new JRadioButton("FASTA");btnFASTA.setBackground(Color.white);
    		btnFASTA.addActionListener(new ActionListener() {
    			public void actionPerformed(ActionEvent arg0) {
    				nMode = ex_fasta;
    			}
    		});
    		if (isSingle) btnFASTA.setEnabled(false);
	   	  
    		ButtonGroup grp = new ButtonGroup();
    		grp.add(btnCSV);
    		grp.add(btnHTML);
	        grp.add(btnFASTA);  
	    	btnCSV.setSelected(true);
	    	nMode = ex_csv;
    			
    		JPanel selectPanel = createPanel(false);
    		selectPanel.add(new JLabel("Table rows and columns")); selectPanel.add(Box.createVerticalStrut(5));
    		selectPanel.add(btnCSV); 			selectPanel.add(Box.createVerticalStrut(5));
    		selectPanel.add(btnHTML);			selectPanel.add(Box.createVerticalStrut(5));
	        selectPanel.add(rowPanel);
	        
	        selectPanel.add(new JSeparator());
	        selectPanel.add(new JLabel("Table sequence")); selectPanel.add(Box.createVerticalStrut(5));
	        selectPanel.add(btnFASTA); 
	        
	    // buttons
        	JButton btnOK = new JButton("OK");
        	btnOK.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					setVisible(false);
				}
			});
    		JButton btnCancel = new JButton("Cancel");
    		btnCancel.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					nMode = ex_cancel;
					setVisible(false);
				}
			});
    		btnOK.setPreferredSize(btnCancel.getPreferredSize());
    		btnOK.setMaximumSize(btnCancel.getPreferredSize());
    		btnOK.setMinimumSize(btnCancel.getPreferredSize());
    		
    		JPanel buttonPanel = createPanel(true);
    		buttonPanel.add(btnOK);			buttonPanel.add(Box.createHorizontalStrut(5));
    		buttonPanel.add(btnCancel);		
  
	      // Finish 
    		JPanel mainPanel = createPanel(false);
    		mainPanel.add(selectPanel); 	mainPanel.add(Box.createVerticalStrut(5));
    		mainPanel.add(new JSeparator());
    		mainPanel.add(buttonPanel);
    		mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    		add(mainPanel);
    		
    		pack();
    		this.setResizable(false);
    		setLocationRelativeTo(null); 
        }
    	private JPanel createPanel(boolean isRow) {
    		JPanel xPanel = new JPanel();
    		if (isRow) {
    			xPanel.setLayout(new BoxLayout(xPanel, BoxLayout.LINE_AXIS));
    			xPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
    		}
    		else {
    			xPanel.setLayout(new BoxLayout(xPanel, BoxLayout.PAGE_AXIS)); 
    			xPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
    			xPanel.setAlignmentY(Component.TOP_ALIGNMENT);
    		}
    		xPanel.setBackground(Color.white);
    		return xPanel;
    	}
        public int getSelection() { return nMode; }
        
        JRadioButton btnYes;
        int nMode = -1;
        
        /*********************************************************
         * Write CSV - works with 504
         */
        private void writeCSV() {
        try {
    		PrintWriter outFH = getFileHandle(ex_csv, "query.csv", true);
    		if (outFH==null) return;
    		
    		boolean incRow = btnYes.isSelected();
			
    		setPanelEnabled(false);
    		boolean reset = false;
    		if(theTable.getSelectedRowCount() == 0) {
    			theTable.selectAll();
    			reset = true;
    		}
    		int [] selRows = theTable.getSelectedRows();
    		
    		// Columns names
    		for(int x=0; x<theTable.getColumnCount()-1; x++) {
    			if (!incRow && x==0) continue; // CAS540 for row check
    			
    			outFH.print(reloadCleanString(theTable.getColumnName(x)) + ",");
    		}
    		outFH.println(reloadCleanString(theTable.getColumnName(theTable.getColumnCount()-1)));
    		
    		// rows
    		for(int x=0; x<selRows.length; x++) {
    			for(int y=0; y<theTable.getColumnCount()-1; y++) {
    				if (!incRow && y==0) continue; // CAS540 for row check
    				
    				outFH.print(reloadCleanString(theTable.getValueAt(selRows[x], y)) + ",");
    			}
    			outFH.println(reloadCleanString(theTable.getValueAt(selRows[x], theTable.getColumnCount()-1)));
    			outFH.flush();
    		}
    		System.out.println("Wrote " + selRows.length + " rows                  ");
    		outFH.close();
    		
    		if(reset)
    			theTable.getSelectionModel().clearSelection();
    		setPanelEnabled(true);
    	} catch(Exception e) {ErrorReport.print(e, "Write delim file");}
        }
       
        private void writeHTML() { // CAS542 add
        try {
        	PrintWriter outFH = getFileHandle(ex_html, "query.html", false);
    		if (outFH==null) return;
    	
    		boolean incRow = btnYes.isSelected();
			
    		setPanelEnabled(false);
    		boolean reset = false;
    		if(theTable.getSelectedRowCount() == 0) {
    			theTable.selectAll();
    			reset = true;
    		}
    		int [] selRows = theTable.getSelectedRows();
    		
    		// html header
    		outFH.print("<!DOCTYPE html><html>\n<head>\n"
    				+ "<title>SyMAP Query Table Results</title>\n"
    				+ "<style>\n"
    				+  "body {font-family: Verdana, Arial, Helvetica, sans-serif;  font-size: 14px; }\n"
    				+ ".ty    {border: 1px  solid black; border-spacing: 1px; border-collapse: collapse;margin-left: auto; margin-right: auto;}\n"
    				+ ".ty td {border: 1px  solid black; padding: 5px; width: 80px; word-wrap: break-word; }" 
    				+ "</style>\n</head>\n<body>\n"
    				+ "<a id='top'></a>\n");
    	 
    		outFH.println("<p><center><b><big>SyMAP Results</big></b></center>\n");
    		outFH.println("<br><center>Filter: " + theSummary + "</center>\n");
    		outFH.print("<p><table class='ty'>\n<tr>");
    		
    		// Columns names
    		for(int x=0; x<theTable.getColumnCount(); x++) {
    			if (!incRow && x==0) continue; 
    			String col = reloadCleanString(theTable.getColumnName(x));
    			col = col.replace(" ","<br>");
    			outFH.print("<td><b><center>" + col + "</center></b></th>");
    		}
    		
    		// rows
    		for(int x=0; x<selRows.length; x++) {
    			outFH.print("\n<tr>");
    			for(int y=0; y<theTable.getColumnCount(); y++) {
    				if (!incRow && y==0) continue; 
    				
    				outFH.print("<td>" + reloadCleanString(theTable.getValueAt(selRows[x], y)));
    			}
    			outFH.flush();
    		}
    		outFH.print("\n</table>\n");
    		if (selRows.length>100) outFH.print("<p><a href='#top'>Go to top</a>\n");
    		outFH.print("</body></html>\n");
    		System.out.println("Wrote " + selRows.length + " rows                  ");
    		outFH.close();
    		
    		if(reset)
    			theTable.getSelectionModel().clearSelection();
    		setPanelEnabled(true);
    	} catch(Exception e) {ErrorReport.print(e, "Write delim file");}
        }
        
        /******************************************************************
         * Write fasta 
         */
        private void writeFASTA() {
        try {
        	PrintWriter outFH = getFileHandle(ex_fasta, "query.fa", true);
    		if (outFH==null) return;
    		
    		Thread inThread = new Thread(new Runnable() {
    			public void run() {
    				try {
    					setPanelEnabled(false);
    					
    					boolean reset = false;
    					if(theTable.getSelectedRowCount() == 0) {
    						theTable.selectAll();
    						reset = true;
    					}
    					long startTime = Utilities.getNanoTime();
    					int [] selRows = theTable.getSelectedRows();
    					int selNum = selRows.length;
    					if (selNum>500) {
    						if (!Utilities.showContinue("Export FASTA", 
    							"Selected " + selNum + " row to export. This is a slow function. \n" +
    							"It may take over a minute to export each 500 rows of sequences.")) 
    						{
    							if(reset)theTable.getSelectionModel().clearSelection();
    							setPanelEnabled(true);
    							return;
    						}
    					}
    					int seqNum = 1, pairNum=1;
    					RowData rd = new RowData();
    						
    					for(int x=0; x<selNum; x++) {
    						if (!rd.loadRow(x)) {
    							outFH.close();
    							if(reset) theTable.getSelectionModel().clearSelection();
    	    					setPanelEnabled(true);
    	    					invalidate();
    							return;
    						}
    							
    						for (int i=0; i<2; i++) {
    							// this getSequence is slow.
    							String seq = theParentFrame.getSequence(rd.start[i], rd.end[i], rd.chrIdx[i]);
    								
    							String outString = ">SEQ" + String.format("%06d  ", seqNum)  
    										+ rd.spName[i] + " Chr " + rd.chrNum[i] 
    										+ " Start " + rd.start[i] + " End " + rd.end[i];
    							outString += " Pair#" + pairNum;
    							outFH.println(outString);
    							outFH.println(seq);
    							outFH.flush();
    							seqNum++;
    						}
    						pairNum++;						
    						System.out.print("Wrote: " + ((int)((((float)x)/selRows.length) * 100)) + "%\r");
    					}
    					outFH.close();
    					
    					Utilities.printElapsedNanoTime("Wrote " + (seqNum-1) + " sequences", startTime);
    					
    					if(reset)
    						theTable.getSelectionModel().clearSelection();
    					setPanelEnabled(true);
    					invalidate();
    				}
    				catch(Exception e) {ErrorReport.print(e, "Write fasta");}
    			}
    			});
    			inThread.start();
    		}
    		catch(Exception e) {ErrorReport.print(e, "Save as fasta");}
        }
        // CAS548 add append
        private PrintWriter getFileHandle(int type, String fname, boolean bAppend) {
        	String saveDir = Globals.getExport(); // CAS547 change to call globals
    		
    		JFileChooser chooser = new JFileChooser(saveDir);
    		chooser.setSelectedFile(new File(fname));
    		if(chooser.showSaveDialog(theParentFrame) != JFileChooser.APPROVE_OPTION) return null;
    		if(chooser.getSelectedFile() == null) return null;
    		
    		String saveFileName = chooser.getSelectedFile().getAbsolutePath();
    		if (type==ex_csv) {
    			if(!saveFileName.endsWith(".csv")) saveFileName += ".csv";
    			System.out.println("Exporting CSV to " + saveFileName);
    		}
    		else if (type==ex_html) {
    			if(!saveFileName.endsWith(".html")) saveFileName += ".html";
    			System.out.println("Exporting HTML to " + saveFileName);
    		}
    		else {
        		if(!saveFileName.endsWith(".fasta") && !saveFileName.endsWith(".fa")) saveFileName += ".fa";
        		System.out.println("Exporting Fasta to " + saveFileName);	
    		}
    		boolean append=true;
    		if (new File(saveFileName).exists()) {
    			if (bAppend) {
    				int rc = Utilities.showConfirmFile(saveFileName);
    				if (rc==0) return null;
    				if (rc==1) append=false;
    			}
    			else {
    				if (!Utilities.showConfirm2("File exists","File '" + saveFileName + "' exists.\nOverwrite?")) return null;
    				append=false;
    			}
    		}
    		
    		PrintWriter out=null;
    		try {
    			out = new PrintWriter(new FileOutputStream(saveFileName, append));
    		}
    		catch (Exception e) {ErrorReport.print(e, "Cannot open file - " + saveFileName);}
    		return out;
        }
        private String reloadCleanString(Object obj) {
        	if(obj != null) {
        		String val = obj.toString().trim();
        		if (val.equals("")) return "''";
        		
        		val = val.replaceAll("[\'\"]", "");
        		val = val.replaceAll("\\n", " ");
        		val = val.replaceAll(",", ";"); // CAS540 quit returning with ''; just replace comma
        		return val;
        	}
        	else return "''";
    	}
	} // end exportfile
	
    /**************************************************************
     * Private variable
     */
    private SortTable theTable = null;
    private TableData theTableData = null;
    private JScrollPane sPane = null;
    private JTextField rowCount = null;
    
    private JTextField loadStatus = null, txtMargin = null;
    private JCheckBox chkHigh = null;
    private JButton btnShowSynteny = null, btnShowAlign = null, btnShowRow = null; 
    private JButton btnUnSelectAll = null, btnExport = null;
    private JComboBox <String> cmbSynOpts = null;
    
    private ListSelectionListener selListener = null;
    private Thread buildThread = null;
    
    private JCheckBox [] 				chkGeneralFields = null;
    private JCheckBox [][] 				chkSpeciesFields = null;
    private Vector<Vector<JCheckBox>> 	chkAnnoFields = null;
   
    private ActionListener colSelectChange = null;
    private boolean [] theOldSelections = null; 
    
	private JPanel tableButtonPanel = null, tableStatusPanel = null;
	private JPanel generalColSelectPanel = null, spLocColSelectPanel = null;
	private JPanel spAnnoColSelectPanel = null, statsPanel = null;
	private JPanel buttonPanel = null;
	
	private JPanel columnPanel = null, columnButtonPanel = null;
	private JButton btnShowColumns = null, btnClearColumns=null, btnDefaultColumns=null;
	private JButton btnShowStats = null, btnHelp = null;
	private JTextField txtStatus = null;
    
	public SyMAPQueryFrame theParentFrame = null;
	public QueryPanel      theQueryPanel = null;
	
	private AnnoData theAnnoKeys = null;
  
	private String theName = "", theSummary = "", theQuery = "";
	private boolean isSingle = false; // Cannot use theQueryPanel.isOrphan because only good for last query
    
	private static int nTableID = 0;
}
