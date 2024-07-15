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
 * Return multi-hits >=n; added CAS548. If *Minor was checked, they are already included
 * Filtering on location has already been done, but need to again here because the multi>N must be on a selected
 * chromosome (if there is one).
 * Input is a vector DBdata objects of two species, each will represent a row when created later.
 * CAS555 add pile and same chr options, number groups, print clashes, and move code from DBdata; 
 */

public class ComputeMulti {
	protected static final double bigOlap = 0.4; 
	
	// Input:
	private Vector <DBdata> inData;	// initial input data (each will be a row); then reused for intermediate results
	private HashSet <Integer> grpIdxOnly;
	private int [] spIdxList;
	private HashMap<String,String> projMap;
	
	// parameters
	private boolean isSameChr=false, isExon=false, isTandem=false; // minor is done on query
	private int nHits = 0;
	
	// working
	private int numFam = 1, saveNumFam=1; // save is for filterTandem
	private boolean bSuccess = true;
	
	
	private Vector <DBdata> spData = new Vector <DBdata> (); // intermediate results
	private int spIdx=-1, spIdxOpp=-1; // species index for current species and opposite
	private int ii=-1, jj=-1; 		   // The index for the current species and the opposite
	
	private int [][] spSummaryCnts;
	
	private String strAddRows="";
	private int cntAdd=0;
	
	private void dprt(String msg) {symap.Globals.dprt(msg);}
	private void tprt(String msg) {symap.Globals.tprt(msg);}
	
	private String mkKey(DBdata dObj) {
		int idx = dObj.annotIdx[ii];
		if (idx==0) return null;
		return isSameChr ? (idx + ":" + dObj.chrIdx[jj]) : (idx + "");
	}
	private boolean wrongRow(DBdata dObj) {
		if (spIdx!=dObj.spIdx[ii] || spIdxOpp!=dObj.spIdx[jj]) return true;
		if (grpIdxOnly.size()!=0  && !grpIdxOnly.contains(dObj.chrIdx[ii])) return true;
		return false;
	}
	////////////////////////////////////////////////////////////////////
	public ComputeMulti(Vector <DBdata> dataFromDB, // input data
			QueryPanel qPanel,						// get parameters
			JTextField progress,					// write to symap window
			HashSet<Integer> grpIdxOnly,			// groups to process
			int [] 	spIdxList,						// species to process
			HashMap<String,String> projMap)			// output counts for summary
	{
		this.inData = dataFromDB;
		this.grpIdxOnly = grpIdxOnly;
		this.spIdxList = spIdxList;
		this.projMap = projMap;
		
		spSummaryCnts = new int [spIdxList.length][spIdxList.length]; 
		for (int i=0; i<spIdxList.length; i++)
			for (int j=0; j<spIdxList.length; j++) spSummaryCnts[i][j]=0;
		
		isExon 		= qPanel.isMultiExon();
		isSameChr   = qPanel.isMultiChr();
		isTandem 	= qPanel.isMultiTandem();
		nHits 		= qPanel.getMultiN();
	}
	/*************************************************************
	 * Compute: Using inData, keep only the ones that pass the filters and output dataForDisplay
	 * process species at a time so groups are ordered
	 */
	protected Vector <DBdata> compute() {
		Vector <DBdata> finalRows = new Vector <DBdata> (); 
		
		dprt("");
		dprt("Start " + spIdxList.length + " rows " + inData.size());
		
		for (int sp=0; sp<spIdxList.length; sp++) { // process this sp against all others
			spIdx = spIdxList[sp];
			
			for (int sp2=0; sp2<spIdxList.length; sp2++) { 
				if (sp==sp2) continue;
				
				spIdxOpp = spIdxList[sp2];
				ii = -1;
				for (DBdata dObj : inData) { // find if spIdx is first or second
					if ((spIdx   ==dObj.spIdx[0] || spIdx   ==dObj.spIdx[1]) 
					 && (spIdxOpp==dObj.spIdx[0] || spIdxOpp==dObj.spIdx[1])) {
						ii = (spIdx==dObj.spIdx[0]) ? 0 : 1;
						jj = (spIdx==dObj.spIdx[0]) ? 1 : 0;
						break;
					}
				}
				if (ii== -1) { // CAS556 this happened when no data for on pair
					symap.Globals.prt("No multi-hit genes for a species pair...");
					return finalRows;
				}
				dprt(">> " + sp + "," + sp2 + " Idx/ii " + spIdx + "/" + ii + " && " + spIdxOpp + "/" + jj);
				
				if (isExon) filterExon(); if (!bSuccess) return finalRows;   	// In: inData  Out: inData; remove non-exon
				
				filterNhits(); 		if (!bSuccess) return finalRows;   			// In: inData  Out: spData
				
				if (isTandem) filterTandem(); if (!bSuccess) return finalRows;  // In: spData  Out: spData; remove none-tandem
																
				for (DBdata dObj : spData) finalRows.add(dObj); 	   			// transfer to final vector
				
				spSummaryCnts[sp][sp2] = numFam-1;							 
			}
		}
		
		// finish
		Collections.sort(finalRows, new SortByGrpByAnno());
			
		int rowNum=1;
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
			projMap.put(name, "Groups: " + msg);
		}
		if (cntAdd>0) tprt("   Add rows " + cntAdd + " e.g. " + strAddRows);
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
			if (wrongRow(dObj)) continue;
			
			String key = mkKey(dObj); 
			if (key!=null) {
				if (geneCnt.containsKey(key)) geneCnt.put(key, geneCnt.get(key)+1);
				else 						  geneCnt.put(key, 1);
			}
		}
		
		// create rows with only the cnt>N row for this species pair
		for (DBdata dObj : inData) {
			if (wrongRow(dObj)) continue;
	
			String key = mkKey(dObj); 
			if (key!=null) {
				int num    = (geneCnt.containsKey(key)) ? geneCnt.get(key) : 0;
				
				if (num>=nHits) {// is in a N group
					if (dObj.grpN>0) {
						spData.add((DBdata) dObj.copy());
						if (cntAdd++ < 3)  
							strAddRows += String.format("(#%d %s %s) ", dObj.grpN, dObj.geneTag[ii], dObj.geneTag[jj]);	
					}
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
			dObj.setGroup(fn,sz);
		}		
		dprt("  Main filter " + inData.size() + " -> " + spData.size() + " for " + spIdx + "," + ii + " numFam: " + numFam);
	}
	catch (Exception e) {ErrorReport.print(e, "make multi-hit genes"); bSuccess=false;}
	}
	
	/**************************************************
	 * Run before filterNhits to reduce the hits to the valid EE ones
	 */
	private void filterExon() {
	try {
		Vector <DBdata> eeData = new Vector <DBdata> ();
		for (DBdata dObj : inData) {
			if      (ii==0 && dObj.htype.startsWith("E")) eeData.add(dObj);
			else if (ii==1 && dObj.htype.endsWith("E"))   eeData.add(dObj);
		}
		
		dprt("  EE filter " + inData.size() + " -> " + eeData.size() + " for " + spIdx + "," + ii);
		inData.clear();
		for (DBdata eObj : eeData) inData.add(eObj);
	}
	catch (Exception e) {ErrorReport.print(e, "filter exon"); bSuccess=false;}
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
			
			if (curGrp==dObj.grpN) {
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
		dprt("  Tandem filter " + spData.size() + " -> " + tanData.size() + " for " + spIdx + "," + ii + " numFam: " + numFam);
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
					dprt("    Add: " + ii + " " +numFam+" "+gTagMap.get(tag).geneTag[ii]+" -: "+gTagMap.get(last).geneTag[jj]+" "+gTagMap.get(tag).geneTag[jj]+" Sz: "+nData.size());
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
	private class SortByGrpByAnno implements Comparator <DBdata> { // make sure annos sorted within grop
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
