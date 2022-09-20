package symapQuery;
/***************************************************
 * Results table of Query
 */
import java.awt.Cursor;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Vector;
import java.util.HashMap;

import javax.swing.JTable;
import javax.swing.JTextField;

import util.ErrorReport;

public class TableData implements Serializable {
	private static final long serialVersionUID = 8279185942173639084L;
    private static final long DISPLAY_INTERVAL = 1000; 
    
	/********************************************
	 * Called from TableDataPanel in buildTable and setTable
	 */
	public static TableData createModel(String [] columns, TableData srcTable, TableDataPanel parent) {
		TableData tabObj = 	new TableData(parent);
		
		tabObj.arrData = 	new Object[srcTable.arrData.length][columns.length];
		tabObj.arrHeaders = 	new TableDataHeader[columns.length];
		
		for(int x=0; x<columns.length; x++) {
			int srcColIdx = srcTable.getColHeadIdx(columns[x]);
			
			tabObj.arrHeaders[x] = srcTable.arrHeaders[srcColIdx];
			
			for(int y=0; y<srcTable.arrData.length; y++) 
				tabObj.arrData[y][x] = srcTable.arrData[y][srcColIdx];
		}
		return tabObj;
	}
    	
	public static String [] orderColumns(JTable sourceTable, String [] selectedColumns) {
		String [] retVal = new String[selectedColumns.length];
		Vector<String> columns = new Vector<String> ();
		for(int x=selectedColumns.length-1; x>=0; x--)
			columns.add(selectedColumns[x]);
		
		int targetIndex = 0;
		for(int x=0; x<sourceTable.getColumnCount(); x++) {
			String columnName = sourceTable.getColumnName(x);
			
			int columnIdx = columns.indexOf(columnName);
			if(columnIdx >= 0) {
				retVal[targetIndex] = columnName;
				targetIndex++;
				columns.remove(columnIdx);
			}
		}
		while(columns.size() > 0) {
			retVal[targetIndex] = columns.get(columns.size()-1);
			columns.remove(columns.size()-1);
			targetIndex++;
		}
		return retVal;
	}
	/***************************************
	 * Constructors
	 */
    public TableData(TableDataPanel parent) {
    	vData = 		new Vector<Vector<Object>>();
    	vHeaders = 	new Vector<TableDataHeader>();
    	theParent = parent;
    }

    public TableData(String unused, TableDataPanel parent) {
    	this(parent);
    }
    /********************************************************/
    public void sortMasterList(String columnName) {
    	theParent.sortMasterColumn(columnName);
    }

    public void setColumnHeaders( String [] species, String [] annoKeys) {
        vHeaders.clear();
        	
        // General
        String [] genColNames = FieldData.getGeneralColHead();
        
    	for(int x=0; x<genColNames.length; x++)
    		addColumnHeader(genColNames[x], Integer.class);
    	
    	// Loc headers and type
    	String [] spColNames =	FieldData.getSpeciesColHead();
    	Class <?> [] colType =  FieldData.getSpeciesColType();
    	
    	for(int x=0; x < species.length; x++) {
    		for (int c=0; c< spColNames.length; c++)
    			addColumnHeader(species[x]+ Q.delim + spColNames[c], colType[c]);
    	}
    	// array of species+Q.delim+keywords in correct order
    	for (int x=0; x<annoKeys.length; x++) {
    		addColumnHeader(annoKeys[x], String.class);
    	}
    }
    private void addColumnHeader(String columnName, Class<?> type) {
    	try {
    		vHeaders.add(new TableDataHeader(columnName, type));
    	} 
    	catch(Exception e) {ErrorReport.print(e, " Add column header " + columnName);}
	}
    
    public int getColHeadIdx(String columnName) {
    	int retVal = -1;
  
		for (int x=0;x<arrHeaders.length && retVal<0;x++) {
			if (arrHeaders[x].getColumnName().equals(columnName))
				return x;
		}
    	return -1;
	}  
     
    /************************************************************
     * XXX Add rows to table
     */
    public void addRowsWithProgress(Vector <DBdata> rowsFromDB, JTextField progress) {
    	try {
    		boolean cancelled = false;
  		
    		for (DBdata dd : rowsFromDB) {
    			if (cancelled) {
    				System.err.println("Cancel adding rows to table");
    				return;
    			}
    			
    			Vector <Object> row = dd.makeRow();
    			vData.add(row);
    			
    			if (progress != null && (vData.size() % DISPLAY_INTERVAL == 0)) {
    				if(progress.getText().equals("Cancelled")) cancelled = true;
    				else progress.setText("Displaying " + vData.size() + " rows");
    			}
    		}
    		
    		if (Q.TEST_TRACE) System.out.println("Final Results " + getNumRows());
    		progress.setText("");  	
    	} 
    	catch (Exception e) {ErrorReport.print(e, "add Rows With Progress");}
    }
    
    public void finalize() {
    	arrHeaders = new TableDataHeader[vHeaders.size()];
    	vHeaders.copyInto(arrHeaders);
    	vHeaders.clear();

    	arrData = new Object[vData.size()][];
    	Iterator <Vector<Object>> iter = vData.iterator();
    	Vector<Object> rowV;
    	
    	int x = 0;
    	while(iter.hasNext()) {
    		arrData[x] = new Object[arrHeaders.length];
    		rowV = iter.next();
    		rowV.copyInto(arrData[x]);
    		rowV.clear();
    		x++;
    	}
    	vData.clear();
    }

    public Object getValueAt(int row, int column) {
    	try {
    		return arrData[row][column];
    	}
    	catch(Exception e) {ErrorReport.print(e, "get value at row " + row + " col " + column); return null;}
    }
    public String getColumnName(int column) {
        try {
        	if (column==-1) return "";
            return arrHeaders[column].getColumnName();
        } catch(Exception e) {ErrorReport.print(e, "get column name at " + column); return null;}
    }
    public Class<?> getColumnType(int column) {
    	try {
    		return arrHeaders[column].getColumnClass();
    	}
    	catch(Exception e) {ErrorReport.print(e, "get column type at " + column);return null;}
    }
    public boolean isAscending(int column) {
    	try {
    		if (column==-1) return true;
    		return arrHeaders[column].isAscending();
    	}
    	catch(Exception e) {ErrorReport.print(e, "sort ascending");return false;}
    }

    public int getNumColumns() {
		if(arrHeaders!=null) return arrHeaders.length;
		if (vHeaders!=null) vHeaders.size();
		return 0;
    }

    public int getNumRows() {
		if (arrData!=null) return arrData.length;
		if (vData!=null)	return vData.size();
		return 0;
    }

    public void sortByColumn(int column, boolean ascending) {
		if (column == -1) return; // CAS504
		arrHeaders[column].setAscending(ascending);
		sortByColumn(column);
    }
    
    public void sortByColumn(final int column) {
		if (column == -1) return; // CAS504
		try {
        	theParent.setCursor(new Cursor(Cursor.WAIT_CURSOR));
        	Arrays.sort(arrData, new ColumnComparator(column));
        	
        	//Always keep rows in same order
        	for(int x=0; x<arrData.length; x++) {
        		arrData[x][0] = x+1;
        	}
        	arrHeaders[column].flipAscending();
        	theParent.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
		}
		catch (Exception e) {System.out.println(e.getMessage());};
    }

    public void clear() {
    	arrHeaders = null;
    	if(arrData != null) {
    		for(int x=0; x<arrData.length; x++) 
    			arrData[x] = null;
    		arrData = null;
    	}
    	vHeaders.clear();
    	for(int x=0; x<vData.size(); x++)
    		vData.get(x).clear();
    	vData.clear();
    }
    // XXX
    private class ColumnComparator implements Comparator<Object []> {
    	public ColumnComparator(int column) {
    		nColumn = column;
    	}
    	public int compare(Object [] o1, Object [] o2) { // CAS504 rewrote much of this
    		if (nColumn == -1) return 0;
    		
			if (arrHeaders[nColumn].getColumnName().equals(Q.rowCol)) {
				return 0;
			}
			if(o1[nColumn] == null || o2[nColumn] == null) { // DBdata checks, there should be no null
				System.out.println(arrHeaders[nColumn].getColumnName() + " Incorrect null value, not expected for sort");
				return 0;
			}
		
			int retval = 0;
		
			// this check will work for any of the columns that may not have values
			if (o1[nColumn] instanceof String && ((String)o1[nColumn]).equals(Q.empty) &&
				o2[nColumn] instanceof String && ((String)o2[nColumn]).equals(Q.empty)) {
				return 0;
			}
			else if (o1[nColumn] instanceof String && ((String)o1[nColumn]).equals(Q.empty)) {
				if(arrHeaders[nColumn].isAscending()) retval = 1;
				else retval = -1;
			}		
			else if (o2[nColumn] instanceof String && ((String)o2[nColumn]).equals(Q.empty)) {
				if(arrHeaders[nColumn].isAscending()) retval = -1;
				else retval = 1;
			}	
			else if (arrHeaders[nColumn].getColumnName().equals(Q.blockCol)) {
				String [] vals1 = ((String)o1[nColumn]).split("\\."); 
				String [] vals2 = ((String)o2[nColumn]).split("\\.");
				
				for(int x=0; x<vals1.length && x<vals2.length && retval == 0; x++) {
					boolean valid = true;
					Integer leftVal = null;
					Integer rightVal = null;
					
					try {
						leftVal = Integer.parseInt(vals1[x]); // CAS514 new Integer(Integer.parseInt(vals1[x]));
						rightVal = Integer.parseInt(vals2[x]);       // new Integer(Integer.parseInt(vals2[x]));
					}
					catch(Exception e) {valid = false;}
					if(valid) retval = leftVal.compareTo(rightVal);
					else      retval = vals1[x].compareTo(vals2[x]);
				}
			}
			/** CAS514 remove special processing for Q.geneNCol **/
			else if (arrHeaders[nColumn].getColumnClass() == String.class) {
				boolean bIsIntCompare = false; // Chr is string but can have integer values
				Integer val1 = -1, val2 = -1;  // CAS514 int->Integer so can use compareTo below
				
				if (arrHeaders[nColumn].getColumnName().contains(Q.chrCol)) {
					bIsIntCompare = true;
					
					try {
						val1 = Integer.parseInt(((String)o1[nColumn]));
						val2 = Integer.parseInt(((String)o2[nColumn]));
					}
					catch(Exception e) {bIsIntCompare = false;}
				}
				
				if (bIsIntCompare || o1[nColumn] instanceof Integer)
					retval = val1.compareTo(val2); //new Integer(val1).compareTo(new Integer(val2));
				else
					retval = ((String)o1[nColumn]).compareTo((String)o2[nColumn]);
			}
			else if(arrHeaders[nColumn].getColumnClass() == Integer.class) {
				retval = ((Integer)o1[nColumn]).compareTo((Integer)o2[nColumn]);
			}
			// all columns are currently String or Integer, but in case...
			else if (arrHeaders[nColumn].getColumnClass() == Boolean.class) 
				retval = ((Boolean)o1[nColumn]).compareTo((Boolean)o2[nColumn]);
			else if (arrHeaders[nColumn].getColumnClass() == Float.class)
				retval = ((Float)o1[nColumn]).compareTo((Float)o2[nColumn]);
			else if (arrHeaders[nColumn].getColumnClass() == Double.class) 
				retval = ((Double)o1[nColumn]).compareTo((Double)o2[nColumn]);
			else if( arrHeaders[nColumn].getColumnClass() == Long.class)
				retval = ((Long)o1[nColumn]).compareTo((Long)o2[nColumn]);
			
			if(arrHeaders[nColumn].isAscending())
				return retval;
			else
				return retval * -1;
    	}
    
		private int nColumn;
    } // end ColumnComparator
   
    /**********************************************************
     * For TableDataPanel methods that needs the following values for both ends of hit
     * chrNum, start, end
     */
    public HashMap <String, Object> getRowData(int row) {
		HashMap <String, Object> headVal = new HashMap <String, Object> ();
		for (int i=0; i<arrHeaders.length; i++) {
			String colName = arrHeaders[i].getColumnName();
			if (colName.contains(Q.chrCol) || colName.contains(Q.startCol) || colName.contains(Q.endCol)) 
			{	
				headVal.put(colName, arrData[row][i]);
			}
		}
		return headVal;
    }
    //attributes
    //private boolean bReadOnly = false;  // CAS504 DELETE
    //private String cashe something...   // CAS504 DELETE
    
    //Static data structures
    private TableDataHeader [] arrHeaders = null;
    private Object [][] arrData = null;
    
    //Dynamic data structures
    private Vector<TableDataHeader> vHeaders = null;
    private Vector<Vector<Object>> vData = null; 
    private TableDataPanel theParent = null;
}
