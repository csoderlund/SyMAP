package symapQuery;
/**************************************
 * CAS504 this file replaced the previous approach to parsing the sql results
 */
import java.sql.ResultSet;
import java.util.Vector;
import java.util.TreeSet;
import java.util.HashMap;

import javax.swing.JTextField;

import symap.projectmanager.common.Project;
import util.ErrorReport;
import util.Utilities;

/************************************************
 * create the Vector <DBdata> rowsFromDB
 * 
 * Note that each hit can appear in multiple records depending how many annotations it has. 
 * They are ordered by hit_idx so the identical hits will be consecutive.
 * The code groups them into a single record.
 */
public class DBdata {
	private static boolean debug = symap.SyMAP.DEBUG;
	
	private static String [] spNameList; 				// species Names
	private static int [] 	spIdxList; 					// species spIdx in same order
	private static HashMap <Integer, String[]> annoKeys; // spIdx, keys in order
	private static HashMap <Integer, Integer> grpStart;  // grpIdx, start,end
	private static HashMap <Integer, Integer> grpEnd;    // grpIdx, start,end
	
	private static QueryPanel qPanel;
	private static SpeciesSelectPanel spPanel;
	private static JTextField loadStatus;
	private static boolean isSingle=false, isOrphan = false;;
	
	private static int cntDup=0, cntFilter=0, cntPgeneF=0;
	
	public static Vector <DBdata> loadRowsFromDB(
			ResultSet rs, 
			Vector<Project> projList,	// species in order displayed
			QueryPanel theQueryPanel,  	// for species information	
			String [] annoColumns, 		// Species\nkeyword array in order displayed
			JTextField loadTextField,   // write progress
			HashMap <Integer, Integer> geneCntMap, // output variable of spIdx annotidx counts
			HashMap<String,Integer> proj2regions)  // PgeneF ouput
	{
		clear();
		qPanel = theQueryPanel;
		spPanel = qPanel.getSpeciesPanel();
		loadStatus = loadTextField;
		isSingle = qPanel.isSingle();
		isOrphan = qPanel.isOrphan();
		
		cntDup=0; cntFilter=0; // restart counts
		
		makeSpLists(projList);
		makeAnnoKeys(annoColumns);
		makeGrpLoc(qPanel.getGrpCoords());
		boolean isFilter = (qPanel.isOneAnno() || qPanel.isTwoAnno() || grpStart.size()>0); 
		
		Vector <DBdata> rows = new Vector <DBdata> ();
		HashMap <Integer, DBdata> hitMap = new HashMap <Integer, DBdata> ();
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
			else {
				// two records for each hit, for each side of pair; block, hitIdx, are the same
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
			if (Q.TEST_TRACE) System.err.println("Dups: " + cntDup);
			
		// Filter
			if (isFilter) {
				if (Q.TEST_TRACE) System.err.println("Fil: " + qPanel.isOneAnno() + qPanel.isTwoAnno() + (grpStart.size()>0));
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
				
				if (Q.TEST_TRACE) System.err.println("Filter " + cntFilter);
			}
		// Single only filter
			if (isSingle && !isOrphan) {
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
        	
         	return rows;
     	} 
		catch (Exception e) {ErrorReport.print(e, "Reading rows from db");}
		return rows;
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
	private static void makeSpLists(Vector <Project> projList) {
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
				catch (Exception ex) {System.err.println("Cannot process coords " + grpCoords.get(gidx));}
				
				grpStart.put(gidx, s);
				grpEnd.put(gidx, e);
			}
			if (debug) {
				for (int idx : grpStart.keySet())
					System.out.format("%3d %,10d %,10d\n", idx, grpStart.get(idx), grpEnd.get(idx));
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
    		    		 	
    			if (Q.TEST_TRACE) {
    				System.out.println("hitIdx2Grp: " + hitIdx2Grp.size() + " Grps: " + grpSizes.size());
    				for (int x : hitIdx2Grp.keySet()) {
    					System.out.println("Idx2Grp " + x + " " + hitIdx2Grp.get(x));
    					break;
    				}
    				for (int x : grpSizes.keySet()) {
    					System.out.println("Grp " + x + " " + grpSizes.get(x));
    					break;
    				}
    			}
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
	 */
	private DBdata() {}
	
	private void loadHit(ResultSet rs, int row) {
		try {
			this.rowNum = row;
			
			// from hit table - same values added for each side of hit - if anno on both sides
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
			if (hcnt==0) hcnt=1;				// CAS519 makes more sense to be 1 merge instead of empty
			String s	=	rs.getString(Q.HST);
			hst = (s.contains("+") && s.contains("-")) ? "!=" : "=";
			
			// from anno table - first half of hit
			int annoGrpIdx = rs.getInt(Q.AGIDX);	// grpIdx, so unique; same as chrIdx[0|1]
			int annoIdx = rs.getInt(Q.AIDX);
			if (annoGrpIdx > 0 && (annoIdx==annotIdx[0] || annoIdx==annotIdx[1])) { // CAS520 all annot_idx,hit_idx loaded; make sure best
				int x = (annoGrpIdx==chrIdx[0]) ? 0 : 1; 
				
				gstart[x] =		rs.getInt(Q.ASTART);		// CAS519 add these 4 columns
				gend[x] =		rs.getInt(Q.AEND);
				glen[x] = 		Math.abs(gend[x]-gstart[x])+1;
				gstrand[x] = 	rs.getString(Q.ASTRAND); 
				
				annotStr[x] =	rs.getString(Q.ANAME);
				geneNum[x] = 	rs.getString(Q.AGENE); 		// CAS514 add; CAS518 change genenum to tag
				
				if (x==0) annoSet0.add(annoIdx); // multiple anno's for same hit
				else      annoSet1.add(annoIdx);
			}
		}
		catch (Exception e) {ErrorReport.print(e, "Read hit record");}
	}
	/***************************************
	 * If there is no anno for one side of the hit, there will be no record
	 */
	private void loadHitMerge(ResultSet rs) {
		try {
			int annoIdx = rs.getInt(Q.AIDX);  
			if (annoIdx!=annotIdx[0] && annoIdx!=annotIdx[1]) return; // CAS520 get best
			
			int annoGrpIdx = rs.getInt(Q.AGIDX);
			int x = (annoGrpIdx==chrIdx[0]) ? 0 : 1; // goes with 1st or 2nd half of hit?
			
			gstart[x] =		rs.getInt(Q.ASTART);		// CAS519 add these 4 columns
			gend[x] =		rs.getInt(Q.AEND);
			glen[x] = 		Math.abs(gend[x]-gstart[x])+1;
			gstrand[x] = 	rs.getString(Q.ASTRAND); 
			
			geneNum[x] = 	rs.getString(Q.AGENE); // CAS514
			
			int sz = (x==0) ? annoSet0.size() : annoSet1.size();
			if (sz>0)  { // can be multiple hits for same project	
				annotStr[x]	+=	";" + rs.getString(Q.ANAME);
				cntDup++; 
			}  
			else annotStr[x]	=	rs.getString(Q.ANAME);
			
			if (x==0) annoSet0.add(annoIdx);
			else      annoSet1.add(annoIdx);
		}
		catch (Exception e) {ErrorReport.print(e, "Merge");}
	}
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
			geneNum[0] = 	rs.getString(Q.AGENE);   // CAS514 add
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
			spName[x] = 				spPanel.getSpNameFromChrIdx(chrIdx[x]);
			chrNum[x] = 				spPanel.getChrNameFromChrIdx(chrIdx[x]);
			if (isSingle) spIdx[x] =  	spPanel.getSpIdxFromChrIdx(chrIdx[x]); 

			if (blockNum>0) blockStr = Utilities.blockStr(chrNum[0], chrNum[1], blockNum); // CAS513 use blockStr
			if (runSize>0)  {
				collinearStr = Utilities.blockStr(chrNum[0], chrNum[1], runSize); // CAS517 add chrs;
				if (runNum>0)  collinearStr += "." + runNum; 					  // CAS520 add runNum
			}
			if (geneNum[x]!=Q.empty) geneNum[x] = Utilities.makeChrGenenum(chrNum[x], geneNum[x]); // CAS518 formatted
			
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
				if (grpStart.size()==0) return true; // no locs set
				
				boolean found=false;
				for (int gidx : grpStart.keySet()) {
					int sf = grpStart.get(gidx);   	// 0 if not set or set to 0
					int ef = grpEnd.get(gidx); 	
					
					if (chrIdx[0]==gidx) {
						found = true;	  // its a selected
						
						if (sf > 0 && gstart[0] < sf) return false; 
						if (ef > 0 && gend[0]   > ef) return false;
					}
				}
				return found; // should always be true since some chr selected
			}
			
			if (qPanel.isOneAnno()) {
				if (annoSet0.size() >0 && annoSet1.size() >0) return false; // both anno
				if (annoSet0.size()<=0 && annoSet1.size()<=0) return false; // both not anno
			}
			else if (qPanel.isTwoAnno()) {
				if (annoSet0.size()<=0 || annoSet1.size()<=0) return false; // both anno
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
	public void setPgeneF(int pgenef, int pgfsize) {
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
	public Vector <Object> makeRow() {
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
			if (hst.contentEquals(""))		row.add(Q.empty);  else row.add(hst); // CAS520 add column
		}
		// chr, start, end; CAS519 add gene&hit for pairs
		int cntCol = FieldData.getSpColumnCount(isSingle);
		for (int i=0; i<spIdxList.length; i++) { // add columns for ALL species
			int x=-1;
			if (spIdx[0] == spIdxList[i]) x=0;
			else if (spIdx[1]==spIdxList[i]) x=1;
			
			if (x>=0) {
				row.add(chrNum[x]); 
				if (gstart[x]<0)  	row.add(Q.empty); else row.add(gstart[x]); 
				if (gend[x]<0)  	row.add(Q.empty); else row.add(gend[x]); 
				if (glen[x]<=0)  	row.add(Q.empty); else row.add(glen[x]);
				row.add(gstrand[x]);
				row.add(geneNum[x]); // CAS518 num to string
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
	public int    getSpIdx(int x) 	{return spIdx[x];}
	public String getChrNum(int x) 	{return chrNum[x];}
	public int    getChrIdx(int x) 	{return chrIdx[x];}
	public int 	  getHitIdx() 		{return hitIdx;}
	public int [] getCoords() {
		int [] x = {hstart[0], hend[0], hstart[1], hend[1]}; // CAS519 hit
		return x;
	}
	public boolean hasAnno(int x) 	{
		if (x==0) return annoSet0.size()>0;
		else      return annoSet1.size()>0;
	}
	public boolean hasBlock() 	{return blockNum>0;}
	
	/******************************************************
	 * Class variables -- public -- accessed in ComputePgeneF
	 */
// Annot 
	private TreeSet <Integer> annoSet0 = new TreeSet <Integer> (); // not same order as annotStr
	private TreeSet <Integer> annoSet1 = new TreeSet <Integer> (); // just used to count genes
	private String [] annotStr =	 {"", ""};
	
// hit
	private int hitIdx = -1, hitNum = -1;
	private int pid = -1, psim = -1, hcnt=-1; 	// CAS516 add; 
	private String hst="";						// CAS520 add column
	private int [] spIdx =  {Q.iNoVal, Q.iNoVal};	
	private int [] chrIdx = {Q.iNoVal, Q.iNoVal};		
	private int [] gstart = {Q.iNoVal, Q.iNoVal};  // CAS519 add all g-fields. 
	private int [] gend = 	{Q.iNoVal, Q.iNoVal}; 
	private int [] glen = 	{Q.iNoVal, Q.iNoVal};  
	private String [] gstrand = {Q.empty, Q.empty};
	private int [] hstart = {Q.iNoVal, Q.iNoVal};  
	private int [] hend = 	{Q.iNoVal, Q.iNoVal}; 
	private int [] hlen = 	{Q.iNoVal, Q.iNoVal};  // CAS516 add
	private String [] geneNum = {Q.empty, Q.empty}; // CAS514 add; CAS518 chr.gene#.suffix
	private int [] annotIdx = {Q.iNoVal, Q.iNoVal}; // CAS520 add because was not getting annot1_idx and annot2_idx
	
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
	// Assigned on makeGeneCnt; private String [] geneCnt = {"",""};  CAS514 replace with geneNum from db
}
