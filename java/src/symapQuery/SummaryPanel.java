package symapQuery;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;

import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;

import symap.Globals;
import util.ErrorReport;
import util.Jcomp;
import util.Utilities;

/**************************************************
 * * The summary at the bottom of the results table.
 * Compute the summary in one place (except regions)
 * Called from TableMainPanel.buildTable
 */

public class SummaryPanel  extends JPanel {
	private static final long serialVersionUID = 1L;
	private JPanel statsPanel=null;
	
	private Vector <DBdata> rowsFromDB;
	private  QueryPanel qPanel;
	private SpeciesPanel spPanel;
	private HashMap <String, String> projGrpMap; 
	private HashMap <Integer, Integer> geneCntMap; // computed in DBdata.makeGeneCounts
	
	private boolean isSelf=false; 			  
	
	public SummaryPanel(Vector <DBdata> rowsFromDB, QueryPanel qPanel,  // rows have been merged
			HashMap <String, String> projMap, 			// PgeneN and Multi summary 
			HashMap <Integer, Integer> geneCntMap) {    // spIdx annotidx counts
		
		if (statsPanel == null) statsPanel = Jcomp.createPagePanel();
		if(rowsFromDB==null || rowsFromDB.size()==0) return;
		
		this.rowsFromDB = rowsFromDB;
		this.qPanel = qPanel;
		this.projGrpMap = projMap;
		this.geneCntMap = geneCntMap;
		isSelf = qPanel.isSelf();
		spPanel = qPanel.getSpeciesPanel();
		
		computeStats(); 
	}
	protected JPanel getPanel() {return statsPanel;}
	
	/****************************************************
	 * Compute Stats - fills in the rest of panel
	 */
	private void computeStats() {
		
		HashMap<String,String> chrStrMap = new HashMap<String, String>();
		HashSet<Integer> gidxSet = new HashSet<Integer> ();
		
		// Create Chr: x of x
    	for (DBdata dd : rowsFromDB) {
    		if (!gidxSet.contains(dd.getChrIdx(0))) gidxSet.add(dd.getChrIdx(0));
    		if (!gidxSet.contains(dd.getChrIdx(1))) gidxSet.add(dd.getChrIdx(1));
    	}
   
    	Vector <Integer> order = new Vector <Integer> (); // needed so stats are in order
    	
    	for (int p = 0; p < spPanel.getNumSpecies(); p++) { // Chromosome stats; used if not Group
    		String projName = spPanel.getSpName(p);
    		if (!isSelf || p==0) order.add(spPanel.getSpIdx(p));
    		else                 order.add(order.get(0)+1);  // +1 Used in DBdata.makeGeneCnts and below
    		
    		String [] gidxList =  spPanel.getChrIdxList(p);
    		HashSet <Integer> spGidx = new HashSet <Integer> ();
    		for (String c : gidxList) 
    			spGidx.add(Integer.parseInt(c));
    		
    		int nchrs = gidxList.length;
    		int xchr = 0;
    		for (int idx : gidxSet) 
    			if (spGidx.contains(idx)) xchr++;
    		 		
    		String chrStr = String.format("%2d of %2d", xchr, nchrs);
    		chrStrMap.put(projName, chrStr);
    	}
    	gidxSet.clear();
	    
		if (qPanel.isSingle()) 	createSingle(chrStrMap);
		else 					createPairHits(chrStrMap, order);
	}
	/*****************************************************
	 * Has Gene:  Either %d Both %d  None %d      Has Block: Yes %d  No %d 
	 * 
	 * Sp1  Hits: %d   Annotated: %d   Chr: %d  Regions: %d
	 * 
	 * Works for isSelf because SpeciesPanel uses spIdx+1 for 2nd project
	 */
	private void createPairHits(HashMap <String, String>  chrStrMap, // project name, N chr of M
							   Vector <Integer> order) {
		try {
			int either=0, both=0, none=0, block=0, collinear=0, maxGrp=0, minor=0; 
			HashMap <Integer, Integer> proj2hits 	=  new HashMap <Integer, Integer> ();
			HashMap <Integer, Integer> proj2annot 	= new HashMap <Integer, Integer> ();
			HashMap <String, Integer>  blockCnt		= new HashMap <String, Integer> ();
			HashMap <String, Integer>  collinearCnt = new HashMap <String, Integer> ();
			
			for (DBdata dd : rowsFromDB) {
				int spIdx1 = dd.getSpIdx(0);
				int spIdx2 = (isSelf) ? spIdx1+1 : dd.getSpIdx(1); 
				
				counterInc(proj2hits, spIdx1, 1);
				counterInc(proj2hits, spIdx2, 1);
				
				boolean anno1 = dd.hasGene(0);
				boolean anno2 = dd.hasGene(1);
				if (anno1) counterInc(proj2annot, spIdx1, 1);
				if (anno2) counterInc(proj2annot, spIdx2, 1);
				
				if (anno1 && anno2) both++;
				else if (!anno1 && !anno2) none++;
				else either++;
				
				if (dd.geneTag[0].endsWith(Q.minor)) minor++;
				if (dd.geneTag[1].endsWith(Q.minor)) minor++;
				
				if (dd.hasBlock()) {
					block++;
					counterInc(blockCnt, dd.blockStr, 1);		 
				}
				if (dd.hasCollinear()) {
					collinear++;
					counterInc(collinearCnt, dd.collinearStr, 1); 
				}
				if (dd.hasGroup()) { 
					String [] tok = dd.grpStr.split(Q.GROUP);
					int n = Utilities.getInt(tok[tok.length-1]);
					if (n>maxGrp) maxGrp=n;
				}
			}
		// Add 1st line of summary
			String label = String.format("Block (Hits): %,d (%,d)   Annotated: Both %,d  One %,d  None %,d", 
					 blockCnt.size(), block, both, either, none); 				
			if (minor>0)	 label += String.format("  Minor %,d", minor); 		// only >0 if Every+ or Gene#
			if (maxGrp>0)    label += String.format("   Groups: #%,d", maxGrp); // runs off page if not before collinear, which then runs off page..
			if (collinear>0) label += String.format("   Collinear (Hits): %,d (%,d)", collinearCnt.size(), collinear);
			
			JLabel theLabel = Jcomp.createMonoLabel(label, 12);
			statsPanel.add(theLabel);
			statsPanel.add(Box.createVerticalStrut(5));
			
		// 2nd lines with Project gene counts
			// format:
			int w=5, h=0, a=0, g=0, x=0; // find width
			for (int spIdx : order) {
				String pName;
				if (!isSelf) pName = spPanel.getSpNameFromSpIdx(spIdx);
				else         pName = spPanel.getSelfName(x++);
				
				w = Math.max(w, pName.length());
				
				if (order.size()>2) {// hits is all the same unless more than 2 species
					int nHit =  proj2hits.containsKey(spIdx) ? proj2hits.get(spIdx) : 0;
					h = Math.max(h, String.format("%,d", nHit).length());
				}
				int nAnno = proj2annot.containsKey(spIdx) ? proj2annot.get(spIdx) : 0;
				a = Math.max(a, String.format("%,d", nAnno).length());
				
				int nUnq = geneCntMap.containsKey(spIdx) ? geneCntMap.get(spIdx) : 0;
				g = Math.max(g, String.format("%,d", nUnq).length());
				
				// CAS579c format wrong; if (isSelf) break;
			}
			String nf;
			if (h==0) nf = "%-" + w + "s   Annotated: %," + a + "d   Genes: %," + g + "d";
			else 	  nf = "%-" + w + "s   Hits: %," + h + "d   Annotated: %," + a + "d   Genes: %," + g + "d";
			
			// Loop through species
			x=0;
			for (int spIdx : order ) {
				String pName;
				if (!isSelf) pName = spPanel.getSpNameFromSpIdx(spIdx);
				else         pName = spPanel.getSelfName(x++);
				
				int nHit =  proj2hits.containsKey(spIdx)  ? proj2hits.get(spIdx)  : 0;
				int nAnno = proj2annot.containsKey(spIdx) ? proj2annot.get(spIdx) : 0;
				int nUnq =  geneCntMap.containsKey(spIdx) ? geneCntMap.get(spIdx) : 0; 	// isSelf will have spIdx
				
				if (h==0) label = String.format(nf, pName,  nAnno, nUnq);
				else      label = String.format(nf, pName, nHit, nAnno, nUnq);
				
				String chrStr =  chrStrMap.containsKey(pName)  ? chrStrMap.get(pName) : "Unk";
				if (projGrpMap.containsKey(pName)) label += "   " + projGrpMap.get(pName); // Multi&PgeneF has project stats
				else 							   label += "    Chr: " + chrStr;		   // else, Chr n of m
				
				theLabel = Jcomp.createMonoLabel(label, 12);
				
				statsPanel.add(Box.createVerticalStrut(2));
				statsPanel.add(theLabel);
			}
		}
		catch (Exception e) {ErrorReport.print(e, "Create hit summary");}
	}
	private void createSingle(HashMap<String, String> chrStrMap) { // CAS579c simplify
		try {
			String dname="", chrStr="";
			for (String x : chrStrMap.keySet()) {
				if (!x.endsWith(Globals.SS_X)) {
					dname = x;
					chrStr = chrStrMap.get(x);
					break;
				}
			}
			if (dname.equals("")) {// probably ends with SS_Z
				for (String x : chrStrMap.keySet()) {
					dname = x;
					chrStr = chrStrMap.get(x);
					break;
				}
			}
			int cnt=rowsFromDB.size();
					
			String type = (qPanel.isSingleOrphan()) ? "Orphans" : "Genes"; 
			String label = String.format("%-13s    %s: %,d     Chr: %s",dname, type, cnt, chrStr);
			JLabel theLabel = Jcomp.createMonoLabel(label, 12);
			
			statsPanel.add(Box.createVerticalStrut(4));
			statsPanel.add(theLabel);
		}
		catch (Exception e) {ErrorReport.print(e, "Create orphan summary");}
	}
	
		
	private void counterInc(HashMap<Integer,Integer> ctr, Integer key, int inc) {
    	if (inc == 0) return;
		if (!ctr.containsKey(key))	ctr.put(key, inc);
		else						ctr.put(key, ctr.get(key)+inc);
    }
	private void counterInc(HashMap<String,Integer> ctr, String key, int inc){
    	if (inc == 0) return;
		if (!ctr.containsKey(key))	ctr.put(key, inc);
		else						ctr.put(key, ctr.get(key)+inc);
    }
}
