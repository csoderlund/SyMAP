package symapQuery;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.Vector;
import javax.swing.JTextField;

import util.ErrorReport;

/************************************
 * Multi filter; Return multi-hits >=n;  If *Minor was checked, they are already included
 * Filtering on location has already been done, but need to again here because the multi>N must be on a selected
 * chromosome (if there is one).
 * Input is a vector DBdata objects of two species, each will represent a row when created later.
 */

public class ComputeMulti {
	protected static final double bigOlap = 0.4; 
	
	// Input:
	private Vector <DBdata> inData;	// initial input data (each will be a row); then reused for intermediate results
	private HashSet <Integer> grpIdxOnly;
	private int [] spIdxList;
	private HashMap<String,String> sumProjMap;
	private JTextField loadStatus;
	
	// parameters
	private boolean isSameChr=false, isTandem=false; // minor is done on query
	private int nHits = 0;
	
	// working
	private int numFam = 1, saveNumFam=1; // save is for filterTandem
	private boolean bSuccess = true;
	
	private Vector <DBdata> spData = new Vector <DBdata> (); // intermediate results
	private int spIdxCur=-1, spIdxOpp=-1; // species index for current species and opposite
	private int ii=-1, jj=-1; 		   // The index for the current species and the opposite
	
	private int [][] spSummaryCnts;
	
	private String mkKey(DBdata dObj) {
		if (dObj.annotIdx[ii]==0) return null; 
		if (dObj.geneTag[ii].endsWith(Q.pseudo)) return null;
			
		return isSameChr ? (dObj.annotIdx[ii] + ":" + dObj.chrIdx[jj]) : (dObj.annotIdx[ii] + "");
	}
	private boolean skipRow(DBdata dObj) {
		if (spIdxCur!=dObj.spIdx[ii] || spIdxOpp!=dObj.spIdx[jj]) return true;
		
		if (grpIdxOnly.size()!=0  && !grpIdxOnly.contains(dObj.chrIdx[ii])) return true;
		
		return false;
	}
	////////////////////////////////////////////////////////////////////
	protected ComputeMulti(Vector <DBdata> dataFromDB, // input data
			QueryPanel qPanel,						// get parameters
			JTextField loadStatus,					// write to TableMainPanel in loadStatus bar
			HashSet<Integer> grpIdxOnly,			// groups to process
			int [] 	spIdxList,						// species to process
			HashMap<String,String> projMap)			// output counts for summary
	{
		this.inData 	= dataFromDB;
		this.grpIdxOnly = grpIdxOnly;
		this.spIdxList 	= spIdxList;
		this.sumProjMap 	= projMap;
		this.loadStatus = loadStatus;
		
		spSummaryCnts = new int [spIdxList.length][spIdxList.length]; 
		for (int i=0; i<spIdxList.length; i++)
			for (int j=0; j<spIdxList.length; j++) spSummaryCnts[i][j]=0;
		
		isSameChr   = qPanel.isMultiSame();
		isTandem 	= qPanel.isMultiTandem();
		nHits 		= qPanel.getMultiN();
	}
	/*************************************************************
	 * Compute: Using inData, keep only the ones that pass the filters and output dataForDisplay
	 * process species one at a time so groups are ordered
	 */
	protected Vector <DBdata> compute() {
		Vector <DBdata> finalRows = new Vector <DBdata> (); 
		int rowNum=0;
		
		for (int sp1=0; sp1<spIdxList.length; sp1++) { // process this sp against all others
			spIdxCur = spIdxList[sp1];
			
			for (int sp2=0; sp2<spIdxList.length; sp2++) { 
				if (sp1==sp2) continue;
				
				spIdxOpp = spIdxList[sp2];
				ii = -1;
				for (DBdata dObj : inData) { // find if spIdxCur is first or second
					if ((spIdxCur==dObj.spIdx[0] || spIdxCur==dObj.spIdx[1]) 
					 && (spIdxOpp==dObj.spIdx[0] || spIdxOpp==dObj.spIdx[1])) {
						ii = (spIdxCur==dObj.spIdx[0]) ? 0 : 1;
						jj = (spIdxCur==dObj.spIdx[0]) ? 1 : 0;
						break;
					}
				}
				if (ii== -1) continue; // no data for on pair
					  
				filterNhits(); 		if (!bSuccess) return finalRows;   			// In: inData  Out: spData; all rows for sp1&sp2
				
				if (isTandem) filterTandem(); if (!bSuccess) return finalRows;  // In: spData  Out: spData; remove non-tandem
																
				for (DBdata dObj : spData) finalRows.add(dObj); 	   			// transfer to final vector
				
				spSummaryCnts[sp1][sp2] = numFam-1;	
				
				if (rowNum++ % Q.INC ==0) {
					if (loadStatus.getText().equals(Q.stop)) {bSuccess=false; return null;}
					loadStatus.setText("Cluster " + rowNum + " rows...");
				}
			}
		}
		
		// finish
		Collections.sort(finalRows, new SortByGrpByAnno());
			
		rowNum=1;
		for (DBdata dObj : finalRows) dObj.rowNum = rowNum++;        
		
		// for summary
		int last=0;
		for (int sp=0; sp<spIdxList.length; sp++) {
			String name = DBdata.spNameList[sp];
			String msg = "";
			for (int j=0; j<spIdxList.length; j++) {
				if (spSummaryCnts[sp][j]>0) {
					int sz = spSummaryCnts[sp][j]-last;
					String x = (spIdxList.length==2) ? String.format("#%d", sz) :
							                           String.format("#%d (%d)", sz, spSummaryCnts[sp][j]);
					msg += String.format("%-11s ", x);
					last = spSummaryCnts[sp][j];
				}
			}
			if (msg.isEmpty()) msg="#0";
			sumProjMap.put(name, "Groups: " + msg);
		}
		return finalRows;
	}
	
	/*******************************************************
	 * inRows is from the initial query or filterPiles
	 * spRows only has hits from sp0 and sp1
	 */
	private void filterNhits() {	
	try {
		HashMap<String, Integer> geneCnt = new HashMap<String, Integer>(); 
		spData.clear();  
		
		// build sets of genes and their counts; SameChr is used in the key
		for (DBdata dObj : inData) {
			if (skipRow(dObj)) continue;
			
			String key = mkKey(dObj); 
			if (key!=null) {
				if (geneCnt.containsKey(key)) geneCnt.put(key, geneCnt.get(key)+1);
				else 						  geneCnt.put(key, 1);
				
			}
		}
		
		// create rows with only the cnt>N row for this species pair
		for (DBdata dObj : inData) {
			if (skipRow(dObj)) continue;
	
			String key = mkKey(dObj); 
			if (key!=null) {
				int num = (geneCnt.containsKey(key)) ? geneCnt.get(key) : 0;
				
				if (num>=nHits) {// is in a N group
					if (dObj.grpN>0) spData.add((DBdata) dObj.copyRow());	// dObj can be in mult groups; already been assigned to one
					else spData.add(dObj);									
				}
			}
		}
		
		// add group number and size
		saveNumFam = numFam;  // for tandem, which needs renumbering from here;
		Collections.sort(spData, new SortForGrpByII());
			
		HashMap<String, Integer> geneGrpMap = new HashMap<String, Integer>();  // geneTag, grpN
				
		for (DBdata dObj  : spData) { // valid rows with valid keys and geneCnt entries
			String key = mkKey(dObj); 
			int sz = geneCnt.get(key);
			
			int fn;
			if (geneGrpMap.containsKey(key)) fn = geneGrpMap.get(key);
			else {
				fn = numFam++;
				geneGrpMap.put(key, fn);
			}
			dObj.setGroup(fn,sz); // dObj.grpN = sz-fn
		}		
	}
	catch (Exception e) {ErrorReport.print(e, "make multi-hit genes"); bSuccess=false;}
	}
	
	/*************************************************
	 * Run after filterNhits in order to reduce sets to those with tandem
	 * In: spData sorted by geneTag/group; both genes  Out: spData;
	 */
	private void filterTandem() {
	try {
		if (spData.size()==0) return;
		
		Collections.sort(spData, new SortForTandem());
		
		Vector <DBdata> tanData = new Vector <DBdata> ();
		TreeMap <Integer, DBdata> gTagMap = new TreeMap <Integer, DBdata> (); 
		
		numFam = saveNumFam;	// start where last pair set left off
		
		int curGrp=spData.get(0).grpN;
		
		for (DBdata dObj: spData) {
			String [] v = dObj.geneTag[jj].split("\\."); // opposite
			int g = Integer.parseInt(v[1]); // 0 is chr
			
			if (curGrp==dObj.grpN) {				// will not include pseudo because of numbering
				gTagMap.put(g, dObj);
				continue;
			}
	
			filterTandemEval(curGrp, gTagMap, tanData);
			
			// start next
			gTagMap.clear();
			gTagMap.put(g, dObj);
			curGrp = dObj.grpN;
		}
		filterTandemEval(curGrp, gTagMap, tanData);
		
		// finish
		spData.clear();
		spData = tanData; 
		saveNumFam = numFam;
	}
	catch (Exception ee) {ErrorReport.print(ee, "make multi-hit tandem"); bSuccess = false;}
	}
	// Groups will be renumbered; a gene group may split into multi-group, e.g. groupSize 10 may have two tandem interrupted by one gene
	private void filterTandemEval(int curGrp, TreeMap <Integer, DBdata> gTagMap,  Vector <DBdata> tanData) {
		Vector <DBdata> nData = new Vector <DBdata> ();
		int last = -1;
		
		for (int tag : gTagMap.keySet()) { // figure out if multiple sets of >=N
			if (last!=-1 && (Math.abs(tag-last)>1)) {
				if (nData.size()>=nHits) {
					for (DBdata dObj : nData) {
						dObj.setGroup(numFam, nData.size());
						tanData.add(dObj);
					}
					numFam++;
				}
				nData.clear();
			}
			nData.add(gTagMap.get(tag));
			
			last = tag;
		}
		if (nData.size()>=nHits) {
			for (DBdata dObj : nData) {
				dObj.setGroup(numFam, nData.size());
				tanData.add(dObj);
			}
			numFam++;
		}
	}
	
	//////////////////////////////////////////
	private class SortByGrpByAnno implements Comparator <DBdata> { // make sure annos sorted within group
		public int compare(DBdata a, DBdata b) {
			if (a.grpN==b.grpN) {
				if (a.annotIdx[0]==b.annotIdx[0]) return a.annotIdx[1]-b.annotIdx[1];
				else                              return a.annotIdx[0]-b.annotIdx[0];
			}
			return a.grpN - b.grpN;
		}
	}
	private class SortForGrpByII implements Comparator <DBdata> { // only sort ii
		public int compare(DBdata aa, DBdata bb) {
			if (aa.chrIdx[ii]!=bb.chrIdx[ii]) return aa.chrNum[ii].compareTo(bb.chrNum[ii]);
			
			String [] v1 = aa.geneTag[ii].split("\\."); 
			String [] v2 = bb.geneTag[ii].split("\\.");
			
			int g1 = Integer.parseInt(v1[1]); // 0 is chr
			int g2 = Integer.parseInt(v2[1]);
			if (g1!=g2) return g1-g2;
			
			if (v1.length==3 && v2.length==3) return v1[2].compareTo(v2[2]);
			
			return 0;
		}
	}
	private class SortForTandem implements Comparator <DBdata> { 
		public int compare(DBdata a, DBdata b) {
			
			if (a.chrIdx[ii]!= b.chrIdx[ii]) return a.chrIdx[ii]-b.chrIdx[ii];
			
			if (a.annotIdx[ii]==b.annotIdx[ii]) return a.annotIdx[jj]-b.annotIdx[jj];

			return a.annotIdx[ii]-b.annotIdx[ii];
		}
	}
}
