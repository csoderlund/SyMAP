package symapQuery;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.Vector;
import java.util.Enumeration;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
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

import dotplot.FilterData;
import util.Utilities;

import symap.SyMAP;
import symap.projectmanager.common.Project;
import symap.sequence.Sequence;

public class ListDataPanel extends JPanel {
	public static final String MENU_LABEL = "List Results";

	private static final long serialVersionUID = -3827610586702639105L;
	
	private static final boolean SHOW_ALL_COLUMNS = false;
	
	public static final int VIEW_MODE_LOCAL = 0;
	public static final int QUERY_MODE_LOCAL = 0;

	private static final int ANNO_COLUMN_WIDTH = 100;
	private static final int ANNO_PANEL_WIDTH = 900;
	private static final int MARGIN_WIDTH = 80;
	
	private static final String [] HELP_COL = {
		"Clear selection:",
		"Margin:",
		"Select Columns:",
		"Result table:",
		" ",
		" ",
		" "
	};
	
	private static final String [] HELP_VAL = { 
		"Clears the active selection in the table",
		"Adds to the range of the selected hit in the alignment view",
		"Displays selection of available columns (Location/Annotation)",
		"Change column order by dragging column to selected location",
		"Click the column headers to sort",
		"Example: if you want to see ordered start positions by group,",
		"   click 'Start' then 'Chr'"
	};

	public String [] getSummary() { 
		String [] retVal = new String[2];
		
		retVal[0] = theName;
		retVal[1] = theQuerySummary;
		
		return retVal;
	}
	public ListDataPanel(SyMAPQueryFrame parentFrame, String resultName, int queryMode, int viewMode, boolean [] selections) {
		theParentFrame = parentFrame;
		nViewMode = viewMode;
		if(selections != null) {
			theOldSelections = new boolean[selections.length];
			for(int x=0; x<selections.length; x++)
				theOldSelections[x] = selections[x];
		}
		
		if(queryMode == QUERY_MODE_LOCAL) {
			strSubQuery = theParentFrame.getLocalSubQuery();
			strAnnotSubQuery = theParentFrame.getLocalAnnotSubQuery();
			//strSubQuery += " ORDER BY hit_idx";
			theQuerySummary = theParentFrame.getLocalSubQuerySummary();
			theName = resultName;
			
			colSelectChange = new ActionListener() {
				public void actionPerformed(ActionEvent arg0) {
					setTable();
			        showTable();
				}
			};
		}
		initialize(queryMode);
	}
	
	public ListDataPanel(SyMAPQueryFrame parentFrame, int viewMode) {
		theParentFrame = parentFrame;
		nViewMode = viewMode;
		
		bValidList = readReloadFile(",");

		colSelectChange = new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				setTable();
		        showTable();
			}
		};
		
//		setColumnSelections();
//		showTable();
		//Makes the table appear
		if(isVisible()) {
			setVisible(false);
			setVisible(true);
		}
	}
	
	private void setTable() {
		if(theTable != null) {
			theTable.removeListeners();
		}
		
		if(SHOW_ALL_COLUMNS)
			theTable = new SortTable(theTableData);
		else {
			String [] columns = TableData.orderColumns(theTable, getSelectedColumns());
			theTable = new SortTable(TableData.createModel(columns, theTableData, getInstance()));
		}
		
        theTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        theTable.autofitColumns();
        	
        theTable.getSelectionModel().addListSelectionListener(selListener);
		theTable.addSingleClickListener(sngClick);
        theTable.addDoubleClickListener(dblClick);

        theTable.setTableHeader(new ColumnHeaderToolTip(theTable.getColumnModel()));

        MultiLineHeaderRenderer renderer = new MultiLineHeaderRenderer();
        Enumeration<TableColumn> en = theTable.getColumnModel().getColumns();
        while (en.hasMoreElements()) {
          ((TableColumn)en.nextElement()).setHeaderRenderer(renderer);
        } 
	}
	
	public ListDataPanel getInstance() { return this; }
	
	public String [] getSelectedColumns() {
		Vector<String> retVal = new Vector<String> ();
		String [] annoColumns = theAnnotations.getColumns();
		
		//TODO need to go through
		for(int x=0; x<chkGeneralFields.length; x++) {
			if(chkGeneralFields[x].isSelected())
				retVal.add(chkGeneralFields[x].getText());
		}
//		retVal.add("Row");
//		retVal.add("HGroup");
//		retVal.add("HGrpSize");
//		retVal.add("HitIdx");
//		retVal.add("BlockNum");
//		retVal.add("RunSize");
		for(int x=0; x<getNumAnnoCheckBoxes(); x++) {
			if(getAnnoCheckBoxAt(x).isSelected()) {
				retVal.add(annoColumns[x]);
			}
		}
		
		String [] species = theParentFrame.getDisplayNames();
		for(int x=0; x<species.length; x++) {
			for(int y=0; y<chkSpeciesFields[x].length; y++) {
				if(chkSpeciesFields[x][y].isSelected()) {
					retVal.add(species[x]+"\n"+chkSpeciesFields[x][y].getText());
				}
			}
/*			String name = species[x]+"\n#PGF"; 
			System.out.println(name);
			retVal.add(name);*/
		}
		
//		for (int x = 0; x < species.length; x++)
//		{
//			retVal.add(species[x]+"\n#PGF");			
//		}
		
		return retVal.toArray(new String[retVal.size()]);
	}
	
	public String getName() { return theName; }
	public String getQuerySummary() { return theQuerySummary; }

	private void setProjectAnnotations() {
		Vector<Project> projs = theParentFrame.getProjects();
		Iterator<Project> iter = projs.iterator();
		
		String projList = "" + iter.next().getID();
		while(iter.hasNext())
			projList += "," + iter.next().getID();
		
		try {
			Connection conn = theParentFrame.getDatabase().getConnection();
			Statement stmt = conn.createStatement();
			
			theAnnotations = new AnnoData();
			
			String query = 	"SELECT annot_key.proj_idx, annot_key.keyname FROM annot_key WHERE" +
							" annot_key.proj_idx";
							
			if(projs.size() == 1)
				query += " = " + projList + " ORDER BY annot_key.proj_idx ASC, keyname ASC";
			else
				query += " IN (" + projList + ") ORDER BY annot_key.proj_idx ASC, keyname ASC";

			ResultSet rset = stmt.executeQuery(query);
			while(rset.next()) {
				int projIDX = rset.getInt("annot_key.proj_idx");
				theAnnotations.addAnnoIDForSpecies(projIDX, rset.getString("annot_key.keyname"));
			}
			for(int x=0; x<theAnnotations.getNumSpecies(); x++) {
				theAnnotations.addAnnoIDByPosition(x, "All_Anno");
			}
			rset.close();
			stmt.close();
			conn.close();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	private String getProjectDisplayName(int index) {
		for(int x=0; x<theParentFrame.getProjects().size(); x++) {
			if(theParentFrame.getProjects().get(x).getID() == index)
				return theParentFrame.getProjects().get(x).getDisplayName();
		}
		return null;
	}
	
	private void loadAndInitialize(final BufferedReader in) {
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        setProjectAnnotations();
        setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
        setAlignmentY(LEFT_ALIGNMENT);
        showProgress();
		buildThread = new Thread(new Runnable() {
			public void run() {
				try {
					buildTable(null, in);
					setColumnSelections();
					showTable();
					//Makes the table appear
					if(isVisible()) {
						setVisible(false);
						setVisible(true);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		buildThread.setPriority(Thread.MIN_PRIORITY);
		buildThread.start();
	}
	
	//Common initialization for all constructors
	private void initialize(int queryMode) {
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        nQueryMode = queryMode;
        
        setProjectAnnotations();
        setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
        setAlignmentY(LEFT_ALIGNMENT);
        showProgress();
		buildThread = new Thread(new Runnable() {
			public void run() {
				try {
					ResultSet rset = loadData();
					if(rset != null) {
						buildTable(rset, null);
					}
					else
					{
						return;
					}
					setColumnSelections();
					showTable();
					//Makes the table appear
					if(isVisible()) {
						setVisible(false);
						setVisible(true);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		buildThread.setPriority(Thread.MIN_PRIORITY);
		buildThread.start();
	}
	
	public int getNumResults() {
		int retVal = 0;
		if(theTableData != null)
			retVal = theTableData.getNumRows();
		return retVal;
	}
	
	private ResultSet loadData() {
        FieldData theFields = FieldData.getLocalFields(theAnnotations.getColumns(),theParentFrame.getDisplayNames());

        
        Connection conn;
        Statement stmt;
        
       	// We need the included group indices to know which annotations to load.
    	TreeMap<Integer,TreeSet<Integer>> grpIdxList = new TreeMap<Integer,TreeSet<Integer>>();
    	if (theParentFrame.lQueryPanel.isOrphan())
    	{
	    	SpeciesSelectPanel sp = theParentFrame.lQueryPanel.speciesPanel;
	    
	    	String[] chrs = sp.theChromosomeIndicies;
	    	for (int i = 0; i < chrs.length; i++)
	    	{
	    		int projIdx = sp.getSpeciesIndex(i);
	    		int grpIdx = sp.getChromosomeIndex(i);
	    		grpIdxList.put(projIdx, new TreeSet<Integer>());
	    		if (grpIdx != -1)
	    		{
	    			grpIdxList.get(projIdx).add(grpIdx);
	    		}
	    		else
	    		{
	    			for (String chr : chrs[i].split(","))
	    			{
	    				grpIdxList.get(projIdx).add(Integer.parseInt(chr));
	    			}
	    		}
	    	}
    	}
        try {
        	conn = theParentFrame.getDatabase().getConnection();
        	stmt = conn.createStatement();
        	addBlockScores(stmt);
        	//stmt.setFetchSize(1000);

        	String strQuery = "";
        	if(nQueryMode == QUERY_MODE_LOCAL) {
        		//Make sure needed fields are loaded
        		strQuery = theFields.getLocalFilterQuery(strSubQuery,strAnnotSubQuery, theParentFrame.isSynteny(),
        					theParentFrame.isCollinear(), grpIdxList);
        	}
        	if(loadStatus.getText().equals("Cancelled")) return null; 
        	loadStatus.setText("Getting data");
        	try
        	{
        		BufferedWriter w = new BufferedWriter(new FileWriter("/tmp/symap_query.txt"));
        		w.write(strQuery);
        		w.close();
        	} catch(Exception e){};
        	try
        	{
        		return stmt.executeQuery(strQuery);
        	}
        	catch(Exception e)
        	{
        		e.printStackTrace();
        		return null;
        	}
        } catch(Exception e) {
        	
        	return null;
        }
	}
	private void addBlockScores(Statement s)
	{
		try
		{
			if (!Utilities.tableHasColumn("blocks", "score", s))
			{
				System.out.println("Updating blocks table");
				s.execute("alter table blocks add score integer default 0 after corr");
				s.execute("update blocks set score=(select count(*) from pseudo_block_hits where block_idx=idx)");
			}
		}
		catch(Exception e)
		{
			System.out.println("Unable to verify blocks table");
		}
	}
	
    private void buildTable(ResultSet rs, BufferedReader in) {
        FieldData theFields = FieldData.getLocalFields(theAnnotations.getColumns(), theParentFrame.getDisplayNames());
        
		tableButtonPanel = createTableButtonPanel();
		tableStatusPanel = createTableStatusPanel();
		generalFieldSelectPanel = createGeneralSelectPanel();
		fieldSelectPanel = createFieldSelectPanel();
		annoSelectPanel = createAnnotationSelectPanel();
		
    	columnPanel = new JPanel();
    	columnPanel.setLayout(new BoxLayout(columnPanel, BoxLayout.PAGE_AXIS));
    	
    	columnPanel.setVisible(false);
//		generalFieldSelectPanel.setVisible(false);
//		fieldSelectPanel.setVisible(false);
//		annoSelectPanel.setVisible(false);
		
		showColumnSelect = new JButton("Select Columns");
		showColumnSelect.setBackground(Color.WHITE);
		showColumnSelect.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if(columnPanel.isVisible()) {
					showColumnSelect.setText("Select Columns");
					columnPanel.setVisible(false);
				}
				else {
					showColumnSelect.setText("Hide");
					columnPanel.setVisible(true);
				}
				showTable();
			}
		});
		
		//TODO
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
			}
		});

        dblClick = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				
			}
		};
		
		sngClick = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
			}
		};
		
		selListener = new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent arg0) {
			}
		};

        theTableData = new TableData("hitList" + (nTableID++) + ".dat", this);
        theTableData.setColumnHeaders(theFields.getDisplayFields(), theFields.getDisplayTypes());
        if(rs != null) {
        	theTableData.addRowsWithProgress(rs, theFields, loadStatus);//, theFilterSettings);
            fillStatsPanel(true);
        }
        else {
        	theTableData.addRowsWithProgress(in, theFields, loadStatus, theParentFrame.getDisplayNames());
            fillStatsPanel(false);
        }
        	
        theTableData.addSpeciesFields(theParentFrame.getDisplayNames());
        theTableData.finalize();

		if(SHOW_ALL_COLUMNS)
			theTable = new SortTable(theTableData);
		else
			theTable = new SortTable(TableData.createModel(getSelectedColumns(), theTableData, this));
        theTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        theTable.autofitColumns();
        	
        ColumnHeaderToolTip header = new ColumnHeaderToolTip(theTable.getColumnModel());
        header.setToolTips(theFields.getDBFieldDescriptions());
        theTable.setTableHeader(header);

        
        theTable.getSelectionModel().addListSelectionListener(selListener);
		theTable.addSingleClickListener(sngClick);
        theTable.addDoubleClickListener(dblClick);
//    	theTable.getTableHeader().setBackground(Color.WHITE);
        MultiLineHeaderRenderer renderer = new MultiLineHeaderRenderer();
        Enumeration<TableColumn> en = theTable.getColumnModel().getColumns();
        while (en.hasMoreElements()) {
          ((TableColumn)en.nextElement()).setHeaderRenderer(renderer);
        }         
    }
    
    public void sortMasterColumn(String columnName) {
    	int index = theTableData.getColumnHeaderIndex(columnName);
    	theTableData.sortByColumn(index, !theTableData.isAscending(index));
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
    	
    	JPanel innerPanel = new JPanel();
    	innerPanel.setLayout(new BoxLayout(innerPanel, BoxLayout.PAGE_AXIS));
    	innerPanel.setBackground(Color.WHITE);
    	innerPanel.add(generalFieldSelectPanel);
    	innerPanel.add(Box.createVerticalStrut(10));
    	innerPanel.add(fieldSelectPanel);
    	innerPanel.add(Box.createVerticalStrut(10));
    	innerPanel.add(annoSelectPanel);
    	
    	JScrollPane colPane = new JScrollPane(innerPanel);
    	columnPanel.removeAll();
    	columnPanel.setLayout(new BoxLayout(columnPanel, BoxLayout.PAGE_AXIS));
    	columnPanel.add(colPane);
    	
    	add(tableButtonPanel);
    	add(Box.createVerticalStrut(10));
    	add(tableStatusPanel);
    	add(sPane);
    	add(Box.createVerticalStrut(10));
    	add(statsPanel);
    	add(Box.createVerticalStrut(10));
    	add(columnPanel);
    	add(Box.createVerticalStrut(10));
    	
    	JPanel tempPanel = new JPanel();
    	tempPanel.setLayout(new BoxLayout(tempPanel, BoxLayout.LINE_AXIS));
    	tempPanel.setBackground(Color.WHITE);
    	tempPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
    	
    	tempPanel.add(showColumnSelect);
    	tempPanel.add(Box.createHorizontalStrut(20));
    	tempPanel.add(btnShowStats);
    	//So the resized button doesn't get clipped
    	tempPanel.add(Box.createHorizontalStrut(20));
    	tempPanel.setMaximumSize(tempPanel.getPreferredSize());
    	add(tempPanel);
    	
    	if(theTable != null)
    		if(theTable.getRowCount() > 0)
    			updateTableStatus(theName + " : " + theTable.getRowCount() + " rows");
    		else
    			updateTableStatus(theName + " : No results");

    	invalidate();
    	validateTable();
    	
    	theParentFrame.updateResultCount(this);
    }
    
    public boolean [] getColumnSelections() {
    	int numCols = chkGeneralFields.length + (chkSpeciesFields.length * chkSpeciesFields[0].length);
    	for(int x=0; x<chkAnno.size(); x++) {
    		numCols += chkAnno.get(x).size();
    	}
    	
    	boolean [] retVal = new boolean[numCols];
    	int targetPos = 0;
    	for(int x=0; x<chkGeneralFields.length; x++) {
    		retVal[targetPos] = chkGeneralFields[x].isSelected();
    		targetPos++;
    	}
    	
    	for(int x=0; x<chkSpeciesFields.length; x++) {
    		for(int y=0; y<chkSpeciesFields[x].length; y++) {
    			retVal[targetPos] = chkSpeciesFields[x][y].isSelected();
    			targetPos++;
    		}
    	}
    	
    	for(int x=0; x<chkAnno.size(); x++) {
    		for(int y=0; y<chkAnno.get(x).size(); y++) {
    			retVal[targetPos] = chkAnno.get(x).get(y).isSelected();
    			targetPos++;
    		}
    	}
    	return retVal;
    }
    
    public void setColumnSelections() {
    	if(theOldSelections == null)
    		return; 
    	
    	int targetPos = 0;
    	for(int x=0; x<chkGeneralFields.length; x++) {
    		chkGeneralFields[x].setSelected(theOldSelections[targetPos]);
    		targetPos++;
    	}
    	
    	for(int x=0; x<chkSpeciesFields.length; x++) {
    		for(int y=0; y<chkSpeciesFields[x].length; y++) {
    			chkSpeciesFields[x][y].setSelected(theOldSelections[targetPos]);
    			targetPos++;
    		}
    	}
    	
    	for(int x=0; x<chkAnno.size(); x++) {
    		for(int y=0; y<chkAnno.get(x).size(); y++) {
    			chkAnno.get(x).get(y).setSelected(theOldSelections[targetPos]);
    			targetPos++;
    		}
    	}
    	
    	theOldSelections = null;
    	setTable();
    }
    
    private JPanel createGeneralSelectPanel() {
    	JPanel retVal = new JPanel();
    	retVal.setLayout(new BoxLayout(retVal, BoxLayout.PAGE_AXIS));
    	retVal.setAlignmentX(Component.LEFT_ALIGNMENT);
    	retVal.setBackground(Color.WHITE);
    
    	JPanel genRow = new JPanel();
    	genRow.setLayout(new BoxLayout(genRow, BoxLayout.LINE_AXIS));
    	genRow.setBackground(Color.WHITE);
    	genRow.setAlignmentX(Component.LEFT_ALIGNMENT);

    	String [] genCols = FieldData.getGeneralColumns();
    	Boolean [] genColDef = FieldData.getGeneralColumnDef();
    	chkGeneralFields = new JCheckBox[genCols.length];
    	for(int x=0; x<chkGeneralFields.length; x++) {
    		chkGeneralFields[x] = new JCheckBox(genCols[x]);
    		chkGeneralFields[x].setAlignmentX(Component.LEFT_ALIGNMENT);
    		chkGeneralFields[x].setBackground(Color.WHITE);
    		chkGeneralFields[x].addActionListener(colSelectChange);
    		chkGeneralFields[x].setSelected(genColDef[x]);
    		
    		genRow.add(chkGeneralFields[x]);
    		genRow.add(Box.createHorizontalStrut(5));
    	}
    	
    	retVal.add(genRow);
    	
    	retVal.setBorder(BorderFactory.createTitledBorder("General"));
    	retVal.setMaximumSize(retVal.getPreferredSize());

    	return retVal;
    }
    
    private JPanel createFieldSelectPanel() {
    	JPanel retVal = new JPanel();
    	retVal.setLayout(new BoxLayout(retVal, BoxLayout.PAGE_AXIS));
    	retVal.setAlignmentX(Component.LEFT_ALIGNMENT);
    	retVal.setBackground(Color.WHITE);
        	
    	String [] species = theParentFrame.getDisplayNames();
    
    	chkSpeciesFields = new JCheckBox[species.length][4];
    	for(int x=0; x<species.length; x++) {
    		JPanel row = new JPanel();
        	row.setLayout(new BoxLayout(row, BoxLayout.LINE_AXIS));
        	row.setAlignmentX(Component.LEFT_ALIGNMENT);
        	row.setBackground(Color.WHITE);
    		
        	JLabel tmpLabel = new JLabel(species[x]);
        	if(ANNO_COLUMN_WIDTH - tmpLabel.getPreferredSize().width > 0)
        		row.add(Box.createHorizontalStrut(ANNO_COLUMN_WIDTH - tmpLabel.getPreferredSize().width));
    		
    		row.add(new JLabel(species[x]));
    		row.add(Box.createHorizontalStrut(10));
    		chkSpeciesFields[x][0] = new JCheckBox("Chr");
    		chkSpeciesFields[x][0].setSelected(true);
    		chkSpeciesFields[x][1] = new JCheckBox("Start");
    		chkSpeciesFields[x][1].setSelected(false);
    		chkSpeciesFields[x][2] = new JCheckBox("End");
    		chkSpeciesFields[x][2].setSelected(false);
    		chkSpeciesFields[x][3] = new JCheckBox("#RGN");
    		chkSpeciesFields[x][3].setSelected(false);
        	
        	for(int y=0; y<chkSpeciesFields[x].length; y++) {
        		chkSpeciesFields[x][y].setAlignmentX(Component.LEFT_ALIGNMENT);
        		chkSpeciesFields[x][y].setBackground(Color.WHITE);
        		chkSpeciesFields[x][y].addActionListener(colSelectChange);
        		row.add(chkSpeciesFields[x][y]);
        		if(ANNO_COLUMN_WIDTH - chkSpeciesFields[x][y].getPreferredSize().width > 0)
        			row.add(Box.createHorizontalStrut(ANNO_COLUMN_WIDTH - chkSpeciesFields[x][y].getPreferredSize().width));

        	}
        	retVal.add(row);
        	if(x < species.length - 1)
        		retVal.add(new JSeparator());
    	}
    	
    	retVal.setBorder(BorderFactory.createTitledBorder("Location"));
    	retVal.setMaximumSize(retVal.getPreferredSize());

    	return retVal;
    }
    private JPanel createAnnotationSelectPanel() {
    	JPanel retVal = new JPanel();
    	retVal.setLayout(new BoxLayout(retVal, BoxLayout.LINE_AXIS));
    	retVal.setAlignmentX(Component.LEFT_ALIGNMENT);
    	retVal.setBackground(Color.WHITE);

    	chkAnno = new Vector<Vector<JCheckBox>> ();
    	int [] speciesIDs = theAnnotations.getSpeciesIDList();
    	for(int x=0; x<speciesIDs.length; x++) {
    		Vector<JCheckBox> temp = new Vector<JCheckBox> ();
    		for(int y=0; y<theAnnotations.getNumberAnnosForSpecies(speciesIDs[x]); y++){
    			String annotName = theAnnotations.getAnnoIDforSpeciesAt(speciesIDs[x], y);
    			JCheckBox chkTemp = new JCheckBox(annotName);
    			chkTemp.setBackground(Color.WHITE);
    			chkTemp.addActionListener(colSelectChange);
    			if (annotName.equalsIgnoreCase("description"))
    			{
    				chkTemp.setSelected(true);
    			}
    			temp.add(chkTemp);
    		}
    		chkAnno.add(temp);
    	}
    	
    	JPanel colPanel = new JPanel();
    	colPanel.setLayout(new BoxLayout(colPanel, BoxLayout.PAGE_AXIS));
    	colPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
    	colPanel.setBackground(Color.WHITE);
    	
    	Iterator<Vector<JCheckBox>> spIter = chkAnno.iterator();
    	int pos = 0;
    	while(spIter.hasNext()) {
        	JPanel row = new JPanel();
        	row.setLayout(new BoxLayout(row, BoxLayout.LINE_AXIS));
        	row.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        	row.setBackground(Color.WHITE);
        	
        	int curWidth = ANNO_COLUMN_WIDTH;
        	JLabel tmpLabel = new JLabel(theAnnotations.getSpeciesNameByID(speciesIDs[pos]));
        	if(ANNO_COLUMN_WIDTH - tmpLabel.getPreferredSize().width > 0)
        		row.add(Box.createHorizontalStrut(ANNO_COLUMN_WIDTH - tmpLabel.getPreferredSize().width));
        	row.add(tmpLabel);
        	row.add(Box.createHorizontalStrut(10));
        	Iterator<JCheckBox> annoIter = spIter.next().iterator();
        	while(annoIter.hasNext()) {
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
        		if(ANNO_COLUMN_WIDTH - chkTemp.getPreferredSize().width > 0)
        			row.add(Box.createHorizontalStrut(ANNO_COLUMN_WIDTH - chkTemp.getPreferredSize().width));
        	}
        	colPanel.add(row);
        	if(spIter.hasNext())
        		colPanel.add(new JSeparator());
        	pos++;
    	}
    	
    	retVal.add(colPanel);
    	
    	retVal.setBorder(BorderFactory.createTitledBorder("Annotation Columns"));
    	retVal.setMaximumSize(retVal.getPreferredSize());
    	return retVal;
    }
    
    private void validateTable() {
    	validate();
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
    	
    	JPanel botRow = new JPanel();
    	botRow.setLayout(new BoxLayout(botRow, BoxLayout.LINE_AXIS));
    	botRow.setBackground(Color.WHITE);
    	botRow.setAlignmentX(Component.LEFT_ALIGNMENT);
    	
    	topRow.add(new JLabel("For selected hits with "));
        txtMargin = new JTextField(5);
        txtMargin.setText("50");
        Dimension d = txtMargin.getPreferredSize();
        d.width = MARGIN_WIDTH;
        txtMargin.setMinimumSize(d);
        
        topRow.add(txtMargin);
        topRow.add(new JLabel(" kb margin: "));
        btnShowAlign = new JButton("Show Synteny");
        btnShowAlign.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnShowAlign.setBackground(Color.WHITE);
        btnShowAlign.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				try{
					if(nQueryMode == QUERY_MODE_LOCAL) {
						double padding = Double.parseDouble(txtMargin.getText()) * 1000;

						boolean reset = false;
						if(theTable.getSelectedRowCount() == 0) {
							reset = true;
							theTable.selectAll();
						}
						
						int [] selRows = theTable.getSelectedRows();
						for(int x=0; x<selRows.length; x++) {
							int selRow = selRows[x];
							if(theTableData.getValueAt(selRow, theTableData.getColumnHeaderIndex("Start 2")) != null) {
	 							double track1Start = (Integer)theTableData.getValueAt(selRow, theTableData.getColumnHeaderIndex("Start 1"));
								track1Start = Math.max(0, track1Start - padding);
								double track2Start = (Integer)theTableData.getValueAt(selRow, theTableData.getColumnHeaderIndex("Start 2"));
								track2Start = Math.max(0, track2Start - padding);
								double track1End = (Integer)(theTableData.getValueAt(selRow, theTableData.getColumnHeaderIndex("End 1"))) + padding;
								double track2End = (Integer)(theTableData.getValueAt(selRow, theTableData.getColumnHeaderIndex("End 2"))) + padding;
		
								int p1Idx = (Integer)theTableData.getValueAt(selRow, theTableData.getColumnHeaderIndex("Proj 1 idx"));
								int p2Idx = (Integer)theTableData.getValueAt(selRow, theTableData.getColumnHeaderIndex("Proj 2 idx"));
		
								int grp1Idx = (Integer)theTableData.getValueAt(selRow, theTableData.getColumnHeaderIndex("Group 1 idx"));
								int grp2Idx = (Integer)theTableData.getValueAt(selRow, theTableData.getColumnHeaderIndex("Group 2 idx"));
		
								SyMAP symap = new SyMAP(theParentFrame.getApplet(), theParentFrame.getDatabase(), getInstance());
							
								symap.getDrawingPanel().setMaps(1);
								symap.getHistory().clear(); // clear history - mdb added 10/12/09
		
								FilterData fd2 = new FilterData();
								fd2.setShowHits(FilterData.ALL_HITS); 
								symap.getDrawingPanel().setHitFilter(1,fd2);
								Sequence.setDefaultShowAnnotation(true); // mdb added 2/1/10
		
								// PSEUDO to PSEUDO
								symap.getDrawingPanel().setSequenceTrack(1,p2Idx,grp2Idx,Color.CYAN);
								symap.getDrawingPanel().setSequenceTrack(2,p1Idx,grp1Idx,Color.GREEN);
								symap.getDrawingPanel().setTrackEnds(1,track2Start,track2End);
								symap.getDrawingPanel().setTrackEnds(2,track1Start,track1End);
								symap.getFrame().show();
								Sequence.setDefaultShowAnnotation(false);
							}
							else {
								System.out.println("Unable to show synteny for orphan");
							}
						}
						
						if(reset)
							theTable.getSelectionModel().clearSelection();
					}
				} catch(Exception e) {
					e.printStackTrace();
				}
			}
		}); 
        btnUnSelectAll = new JButton("Unselect All");
        btnUnSelectAll.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnUnSelectAll.setBackground(Color.WHITE);
        btnUnSelectAll.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				theTable.getSelectionModel().clearSelection();
			}
		});       

        btnSaveCSV = new JButton("Save CSV");
        btnSaveCSV.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnSaveCSV.setBackground(Color.WHITE);
        btnSaveCSV.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				boolean reset = false;
				if(theTable.getSelectedRowCount() == 0) {
					theTable.selectAll();
					reset = true;
				}
				writeDelimFile(",", theTable.getSelectedRows());
				if(reset)
					theTable.getSelectionModel().clearSelection();
			}
		});       
        
        btnSaveReload = new JButton("Save For Reload");
        btnSaveReload.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnSaveReload.setBackground(Color.WHITE);
        btnSaveReload.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				boolean reset = false;
				if(theTable.getSelectedRowCount() == 0) {
					theTable.selectAll();
					reset = true;
				}
				writeReloadFile(",", theTable.getSelectedRows());
				if(reset)
					theTable.getSelectionModel().clearSelection();
			}
		});  
        
        btnMuscleAlign = new JButton("Align Sequences");
        btnMuscleAlign.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnMuscleAlign.setBackground(Color.WHITE);
        btnMuscleAlign.addActionListener(new ActionListener() {
        	public void actionPerformed(ActionEvent arg0) {
        		boolean doAlign = true;
        		
        		if(theTable.getSelectedRowCount() == 0 && theTable.getRowCount() > 10) {
        			if(JOptionPane.showConfirmDialog(getInstance(), "Align all " + theTable.getRowCount() +  " sequence pairs?", "Align Sequences", JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.NO_OPTION) {
        				doAlign = false;
        			}
        		}
        		
        		if(doAlign) {
        				Thread inThread = new Thread(new Runnable() {
        					public void run() {
        						try {
        							setPanelEnabled(false);
        										
        							boolean reset = false;
        							if(theTable.getSelectedRowCount() == 0) {
        								theTable.selectAll();
        								reset = true;
        							}
        							int [] selRows = theTable.getSelectedRows();
        							Vector<String> theNames = new Vector<String> ();
        							Vector<String> theSequences = new Vector<String> ();

        							for(int x=0; x<selRows.length; x++) {
        								String name = "";
        								int start = (Integer) theTableData.getValueAt(selRows[x], theTableData.getColumnHeaderIndex("Start 1"));
        								int end = (Integer) theTableData.getValueAt(selRows[x], theTableData.getColumnHeaderIndex("End 1"));
        								int group = (Integer) theTableData.getValueAt(selRows[x], theTableData.getColumnHeaderIndex("Group 1 idx"));
        											
        								name = theTableData.getValueAt(selRows[x], theTableData.getColumnHeaderIndex("Species 1")) + " ";
        								name += "Chr " + theTableData.getValueAt(selRows[x], theTableData.getColumnHeaderIndex("Chr 1")) + " ";
        								name += "Start " + theTableData.getValueAt(selRows[x], theTableData.getColumnHeaderIndex("Start 1")) + " ";
        								name += "End " + theTableData.getValueAt(selRows[x], theTableData.getColumnHeaderIndex("End 1"));
        								if(!theNames.contains(name)) {
        									theNames.add(name);
        									theSequences.add(theParentFrame.getSequence(start, end, group));
        								}
        											
        								start = (Integer) theTableData.getValueAt(selRows[x], theTableData.getColumnHeaderIndex("Start 2"));
        								end = (Integer) theTableData.getValueAt(selRows[x], theTableData.getColumnHeaderIndex("End 2"));
        								group = (Integer) theTableData.getValueAt(selRows[x], theTableData.getColumnHeaderIndex("Group 2 idx"));
        											
        								name = theTableData.getValueAt(selRows[x], theTableData.getColumnHeaderIndex("Species 2")) + " ";
        								name += "Chr " + theTableData.getValueAt(selRows[x], theTableData.getColumnHeaderIndex("Chr 2")) + " ";
        								name += "Start " + theTableData.getValueAt(selRows[x], theTableData.getColumnHeaderIndex("Start 2")) + " ";
        								name += "End " + theTableData.getValueAt(selRows[x], theTableData.getColumnHeaderIndex("End 2"));
        								if(!theNames.contains(name)) {
        									theNames.add(name);
        									theSequences.add(theParentFrame.getSequence(start, end, group));
        								}
        							}
        							
        							theParentFrame.addAlignmentTab(getInstance(), theNames.toArray(new String[0]), theSequences.toArray(new String[0]));
        							theNames.clear();
        							theSequences.clear();
        							
        							if(reset)
        								theTable.getSelectionModel().clearSelection();
        							setPanelEnabled(true);
        						}
        						catch(Exception e) {
        							e.printStackTrace();
        						}
        					}
        				});
        				inThread.start();
        			}
        		}
		});  

        
        btnSaveFasta = new JButton("Save as Fasta");
        btnSaveFasta.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnSaveFasta.setBackground(Color.WHITE);
        btnSaveFasta.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				try {
		    		String saveDir = System.getProperty("user.dir") + "/fasta/";
		    		File temp = new File(saveDir);
		    		if(!temp.exists())
		    			temp.mkdir();

					JFileChooser chooser = new JFileChooser(saveDir);
					if(chooser.showSaveDialog(theParentFrame) == JFileChooser.APPROVE_OPTION) {
						if(chooser.getSelectedFile() != null) {
							String saveFileName = chooser.getSelectedFile().getAbsolutePath();
							if(!saveFileName.endsWith(".fasta"))
								saveFileName += ".fasta";
							
							final String fName = saveFileName;
							Thread inThread = new Thread(new Runnable() {
								public void run() {
									try {
										setPanelEnabled(false);
										PrintWriter out = new PrintWriter(new FileWriter(new File(fName)));
										
										boolean reset = false;
										if(theTable.getSelectedRowCount() == 0) {
											theTable.selectAll();
											reset = true;
										}
										int [] selRows = theTable.getSelectedRows();
										int index = 0;
										for(int x=0; x<selRows.length; x++) {
											int start = (Integer) theTableData.getValueAt(selRows[x], theTableData.getColumnHeaderIndex("Start 1"));
											int end = (Integer) theTableData.getValueAt(selRows[x], theTableData.getColumnHeaderIndex("End 1"));
											int group = (Integer) theTableData.getValueAt(selRows[x], theTableData.getColumnHeaderIndex("Group 1 idx"));
											String seq = theParentFrame.getSequence(start, end, group);
											
											String outString = ">SEQ" + String.format("%09d", index) + " ";
											outString += theTableData.getValueAt(selRows[x], theTableData.getColumnHeaderIndex("Species 1")) + " ";
											outString += "Chr " + theTableData.getValueAt(selRows[x], theTableData.getColumnHeaderIndex("Chr 1")) + " ";
											outString += "Start " + theTableData.getValueAt(selRows[x], theTableData.getColumnHeaderIndex("Start 1")) + " ";
											outString += "End " + theTableData.getValueAt(selRows[x], theTableData.getColumnHeaderIndex("End 1"));
											out.println(outString);
											out.println(seq);
											out.flush();
											index++;
											
											start = (Integer) theTableData.getValueAt(selRows[x], theTableData.getColumnHeaderIndex("Start 2"));
											end = (Integer) theTableData.getValueAt(selRows[x], theTableData.getColumnHeaderIndex("End 2"));
											group = (Integer) theTableData.getValueAt(selRows[x], theTableData.getColumnHeaderIndex("Group 2 idx"));
											seq = theParentFrame.getSequence(start, end, group);
											
											outString = ">SEQ" + String.format("%09d", index) + " ";											
											outString += theTableData.getValueAt(selRows[x], theTableData.getColumnHeaderIndex("Species 2")) + " ";
											outString += "Chr " + theTableData.getValueAt(selRows[x], theTableData.getColumnHeaderIndex("Chr 2")) + " ";
											outString += "Start " + theTableData.getValueAt(selRows[x], theTableData.getColumnHeaderIndex("Start 2")) + " ";
											outString += "End " + theTableData.getValueAt(selRows[x], theTableData.getColumnHeaderIndex("End 2"));
											out.println(outString);
											out.println(seq);
											out.flush();
											index++;
											
											System.out.print("Writing fasta file: " + ((int)((((float)x)/selRows.length) * 100)) + "% complete    \r");
										}
										System.out.println("Fasta file written                                            ");
										out.close();
										if(reset)
											theTable.getSelectionModel().clearSelection();
										setPanelEnabled(true);
										invalidate();
									}
									catch(Exception e) {
										e.printStackTrace();
									}
								}
							});
							inThread.start();
						}
					}

				}
				catch(Exception e) {
					e.printStackTrace();
				}
			}
		});  
       
        topRow.add(btnShowAlign);
        topRow.add(Box.createHorizontalStrut(5));
        
        botRow.add(btnUnSelectAll);
        botRow.add(Box.createHorizontalStrut(5));
        botRow.add(btnSaveCSV);
        botRow.add(Box.createHorizontalStrut(5));
        botRow.add(btnSaveReload);
        botRow.add(Box.createHorizontalStrut(5));
        botRow.add(btnSaveFasta);
        botRow.add(Box.createHorizontalStrut(5));
        botRow.add(btnMuscleAlign);
        
		btnHelp = new JButton("Help");
		btnHelp.setAlignmentX(Component.RIGHT_ALIGNMENT);
		btnHelp.setBackground(UserPrompt.PROMPT);
		btnHelp.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				Utilities.showHTMLPage(null, "Query Results Help", "/html/QueryResultHelp.html");
				//UserPrompt.displayInfo(theParentFrame, "Results Table Help", HELP_COL, HELP_VAL, false);
			}
		});
		topRow.add(Box.createHorizontalStrut(15));
		topRow.add(btnHelp);
        
		topRow.setAlignmentX(LEFT_ALIGNMENT);
		topRow.setMaximumSize(topRow.getPreferredSize());
        
		buttonPanel.add(topRow);
		buttonPanel.add(Box.createVerticalStrut(10));
		buttonPanel.add(botRow);
		
		buttonPanel.setMaximumSize(buttonPanel.getPreferredSize());
		
        return buttonPanel;
    }
    
    public long getSelectedSeq1Start() {
    	if(theTable.getSelectedRow() >= 0)
    		return (Integer) theTableData.getValueAt(theTable.getSelectedRow(), theTableData.getColumnHeaderIndex("Start 1"));
    	return -1;
    }
        
    public long getSelectedSeq2Start() {
    	if(theTable.getSelectedRow() >= 0)
    		return (Integer) theTableData.getValueAt(theTable.getSelectedRow(), theTableData.getColumnHeaderIndex("Start 2"));
    	return -1;
    }
        
    public long getSelectedSeq1End() {
    	if(theTable.getSelectedRow() >= 0)
    		return (Integer) theTableData.getValueAt(theTable.getSelectedRow(), theTableData.getColumnHeaderIndex("End 1"));
    	return -1;
    }
        
    public long getSelectedSeq2End() {
    	if(theTable.getSelectedRow() >= 0)
    		return (Integer) theTableData.getValueAt(theTable.getSelectedRow(), theTableData.getColumnHeaderIndex("End 2"));
    	return -1;
    }
        
    private JPanel createTableStatusPanel() {
    	JPanel thePanel = new JPanel();
    	thePanel.setLayout(new BoxLayout(thePanel, BoxLayout.LINE_AXIS));
    	thePanel.setBackground(Color.WHITE);

    	tableType = new JTextField(20);
    	tableType.setBackground(Color.WHITE);
    	tableType.setBorder(BorderFactory.createEmptyBorder());
    	tableType.setEditable(false);
    	tableType.setMaximumSize(tableType.getPreferredSize());
    	tableType.setAlignmentX(LEFT_ALIGNMENT);

    	rowCount = new JTextField(30);
    	rowCount.setBackground(Color.WHITE);
    	rowCount.setBorder(BorderFactory.createEmptyBorder());
    	rowCount.setEditable(false);
    	rowCount.setMaximumSize(rowCount.getPreferredSize());
    	rowCount.setAlignmentX(LEFT_ALIGNMENT);
    	thePanel.add(tableType);
    	thePanel.add(rowCount);
    	thePanel.setMaximumSize(thePanel.getPreferredSize());
    	
    	return thePanel;
    }
    
    private void updateTableStatus(String status) {
    	if(nViewMode == VIEW_MODE_LOCAL)
    		tableType.setText("Local View");
    	rowCount.setText(status);
    }
    
    private JCheckBox getAnnoCheckBoxAt(int pos) {
    	Iterator<Vector<JCheckBox>> spIter = chkAnno.iterator();
    	while(spIter.hasNext()) {
    		Vector<JCheckBox> temp = spIter.next();
    		if(temp.size() <= pos) {
    			pos -= temp.size();
    		}
    		else
    			return temp.get(pos);
    	}
    	return null;
    }
    
    private int getNumAnnoCheckBoxes() {
    	int total = 0;
    	Iterator<Vector<JCheckBox>> spIter = chkAnno.iterator();
    	while(spIter.hasNext()) {
    		total += spIter.next().size();
    	}
    	return total;
    }

    private void writeReloadFile(String delim, int [] selRows) {
    	try {
    		String saveDir = System.getProperty("user.dir") + "/reload/";
    		File temp = new File(saveDir);
    		if(!temp.exists())
    			temp.mkdir();

			JFileChooser chooser = new JFileChooser(saveDir);
			if(chooser.showSaveDialog(theParentFrame) == JFileChooser.APPROVE_OPTION) {
				if(chooser.getSelectedFile() != null) {
					String saveFileName = chooser.getSelectedFile().getAbsolutePath();
					if(!saveFileName.endsWith(".reload.csv"))
						saveFileName += ".reload.csv";
					
					PrintWriter out = new PrintWriter(new FileWriter(new File(saveFileName)));
					out.print("#Species: ");
					String [] species = theParentFrame.getDisplayNames();
					for(int x=0; x<species.length; x++) {
						if(x>0)
							out.print(delim);
						out.print(cleanString(species[x]));
					}
					out.println();
					out.println("#Summary: " + theQuerySummary);
					out.flush();
					
					for(int x=0; x<theTableData.getNumColumns()-1; x++) {
						out.print(cleanString(theTableData.getColumnName(x)) + delim);
					}
					out.println(cleanString(theTableData.getColumnName(theTableData.getNumColumns()-1)));
					
					for(int x=0; x<selRows.length; x++) {
						for(int y=0; y<theTableData.getNumColumns()-1; y++) {
							out.print(cleanString(theTableData.getValueAt(selRows[x], y)) + delim);
						}
						out.println(cleanString(theTableData.getValueAt(selRows[x], theTableData.getNumColumns()-1)));
						out.flush();
					}
					
					System.out.println((selRows.length * 2) + " sequences saved to " + saveFileName);
				}
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
    }
    
    private boolean readReloadFile(String delim) {
    	try {
    		String readDir = System.getProperty("user.dir") + "/reload/";
    		File temp = new File(readDir);
    		if(!temp.exists())
    			temp.mkdir();

			JFileChooser chooser = new JFileChooser(readDir);
			if(chooser.showOpenDialog(theParentFrame) == JFileChooser.APPROVE_OPTION) {
				String readFileName = chooser.getSelectedFile().getAbsolutePath();

				String line = "";
				BufferedReader in = new BufferedReader(new FileReader(new File(readFileName)));
				line = in.readLine();
				//Use this as the check if the file is valid... assume data is from this point
				if(!line.startsWith("#Species: ")) {
					JOptionPane.showMessageDialog(this, "Not a valid saved query", "Error", JOptionPane.PLAIN_MESSAGE);
					return false; 
				}
				theName = theParentFrame.getNextResultLabel();//line.substring("#Name: ".length());
				
				//Validate loaded projects
				String [] fileNames = line.substring("#Species: ".length()).split(",");
				for(int x=0; x<fileNames.length; x++)
					fileNames[x] = fileNames[x].substring(1, fileNames[x].length()-1);
				String [] speciesNames = theParentFrame.getDisplayNames();
				if(fileNames.length != speciesNames.length) {
					JOptionPane.showMessageDialog(this, "Query was saved from a different set of projects", "Error", JOptionPane.PLAIN_MESSAGE);
					return false;
				}
				for(int x=0; x<fileNames.length; x++) {
					boolean found = false;
					for(int y=0; y<speciesNames.length && !found; y++) {
						if(fileNames[x].equals(speciesNames[y]))
							found = true;
					}
					if(!found) {
						JOptionPane.showMessageDialog(this, "Query was saved from a different set of projects", "Error", JOptionPane.PLAIN_MESSAGE);
						return false;
					}
				}
				
				line = in.readLine();
				theQuerySummary = line.substring("#Summary: ".length());
				
				loadAndInitialize(in);
			}
			else {
				JOptionPane.showMessageDialog(this, "Not a valid saved query", "Error", JOptionPane.PLAIN_MESSAGE);
				return false;
			}
    	}
    	catch(Exception e) {
    		e.printStackTrace();
    	}
    	return true;
    }
    
    private void writeDelimFile(String delim, int [] selRows) {
    	try {
    		String saveDir = System.getProperty("user.dir") + "/csv/";
    		File temp = new File(saveDir);
    		if(!temp.exists())
    			temp.mkdir();
    		
			JFileChooser chooser = new JFileChooser(saveDir);
			if(chooser.showSaveDialog(theParentFrame) == JFileChooser.APPROVE_OPTION) {
				if(chooser.getSelectedFile() != null) {
					String saveFileName = chooser.getSelectedFile().getAbsolutePath();
					if(!saveFileName.endsWith(".csv"))
						saveFileName += ".csv";
					
					PrintWriter out = new PrintWriter(new FileWriter(new File(saveFileName)));
					for(int x=0; x<theTable.getColumnCount()-1; x++) {
						out.print(cleanString(theTable.getColumnName(x)) + delim);
					}
					out.println(cleanString(theTable.getColumnName(theTable.getColumnCount()-1)));
					
					for(int x=0; x<selRows.length; x++) {
						for(int y=0; y<theTable.getColumnCount()-1; y++) {
							out.print(cleanString(theTable.getValueAt(selRows[x], y)) + delim);
						}
						out.println(cleanString(theTable.getValueAt(selRows[x], theTable.getColumnCount()-1)));
						out.flush();
					}
				}
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
    }
    
    private static String cleanString(Object obj) {
    	if(obj != null) {
    		String val = obj.toString();
    		val = val.replaceAll("[\'\"]", "");
    		val = val.replaceAll("\\n", " ");
    		return "'" + val + "'";
    	}
    	else {
    		return "''";
    	}
    }
    private void fillStatsPanel(boolean haveData)
    {
    	if (statsPanel == null) statsPanel = new JPanel();
		statsPanel.setLayout(new BoxLayout(statsPanel, BoxLayout.PAGE_AXIS));
		statsPanel.setBackground(Color.WHITE);
		if(haveData) {
			
			HashSet<String> noAnnots = new HashSet<String>();
			for (Project p : theParentFrame.theProjects)
			{
				if (p.numAnnot == 0)
				{
					noAnnots.add(p.strDBName);
				}
			}
			 
	    	SpeciesSelectPanel sp = theParentFrame.lQueryPanel.speciesPanel;
		    TreeMap<String,Integer> nChroms = new TreeMap<String,Integer>();
		    
	    	String[] chrs = sp.theChromosomeIndicies;
	    	for (int i = 0; i < chrs.length; i++)
	    	{
	    		String projName = sp.getSpecies(i);
	    		int nchrs = chrs[i].split(",").length;
	    		nChroms.put(projName.toLowerCase(), nchrs);
	    	}
			statsPanel.add(new JLabel("Statistics"));
	
			if (!theParentFrame.lQueryPanel.isOrphan())
			{
				for (String pname : theTableData.proj2regions.keySet() )
				{
					int n = Utilities.ctrGet(theTableData.proj2regions,pname); 
					int h = Utilities.ctrGet(theTableData.proj2hits,pname); 
					int a = Utilities.ctrGet(theTableData.proj2annot,pname); 			
					int o = Utilities.ctrGet(theTableData.proj2orphs,pname);
					int nchr = nChroms.get(pname.toLowerCase());
					statsPanel.add(Box.createVerticalStrut(2));
					String label = String.format("%-13s hits: %-7d distinct regions: %-7d annotated: %-7d chrs: %-6d", pname, h, n, a,nchr);
					JLabel theLabel = new JLabel(label);
					theLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
					statsPanel.add(theLabel);
				}
				for (String pname : theTableData.proj2regions.keySet() )
				{
					if (noAnnots.contains(pname))
					{
						JLabel lbl = new JLabel(pname + " is not annotated");
						lbl.setFont(new Font("Monospaced", Font.PLAIN, 12));
						statsPanel.add(lbl);
					}
				}
			}
			else
			{
				for (String pname : theTableData.proj2orphs.keySet() )
				{
					int o = Utilities.ctrGet(theTableData.proj2orphs,pname);
					int nchr = nChroms.get(pname.toLowerCase());
					statsPanel.add(Box.createVerticalStrut(2));
					String label = String.format("%-13s  orphans: %-7d chrs: %-6d",pname, o,nchr);
					JLabel theLabel = new JLabel(label);
					theLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
					statsPanel.add(theLabel);
				}
				for (String pname : noAnnots )
				{
					if (noAnnots.contains(pname))
					{
						JLabel lbl = new JLabel(pname + " is not annotated");
						lbl.setFont(new Font("Monospaced", Font.PLAIN, 12));
						statsPanel.add(lbl);
					}
				}
			}
		}
    }

    public void setCacheFilename(String POGName) { strCacheFileName = POGName; }
    public String getCacheFilename() { return strCacheFileName; }
    public void setStatsPanel(JPanel panel){statsPanel = panel;}
    
    public boolean isValidData() { return bValidList; }
    private static int nTableID = 0;
    
    public void setPanelEnabled(boolean enable) {
    	theTable.setEnabled(enable);
    	rowCount.setEnabled(enable);
    	tableType.setEnabled(enable);
    	txtMargin.setEnabled(enable);
    	btnShowAlign.setEnabled(enable);
    	btnSaveCSV.setEnabled(enable);
    	btnSaveReload.setEnabled(enable);
    	btnUnSelectAll.setEnabled(enable);
    	btnSaveFasta.setEnabled(enable);
    	btnMuscleAlign.setEnabled(enable);
    	showColumnSelect.setEnabled(enable);
    	btnHelp.setEnabled(enable);
    	
    	for(int x=0; x<chkGeneralFields.length; x++)
    		chkGeneralFields[x].setEnabled(enable);
    		
    	for(int x=0; x<chkSpeciesFields.length; x++) {
    		for(int y=0; y<chkSpeciesFields[x].length; y++)
    			chkSpeciesFields[x][y].setEnabled(enable);
    	}
    }

    private SortTable theTable = null;
    private TableData theTableData = null;
    private JScrollPane sPane = null;
    private int nQueryMode = QUERY_MODE_LOCAL;
    private JTextField rowCount = null;
    private JTextField tableType = null;
    private JTextField loadStatus = null;
    private JTextField txtMargin = null;
    private JButton btnShowAlign = null;
    private JButton btnSaveCSV = null;
    private JButton btnSaveReload = null;
    private JButton btnUnSelectAll = null;
    private JButton btnSaveFasta = null;
    private JButton btnMuscleAlign = null;
    private ActionListener dblClick = null;
    private ActionListener sngClick = null;
    private ListSelectionListener selListener = null;
    private String strSubQuery = "";
    private String strAnnotSubQuery = "";
    private int nViewMode = VIEW_MODE_LOCAL;
	private String theName = "";
	private String theQuerySummary = "";
    private Thread buildThread = null;
    private String strCacheFileName = null;
    public SyMAPQueryFrame theParentFrame = null;
    private Vector<Vector<JCheckBox>> chkAnno = null;
    private JCheckBox [][] chkSpeciesFields = null;
    private JCheckBox [] chkGeneralFields = null;
    
    private AnnoData theAnnotations = null;
    private ActionListener colSelectChange = null;
    private boolean [] theOldSelections = null; 
    
//	private JPanel buttonPanel = null;
	private JPanel tableButtonPanel = null;
	private JPanel tableStatusPanel = null;
	private JPanel generalFieldSelectPanel = null;
	private JPanel fieldSelectPanel = null;
	private JPanel annoSelectPanel = null;
	private JPanel statsPanel = null;
	
	private JPanel columnPanel = null;
	
	private JButton showColumnSelect = null;
	private JButton btnShowStats = null;
	private JButton btnHelp = null;
	private boolean bValidList = true;
    
    private class AnnoData {
    	public AnnoData() {
    		theAnnos = new Vector<AnnoID> ();
    	}
    	
    	public void addSpecies(int speciesID) {
    		if((getSpeciesPosition(speciesID)) < 0) {
    			theAnnos.add(new AnnoID(getProjectDisplayName(speciesID), speciesID));
    			Collections.sort(theAnnos);
    		}
    	}
    	
    	public int getNumberAnnosForSpecies(int speciesID) {
    		int pos = getSpeciesPosition(speciesID);
    		
    		if(pos < 0) return -1;
    		
    		return theAnnos.get(pos).getNumAnnoIDs();
    	}
    	
    	public void addAnnoIDForSpecies(int speciesID, String annoID) {
    		addSpecies(speciesID);
    		int pos = getSpeciesPosition(speciesID);
    		theAnnos.get(pos).addAnnoID(annoID);
    	}
    	
    	public void addAnnoIDByPosition(int species, String annoID) {
    		theAnnos.get(species).addAnnoID(annoID);
    	}
    	
    	public String getAnnoIDforSpeciesAt(int speciesID, int annoPos) {
    		int pos = getSpeciesPosition(speciesID);
    		return theAnnos.get(pos).getAnnoAt(annoPos);
    	}
    	
    	public int getNumSpecies() {
    		return theAnnos.size();
    	}
    	
    	public int [] getSpeciesIDList() {
    		int [] retVal = new int[theAnnos.size()];
    		
    		Iterator<AnnoID> iter = theAnnos.iterator();
    		int x = 0;
    		while(iter.hasNext()) {
    			retVal[x] = iter.next().getSpeciesID();
    			x++;
    		}
    		return retVal;
    	}
    	
    	public String getSpeciesNameByID(int speciesID) {
    		int pos = getSpeciesPosition(speciesID);
    		if(pos < 0) return null;
    		return theAnnos.get(pos).getSpeciesName();
    	}
    	
    	public String [] getColumns() {
    		Vector<String> retVal = new Vector<String> ();
    		
    		Iterator<AnnoID> speciesIter = theAnnos.iterator();
    		while(speciesIter.hasNext()) {
    			AnnoID temp = speciesIter.next();
    			String species = temp.getSpeciesName();
    			for(int x=0; x<temp.getNumAnnoIDs(); x++) {
    				retVal.add(species+"\n"+temp.getAnnoAt(x));
    			}
    		}
    		
    		return retVal.toArray(new String[retVal.size()]);
    	}
    	
    	private int getSpeciesPosition(int speciesID) {
    		int pos = 0;
    		Iterator<AnnoID> iter = theAnnos.iterator();
    		boolean found = false;
    		while(iter.hasNext() && !found) {
    			AnnoID temp = iter.next();
    			if(temp.getSpeciesID() == speciesID)
    				found = true;
    			else
    				pos++;
    		}
    		if(!found)
    			return -1;
    		return pos;
    	}
    	
    	private Vector<AnnoID> theAnnos = null;
    	private class AnnoID implements Comparable<AnnoID> {
    		public AnnoID(String speciesName, int speciesID) {
    			theIDs = new Vector<String> ();
    			theSpeciesName = speciesName;
    			theSpeciesID = speciesID;
    		}
    		
    		public void addAnnoID(String annoID) {
    			theIDs.add(annoID);
    		}
    		
    		public int getNumAnnoIDs() { return theIDs.size(); }
    		public String getAnnoAt(int pos) { return theIDs.get(pos); }
    		
    		public String getSpeciesName() { return theSpeciesName; }
    		public int getSpeciesID() { return theSpeciesID; }
    		
    		private String theSpeciesName = "";
    		private int theSpeciesID = -1; 
    		private Vector<String> theIDs = null;
		
			public int compareTo(AnnoID arg0) {
				return theSpeciesName.compareTo(arg0.theSpeciesName);
			}
    	}
    }
    
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
    
    public class MultiLineHeaderRenderer extends JList implements TableCellRenderer {
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
    	      ex.printStackTrace();
    	    }
    	    setListData(v);
    	    return this;
    	  }
    	}

}
