package symapQuery;

import java.sql.ResultSet;
import java.util.Vector;
import java.util.TreeSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;

import javax.swing.JTextField;

import database.DBconn2;
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
 */
public class DBdata implements Cloneable {
	private static boolean TRC = Globals.TRACE;
	
	private static Vector <DBdata> rows = null; // Output from rowsLoadFromDB
	
	private static TableMainPanel tablePanel;
	private static QueryPanel qPanel;
	private static SpeciesPanel spPanel;
	private static JTextField loadStatus;
	
	// SpStructs
	protected static String [] spNameList=null; 				// species Names; used in ComputeMulti/Clust
	private static int [] 	spIdxList=null; 					// species spIdx in same order
	private static HashMap <Integer, String[]> annoKeys=null;	// spIdx, keys in order
	private static HashMap <Integer, Integer> grpStart=null; 	// grpIdx, start
	private static HashMap <Integer, Integer> grpEnd=null;   	// grpIdx, end
	private static HashSet <Integer> geneIdxForOlap = new HashSet <Integer> (); // for isOlapAnno; see qPanel.isMinorOlap
	
	private static boolean isSelf=false; 
	private static int cntMinorFail=0;	 // self-synteny cannot do minors
	private static boolean isSingle=false, isIncMinor = true, isAnno = false; 
	private static HashSet <Integer> grpIdxForAnno;
	private static String geneNum=null;
	private static int geneOlap=0;
	
	private static boolean bSuccess=true;
	
	private static int cntDup=0, cntFilter=0; // Globals.TRACE
	private static int cntPlus2=0;	// check for possible pre-v547
	
	/***********************************************************************
	 * STATIC loading rows
	 */
	protected static Vector <DBdata> rowsLoadFromDB(
			TableMainPanel tblPanel,				// to access bStopped; 
			ResultSet rs, 							// Result set, ready to be parsed
			Vector<Mproject> projList,				// species in order displayed
			QueryPanel theQueryPanel,  				// for species information	
			String [] annoColumns, 					// Species\nkeyword array in order displayed
			JTextField loadTextField,   			// write progress
			
			HashMap <Integer, Integer> geneCntMap, 	// output variable of spIdx annotidx counts
			HashMap<String, String> sumProjMap)  	// output Clust and Multi summary output
	{
		clearStatic();
		rows = new Vector <DBdata> ();
		
		tablePanel 	= tblPanel;
		qPanel 		= theQueryPanel;
		loadStatus 	= loadTextField;
		spPanel     = qPanel.getSpeciesPanel();
	
		isSelf		= qPanel.isSelf();
		isSingle 	= qPanel.isSingle();
		isIncMinor 	= qPanel.isIncludeMinor(); // See loadHit; loadMergeHit
		
		geneNum  = (qPanel.isGeneNum())  ? qPanel.getGeneNum()  : null;	// MySQL searches on genenum, but this search on suffix
		geneOlap = (qPanel.isGeneOlap()) ? qPanel.getGeneOlap() : -1;   // Checked in passFilterPair() 
		
		isAnno		  = qPanel.isAnnoStr();
		grpIdxForAnno = (isAnno) ? spPanel.getGrpIdxSet() : null; // will be null if all are valid
		
		initSpStructs(projList, annoColumns, qPanel.getGrpCoords());
		
		try {
			rowsMake(rs);			if (!bSuccess) return null;	 // DBdata will have both halves (unless single, gene, anno)
			rowsFilter();   		if (!bSuccess) return null;
			rowsFinish(sumProjMap);	if (!bSuccess) return null;
		 	
         	sortRows(); 				
         	makeSumCnts(geneCntMap); 
        	
         	if (qPanel.isIncludeMinor() && cntMinorFail>0) { // Gene# or Hit#
         		String gene = qPanel.getGeneNum();
         		String x = (gene!=null) ? gene : "Hit";
         		String msg=String.format("Gene# %s has %d minor hits\non self-chromosome\nthat could not be shown", x, cntMinorFail);
         		util.Popup.showWarning(msg);
         	}
         	if (TRC) {
         		if (cntDup>0)    System.out.format("%6d Dups\n", cntDup);
         		if (cntFilter>0) System.out.format("%6d Filter\n", cntFilter);
         		if (cntPlus2>0)  System.out.format("%6d hits with two non-best genes\n", cntPlus2);
         	}
         	return rows;
     	} 
		catch (Exception e) {ErrorReport.print(e, "Reading rows from db");}
		return rows;
	}
	private static void clearStatic() { // everything is static (rows gets recreated at start)
		bSuccess = true;
		if (annoKeys!=null) 	annoKeys.clear();
		if (grpStart!=null) 	grpStart.clear();
		if (grpEnd!=null) 		grpEnd.clear();
		if (spNameList!=null) 	spNameList=null;
		if (spIdxList!=null) 	spIdxList=null;
		
		geneIdxForOlap.clear();
		geneNum = null;				// BUG fix CAS579b was not clearing from last run
		geneOlap = 0;
		cntMinorFail = 0;
		cntPlus2 = 0;
		cntDup = 0; cntFilter = 0; // restart TEST_TRACE counts
	}
	/***********************************************************************
	 * 1. Load and Merge 2nd side from ResultSet
	 *****************************************************************/
	private static void rowsMake(ResultSet rs) {
	try {
		int cntRowNum=0;
		if (isSingle) {
			while (rs.next()) {
				DBdata dd = new DBdata();
				dd.rsLoadSingle(rs, cntRowNum++);
				rows.add(dd); 
				
     			if (cntRowNum%Q.INC ==0) {
     				loadStatus.setText("Processed " + cntRowNum + " rows...");
     				if (tablePanel.bStopped) {rs.close(); bSuccess=false; return;}
     			}
			}
			rs.close(); 
			return;
		}
		// two records for each hit, for each side of pair; block, hitIdx, etc are the same, only anno diff
		HashMap <String, DBdata> hitRowMap = new HashMap <String, DBdata> (); // for key lookup
		
     	while (rs.next()) {
     		int hitIdx = rs.getInt(Q.HITIDX);
     		String key = hitIdx+"";
     		if (isIncMinor) {				
     			int a1=rs.getInt(Q.ANNOT1IDX), a2=rs.getInt(Q.ANNOT2IDX), a=rs.getInt(Q.AIDX);
     			boolean b = (a1!=a && a2!=a);
     			if (b) key = hitIdx + ":" + a; // don't let it match with anything else; 
     		}
     		
     		if (!hitRowMap.containsKey(key)) { // first occurrences of hit
     			DBdata dd = new DBdata();
     			dd.rsLoadHit(rs, cntRowNum++);
     			rows.add(dd); 
     			
     			hitRowMap.put(key, dd);
     	
     			if (cntRowNum%Q.INC ==0) {
     				loadStatus.setText("Processed " + cntRowNum + " rows...");
     				if (tablePanel.bStopped) {rs.close(); bSuccess=false; return;}
     			}
     		}
     		else  {							// merge further occurrences of hit
     			DBdata dd = hitRowMap.get(key);
     			dd.rsMergeHit(rs);
     		}
     	}
     	if (isIncMinor) rowsMakeMinor(); // fill in minor rows
		
		rs.close(); 
		if (TRC) Globals.prt("Load rows " + rows.size());
	}
	catch (Exception e) {ErrorReport.print(e, "Reading rows from db"); bSuccess=false;}
	}
	/********************************************************
	 * Transfer gene information for Minor records: Example:
	 * Best   gene X  gene Y   both annotated when two records, X & Y
	 * Other  gene Z  gene Y   only Z annotated because no corresponding record for Y, so get from Best
	 * The other annotIdx=-1 and geneTag='.' as defaults
	 * 
	 * PH.idx PH.annot1_idx  PH.annot2_idx	PA.idx	Gennum
	 * 98  		7670, 			82902   	7670     1358.     1 New Row
     * 98  		7670, 			82902  		82902     664.     2 Enter second
     * 98 		7670, 			82902  		82904     665.  ***2 New row, add second
	 */
	private static void rowsMakeMinor() {
		try {
			// Find full rows
			HashMap <Integer, DBdata> tagMap0 = new HashMap <Integer, DBdata> ();
			HashMap <Integer, DBdata> tagMap1 = new HashMap <Integer, DBdata> ();
			for (DBdata dObj : rows) {
				if (!dObj.geneTag[0].equals(Q.empty) && !dObj.geneTag[1].equals(Q.empty)) {
					tagMap0.put(dObj.annotIdx[0], dObj);
					tagMap1.put(dObj.annotIdx[1], dObj);
				}
			}
	
			// assign 	
			for (DBdata dd : rows) { 
				DBdata ddCp=null;
				int x=0;
				if (dd.annotIdx[0]>0 && dd.geneTag[0].equals(Q.empty)) {
					if (tagMap0.containsKey(dd.annotIdx[0])) {
						ddCp = tagMap0.get(dd.annotIdx[0]);
						x=0;
					}
				}
				else if (dd.annotIdx[1]>0 && dd.geneTag[1].equals(Q.empty)) {
					if (tagMap1.containsKey(dd.annotIdx[1])) {
						ddCp = tagMap1.get(dd.annotIdx[1]);
						x=1;
					}
				}
				if (ddCp==null) continue;
			
				dd.geneTag[x]	=	ddCp.geneTag[x];
				dd.gstart[x] 	= 	ddCp.gstart[x];		
				dd.gend[x] 		=	ddCp.gend[x];
				dd.glen[x] 		= 	ddCp.glen[x];
				dd.gstrand[x] 	= 	ddCp.gstrand[x];
				dd.golap[x] 	= 	ddCp.golap[x];
				dd.annotStr[x] 	=	ddCp.annotStr[x];
				
				TreeSet <Integer> cpAnnoSet = (x==0) ? ddCp.geneSet0 : ddCp.geneSet1;
				for (int an : cpAnnoSet) dd.geneSet0.add(an); // already checked for pseudo
				
				if (qPanel.isOlapAnno_ii()) { //-ii minor/overlap; This got removed at some point, CAS579c
         			if (!geneIdxForOlap.contains(dd.annotIdx[x])) geneIdxForOlap.add(dd.annotIdx[x]); 
				}
			}
		}
		catch (Exception e) {ErrorReport.print(e, "Finish all genes");}
	}
	
	/************************************************************
	 * 2. Rows have been merged, but not finished
	 */
	private static void rowsFilter() {
	try {
		boolean isFilter = (grpStart.size()>0  || geneNum!=null || geneOlap>0 || isAnno
				|| qPanel.isOlapAnno_ii()); // isOlapAnno -ii
		if (!isFilter) return;
			
		int cntRowNum=0;
		Vector <DBdata> filterRows = new Vector <DBdata> ();
		for (DBdata dd : rows) {
			boolean pass = (isSingle) ? dd.passFilterSingle() : dd.passFilterPair();
			if (pass) {
				filterRows.add(dd);	
				cntRowNum++;
				dd.rowNum = cntRowNum;		// renumber
				if (cntRowNum%Q.INC ==0) {
     				loadStatus.setText("Filtered " + cntRowNum + " rows...");
     				if (tablePanel.bStopped) {bSuccess=false; return;}
				}
			}
			else cntFilter++;
		}
		rows.clear();
		rows = filterRows;
	}
	catch (Exception e) {ErrorReport.print(e, "filter rows"); bSuccess=false;}
	}
	/**************************************************************
	 * 3. Finish rows
	 */
	private static void rowsFinish(HashMap<String, String> sumProjMap) {
	try {
		// Fill in other side from DB for geneNum
		if (geneNum!=null) { 			
			DBconn2 dbc = qPanel.getDBC(); 
			boolean isAlgo2 = qPanel.isAlgo2();
	
			for (DBdata dd : rows) {
         		if (!dd.loadGeneFromDB(dbc, isAlgo2)) {bSuccess=false; return;}
			}
     	}
		// Create block string, chromosome strings and make anno keys 	
		int cntRowNum=0;
     	for (DBdata dd : rows) {
     		if (!dd.finishRow()) {bSuccess=false; return;}		
     		
     		cntRowNum++;
			if (cntRowNum%1000 ==0)  {
 				loadStatus.setText("Finished " + cntRowNum + " rows...");
 				if (tablePanel.bStopped) {bSuccess=false; return;}
			}
     	}
     	if (tablePanel.bStopped) {bSuccess=false; return;}
     	
     	// Special processing
     	if (geneNum!=null) { 
     		runGeneNum(rows);
     	}
     	else if (qPanel.isMultiN()) { // Multi - may remove rows and renumber
     		loadStatus.setText("Compute multi-hit genes...");
     		rows = runMultiGene(rows, sumProjMap);
     	}
     	else if (qPanel.isClustN()) { // Clust - may remove rows and renumber
     		loadStatus.setText("Compute Clust and filter...");
     		rows = runClustHits(rows, sumProjMap);
     	}
     	if (tablePanel.bStopped) {bSuccess=false; return;}
	}
	catch (Exception e) {ErrorReport.print(e, "finish rows"); bSuccess=false;}
	}
	
	/***********************************************************************
	 * Initial sort and numbering of rows; should be like TableData.compare
	 * Multi is sorted in ComputeMulti
	 */
	private static void sortRows() {
	try {
		if (qPanel.isGroup()) { // highest precedence CAS577
			Collections.sort(rows, new Comparator<DBdata> () {
				public int compare(DBdata a, DBdata b) {
					String as=a.grpStr, bs=b.grpStr;
					if (as.equals(Q.empty) && !bs.equals(Q.empty)) return 1;
					if (!as.equals(Q.empty) && bs.equals(Q.empty)) return -1;
					if (as.equals(bs)) return a.hitNum-b.hitNum;
					
					int retval=0;
					String [] av = as.split(Q.GROUP); 
					String [] bv = bs.split(Q.GROUP);
					
					for(int x=1; x>=0 && retval == 0; x--) { // copied from TableData
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
					return -retval;	// reverse sort
				}
			});
		}
		else if (qPanel.isCollinear() || qPanel.isBlock()) { // if change here, change in TableData.compare
			Collections.sort(rows, new Comparator<DBdata> () {
				public int compare(DBdata a, DBdata b) {
					String as="", bs="";
					if (qPanel.isCollinear()) 	{as=a.collinearStr; bs=b.collinearStr;} // precedence to collinear; CAS577
					else 						{as=a.blockStr;     bs=b.blockStr;}
					
					if (as.equals(Q.empty) && !bs.equals(Q.empty)) return 1;
					if (!as.equals(Q.empty) && bs.equals(Q.empty)) return -1;
					if (as.equals(bs)) return a.hitNum-b.hitNum;
					
					int retval=0;
					String [] av = as.split(Q.SDOT); 
					String [] bv = bs.split(Q.SDOT); 
					int n = Math.min(av.length, bv.length);
					boolean bCS = qPanel.isCollinear();
					
					for(int x=0; x<n && retval == 0; x++) { // copied from TableData
						if (bCS && x==2) continue; 				// ignore size; cosets numbered by size CAS577
						
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
					return -retval;	// reverse sort
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
		// Number results for display
		int rnum=1;
		for (DBdata r : rows) r.rowNum = rnum++;
	}
	catch (Exception e) {ErrorReport.print(e, "Sort rows");}
	}

	/*****************************************************
	 * 1. spNameList, spIdxList: projList - Create species columns in order of projects 
	 * 2. annoKeys: annoColumns - contains spIdx::keyword entries for all projects. Passed in from TableMainPanel.
	 * 3. grpStart, grpEnd: grpCoords - Set up coordinates from the interface for filter
	 */
	private static void initSpStructs(Vector <Mproject> projList, String [] annoColumns, HashMap <Integer, String> grpCoords) {
		try {
			int numSp = projList.size();
			
			// 1. Species columns in order of projects
			spNameList = new String [numSp];
			spIdxList = new int [numSp];
			for (int p=0; p<numSp; p++) {
				spNameList[p] = projList.get(p).getDisplayName();
				spIdxList[p] = projList.get(p).getIdx(); 
			}
			
			// 2. The keywords should be in order the way they are to be displayed
			annoKeys = new HashMap <Integer, String[]>  ();
			
			// count number of keys per species
			HashMap <String, Integer> cntSpKeys = new HashMap <String, Integer> ();
			for (int i=0; i<annoColumns.length; i++) {
				String sp = annoColumns[i].split(Q.delim)[0];			
				if (cntSpKeys.containsKey(sp)) 	cntSpKeys.put(sp, cntSpKeys.get(sp)+1);
				else 							cntSpKeys.put(sp, 1);
			}	
			
			// Make array of keywords for each species; used in formatRowForTbl
			HashMap <String, Integer> spName2spIdx = spPanel.getSpName2spIdx();
			for (String spName : cntSpKeys.keySet()) {
				int spidx = spName2spIdx.get(spName);

				int cnt = cntSpKeys.get(spName);
				String [] keys = new String [cnt];
				
				for (int i=0, k=0; i<annoColumns.length; i++) {
					String sp = annoColumns[i].split(Q.delim)[0];
					if (sp.equals(spName)) {
						keys[k++] =  annoColumns[i].split(Q.delim)[1];
					}
				}
				annoKeys.put(spidx, keys);
				if (isSelf) break;			
			}
			
			// 3. grpCoords.put(index, start + Q.delim2 + stop); created in QueryPanel
			grpStart = new HashMap <Integer, Integer> ();
			grpEnd   = new HashMap <Integer, Integer> ();
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
		catch (Exception e) {ErrorReport.print(e, "Make species lits");}
	}
	/*************************************************
	 * Add other geneTag of query; put results from Gene# in groups; 
	 */
	private static void runGeneNum(Vector <DBdata> rows) {
	try {
	/* Compute group  */
		String [] tok = geneNum.split(Q.SDOT);
		int genenum = Utilities.getInt(tok[0]); 
		if (genenum == -1) return;
		
		HashMap <String, Integer> geneTag = new HashMap <String, Integer>  ();
		String key;
		
		// Compute sz from: Tag:chrIdx:spIdx counts
		for (DBdata dd : rows) {
			for (int i=0; i<2; i++) {
				tok = dd.geneTag[i].split(Q.SDOT);
				if (tok.length<2 || tok[1].equals(Q.dash)) continue;
				
				int num = Utilities.getInt(tok[1]); 	// chr.tag.[suffix]
			
				if (num == genenum) {
					int j = (i==0) ? 1 : 0;
					key = dd.chrIdx[i] + ":" + dd.spIdx[i] + ":" + dd.chrIdx[j] + ":" + dd.spIdx[j];
					if (geneTag.containsKey(key)) geneTag.put(key, geneTag.get(key)+1);
					else 						  geneTag.put(key, 1);
				}
			}
     	}
		
		// Assign groups
		HashMap<String, Integer> grpMap = new HashMap<String, Integer>();  // geneTag, grpN
		int numFam=1, sz=0;
		for (DBdata dd : rows) { 
			for (int i=0; i<2; i++) {
				tok = dd.geneTag[i].split(Q.SDOT);
				if (tok.length<2 || tok[1].equals("-")) continue;
				
				int num = Utilities.getInt(tok[1]); 	// chr.tag.[suffix]
				if (num!=genenum) continue;
				
				int j = (i==0) ? 1 : 0;
				key = dd.chrIdx[i] + ":" + dd.spIdx[i] + ":" + dd.chrIdx[j] + ":" + dd.spIdx[j];
				
				if (geneTag.containsKey(key)) {
					int fn;
					if (grpMap.containsKey(key)) fn = grpMap.get(key);
					else {
						fn = numFam++;
						grpMap.put(key, fn);
					}
					sz = geneTag.get(key);
					dd.setGroup(fn,sz);
					break; // if i=0, no need to do i=1; if both have genenum, 1st gets used
				}
				else symap.Globals.eprt("SyMAP error: No entry for " + key);
			}
		}
	}
	catch (Exception e) {ErrorReport.print(e, "make Gene# groups");}
	}
	
	// ComputeMulti; compute does the rows, Clust and PgFSize
	private static Vector <DBdata> runMultiGene(Vector <DBdata> rows, HashMap<String, String> projMap) {
		HashSet<Integer> selGrp = spPanel.getSelectGrpIdxSet();
		ComputeMulti mobj = new ComputeMulti(rows, qPanel, loadStatus, selGrp, spIdxList, projMap);
		return mobj.compute();	
	}
	
	////////////////////////////////////////////////////////////////////////////
	// statsProjMap shows in statistics at end of each projects row
	private static Vector <DBdata> runClustHits(Vector <DBdata> rows, HashMap<String, String> statsProjMap ) {
		try {
			HashMap<Integer,Integer> hitIdx2Grp = new HashMap<Integer,Integer>(); // hitIdx, Clust
    		HashMap<Integer,Integer> grpSizes = new HashMap<Integer,Integer>();   // Clust, size
    		
			if (!Globals.bQueryPgeneF)  
				new ComputeClust(rows,  qPanel, loadStatus, hitIdx2Grp,  grpSizes); 
			else {
				new ComputePgeneF(rows, qPanel, loadStatus, hitIdx2Grp,  grpSizes, statsProjMap); 
			}	 	              
			
			hitIdx2Grp.put(0, 0); // for the annots that didn't go into a group
    		grpSizes.put(0, 0);
    		
    		// Create new set of rows with groups assigned
    		int rowNum=1;
    		Vector <DBdata> rowsForDisplay = new Vector <DBdata> ();
    		for (DBdata dd : rows) {
    			if (!hitIdx2Grp.containsKey(dd.hitIdx)) continue;
    		
    			dd.rowNum = rowNum;
    			rowNum++;
    			
    			int grp  = hitIdx2Grp.get(dd.hitIdx);
    			int sz   = grpSizes.containsKey(grp) ? grpSizes.get(grp) : 0; 
    			dd.setGroup(grp, sz);
    			
    			rowsForDisplay.add(dd);
    			
    			if (rowNum%Q.INC ==0) 
    				loadStatus.setText("Load " + rowNum + " rows...");
    		}
			rows.clear();
			return rowsForDisplay;
		}
		catch (Exception e) {ErrorReport.print(e, "Making cluster table"); return null;}
	}
	// Create Unique Gene Counts for Stats (see SummaryPanel)
	private static void makeSumCnts(HashMap <Integer, Integer> geneCntMap) { // spIdx, num-unique-genes
		try {
         	if (isSelf) { // 2 species only
         		HashSet <Integer> anno1 = new HashSet <Integer> ();
         		HashSet <Integer> anno2 = new HashSet <Integer> ();
         		for (DBdata dd : rows) {
             		for (int x=0; x<2; x++) {
             			if (dd.annotIdx[x]>0 && !dd.geneTag[x].endsWith(Q.pseudo)) {
             				HashSet <Integer> anno = (x==0) ? anno1 : anno2;
             				if (!anno.contains(dd.annotIdx[x])) anno.add(dd.annotIdx[x]);
             			}
             		}
         		}
         		int spIdx = spIdxList[0];
         		geneCntMap.put(spIdx, anno1.size());
         		geneCntMap.put(spIdx+1, anno2.size()); //SummaryPanel expects this
         		return;
         	}
         	
         	// account for >2 species
         	HashMap <Integer, Integer> numPerGene = new HashMap <Integer, Integer>  ();   // AIDX, assigned num
    		HashMap <Integer, Integer> lastNumPerSp = new HashMap <Integer, Integer>  (); // SpIdx, lastNum

         	for (int spIdx : spIdxList) lastNumPerSp.put(spIdx, 0);
         	
         	for (DBdata dd : rows) {
         		for (int x=0; x<2; x++) {
         	        TreeSet <Integer> anno = (x==0) ? dd.geneSet0 : dd.geneSet1;
	         		for (int aidx : anno) {
	         			int num=0;
	         			int spx = dd.spIdx[x];
	         			if (!numPerGene.containsKey(aidx)) {
	         				num = lastNumPerSp.get(spx);   		// new number
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
	 */
	private DBdata() {}
	
	// called from rsMakeRows
	private void rsLoadHit(ResultSet rs, int row) {
		try {
			rowNum = row;
			
			// from pseudo_hits; values  will be the same for either of the hit ends (order does not matter)
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
			
			if (hcnt==0) 	hcnt=1;				// makes more sense to be 1 merge instead of empty
			String s =		rs.getString(Q.HST);
			hst = 			s; 	                // NOTE: use 's' to show the actual value
			hscore	=		rs.getInt(Q.HSCORE);
			htype	=		rs.getString(Q.HTYPE); 
				
			int annoGrpIdx = rs.getInt(Q.AGIDX);	// grpIdx, so unique; same as chrIdx[0|1]
			if (annoGrpIdx==0) return; 				// no anno, no pseudo gene num
				
		// Get anno for one side; either side could come first
		// These are from pseudo_annot
			int x = (annoGrpIdx==chrIdx[0]) ? 0 : 1; 
			int annoIdx  = rs.getInt(Q.AIDX);	
			String tag = Utilities.getGenenumFromDBtag(rs.getString(Q.AGENE));// // Remove '2 (9 1306)' or '2.b (9 1306)'
			
			if (isSelf && chrIdx[0]==chrIdx[1]) { 
				if  (annoIdx==annotIdx[0]) x = 0;
				else if  (annoIdx==annotIdx[1]) x = 1;
				else {
					cntMinorFail++;
					return; // Can't do minors on same chr
				}
			}
			if (annoIdx!=annotIdx[0] && annoIdx!=annotIdx[1]) { // All annot_idx,hit_idx loaded; make sure best
				if (!isIncMinor) return;  // is Minor, but not being shown, okay to fill in first part; comes from pseudo_hits
					
				annotIdx[x] = annoIdx;		  // this is a new row; see key 
				htype = "--";								
				tag = tag + " " + Q.minor;    // will get best in finishAllMinor
			}
			
			gstart[x] =		rs.getInt(Q.ASTART);		
			gend[x] =		rs.getInt(Q.AEND);
			glen[x] = 		Math.abs(gend[x]-gstart[x])+1;
			gstrand[x] = 	rs.getString(Q.ASTRAND); 
			golap[x] = 		rs.getInt(Q.AOLAP);		// from pseudo_hit_annot
			
			annotStr[x] =	rs.getString(Q.ANAME);
			geneTag[x] = 	tag; 		
		
			if (!tag.endsWith(Q.pseudo) && annoIdx>0) {
				if (x==0) geneSet0.add(annoIdx); 		// multiple anno's for same hit
				else      geneSet1.add(annoIdx);
			}
		}
		catch (Exception e) {ErrorReport.die(e, "Read hit record");}
	}
	/***************************************
	 * If there is no anno for one side of the hit, there will be no merged record
	 * called from rsMakeRows
	 */
	private void rsMergeHit(ResultSet rs) {
		try {
			int annoGrpIdx = rs.getInt(Q.AGIDX); 	// will not be zero because no record if anno not on other side
			int annoIdx = rs.getInt(Q.AIDX); // PA.idx
			String tag = Utilities.getGenenumFromDBtag(rs.getString(Q.AGENE));// convert here for AllGenes
			
			int x = (annoGrpIdx==chrIdx[0]) ? 0 : 1; // goes with 1st or 2nd half of hit
			if (isSelf && chrIdx[0]==chrIdx[1]) {
				if (annoIdx==annotIdx[0]) x = 0;
				else if (annoIdx==annotIdx[1]) x = 1;
				else {
					cntMinorFail++;
					return; // Can't do minors on same chr; could search for Gene# or Hit# and get no results when there is a minor
				}
			}
		
			if (annoIdx!=annotIdx[0] && annoIdx!=annotIdx[1]) { // PH.annot1_idx and PH.annot2_idx
				if (!isIncMinor) return; // is Minor, but not being included; ignore
				
				annotIdx[x] = annoIdx;	 	 // should not happen because of key
				tag = tag + Q.minor+Q.minor; // hit overlaps at least two genes on both sides
				cntPlus2++;		
				
				int y = (x==0) ? 1 : 0;
				String chry = (chrIdx[y]>0) ? spPanel.getChrNumFromChrIdx(chrIdx[y]) : "?";
				Globals.prt(spPanel.getChrNumFromChrIdx(chrIdx[x]) + "." + tag 
						+ " merge " + spPanel.getChrNumFromChrIdx(chrIdx[x]) + "." + geneTag[x] + chry + "." + geneTag[y] );
			}
			
			gstart[x] =		rs.getInt(Q.ASTART);	
			gend[x] =		rs.getInt(Q.AEND);
			glen[x] = 		Math.abs(gend[x]-gstart[x])+1;
			gstrand[x] = 	rs.getString(Q.ASTRAND); 
			golap[x] = 		rs.getInt(Q.AOLAP);
			geneTag[x] = 	tag; 					
			
			int sz = (x==0) ? geneSet0.size() : geneSet1.size();
			if (sz>0)  { 					
				annotStr[x]	+=	";" + rs.getString(Q.ANAME);
				cntDup++; 
			}  
			else annotStr[x] =	rs.getString(Q.ANAME);
				
			if (!tag.endsWith(Q.pseudo) && annoIdx>0) { 
				if (x==0) geneSet0.add(annoIdx); // multiple anno's for same hit
				else      geneSet1.add(annoIdx);
			}
		}
		catch (Exception e) {ErrorReport.print(e, "Merge");}
	}
	/***************************************************************/
	private void rsLoadSingle(ResultSet rs, int row) {
		try {
			this.rowNum = row;
			
			geneSet0.add(rs.getInt(Q.AIDX));
			annotStr[0]	=	rs.getString(Q.ANAME);
			chrIdx[0] = 	rs.getInt(Q.AGIDX);
			gstart[0] =		rs.getInt(Q.ASTART);		// Gene start to chromosome
			gend[0] =		rs.getInt(Q.AEND);
			glen[0] = 		Math.abs(gend[0]-gstart[0])+1;
			gstrand[0] = 	rs.getString(Q.ASTRAND); 
			geneTag[0] = 	rs.getString(Q.AGENE);   
			geneTag[0] = 	Utilities.getGenenumFromDBtag(geneTag[0]); 
			
			numHits[0] = 	rs.getInt(Q.ANUMHITS);   
		}
		catch (Exception e) {ErrorReport.print(e, "Read single");}
	}
	/*****************************************************
	 * Get other side of hit.  					
	 * In QueryPanel, search for actual genenum or annotation string; that only returns one half of hit 
	 *		but is fast and allows the de-activation of a species
	 ***********************************************************************/
	private boolean loadGeneFromDB(DBconn2 dbc, boolean isAlgo2) {
	try {
		int idx=-1;
		
		if (geneTag[0].endsWith(Q.pseudo) || geneTag[0].equals(Q.empty)) idx = 0; 
		else if (geneTag[1].endsWith(Q.pseudo)|| geneTag[1].equals(Q.empty)) idx = 1;
		else return true;
		
		if (annotIdx[idx]==0) return true; // one side
		
		String sql = "SELECT PHA.exlap, PHA.olap, PA.start, PA.end, PA.strand, PA.tag, PA.name  "
				+ " from pseudo_hits_annot AS PHA"
				+ " LEFT JOIN pseudo_annot AS PA  ON PHA.annot_idx = PA.idx"
				+ " where PHA.hit_idx = " + hitIdx + " and PA.idx=" + annotIdx[idx];
		
		ResultSet rs = dbc.executeQuery(sql);
		if (rs.next() ) {
			golap[idx] = isAlgo2 ? rs.getInt(1) : rs.getInt(2);
			gstart[idx] = rs.getInt(3);
			gend[idx] 	= rs.getInt(4);
			glen[idx]	= gend[idx]-gstart[idx] +1;
			gstrand[idx] = rs.getString(5);
			geneTag[idx] = Utilities.getGenenumFromDBtag(rs.getString(6));
			annotStr[idx] = rs.getString(7);
		}
		else Globals.tprt("Cannot parse gene ");
		
		return true;
	}
	catch (Exception e) {ErrorReport.print(e, "Merge gene"); return false;}
	}
	
	/*********************************************
	 * Create block string, chromosome strings and make anno keys 
	 */
	private boolean finishRow() {
	try {
		int n = (isSingle) ? 1 : 2;
		
		for (int x=0; x<n; x++) {
			chrNum[x] =  spPanel.getChrNumFromChrIdx(chrIdx[x]); 
			spName[x] =  spPanel.getSpNameFromChrIdx(chrIdx[x]);
			if (isSingle) spIdx[x] = spPanel.getSpIdxFromChrIdx(chrIdx[x]); 
			
			String chr = (chrNum[x].startsWith("0") && chrNum[x].length()>1) ? // remove leading 0's
					     chrNum[x].substring(1) : chrNum[x];
			
			if (!geneTag[x].equals(Q.empty)) geneTag[x] = chr + "." + geneTag[x];  
			else 							 geneTag[x] = chr + "." + Q.pseudo; // indicates hit but no gene; parsed in TmpRowData/UtilReport
			
			if (blockNum>0) {
				blockStr = Utilities.blockStr(chrNum[0], chrNum[1], blockNum); 
			}
			if (runSize>0)  {
				collinearStr = Utilities.blockStr(chrNum[0], chrNum[1], runSize); 
				if (runNum>0)  collinearStr += Q.COSET + runNum; 					  
			}
			
			/******* Anno Keys ******************/
			if (!annoKeys.containsKey(spIdx[x])) { // spIdx, keys in order
				continue; // not annotated
			}
			String [] keyList = annoKeys.get(spIdx[x]);
			int cntKey = keyList.length;
			
			String [] valList = new String [cntKey]; // if n keys, then n values
			for (int i=0; i<cntKey; i++) valList[i] = Q.NOANNOVAL;    // CAS579c (see Release Weird bug fix) 
	
			if (!annotStr[x].equals("")) { 
				String[] annoList = annotStr[x].split(";"); // key1=val1;key2=val2....
				
				for (String tuple : annoList) { 
					String [] words = tuple.trim().split("[\\s,=]",2);
					if (words.length == 1) continue;
					
					String key = words[0].trim();
					if (key.endsWith("="))	key = key.substring(0,key.length() - 1);
					if (key.equals("")) continue;
					
					String val = words[1].trim();
					if (val.startsWith("=")) val = val.substring(1);
					if (val.trim().equals("")) val = Q.NOANNOVAL; // key={empty}
					
					for (int k=0; k<cntKey; k++) {
						if (keyList[k].equals(key)) {
							valList[k] = valList[k].equals(Q.NOANNOVAL) 
									? val : (valList[k] + ";" + val); // repeated keywords
							break;
						}
					}
				}
				if (keyList[cntKey-1].equals(Q.All_Anno))				
					valList[cntKey-1] = annotStr[x];
			}
			annoVals.put(x, valList); // annoVals is local to the row, it can have a 0 and 1 entry; see formatRowForTbl
		}	
		return true;
		
	} catch (Exception e) {ErrorReport.print(e, "Reading rows"); return false;}
	}
	
	/*********************************************************
	 * Row Filters: The locations and hit gene filters are done here instead of MySQL where statement
	 * This has to be performed after all hits are read for the isOneAnno and isOlapAnno
	 */
	private boolean passFilterPair() {
		try {
			// GeneNum: Chr is the only other filter, which happened in SQL; but need to make sure geneNum on correct chr
			if (grpStart.size()>0) { // put here so work for the rest of filters
				for (int gidx : grpStart.keySet()) {
					int sf = grpStart.get(gidx); 
					int ef = grpEnd.get(gidx);
					
					for (int x=0; x<2; x++) { // using hit coords
						if (chrIdx[x]==gidx) {
							if (sf > 0 && hstart[x] < sf)return false;  
							if (ef > 0 && hend[x] > ef)  return false; 
						}
					}
				}
			}
			if (geneOlap>0) { // Not checked in MySQL because will only return one-half, which looks the same as pseudo
				if (qPanel.isGeneOlapOr()) { // CAS579c was checking that it is a gene, where pseudos should be removed to
					if (golap[0]<geneOlap && golap[1]<geneOlap) return false;
				}
				else {
					if (golap[0]<geneOlap || golap[1]<geneOlap) return false;
				}
			}
			// CAS579b was checking for select grpIdxOnly (selected chromosome), but they are filter in MySQL query
			
			// when doing anno search as query, returned hits without anno if they overlap
			if (isAnno) return passFilterAnno();
			
			// cannot have Anno and geneNum
			if (geneNum!=null) { // genenum already checked in mysql; this is for suffix
				String tag0 = geneTag[0].replace(Q.minor,"").trim();	
				String tag1 = geneTag[1].replace(Q.minor,"").trim();
				
				if (geneNum.endsWith(".")) geneNum = geneNum.substring(0, geneNum.length()-1);
				
				if (!geneNum.contains(".")) { // e.g. 16.a and 16.b and inputNum=16
					tag0 = Utilities.getGenenumIntOnly(tag0);
					tag1 = Utilities.getGenenumIntOnly(tag1);
				}
				
				if (tag0!="-" && tag0.equals(geneNum)) return true;	
				if (tag1!="-" && tag1.equals(geneNum)) return true;
					
				return false;
			}
			
			if (qPanel.isOlapAnno_ii()) { // for -ii
				boolean b = geneIdxForOlap.contains(annotIdx[0]) || geneIdxForOlap.contains(annotIdx[1]);
				if (qPanel.isMinorOlap) // otherwise, overlapping
					return  b && (geneTag[0].endsWith(Q.minor) || geneTag[1].endsWith(Q.minor));
				return b;
			}
			return true;
			
		} catch (Exception e) {ErrorReport.print(e, "Reading rows"); return false;}
	}
	// Specific to singles
	private boolean passFilterSingle() {
		try {
			if (isAnno && !passFilterAnno()) return false; // if passes, still need to check start/end
			
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
			return true;
			
		} catch (Exception e) {ErrorReport.print(e, "Reading rows"); return false;}
	}
	// check isAnno before calling (when doing anno search as query, returned hits without anno if they overlap)
    private boolean passFilterAnno() {
		String anno = qPanel.getAnnoTxt().toLowerCase();
		boolean b0=true, b1=true;
		if (grpIdxForAnno!=null) { // null means all chromosomes, otherwise, hashSet of valid
			b0 = grpIdxForAnno.contains(chrIdx[0]);
			b1 = grpIdxForAnno.contains(chrIdx[1]);
		}
		if (b0 && annotStr[0].toLowerCase().contains(anno)) return true;
		if (b1 && annotStr[1].toLowerCase().contains(anno)) return true;
			
		return false;
    }
	/*********************************************************
	 * Called if ComputeClust or runGeneGroups
	 */
	protected void setGroup(int grpN, int grpSz) {
		this.grpStr = grpSz + Q.GROUP + grpN; 
		this.grpN = grpN;				  // needed for ComputeMulti
	}
	
	/*************************************************
	 * XXX TableData.addRowsWithProgress: returns row formated for table.
	 */
	protected Vector <Object> formatRowForTbl() {
	try {
		Vector<Object> row = new Vector<Object>();

		// general
		row.add(rowNum);		
		
		if (!isSingle) {
			if (blockNum<=0)    row.add(Q.empty);  else row.add(blockStr);
			if (blockScore<=0)  row.add(Q.empty);  else row.add(blockScore);
			if (runSize<0)      row.add(Q.empty);  else row.add(collinearStr);
			row.add(grpStr);	
			
			if (hitNum<=0)      row.add(hitIdx);   else row.add(hitNum);
			if (hscore<=0)		row.add(Q.empty);  else row.add(hscore); 
			if (pid<=0)			row.add(Q.empty);  else row.add(pid);
			if (psim<=0)		row.add(Q.empty);  else row.add(psim);
			if (hcnt<=0)		row.add(Q.empty);  else row.add(hcnt);
			if (hst.contentEquals(""))	row.add(Q.empty);  else row.add(hst); 
			if (htype.contentEquals("")) row.add(Q.empty);  else row.add(htype); 
		}
		// chr, start, end, strand, geneTag per pair i
		int cntCol = TableColumns.getSpColumnCount(isSingle);
		for (int i=0; i<spIdxList.length; i++) { // add columns, could be from any of the N species
			int x=-1;
			if (isSelf) x = i;					 
			else if (spIdx[0] == spIdxList[i]) x=0;
			else if (spIdx[1] == spIdxList[i]) x=1;
			
			if (x>=0) {
				row.add(geneTag[x]); 
				if (!isSingle) {
					if (golap[x]<0)  	row.add(Q.empty); else row.add(golap[x]); // zero is okay
				}
				else row.add(numHits[x]); 
				
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
			}
			else {
				for (int j=0; j<cntCol; j++) row.add(Q.empty);
			}
		}
		// annotation
		for (int i=0; i<spIdxList.length; i++) {
			int x = -1;
			if (isSelf) x = i;
			else if (spIdx[0] == spIdxList[i]) x = 0;
			else if (spIdx[1] == spIdxList[i]) x = 1;
			
			if (x>=0) {
				if (annoVals.containsKey(x)) {
					String [] vals = annoVals.get(x);
					for (String v : vals) {
						if (v==null || v.equals("")) v=Q.empty;
						row.add(v);
					}
				}
				// otherwise no anno, no space holders
			}
			else { // there are more than 2 species, so this fills in the anno columns as blank
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
	protected String getChrNum(int x) 	{return chrNum[x];} // For ComputeClust CAS578
	protected int 	 getHitIdx() 		{return hitIdx;}
	protected int [] getCoords() {
		int [] x = {hstart[0], hend[0], hstart[1], hend[1]}; 
		return x;
	}
	protected boolean hasGene(int x) 	{
		if (x==0)   return geneSet0.size()>0;
		else        return geneSet1.size()>0;
	}
	
	protected boolean hasBlock() 	{return blockNum>0;}
	protected boolean hasCollinear() {return !collinearStr.isEmpty() && !collinearStr.equals(Q.empty);} 
	protected boolean hasGroup() {return !grpStr.isEmpty() && !grpStr.equals(Q.empty);} // for GrpSz.Grp#
	
	protected Object copyRow() { // ComputeMulti: clone only copies primitive, might as well just copy
		DBdata nObj = new DBdata();
		nObj.hitIdx = hitIdx; nObj.hitNum = hitNum;
		nObj.pid = pid; nObj.psim = psim; nObj.hcnt=hcnt; 		
		nObj.hst=hst;	nObj.hscore = hscore; nObj.htype = htype;			
		nObj.blockNum = blockNum; nObj.runNum = runNum;
		nObj.blockScore=blockScore; nObj.runSize=runSize; 
		nObj.blockStr = blockStr; nObj.collinearStr = collinearStr;
		nObj.grpStr = grpStr;
		
		nObj.geneSet0 = new TreeSet <Integer> ();  for (int x : this.geneSet0) nObj.geneSet0.add(x);
		nObj.geneSet1 = new TreeSet <Integer> ();  for (int x : this.geneSet0) nObj.geneSet1.add(x);
		
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
		nObj.rowNum = -1; // set unique
		
		return nObj;
	}
	public String toString() {
		return String.format("hit# %,5d   sp %d %d   chr %3s %3s   tag %10s %10s  idx %,6d %,6d  grp# %s", 
				hitNum, spIdx[0], spIdx[1], chrNum[0], chrNum[1], geneTag[0], geneTag[1], 
				annotIdx[0], annotIdx[1], grpStr);
	}
	/******************************************************
	 * Class variables -- protected -- accessed in ComputeClust and ComputeMulti
	 */
// hit
	private int hitNum = -1;
	private int pid = -1, psim = -1, hcnt=-1; 		
	private String hst="";							// hit strand
	private int hscore = -1;						// 
	protected String htype = "";					// EE, EI, IE, nn, etc
		
	private int [] gstart 	= {Q.iNoVal, Q.iNoVal};  
	private int [] gend 	= {Q.iNoVal, Q.iNoVal}; 
	private int [] glen 	= {Q.iNoVal, Q.iNoVal};  
	private String [] gstrand = {Q.empty, Q.empty};
	protected int [] hstart = {Q.iNoVal, Q.iNoVal};  
	protected int [] hend 	= {Q.iNoVal, Q.iNoVal}; 
	private int [] hlen 	= {Q.iNoVal, Q.iNoVal};  	
	private int [] golap 	= {Q.iNoVal, Q.iNoVal};  	
	protected String [] geneTag = {Q.empty, Q.empty}; 	// chr.gene#.suffix
	private int [] numHits 	= {Q.iNoVal, Q.iNoVal}; 	
	
// block, collinear
	protected int blockNum = -1, runNum = -1;// run is for collinear
	private int blockScore=-1, runSize=-1; 
	protected String blockStr = Q.empty, collinearStr = Q.empty; // SummaryPanel needs this
	
// Other
	protected int rowNum=0;			// this rowNum is not used in table, but is a placeholder and used in debugging
	protected String grpStr = Q.empty;	// ComputeClust && ComputeMulti
	
	protected String [] spName= {Q.empty, Q.empty};	// ComputeClust uses
	protected String [] chrNum= {Q.empty, Q.empty};
	
	// AnnoStr broken down into keyword values in finish()
	private HashMap <Integer, String[]> annoVals = new HashMap <Integer, String[]>  (); // 0,1, values in order

// not columns in table, needed for filters
	protected int hitIdx = -1;
	protected int [] annotIdx = {Q.iNoVal, Q.iNoVal}; // is PA.idx when isMinor	
	protected int [] spIdx 	= {Q.iNoVal, Q.iNoVal};	   
	protected int [] chrIdx = {Q.iNoVal, Q.iNoVal};	
	
	private TreeSet <Integer> geneSet0 = new TreeSet <Integer> (); // not same order as annotStr
	private TreeSet <Integer> geneSet1 = new TreeSet <Integer> (); // used to count genes and concat annos, i.e. multi annos per hit
	
	private String [] annotStr =	 {"", ""}; 					   // broken down into annoVals columns
	protected int grpN = -1;									   // used in ComputeMulti 
}
