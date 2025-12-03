package symapQuery;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import javax.swing.JTextField;

import symap.Globals;
import util.ErrorReport;

/*************************************************************
 * Cluster genes that share hits 
 * Only g2 and g1 as gene based; g1 used if Pseudo
 */
public class ComputeClust {  
	private boolean TRC = Globals.TRACE;
	// Output structures; row is not removed in DBdata if no hitIdx2Grp entry
	private HashMap<Integer,Integer> hitIdx2Grp; // hitID to Cluster#
	private HashMap<Integer,Integer> grpSizes; 	 // group sizes
		
	// Input:
	private QueryPanel qPanel;
	private Vector <DBdata> inRows;		// Rows will be removed in DBdata if no hitIdx2Grp entry
	private JTextField loadStatus;
	private boolean bHasExc=false, bHasInc=false;
	private int cutoff=0;
	
	//From query panel
	private HashMap <Integer, Species> idxSpMap = new HashMap <Integer, Species> ();  // spIdx, Species
	private HashMap <Integer, Cluster> gnClMap  = new HashMap <Integer, Cluster> ();  // annotIdx, Cluster; used for Merge
 	private HashMap <Integer, Cluster> hitClMap = new HashMap <Integer, Cluster> ();  // hitIdx, Cluster 
 	private Vector <Cluster> clVec = new Vector <Cluster>();
 	
 	private int clNum=0;
 	private boolean bSuccess=true;
 	
	protected ComputeClust(
			Vector <DBdata> rowsFromDB, 		
			QueryPanel lQueryPanel,JTextField progress,
			HashMap<Integer,Integer> hitIdx2Grp, HashMap<Integer,Integer> grpSizes) { // output structures
		
		this.inRows = rowsFromDB;	
		this.qPanel = lQueryPanel;
		this.loadStatus = progress;
									  // Output structures:
		this.hitIdx2Grp = hitIdx2Grp; // identify hits in clusters; only these will be kept as rows with the assigned cluster
		this.grpSizes = grpSizes;	  // to save grp sizes with each hit to can show sz.cl#
		
		computeClusters();
	}
	/************************************************************/
	private void computeClusters() {
	try {
		initFilters();  if (!bSuccess) return;
		
		computeClust();	if (!bSuccess) return;
		filterClust();	if (!bSuccess) return;
		makeOutput();
	} 
	catch (Exception e) {ErrorReport.print(e, "Compute Cluster"); }
	} // end computeGroups
    
	/****************************************************************
	 * If either end is in cluster, add hit
	 ***************************************************************/
	private void computeClust() {
	try {
		int rowNum=0;
		for (DBdata dd : inRows) {
			if (rowNum++ % Q.INC ==0) { 
				if (loadStatus.getText().equals(Q.stop)) {bSuccess=false; return;} // uses Stopped
				loadStatus.setText("Cluster " + rowNum + " rows...");
			}
			if (dd.annotIdx[0]<=0 || dd.annotIdx[1]<=0) continue; // pseudo_genes have annotIdx
			
			Cluster clObj0 = gnClMap.containsKey(dd.annotIdx[0]) ?  gnClMap.get(dd.annotIdx[0]) : null;
			Cluster clObj1 = gnClMap.containsKey(dd.annotIdx[1]) ?  gnClMap.get(dd.annotIdx[1]) : null;
			Cluster clObj=null;
			
			if (clObj0!=null && clObj1!=null) {	
				if (clObj0==clObj1) clObj = clObj0;
				else {
					clObj0.mergeCluster(clObj1); 	// Merge
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
		if (TRC) Globals.tprt("Clusters: " + clNum + "    Genes in Cl: " + gnClMap.size() + "    Hits in Cls: " + hitClMap.size());
	}
	catch (Exception e) {ErrorReport.print(e, "ParseRowsG2"); bSuccess=false;}
	}
	
	/***********************************************************/
	private void filterClust() {  // set filtered cl.clNum to 0
	try {
		int cntNfil=0, cntAllInc=0, cntExc=0;
		for (Cluster cl : clVec) {
			if (cl.hitMap.size()<cutoff) {					// If a cluster has less than < hits, reject
				cl.clNum=0; cntNfil++;
				continue;
			}
			if (bHasInc) {									// If a cluster does not have all included species, reject
				for (Species spObj : idxSpMap.values()) {
					if (spObj.bInc && cl.spCntMap.get(spObj.spIdx)==0) {
						cl.clNum=0; cntAllInc++;
						break;
					}
				}
			}
			if (cl.clNum>0 && bHasExc) {					// If a cluster has excluded species, reject
				for (Species spObj : idxSpMap.values()) {
					if (spObj.bExc && cl.spCntMap.get(spObj.spIdx)>0) {
						cl.clNum=0; cntExc++;
						break;
					}
				}
			}
		}
		if (TRC) Globals.tprt(String.format("Filter: N %,d   !Inc: %,d   Exc: %,d ", cntNfil, cntAllInc, cntExc));
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
		// The output is sorted in DBdata.sortRows, but the grpNum can change based on order of species input.
		// Hence, sort here first for deterministic numbering. CAS578 add sort
		for (Cluster clObj : clVec) clObj.numHit = clObj.hitMap.size();
		try {
			Collections.sort(clVec, new Comparator<Cluster> () {
				public int compare(Cluster c1, Cluster c2) {
					if (c2.numHit!=c1.numHit) return c2.numHit-c1.numHit;
					
					int cmpNum0 = c1.chrNum[0].compareTo(c2.chrNum[0]);
					if (cmpNum0!=0) return cmpNum0;
					
					int cmpNum1 = c1.chrNum[1].compareTo(c2.chrNum[1]);
					if (cmpNum1!=0) return cmpNum1;
					
					return 0;
				}
			} );
		} catch (Exception e) {} // in case it violates
			
		// new cluster number
		int nGrpNum=1;
		for (Cluster clObj : clVec) {
			if (clObj.clNum==0) continue; 	// clNum=0 filtered; 
			
			clObj.clNum = nGrpNum++; 		// reassign new number
			
			grpSizes.put(clObj.clNum, clObj.hitMap.size());	// To create rows in DBdata: cl#->size
		}	
		// add cluster to hit
		for (int hitIdx : hitClMap.keySet()) {
			Cluster clObj = hitClMap.get(hitIdx);
			if (clObj.clNum==0) continue;	// Filtered
			
			hitIdx2Grp.put(hitIdx, clObj.clNum);	// To create rows in DBdata: hit->Cl#
		}
		Globals.tprt(String.format("hitidx2Grp: %,d    grpSizes: %,d", hitIdx2Grp.size(), grpSizes.size()));
	}
    catch (Exception e) {ErrorReport.print(e, "Make output"); bSuccess=false;}
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
    		if (sObj.bInc) bHasInc=true;	// zero results if 0/1 included
    		if (sObj.bExc) bHasExc=true;
    		
    		idxSpMap.put(sObj.spIdx, sObj);
    		
    		for (String idxstr : spPanel.getChrIdxList(i)){
    			if(idxstr != null && idxstr.length() > 0) { 
        			int idx = Integer.parseInt(idxstr);
        			sObj.chrIdx.add(idx);
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
		int clNum = 0;		// assigned while building, then reassigned in makeOutput
		int [] minStart = {Integer.MAX_VALUE, Integer.MAX_VALUE};
		int [] maxEnd =   {0,0};
		String [] chrNum = {"",""};
		
		HashMap <Integer, DBdata> hitMap = new HashMap <Integer, DBdata> (); 
		int numHit = 0;
		
		HashMap <Integer, Integer> spCntMap   = new HashMap <Integer, Integer> (); // spIdx, cnt
 		
		private Cluster(int num) {
			clNum=num;
			for (int idx : idxSpMap.keySet()) spCntMap.put(idx, 0);
		}
		private void addHitG2(DBdata dd) {
			hitMap.put(dd.hitIdx, dd);
			for (int x=0; x<2; x++) {
				if (dd.hstart[x]<minStart[x]) minStart[x] = dd.hstart[x];
				if (dd.hend[x]>maxEnd[x])     maxEnd[x] = dd.hend[x];
				
				if (chrNum[x]=="" || dd.getChrNum(x).compareTo(chrNum[x])<0) 
					chrNum[x] = dd.getChrNum(x); // use smallest for sort
				
				
				utilMapInc(spCntMap, dd.spIdx[x]);	// filters
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
