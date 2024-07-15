package symapQuery;

import java.sql.ResultSet;
import java.util.Vector;
import java.util.TreeSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;

import javax.swing.JTextField;

import symap.Globals;
import symap.manager.Mproject;
import util.ErrorReport;
import util.Utilities;

/************************************************
 * create the Vector <DBdata> rowsFromDB; 
 * TableDataPanel.buildTable calls the static method loadRowsFromDB, and passes in the result set.
 *   buildTable then calls makeRow on each DBdata to put in order.
 *   
 * Each hit can appear in multiple records depending how many annotations it has. 
 * They are ordered by hit_idx so the identical hits will be consecutive.
 * The code merges each hit_idx  into a single row.
 * 
 * if change column: loadHit, loadHitMerge, makeRow
 * 
 * CAS504 this file replaced the previous approach to parsing the sql results
 * CAS547 add EveryPlus; CAS548 add Olap 
 */
public class DBdata implements Cloneable {
	protected static String [] spNameList; 				// species Names
	private static int [] 	spIdxList; 					// species spIdx in same order
	private static HashMap <Integer, String[]> annoKeys;// spIdx, keys in order
	private static HashMap <Integer, Integer> grpStart; // grpIdx, start
	private static HashMap <Integer, Integer> grpEnd;   // grpIdx, end
	
	private static QueryPanel qPanel;
	private static SpeciesSelectPanel spPanel;
	private static JTextField loadStatus;
	private static boolean isSingle=false, isSingleGenes = false, isIncludeMinor = true; // CAS547 
	private static HashSet <Integer> grpIdxOnly;
	
	private static int cntDup=0, cntFilter=0, cntPgeneF=0; // TEST_TRACE
	private static int cntPlus2=0;	// CAS547 check for possible pre-v547
	
	protected static Vector <DBdata> loadRowsFromDB(
			ResultSet rs, 							// Result set, ready to be parsed
			Vector<Mproject> projList,				// species in order displayed
			QueryPanel theQueryPanel,  				// for species information	
			String [] annoColumns, 					// Species\nkeyword array in order displayed
			JTextField loadTextField,   			// write progress
			HashMap <Integer, Integer> geneCntMap, 	// output variable of spIdx annotidx counts
			HashMap<String, String> projMap)  		// PgeneF and Multi summary output
	{
		clear();
		qPanel = 		theQueryPanel;
		spPanel = 		qPanel.getSpeciesPanel();
		loadStatus = 	loadTextField;
		
		isSingle 		= qPanel.isSingle();
		isSingleGenes 	= qPanel.isSingleGenes();
		isIncludeMinor 	= qPanel.isIncludeMinor(); // See loadHit; loadMergeHit
		
		cntPlus2=0; cntPgeneF=0;
		cntDup=0; cntFilter=0; // restart TEST_TRACE counts
		
		makeSpLists(projList);
		makeAnnoKeys(annoColumns);
		makeGrpLoc(qPanel.getGrpCoords()); // gStart and gEnd
		grpIdxOnly = qPanel.getGrp();      // Chr is filtered, but may want one gene, anno, multi to just be on the selected
		
		// CAS543 remove isEitherAnno and isBothAnno (can do in QueryPanel), add isAnnoTxt; CAS549 all use grpIdxOnly
		boolean isFilter = (grpStart.size()>0  || qPanel.isAnnoTxt() || qPanel.isGeneNum() || qPanel.isOneAnno());
			
		Vector <DBdata> rows = new Vector <DBdata> ();
	
		int rowNum=1;
		
		try {
			if (isSingle) {
				while (rs.next()) {
					DBdata dd = new DBdata();
					dd.loadSingle(rs, rowNum);
					rows.add(dd); 
         			rowNum++;
         			if (rowNum%Q.INC ==0) 
         				loadStatus.setText("Processed " + rowNum + " rows");
				}
			}
			else if (isIncludeMinor) {
				HashMap <String, DBdata> hitMap = new HashMap <String, DBdata> ();
				
	         	while (rs.next()) {
	         		int hitIdx = rs.getInt(Q.HITIDX);
	         		int anno1 =  rs.getInt(Q.PHAANNOT1IDX);
	         		int anno2 =  rs.getInt(Q.PHAANNOT2IDX);
	         		String pidx = (anno1<anno2) ? anno1+":"+anno2 : anno2+":"+anno1;
	         		String key = hitIdx + ":" + pidx;
	         		
	         		if (!hitMap.containsKey(key)) { // first occurrences of hit
	         			DBdata dd = new DBdata();
	         			dd.loadHit(rs, rowNum);
	         			
	         			hitMap.put(key, dd);
	         			rows.add(dd); 
	         			rowNum++;
	         			if (rowNum%Q.INC ==0) 
	         				loadStatus.setText("Processed " + rowNum + " rows");
	         		}
	         		else  {							// merge further occurrences of hit
	         			DBdata dd = hitMap.get(key);
	         			dd.loadHitMerge(rs);
	         		}
	         	} 	
	         	finishAllGenes(hitMap);
			}
			else { // two records for each hit, for each side of pair; block, hitIdx, are the same
				HashMap <Integer, DBdata> hitMap = new HashMap <Integer, DBdata> ();
				
	         	while (rs.next()) {
	         		int hitIdx = rs.getInt(Q.HITIDX);
	         		
	         		if (!hitMap.containsKey(hitIdx)) { // first occurrences of hit
	         			DBdata dd = new DBdata();
	         			dd.loadHit(rs, rowNum);
	         			
	         			hitMap.put(hitIdx, dd);
	         			rows.add(dd); 
	         			rowNum++;
	         			if (rowNum%Q.INC ==0) 
	         				loadStatus.setText("Processed " + rowNum + " rows");
	         		}
	         		else  {							// merge further occurrences of hit
	         			DBdata dd = hitMap.get(hitIdx);
	         			dd.loadHitMerge(rs);
	         		}
	         	} 	
			}
			rs.close();
			
		// Filter
			if (isFilter) {
				rowNum=0;
				Vector <DBdata> filterRows = new Vector <DBdata> ();
				for (DBdata dd : rows) {
					if (dd.passFilters()) {
						filterRows.add(dd);	
						
						rowNum++;
						dd.rowNum = rowNum;
						if (rowNum%Q.INC ==0) 
	         				loadStatus.setText("Filtered " + rowNum + " rows");
					}
					else cntFilter++;
				}
				rows.clear();
				rows = filterRows;
			}
		// Single only filter
			if (isSingleGenes) {
				int lastIdx=-1;
				rowNum=0;
				Vector <DBdata> filterRows = new Vector <DBdata> ();
				for (DBdata dd : rows) {
					if (lastIdx!= dd.annoSet0.first())  {// you have to have a gene
						lastIdx = dd.annoSet0.first();
						
						filterRows.add(dd);	
						rowNum++;
						dd.rowNum = rowNum;
						if (rowNum%Q.INC ==0) 
							loadStatus.setText("Filtered " + rowNum + " rows");
					}
				}
				rows.clear();
				rows = filterRows;
			}
				
		// Finish
			rowNum=0;
         	for (DBdata dd : rows) {
         		if (!dd.finish(annoColumns)) return rows;
         		rowNum++;
				if (rowNum%1000 ==0) 
     				loadStatus.setText("Finished " + rowNum + " rows");
         	}
         	if (qPanel.isGeneNum()) { // CAS555 - add computation of grpN
         		runGeneGroups(rows);
         	}
         	else if (qPanel.isMultiAnno()) { // Multi - may remove rows and renumber
         		loadStatus.setText("Compute multi-hit genes");
         		rows = runMultiGene(rows, projMap);
         	}
         	else if (qPanel.isPgeneF()) { // PgeneF - may remove rows and renumber
         		loadStatus.setText("Compute PgeneF and filter");
         		rows = runPgeneF(rows, projMap);
         	}
         	else {
         		sortRows(rows);
         	}
         	makeGeneCnts(rows, geneCntMap); // CAS514 delete makeGeneCnts, use genenum from DB instead
        	
         	if (Q.TEST_TRACE) {
         		if (cntDup>0) System.out.format("%6d Dups\n", cntDup);
         		if (cntFilter>0) System.out.format("%6d Filter\n", cntFilter);
         		if (cntPlus2>0) System.out.format("%6d hits with two non-best genes\n", cntPlus2);
         	}
         		
         	return rows;
     	} 
		catch (Exception e) {ErrorReport.print(e, "Reading rows from db");}
		return rows;
	}
	// CAS556 order was by hitNum always; run after finish(); Multi is sorted in ComputeMulti
	private static void sortRows(Vector <DBdata> rows) {
	try {
		if (qPanel.isCollinear() || qPanel.isBlock()) {
			Collections.sort(rows, new Comparator<DBdata> () {
				public int compare(DBdata a, DBdata b) {
					String as = (qPanel.isCollinear()) ? a.collinearStr : a.blockStr;
					String bs = (qPanel.isCollinear()) ? b.collinearStr : b.blockStr;
					
					if (as.equals(Q.empty) && !bs.equals(Q.empty)) return 1;
					if (!as.equals(Q.empty) && bs.equals(Q.empty)) return -1;
					if (as.equals(bs)) return a.hitNum-b.hitNum;
					
					int retval=0;
					String [] av = as.split("\\."); 
					String [] bv = bs.split("\\.");
					int n = Math.min(av.length, bv.length);
					
					for(int x=0; x<n && retval == 0; x++) { // copied from TableData
						boolean valid = true;
						Integer leftVal = -1, rightVal = -1; 
						
						try {
							leftVal  = Integer.parseInt(av[x]); 
							rightVal = Integer.parseInt(bv[x]);      
						}
						catch(Exception e) {valid = false; } // e.g. chr X
						
						if (valid) retval = rightVal.compareTo(leftVal);
						else       retval = bv[x].compareTo(av[x]);
					}
					return retval;
				}
			});
 		}
		else {
			Collections.sort(rows, new Comparator<DBdata> () {
				public int compare(DBdata a, DBdata b) {
					if (a.chrIdx[0]!=b.chrIdx[0]) return a.chrIdx[0] - b.chrIdx[0];
					if (a.chrIdx[1]!=b.chrIdx[1]) return a.chrIdx[1] - b.chrIdx[1];
					return a.hitNum-b.hitNum;
				}
			});
		}
		int rnum=1;
		for (DBdata r : rows) r.rowNum = rnum++;
	}
	catch (Exception e) {ErrorReport.print(e, "Sort rows");}
	}
	/********************************************************
	 * Example:
	 * Best   gene X  gene Y   both annotated when two records, X & Y
	 * Other  gene Z  gene Y   only Z annotated because no corresponding record for Y, so get from Best
	 */
	private static void finishAllGenes(HashMap <String, DBdata> hitMap) {
		try {
			// gather all geneIdx with record
			HashMap <Integer, DBdata> tagMap = new HashMap <Integer, DBdata> ();
			for (DBdata dObj : hitMap.values()) {
				for (int i=0; i<2; i++)
					if (dObj.annotIdx[i]>0 && !dObj.geneTag[i].equals(Q.empty)) 
						tagMap.put(dObj.annotIdx[i], dObj);
			}
			// assign 
			Vector <DBdata> noAnno = new Vector <DBdata> ();
			for (DBdata dObj : hitMap.values()) { 
				DBdata cpObj=null;
				int x=0;
				if (dObj.annotIdx[0]>0 && dObj.geneTag[0].equals(Q.empty)) {
					if (tagMap.containsKey(dObj.annotIdx[0])) {
						cpObj = tagMap.get(dObj.annotIdx[0]);
						x=0;
					}
					else noAnno.add(dObj);
				}
				else if (dObj.annotIdx[1]>0 && dObj.geneTag[1].equals(Q.empty)) {
					if (tagMap.containsKey(dObj.annotIdx[1])) {
						cpObj = tagMap.get(dObj.annotIdx[1]);
						x=1;
					}
					else noAnno.add(dObj);
				}
				if (cpObj==null) continue;
				
				dObj.geneTag[x]	=	cpObj.geneTag[x];
				dObj.gstart[x] = 	cpObj.gstart[x];		
				dObj.gend[x] =		cpObj.gend[x];
				dObj.glen[x] = 		cpObj.glen[x];
				dObj.gstrand[x] = 	cpObj.gstrand[x];
				dObj.golap[x] = 	cpObj.golap[x];
				dObj.annotStr[x] =	cpObj.annotStr[x];
				
				if (x==0) {
					for (int an : cpObj.annoSet0) dObj.annoSet0.add(an);
				}
				else    {
					for (int an : cpObj.annoSet1) dObj.annoSet1.add(an);
				}  
			}
		}
		catch (Exception e) {ErrorReport.print(e, "Finish all genes");}
	}
	private static void clear() { // CAS519 added since everything is static
		if (annoKeys!=null) 	annoKeys.clear();
		if (grpStart!=null) 	grpStart.clear();
		if (grpEnd!=null) 		grpEnd.clear();
		if (spNameList!=null) 	spNameList=null;
		if (spIdxList!=null) 	spIdxList=null;
	}
	/*****************************************************
	 * Species columns in order of projects
	 */
	private static void makeSpLists(Vector <Mproject> projList) {
		try {
			int numSp = projList.size();
			spNameList = new String [numSp];
			spIdxList = new int [numSp];
			for (int p=0; p<numSp; p++) {
				spNameList[p] = projList.get(p).getDisplayName();
				spIdxList[p] = spPanel.getSpIdxFromSpName(spNameList[p]);
			}
		}
		catch (Exception e) {ErrorReport.print(e, "Make species lits");}
	}
	
	/********************************************
	 * annoColumns is created in TableDataPanel.loadProjectAnnotations()
	 * The annoColumns array contains spIdx::keyword entries for all projects.
	 * The keywords should be in order the way they are to be displayed
	 */
	private static void makeAnnoKeys(String [] annoColumns) {
		try {	
			annoKeys = new HashMap <Integer, String[]>  ();
			
			// count number of keys per species
			HashMap <String, Integer> cntSpKeys = new HashMap <String, Integer> ();
			for (int i=0; i<annoColumns.length; i++) {
				String sp = annoColumns[i].split(Q.delim)[0];
				
				if (cntSpKeys.containsKey(sp)) 	cntSpKeys.put(sp, cntSpKeys.get(sp)+1);
				else 							cntSpKeys.put(sp, 1);
			}
			
			// Make array of keywords for each species
			HashMap <String, Integer> spName2spIdx = spPanel.getSpName2spIdx();
			for (String spName : cntSpKeys.keySet()) {
				int spidx = spName2spIdx.get(spName);
				
				int cnt = cntSpKeys.get(spName);
				String [] keys = new String [cnt];
				
				for (int i=0, k=0; i<annoColumns.length; i++) {
					String sp = annoColumns[i].split(Q.delim)[0];
					if (sp.equals(spName)) 
						keys[k++] =  annoColumns[i].split(Q.delim)[1];
				}
				annoKeys.put(spidx, keys);
			}
		}
		catch (Exception e) {ErrorReport.print(e, "make keyword array");}
	}
	/**********************************************************
	 * Set up coordinates from the interface for filter
	 * grpCoords.put(index, start + Q.delim2 + stop); created in QueryPanel
	 */
	static private void makeGrpLoc(HashMap <Integer, String> grpCoords) {
		try {
			grpStart = new HashMap <Integer, Integer> ();
			grpEnd = new HashMap <Integer, Integer> ();
			if (grpCoords.size()==0) return;
		
			for (int gidx : grpCoords.keySet()) {
				String [] coords = grpCoords.get(gidx).split(Q.delim2);
				int s=0, e=0;
				try {
					s = Integer.parseInt(coords[0]);
					e = Integer.parseInt(coords[1]);
				}
				catch (Exception ex) {ErrorReport.print(ex, "Cannot process coords " + grpCoords.get(gidx));}
				
				grpStart.put(gidx, s);
				grpEnd.put(gidx, e);
			}
		}
		catch (Exception e) {ErrorReport.print(e, "make location list");}
	}
	/*************************************************
	 * put results from Gene# in groups; CAS555 add 
	 */
	private static void runGeneGroups(Vector <DBdata> rows) {
	try {
		Collections.sort(rows, new Comparator<DBdata> () {
			public int compare(DBdata a, DBdata b) {
				if (a.chrIdx[0]==b.chrIdx[0]) {
					if (a.annotIdx[0]==b.annotIdx[0]) return a.annotIdx[1]-b.annotIdx[1];
					else                              return a.annotIdx[0]-b.annotIdx[0];
				}
				return a.chrIdx[0] - b.chrIdx[0];
			}
		});
		
		String inputGN = qPanel.getGeneNum();
		if (inputGN.endsWith(".")) inputGN = inputGN.substring(0, inputGN.length()-1);
		String genestr = Utilities.getGenenumIntOnly(inputGN);
		genestr = "." + genestr;
		
		HashMap <String, Integer> geneTag0 = new HashMap <String, Integer>  ();
		HashMap <String, Integer> geneTag1 = new HashMap <String, Integer>  ();
		String [] tok;
		String [] tag = new String [2];
		String [] chr = new String [2];
		String key;
		
		for (DBdata dd : rows) {
			for (int i=0; i<2; i++) {
				tag[i] = dd.geneTag[i]; 
				tok = tag[i].split("\\.");  
				chr[i] = tok[0]; 
				if (tok.length==3) 				tag[i] = tok[0]+"."+tok[1];
				else if (tag[i].endsWith("."))  tag[i] = tag[i].substring(0, tag[i].length()-1);
			}
		
			if (tag[0].endsWith(genestr)) {
				key = tag[0] + ":" + chr[1];
				if (geneTag0.containsKey(key)) geneTag0.put(key, geneTag0.get(key)+1);
				else 						   geneTag0.put(key, 1);
			}
			if (tag[1].endsWith(genestr)) {
				key = tag[1] + ":" + chr[0];
				if (geneTag1.containsKey(key)) geneTag1.put(key, geneTag1.get(key)+1);
				else 						   geneTag1.put(key, 1);
			}
     	}
		
		HashMap<String, Integer> grpMap0 = new HashMap<String, Integer>();  // geneTag, grpN
		HashMap<String, Integer> grpMap1 = new HashMap<String, Integer>();  // geneTag, grpN
		HashMap<String, Integer> grpMap;
		HashMap<String, Integer> geneTag;
		
		int numFam=1, sz=0;
		for (DBdata dd : rows) { // valid rows with valid keys and geneCnt entries
			for (int i=0; i<2; i++) {
				tag[i] = dd.geneTag[i]; 
				tok = tag[i].split("\\.");  
				chr[i] = tok[0]; 
				if (tok.length==3) 				tag[i] = tok[0]+"."+tok[1];
				else if (tag[i].endsWith(".")) 	tag[i] = tag[i].substring(0, tag[i].length()-1);
				
				if (!tag[i].endsWith(genestr)) continue;
				
				int j = (i==0) ? 1 : 0;
				String chrj = dd.geneTag[j].substring(0, dd.geneTag[j].indexOf("."));
				key = (tag[i]+ ":" + chrj);
				
				grpMap  = (i==0) ? grpMap0 : grpMap1;
				geneTag = (i==0) ? geneTag0 : geneTag1;
				
				if (geneTag.containsKey(key)) {
					sz = geneTag.get(key);
					int fn;
					if (grpMap.containsKey(key)) fn = grpMap.get(key);
					else {
						fn = numFam++;
						grpMap.put(key, fn);
					}
					dd.setGroup(fn,sz);
					break; // if i=0, no need to do i=1
				}
				else symap.Globals.eprt("SyMAP error: No entry for " + key);
			}
		}
	}
	catch (Exception e) {ErrorReport.print(e, "make Gene# groups");}
	}
	
	// CAS555 move code to ComputeMulti; compute does the rows, PgeneF and PgFSize
	private static Vector <DBdata> runMultiGene(Vector <DBdata> rows, HashMap<String, String> projMap) {
		ComputeMulti mobj = new ComputeMulti(rows, qPanel, loadStatus, grpIdxOnly, spIdxList, projMap);
		return mobj.compute();	
	}
	
	////////////////////////////////////////////////////////////////////////////
	private static Vector <DBdata> runPgeneF(Vector <DBdata> rows, HashMap<String, String> projMap ) {
		try {
			HashMap<Integer,Integer> hitIdx2Grp = new HashMap<Integer,Integer>(); // PgeneF
    		HashMap<Integer,Integer> grpSizes = new HashMap<Integer,Integer>();   // PgFSize
    		
			new ComputePgeneF(rows,  qPanel, loadStatus,
				hitIdx2Grp,  grpSizes, projMap); // output data structures
		    		 	
			hitIdx2Grp.put(0, 0); // for the annots that didn't go into a group
    		grpSizes.put(0, 0);
    		
    		int rowNum=1;
    		Vector <DBdata> rowsForDisplay = new Vector <DBdata> ();
    		for (DBdata dd : rows) {
    			if (!hitIdx2Grp.containsKey(dd.hitIdx)) {
    				cntPgeneF++;
    				if (cntPgeneF<3) symap.Globals.dprt("Not in hitIdx2Grp: " + dd.hitIdx);
    				continue;
    			}
    			
    			dd.rowNum = rowNum;
    			rowNum++;
    			
    			int pgenef = hitIdx2Grp.get(dd.hitIdx);
    			int pgfsize = grpSizes.get(pgenef);
    			dd.setGroup(pgenef, pgfsize);
    			
    			rowsForDisplay.add(dd);
    			
    			if (rowNum%Q.INC ==0) 
     				loadStatus.setText("Filtered " + rowNum + " rows");
    		}
			rows.clear();
			return rowsForDisplay;
		}
		catch (Exception e) {ErrorReport.print(e, "run pgenef"); return null;}
	}
	private static void makeGeneCnts(Vector <DBdata> rows, HashMap <Integer, Integer> geneCntMap) {
		try {
			HashMap <Integer, Integer> numPerGene = new HashMap <Integer, Integer>  ();  // AIDX, assigned num
			HashMap <Integer, Integer> lastNumPerSp = new HashMap <Integer, Integer>  (); // SpIdx, lastNum

         	for (int spIdx : spIdxList) lastNumPerSp.put(spIdx, 0);
        
         	for (DBdata dd : rows) {
         		for (int x=0; x<2; x++) {
         	        TreeSet <Integer> anno = (x==0) ? dd.annoSet0 : dd.annoSet1;
	         		for (int aidx : anno) {
	         			int num=0;
	         			int spx = dd.spIdx[x];
	         			if (!numPerGene.containsKey(aidx)) {
	         				num=lastNumPerSp.get(spx);   		// new number
	         				num++;
	         				lastNumPerSp.put(spx, num);
	         				numPerGene.put(aidx, num);			// assign number to gene
	         			}
	         			else {
	         				num = numPerGene.get(aidx);
	         			}
	         		}
         		}
         	}
         	for (int spIdx : lastNumPerSp.keySet()) {
         		geneCntMap.put(spIdx, lastNumPerSp.get(spIdx));
         	}
		}
		catch (Exception e) {ErrorReport.print(e, "make gene cnt");}
	}
	/****************************************************************
	 * XXX DBdata class - one instance per row
	 * from hit table - same values added for each side of hit - anno different for both sides
	 */
	private DBdata() {}
	
	private void loadHit(ResultSet rs, int row) {
		try {
			this.rowNum = row;
			
			hitIdx = 		rs.getInt(Q.HITIDX);
			hitNum = 		rs.getInt(Q.HITNUM);
			
			spIdx[0] = 		rs.getInt(Q.PROJ1IDX);
			chrIdx[0] = 	rs.getInt(Q.GRP1IDX);
			hstart[0] = 	rs.getInt(Q.HIT1START);
			hend[0] = 		rs.getInt(Q.HIT1END);
			hlen[0] =		Math.abs(hend[0]-hstart[0])+1;
			annotIdx[0]	= 	rs.getInt(Q.ANNOT1IDX);
			
			spIdx[1] = 		rs.getInt(Q.PROJ2IDX);
			chrIdx[1] = 	rs.getInt(Q.GRP2IDX);  
			hstart[1] = 	rs.getInt(Q.HIT2START); 
			hend[1] = 		rs.getInt(Q.HIT2END);   
			hlen[1] = 		Math.abs(hend[1]-hstart[1])+1;
			annotIdx[1]	= 	rs.getInt(Q.ANNOT2IDX);
			
			runSize = 		rs.getInt(Q.COSIZE);
			runNum = 		rs.getInt(Q.CONUM);
			blockNum =		rs.getInt(Q.BNUM);
			blockScore =	rs.getInt(Q.BSCORE);
			pid	=			rs.getInt(Q.PID);
			psim	=		rs.getInt(Q.PSIM);
			hcnt	=		rs.getInt(Q.HCNT);
			
			if (hcnt==0) 	hcnt=1;				// CAS519 makes more sense to be 1 merge instead of empty
			String s =		rs.getString(Q.HST);
			hst = 			(s.contains("+") && s.contains("-")) ? "!=" : "="; // NOTE: use 's' to show the actual value
			hscore	=		rs.getInt(Q.HSCORE);
			htype	=		rs.getString(Q.HTYPE);
			
			int annoGrpIdx = rs.getInt(Q.AGIDX);	// grpIdx, so unique; same as chrIdx[0|1]
			if (annoGrpIdx==0) return; 				// NO ANNO
			
		// Get anno for one side
			int x = (annoGrpIdx==chrIdx[0]) ? 0 : 1; 
			String tag = Utilities.getGenenumFromDBtag(rs.getString(Q.AGENE));// CAS547 convert here for AllGenes
			int annoIdx  = rs.getInt(Q.AIDX);
			
			if (annoIdx!=annotIdx[0] && annoIdx!=annotIdx[1]) { // CAS520 all annot_idx,hit_idx loaded; make sure best
				if (isIncludeMinor) {							// CAS547 add
					annotIdx[x] = annoIdx;						// replace best
					htype = "--";								// otherwise, inherits best-best assignment
					tag = tag + " " + Globals.minorAnno;		// + indicates best replaced
				}
				else return; // The correct annotation will be added in merged to THIS record
			}
			
			gstart[x] =		rs.getInt(Q.ASTART);		// CAS519 add these 4 columns
			gend[x] =		rs.getInt(Q.AEND);
			glen[x] = 		Math.abs(gend[x]-gstart[x])+1;
			gstrand[x] = 	rs.getString(Q.ASTRAND); 
			golap[x] = 		rs.getInt(Q.AOLAP);
			
			annotStr[x] =	rs.getString(Q.ANAME);
			geneTag[x] = 	tag; 		// CAS514 add geneTag; CAS518 chg genenum to tag
			
			if (x==0) annoSet0.add(annoIdx); // multiple anno's for same hit
			else      annoSet1.add(annoIdx);
		}
		catch (Exception e) {ErrorReport.die(e, "Read hit record");}
	}
	/***************************************
	 * If there is no anno for one side of the hit, there will be no merged record
	 */
	private void loadHitMerge(ResultSet rs) {
		try {
			int annoGrpIdx = rs.getInt(Q.AGIDX); 	// will not be zero because no record if anno not on other side
			 
			int x = (annoGrpIdx==chrIdx[0]) ? 0 : 1; // goes with 1st or 2nd half of hit
			String tag = Utilities.getGenenumFromDBtag(rs.getString(Q.AGENE));// CAS547 convert here for AllGenes
			
			int annoIdx = rs.getInt(Q.AIDX);
			if (annoIdx!=annotIdx[0] && annoIdx!=annotIdx[1]) { 
				if (isIncludeMinor) {		// CAS547 add
					annotIdx[x] = annoIdx;	// CAS555 bug fix! was setting to grpIdx, hit overlaps at least two genes on both sides
					tag = tag + "**";		// indicate 2nd non-best; CAS555 was '++'
					cntPlus2++;
				}
				else return;
			}
			
			gstart[x] =		rs.getInt(Q.ASTART);	// CAS519 add these 4 columns
			gend[x] =		rs.getInt(Q.AEND);
			glen[x] = 		Math.abs(gend[x]-gstart[x])+1;
			gstrand[x] = 	rs.getString(Q.ASTRAND); 
			golap[x] = 		rs.getInt(Q.AOLAP);
			geneTag[x] = 	tag; 					// CAS514
			
			int sz = (x==0) ? annoSet0.size() : annoSet1.size();
			if (sz>0)  { 					
				annotStr[x]	+=	";" + rs.getString(Q.ANAME);
				cntDup++; 
			}  
			else annotStr[x]	=	rs.getString(Q.ANAME);
			
			if (x==0) annoSet0.add(annoIdx);
			else      annoSet1.add(annoIdx);
		}
		catch (Exception e) {ErrorReport.print(e, "Merge");}
	}
	
	/////////////////////////////////////////////////////////////
	private void loadSingle(ResultSet rs, int row) {
		try {
			this.rowNum = row;
			
			annoSet0.add(rs.getInt(Q.AIDX));
			annotStr[0]	=	rs.getString(Q.ANAME);
			chrIdx[0] = 	rs.getInt(Q.AGIDX);
			gstart[0] =		rs.getInt(Q.ASTART);		// Gene start to chromosome
			gend[0] =		rs.getInt(Q.AEND);
			glen[0] = 		Math.abs(gend[0]-gstart[0])+1;
			gstrand[0] = 	rs.getString(Q.ASTRAND); // CAS519 add
			geneTag[0] = 	rs.getString(Q.AGENE);   // CAS514 add
			geneTag[0] = 	Utilities.getGenenumFromDBtag(geneTag[0]); // CAS547
			
			numHits[0] = 	rs.getInt(Q.ANUMHITS);   // CAS541 add
		}
		catch (Exception e) {ErrorReport.print(e, "Read single");}
	}
	/*********************************************
	 * Create block string and make anno keys 
	 */
	private boolean finish(String [] annoColumns) {
	try {
		int n = (isSingle) ? 1 : 2;
		for (int x=0; x<n; x++) {
			chrNum[x] =  spPanel.getChrNameFromChrIdx(chrIdx[x]); 
			spName[x] =  spPanel.getSpNameFromChrIdx(chrIdx[x]);
			if (isSingle) spIdx[x] =  spPanel.getSpIdxFromChrIdx(chrIdx[x]); 

			String chr = (chrNum[x].startsWith("0") && chrNum[x].length()>1) ? chrNum[x].substring(1) : chrNum[x];
			if (!geneTag[x].equals(Q.empty)) geneTag[x] = chr +"."+geneTag[x];  // CAS547 was in Utilities, but now remove (...) earlier
			else geneTag[x] = chr +".-"; // indicates hit but no gene; parsed in TmpRowData/TableReport; CAS555 add
			
			if (blockNum>0) blockStr = Utilities.blockStr(chrNum[0], chrNum[1], blockNum); // CAS513 use blockStr
			if (runSize>0)  {
				collinearStr = Utilities.blockStr(chrNum[0], chrNum[1], runSize); // CAS517 add chrs;
				if (runNum>0)  collinearStr += "." + runNum; 					  // CAS520 add runNum
			}
			
			/******* Anno Keys ******************/
			if (!annoKeys.containsKey(spIdx[x])) continue; // If this is index 0, then still need 1
			
			String [] keyList = annoKeys.get(spIdx[x]);
			int cnt= keyList.length;
			
			String [] valList = new String [cnt]; // if n keys, then n values
			for (int i=0; i<cnt; i++) valList[i]="";
	
			if (annotStr[x].equals("")) { 
				for (int i=0; i<cnt; i++) valList[i]="";
			}
			else {
				String[] fieldList = annotStr[x].split(";");
				for (String field : fieldList)
				{
					String[] words = field.trim().split("[\\s,=]",2);
					if (words.length == 1) continue;
					
					String key = words[0].trim();
					if (key.endsWith("="))	key = key.substring(0,key.length() - 1);
					
					String val = words[1].trim();
					if (val.startsWith("=")) val = val.substring(1);
					
					if (key.equals("") || val.equals("")) continue;
					
					for (int k=0; k<cnt; k++) {
						if (keyList[k].equals(key)) {
							valList[k] = valList[k].equals("") ? val : (valList[k] + ";" + val);
							break;
						}
					}
				}
				if (keyList[cnt-1].equals(Q.All_Anno))
								valList[cnt-1] = annotStr[x];
			}
			annoVals.put(x, valList);
		}	
		return true;
		
	} catch (Exception e) {ErrorReport.print(e, "Reading rows"); return false;}
	}
	
	/*********************************************************
	 * The locations and hit gene filters are done here instead of MySQL where statement
	 * This has to be performed after all hits are read for the isOneAnno and isTwoAnno
	 */
	private boolean passFilters() {
		try {
			if (isSingle) { // anno check is in query; see below why it needs to be done here for !isSingle
				if (grpStart.size()==0) return true;
				
				for (int gidx : grpStart.keySet()) { // only one selected
					int sf = grpStart.get(gidx);   	 // 0 if not set or set to 0
					int ef = grpEnd.get(gidx); 	
					
					if (chrIdx[0]==gidx) {
						if (sf > 0 && gstart[0] < sf) return false;  // note gene start (not hit as used below)
						if (ef > 0 && gend[0]   > ef) return false;
						return true;
					}
				}
				return false; 
			}
			// GeneNum: Chr is the only other filter, which happened in SQL; but need to make sure geneNum on correct chr
			// CAS541 added gene# only; CAS543 allow suffix; CAS548 allow 2 chr selected, but must have correct genenum
			
			if (grpStart.size()>0) { // CAS549 move from end so work for following 3
				for (int gidx : grpStart.keySet()) {
					int sf = grpStart.get(gidx); 
					int ef = grpEnd.get(gidx);
					
					for (int x=0; x<2; x++) { // using hit coords
						if (chrIdx[x]==gidx) {
							if (sf > 0 && hstart[x] < sf)return false;  
							if (ef > 0 && hend[x] > ef)  return false; // CAS522 was a <e since v519
						}
					}
				}
			}
						
			// CAS549 only show if selected chr or no selection
			boolean bChr0 = (grpIdxOnly.size()==0 || grpIdxOnly.contains(chrIdx[0]));
			boolean bChr1 = (grpIdxOnly.size()==0 || grpIdxOnly.contains(chrIdx[1]));
				
			// CAS543 when doing anno search as query, returned hits without anno if they overlap
			// CAS549 add check to make sure anno is on the selected chr-only; put before one
			if (qPanel.isAnnoTxt()) { 
				String anno = qPanel.getAnnoTxt().toLowerCase();
				
				if (bChr0 && annotStr[0].toLowerCase().contains(anno)) return true;
				if (bChr1 && annotStr[1].toLowerCase().contains(anno)) return true;
					
				return false;
			}
						
			// After ANNO: CAS543 moved to SQL 
			if (qPanel.isOneAnno()) { // Either is done for One, but must further filter
				if (annotIdx[0] > 0   && annotIdx[1] <= 0) return true;
				if (annotIdx[0] <= 0  && annotIdx[1] > 0)  return true;
				return false;
			}
						
			String inputGN = qPanel.getGeneNum();
			if (inputGN!=null) {
				if (inputGN.endsWith(".")) inputGN = inputGN.substring(0, inputGN.length()-1);
				
				String tag0 = geneTag[0], tag1 = geneTag[1];
				if (!inputGN.contains(".")) { // e.g. 16.a and 16.b will match if no specific suffix
					tag0 = Utilities.getGenenumIntOnly(tag0);
					tag1 = Utilities.getGenenumIntOnly(tag1);
				}
				
				if (bChr0 && tag0!="-" && tag0.equals(inputGN)) return true;	
				if (bChr1 && tag1!="-" && tag1.equals(inputGN))  return true;
					
				return false;
			}
			return true;
			
		} catch (Exception e) {ErrorReport.print(e, "Reading rows"); return false;}
	}
	
	/*********************************************************
	 * Called if ComputePgeneF is run
	 */
	protected void setGroup(int pgenef, int pgfsize) {
		this.grpN = pgenef;
		this.grpSize = pgfsize;
	}
	/*************************************************
	 * TableData.addRowsWithProgress: returns row formated for table.
	 * 
	 * Values must be enter in order of table columns
	 * Row, PgeneF, PgFSize, HitIdx, BlockNum, BlockScore, RunSize};
	 * For each species: Chr Start End
	 * For each species: keywords in order
	 */
	protected Vector <Object> makeRow() {
	try {
		Vector<Object> row = new Vector<Object>();

		// general
		row.add(rowNum);		
		
		if (!isSingle) {
			if (blockNum<=0)    row.add(Q.empty);  else row.add(blockStr);
			if (blockScore<=0)  row.add(Q.empty);  else row.add(blockScore);
			if (runSize<0)      row.add(Q.empty);  else row.add(collinearStr);
			
			if (grpN<=0)      	row.add(Q.empty);  else row.add(grpN);
			if (grpSize<=0)     row.add(Q.empty);  else row.add(grpSize);
			
			if (hitNum<=0)      row.add(hitIdx);   else row.add(hitNum);
			if (hscore<=0)		row.add(Q.empty);  else row.add(hscore); // CAS540 add column; CAS548 mv column
			if (pid<=0)			row.add(Q.empty);  else row.add(pid);
			if (psim<=0)		row.add(Q.empty);  else row.add(psim);
			if (hcnt<=0)		row.add(Q.empty);  else row.add(hcnt);
			if (hst.contentEquals(""))	row.add(Q.empty);  else row.add(hst); // CAS520 add column
			if (htype.contentEquals("")) row.add(Q.empty);  else row.add(htype); // CAS546 add column
		}
		// chr, start, end; CAS519 add gene&hit for pairs
		int cntCol = FieldData.getSpColumnCount(isSingle);
		for (int i=0; i<spIdxList.length; i++) { // add columns for ALL species
			int x=-1;
			if (spIdx[0] == spIdxList[i]) x=0;
			else if (spIdx[1]==spIdxList[i]) x=1;
			
			if (x>=0) {
				row.add(geneTag[x]); // CAS518 num to string
				if (!isSingle)
					if (golap[x]<0)  	row.add(Q.empty); else row.add(golap[x]); // zero is okay
				row.add(chrNum[x]); 
				if (gstart[x]<0)  	row.add(Q.empty); else row.add(gstart[x]); 
				if (gend[x]<0)  	row.add(Q.empty); else row.add(gend[x]); 
				if (glen[x]<=0)  	row.add(Q.empty); else row.add(glen[x]);
				row.add(gstrand[x]);
				
				if (!isSingle) {
					row.add(hstart[x]); 
					row.add(hend[x]); 
					row.add(hlen[x]);	
				}
				else row.add(numHits[x]); // CAS541
			}
			else {
				for (int j=0; j<cntCol; j++) row.add(Q.empty);
			}
		}
		// annotation
		for (int i=0; i<spIdxList.length; i++) {
			if (spIdx[0] == spIdxList[i]) {
				if (annoVals.containsKey(0)) {
					String [] vals = annoVals.get(0);
					for (String v : vals) {
						if (v==null || v.equals("")) v=Q.empty;
						row.add(v);
					}
				}
			}
			else if (spIdx[1] == spIdxList[i]) {
				if (annoVals.containsKey(1)) {
					String [] vals = annoVals.get(1);
					for (String v : vals) {
						if (v==null || v.equals("")) v=Q.empty;
						row.add(v);
					}
				}
			}
			else {
				if (annoKeys.containsKey(spIdxList[i])) { // fill in columns as empty from Keys (not Vals)
					String [] vals = annoKeys.get(spIdxList[i]);
					if (vals!=null)
						for (int v=0; v<vals.length; v++) row.add(Q.empty);
				}
			}
		}
		return row;
	}
	catch (Exception e) {ErrorReport.die(e, "Making rows"); return null;}
	}
	/***************************************************
	 * Gets for Summary Panel - x is 0 or 1
	 */
	protected int    getSpIdx(int x) 	{return spIdx[x];}
	protected int    getChrIdx(int x) 	{return chrIdx[x];}
	protected int 	 getHitIdx() 		{return hitIdx;}
	protected int [] getCoords() {
		int [] x = {hstart[0], hend[0], hstart[1], hend[1]}; // CAS519 hit
		return x;
	}
	protected boolean hasAnno(int x) 	{
		if (x==0) return annoSet0.size()>0;
		else      return annoSet1.size()>0;
	}
	protected boolean hasBlock() 	{return blockNum>0;}
	protected boolean hasCollinear() {return !collinearStr.isEmpty() && !collinearStr.equals(Q.empty);} // CAS555
	
	protected Object copy() { // clone only copies primitive, might as well just copy
		DBdata nObj = new DBdata();
		nObj.hitIdx = hitIdx; nObj.hitNum = hitNum;
		nObj.pid = pid; nObj.psim = psim; nObj.hcnt=hcnt; 		
		nObj.hst=hst;	nObj.hscore = hscore; nObj.htype = htype;			
		nObj.blockNum = blockNum; nObj.runNum = runNum;
		nObj.blockScore=blockScore; nObj.runSize=runSize; 
		nObj.blockStr = blockStr; nObj.collinearStr = collinearStr;
		
		nObj.annoSet0 = new TreeSet <Integer> ();  for (int x : this.annoSet0) nObj.annoSet0.add(x);
		nObj.annoSet1 = new TreeSet <Integer> ();  for (int x : this.annoSet0) nObj.annoSet1.add(x);
		
		nObj.annotStr = new String [2];	nObj.annotStr[0] = annotStr[0];  nObj.annotStr[1] = annotStr[1];
		nObj.gstrand = new String [2];	nObj.gstrand[0] = gstrand[0];  	 nObj.gstrand[1] = gstrand[1];
		nObj.geneTag = new String [2];	nObj.geneTag[0] = geneTag[0];    nObj.geneTag[1] = geneTag[1];
		nObj.spName = new String [2];	nObj.spName[0] = spName[0];      nObj.spName[1] = spName[1];
		nObj.chrNum = new String [2];	nObj.chrNum[0] = chrNum[0];      nObj.chrNum[1] = chrNum[1];
		
		nObj.spIdx = new int [2];	nObj.spIdx[0] = spIdx[0];    nObj.spIdx[1] =  spIdx[1];
		nObj.chrIdx = new int [2];	nObj.chrIdx[0] = chrIdx[0];  nObj.chrIdx[1] =  chrIdx[1];
		nObj.gstart = new int [2];	nObj.gstart[0] = gstart[0];  nObj.gstart[1] =  gstart[1];
		nObj.gend = new int [2];	nObj.gend[0] = gend[0];      nObj.gend[1] =  gend[1];
		nObj.glen = new int [2];	nObj.glen[0] = glen[0];      nObj.glen[1] =  glen[1];
		
		nObj.hstart = new int [2];	nObj.hstart[0] = hstart[0];  nObj.hstart[1] =  hstart[1];
		nObj.hend = new int [2];	nObj.hend[0] = hend[0];      nObj.hend[1] =  hend[1];
		nObj.hlen = new int [2];	nObj.hlen[0] = hlen[0];      nObj.hlen[1] =  hlen[1];
		
		nObj.golap = new int [2];	nObj.golap[0] = golap[0];  		nObj.golap[1] =  golap[1];
		nObj.annotIdx = new int [2];nObj.annotIdx[0] = annotIdx[0]; nObj.annotIdx[1] =  annotIdx[1];
		nObj.numHits = new int [2];	nObj.numHits[0] = numHits[0];  	nObj.numHits[1] =  numHits[1];
		
		nObj.annoVals = new HashMap <Integer, String[]> ();
		for (int i : annoVals.keySet()) {
			String [] v = annoVals.get(i);
			String [] x = new String [v.length];
			for (int j=0; j<v.length; j++) x[j] = v[j];
			nObj.annoVals.put(i, x);
		}
		nObj.rowNum = nObj.grpN = nObj.grpSize = -1; // set unique
		
		return nObj;
	}
	public String toString() {
		return String.format("hit# %,5d   sp %d %d   chr %3s %3s   tag %10s %10s  idx %,6d %,6d  grp# %3d", 
				hitNum, spIdx[0], spIdx[1], chrNum[0], chrNum[1], geneTag[0], geneTag[1], 
				annotIdx[0], annotIdx[1], grpN);
	}
	/******************************************************
	 * Class variables -- protected -- accessed in ComputePgeneF and ComputeMulti
	 */
// hit
	private int hitNum = -1;
	private int pid = -1, psim = -1, hcnt=-1; 		// CAS516 add; 
	private String hst="";							// CAS520 add column
	private int hscore = -1;						// CAS540 add column
	protected String htype = "";					// CAS546 add column; EE, EI, IE, nn, etc
		
	private int [] gstart 	= {Q.iNoVal, Q.iNoVal};  // CAS519 add all g-fields. 
	private int [] gend 	= {Q.iNoVal, Q.iNoVal}; 
	private int [] glen 	= {Q.iNoVal, Q.iNoVal};  
	private String [] gstrand = {Q.empty, Q.empty};
	protected int [] hstart = {Q.iNoVal, Q.iNoVal};  
	protected int [] hend 	= {Q.iNoVal, Q.iNoVal}; 
	private int [] hlen 	= {Q.iNoVal, Q.iNoVal};  	// CAS516 add
	private int [] golap 	= {Q.iNoVal, Q.iNoVal};  	// CAS548 add
	protected String [] geneTag = {Q.empty, Q.empty}; 	// CAS514 add; CAS518 chr.gene#.suffix
	private int [] numHits 	= {Q.iNoVal, Q.iNoVal}; 	// CAS541 add
	
// block, collinear
	protected int blockNum = -1, runNum = -1;// run is for collinear
	private int blockScore=-1, runSize=-1; 
	private String blockStr = Q.empty, collinearStr = Q.empty;
	
// Other
	protected int rowNum=0;				// this rowNum is not used in table, but is a placeholder and used in debugging
	protected int grpN=-1, grpSize=-1;	// ComputePgeneF && ComputeMulti; CAS555 was PgeneF and PgFSize
	
	private String [] spName= {Q.empty, Q.empty};	
	protected String [] chrNum= {Q.empty, Q.empty};
	
	// AnnoStr broken down into keyword values in finish()
	private HashMap <Integer, String[]> annoVals = new HashMap <Integer, String[]>  (); // 0,1, values in order

// not columns in table, needed for filters
	protected int hitIdx = -1;
	protected int [] annotIdx 	= {Q.iNoVal, Q.iNoVal}; // CAS520 add 	
	protected int [] spIdx 	= {Q.iNoVal, Q.iNoVal};	
	protected int [] chrIdx = {Q.iNoVal, Q.iNoVal};	
	
	private TreeSet <Integer> annoSet0 = new TreeSet <Integer> (); // not same order as annotStr
	private TreeSet <Integer> annoSet1 = new TreeSet <Integer> (); // just used to count genes
	private String [] annotStr =	 {"", ""}; 					   // broken down into annoVals columns
}
