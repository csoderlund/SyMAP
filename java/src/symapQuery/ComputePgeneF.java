package symapQuery;

import java.sql.ResultSet;
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
import util.Utilities;
import backend.Utils;

public class ComputePgeneF {
    // Group the hits based on overlap on one side or the other. It's a bit complicated because
    // the hits have to be binned by location in order to avoid all-against-all searches.
    // Aside from that it's just building a graph and finding the connected pieces.
    //
    // If they've chosen "include only", then we build the groups ONLY with hits between included species.
    // Otherwise, we build the groups first, and then apply the include/exclude, as follows:
    // includes: the group must hit ALL the included species
    // excludes: the group must not hit ANY of the excluded species
    //
   
	public static void run(HashMap<Integer,Integer> out, 
		HashMap<Integer,Integer> annotMap, HashMap<Integer,Integer> pgfSizes, 
		ResultSet rs, 
		Vector<TreeSet<Integer>> includes, TreeSet<Integer> excludes, TreeSet<Integer> allIncludes, 
		boolean incOnly, boolean orphOnly, boolean unAnnot, boolean isClique,
		JTextField progress,
		HashMap<String,Integer> proj2orphs, 
		HashMap<Integer,HashMap<String,Integer>> grp2projcounts, 
		HashMap<String,Integer> proj2regions, HashMap<String,Integer> proj2annot) 
	{
	try {
		rs.beforeFirst();
		int binsize = 100000;
	
	    	class rhit implements Comparable<rhit>
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
   
	    	HashMap<Integer, Collection<rhit>> hit2grp = new HashMap<Integer, Collection<rhit>>();
	
	    	TreeMap<Integer,Integer> grpIdx2IncIdx = new TreeMap<Integer,Integer>();
	    	for (int i = 0; i < includes.size(); i++)
	    	{
	    		for (int gidx : includes.get(i)) {
	    			grpIdx2IncIdx.put(gidx,i);
	    		}
	    	}
		// bins: first index, grp; second index, location bin
	    	HashMap<Integer,HashMap<Integer,HashSet<rhit>>> bins = new HashMap<Integer,HashMap<Integer,HashSet<rhit>>>();
	    	int prevIdx = -1;
	    	rhit prev_h = null;
	    	HashMap<Integer,Integer> projIdx2SpecNum = new HashMap<Integer,Integer>();
	    	HashMap<Integer,String> grp2proj = new HashMap<Integer,String>();
		
	    	Utilities.init_rs2col();
	 
	    	while(rs.next())
	    	{
			if(progress != null) {
				if(progress.getText().equals("Cancelled"))
					return;
			}
	
			boolean isAnnot = rs.getBoolean(Utilities.rs2col(Q.isAnnot,rs));
	  		if (!isAnnot)  { 
	      		rhit h = new rhit();
	       			
	    			h.idx = rs.getInt(Utilities.rs2col(Q.HITIDX,rs));
	    			h.gidx1 = rs.getInt(Utilities.rs2col(Q.GRPIDX1,rs));
	    			h.gidx2 = rs.getInt(Utilities.rs2col(Q.GRPIDX2,rs));
	    			h.pidx1 = rs.getInt(Utilities.rs2col(Q.PROJ1IDX,rs));
	    			h.pidx2 = rs.getInt(Utilities.rs2col(Q.PROJ2IDX,rs));
	    			
	    			grp2proj.put(h.gidx1, rs.getString(Utilities.rs2col(Q.SPECNAME1,rs)).toLowerCase());
	    			grp2proj.put(h.gidx2, rs.getString(Utilities.rs2col(Q.SPECNAME2,rs)).toLowerCase());
	    			    			
	    			h.s1 = rs.getInt(Utilities.rs2col(Q.GRP1START,rs));
	    			h.e1 = rs.getInt(Utilities.rs2col(Q.GRP1END,rs));
	    			h.s2 = rs.getInt(Utilities.rs2col(Q.GRP2START,rs));
	    			h.e2 = rs.getInt(Utilities.rs2col(Q.GRP2END,rs));
	    			h.grpHitIdx = 0;
	
	    			int agidx = rs.getInt(Utilities.rs2col(Q.AGIDX,rs));
	
	    			if ( agidx > 0)
	    			{
	    				if (agidx == h.gidx1) h.annot1 = true;
	    				else if (agidx == h.gidx2) h.annot2 = true;
	    			}
	    			if (h.idx == prevIdx) 
	    			{
	    				// collect the subsequent annotations from the instance of this hit
	    				// that we added to the groups.
	    				prev_h.annot1 = (prev_h.annot1 || h.annot1);
	    				prev_h.annot2 = (prev_h.annot2 || h.annot2);
	    				continue; 
	    			}
	       		prevIdx = h.idx;
	       		prev_h = h;
	       
	    			if (incOnly) {
	    				if (!allIncludes.contains(h.gidx1) && !allIncludes.contains(h.gidx2))
	    				{
	    					// Now it won't get put in a group which means it will be skipped
	    					// when loading the table. 
	    					continue;
	    				}
	    			}
	    			// Go through the bins this hit is in and see if it overlaps any of the hits in them.
	    			// Also add it to the bins.
	    			
	    			TreeSet<rhit> olaps = new TreeSet<rhit>();
	    			
				if (!bins.containsKey(h.gidx1)) {
					bins.put(h.gidx1, new HashMap<Integer,HashSet<rhit>>());					
				}   	
				HashMap<Integer,HashSet<rhit>> bin1 = bins.get(h.gidx1);
				if (!bins.containsKey(h.gidx2)) {
					bins.put(h.gidx2, new HashMap<Integer,HashSet<rhit>>());					
				}   	
				HashMap<Integer,HashSet<rhit>> bin2 = bins.get(h.gidx2);
				
	    			for(int b1 = (int)(h.s1/binsize); b1 <= (int)(h.e1/binsize); b1++)
	    			{
	    				if (!bin1.containsKey(b1)) {
	    					bin1.put(b1, new HashSet<rhit>());		
	    				}
	    				HashSet<rhit> bin = bin1.get(b1);
	    				for (rhit rh : bin) {
	    					if ((rh.gidx1==h.gidx1 && Utils.intervalsOverlap(h.s1,h.e1,rh.s1,rh.e1,0)) || 
	    						(rh.gidx2==h.gidx1 && Utils.intervalsOverlap(h.s1,h.e1,rh.s2,rh.e2,0)) )
	    					{
	    						olaps.add(rh);
	    					}
	    				}
	    				bin.add(h);
	    			}
	    			for(int b2 = (int)(h.s2/binsize); b2 <= (int)(h.e2/binsize); b2++)
	    			{
	    				if (!bin2.containsKey(b2)) {
	    					bin2.put(b2, new HashSet<rhit>());		
	    				}
	    				HashSet<rhit> bin = bin2.get(b2);
	    				for (rhit rh : bin){
	    					if ( (rh.gidx1==h.gidx2 && Utils.intervalsOverlap(h.s2,h.e2,rh.s1,rh.e1,0)) || 
	    						 (rh.gidx2==h.gidx2 && Utils.intervalsOverlap(h.s2,h.e2,rh.s2,rh.e2,0)) )
	    					{
	    						olaps.add(rh);
	    					}
	    				}
	    				bin.add(h);
	    			}
				// Combine all the earlier groups connected by the overlaps (or make a new group if no overlaps)
	    			if (!orphOnly) // for just getting orphans of one species, can skip this lengthy step
	    			{
		    			Collection<rhit> maxGrp = null; 
		    			int maxGrpIdx = 0;
		    			HashSet<Integer> grpList = new HashSet<Integer>();
		    			
	    				// First get the non-redundant list of groups, and 
		    			// also find the biggest existing  group so we can reuse it. 
		    			// (Because HashSet adds were costing a lot of time.)
		    			
		    			for (rhit rhit : olaps)
		    			{
		    				int grpHitIdx = rhit.grpHitIdx;
		    				if (grpHitIdx == 0) continue; // it's the new hit
		    				if (!grpList.contains(grpHitIdx))
		    				{
		    					grpList.add(grpHitIdx);
		    					if (maxGrp == null)
		    					{
		    						maxGrp = hit2grp.get(grpHitIdx);
		    						maxGrpIdx = grpHitIdx;
		    					}
		    					else
		    					{
		    						if (hit2grp.get(grpHitIdx).size() > maxGrp.size())
		    						{
		    							maxGrp = hit2grp.get(grpHitIdx);
		    							maxGrpIdx = grpHitIdx;
		    						}
		    					}
		    				}
		    			}
		    			if (maxGrp != null)
		    			{
			    			for (int idx : grpList)
			    			{
			    				if (idx != maxGrpIdx )
			    				{
			    					maxGrp.addAll(hit2grp.get(idx));
			    					for (rhit rh2 : hit2grp.get(idx))
			    					{
			    						rh2.grpHitIdx = maxGrpIdx;
			    					}
			    					hit2grp.remove(idx);		    				
			    				}
			    			}
			    			maxGrp.add(h);
			    			h.grpHitIdx = maxGrpIdx;
		    			}
		    			else
		    			{
			    			hit2grp.put(h.idx, new Vector<rhit>());
			    			hit2grp.get(h.idx).add(h);
			    			h.grpHitIdx = h.idx;
		    			}
	    			}
	    			else
	    			{
		    			hit2grp.put(h.idx, new Vector<rhit>());
		    			h.grpHitIdx = h.idx;	    				
	    			}
	        	} // end not isAnnot
	  		
			else // isAnnot
			{
				// Annotations come last so the groups should already be formed. 
				// We just check whether the annot is in a group, i.e. not an orphan,
				// and also whether it satisifes the include/excludes.
				// If it passes all tests we add it to the annotMap and it will be shown.
	    			int gidx = rs.getInt(Utilities.rs2col(Q.AGIDX,rs));
	    			int aidx = rs.getInt(Utilities.rs2col(Q.AIDX,rs));
	    			int s = rs.getInt(Utilities.rs2col(Q.ASTART,rs));
	    			int e = rs.getInt(Utilities.rs2col(Q.AEND,rs));
	    			int pidx = rs.getInt(Utilities.rs2col(Q.PROJIDX,rs));
	    			String pname = rs.getString(Utilities.rs2col(Q.SPECNAME1,rs)).toLowerCase();
	    			
				// It must contain all the included species, (i.e., there can only be one included).
				// It must not be excluded.
				if (includes.size() != 0)
				{
					if (includes.size() > 1) continue;
					if (!includes.firstElement().contains(gidx)) continue;
				}
				if (excludes.contains(gidx)) continue;
	
	    			boolean inGrp = false;
	    			if (!projIdx2SpecNum.containsKey(pidx))
	    			{
	    				int curNum = 1 + projIdx2SpecNum.keySet().size();
	    				projIdx2SpecNum.put(pidx, curNum);
	    			}
	    			int specNum = projIdx2SpecNum.get(pidx); // this is the "group" number for orphans ???
				
	    			if (bins.containsKey(gidx))
	    			{
					HashMap<Integer,HashSet<rhit>> gbin = bins.get(gidx);
		    			for(int b = (int)(s/binsize); b <= (int)(e/binsize); b++)
		    			{
		    				if (gbin.containsKey(b))
		    				{
			    				for (rhit rh : gbin.get(b))
			    				{
			    					if ((rh.gidx1==gidx && Utils.intervalsOverlap(s,e,rh.s1,rh.e1,0)) || 
			    						(rh.gidx2==gidx && Utils.intervalsOverlap(s,e,rh.s2,rh.e2,0)) )
			    					{
			    						inGrp = true;
			    						break;
			    					}
			    				}
		    				}
		    				if (inGrp) break;
		    			}
	    			}
	    			if (!inGrp)
	    			{
	    				annotMap.put(aidx,specNum);
	    				counterInc(proj2orphs,pname,1);
	    			}
			} // end isAnnot
	    	} // end very long while loop		

    	
	    	// Lastly number the groups and fill the output map.
	    	// Also check the includes/excludes.
		for (int grp : projIdx2SpecNum.values())
		{
			pgfSizes.put(grp,0);
		}
		for (int grp : annotMap.values())
		{
			pgfSizes.put(grp, 1 + pgfSizes.get(grp));
		}
	    	int grpNum =  projIdx2SpecNum.keySet().size();
	    	
	    	// We also want to count the distinct regions on each chr and how
	    	// many are annotated.
	    	// This can be done group by group since whenever two regions overlap,
	    	// their hits will be in the same group.
	    	
	    	Set<Integer> hitList = hit2grp.keySet(); // since we alter hit2grp within
	    	for (int idx : hitList)
	    	{
	    		// First remove the duplicate hits from this grp
	    		HashSet<rhit> fixedGrp = new HashSet<rhit>();
	    		fixedGrp.addAll(hit2grp.get(idx));
	    		hit2grp.put(idx, fixedGrp);
	    		if (includes.size() > 0)
	    		{
	    			// Check that this group includes all of the include species,
	    			// i.e. that at least one grpidx from each of the includes is
	    			// found on at least one hit in the group.
	    			int[] incCount = new int[includes.size()];
	    			for (rhit h : hit2grp.get(idx))
	    			{
	    				if (grpIdx2IncIdx.containsKey(h.gidx1)){
	    					incCount[grpIdx2IncIdx.get(h.gidx1)]++;
	    				}
	    				if (grpIdx2IncIdx.containsKey(h.gidx2)) {
	    					incCount[grpIdx2IncIdx.get(h.gidx2)]++;
	    				}
	    			}
	    			boolean pass = true;
	    			for (int i = 0; i < incCount.length; i++)
	    			{
	    				if (0 == incCount[i])
	    				{
	    					pass = false;
	    					break;
	    				}
	    			}
	    			if (!pass) continue;
	    		}
	    		if (excludes.size() > 0 || allIncludes.size() > 0)
	    		{
	    			// Check that none of the hits is to a grpidx from an excluded species.
	    			boolean pass = true;
	    			for (rhit h : hit2grp.get(idx))
	    			{        				
	    				if (excludes.contains(h.gidx1) || excludes.contains(h.gidx2)) 
	    				{
	    					pass = false;
	    					break;
	    				}
	    				if (unAnnot)
	    				{
	    					if (allIncludes.contains(h.gidx1))
	    					{
	    						if (h.annot1) 
	    						{
	    							pass = false;
	    							break;
	    						}
	    					}
	    					if (allIncludes.contains(h.gidx2))
	    					{
	    						if (h.annot2) 
	    						{
	    							pass = false;
	    							break;
	    						}
	    					}
	    				}
	    			}
	    			if (!pass) continue;
	    		}
	    		if (isClique)
	    		{
	    			HashMap<Integer,HashSet<Integer>> links = new HashMap<Integer,HashSet<Integer>>();
	    			for (rhit h : hit2grp.get(idx))
	    			{
	    				if (allIncludes.contains(h.gidx1))
	    				{
	    					if (!links.containsKey(h.pidx1)) {
	    						links.put(h.pidx1, new HashSet<Integer>());
	    					}
	    					links.get(h.pidx1).add(h.pidx2);       					        					
	    				}
	       			if (allIncludes.contains(h.gidx2))
	    				{
	    					if (!links.containsKey(h.pidx2)) {
	    						links.put(h.pidx2, new HashSet<Integer>());
	    					}
	    					links.get(h.pidx2).add(h.pidx1);       					
	    				}
	    			}
	    			int np = links.keySet().size();
	    			boolean pass = true;
	    			for (int pidx : links.keySet())
	    			{
	    				if (links.get(pidx).size() < np-1)
	    				{
	    					pass = false;
	    					break;
	    				}
	    			}
	    			if (!pass) continue;
	    		}
	    		grpNum++;
	    		grp2projcounts.put(grpNum, new HashMap<String,Integer>());
	    		HashMap<String,Integer> pcounts = grp2projcounts.get(grpNum);
	    		
	    		// Count the regions in this group
	    		HashMap<Integer,Vector<Integer>> slist = new HashMap<Integer,Vector<Integer>>();
	    		HashMap<Integer,Vector<Integer>> elist = new HashMap<Integer,Vector<Integer>>();
	    		HashMap<Integer,Vector<Boolean>> alist = new HashMap<Integer,Vector<Boolean>>();
	    		for (rhit h : hit2grp.get(idx))
	    		{
	   			if (!slist.containsKey(h.gidx1))
	    			{
	    				slist.put(h.gidx1, new Vector<Integer>());
	    				elist.put(h.gidx1, new Vector<Integer>());
	    				alist.put(h.gidx1, new Vector<Boolean>());
	    			}
	   			if (!slist.containsKey(h.gidx2))
	    			{
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
	    		for (int gidx : slist.keySet())
	    		{
	    			int[] results = new int[]{0,0};
	    			clusterIntervals(slist.get(gidx), elist.get(gidx), alist.get(gidx),results);
	    			int nRegions = results[0];
	    			int nAnnot = results[1];
	    			String pname = grp2proj.get(gidx);
	    			counterInc(proj2regions,pname,nRegions);
	    			counterInc(proj2annot,pname,nAnnot);
	    			counterInc(pcounts,pname,nRegions);
	    		}
	    		
	    		for (rhit h : hit2grp.get(idx)) {
	    			out.put(h.idx, grpNum);
	    		}
	    		pgfSizes.put(grpNum, hit2grp.get(idx).size());
	    	} // end for loop
	    
	} 
	catch (Exception e) {ErrorReport.print(e, "Compute PgeneF"); }
	} // end computeGroups
    
	private static void clusterIntervals(Vector<Integer> starts, Vector<Integer> ends, Vector<Boolean> annots,
			int[] results)
	{
	    	Vector<Integer> cstarts = new Vector<Integer>();
	    	Vector<Integer> cends = new Vector<Integer>();
	    	Vector<Boolean> cannots = new Vector<Boolean>();
	    	for (int i = 0; i < starts.size(); i++)
	    	{
	    		Vector<Integer> chits = new Vector<Integer>();
	    		boolean cannot = annots.get(i);
	    		int mins = starts.get(i);
	    		int maxe = ends.get(i);
	    		for (int j = 0; j < cstarts.size(); j++)
	    		{
	    			if (Utils.intervalsOverlap(starts.get(i), ends.get(i), cstarts.get(j), cends.get(j), 0))
	    			{
	    				chits.add(j);
	    				cannot = (cannot || cannots.get(j));
	    				mins = Math.min(mins, cstarts.get(j));
	    				maxe = Math.max(maxe, cends.get(j));
	    			}
	    		}
	    		if (chits.size() == 0)
	    		{
	    			cstarts.add(mins);
	    			cends.add(maxe);
	    			cannots.add(cannot);
	    		}
	    		else 
	    		{
	    			// grow the first one and remove the rest
	    			cstarts.set(chits.get(0),mins);
	    			cends.set(chits.get(0),maxe);
	    			cannots.set(chits.get(0), cannot);
	    			for (int k = chits.size()-1; k >= 1; k--)
	    			{
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
	    	for (boolean b : cannots)
	    	{
	    		if (b) nannot++;
	    	}
	    	results[1] = nannot;
	}
	static private void counterInc(Map<String,Integer> ctr, String key, int inc  )
	{
		if (inc == 0) return;
		if (!ctr.containsKey(key))
			ctr.put(key, inc);
		else
			ctr.put(key, ctr.get(key)+inc);
	}
}
