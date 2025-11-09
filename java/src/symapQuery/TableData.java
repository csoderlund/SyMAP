package symapQuery;

import java.awt.Cursor;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.TreeSet;
import java.util.Vector;
import java.util.HashMap;
import javax.swing.JTable;
import javax.swing.JTextField;

import symap.Globals;
import util.ErrorReport;
import util.Utilities;

/***************************************************
 * Results table of Query
 * Has ColumnComparator and sortByColumn
 */
public class TableData implements Serializable {
	private static final long serialVersionUID = 8279185942173639084L;
    private static final int DISPLAY_INTERVAL = 1000; 
    
	/********************************************
	 * Called from TableDataPanel in buildTable and setTable
	 */
	protected static TableData createData(String [] columns, TableData srcData, TableMainPanel parent) {
		TableData tabObj = 	new TableData("createData", parent);
		
		tabObj.arrData = 	new Object[srcData.arrData.length][columns.length];
		tabObj.arrHeaders = new TableDataHeader[columns.length];
		
		for(int x=0; x<columns.length; x++) {
			int srcColIdx = srcData.getColHeadIdx(columns[x]);
			
			tabObj.arrHeaders[x] = srcData.arrHeaders[srcColIdx];
			
			for(int y=0; y<srcData.arrData.length; y++) 
				tabObj.arrData[y][x] = srcData.arrData[y][srcColIdx];
		}
		return tabObj;
	}
    /********************************************
     * Called from TableDataPanel.setTable, which is called on Build and Change Columns
     * Puts the selected columns in the order of the srcTable
     */
	protected static String [] orderColumns(JTable srcTable, String [] selCols) {
		String [] retVal = new String[selCols.length];
		
		Vector <String> columns = new Vector<String> ();
		for (int x=selCols.length-1; x>=0; x--) columns.add(selCols[x]);
		
		int targetIndex = 0;
		for (int x=0; x<srcTable.getColumnCount(); x++) {
			String colName = srcTable.getColumnName(x);
			
			int colIdx = columns.indexOf(colName);
			
			if(colIdx >= 0) {
				retVal[targetIndex] = colName;
				targetIndex++;
				columns.remove(colIdx);
			}
		}
		while (columns.size() > 0) {
			retVal[targetIndex] = columns.get(columns.size()-1);
			columns.remove(columns.size()-1);
			targetIndex++;
		}
		return retVal;
	}
	/****************************************************************
	 * Arrange columns:	
	 * Group all gene columns with same second part, e.g. Gene#.
	 * Group Hit columns, then rest of General
	 */
	protected static String [] arrangeColumns(String [] selColList, boolean isSingle, AnnoData theAnno) {
	try {
		// Group selected columns into sp, hit or general
		final String HIT = Q.hitPrefix, GEN = "Gen"; 
		
		HashMap <String, ArrCol> selColMap = new HashMap <String, ArrCol> ();
		selColMap.put(HIT, new ArrCol());
		selColMap.put(GEN, new ArrCol());
		
		TreeSet <String> spSet = theAnno.getSpeciesAbbrList();		    // abbrev for selected set
		for (String sp : spSet) selColMap.put(sp, new ArrCol());
		
		for (String col : selColList) {
			String prefix="", root="";
			if (col.contains("\n")) {
				String [] tok = col.split("\n");
				prefix = tok[0]; root = tok[1];
			}
			else root = col;
			
			if (!prefix.isEmpty() && spSet.contains(prefix)) {
				selColMap.get(prefix).cols.put(root, false);	// prefix removed
			}
			else if (root.equals(Q.hitCol) || prefix.equals(HIT)) {
				selColMap.get(HIT).cols.put(col, false);		// may have "\n"
			}
			else {
				selColMap.get(GEN).cols.put(col, false);       // may have "\n"
			}
		}
		
	// Build return array from selColMap	
		String [] retColList = new String[selColList.length];
		int ix=0;
		retColList[ix++] = Q.rowCol;
		
		// 1. species 1st; add static gene set in Field order but all selected species together
		String [] spFcolList  = TableColumns.getSpeciesColHead(isSingle); 	// static gene columns 
		
		for (String col : spFcolList) {
			for (String sp : spSet) {
				ArrCol spColObj = selColMap.get(sp);
				if (spColObj.cols.containsKey(col)) {
					retColList[ix++] = sp + "\n" + col;		// prefix put back on
					spColObj.cols.put(col, true); 	
				}
			}
		}
		
		// 2. put in GFF species column
		for (int i=0; i<2; i++) { // 1st ID, 2nd desc, product (1. Only if these not in order. 2. Exact match)
			String [] kcolList = (i==0) ? AnnoData.key1 : AnnoData.key2;
			
			for (String sp : spSet) { // by species order first
				ArrCol spColObj = selColMap.get(sp);
				
				for (String kcol : kcolList) {
					if (spColObj.cols.containsKey(kcol)) {
						retColList[ix++] = sp + "\n" + kcol;
						spColObj.cols.put(kcol, true);
					}
				}
			}
		}
		// rest of GFF species columns in order found
		for (String sp1 : spSet) {
			ArrCol spColObj1 = selColMap.get(sp1);
			for (String scol : spColObj1.cols.keySet()) {
				if (spColObj1.cols.get(scol)) continue; 			// already added
				
				retColList[ix++] = sp1 + "\n" + scol;
				spColObj1.cols.put(scol, true);
				
				for (String sp2 : spSet) {						// see if other species have same column
					if (sp1.equals(sp2)) continue;
					
					ArrCol spColObj2 = selColMap.get(sp2);
					if (spColObj2.cols.containsKey(scol) && !spColObj2.cols.get(scol)) { 
						retColList[ix++] = sp2 + "\n" + scol;
						spColObj2.cols.put(scol, true);
					}	
				}
			}
		}
	
		// 3. hit columns in Fields order
		String [] genFcolList = TableColumns.getGeneralColHead(); 	// e.g. Block, Grp#, Hit#, Hit %id, etc
		
		ArrCol hitColObj = selColMap.get(HIT);
		for (String col : genFcolList) {
			if (col.startsWith(Q.hitPrefix))
				if (hitColObj.cols.containsKey(col)) retColList[ix++] = col;
		}
		
		// 4. general in Fields order
		ArrCol genColObj = selColMap.get(GEN);
		for (String col : genFcolList) {
			if (!col.equals(Q.rowCol) && !col.startsWith(Q.hitPrefix)) 
				if (genColObj.cols.containsKey(col)) retColList[ix++] = col;
		}
	
		if (ix==selColList.length) return retColList;
		
		Globals.tprt("Error Length " + ix + " " + selColList.length);
		for (String s : retColList) if (s!=null) Globals.tprt(s.replace("\n", " "));
		return selColList;  // problem with building new list
	} 
	catch (Exception e) {ErrorReport.print(e,"Arrange columns"); return selColList;}
	}
	static private class ArrCol { // selColMap.key = SP, HIT, GEN 
		private HashMap <String, Boolean> cols = new HashMap <String, Boolean> (); // Column name, true if gene put in colMap
	}
	/*****************************************************************/
	/***************************************
	 * XXX Constructors
	 */
    protected TableData(String tblID, TableMainPanel parent) { // called from TableDataPanel.buildTable
    	this.tblId = tblID;
    	vData = 	new Vector<Vector<Object>>();
    	vHeaders = 	new Vector<TableDataHeader>();
    	tPanel = parent;
    }
   
    /********************************************************/
    protected void sortMasterList(String columnName) {
    	tPanel.sortMasterColumn(columnName);
    }

    protected void setColumnHeaders( String [] species, String [] annoKeys, boolean isSingle) {
        vHeaders.clear();
     	
        // General
        String [] genColNames = TableColumns.getGeneralColHead();
        Class <?> [] genColType =  TableColumns.getGeneralColType();
        int genColCnt = TableColumns.getGenColumnCount(isSingle); 
        
    	for(int x=0; x<genColCnt; x++)
    		addColumnHeader(genColNames[x], genColType[x]);
    	
    	// Loc headers and type
    	String [] spColNames =	  TableColumns.getSpeciesColHead(isSingle);
    	Class <?> [] spColType =  TableColumns.getSpeciesColType(isSingle);
    	int spColCnt = TableColumns.getSpColumnCount(isSingle);	// less columns for single
    	
    	for(int x=0; x < species.length; x++) {
    		String sp = species[x]+ Q.delim ;
    		for (int c=0; c< spColCnt; c++)
    			addColumnHeader(sp + spColNames[c], spColType[c]);
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
    
    protected int getColHeadIdx(String columnName) {
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
    protected void addRowsWithProgress(Vector <DBdata> rowsFromDB, JTextField progress) {
    	try {
    		boolean cancelled = false;
  		
    		for (DBdata dd : rowsFromDB) {
    			if (cancelled) {
    				System.err.println("Cancel adding rows to table");
    				return;
    			}
    			
    			Vector <Object> row = dd.formatRowForTbl();
    			vData.add(row);
    			
    			if (progress != null && (vData.size() % DISPLAY_INTERVAL == 0)) {
    				if(progress.getText().equals("Cancelled")) cancelled = true;
    				else progress.setText("Displaying " + vData.size() + " rows");
    			}
    		}
    		progress.setText("");  	
    	} 
    	catch (Exception e) {ErrorReport.print(e, "add Rows With Progress");}
    }
    
 // it was using iteration and copyInfo; copyInfo would not work with the self, which I never figured out why; CAS575
    protected void finalizeX() { // X so not deprecated
    	int ncol = vHeaders.size();
    	arrHeaders = new TableDataHeader[ncol];
  
    	for (int i=0; i<ncol; i++) {	
    	    arrHeaders[i] = vHeaders.get(i);
    	}
    	vHeaders.clear();

    	arrData = new Object[vData.size()][];
    	for (int i=0; i< vData.size(); i++) {
    		arrData[i] = new Object[ncol];
    		Vector <Object> row = vData.get(i);
    		
    		for (int j=0; j< arrHeaders.length; j++) {
    			if (j>=row.size()) break;				// for isSelf
    			arrData[i][j] = row.get(j);
    		}
    	}
    	vData.clear();
    }

    protected Object getValueAt(int row, int column) {
    	try {
    		return arrData[row][column];
    	}
    	catch(Exception e) {ErrorReport.print(e, "get value at row " + row + " col " + column); return null;}
    }
    protected String getColumnName(int column) {
        try {
        	if (column==-1) return "";
            return arrHeaders[column].getColumnName();
        } catch(Exception e) {ErrorReport.print(e, "get column name at " + column); return null;}
    }
    protected Class<?> getColumnType(int column) {
    	try {
    		return arrHeaders[column].getColumnClass();
    	}
    	catch(Exception e) {ErrorReport.print(e, "get column type at " + column);return null;}
    }
    protected boolean isAscending(int column) {
    	try {
    		if (column==-1) return true;
    		return arrHeaders[column].isAscending();
    	}
    	catch(Exception e) {ErrorReport.print(e, "sort ascending");return false;}
    }

    protected int getNumColumns() {
		if(arrHeaders!=null) return arrHeaders.length;
		if (vHeaders!=null) return vHeaders.size(); 
		return 0;
    }

    protected int getNumRows() {
		if (arrData!=null) return arrData.length;
		if (vData!=null)	return vData.size();
		return 0;
    }
    protected void clear() {
    	arrHeaders = null;
    	if(arrData != null) {
    		for(int x=0; x<arrData.length; x++) arrData[x] = null;
    		arrData = null;
    	}
    	vHeaders.clear();
    	for(int x=0; x<vData.size(); x++) vData.get(x).clear();
    	vData.clear();
    }
    
    protected void sortByColumn(int column, boolean ascending) {
		if (column == -1) return; 
		arrHeaders[column].setAscending(ascending);
		sortByColumn(column);
    }
    
    protected void sortByColumn(final int column) {
		if (column == -1) return; 
		try {
        	tPanel.setCursor(new Cursor(Cursor.WAIT_CURSOR));
        	Arrays.sort(arrData, new ColumnComparator(column)); // error on sort if change tabs; Comparison method violates its general contract!
        	
        	//Always keep rows in same order
        	for(int x=0; x<arrData.length; x++) {
        		arrData[x][0] = x+1;
        	}
        	arrHeaders[column].flipAscending();
        	tPanel.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
		}
		catch (Exception e) {} // With sort error, seems to work okay anyway {ErrorReport.print(e, "Sort by column");}
    }
    // XXX
    private class ColumnComparator implements Comparator<Object []> {
    	protected ColumnComparator(int column) {
    		nColumn = column;
    	}
    	public int compare(Object [] o1, Object [] o2) { 
    	try {
    		if (nColumn == -1) return 0;
    		
    		String colHeader = arrHeaders[nColumn].getColumnName();
 		
			if (colHeader.equals(Q.rowCol)) {
				return 0;
			}
			if(o1[nColumn] == null || o2[nColumn] == null) { // DBdata checks, there should be no null
				System.out.println(arrHeaders[nColumn].getColumnName() + " Incorrect null value, not expected for sort");
				return 0;
			}
		
			int retval = 0;
		
			if (colHeader.endsWith(Q.gStrandCol)) { // was separate since strand can be '-', and empty was '-', but now '.'
				retval = ((String)o1[nColumn]).compareTo((String)o2[nColumn]);
			}
			else if (o1[nColumn] instanceof String && ((String)o1[nColumn]).equals(Q.empty) &&
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
			else if (colHeader.equals(Q.grpCol)) { // sort on grp# instead of sz; is Sz-Grp (no chr)
				String [] vals1 = ((String)o1[nColumn]).split(Q.GROUP); 
				String [] vals2 = ((String)o2[nColumn]).split(Q.GROUP);
				int n = Math.min(vals1.length, vals2.length);
				
				for(int x=n-1; x>0 && retval == 0; x--) {
					boolean valid = true;
					Integer leftVal = null, rightVal = null;
					
					try {
						leftVal = Integer.parseInt(vals1[x]); 
						rightVal = Integer.parseInt(vals2[x]);      
					}
					catch(Exception e) {valid = false; Globals.prt("not valid " + vals1[x] + " " + vals2[x]);} 
					
					if (valid) retval = leftVal.compareTo(rightVal);
					else       retval = vals1[x].compareTo(vals2[x]);
				}
			}
			else if (colHeader.endsWith(Q.gNCol)) { // Sorts left to right (chr.genenum.suffix);
				String [] vals1 = ((String)o1[nColumn]).split(Q.SDOT); 
				String [] vals2 = ((String)o2[nColumn]).split(Q.SDOT);
				
				boolean t1 = (vals1[vals1.length-1].equals(Q.pseudo));
				boolean t2 = (vals2[vals2.length-1].equals(Q.pseudo));
				if (t1  && !t2) return 1;
				if (!t1 &&  t2) return -1;
				
				int n = Math.min(vals1.length, vals2.length);
				
				for(int x=0; x<n && retval==0; x++) {
					boolean valid = true;
					Integer leftVal = null, rightVal = null;
					
					try {
						leftVal = Integer.parseInt(vals1[x]); 
						rightVal = Integer.parseInt(vals2[x]);      
					}
					catch(Exception e) {valid = false;} // char for gene#
					
					if (valid) retval = leftVal.compareTo(rightVal); // integers
					else  retval = vals1[x].compareTo(vals2[x]);// geneNum suffix a,b....
				}
			}
			// sorts left to right; chr.chr.sz.num
			else if (colHeader.equals(Q.blockCol) || colHeader.equals(Q.cosetCol)) {
				String [] vals1 = ((String)o1[nColumn]).split(Q.SDOT); 
				String [] vals2 = ((String)o2[nColumn]).split(Q.SDOT);
				int n = Math.min(vals1.length, vals2.length);
			
				for(int x=0; x<n && retval == 0; x++) {
					boolean valid = true;
					Integer leftVal = null, rightVal = null;
					
					try {
						leftVal = Integer.parseInt(vals1[x]); 
						rightVal = Integer.parseInt(vals2[x]);      
					}
					catch(Exception e) {valid = false;} 
					
					if (valid) retval = leftVal.compareTo(rightVal);
					else       retval = vals1[x].compareTo(vals2[x]);
				}
			}
			else if (colHeader.equals(Q.hitSt)) {// TableSort converts signs x/x; but its x/x here;
				String c1 = (String)o1[nColumn];
				String c2 = (String)o2[nColumn];
				if (c1.equals("+/+") || c1.equals("-/-")) c1 = "=="; else c1 = "!=";
				if (c2.equals("+/+") || c2.equals("-/-")) c2 = "=="; else c2 = "!=";
				retval = c1.compareTo(c2);
			}
			else if (arrHeaders[nColumn].getColumnClass() == String.class) {
				boolean bIsIntCompare = false; // Chr is string but can have integer values
				Integer val1 = -1, val2 = -1;  // Integer so can use compareTo below
				
				if (arrHeaders[nColumn].getColumnName().contains(Q.chrCol)) {
					bIsIntCompare = true;
					
					try {
						val1 = Integer.parseInt(((String)o1[nColumn]));
						val2 = Integer.parseInt(((String)o2[nColumn]));
					}
					catch(Exception e) {bIsIntCompare = false;}
				}
				
				if (bIsIntCompare || o1[nColumn] instanceof Integer)
					retval = val1.compareTo(val2); 
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
			
			if (retval==0) return 0; 
			if(arrHeaders[nColumn].isAscending())	return retval;
			else									return retval * -1;
    	}
    	catch (Exception e) {
    		String col = (nColumn>0 && nColumn<arrHeaders.length) ? arrHeaders[nColumn].getColumnName() : "";
    		ErrorReport.print(e, "Sorting " + col + " (Column " + nColumn + ")"); return 0;}
    	}
    
		private int nColumn;
    } // end ColumnComparator
   
    /**********************************************************
     * For TableDataPanel methods that needs the following values for both ends of hit
     * chrNum, start, end; note the contains gets both values as it starts with the species name
     * e.g. for 3 species, returns (\n after species, check for '-' for the ignored ones):
     * Hit# 11	 Block - 	Collinear - 	Grp# 1
	 * hsap Chr X			Panx Chr -		musm Chr X
	 * musm Gene# X.520.	Panx Gene# -	hsap Gene# X.23.
	 * Panx Hstart -	musm Hend 100363922	hsap Hstart 5892760	Panx Hend -	musm Hstart 100363026	hsap Hend 5893665
     */
    protected HashMap <String, Object> getRowLocData(int row) { // For Group
		HashMap <String, Object> headVal = new HashMap <String, Object> ();
		for (int i=0; i<arrHeaders.length; i++) {
			String colName = arrHeaders[i].getColumnName();
	
			// endsWith because spAbbr could be column name
			if (colName.endsWith(Q.chrCol)) 		headVal.put(colName, arrData[row][i]);
			else if (colName.endsWith(Q.hStartCol)) headVal.put(colName, arrData[row][i]);
			else if (colName.endsWith(Q.hEndCol)) 	headVal.put(colName, arrData[row][i]);
			else if (colName.endsWith(Q.gNCol)) 	headVal.put(colName, arrData[row][i]);
			else if (colName.equals(Q.blockCol)) 	headVal.put(colName, arrData[row][i]);
			else if (colName.equals(Q.cosetCol)) 	headVal.put(colName, arrData[row][i]); 
			else if (colName.equals(Q.hitCol)) 		headVal.put(colName, arrData[row][i]); 
			else if (colName.equals(Q.grpCol)) 		headVal.put(colName, arrData[row][i]); 
		}
		return headVal;
    }
    // For 2D 3track, Report; Align
    protected void getRowLocData(int row, Vector <String> colNames, Vector <Object> colVals) {
  		for (int i=0; i<arrHeaders.length; i++) {
  			String colName = arrHeaders[i].getColumnName();
  			// Species
  			if (colName.endsWith(Q.chrCol)) 		{colNames.add(colName); colVals.add(arrData[row][i]);}
  			else if (colName.endsWith(Q.hStartCol)) {colNames.add(colName); colVals.add(arrData[row][i]);}
  			else if (colName.endsWith(Q.hEndCol)) 	{colNames.add(colName); colVals.add(arrData[row][i]);}
  			else if (colName.endsWith(Q.gNCol)) 	{colNames.add(colName); colVals.add(arrData[row][i]);}
  			
  			// general
  			else if (colName.endsWith(Q.hitSt))		{colNames.add(colName); colVals.add(arrData[row][i]);}
  			else if (colName.equals(Q.blockCol)) 	{colNames.add(colName); colVals.add(arrData[row][i]);}
  			else if (colName.equals(Q.cosetCol)) 		{colNames.add(colName); colVals.add(arrData[row][i]);}
  			else if (colName.equals(Q.hitCol)) 		{colNames.add(colName); colVals.add(arrData[row][i]);}
  			else if (colName.equals(Q.grpCol)) 		{colNames.add(colName); colVals.add(arrData[row][i]);}
  		}
     }
    /* For show row */
    protected String getRowData(int row) {
    	String line="";
		for (int i=0; i<arrHeaders.length; i++) {
			String colName = arrHeaders[i].getColumnName().replace(Q.delim, Q.dash);
			if (colName.endsWith("start") || colName.endsWith("end") || colName.endsWith("len") 
				|| colName.equals(Q.hitCol))
			{
				int dd = Utilities.getInt(arrData[row][i].toString());
				if (dd == -1) line += String.format("%-15s %s\n", colName, arrData[row][i].toString());
				else line += String.format("%-15s %,d\n", colName, dd);
			}
			else if (!colName.endsWith(Q.All_Anno)) {// generally in other columns, but not always..
				String all = arrData[row][i].toString();
				String [] tok = all.split(";");
				String prt = "";
				String head = colName;
				for (String t : tok) {
					if (prt.isEmpty()) prt = t;
					else if (prt.length() + t.length()< 60) prt += "; " + t;
					else {
						line += String.format("%-15s %s\n", head, prt);
						head = " ";
						prt = t;
					}
				}
				if (!prt.isEmpty()) line += String.format("%-15s %s\n", head, prt);
			}
			else line += String.format("%-15s %s\n", colName, arrData[row][i].toString());
		}
		return line;
    }
    // For UtilReport, get set of All_Anno
    protected HashMap <String, String> getAllAnno(int row) {
    	HashMap <String, String> rowMap = new HashMap <String, String> ();
		for (int i=0; i<arrHeaders.length; i++) {
			String colName = arrHeaders[i].getColumnName();
			if (colName.endsWith(Q.All_Anno)) 
				rowMap.put(colName, (String) arrData[row][i]);
		}
		return rowMap;
    }
    // For TableShow.align to get gene coordinates
    protected Object [] getGeneCoords(String spAbbr, int row) {
    	Object [] coords = {-1, -1};
    	
    	for (int i=0; i<arrHeaders.length; i++) {
  			String colName = arrHeaders[i].getColumnName().replace("\n", " ");
  			
  			if (colName.startsWith(spAbbr)) {
  				if (colName.endsWith(Q.gStartCol)) coords[0] = arrData[row][i];
  				else if (colName.endsWith(Q.gEndCol)) coords[1] = arrData[row][i];
  			}	
  		}
    	return coords;
    }
    protected String tblId="";
    
    //Static data structures
    private TableDataHeader [] arrHeaders = null;
    private Object [][] arrData = null;
    
    //Dynamic data structures
    private Vector<TableDataHeader> vHeaders = null;
    private Vector<Vector<Object>> vData = null; 
    private TableMainPanel tPanel = null;
}
