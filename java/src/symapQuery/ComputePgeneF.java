package symapQuery;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

import javax.swing.JTextField;

import util.ErrorReport;
import backend.Utils;

public class ComputePgeneF {
    // Group the hits based on overlap on one side or the other. 
	// The hits have to be binned by location in order to avoid all-against-all searches.
    // Aside from that it's just building a graph and finding the connected pieces.
    //
    // If they've chosen "include only", then we build the groups ONLY with hits between included species.
    // Otherwise, we build the groups first, and then apply the include/exclude, as follows:
    // includes: the group must hit ALL the included species
    // excludes: the group must not hit ANY of the excluded species
    //
    // Output:
	private HashMap<Integer,Integer> hitIdx2Grp; // hitID to PgeneF num
	private HashMap<Integer,Integer> pgfSizes; 	// PgeneF sizes
	
	// Summary counts
	private HashMap<String,Integer> proj2regions; 
	
	// Input:
	private QueryPanel qPanel;
	private Vector <DBdata> rows;
	private JTextField progress;
	
	//From query panel
	private Vector <TreeSet<Integer>> allIncChrBySp =  new Vector<TreeSet<Integer>>(); 
	private TreeSet<Integer> 		allExcChr = new TreeSet<Integer>();
	private TreeSet<Integer> 		allIncChr = new TreeSet<Integer>(); 
	private boolean incOnly, unAnnotOnly, isLinkage;
	
	// Working
	private final int binsize = 100000;
	private HashMap<Integer, HashMap<Integer,HashSet<rhit>>> bins =
			new HashMap<Integer,HashMap<Integer,HashSet<rhit>>>(); // 1st index, grp; 2nd index, location bin
	
	private HashMap<Integer, Collection<rhit>> hit2grp = new HashMap<Integer, Collection<rhit>>();	
 	private HashMap<Integer,String>  grp2proj =          new HashMap<Integer,String>();
 	
 	
	public ComputePgeneF(
			Vector <DBdata> rowsFromDB, 
			QueryPanel lQueryPanel,
			JTextField progress,
			
			HashMap<Integer,Integer> hitIdx2Grp, 
			HashMap<Integer,Integer> pgfSizes, 
			
			HashMap<String,Integer> proj2regions) {
		
		this.rows = rowsFromDB;
		this.qPanel = lQueryPanel;
		this.progress = progress;
		
		this.hitIdx2Grp = hitIdx2Grp;
		this.pgfSizes = pgfSizes;
		
		this.proj2regions = proj2regions;
		
		computeGroups();
	}
	/************************************************************/
	private void computeGroups() {
	try {
		init();
		
		int rowNum=0;
	    for (DBdata dd : rows){
			if(progress != null) {
				if(progress.getText().equals("Cancelled")) return;
			}
	  		parseRowAndBin(dd); // creates hit2grp, bins included hits
	  		if (rowNum%Q.INC ==0) 
				progress.setText("PgeneF " + rowNum + " rows");
	  		rowNum++;
	    }
	    
	    // used to index into incCount array; where i is the species
	    TreeMap<Integer,Integer> chrIdxTmp = new TreeMap<Integer,Integer>();
	    for (int sp = 0; sp < allIncChrBySp.size(); sp++)	{
	    	for (int gidx : allIncChrBySp.get(sp)) 
	   			chrIdxTmp.put(gidx,sp);
    	}
    
    	// count the distinct regions on each chr and how many are annotated.
    	// This can be done group by group since whenever two regions overlap,
    	// their hits will be in the same group.
    	int grpNum=0;
	
	 /** Loop through hits **/
	    	
    	Set<Integer> hitList = hit2grp.keySet(); // since we alter hit2grp within
    	for (int hidx : hitList){
    		// First remove the duplicate hits from this grp -- don't think this is necessary
    		HashSet<rhit> fixedGrp = new HashSet<rhit>();
    		fixedGrp.addAll(hit2grp.get(hidx));
    		hit2grp.put(hidx, fixedGrp);
    		
    	/** Exclude hits that don't pass the interface checks **/
    		
    		// Each PgeneF group must have at least one hit from the
    		// Included species (this could have been done with spIdx)
    		if (allIncChrBySp.size() > 0)  {
    			int[] chrCnt = new int[allIncChrBySp.size()];
    			for (rhit h : hit2grp.get(hidx))
    			{
    				if (chrIdxTmp.containsKey(h.gidx1)){
    					int idx = chrIdxTmp.get(h.gidx1);
    					chrCnt[idx]++;
    				}
    				if (chrIdxTmp.containsKey(h.gidx2)) {
    					int idx = chrIdxTmp.get(h.gidx2);
    					chrCnt[idx]++;
    				}
    			}
    			boolean pass = true;
    			for (int i = 0; i < chrCnt.length; i++) {
    				if (chrCnt[i] == 0) {
    					pass = false;
    					break;
    				}
    			}
    			if (!pass) continue;
    		}
    		// Exclude selected species and Include 'No annotation of hits'
    		if (allExcChr.size() > 0 || allIncChr.size() > 0) {
    			boolean pass = true;
    			for (rhit h : hit2grp.get(hidx)){        				
    				if (allExcChr.contains(h.gidx1) || allExcChr.contains(h.gidx2)) {
    					pass = false;
    					break;
    				}
    				if (unAnnotOnly){
    					if (allIncChr.contains(h.gidx1)) {
    						if (h.annot1) {
    							pass = false;
    							break;
    						}
    					}
    					if (allIncChr.contains(h.gidx2)) {
    						if (h.annot2) {
    							pass = false;
    							break;
    						}
    					}
    				}
    			}
    			if (!pass) continue;
    		}
    		// Complete Linkage
    		if (isLinkage) {
    			HashMap<Integer,HashSet<Integer>> links = new HashMap<Integer,HashSet<Integer>>();
    			for (rhit h : hit2grp.get(hidx)){
    				if (allIncChr.contains(h.gidx1)) { 
    					if (!links.containsKey(h.pidx1)) {
    						links.put(h.pidx1, new HashSet<Integer>());
    					}
    					links.get(h.pidx1).add(h.pidx2);       					        					
    				}
    				if (allIncChr.contains(h.gidx2)) {
    					if (!links.containsKey(h.pidx2)) {
    						links.put(h.pidx2, new HashSet<Integer>());
    					}
    					links.get(h.pidx2).add(h.pidx1);       					
    				}
    			}
				int np = links.keySet().size();
			
				boolean pass = true;
    			for (int pidx : links.keySet()) {
    				if (links.get(pidx).size() < np-1) {
    					pass = false;
    					break;
    				}
    			}
    			if (!pass) continue;
    		}
    		grpNum++;
    		HashMap<String,Integer> pcounts = new HashMap<String,Integer>();
    		
    		// Count the regions in this group
    		HashMap<Integer,Vector<Integer>> slist = new HashMap<Integer,Vector<Integer>>();
    		HashMap<Integer,Vector<Integer>> elist = new HashMap<Integer,Vector<Integer>>();
    		HashMap<Integer,Vector<Boolean>> alist = new HashMap<Integer,Vector<Boolean>>();
    		for (rhit h : hit2grp.get(hidx)){
    			if (!slist.containsKey(h.gidx1)) {
    				slist.put(h.gidx1, new Vector<Integer>());
    				elist.put(h.gidx1, new Vector<Integer>());
    				alist.put(h.gidx1, new Vector<Boolean>());
    			}
    			if (!slist.containsKey(h.gidx2)) {
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
    		for (int gidx : slist.keySet()){
    			int[] results = new int[]{0,0};
    			clusterIntervals(slist.get(gidx), elist.get(gidx), alist.get(gidx),results);
    			int nRegions = results[0];
    			String pname = grp2proj.get(gidx);
    			
    			counterInc(proj2regions,pname,nRegions);
    			counterInc(pcounts,pname,nRegions);
    		}
    		
    		for (rhit h : hit2grp.get(hidx)) {
    			hitIdx2Grp.put(h.idx, grpNum);
    		}
    		pgfSizes.put(grpNum, hit2grp.get(hidx).size());
    		
    		if (grpNum%100 ==0) 
 				progress.setText("PgeneF " + rowNum + " final groups");
    	} // end for loop
	} 
	catch (Exception e) {ErrorReport.print(e, "Compute PgeneF"); }
	} // end computeGroups
    
	/****************************************************************/
	private boolean parseRowAndBin(DBdata dd) {
		try {
			if (incOnly) {
				if (!allIncChr.contains(dd.getChrIdx(0)) && !allIncChr.contains(dd.getChrIdx(1))){
					return false; // don't put in a group and will be skipped when loading the table. 
				}
			}
			
   			// Transfer data to nhit
			rhit nhit = new rhit();
			
			nhit.idx 	= dd.getHitIdx();
			nhit.gidx1  = dd.getChrIdx(0);
			nhit.gidx2  = dd.getChrIdx(1);
			nhit.pidx1  = dd.getSpIdx(0);
			nhit.pidx2  = dd.getSpIdx(1);
			int [] coords = dd.getCoords();
			nhit.s1 		= coords[0];
			nhit.e1 		= coords[1];
			nhit.s2 		= coords[2];
			nhit.e2 		= coords[3];
			nhit.annot1 = dd.hasAnno(0); 
			nhit.annot2 = dd.hasAnno(1);
			
		/** XXX Good hit - Bin for PgeneF **/
			// Go through the bins this hit is in and see if it overlaps any of the hits in them.
			// Also add it to the bins.
			TreeSet<rhit> olaps = new TreeSet<rhit>();
			
			if (!bins.containsKey(nhit.gidx1)) {
				bins.put(nhit.gidx1, new HashMap<Integer,HashSet<rhit>>());					
			}   	
			HashMap<Integer,HashSet<rhit>> bin1 = bins.get(nhit.gidx1);
			if (!bins.containsKey(nhit.gidx2)) {
				bins.put(nhit.gidx2, new HashMap<Integer,HashSet<rhit>>());					
			}   	
			HashMap<Integer,HashSet<rhit>> bin2 = bins.get(nhit.gidx2);
			
			for(int b1 = (int)(nhit.s1/binsize); b1 <= (int)(nhit.e1/binsize); b1++){
				if (!bin1.containsKey(b1)) {
					bin1.put(b1, new HashSet<rhit>());		
				}
				HashSet<rhit> bin = bin1.get(b1);
				for (rhit rh : bin) {
					if ((rh.gidx1==nhit.gidx1 && Utils.intervalsOverlap(nhit.s1,nhit.e1,rh.s1,rh.e1,0)) || 
						(rh.gidx2==nhit.gidx1 && Utils.intervalsOverlap(nhit.s1,nhit.e1,rh.s2,rh.e2,0)) )
					{
						olaps.add(rh);
					}
				}
				bin.add(nhit);
			}
			for(int b2 = (int)(nhit.s2/binsize); b2 <= (int)(nhit.e2/binsize); b2++){
				if (!bin2.containsKey(b2)) {
					bin2.put(b2, new HashSet<rhit>());		
				}
				HashSet<rhit> bin = bin2.get(b2);
				for (rhit rh : bin){
					if ( (rh.gidx1==nhit.gidx2 && Utils.intervalsOverlap(nhit.s2,nhit.e2,rh.s1,rh.e1,0)) || 
						 (rh.gidx2==nhit.gidx2 && Utils.intervalsOverlap(nhit.s2,nhit.e2,rh.s2,rh.e2,0)) )
					{
						olaps.add(rh);
					}
				}
				bin.add(nhit);
			}
			
		// Combine all the earlier groups connected by the overlaps (or make a new group if no overlaps)
    		Collection<rhit> maxGrp = null; 
    		int maxGrpIdx = 0;
    		HashSet<Integer> grpList = new HashSet<Integer>();
    			
			// First get the non-redundant list of groups, and 
    			// also find the biggest existing  group so we can reuse it. 
    			// (Because HashSet adds were costing a lot of time.)
			
			for (rhit rhit : olaps){
				int grpHitIdx = rhit.grpHitIdx;
				if (grpHitIdx == 0) continue; // it's the new hit
				if (!grpList.contains(grpHitIdx)){
					grpList.add(grpHitIdx);
					if (maxGrp == null){
						maxGrp = hit2grp.get(grpHitIdx);
						maxGrpIdx = grpHitIdx;
					}
					else{
						if (hit2grp.get(grpHitIdx).size() > maxGrp.size()){
							maxGrp = hit2grp.get(grpHitIdx);
							maxGrpIdx = grpHitIdx;
						}
					}
				}
			}
			if (maxGrp != null){
    			for (int idx : grpList){
    				if (idx != maxGrpIdx ){
    					maxGrp.addAll(hit2grp.get(idx));
    					for (rhit rh2 : hit2grp.get(idx)) {
    						rh2.grpHitIdx = maxGrpIdx;
    					}
    					hit2grp.remove(idx);		    				
    				}
    			}
    			maxGrp.add(nhit);
    			nhit.grpHitIdx = maxGrpIdx;
			}
			else {
    			hit2grp.put(nhit.idx, new Vector<rhit>());
    			hit2grp.get(nhit.idx).add(nhit);
    			nhit.grpHitIdx = nhit.idx;
    			// System.out.println("QQQ new grp idx:" + nhit.idx); same as 4.2
			}
			return true;
		}
		catch (Exception e) {ErrorReport.print(e, "Parse rs hit record"); return false;}
	}
	
	private void clusterIntervals(Vector<Integer> starts, Vector<Integer> ends, Vector<Boolean> annots,
			int[] results)
	{
    	Vector<Integer> cstarts = new Vector<Integer>();
    	Vector<Integer> cends = new Vector<Integer>();
    	Vector<Boolean> cannots = new Vector<Boolean>();
    	for (int i = 0; i < starts.size(); i++) {
    		Vector<Integer> chits = new Vector<Integer>();
    		boolean cannot = annots.get(i);
    		int mins = starts.get(i);
    		int maxe = ends.get(i);
    		for (int j = 0; j < cstarts.size(); j++){
    			if (Utils.intervalsOverlap(starts.get(i), ends.get(i), cstarts.get(j), cends.get(j), 0)){
    				chits.add(j);
    				cannot = (cannot || cannots.get(j));
    				mins = Math.min(mins, cstarts.get(j));
    				maxe = Math.max(maxe, cends.get(j));
    			}
    		}
    		if (chits.size() == 0){
    			cstarts.add(mins);
    			cends.add(maxe);
    			cannots.add(cannot);
    		}
    		else {
    			// grow the first one and remove the rest
    			cstarts.set(chits.get(0),mins);
    			cends.set(chits.get(0),maxe);
    			cannots.set(chits.get(0), cannot);
    			for (int k = chits.size()-1; k >= 1; k--){
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
    	for (boolean b : cannots){
    		if (b) nannot++;
    	}
    	results[1] = nannot;
	}
	private void init() {
    	incOnly = qPanel.isOnlyInc();
    	unAnnotOnly = qPanel.isUnannotInc();
    	isLinkage = qPanel.isLinkageInc();
    	SpeciesSelectPanel spPanel = qPanel.getSpeciesPanel();

    	for (int i = 0; i < spPanel.getNumSpecies(); i++){
    		boolean incSp = qPanel.isInclude(i);
    		boolean excSp = qPanel.isExclude(i);
    		
    		TreeSet<Integer> incs = (incSp) ? new TreeSet<Integer>() : null;
    		
    		for (String idxstr : spPanel.getChrIdxList(i))
    		{
    			if(idxstr != null && idxstr.length() > 0) { 
        			int idx = Integer.parseInt(idxstr);
        			grp2proj.put(idx, qPanel.getSpeciesPanel().getSpName(i));
        			if (!incSp && !excSp) continue;
        			
        			if (incSp){
        				incs.add(idx);
        				allIncChr.add(idx);
        			}
        			if (excSp) {
        				allExcChr.add(idx);
        			}
    			}
    		}
    		if (incSp) {
    			allIncChrBySp.add(incs);
    		}       				
    	} // end going through chromosomes
	}
	private void counterInc(Map<String,Integer> ctr, String key, int inc  )
	{
		if (inc == 0) return;
		if (!ctr.containsKey(key))
			ctr.put(key, inc);
		else
			ctr.put(key, ctr.get(key)+inc);
	}
	private class rhit implements Comparable<rhit>
	{
		int gidx1, gidx2, pidx1, pidx2;
		int s1, s2, e1, e2, idx;
		int grpHitIdx; // the index of the group this hit is in
		boolean annot1 = false, annot2 = false;
		
		public int compareTo(rhit h)
		{
			if (idx < h.idx) return -1;
			else if (idx > h.idx) return 1;
			return 0;
		}
	}
}
