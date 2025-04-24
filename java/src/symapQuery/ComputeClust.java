package symapQuery;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import javax.swing.JTextField;

import symap.Globals;
import util.ErrorReport;

/*************************************************************
 * Only g2 and g1 as gene based.
 */
public class ComputeClust {
    // Output: inRows is also outRows, as it is replaced with the filter set
	private HashMap<Integer,Integer> hitIdx2Grp; // hitID to PgeneF num
	private HashMap<Integer,Integer> grpSizes; 	 // group sizes
	private HashMap<String, String>  projMap;    // return string per species for summary
		
	// Input:
	private QueryPanel qPanel;
	private Vector <DBdata> inRows;		// Rows will be removed in DBdata if no hitIdx2Grp entry
	private JTextField loadStatus;
	private boolean bIncOne=true; // always on for included species
	private boolean bHasExc=false, bHasInc=false;
	private boolean bIncNoGene=false; // not implemented yet
	private int cutoff=0;
	
	//From query panel
	private HashMap <Integer, Species> idxChrMap = new HashMap <Integer, Species> (); // chrIdx, Species; used for filters
	private HashMap <Integer, Species> idxSpMap = new HashMap <Integer, Species> ();  // spIdx, Species
	private HashMap <Integer, Cluster> gnClMap = new HashMap <Integer, Cluster> ();	 // annotIdx, G2, G1
 	private HashMap <Integer, Cluster> hitClMap = new HashMap <Integer, Cluster> (); // hitIdx, all hits
 	private Vector <Cluster> clVec = new Vector <Cluster>();
 	
 	private int clNum=0;
 	private boolean bSuccess=true;
 	
	public ComputeClust(
			Vector <DBdata> rowsFromDB, 		// in/out
			QueryPanel lQueryPanel,JTextField progress,
			HashMap<Integer,Integer> hitIdx2Grp, HashMap<Integer,Integer> grpSizes, // output structures
			HashMap<String,String> projMap) {	// summary table
		
		this.inRows = rowsFromDB;
		this.qPanel = lQueryPanel;
		this.loadStatus = progress;
		
		this.hitIdx2Grp = hitIdx2Grp;
		this.grpSizes = grpSizes;
		
		this.projMap = projMap;
		
		computeClusters();
	}
	/************************************************************/
	private void computeClusters() {
	try {
		initFilters();  if (!bSuccess) return;
		
		computeG2();	if (!bSuccess) return;
		filterClust();	if (!bSuccess) return;
		makeOutput();
	} 
	catch (Exception e) {ErrorReport.print(e, "Compute Cluster"); }
	} // end computeGroups
    
	/****************************************************************
	 * If either end is in cluster, add hit
	 ***************************************************************/
	private void computeG2() {
	try {
		int rowNum=0;
		for (DBdata dd : inRows) {
			if (rowNum++ % Q.INC ==0) { // CAS564 checked stopped
				if (loadStatus.getText().equals(Q.stop)) {bSuccess=false; return;} // CAS564 uses Stopped
				loadStatus.setText("Cluster " + rowNum + " rows...");
			}
			if (dd.annotIdx[0]<=0 || dd.annotIdx[1]<=0) continue; // pseudo_genes have annotIdx; CAS565 
			if (filterRow(dd)) continue;
			
			Cluster clObj0 = gnClMap.containsKey(dd.annotIdx[0]) ?  gnClMap.get(dd.annotIdx[0]) : null;
			Cluster clObj1 = gnClMap.containsKey(dd.annotIdx[1]) ?  gnClMap.get(dd.annotIdx[1]) : null;
			Cluster clObj=null;
			
			if (clObj0!=null && clObj1!=null) {
				if (clObj0==clObj1) clObj = clObj0;
				else {
					clObj0.mergeCluster(clObj1); 
					clObj = clObj0;
					clObj1 = null;
				}
			}
			else if (clObj0!=null) clObj=clObj0;
			else if (clObj1!=null) clObj=clObj1;
			else {
				clObj = new Cluster(clNum++);
				clVec.add(clObj);
			}
			clObj.addHitG2(dd); // add hitIdx, Cluster to gnClMap and hitClMap
		}
		Globals.tprt("Clusters: " + clNum + "    Genes in Cl: " + gnClMap.size() + "    Hits in Cls: " + hitClMap.size());
	}
	catch (Exception e) {ErrorReport.print(e, "ParseRowsG2"); bSuccess=false;}
	}
	
	/***********************************************************
	 * If a row has an included annotated species, then do not process at all
	 *******************************************************/
	private boolean filterRow(DBdata dd) {
	try {
		if (bIncNoGene) {
			boolean b1 = idxChrMap.get(dd.getChrIdx(0)).bInc;
			boolean b2 = idxChrMap.get(dd.getChrIdx(1)).bInc;
			if (b1 || b2) return true;
		}
		return false;
	}
	catch (Exception e) {ErrorReport.print(e, "Filter"); bSuccess=false; return false; }
	}
	/***********************************************************/
	private void filterClust() { 
	try {
		int cntNfil=0, cntAllfil=0, cntExc=0;
		for (Cluster cl : clVec) {
			// If a cluster has less than < hits, reject
			if (cl.hitMap.size()<cutoff) {
				cl.clNum=0; cntNfil++;
				continue;
			}
			// If a cluster does not have all included species, reject
			if (bIncOne && bHasInc) {
				for (Species spObj : idxSpMap.values()) {
					if (spObj.bInc && cl.spCntMap.get(spObj.spIdx)==0) {
						cl.clNum=0; cntAllfil++;
						break;
					}
				}
				if (cl.clNum==0) continue;
			}
			// If a species is excluded, but has entries, reject
			if (bHasExc) {
				for (Species spObj : idxSpMap.values()) {
					if (spObj.bExc && cl.spCntMap.get(spObj.spIdx)>0) {
						cl.clNum=0; cntExc++;
						break;
					}
				}
				if (cl.clNum==0) continue;
			}
		}
		Globals.tprt(String.format("Filter: N %,d   All: %,d   Exc: %,d", cntNfil, cntAllfil, cntExc));
	}
	catch (Exception e) {ErrorReport.print(e, "Filter"); bSuccess=false;}
	}
	/***********************************************************
	 * Populate output structures, renumber
	 * private HashMap<Integer,Integer> hitIdx2Grp; // all hits:  hitID to clNum
	 * private HashMap<Integer,Integer> grpSizes; 	// all Clust: clNum to size
	 * private HashMap<String, String>  projMap;    // all species: species name to summary
	 */
	private void makeOutput() {
	try {
		int nGrpNum=1;
		HashMap<Integer,Integer> old2new   = new HashMap<Integer,Integer>();  // Renumber  
		
		// For summary
		SpeciesPanel spPanel = qPanel.getSpeciesPanel();
		int nSpecies = spPanel.getNumSpecies();
		HashMap <Integer, Integer> spIdxOrdMap = new HashMap <Integer, Integer>  ();
		int [] spClCnt = new int [nSpecies];
		int [] spHitCnt = new int [nSpecies];
		for (int i = 0; i < nSpecies; i++) {
			spClCnt[i] = spHitCnt[i]=0;
			spIdxOrdMap.put(spPanel.getSpIdx(i), i);
		}
		
		// Loop through hits found in clusters
		for (int hitIdx : hitClMap.keySet()) {
			Cluster clObj = hitClMap.get(hitIdx);
			if (clObj.clNum==0) continue;	// Filtered
			
			int ngrp, ogrp = clObj.clNum;
			if (old2new.containsKey(ogrp)) ngrp = old2new.get(ogrp);
			else {										  
				ngrp = nGrpNum++;							// Assign new cluster number						
				old2new.put(ogrp, ngrp);
				
				grpSizes.put(ngrp, clObj.hitMap.size());	// Output: cl#->size
				
				for (int idx : clObj.spCntMap.keySet()) {	// Summary
					int ord = spIdxOrdMap.get(idx);
					if (clObj.spCntMap.containsKey(idx) && clObj.spCntMap.get(idx)>0) {
						spHitCnt[ord] += clObj.spCntMap.get(idx);
						spClCnt[ord]++;
					}
				}
			}
			hitIdx2Grp.put(hitIdx, ngrp);				    // Output: hit->Cl#
		}
		old2new.clear();
		Globals.tprt(String.format("hitidx2Grp: %,d    grpSizes: %,d", hitIdx2Grp.size(), grpSizes.size()));
		// for summary
		for (int sp=0; sp<nSpecies; sp++) {
			String name = DBdata.spNameList[sp];
			projMap.put(name, String.format("Groups: #%,d (%,d)", spClCnt[sp], spHitCnt[sp]));
		}
	}
    catch (Exception e) {ErrorReport.print(e, "Filter"); bSuccess=false;}
	}
	/***********************************************************
	 * Get filters from queryPanel; Create idxChrMap with inc/exc info
	 **************************************************/
	private void initFilters() { 
	try {
    	cutoff 		= qPanel.getClustN();
    	
    	SpeciesPanel spPanel = qPanel.getSpeciesPanel();

    	for (int i = 0; i < spPanel.getNumSpecies(); i++){
    		Species sObj = new Species();
    		
    		sObj.spIdx 	= spPanel.getSpIdx(i);
    		sObj.spPos  = i;
    		sObj.bInc 	= qPanel.isInclude(i);
    		sObj.bExc 	= qPanel.isExclude(i);
    		if (sObj.bInc) bHasInc=true;
    		if (sObj.bExc) bHasExc=true;
    		
    		idxSpMap.put(sObj.spIdx, sObj);
    		
    		for (String idxstr : spPanel.getChrIdxList(i)){
    			if(idxstr != null && idxstr.length() > 0) { 
        			int idx = Integer.parseInt(idxstr);
        			sObj.chrIdx.add(idx);
        			idxChrMap.put(idx, sObj);
    			}
    		}  
    	} // end going through chromosomes
	}
    catch (Exception e) {ErrorReport.print(e, "FilterInit"); bSuccess=false;}
	}
	/************************************************************/
	private void utilMapInc(Map<Integer,Integer> ctr, int key){
		if (!ctr.containsKey(key)) 	ctr.put(key, 1);
		else						ctr.put(key, ctr.get(key)+1);
	}
	/*****************************************************************/
	private class Cluster {
		int clNum=0;
		int [] minStart = {Integer.MAX_VALUE, Integer.MAX_VALUE};
		int [] maxEnd =   {0,0};
		HashMap <Integer, DBdata> hitMap = new HashMap <Integer, DBdata> ();  
		
		HashMap <Integer, Integer> spCntMap = new HashMap <Integer, Integer> (); // spIdx, cnt
		HashMap <Integer, Integer> spAnCntMap = new HashMap <Integer, Integer> ();
 		
		private Cluster(int num) {
			clNum=num;
			for (int idx : idxSpMap.keySet()) spCntMap.put(idx, 0);
		}
		private void addHitG2(DBdata dd) {
			hitMap.put(dd.hitIdx, dd);
			for (int x=0; x<2; x++) {
				if (dd.hstart[x]<minStart[x]) minStart[x] = dd.hstart[x];
				if (dd.hend[x]>maxEnd[x])     maxEnd[x] = dd.hend[x];
				
				// spIdx is key to both - this is for summary and filters
				utilMapInc(spCntMap, dd.spIdx[x]);	
				if (dd.annotIdx[x]>0) utilMapInc(spAnCntMap, dd.spIdx[x]);
			}
			// the following may replace another cluster during mergeCluster
			gnClMap.put(dd.annotIdx[0], this);	
			gnClMap.put(dd.annotIdx[1], this);	
			hitClMap.put(dd.hitIdx, this);
		}  
		private void mergeCluster(Cluster cObj) {
			for (DBdata dd : hitMap.values()) addHitG2(dd);
		}
		public String toString() {
			String msg= clNum + ". " + hitMap.size() + " Hits: ";
			for (int x : hitMap.keySet()) msg += x + " ";
			msg += "\n Species: ";
			for (int x : spCntMap.keySet()) msg += idxSpMap.get(x).spPos + ":" + spCntMap.get(x) + " "; 
			return msg;
		}
	}
	private class Species {	// in idxChrMap
		int spIdx=0, spPos=0;
		boolean bInc=true, bExc=false;
		Vector <Integer> chrIdx = new Vector <Integer> ();
	}
}
