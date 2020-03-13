package symapQuery;
/***************************************************
 * Results table of Query
 */
import java.awt.Cursor;
import java.io.BufferedReader;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Vector;
import java.math.BigDecimal;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

import java.sql.ResultSet;

import javax.swing.JTable;
import javax.swing.JTextField;

import util.ErrorReport;
import util.Utilities;
import backend.Utils;

public class TableData implements Serializable {
	private static final long serialVersionUID = 8279185942173639084L;
    	private static final long DISPLAY_INTERVAL = 1000; 
    	
    	// Statistics structures
    	public HashMap<String,Integer> proj2regions; 
    	public HashMap<String,Integer> proj2hits;
    	public HashMap<String,Integer> proj2annot;
    	public HashMap<String,Integer> proj2orphs;
    	public HashMap<Integer,HashMap<String,Integer>> grp2projcounts;
    	
    	public static TableData createModel(String [] columns, TableData source, ListDataPanel parent) {
    		TableData retVal = new TableData(parent);
    		
    		retVal.arrData = new Object[source.arrData.length][columns.length];
    		retVal.arrHeaders = new TableDataHeader[columns.length];
    		
    		for(int x=0; x<columns.length; x++) {
    			int sourceColumnIdx = source.getColumnHeaderIndex(columns[x]);
    			retVal.arrHeaders[x] = source.arrHeaders[sourceColumnIdx];
				for(int y=0; y<source.arrData.length; y++) {
					retVal.arrData[y][x] = source.arrData[y][sourceColumnIdx];
				}
    		}
    		retVal.strCacheName = source.strCacheName;
    		retVal.bReadOnly = source.bReadOnly;
    		
    		return retVal;
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
    	
    public TableData(ListDataPanel parent) {
    		vData = new Vector<Vector<Object>>();
    		vHeaders = new Vector<TableDataHeader>();
    		theParent = parent;
    }

    public TableData(String cacheFileName, ListDataPanel parent) {
    		this(parent);
     	strCacheName = cacheFileName;
    }
        
    public void sortMasterList(String columnName) {
    		theParent.sortMasterColumn(columnName);
    }

    public String getCacheFileName() { return strCacheName; }
    public void setCacheFileName(String fileName) { strCacheName = fileName; } 

    public void setColumnHeaders(String [] columnNames, Class<?> [] columnTypes) {
        	vHeaders.clear();

        	addColumnHeader("Row", Long.class);
        	for(int x=0; x<columnNames.length; x++)
        		addColumnHeader(columnNames[x], columnTypes[x]);
    }
        
    public void addColumnHeader(String columnName, Class<?> type) {
        	try {
        		if(bReadOnly) throw (new Exception());
        		vHeaders.add(new TableDataHeader(columnName, type));
        	} 
        	catch(Exception e) {
        		ErrorReport.print(e, " Add column header " + columnName);
        	}
    }
        
    public void insertColumnHeader(int pos, String columnName, Class<?> type) {
        	try {
        		if(bReadOnly) throw (new Exception());
        		vHeaders.insertElementAt(new TableDataHeader(columnName, type), pos);
        	} 
        	catch(Exception e) {
        		ErrorReport.print(e, " insert column header " + columnName);
        	}
    }
        
    public int getColumnHeaderIndex(String columnName) {
        	int retVal = -1, x=0;
        	
        	if(bReadOnly) {
        		for(;x<arrHeaders.length && retVal<0;x++) {
        			if(arrHeaders[x].getColumnName().equals(columnName))
        				retVal = x;
        		}
        	}
        	else {
        		Iterator<TableDataHeader> iter = vHeaders.iterator();
        		for(;iter.hasNext() && retVal<0; x++) 
        			if(iter.next().getColumnName().equals(columnName))
        				retVal = x;
        	}
        	if (retVal==-1) System.out.println("no column " + columnName);
        	return retVal;
    }
        
    public void addSpeciesFields(String [] species) {
        	for(int x=0; x < species.length; x++) {
        		addColumnHeader(species[x]+"\nChr", String.class);
        		addColumnHeader(species[x]+"\nStart", Integer.class);
        		addColumnHeader(species[x]+"\nEnd", Integer.class);
        	}

        	Iterator<Vector<Object>> iter = vData.iterator();
        	int sp1Pos = getColumnHeaderIndex("Species 1");
        	int sp2Pos = getColumnHeaderIndex("Species 2");
        	while(iter.hasNext()) {
        		Vector<Object> temp = iter.next();
        		for(int x=0; x<species.length; x++) {
        			if(species[x].equals(temp.get(sp1Pos))) {
        				temp.add(temp.get(getColumnHeaderIndex("Chr 1")));
        				temp.add(temp.get(getColumnHeaderIndex("Start 1")));
        				temp.add(temp.get(getColumnHeaderIndex("End 1")));
        			} 
        			else if(species[x].equals(temp.get(sp2Pos))) {
        				temp.add(temp.get(getColumnHeaderIndex("Chr 2")));
        				temp.add(temp.get(getColumnHeaderIndex("Start 2")));
        				temp.add(temp.get(getColumnHeaderIndex("End 2")));
        			}
        			else {
        				temp.add(null);
        				temp.add(null);
        				temp.add(null);
        			}
        		}
        	}
    }
        //Handles an imported table
    public void addRowsWithProgress(BufferedReader in, FieldData theFields, JTextField progress, String [] species) {
        	try {
        		addSpeciesFields(species);
        		String line = in.readLine();
        		String [] columnNames = line.split(",");
        		for(int x=0; x<columnNames.length; x++) {
        			columnNames[x] = columnNames[x].substring(1, columnNames[x].length() - 1);
        		}
        		
        		int counter = 0;
        		while((line = in.readLine()) != null) {
        			counter++;
        			if(counter % 123 == 0)
        				progress.setText("Loading row: " + counter);
        			String [] values = parseLine(line);
        			Vector<Object> row = new Vector<Object> ();
        			
        			for(int x=0; x<values.length; x++) {
        				if(vHeaders.get(x).getColumnClass() == Integer.class) { // CAS503 added {}
        					if(values[x].length() > 0) 
        						row.add(new Integer(Integer.parseInt(values[x])));
        					else 
        						row.add(null);
        				}
        				else if(vHeaders.get(x).getColumnClass() == Long.class) {
        					if(values[x].length() > 0) 
            					row.add(new Long(Long.parseLong(values[x])));
        					else 
        						row.add(null);
        				}
        				else if(vHeaders.get(x).getColumnClass() == Double.class) {
        					if(values[x].length() > 0) 
            					row.add(new Double(Double.parseDouble(values[x])));
        					else 
        						row.add(null);
        				}
        				else {
        					if(values[x].length() > 0) 
            					row.add(values[x]);
        					else 
        						row.add(null);
        				}
        			}
        			addRow(row);
        		}
        		progress.setText("");
        	}
        	catch(Exception e) {ErrorReport.print(e, "add row with progress");}
    }
        
    // XXX Note that each hit can appear in multiple records depending how many annotations it has. 
    // They are ordered by hit_idx so the identical hits should be consecutive.
    // The code groups them into a single record, combining the annotations.
    public void addRowsWithProgress(ResultSet rset, FieldData theFields, JTextField progress) {
        	try {
               	// Collect the included/excluded group indices.
        		//
        		// Note that if they chose a particular chromosome from the dropdown list, that is
        		// enforced in the query, so the hits/annotations we have here already come only from those chromosomes.
        		// The additional constraints we have to now apply come from whether they have the include/exclude
        		// boxes checked, and the "include only" checkbox.
       		
        		rset.setFetchSize(100);
        		
        		LocalQueryPanel lQueryPanel = theParent.theParentFrame.lQueryPanel;
        		
            	TreeSet<Integer> allIncludes = new TreeSet<Integer>(); // for incOnly
            	Vector<TreeSet<Integer>> includes = new Vector<TreeSet<Integer>>();
            	TreeSet<Integer> excludes = new TreeSet<Integer>();
            	
            	boolean incOnly = lQueryPanel.isOnlyIncluded();
           
     // get selected chromosomes for include/exclude species
            	String[] chrs = lQueryPanel.speciesPanel.theChrIdx;
            	
            	for (int i = 0; i < chrs.length; i++)
            	{
            		boolean inc = lQueryPanel.isInclude(i);
            		boolean exc = lQueryPanel.isExclude(i);
            		if (!inc && !exc) continue;
            		
            		TreeSet<Integer> incs = null;
            		if (inc) incs = new TreeSet<Integer>();
            		
            		for(String idxstr : chrs[i].split(","))
            		{
            			if(idxstr != null && idxstr.length() > 0) { 
	            			int idx = Integer.parseInt(idxstr);
	            			if (inc){
	            				incs.add(idx);
	            				allIncludes.add(idx);
	            			}
	            			if (exc) {
	            				excludes.add(idx);
	            			}
            			}
            		}
            		if (inc) {
            			includes.add(incs);
            		}       				
            	} // end going through chromosomes
            	
            	// special case - only show orphans of one species, so there won't be any hits or groups
            	boolean orphOnly = lQueryPanel.isOrphan();
            	
        		String [] symbols = theFields.getDisplayFieldSymbols();

        		Vector<Vector<Object>> rowDatas = new Vector<Vector<Object>> (); // The table data for a row
        		boolean firstRow = true;
        		boolean cancelled = false;
        		
      /** row position the species+keyword combos **/
        		HashMap<String,HashMap<String,Integer>> kw2pos = new HashMap<String,HashMap<String,Integer>>();
        		HashMap<String,Integer> spec2gfpos = new HashMap<String,Integer>();
        		int allAnnoPos = -1, grpPos = -1, bnumPos = -1;
        		
        		for (int pos = 0; pos < theFields.getNumFields(); pos++)
        		{
        			String name = theFields.getFieldNameAt(pos); // .toLowerCase();
        			if (!name.contains("\n"))
        			{
        				if (name.equals(Q.annotation)) 		allAnnoPos = pos;
        				else if (name.equals(Q.pgenef))		grpPos = pos;
        				else if (name.equals(Q.blocknum))	bnumPos = pos;
        				continue;
        			}
    				else 
    				{
    					if (name.endsWith(Q.nRGN))    				
	    				{
	    					String spname = name.replace("\n" + Q.nRGN, "");
	    					spec2gfpos.put(spname,pos);
	    				}
    					else
    					{
		        			String[] fields = name.split("\\n");
		        			String species = fields[0].toLowerCase();
		        			String kw = fields[1];
		     
		        			if (!kw2pos.containsKey(species)) {
		        				kw2pos.put(species,new HashMap<String,Integer>());
		        			}
		        			kw2pos.get(species).put(kw, pos);
    					}
    				}
        		}
        		// init stats holders
        		proj2regions = new HashMap<String,Integer>();
        		proj2hits = new HashMap<String,Integer>();
        		proj2annot = new HashMap<String,Integer>();
        		proj2orphs = new HashMap<String,Integer>();
        		grp2projcounts = new HashMap<Integer,HashMap<String,Integer>>();
        		
        		HashMap<Integer,Integer> hitIdx2Grp = new HashMap<Integer,Integer>();
        		HashMap<Integer,Integer> grpSizes = new HashMap<Integer,Integer>();   // PgeneF and PgFSize
        		HashMap<Integer,Integer> annotMap = new HashMap<Integer,Integer>();
        		
        	/** Compute PgeneF **/
        		computePgeneF(hitIdx2Grp, annotMap, grpSizes, rset, includes, excludes, allIncludes, incOnly, orphOnly, progress);
        		
        		hitIdx2Grp.put(0, 0); // for the annots that didn't go into a group
        		grpSizes.put(0, 0);
        		int prev_hidx = -1;
        		rset.beforeFirst();
        	
        		String pname1 = "oops", pname2 = "oops";
        		Utilities.init_rs2col();
        	
        /** parse resultset **/
        		while(rset.next() && !cancelled) {
        			boolean isAnnot =        rset.getBoolean(Utilities.rs2col(Q.isAnnot,rset));
        			int hidx =               rset.getInt(Utilities.rs2col(Q.HITIDX,rset));
        			int gidx1 = (!isAnnot ?  rset.getInt(Utilities.rs2col(Q.GRPIDX1,rset)) : 
        				                     rset.getInt(Utilities.rs2col(Q.AGIDX,rset))); // actually either would work
        			int agidx =              rset.getInt(Utilities.rs2col(Q.AGIDX,rset));
        			int aidx =  (isAnnot ?   rset.getInt(Utilities.rs2col(Q.AIDX,rset)) : 0);
        			int gidx2 = (!isAnnot ?  rset.getInt(Utilities.rs2col(Q.GRPIDX2,rset)) : 0);
        			String sp1 =             rset.getString(Utilities.rs2col(Q.SPECNAME1,rset)).toLowerCase();
        			String sp2 = (!isAnnot ? rset.getString(Utilities.rs2col(Q.SPECNAME2,rset)).toLowerCase() : "");
        			String allAnno =         rset.getString(Utilities.rs2col(Q.ANAME,rset));
        			
        			if (isAnnot) {
        				assert(gidx1 > 0);
        				assert(aidx >0);
        			}
        			
        			if (!isAnnot) {
        				if (!hitIdx2Grp.containsKey(hidx)) continue;
        			}
        			else {
        				if (!annotMap.containsKey(aidx)) continue; // It's not an orphan.
        			}
        			
        			if (isAnnot || (prev_hidx != -1 && hidx != prev_hidx))
        			{
        				if (rowDatas.size() > 0)
        				{
        					// We're hitting this hit_idx for the first time, so score a hit
        					if (!isAnnot)
        					{
            					assert(!sp1.equals(sp2));
        						counterInc(proj2hits,sp1,1);
        						counterInc(proj2hits,sp2,1);
        					}
         				addRows(rowDatas, kw2pos);
        					rowDatas.clear(); 
        				}
        			}
        			if (!isAnnot) // store the previous proj names 
        			{
        				pname1 = rset.getString(Utilities.rs2col(Q.SPECNAME1,rset)).toLowerCase(); 
        				pname2 = rset.getString(Utilities.rs2col(Q.SPECNAME2,rset)).toLowerCase();
        			}
        			
        			prev_hidx = hidx;
        			
    				String species = null;
        			if (agidx > 0)
        			{
        				if (agidx == gidx1)      species = sp1;
        				else if (agidx == gidx2) species = sp2;
        				else System.out.println("Annotation doesn't go with either group; set to null");       	
        			}
        			
        			// Enter data in the order corresponding to the columns
        			Vector<Object> rowData = new Vector<Object>();
        			rowData.clear();
        			rowData.setSize(symbols.length+1);
					
        			for(int x=0; x<symbols.length; x++) {
    					Object tempVal;
    					String sym = symbols[x];
    					if (isAnnot)  // Location (Hit Loc or Gene Loc)
    					{
    						if (sym.equals(Q.GRP1START))    sym=Q.ASTART;
    						else if (sym.equals(Q.GRP1END)) sym=Q.AEND;
 
    					}    					
    					tempVal = rset.getObject(Utilities.rs2col(sym,rset));

    					if(firstRow && tempVal != null) {
    						vHeaders.get(x+1).setColumnClass(tempVal.getClass());
    					}
	        			rowData.set(x+1, tempVal);
        			}
        			
        			rowData.set(allAnnoPos+1,new String(""));
        			if (species != null)
        			{
        				if (kw2pos.containsKey(species) && kw2pos.get(species).containsKey(Q.All_Anno))
        				{
        					int pos = kw2pos.get(species).get(Q.All_Anno); // all_anno
        					rowData.set(pos+1, allAnno);
        				}
        			}
        			int grp = 0;
        			if (!isAnnot) grp = hitIdx2Grp.get(hidx);
        			else          grp = annotMap.get(aidx);
        			
        			if (!orphOnly) {
	        			int grpsize = grpSizes.get(grp);
	        			rowData.set(grpPos+1, grp);			// add PgeneF
	        			rowData.set(grpPos+2, grpsize);      // add PgFsize
        			}
        			if (grp2projcounts.containsKey(grp))
        			{
        				int[] cnts = new int[spec2gfpos.keySet().size()];
        				int i = 0;
        				for (String spname : spec2gfpos.keySet())
        				{
    						int pos = spec2gfpos.get(spname);
        					if (grp2projcounts.get(grp).containsKey(spname))
        					{
        						int cnt = grp2projcounts.get(grp).get(spname);
        						rowData.set(pos+1, cnt);
        						cnts[i] = cnt; i++;
        					}
        					else
        					{
        						rowData.set(pos+1,0);
        						cnts[i] = 0; i++;
        					}
        				}
        			}
        			if (!isAnnot)
        			{
        				int blockNum = rset.getInt(Utilities.rs2col(Q.BNUM,rset));
        				String blocknum = "";
        				if (blockNum > 0)
        				{
        					blocknum =      rset.getString(Utilities.rs2col(Q.GRPNAME1,rset)) 
        							+ "." + rset.getString(Utilities.rs2col(Q.GRPNAME2,rset)) 
        							+ "." + blockNum;
        				}
    					rowData.set(bnumPos+1, blocknum);
        			}
        			rowDatas.add(rowData);
        			firstRow = false;
        				
        			if(progress != null && (vData.size() % DISPLAY_INTERVAL == 0)) {
        				if(progress.getText().equals("Cancelled"))
        					cancelled = true;
        				else
        					progress.setText("Reading " + vData.size() + " rows");
        			}
        		} // end loop through results
        		
        		if (rowDatas.size() > 0) 
        		{
        			addRows(rowDatas, kw2pos); /** Add row to table **/
				counterInc(proj2hits,pname1,1);
				counterInc(proj2hits,pname2,1);
        		}
        		progress.setText("");
            	
        	} catch (Exception e) {
        		ErrorReport.print(e, "add Rows With Progress");
        	}
    }
    private void counterInc(Map<String,Integer> ctr, String key, int inc  )
    {
    		if (inc == 0) return;
		if (!ctr.containsKey(key))
			ctr.put(key, inc);
		else
			ctr.put(key, ctr.get(key)+inc);
    }
    // Call ComputeGeneF.run()
    private void computePgeneF(HashMap<Integer,Integer> out, HashMap<Integer,Integer> annotMap, HashMap<Integer,
    		Integer> sizes, ResultSet rs, Vector<TreeSet<Integer>> includes, TreeSet<Integer> excludes,
    		TreeSet<Integer> allIncludes, boolean incOnly, boolean orphOnly, JTextField progress) {
    	
    		try {
	    		if(progress != null && (vData.size() % DISPLAY_INTERVAL == 0)) {
				if(!progress.getText().equals("Cancelled"))
					progress.setText("Computing putative gene families");
			}
			Utilities.timerStart();
			boolean unAnnot = theParent.theParentFrame.lQueryPanel.isUnannot();
			boolean isClique = theParent.theParentFrame.lQueryPanel.isClique();
	
			ComputePgeneF.run(out,  annotMap, sizes, rs,  includes,  excludes,
		    		allIncludes, incOnly, orphOnly, unAnnot, isClique, progress,
		    		proj2orphs, grp2projcounts, proj2regions, proj2annot);
		 	Utilities.timerEndNotZero();
    		}
    		catch (Exception e) {ErrorReport.print(e, "computePgeneF"); }
    }
    
    
    // This function concatenates the keyword entries for each species from all
    // the given rows (which should come from a single hit_idx). 
    // It discards duplicate keyword values.
    private void addRows(Vector<Vector<Object>> rowDatas, 
    		HashMap<String,HashMap<String,Integer>> kw2pos) 
    {
        	try 
        		{
        		// First, build the bare output row without annot entries
        		Vector<Object> tempRow = new Vector<Object>();
        		tempRow.setSize(rowDatas.firstElement().size());
        		Collections.copy(tempRow,rowDatas.firstElement()); 
        		
            	for (String sp : kw2pos.keySet())
            	{
            		if(kw2pos.get(sp).get(Q.All_Anno) != null) { // all_anno
            			int pos = kw2pos.get(sp).get(Q.All_Anno);
            			tempRow.set(pos+1,null);
            		}
            	}
            	
        		// build a set of values for each keyword to prevent duplicates
            	HashMap<String,HashMap<String,HashSet<String>>> kwvals 
        				= new HashMap<String,HashMap<String,HashSet<String>>>();
        		for (String sp : kw2pos.keySet())
        		{
        			kwvals.put(sp, new HashMap<String,HashSet<String>>());
        			for (String kw : kw2pos.get(sp).keySet()) {
        				kwvals.get(sp).put(kw, new HashSet<String>());
        			}
        		}
        		
            	// Now parse the annot keywords from each row and group them
        		for (Vector<Object> rowData : rowDatas) 
        		{
	        		if(bReadOnly || rowData.size() != vHeaders.size()) throw (new Exception());
	        		parseAnnoIntoColumns(kwvals, rowData, kw2pos);
        		}
        		
        		// Put the annot key values in their places
        		for (String sp : kwvals.keySet())
        		{
        			for (String kw : kwvals.get(sp).keySet())
        			{
        				int pos = kw2pos.get(sp).get(kw);
        				String x = Utils.join(kwvals.get(sp).get(kw),";"); // multiple vals for keyword
        				tempRow.set(pos+1, x);
        			}
        		}
        		tempRow.set(0, new Long(vData.size() + 1));
        		vData.add(tempRow);
        	}
        	catch(Exception e) {ErrorReport.print(e, "add rows ");}
    }
        
    private void parseAnnoIntoColumns(
    		HashMap<String, HashMap<String, HashSet<String>>> kwvals, // Species, <kw, vals> add vals
    		Vector<Object> rowdata, 									 // rowdata contains All_Anno
    		HashMap<String, HashMap<String, Integer>> kw2pos)		 // Species, <kw, pos>
    {
    	try {
    		String species = null, all_annot = null;
		
        	for (String sp : kw2pos.keySet())
        	{
        		if(kw2pos.get(sp).get(Q.All_Anno) != null) {  
	        		int pos = kw2pos.get(sp).get(Q.All_Anno);
	        		if (rowdata.get(pos+1) != null)
	        		{
	        			species = sp;
	        			all_annot = (String) rowdata.get(pos+1);
	        		}
        		}
        	}
        	if (species == null)
        		return; // This happens for the empty "hit" type annotation
    	
    	// Add the all_annot entry
    		kwvals.get(species).get(Q.All_Anno).add(all_annot);
    	
    	// Find keyword:value of attributes     	
		String[] fields = all_annot.split(";");
		for (String field : fields)
		{
			String[] words = field.trim().split("[\\s,=]",2);
			if (words.length == 1) continue;
			
			String key = words[0].trim();
			if (key.endsWith("="))	key = key.substring(0,key.length() - 1);
			
			String val = words[1].trim();
			if (val.startsWith("=")) val = val.substring(1);
			
			if (key.equals("") || val.equals("")) continue;
			
			if (kwvals.get(species).containsKey(key))
				kwvals.get(species).get(key).add(val);
		}
    	 	}
        	catch(Exception e) {ErrorReport.print(e, "parse attribues");}
    }
    public boolean isReadOnly() { return bReadOnly; }
    
    public void addRow(Vector<Object> row) {
    		vData.add(row);
    }

    public void finalize() {
        	arrHeaders = new TableDataHeader[vHeaders.size()];
        	vHeaders.copyInto(arrHeaders);
        	vHeaders.clear();

        	arrData = new Object[vData.size()][];
        	Iterator<Vector<Object>> iter = vData.iterator();
        	int x = 0;
        	Vector<Object> tempV;
        	while(iter.hasNext()) {
        		arrData[x] = new Object[arrHeaders.length];
        		tempV = iter.next();
        		tempV.copyInto(arrData[x]);
        		tempV.clear();
        		x++;
        	}
        	vData.clear();

    		bReadOnly = true;
    }

    public Object getValueAt(int row, int column) {
        	try {
        		if(!bReadOnly) throw (new Exception());
        		return arrData[row][column];
        	}
        	catch(Exception e) {
        		ErrorReport.print(e, "get value at row " + row + " col " + column);
        		return null;
        	}
    }

    public Object [] getRowAt(int row) {
            try {
                if(!bReadOnly) throw (new Exception());
                return arrData[row];
            }
            catch(Exception e) {
            		ErrorReport.print(e, "get row " + row);
                return null;
            }
    }

    public String getColumnName(int column) {
        try {
            if(!bReadOnly) throw (new Exception());
            return arrHeaders[column].getColumnName();
        } catch(Exception e) {
        		ErrorReport.print(e, "get column name at " + column);
            return null;
        }
    }

    public Class<?> getColumnType(int column) {
        	try {
        		if(!bReadOnly) throw (new Exception());
        		return arrHeaders[column].getColumnClass();
        	}
        	catch(Exception e) {
        		ErrorReport.print(e, "get column type at " + column);
        		return null;
        	}
    }
    
    public boolean isAscending(int column) {
        	try {
        		if(!bReadOnly) throw (new Exception());
        		return arrHeaders[column].isAscending();
        	}
        	catch(Exception e) {
        		ErrorReport.print(e, "sort ascending");
        		return false;
        	}
    }

    public int getNumColumns() {
    		if(bReadOnly)
    			return arrHeaders.length;
    		return vHeaders.size();
    }

    public int getNumRows() {
    		if(bReadOnly)
    			return arrData.length;
    		return vData.size();
    }

    public void sortByColumn(int column, boolean ascending) {
    		arrHeaders[column].setAscending(ascending);
    		sortByColumn(column);
    }
    
    public void sortByColumn(final int column) {
        	theParent.setCursor(new Cursor(Cursor.WAIT_CURSOR));
        	Arrays.sort(arrData, new ColumnComparator(column));
        	//Always keep rows in same order
        	for(int x=0; x<arrData.length; x++) {
        		arrData[x][0] = x+1;
        	}
     	arrHeaders[column].flipAscending();
     	theParent.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
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
    
    private class ColumnComparator implements Comparator<Object []> {
        	public ColumnComparator(int column) {
        		nColumn = column;
        	}
    	
		public int compare(Object [] o1, Object [] o2) {
			if (nColumn==-1) return 0; // CAS503
			int retval = 0;
			if(arrHeaders[nColumn].getColumnName().equals("Row")) {
				return 0;
			}
			if(o1[nColumn] == null && o2[nColumn] == null || 
					(o1[nColumn] instanceof String && ((String)o1[nColumn]).length() == 0 &&
							o2[nColumn] instanceof String && ((String)o2[nColumn]).length() == 0)) {
				retval = 0;
			}
			else if(o1[nColumn] == null || (o1[nColumn] instanceof String && ((String)o1[nColumn]).length() == 0)) {
				if(arrHeaders[nColumn].isAscending())
					retval = 1;
				else
					retval = -1;
			}
			else if(o2[nColumn] == null || (o2[nColumn] instanceof String && ((String)o2[nColumn]).length() == 0)) {
				if(arrHeaders[nColumn].isAscending())
					retval = -1;
				else
					retval = 1;
			}
			else {
				if(arrHeaders[nColumn].getColumnClass() == Integer.class) {
					if(o1[nColumn] instanceof String && o2[nColumn] instanceof String) {
						//Block Num case
						String val1 = ((String)o1[nColumn]);
						String val2 = ((String)o2[nColumn]);
						
						if(val1.length() == 0 && val1.length() == 0)
							retval = 0;
						else if(val1.length() > 0 && val2.length() == 0) {
							retval = 1;
						}
						else if(val1.length() == 0 && val2.length() > 0) {
							retval = -1;
						}
						else {
							String [] vals1 = ((String)o1[nColumn]).split("\\."); 
							String [] vals2 = ((String)o2[nColumn]).split("\\.");
							
							for(int x=0; x<vals1.length && x<vals2.length && retval == 0; x++) {
								boolean valid = true;
								
								Integer leftVal = null;
								Integer rightVal = null;
								
								try {
									leftVal = new Integer(Integer.parseInt(vals1[x]));
									rightVal = new Integer(Integer.parseInt(vals2[x]));
								}
								catch(Exception e) {
									valid = false;
								}
								if(valid)
									retval = leftVal.compareTo(rightVal);
								else
									retval = vals1[x].compareTo(vals2[x]);
							}
						}
					}
					else {
						retval = ((Integer)o1[nColumn]).compareTo((Integer)o2[nColumn]);
					}
				}
				else if(arrHeaders[nColumn].getColumnClass() == Long.class) {
					if(o1[nColumn] instanceof String)
						retval = (new Long((String)o1[nColumn])).compareTo(new Long((String)o2[nColumn]));
					else if(o1[nColumn] instanceof Integer)
						retval = ((Integer)o1[nColumn]).compareTo((Integer)o2[nColumn]);
					else
						retval = ((Long)o1[nColumn]).compareTo((Long)o2[nColumn]);
				}
				else if(arrHeaders[nColumn].getColumnClass() == Float.class)
					retval = ((Float)o1[nColumn]).compareTo((Float)o2[nColumn]);
				else if(arrHeaders[nColumn].getColumnClass() == Double.class) 
					retval = ((Double)o1[nColumn]).compareTo((Double)o2[nColumn]);
				else if(arrHeaders[nColumn].getColumnClass() == String.class) {
					//Check if strings are numbers
					boolean bIsIntCompare = true;
					
					int val1 = -1, val2 = -1;
					
					try {
						val1 = Integer.parseInt(((String)o1[nColumn]));
						val2 = Integer.parseInt(((String)o2[nColumn]));
					}
					catch(Exception e) {
						bIsIntCompare = false;
					}
					
					if(bIsIntCompare || o1[nColumn] instanceof Integer)
						retval = new Integer(val1).compareTo(new Integer(val2));
					else
						retval = ((String)o1[nColumn]).compareTo((String)o2[nColumn]);
				}
				else if(arrHeaders[nColumn].getColumnClass() == BigDecimal.class)
					retval = ((BigDecimal)o1[nColumn]).compareTo((BigDecimal)o2[nColumn]);
				else if(arrHeaders[nColumn].getColumnClass() == Boolean.class)
					retval = ((Boolean)o1[nColumn]).compareTo((Boolean)o2[nColumn]);
			}

			if(arrHeaders[nColumn].isAscending())
				return retval;
			else
				return retval * -1;
		}
		
    		private int nColumn;
    } // end ColumnComparator
    
    private static String [] parseLine(String line) {
        	Vector<String> vals = new Vector<String> ();
        	boolean inValue = false;
        	String val = "";
        	for(int x=0; x<line.length(); x++) {
        		if(line.charAt(x) == '\'') {
        			if(inValue) {
        				vals.add(val);
        				val = "";
        				inValue = false;
        			}
        			else
        				inValue = true;
        		}
        		else if(inValue) {
        			val += line.charAt(x);
        		}
        	}
    	
        	String [] retVal = new String[vals.size()];
        	for(int x=0; x<retVal.length; x++)
        		retVal[x] = vals.get(x);
    	
    		vals.clear();
    	
    		return retVal;
    }
    
    //attributes
    private boolean bReadOnly = false;
    private String strCacheName = null;
    //Static data structures
    private TableDataHeader [] arrHeaders = null;
    private Object [][] arrData = null;
    //Dynamic data structures
    private Vector<TableDataHeader> vHeaders = null;
    private Vector<Vector<Object>> vData = null; 
    private ListDataPanel theParent = null;
}
