package symapQuery;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;
import javax.swing.JTextField;

import symap.Globals;
import util.ErrorReport;
import util.Popup;

/*************************************************************
 * Cluster genes that share hits 
 * Only g2 and g1 as gene based; g1 used if Pseudo
 */
public class ComputeClust {  
	private boolean TRC = Globals.TRACE;
	private boolean bSaveLgClust = Globals.bQuerySaveLgClust;
	private int MAX_CL=20000; // stop if clusters get too large
	
	// Output structures; row is not removed in DBdata if no hitIdx2Grp entry
	private HashMap<Integer,Integer> hitIdx2Grp; // hitID to Cluster#
	private HashMap<Integer,Integer> grpSizes; 	 // group sizes
		
	// Input:
	private QueryPanel qPanel;
	private Vector <DBdata> inRows;		// Rows will be removed in DBdata if no hitIdx2Grp entry
	private JTextField loadStatus;
	private boolean bPerSpecies=true;
	private int cutoff=0;
	
	//From query panel
	private HashMap <Integer, Species> idxSpMap = new HashMap <Integer, Species> ();  // spIdx, Species
	private HashMap <Integer, Cluster> geneClMap  = new HashMap <Integer, Cluster> ();  // annotIdx, Cluster; used for Merge
 	private HashMap <Integer, Cluster> hitClMap = new HashMap <Integer, Cluster> ();  // hitIdx, Cluster 
 	private Vector <Cluster> clVec = new Vector <Cluster>();
 	
 	private HashMap <Integer, String> idxGeneMap = new HashMap <Integer, String> ();
 	
 	private int clNum=0;
 	private boolean bSuccess=true;
 	
	protected ComputeClust(
			Vector <DBdata> rowsFromDB, 		
			QueryPanel queryPanel,JTextField progress,
			HashMap<Integer,Integer> hitIdx2Grp, HashMap<Integer,Integer> grpSizes) { // output structures
		
		this.inRows = rowsFromDB;	
		this.qPanel = queryPanel;
		this.loadStatus = progress;
									  // Output structures:
		this.hitIdx2Grp = hitIdx2Grp; // identify hits in clusters; only these will be kept as rows with the assigned cluster
		this.grpSizes = grpSizes;	  // to save grp sizes with each hit to can show sz.cl#
		
		try {
			initFilters();  if (!bSuccess) return;
			
			computeClust();	if (!bSuccess) return;
			
			filterClust();	if (!bSuccess) return;
			
			rmLgClust();	if (!bSuccess) return;
			
			makeOutput();
		} 
		catch (Exception e) {ErrorReport.print(e, "Compute Cluster"); }
	}
	
	/****************************************************************
	 * If either end is in cluster, add hit
	 ***************************************************************/
	private void computeClust() {
	try {
		int rowNum=0, numMerge=0, numRow=inRows.size();
		for (DBdata dd : inRows) {
			rowNum++;
			if (rowNum % Q.INC == 0) { 
				if (loadStatus.getText().equals(Q.stop)) {bSuccess=false; return;} 
				int i = (int) (((double)rowNum/(double)numRow) * 100.0);
				loadStatus.setText("Cluster " + i + "% of rows into " + clVec.size() + " (" + numMerge + " merged)...");
				System.gc();
			}
			if (dd.annotIdx[0]<=0 || dd.annotIdx[1]<=0) continue; // pseudo_genes have annotIdx
			
			Cluster clObj0 = geneClMap.containsKey(dd.annotIdx[0]) ?  geneClMap.get(dd.annotIdx[0]) : null; // idx->geneClMap in addHit
			Cluster clObj1 = geneClMap.containsKey(dd.annotIdx[1]) ?  geneClMap.get(dd.annotIdx[1]) : null;
			Cluster clObj=null;
			
			if (clObj0!=null && clObj1!=null) {	
				if (clObj0==clObj1) clObj = clObj0;
				else {
					clObj0.mergeCluster(clObj1); 	// Merge
					clObj = clObj0;
					
					numMerge++;
				}
			}
			else if (clObj0!=null) clObj=clObj0;
			else if (clObj1!=null) clObj=clObj1;
			else {
				clObj = new Cluster(clNum++);
				clVec.add(clObj);
			}
			clObj.addHit(dd); // update geneClMap, hitClMap
		}
		
		if (TRC) {
			String msg = String.format("Clusters: %,4d  Merge : %,4d   Genes: %,5d  Hits%,6d", clNum, numMerge,  geneClMap.size(), hitClMap.size());
			Globals.prt(msg);
		}
	}
	catch (Exception e) {ErrorReport.print(e, "Compute Cluster"); bSuccess=false; }
	catch (OutOfMemoryError e) {ErrorReport.print(e, "Out of memory creating clusters");}
	}
	
	/***********************************************************
	 * Exc: if there is any link to an excluded, it excludes the whole group; 
	 * this means that gene pairs can occur when %sim, etc set that do not appear otherwise
	 ***********************************************************/
	private void filterClust() {  // set filtered cl.clNum to 0
	try {
		int nFilter=0;
		for (Cluster clObj : clVec) {
			int n = (bPerSpecies) ? clObj.geneSet.size() : clObj.hitMap.size();
			if (n<cutoff) {
				clObj.clear(); // clear() sets clObj.clNum=0
			}
			else {
				for (Species spObj : idxSpMap.values()) {
					int geneSpCnt = clObj.spCntMap.get(spObj.spIdx);
					
					if (spObj.bExc) {	
						if (geneSpCnt>0) {
							clObj.clear(); 
							break;			// break out of species loop
						}
					}
					else if (spObj.bInc) {
						if (geneSpCnt==0 || (bPerSpecies && geneSpCnt<cutoff)) {
							if (geneSpCnt<cutoff) {
								clObj.clear(); 
								break;			
							}
						}
					}
				}// end species
			}
			if (clObj.clNum==0) nFilter++;
		} // end cluster
		if (TRC) Globals.prt(String.format("Filtered clusters %,d", nFilter));
	}
	catch (Exception e) {ErrorReport.print(e, "Filter clusters"); bSuccess=false;}
	}
	/**********************************************************
	 * Remove large clusters after filtering; CAS579 added this because the bug fix caused big clusters
	 */
	private void rmLgClust() {
	try {
		Vector <Cluster> newClVec = new Vector <Cluster>();
		if (bSaveLgClust) { // remove dead clusters
			for (Cluster clObj : clVec) if (clObj.clNum>0) newClVec.add(clObj);
			clVec = newClVec;
			return;
		}
		
		int cntRm=0;
		for (Cluster clObj : clVec) {
			if (clObj.clNum>0 && clObj.numHit<=MAX_CL)  {
				newClVec.add(clObj);
			}
			else if (clObj.numHit>MAX_CL) {
				clObj.clear();
				cntRm++;
			}
		}
		clVec = newClVec;
		
		if (cntRm>0) {
			String msg = String.format("Removed %,d clusters that have >%,d hits.\nSet stricter filters and try again.", cntRm, MAX_CL);
			Popup.showWarningMessage(msg);
		}
	}
	catch (Exception e) {ErrorReport.print(e, "Remove large clusters"); bSuccess=false;}
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
		loadStatus.setText("Sort " + clVec.size() + " cluster...");
		
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
			
		if (loadStatus.getText().equals(Q.stop)) {bSuccess=false; return;} // uses Stopped
		
		// new cluster number
		// The clusters use the hit number, even if the perGene option was used; otherwise, it is confusing to view
		int nGrpNum=1;
		for (Cluster clObj : clVec) {
			if (clObj.clNum==0) continue; 	// clNum=0 filtered; 
			
			clObj.clNum = nGrpNum++; 		// reassign new number
			
			grpSizes.put(clObj.clNum, clObj.hitMap.size());	// For rows in DBdata: cl#->size
		}	
		// add cluster to hit
		for (int hitIdx : hitClMap.keySet()) {
			Cluster clObj = hitClMap.get(hitIdx);
			if (clObj.clNum!=0) 	
				hitIdx2Grp.put(hitIdx, clObj.clNum);	// To create rows in DBdata: hit->Cl#
		}
		if (TRC) Globals.prt(String.format("hitidx2Grp: %,d    grpSizes: %,d", hitIdx2Grp.size(), grpSizes.size()));
	}
    catch (Exception e) {ErrorReport.print(e, "Make output"); bSuccess=false;}
	}
	/***********************************************************
	 * Get filters from queryPanel; Create idxChrMap with inc/exc info
	 **************************************************/
	private void initFilters() { 
	try {
    	cutoff 		= qPanel.getClustN();
    	bPerSpecies = qPanel.isClPerSp();
    	
    	SpeciesPanel spPanel = qPanel.getSpeciesPanel();

    	for (int pos = 0; pos < spPanel.getNumSpecies(); pos++){
    		Species sObj = new Species();
    		sObj.spIdx 	= spPanel.getSpIdx(pos);
    		sObj.bExc 	= qPanel.isClExclude(pos); // either include/exclude/whatever	
    		sObj.bInc 	= qPanel.isClInclude(pos);
    		idxSpMap.put(sObj.spIdx, sObj);
    		
    		for (String idxstr : spPanel.getChrIdxList(pos)){
    			if(idxstr != null && idxstr.length() > 0) { 
        			int idx = Integer.parseInt(idxstr);
        			sObj.chrIdx.add(idx);
    			}
    		}  
    	} 
	}
    catch (Exception e) {ErrorReport.print(e, "FilterInit"); bSuccess=false;}
	}
	
	/*****************************************************************/
	private class Cluster {
		int clNum = 0;		// assigned while building, then reassigned in makeOutput
		String [] chrNum = {"",""};
		
		HashMap <Integer, DBdata> hitMap = new HashMap <Integer, DBdata> (); 
		int numHit = 0;
		
		HashMap <Integer, Integer> spCntMap = new HashMap <Integer, Integer> (); // spIdx, count unique genes
		HashSet <Integer> geneSet = new HashSet <Integer> ();		// all genes; no count dups
 		
		private Cluster(int num) {
			clNum = num;
			for (int idx : idxSpMap.keySet()) spCntMap.put(idx, 0);
		}
		private void addHit(DBdata dd) {
			hitMap.put(dd.hitIdx, dd);
			numHit++;
			
			for (int x=0; x<2; x++) {
				if (chrNum[x]=="" || dd.getChrNum(x).compareTo(chrNum[x])<0) 
					chrNum[x] = dd.getChrNum(x); // use smallest for sort
				
				// filters
				int spkey = dd.spIdx[x];
				int gnKey = dd.annotIdx[x];
				if (!geneSet.contains(gnKey)) {	// only count non-dups
					spCntMap.put(spkey, spCntMap.get(spkey)+1); 
					geneSet.add(gnKey);
					
					if (!idxGeneMap.containsKey(gnKey)) idxGeneMap.put(gnKey, dd.geneTag[x]);
				}
				
			}
			// the following may replace another cluster during mergeCluster
			geneClMap.put(dd.annotIdx[0], this);	
			geneClMap.put(dd.annotIdx[1], this);	
			hitClMap.put(dd.hitIdx, this);		
		}  
		private void mergeCluster(Cluster rmObj) {
			for (DBdata dd : rmObj.hitMap.values()) addHit(dd); // move hits from cObj to this cluster; CAS579 bug wasn't moving
			clVec.remove(rmObj);
		}
		private void clear() {
			clNum=0;
			numHit=0;
			hitMap.clear();
			spCntMap.clear();
			geneSet.clear();
		}
		public String toString() {
			String msg = String.format("%4d. #Hits %3d  #Genes %3d  Species: ", clNum, hitMap.size(), geneSet.size());
			for (int x : spCntMap.keySet()) {
				Species sp = idxSpMap.get(x);
				msg +=  sp.getDN() + " " + spCntMap.get(x) + " " + Globals.toTF(sp.bExc) + "   "; 
			}
			String x="";
			for (int idx : geneSet) {x += "  " + idxGeneMap.get(idx);}
			msg += "\n   " + x;
			return msg;
		}
	}
	private class Species {	// in idxChrMap
		int spIdx=0;
		boolean bExc=false, bInc=false;
		Vector <Integer> chrIdx = new Vector <Integer> ();
		
		private String getDN() {
			return qPanel.spPanel.getSpNameFromSpIdx(spIdx);
		}
	}
}
