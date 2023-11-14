package symapQuery;

import java.sql.ResultSet;
import java.util.Vector;
import java.util.TreeSet;
import java.util.HashMap;

import javax.swing.JTextField;

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
 * CAS504 this file replaced the previous approach to parsing the sql results
 * CAS547 add EveryPlus
 */
public class DBdata {
	private static String [] spNameList; 				// species Names
	private static int [] 	spIdxList; 					// species spIdx in same order
	private static HashMap <Integer, String[]> annoKeys;// spIdx, keys in order
	private static HashMap <Integer, Integer> grpStart; // grpIdx, start,end
	private static HashMap <Integer, Integer> grpEnd;   // grpIdx, start,end
	private static int grpIdxOnly;						// grpIdx, geneNum only
	
	private static QueryPanel qPanel;
	private static SpeciesSelectPanel spPanel;
	private static JTextField loadStatus;
	private static boolean isSingle=false, isSingleGenes = false, isEveryPlus = true; // CAS547 
	
	private static int cntDup=0, cntFilter=0, cntPgeneF=0; // TEST_TRACE
	private static int cntPlus2=0;	// CAS547 check for possible pre-v547
	
	protected static Vector <DBdata> loadRowsFromDB(
			ResultSet rs, 							// Result set, ready to be parsed
			Vector<Mproject> projList,				// species in order displayed
			QueryPanel theQueryPanel,  				// for species information	
			String [] annoColumns, 					// Species\nkeyword array in order displayed
			JTextField loadTextField,   			// write progress
			HashMap <Integer, Integer> geneCntMap, 	// output variable of spIdx annotidx counts
			HashMap<String,Integer> proj2regions)  	// PgeneF ouput
	{
		clear();
		qPanel = 		theQueryPanel;
		spPanel = 		qPanel.getSpeciesPanel();
		loadStatus = 	loadTextField;
		
		isSingle 		= qPanel.isSingle();
		isSingleGenes 	= qPanel.isSingleGenes();
		isEveryPlus 		= qPanel.isEveryPlusAnno();
		
		if (qPanel.isGeneNum()) grpIdxOnly = qPanel.getGrp();
		
		if (Q.TEST_TRACE) {
			System.out.println(projList.get(0).getIdx() + " " + projList.get(0).getDBName() + "     "
					+ projList.get(1).getIdx() + " " + projList.get(1).getDBName());
		}
		
		cntPlus2=0; cntPgeneF=0;
		cntDup=0; cntFilter=0; // restart TEST_TRACE counts
		
		makeSpLists(projList);
		makeAnnoKeys(annoColumns);
		makeGrpLoc(qPanel.getGrpCoords());
		
		// CAS543 remove isEitherAnno and isBothAnno (can do in QueryPanel), add isAnnoTxt
		boolean isFilter = (qPanel.isOneAnno() || grpStart.size()>0 ||  qPanel.isAnnoTxt() || qPanel.isGeneNum());
				
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
			else if (isEveryPlus) {
				HashMap <String, DBdata> hitMap = new HashMap <String, DBdata> ();
				
	         	while (rs.next()) {
	         		int hitIdx = rs.getInt(Q.HITIDX);
	         		int anno1 = rs.getInt(Q.PHAANNOT1IDX);
	         		int anno2 = rs.getInt(Q.PHAANNOT2IDX);
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
        
         // PgeneF - may remove rows and renumber
         	if (qPanel.isPgeneF()) {
         		loadStatus.setText("Compute PgeneF and filter");
         		rows = runPgeneF(rows, proj2regions);
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
			int found=0;
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
				dObj.annotStr[x] =	cpObj.annotStr[x];
				
				if (x==0) {
					for (int an : cpObj.annoSet0) dObj.annoSet0.add(an);
				}
				else    {
					for (int an : cpObj.annoSet1) dObj.annoSet1.add(an);
				}  
				found++;
			}
			if (Q.TEST_TRACE) {
				System.out.format("%6d annotated added to non-best", found);
				if (noAnno.size()>0) {
					System.out.println("Gene hits with missing anno: " + noAnno.size());
					int i=0;
					for (DBdata dObj : noAnno) {
						i++;
						if (i<10) 
							System.out.println("Hit " + dObj.hitNum + " Tag1: " + dObj.geneTag[0] + " Tag2: " + dObj.geneTag[1]
								+ "  "+ dObj.annotIdx[0] + "," + dObj.annotIdx[1]);
					}
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
	private static Vector <DBdata> runPgeneF(Vector <DBdata> rows, HashMap<String,Integer> proj2regions ) {
		try {
			HashMap<Integer,Integer> hitIdx2Grp = new HashMap<Integer,Integer>(); // PgeneF
    			HashMap<Integer,Integer> grpSizes = new HashMap<Integer,Integer>();   // PgFSize
    		
    			new ComputePgeneF(rows,  qPanel, loadStatus,
    				hitIdx2Grp,  grpSizes, proj2regions); // output data structures
    		    		 	
    			hitIdx2Grp.put(0, 0); // for the annots that didn't go into a group
        		grpSizes.put(0, 0);
        		
        		int rowNum=1;
        		Vector <DBdata> rowsForDisplay = new Vector <DBdata> ();
        		for (DBdata dd : rows) {
        			if (!hitIdx2Grp.containsKey(dd.hitIdx)) {
        				cntPgeneF++;
        				if (cntPgeneF<3 && Q.TEST_TRACE) 
        					System.out.println("Not in hitIdx2Grp: " + dd.hitIdx);
        				continue;
        			}
        			
        			dd.rowNum = rowNum;
        			rowNum++;
        			
        			int pgenef = hitIdx2Grp.get(dd.hitIdx);
        			int pgfsize = grpSizes.get(pgenef);
        			dd.setPgeneF(pgenef, pgfsize);
        			
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
			hst = 			(s.contains("+") && s.contains("-")) ? "!=" : "=";
			hscore	=		rs.getInt(Q.HSCORE);
			htype	=		rs.getString(Q.HTYPE);
			
			int annoGrpIdx = rs.getInt(Q.AGIDX);	// grpIdx, so unique; same as chrIdx[0|1]
			if (annoGrpIdx==0) return; 				// NO ANNO
			
		// Get anno for one side
			int x = (annoGrpIdx==chrIdx[0]) ? 0 : 1; 
			String tag = Utilities.getGenenumFromDBtag(rs.getString(Q.AGENE));// CAS547 convert here for AllGenes
			int annoIdx  = rs.getInt(Q.AIDX);
			
			if (annoIdx!=annotIdx[0] && annoIdx!=annotIdx[1]) {// CAS520 all annot_idx,hit_idx loaded; make sure best
				if (isEveryPlus) {							// CAS547 add
					annotIdx[x] = annoIdx;					// replace best
					htype = "--";							// otherwise, inherits best-best assignment
					tag = tag + "+";						// + indicates best replaced
				}
				else return; // The correct annotation will be added in merged to THIS record
			}
			
			gstart[x] =		rs.getInt(Q.ASTART);		// CAS519 add these 4 columns
			gend[x] =		rs.getInt(Q.AEND);
			glen[x] = 		Math.abs(gend[x]-gstart[x])+1;
			gstrand[x] = 	rs.getString(Q.ASTRAND); 
			
			annotStr[x] =	rs.getString(Q.ANAME);
			geneTag[x] = 	tag; 		// CAS514 add geneTag; CAS518 chg genenum to tag
			
			if (x==0) annoSet0.add(annoIdx); // multiple anno's for same hit
			else      annoSet1.add(annoIdx);
		}
		catch (Exception e) {ErrorReport.print(e, "Read hit record");}
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
				if (isEveryPlus) {						// CAS547 add
					annotIdx[x] = rs.getInt(Q.AGIDX);	// hit overlaps at least two genes on both sides
					tag = tag + "++";					// indicate 2nd non-best
					cntPlus2++;
					if (Q.TEST_TRACE && cntPlus2<3) System.out.println("Double Plus (" + x + ") " + tag);
				}
				else return;
			}
			
			gstart[x] =		rs.getInt(Q.ASTART);	// CAS519 add these 4 columns
			gend[x] =		rs.getInt(Q.AEND);
			glen[x] = 		Math.abs(gend[x]-gstart[x])+1;
			gstrand[x] = 	rs.getString(Q.ASTRAND); 
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
			if (isSingle) {
				if (grpStart.size()==0) return true;
				
				for (int gidx : grpStart.keySet()) { // only one selected
					int sf = grpStart.get(gidx);   	 // 0 if not set or set to 0
					int ef = grpEnd.get(gidx); 	
					
					if (chrIdx[0]==gidx) {
						if (sf > 0 && gstart[0] < sf) return false; 
						if (ef > 0 && gend[0]   > ef) return false;
						return true;
					}
				}
				return false; 
			}
			// GeneNum: Chr is the only other filter, which happened in SQL; CAS541 added gene# only; CAS543 allow suffix
			String inputGN = qPanel.getGeneNum();
			if (inputGN!=null) {
				if (inputGN.endsWith(".")) inputGN = inputGN.substring(0, inputGN.length()-1);
				int x=-1;
				if (inputGN.contains(".")) {
					if      (geneTag[0]!="-" && geneTag[0].equals(inputGN))  x=0;
					else if (geneTag[1]!="-" && geneTag[1].equals(inputGN))  x=1;
				}
				else {
					if      (geneTag[0]!="-" && Utilities.getGenenumIntOnly(geneTag[0]).equals(inputGN))  x=0;
					else if (geneTag[1]!="-" && Utilities.getGenenumIntOnly(geneTag[1]).equals(inputGN))  x=1;
				}
				if (x==-1) return false;
				
				if (grpIdxOnly<0 || chrIdx[x]==grpIdxOnly) return true;
				
				return false;
			}
			
			/* CAS543 moved to SQL
			else if (qPanel.isEitherAnno()) if (annoSet0.size()==0 && annoSet1.size()==0) return false; // not either anno
			else if (qPanel.isBothAnno())   if (annoSet0.size()<=0 || annoSet1.size()<=0) return false; // not both anno
			*/
			
			if (qPanel.isOneAnno()) { // Either is done for One, but must further filter
				if (annotIdx[0] <= 0 && annotIdx[1] <= 0) return false; // both !anno; CAS547 was annoSet0/1.size()>0
				if (annotIdx[0] > 0  && annotIdx[1] > 0)  return false; // both anno; CAS547 was annoSet0/1.size()<=0
			}
			
			// CAS543 when doing anno search as query, returned hits without anno if they overlap
			if (qPanel.isAnnoTxt()) { 
				String anno = qPanel.getAnnoTxt().toLowerCase();
				if (!annotStr[0].toLowerCase().contains(anno) && !annotStr[1].toLowerCase().contains(anno)) return false;
			}
			if (grpStart.size()==0) return true;
			
			// if one or more chromosome selected, one of the chr pairs has to be a selected one
			boolean found=false;
			for (int gidx : grpStart.keySet()) {
				int sf = grpStart.get(gidx); 
				int ef = grpEnd.get(gidx);
				
				for (int x=0; x<2; x++) { // using hit coords
					if (chrIdx[x]==gidx) {
						found = true;	  // its a selected
						
						if (sf > 0 && hstart[x] < sf)return false;  
						if (ef > 0 && hend[x] > ef)  return false; // CAS522 was a <e since v519
					}
				}
			}
			return found;
			
		} catch (Exception e) {ErrorReport.print(e, "Reading rows"); return false;}
	}
	
	/*********************************************************
	 * Called if ComputePgeneF is run
	 */
	protected void setPgeneF(int pgenef, int pgfsize) {
		this.pgenef = pgenef;
		this.pgfsize = pgfsize;
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
			
			if (pgenef<=0)      row.add(Q.empty);  else row.add(pgenef);
			if (pgfsize<=0)     row.add(Q.empty);  else row.add(pgfsize);
			
			if (hitNum<=0)      row.add(hitIdx);   else row.add(hitNum);
			if (pid<=0)			row.add(Q.empty);  else row.add(pid);
			if (psim<=0)		row.add(Q.empty);  else row.add(psim);
			if (hcnt<=0)		row.add(Q.empty);  else row.add(hcnt);
			if (hst.contentEquals(""))	row.add(Q.empty);  else row.add(hst); // CAS520 add column
			if (hscore<=0)		row.add(Q.empty);  else row.add(hscore); // CAS540 add column
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
	
	/******************************************************
	 * Class variables -- public -- accessed in ComputePgeneF
	 */
// Annot 
	private TreeSet <Integer> annoSet0 = new TreeSet <Integer> (); // not same order as annotStr
	private TreeSet <Integer> annoSet1 = new TreeSet <Integer> (); // just used to count genes
	private String [] annotStr =	 {"", ""};
	
// hit
	private int hitIdx = -1, hitNum = -1;
	private int pid = -1, psim = -1, hcnt=-1; 		// CAS516 add; 
	private String hst="";							// CAS520 add column
	private int hscore = -1;						// CAS540 add column
	private String htype = "";						// CAS546 add column
	private int [] spIdx =  {Q.iNoVal, Q.iNoVal};	
	private int [] chrIdx = {Q.iNoVal, Q.iNoVal};		
	private int [] gstart = {Q.iNoVal, Q.iNoVal};  // CAS519 add all g-fields. 
	private int [] gend = 	{Q.iNoVal, Q.iNoVal}; 
	private int [] glen = 	{Q.iNoVal, Q.iNoVal};  
	private String [] gstrand = {Q.empty, Q.empty};
	private int [] hstart = {Q.iNoVal, Q.iNoVal};  
	private int [] hend = 	{Q.iNoVal, Q.iNoVal}; 
	private int [] hlen = 	{Q.iNoVal, Q.iNoVal};  // CAS516 add
	private String [] geneTag = {Q.empty, Q.empty}; // CAS514 add; CAS518 chr.gene#.suffix
	private int [] annotIdx = {Q.iNoVal, Q.iNoVal}; // CAS520 add because was not getting annot1_idx and annot2_idx
	private int [] numHits = {Q.iNoVal, Q.iNoVal}; // CAS541 add
	
// block
	private int blockNum=-1, blockScore=-1, runSize=-1, runNum=-1;
	private String blockStr = Q.empty, collinearStr = Q.empty;
	
// Other
	private int rowNum=0;				// this rowNum is not used in table, but is a placeholder and used in debugging
	private int pgenef=-1, pgfsize=-1;		// ComputePgeneF
	
	private String [] spName= {Q.empty, Q.empty};	
	private String [] chrNum= {Q.empty, Q.empty};
	
	// AnnoStr broken down into keyword values in finish()
	private HashMap <Integer, String[]> annoVals = new HashMap <Integer, String[]>  (); // 0,1, values in order
}
