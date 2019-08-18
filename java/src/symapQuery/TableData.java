package symapQuery;

import java.awt.Cursor;
import java.io.BufferedReader;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Vector;
import java.util.Collection;
import java.math.BigDecimal;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

import java.sql.ResultSet;

import javax.swing.JTable;
import javax.swing.JTextField;

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
        		e.printStackTrace();
        	}
        }
        
        public void insertColumnHeader(int pos, String columnName, Class<?> type) {
        	try {
        		if(bReadOnly) throw (new Exception());
        		vHeaders.insertElementAt(new TableDataHeader(columnName, type), pos);
        	} 
        	catch(Exception e) {
        		e.printStackTrace();
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
        			//System.out.println("Column: " + columnNames[x]);
        		}
        		
        		int counter = 0;
        		while((line = in.readLine()) != null) {
        			counter++;
        			if(counter % 123 == 0)
        				progress.setText("Loading row: " + counter);
        			String [] values = parseLine(line);
        			Vector<Object> row = new Vector<Object> ();
        			
        			for(int x=0; x<values.length; x++) {
        				if(vHeaders.get(x).getColumnClass() == Integer.class)
        					if(values[x].length() > 0) {
        						row.add(new Integer(Integer.parseInt(values[x])));
        					}
        					else {
        						row.add(null);
        					}
        				else if(vHeaders.get(x).getColumnClass() == Long.class) {
        					if(values[x].length() > 0) {
            					row.add(new Long(Long.parseLong(values[x])));
        					}
        					else {
        						row.add(null);
        					}
        				}
        				else if(vHeaders.get(x).getColumnClass() == Double.class) {
        					if(values[x].length() > 0) {
            					row.add(new Double(Double.parseDouble(values[x])));
        					}
        					else {
        						row.add(null);
        					}
        				}
        				else {
        					if(values[x].length() > 0) {
            					row.add(values[x]);
        					}
        					else {
        						row.add(null);
        					}
        				}
        			}
        			
        			addRow(row);
        		}
        		progress.setText("");
        	}
        	catch(Exception e) {
        		e.printStackTrace();
        	}
        }
        
        // Note that each hit can appear in multiple records depending how many annotations it has. 
        // They are ordered by hit_idx so the identical hits should be consecutive.
        // The code groups them into a single record, combining the annotations.
        
        public void addRowsWithProgress(ResultSet rset, FieldData theFields, JTextField progress) {
        	try {
               	// Collect the included/excluded group indices.
        		//
        		// Note that if they chose a particular chromosome from the dropdown list, that is
        		// enforced in the query, so the hits/annotations we have here already come only
        		// from those chromosomes.
        		// The additional constraints we have to now apply come from whether they have the include/exclude
        		// boxes checked, and the "include only" checkbox.
       		
        		rset.setFetchSize(100);
        		
            	TreeSet<Integer> allIncludes = new TreeSet<Integer>(); // for incOnly
            	Vector<TreeSet<Integer>> includes = new Vector<TreeSet<Integer>>();
            	TreeSet<Integer> excludes = new TreeSet<Integer>();
            	SpeciesSelectPanel spl = theParent.theParentFrame.lQueryPanel.speciesPanel;
            	String[] chrs = spl.theChromosomeIndicies;
            	// Are we keeping only hits to the included?
            	boolean incOnly = theParent.theParentFrame.lQueryPanel.isOnlyIncluded();
            	int nExcChr= 0;
            	for (int i = 0; i < chrs.length; i++)
            	{
            		boolean inc = theParent.theParentFrame.lQueryPanel.isInclude(i);
            		boolean exc = theParent.theParentFrame.lQueryPanel.isExclude(i);
            		if (exc) nExcChr++;
            		if (!inc && !exc) continue;
            		TreeSet<Integer> incs = null;
            		if (inc)
            		{
            			incs = new TreeSet<Integer>();
            		}
            		for(String idxstr : chrs[i].split(","))
            		{
            			if(idxstr != null && idxstr.length() > 0) { 
	            			int idx = Integer.parseInt(idxstr);
	            			if (inc)
	            			{
	            				incs.add(idx);
	            				allIncludes.add(idx);
	            			}
	            			if (exc)
	            			{
	            				excludes.add(idx);
	            			}
            			}
            		}
            		if (inc)
            		{
            			includes.add(incs);
            		}       				
            	}
            	// special case - they only want to see the orphans of one species,
            	// so there won't be any hits or groups
            	boolean orphOnly = theParent.theParentFrame.lQueryPanel.isOrphan();//(chrs.length == 1 + nExcChr);
            	//if (orphOnly)
            	//{
            	//	System.out.println("Orphans only (1 include, rest excludes)");
            	//}

        		String [] symbols = theFields.getDisplayFieldSymbols();

        		Vector<Vector<Object>> rowDatas = new Vector<Vector<Object>> ();
        		boolean firstRow = true;
        		boolean cancelled = false;
        		
        		// Build a lookup table telling which row position the species+keyword combos are in
        		HashMap<String,HashMap<String,Integer>> kw2pos = new HashMap<String,HashMap<String,Integer>>();
        		HashMap<String,Integer> spec2gfpos = new HashMap<String,Integer>();
        		int aNamePos = -1;
        		int grpPos = -1;
        		int bnumPos = -1;
        		int rstatPos = -1;
        		for (int pos = 0; pos < theFields.getNumFields(); pos++)
        		{
        			String name = theFields.getFieldNameAt(pos).toLowerCase();
        			//System.out.println(name);
        			if (!name.contains("\n"))
        			{
        				if (name.equals("annotation"))
        				{
        					aNamePos = pos;
        				}
        				else if (name.equals("pgenef"))
        				{
        					grpPos = pos;
        				}
        				else if (name.equals("blocknum"))
        				{
        					bnumPos = pos;
        				}
        				else if (name.equals("rstat"))
        				{
        					rstatPos = pos;
        				}

        				continue;
        			}
    				else 
    				{
    					if (name.endsWith("#rgn"))    				
	    				{
	    					String spname = name.replace("\n#rgn", "");
	    					spec2gfpos.put(spname,pos);
	    				}
    					else
    					{
		        			String[] fields = name.split("\\n");
		        			String species = fields[0];
		        			String kw = fields[1];
		        			if (!kw2pos.containsKey(species))
		        			{
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
        		HashMap<Integer,Integer> grpSizes = new HashMap<Integer,Integer>();
        		HashMap<Integer,Integer> annotMap = new HashMap<Integer,Integer>();
        		computeGroups(hitIdx2Grp,annotMap,grpSizes,rset, includes, excludes, allIncludes, incOnly,orphOnly, progress);
        		hitIdx2Grp.put(0, 0); // for the annots that didn't go into a group
        		grpSizes.put(0, 0);
        		int prev_hidx = -1;
        		rset.beforeFirst();
        		int nAnnot = 0;
        		String pname1 = "oops", pname2 = "oops";
        		Utilities.init_rs2col();
        		while(rset.next() && !cancelled) {
        			
        			boolean isAnnot = rset.getBoolean(Utilities.rs2col("isAnnot",rset));
        			int hidx = rset.getInt(Utilities.rs2col("HITIDX",rset));
        			int gidx1 = (!isAnnot ? rset.getInt(Utilities.rs2col("GRPIDX1",rset)) : rset.getInt(Utilities.rs2col("AGIDX",rset))); // actually either would work
        			int agidx = rset.getInt(Utilities.rs2col("AGIDX",rset));
        			int aidx = (isAnnot ? rset.getInt(Utilities.rs2col("AIDX",rset)) : 0);
        			int gidx2 = (!isAnnot ? rset.getInt(Utilities.rs2col("GRPIDX2",rset)) : 0);
        			String sp1 = rset.getString(Utilities.rs2col("SPECNAME1",rset)).toLowerCase();
        			String sp2 = (!isAnnot ? rset.getString(Utilities.rs2col("SPECNAME2",rset)).toLowerCase() : "");
        			String annot = rset.getString(Utilities.rs2col("ANAME",rset));
        			
        			if (isAnnot)
        			{
        				//assert(prev_hidx >= 0);
        				assert(gidx1 > 0);
        				assert(aidx >0);
        				nAnnot++;
        			}
        			       			
        			if (!isAnnot)
        			{
        				if (!hitIdx2Grp.containsKey(hidx)) continue;
        			}
        			else
        			{
        				if (!annotMap.containsKey(aidx))
        				{
        					// It's not an orphan.
        					continue;
        				}
 
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
        			if (!isAnnot)
        			{
        				// store the previous proj names 
        				pname1 = rset.getString(Utilities.rs2col("SPECNAME1",rset)).toLowerCase(); 
        				pname2 = rset.getString(Utilities.rs2col("SPECNAME2",rset)).toLowerCase();
        			}
        			
        			prev_hidx = hidx;
        			
    				String sp = null;
        			if (agidx > 0)
        			{
        				if (agidx == gidx1)
        				{
        					sp = sp1;
        				}
        				else if (agidx == gidx2)
        				{
        					sp = sp2;
        				}
        				else
        				{
        					System.out.println("Annotation doesn't go with either group; set to null");       					
        				}
        			}
        			Vector<Object> rowData = new Vector<Object>();
        			rowData.clear();
        			rowData.setSize(symbols.length+1);
					
					
        			for(int x=0; x<symbols.length; x++) {
    					Object tempVal;
    					String sym = symbols[x];
    					if (isAnnot)
    					{
    						if (sym.equals("GRP1START")) 
    						{
    							sym="ASTART";
    						}
    						else if (sym.equals("GRP1END")) 
    						{
    							sym="AEND";
    						}
    					}    					
    					tempVal = rset.getObject(Utilities.rs2col(sym,rset));

	        				
    					if(firstRow && tempVal != null) {
    						vHeaders.get(x+1).setColumnClass(tempVal.getClass());
    					}
	        			rowData.set(x+1, tempVal);
        			}
        			
        			rowData.set(aNamePos+1,new String(""));
        			if (sp != null)
        			{
        				if (kw2pos.containsKey(sp) && kw2pos.get(sp).containsKey("all_anno"))
        				{
        					int pos = kw2pos.get(sp).get("all_anno");
        					rowData.set(pos+1, annot);
        				}
        			}
        			int grp = 0;
        			if (!isAnnot)
        			{
        				grp = hitIdx2Grp.get(hidx);
        			}
        			else 
        			{
        				grp = annotMap.get(aidx);
        			}
        			int grpsize = grpSizes.get(grp);
        			rowData.set(grpPos+1, grp);
        			rowData.set(grpPos+2, grpsize);
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
        				//int rstat = (int)(calcRstat(cnts));
        				//rowData.set(rstatPos+1, rstat);
        			}
        			if (!isAnnot)
        			{
        				int blockNum = rset.getInt(Utilities.rs2col("BNUM",rset));
        				String blocknum = "";
        				if (blockNum > 0)
        				{
        					blocknum = rset.getString(Utilities.rs2col("GRPNAME1",rset)) + "." + rset.getString(Utilities.rs2col("GRPNAME2",rset)) + "." +
        					rset.getInt(Utilities.rs2col("BNUM",rset));
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
        		}
        		if (rowDatas.size() > 0) 
        		{
        			addRows(rowDatas, kw2pos);
					counterInc(proj2hits,pname1,1);
					counterInc(proj2hits,pname2,1);
        		}
        		progress.setText("");
            	//System.out.println("Annots:" + nAnnot);
        	} catch (Exception e) {
        		e.printStackTrace();
        	}
        	//System.out.println("Rows:" + vData.size());
        }
    	private double calcRstat(int[] ctgCounts) throws Exception
    	{
     		double ctgTotal = 0;
    		for (int i = 0; i < ctgCounts.length; i++)
    		{
    			ctgTotal += ctgCounts[i];
    		}
    		double rstat = 0.0;
    		double N = ctgCounts.length;
    		for (int i = 0; i < ctgCounts.length; i++)
    		{
    			double x = (double)ctgCounts[i];			
    			double expected = ctgTotal/N;
    			if (x > 0.0 && expected > 0.0)
    			{
    				rstat += x*Math.log10(x/expected);
    			}
    		}
    		rstat = ((double)Math.round(rstat*10))/10.0;
    		//System.out.println(rstat);
    		if (rstat < -.5)
    		{
    			System.out.println("Warning: negative R-Stat " + rstat);
    		}
    		if (rstat < 0) rstat = 0.0;
    		
    		return rstat;
    	}
        private void counterInc(Map<String,Integer> ctr, String key, int inc  )
        {
        	if (inc == 0) return;
			if (!ctr.containsKey(key))
			{
				ctr.put(key, inc);
			}
			else
			{
				ctr.put(key, ctr.get(key)+inc);
			}

        }
        // Group the hits based on overlap on one side or the other. It's a bit complicated because
        // the hits have to be binned by location in order to avoid all-against-all searches.
        // Aside from that it's just building a graph and finding the connected pieces.
        //
        // If they've chosen "include only", then we build the groups ONLY with hits between included species.
        // Otherwise, we build the groups first, and then apply the include/exclude, as follows:
        // includes: the group must hit ALL the included species
        // excludes: the group must not hit ANY of the excluded species
        //
        
        private void computeGroups(HashMap<Integer,Integer> out, HashMap<Integer,Integer> annotMap, HashMap<Integer,
        		Integer> sizes, ResultSet rs, Vector<TreeSet<Integer>> includes, TreeSet<Integer> excludes,
        		TreeSet<Integer> allIncludes, boolean incOnly, boolean orphOnly, JTextField progress) throws Exception
        {
        	System.out.println("Computing PgeneF groupings");
        	rs.beforeFirst();
        	int binsize = 100000;
        	boolean unAnnot = theParent.theParentFrame.lQueryPanel.isUnannot();
        	boolean isClique = theParent.theParentFrame.lQueryPanel.isClique();

			if(progress != null && (vData.size() % DISPLAY_INTERVAL == 0)) {
				if(!progress.getText().equals("Cancelled"))
					progress.setText("Computing putative gene families");
			}
			Utilities.timerStart();
        	class rhit implements Comparable<rhit>
        	{
        		int gidx1;
        		int gidx2;
        		int pidx1;
        		int pidx2;
        		int s1;
        		int s2;
        		int e1;
        		int e2;
        		int idx;
        		int grpHitIdx; // the index of the group this hit is in
        		int grpCount = 0;
        		boolean annot1 = false;
        		boolean annot2 = false;
        		
        		public int compareTo(rhit h)
        		{
        			if (idx < h.idx) return -1;
        			else if (idx > h.idx) return 1;
        			return 0;
        		}
        	}
        	//TreeSet<rhit> allHits = new TreeSet<rhit>(); // for validation testing
        	HashMap<Integer,Collection<rhit>> hit2grp = new HashMap<Integer,Collection<rhit>>();
        	
 
        	TreeMap<Integer,Integer> grpIdx2IncIdx = new TreeMap<Integer,Integer>();
        	for (int i = 0; i < includes.size(); i++)
        	{
        		for (int gidx : includes.get(i))
        		{
        			grpIdx2IncIdx.put(gidx,i);
        		}
        	}
        	// bins: first index, grp; second index, location bin
        	HashMap<Integer,HashMap<Integer,HashSet<rhit>>> bins = new HashMap<Integer,HashMap<Integer,HashSet<rhit>>>();
        	int numRows = 0;
        	int prevIdx = -1;
        	rhit prev_h = null;
        	HashMap<Integer,Integer> projIdx2SpecNum = new HashMap<Integer,Integer>();
        	HashMap<Integer,String> grp2proj = new HashMap<Integer,String>();
        	
        	Utilities.init_rs2col();
        	
        	while(rs.next())
        	{
    			if(progress != null && (vData.size() % DISPLAY_INTERVAL == 0)) {
    				if(progress.getText().equals("Cancelled"))
    					return;
    			}

    			boolean isAnnot = rs.getBoolean(Utilities.rs2col("isAnnot",rs));
      			if (!isAnnot) 
      			{
	      			rhit h = new rhit();
	       			
	    			h.idx = rs.getInt(Utilities.rs2col("HITIDX",rs));
	    			h.gidx1 = rs.getInt(Utilities.rs2col("GRPIDX1",rs));
	    			h.gidx2 = rs.getInt(Utilities.rs2col("GRPIDX2",rs));
	    			h.pidx1 = rs.getInt(Utilities.rs2col("PROJ1IDX",rs));
	    			h.pidx2 = rs.getInt(Utilities.rs2col("PROJ2IDX",rs));
	    			
	    			grp2proj.put(h.gidx1, rs.getString(Utilities.rs2col("SPECNAME1",rs)).toLowerCase());
	    			grp2proj.put(h.gidx2, rs.getString(Utilities.rs2col("SPECNAME2",rs)).toLowerCase());
	    			    			
	    			h.s1 = rs.getInt(Utilities.rs2col("GRP1START",rs));
	    			h.e1 = rs.getInt(Utilities.rs2col("GRP1END",rs));
	    			h.s2 = rs.getInt(Utilities.rs2col("GRP2START",rs));
	    			h.e2 = rs.getInt(Utilities.rs2col("GRP2END",rs));
	    			h.grpHitIdx = 0;

	    			//String atype = rs.getString("ATYPE");
	    			int agidx = rs.getInt(Utilities.rs2col("AGIDX",rs));

	    			if ( agidx > 0)
	    			{
	    				if (agidx == h.gidx1) 
	    				{
	    					h.annot1 = true;
	    				}
	    				else if (agidx == h.gidx2) 
	    				{
	    					h.annot2 = true;
	    				}
	    			}
	    			if (h.idx == prevIdx) 
	    			{
	    				// Here we collect the subsequent annotations from the instance of this hit
	    				// that we added to the groups.
	    				prev_h.annot1 = (prev_h.annot1 || h.annot1);
	    				prev_h.annot2 = (prev_h.annot2 || h.annot2);
	    				continue; 
	    			}
	       			prevIdx = h.idx;
	       			prev_h = h;
	        		numRows++;
	    			
	    			if (incOnly)
	    			{
	    				if (!allIncludes.contains(h.gidx1) && !allIncludes.contains(h.gidx2))
	    				{
	    					// Now it won't get put in a group which means it will be skipped
	    					// when loading the table. 
	    					continue;
	    				}
	    			}
	    			
	    			//allHits.add(h);
	    			
	    			// Go through the bins this hit is in and see if it overlaps any of the hits in them.
	    			// Also add it to the bins.
	    			
	    			TreeSet<rhit> olaps = new TreeSet<rhit>();
	    			
					if (!bins.containsKey(h.gidx1))
					{
						bins.put(h.gidx1, new HashMap<Integer,HashSet<rhit>>());					
					}   	
					HashMap<Integer,HashSet<rhit>> bin1 = bins.get(h.gidx1);
					if (!bins.containsKey(h.gidx2))
					{
						bins.put(h.gidx2, new HashMap<Integer,HashSet<rhit>>());					
					}   	
					HashMap<Integer,HashSet<rhit>> bin2 = bins.get(h.gidx2);
					
	    			for(int b1 = (int)(h.s1/binsize); b1 <= (int)(h.e1/binsize); b1++)
	    			{
	    				if (!bin1.containsKey(b1))
	    				{
	    					bin1.put(b1, new HashSet<rhit>());		
	    				}
	    				HashSet<rhit> bin = bin1.get(b1);
	    				for (rhit rh : bin)
	    				{
	    					if ( (rh.gidx1==h.gidx1 && Utils.intervalsOverlap(h.s1,h.e1,rh.s1,rh.e1,0)) || 
	    							(rh.gidx2==h.gidx1 && Utils.intervalsOverlap(h.s1,h.e1,rh.s2,rh.e2,0)) )
	    					{
	    						olaps.add(rh);
	    					}
	    				}
	    				bin.add(h);
	        			
	    			}
	    			for(int b2 = (int)(h.s2/binsize); b2 <= (int)(h.e2/binsize); b2++)
	    			{
	    				if (!bin2.containsKey(b2))
	    				{
	    					bin2.put(b2, new HashSet<rhit>());		
	    				}
	    				HashSet<rhit> bin = bin2.get(b2);
	    				for (rhit rh : bin)
	    				{
	    					if ( (rh.gidx1==h.gidx2 && Utils.intervalsOverlap(h.s2,h.e2,rh.s1,rh.e1,0)) || 
	    							(rh.gidx2==h.gidx2 && Utils.intervalsOverlap(h.s2,h.e2,rh.s2,rh.e2,0)) )
	    					{
	    						olaps.add(rh);
	    					}
	    				}
	    				bin.add(h);
	        			
	    			}
	    			// Combine all the earlier groups connected by the overlaps (or make a new group if no overlaps)
	    			if (!orphOnly) // for just getting orphans of one species, can skip this lengthy step
	    			{
		    			Collection<rhit> maxGrp = null; 
		    			int maxGrpIdx = 0;
		    			HashSet<Integer> grpList = new HashSet<Integer>();
		    			
	    				// First get the non-redundant list of groups, and 
		    			// also find the biggest existing  group so we can reuse it. 
		    			// (Because HashSet adds were costing a lot of time.)
		    			
		    			for (rhit rhit : olaps)
		    			{
		    				int grpHitIdx = rhit.grpHitIdx;
		    				if (grpHitIdx == 0) continue; // it's the new hit
		    				if (!grpList.contains(grpHitIdx))
		    				{
		    					grpList.add(grpHitIdx);
		    					if (maxGrp == null)
		    					{
		    						maxGrp = hit2grp.get(grpHitIdx);
		    						maxGrpIdx = grpHitIdx;
		    					}
		    					else
		    					{
		    						if (hit2grp.get(grpHitIdx).size() > maxGrp.size())
		    						{
		    							maxGrp = hit2grp.get(grpHitIdx);
		    							maxGrpIdx = grpHitIdx;
		    						}
		    					}
		    				}
		    			}
		    			if (maxGrp != null)
		    			{
		    				//System.out.println("maxGrpIdx " + maxGrpIdx);
			    			for (int idx : grpList)
			    			{
			    				if (idx != maxGrpIdx )
			    				{
			    					//System.out.println("merge with: " + idx);
			    					maxGrp.addAll(hit2grp.get(idx));
			    					for (rhit rh2 : hit2grp.get(idx))
			    					{
			    						rh2.grpHitIdx = maxGrpIdx;
			    					}
			    					hit2grp.remove(idx);		    				
			    				}
			    			}
			    			maxGrp.add(h);
			    			h.grpHitIdx = maxGrpIdx;
		    			}
		    			else
		    			{
			    			hit2grp.put(h.idx, new Vector<rhit>());
			    			hit2grp.get(h.idx).add(h);
			    			h.grpHitIdx = h.idx;
			    			//System.out.println("new grp idx:" + h.idx);

		    			}
	    			}
	    			else
	    			{
		    			hit2grp.put(h.idx, new Vector<rhit>());
		    			h.grpHitIdx = h.idx;	    				
	    			}
	        	}
      			else // isAnnot
      			{
      				// Annotations come last so the groups should already be formed. 
      				// We just check whether the annot is in a group, i.e. not an orphan,
      				// and also whether it satisifes the include/excludes.
      				// If it passes all tests we add it to the annotMap and it will be shown.
	    			int gidx = rs.getInt(Utilities.rs2col("AGIDX",rs));
	    			int aidx = rs.getInt(Utilities.rs2col("AIDX",rs));
	    			int s = rs.getInt(Utilities.rs2col("ASTART",rs));
	    			int e = rs.getInt(Utilities.rs2col("AEND",rs));
	    			int pidx = rs.getInt(Utilities.rs2col("PROJIDX",rs));
	    			String pname = rs.getString(Utilities.rs2col("SPECNAME1",rs)).toLowerCase();
	    			
					// It must contain all the included species, (i.e., there can only be one included).
					// It must not be excluded.
    				if (includes.size() != 0)
    				{
    					if (includes.size() > 1) continue;
    					if (!includes.firstElement().contains(gidx)) continue;
    				}
    				if (excludes.contains(gidx)) continue;

    				
	    			boolean inGrp = false;
	    			if (!projIdx2SpecNum.containsKey(pidx))
	    			{
	    				int curNum = 1 + projIdx2SpecNum.keySet().size();
	    				projIdx2SpecNum.put(pidx, curNum);
	    			}
	    			int specNum = projIdx2SpecNum.get(pidx); // this is the "group" number for orphans
	    			
	    			if (bins.containsKey(gidx))
	    			{
						HashMap<Integer,HashSet<rhit>> gbin = bins.get(gidx);
		    			for(int b = (int)(s/binsize); b <= (int)(e/binsize); b++)
		    			{
		    				if (gbin.containsKey(b))
		    				{
			    				for (rhit rh : gbin.get(b))
			    				{
			    					if ( (rh.gidx1==gidx && Utils.intervalsOverlap(s,e,rh.s1,rh.e1,0)) || 
			    							(rh.gidx2==gidx && Utils.intervalsOverlap(s,e,rh.s2,rh.e2,0)) )
			    					{
			    						inGrp = true;
			    						//annotMap.put(aidx,rh.idx);
			    						break;
			    					}
			    				}
		    				}
		    				if (inGrp) break;
		    			}
	    			}
	    			if (!inGrp)
	    			{
	    				annotMap.put(aidx,specNum);
	    				counterInc(proj2orphs,pname,1);
	    			}
      			}
        	}

			// Validate
			/*for (rhit rh : allHits) { rh.grpCount = 0; }
			for (int idx : hit2grp.keySet())
			{
				Set<rhit> grp1 = hit2grp.get(idx); 
				for (rhit rh : grp1)
				{
					rh.grpCount++;
					assert(rh.grpCount == 1);
					assert(rh.grpHitIdx == idx);
				}
			}  */			

        	// Lastly number the groups and fill the output map.
        	// Also check the includes/excludes.
			for (int grp : projIdx2SpecNum.values())
			{
				sizes.put(grp,0);
			}
			for (int grp : annotMap.values())
			{
				sizes.put(grp,1 + sizes.get(grp));
			}
        	int grpNum =  projIdx2SpecNum.keySet().size();
        	
        	// We also want to count the distinct regions on each chr and how
        	// many are annotated.
        	// This can be done group by group since whenever two regions overlap,
        	// their hits will be in the same group.
        	
        	Set<Integer> hitList = hit2grp.keySet(); // since we alter hit2grp within
        	for (int idx : hitList)
        	{
        		// First remove the duplicate hits from this grp
        		HashSet<rhit> fixedGrp = new HashSet<rhit>();
        		fixedGrp.addAll(hit2grp.get(idx));
        		hit2grp.put(idx, fixedGrp);
        		if (includes.size() > 0)
        		{
        			// Check that this group includes all of the include species,
        			// i.e. that at least one grpidx from each of the includes is
        			// found on at least one hit in the group.
        			int[] incCount = new int[includes.size()];
        			for (rhit h : hit2grp.get(idx))
        			{
        				if (grpIdx2IncIdx.containsKey(h.gidx1))
        				{
        					incCount[grpIdx2IncIdx.get(h.gidx1)]++;
        				}
        				if (grpIdx2IncIdx.containsKey(h.gidx2))
        				{
        					incCount[grpIdx2IncIdx.get(h.gidx2)]++;
        				}
        			}
        			boolean pass = true;
        			for (int i = 0; i < incCount.length; i++)
        			{
        				if (0 == incCount[i])
        				{
        					pass = false;
        					break;
        				}
        			}
        			if (!pass) continue;
        		}
        		if (excludes.size() > 0 || allIncludes.size() > 0)
        		{
        			// Check that none of the hits is to a grpidx from an excluded species.
        			boolean pass = true;
        			for (rhit h : hit2grp.get(idx))
        			{        				
        				if (excludes.contains(h.gidx1) || excludes.contains(h.gidx2)) 
        				{
        					pass = false;
        					break;
        				}
        				if (unAnnot)
        				{
        					if (allIncludes.contains(h.gidx1))
        					{
        						if (h.annot1) 
        						{
        							pass = false;
        							break;
        						}
        					}
        					if (allIncludes.contains(h.gidx2))
        					{
        						if (h.annot2) 
        						{
        							pass = false;
        							break;
        						}
        					}
        				}
        			}
        			if (!pass) continue;
        		}
        		if (isClique)
        		{
        			HashMap<Integer,HashSet<Integer>> links = new HashMap<Integer,HashSet<Integer>>();
        			for (rhit h : hit2grp.get(idx))
        			{
        				if (allIncludes.contains(h.gidx1))
        				{
        					if (!links.containsKey(h.pidx1))
        					{
        						links.put(h.pidx1, new HashSet<Integer>());
        					}
        					links.get(h.pidx1).add(h.pidx2);       					        					
        				}
           				if (allIncludes.contains(h.gidx2))
        				{
        					if (!links.containsKey(h.pidx2))
        					{
        						links.put(h.pidx2, new HashSet<Integer>());
        					}
        					links.get(h.pidx2).add(h.pidx1);       					
        				}
        			}
        			int np = links.keySet().size();
        			//System.out.println("projects:" + np);
        			boolean pass = true;
        			for (int pidx : links.keySet())
        			{
        				//System.out.println("links:" + links.get(pidx).size());
        				if (links.get(pidx).size() < np-1)
        				{
        					pass = false;
        					break;
        				}
        			}
        			if (!pass) continue;
        		}
        		grpNum++;
        		grp2projcounts.put(grpNum, new HashMap<String,Integer>());
        		HashMap<String,Integer> pcounts = grp2projcounts.get(grpNum);
        		// Count the regions in this group
        		if (true)//hit2grp.get(idx).size() > 9) 
        		{
	        		HashMap<Integer,Vector<Integer>> slist = new HashMap<Integer,Vector<Integer>>();
	        		HashMap<Integer,Vector<Integer>> elist = new HashMap<Integer,Vector<Integer>>();
	        		HashMap<Integer,Vector<Boolean>> alist = new HashMap<Integer,Vector<Boolean>>();
	        		for (rhit h : hit2grp.get(idx))
	        		{
	           			if (!slist.containsKey(h.gidx1))
	        			{
	        				slist.put(h.gidx1, new Vector<Integer>());
	        				elist.put(h.gidx1, new Vector<Integer>());
	        				alist.put(h.gidx1, new Vector<Boolean>());
	        			}
	           			if (!slist.containsKey(h.gidx2))
	        			{
	        				slist.put(h.gidx2, new Vector<Integer>());
	        				elist.put(h.gidx2, new Vector<Integer>());
	        				alist.put(h.gidx2, new Vector<Boolean>());
	        			}
	        			
	           			slist.get(h.gidx1).add(h.s1);
	           			elist.get(h.gidx1).add(h.e1);
	           			alist.get(h.gidx1).add(h.annot1);
	           			slist.get(h.gidx2).add(h.s2);
	           			elist.get(h.gidx2).add(h.e2);
	           			alist.get(h.gidx2).add(h.annot2);
	        		}
	        		for (int gidx : slist.keySet())
	        		{
	        			int[] results = new int[]{0,0};
	        			clusterIntervals(slist.get(gidx), elist.get(gidx), alist.get(gidx),results);
	        			int nRegions = results[0];
	        			int nAnnot = results[1];
	        			String pname = grp2proj.get(gidx);
	        			counterInc(proj2regions,pname,nRegions);
	        			counterInc(proj2annot,pname,nAnnot);
	        			counterInc(pcounts,pname,nRegions);
	                	//System.out.println(nRegions + " clusters, " + nAnnot + " annotated");
	        		}
        		}
        		if (hit2grp.get(idx).size() > 10)
        		{
        			//System.out.println("group size " + hit2grp.get(idx).size() + " num:" + grpNum);
        		}
        		for (rhit h : hit2grp.get(idx))
        		{
        			out.put(h.idx, grpNum);
        		}
        		sizes.put(grpNum, hit2grp.get(idx).size());
        	}
        	Utilities.timerEnd();
         }
        private void clusterIntervals(Vector<Integer> starts, Vector<Integer> ends, Vector<Boolean> annots,
        		int[] results)
        {
        	Vector<Integer> cstarts = new Vector<Integer>();
        	Vector<Integer> cends = new Vector<Integer>();
        	Vector<Boolean> cannots = new Vector<Boolean>();
        	for (int i = 0; i < starts.size(); i++)
        	{
        		Vector<Integer> chits = new Vector<Integer>();
        		boolean cannot = annots.get(i);
        		int mins = starts.get(i);
        		int maxe = ends.get(i);
        		for (int j = 0; j < cstarts.size(); j++)
        		{
        			if (Utils.intervalsOverlap(starts.get(i), ends.get(i), cstarts.get(j), cends.get(j), 0))
        			{
        				chits.add(j);
        				cannot = (cannot || cannots.get(j));
        				mins = Math.min(mins, cstarts.get(j));
        				maxe = Math.max(maxe, cends.get(j));
        			}
        		}
        		if (chits.size() == 0)
        		{
        			cstarts.add(mins);
        			cends.add(maxe);
        			cannots.add(cannot);
        		}
        		else 
        		{
        			// grow the first one and remove the rest
        			cstarts.set(chits.get(0),mins);
        			cends.set(chits.get(0),maxe);
        			cannots.set(chits.get(0), cannot);
        			for (int k = chits.size()-1; k >= 1; k--)
        			{
        				int kk = chits.get(k);
        				cstarts.remove(kk);
        				cends.remove(kk);
        				cannots.remove(kk);
        			}
        		}
 
        	}
        	assert(cstarts.size() == cannots.size());
        	results[0] = cstarts.size();
        	int nannot = 0;
        	for (boolean b : cannots)
        	{
        		if (b) nannot++;
        	}
        	results[1] = nannot;
        }
        // This function catenates the keyword entries for each species from all
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
            		if(kw2pos.get(sp).get("all_anno") != null) {
            			int pos = kw2pos.get(sp).get("all_anno");
            			tempRow.set(pos+1,null);
            		}
            	}
            	
        		// We'll build up a set of values for each keyword to prevent duplicates
            	HashMap<String,HashMap<String,HashSet<String>>> kwvals 
        				= new HashMap<String,HashMap<String,HashSet<String>>>();
        		for (String sp : kw2pos.keySet())
        		{
        			kwvals.put(sp,new HashMap<String,HashSet<String>>());
        			for (String kw : kw2pos.get(sp).keySet())
        			{
        				kwvals.get(sp).put(kw, new HashSet<String>());
        			}
        		}
        		
            	// Now parse the annot keywords from each row and group them
        		for (Vector<Object> rowData : rowDatas) 
        		{
	        		if(bReadOnly || rowData.size() != vHeaders.size()) throw (new Exception());
	        		parseRow(kwvals,rowData,kw2pos);
        		}
        		
        		// Put the annot key values in their places
        		for (String sp : kwvals.keySet())
        		{
        			for (String kw : kwvals.get(sp).keySet())
        			{
        				int pos = kw2pos.get(sp).get(kw);
        				tempRow.set(pos+1,Utils.join(kwvals.get(sp).get(kw),";"));
        			}
        		}
        		tempRow.set(0, new Long(vData.size() + 1));
        		vData.add(tempRow);

        	}
        	catch(Exception e) {
        		e.printStackTrace();
        	}
        }
        private void parseRow(HashMap<String,HashMap<String,HashSet<String>>> kwvals, Vector<Object> in, 
        		HashMap<String,HashMap<String,Integer>> kw2pos) throws Exception
        {

        	String species = null;
        	String all_annot = null;
    		// First, find which species is annotated and get the annot string
        	for (String sp : kw2pos.keySet())
        	{
        		if(kw2pos.get(sp).get("all_anno") != null) {
	        		int pos = kw2pos.get(sp).get("all_anno");
	        		if (in.get(pos+1) != null)
	        		{
	        			if (species != null)
	        			{
	        				throw(new Exception("Row has two species annots! "));
	        			}
	        			species = sp;
	        			all_annot = (String)in.get(pos+1);
	        		}
        		}
        	}
        	if (species == null)
        	{
        		//throw(new Exception("No annot on row!"));
        		return; // This happens for the empty "hit" type annotations
        	}
        	
        	// Add the all_annot entry
        	kwvals.get(species).get("all_anno").add(all_annot);
        	
        	// Parse out the rest of the entries       	
			String[] fields = all_annot.split(";");
			for (String field : fields)
			{
				String[] words = field.trim().split("[\\s,=]",2);
				if (words.length == 1) continue;
				String key = words[0].trim().toLowerCase();
				String val = words[1].trim();
				if (val.startsWith("="))
				{
					val = val.substring(1);
				}
				if (key.endsWith("="))
				{
					key = key.substring(0,key.length() - 1);
				}
				if (key.equals("") || val.equals("")) 
				{
					continue;
				}
				if (kwvals.get(species).containsKey(key))
				{
					kwvals.get(species).get(key).add(val);
				}
			}
        	
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
        		e.printStackTrace();
        		return null;
        	}
        }

        public Object [] getRowAt(int row) {
                try {
                        if(!bReadOnly) throw (new Exception());
                        return arrData[row];
                }
                catch(Exception e) {
                        e.printStackTrace();
                        return null;
                }
        }

        public String getColumnName(int column) {
                try {
                        if(!bReadOnly) throw (new Exception());
                        return arrHeaders[column].getColumnName();
                } catch(Exception e) {
                        e.printStackTrace();
                        return null;
                }
        }

        public Class<?> getColumnType(int column) {
        	try {
        		if(!bReadOnly) throw (new Exception());
        		return arrHeaders[column].getColumnClass();
        	}
        	catch(Exception e) {
        		e.printStackTrace();
        		return null;
        	}
        }
        
        public boolean isAscending(int column) {
        	try {
        		if(!bReadOnly) throw (new Exception());
        		return arrHeaders[column].isAscending();
        	}
        	catch(Exception e) {
        		e.printStackTrace();
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
        }
        
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
