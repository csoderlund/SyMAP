package symapQuery;
/**************************************************
 * CAS504 new class to compute the summary in one place (except regions)
 * The summary at the bottom of the results table.
 */
import java.awt.Color;
import java.awt.Font;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

import util.ErrorReport;

public class SummaryPanel  extends JPanel {
	private static final long serialVersionUID = 1L;
	private JPanel statsPanel=null;
	
	private Vector <DBdata> rowsFromDB;
	private  QueryPanel qPanel;
	private SpeciesSelectPanel spPanel;
	
	public SummaryPanel(Vector <DBdata> rowsFromDB, QueryPanel qPanel,  
			HashMap <String, String> projMap, 			// CAS555 changed from integer to string for PgeneF/Multi
			HashMap <Integer, Integer> geneCntMap) {
		
		if (statsPanel == null) statsPanel = new JPanel();
		statsPanel.setLayout(new BoxLayout(statsPanel, BoxLayout.PAGE_AXIS));
		statsPanel.setBackground(Color.WHITE);
		if(rowsFromDB==null || rowsFromDB.size()==0) return;
		
		this.rowsFromDB = rowsFromDB;
		this.qPanel = qPanel;
		spPanel = qPanel.getSpeciesPanel();
		
		computeStats(projMap, geneCntMap); 
	}
	public JPanel getPanel() {return statsPanel;}
	
	/****************************************************
	 * Compute Stats - fills in the rest of panel
	 */
	private void computeStats(HashMap <String, String> projMap, HashMap <Integer, Integer> geneCntMap) {
		HashMap<String,String> chrStrMap = new HashMap<String, String>();
		HashSet<Integer> gidxSet = new HashSet<Integer> ();
		
		// Create Chr: x of x
    	for (DBdata dd : rowsFromDB) {
    		if (!gidxSet.contains(dd.getChrIdx(0))) gidxSet.add(dd.getChrIdx(0));
    		if (!gidxSet.contains(dd.getChrIdx(1))) gidxSet.add(dd.getChrIdx(1));
    	}
   
    	Vector <Integer> order = new Vector <Integer> (); // CAS522 added so stats are in order
    	for (int p = 0; p < spPanel.getNumSpecies(); p++) {
    		String projName = spPanel.getSpName(p);
    		order.add(spPanel.getSpIdx(p));
    		
    		String [] gidxList =  spPanel.getChrIdxList(p);
    		HashSet <Integer> spGidx = new HashSet <Integer> ();
    		for (String c : gidxList) 
    			spGidx.add(Integer.parseInt(c));
    		
    		int nchrs =       gidxList.length;
    		int xchr = 0;
    		for (int idx : gidxSet) 
    			if (spGidx.contains(idx)) xchr++;
    		 		
    		String chrStr = String.format("%2d of %2d", xchr, nchrs);
    		chrStrMap.put(projName, chrStr);
    	}
    	gidxSet.clear();
	    	
		if (qPanel.isSingle()) 	createSingle(chrStrMap);
		else 					createPairHits(chrStrMap, geneCntMap, projMap, order);
	}
	/*****************************************************
	 * Has Gene:  Either %d Both %d  None %d      Has Block: Yes %d  No %d 
	 * 
	 * Sp1  Hits: %d   Annotated: %d   Chr: %d  Regions: %d
	 */
	private void createPairHits(HashMap <String, String>  chrStrMap, 
							HashMap <Integer, Integer> annoCntMap,
							HashMap <String, String> projMap, 
							Vector <Integer> order) {
		try {
			int either=0, both=0, none=0, block=0, collinear=0, maxGrp=0; // CAS555 added collinear and maxGrp
			HashMap <Integer, Integer> proj2hits =  new HashMap <Integer, Integer> ();
			HashMap <Integer, Integer> proj2annot = new HashMap <Integer, Integer> ();
			HashMap <Integer, Integer> blockCnt = new HashMap <Integer, Integer> ();
			HashMap <Integer, Integer> collinearCnt = new HashMap <Integer, Integer> ();
			
			for (DBdata dd : rowsFromDB) {
				int spIdx1 = dd.getSpIdx(0);
				int spIdx2 = dd.getSpIdx(1);
				
				counterInc(proj2hits, spIdx1, 1);
				counterInc(proj2hits, spIdx2, 1);
				
				boolean anno1 = dd.hasAnno(0);
				boolean anno2 = dd.hasAnno(1);
				if (anno1) counterInc(proj2annot, spIdx1, 1);
				if (anno2) counterInc(proj2annot, spIdx2, 1);
				
				if (anno1 && anno2) both++;
				else if (!anno1 && !anno2) none++;
				else either++;
				
				if (dd.hasBlock()) {
					block++;
					counterInc(blockCnt, dd.blockNum, 1);
				}
				if (dd.hasCollinear()) {
					collinear++;
					counterInc(collinearCnt, dd.runNum, 1);
				}
				if (dd.grpN>maxGrp) maxGrp=dd.grpN;
			}
			String label = String.format("Has Block: %,d (#%,d)   Has Gene: Both %,d  One %,d  None %,d", 
							 block, blockCnt.size(), both, either, none); // CAS541 changed text to match dotplot summary
			if (collinear>0) label += String.format("  Collinear %,d (#%,d)", collinear, collinearCnt.size());
			if (maxGrp>0) label += String.format("   Groups: #%,d", maxGrp);
			
			JLabel theLabel = new JLabel(label);
			theLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
			statsPanel.add(theLabel);
			statsPanel.add(Box.createVerticalStrut(5));
			
			for (int spIdx : order ) {
				String pName = spPanel.getSpNameFromSpIdx(spIdx);
				int nHit =  proj2hits.containsKey(spIdx)    ? proj2hits.get(spIdx)    : 0;
				int nAnno = proj2annot.containsKey(spIdx)   ? proj2annot.get(spIdx)   : 0;
				
				String chrStr =  chrStrMap.containsKey(pName)  ? chrStrMap.get(pName) : "Unk";
				int nUnq =       annoCntMap.containsKey(spIdx) ? annoCntMap.get(spIdx) : 0;
				
				statsPanel.add(Box.createVerticalStrut(2));
				
				label = String.format("%-13s  Hits: %,6d    Annotated: %,6d   Genes: %,5d", 
						pName, nHit, nAnno, nUnq);
				
				if (projMap.containsKey(pName)) label += "   " + projMap.get(pName);
				else 							label += "    Chr: " + chrStr;
				
				theLabel = new JLabel(label);
				theLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
				statsPanel.add(theLabel);
			}
		}
		catch (Exception e) {ErrorReport.print(e, "Create hit summary");}
	}
	private void createSingle(HashMap<String, String> chrStrMap) {
		try {
			HashMap <Integer, Integer> proj2orphs = new HashMap <Integer, Integer> ();
			
		/* find number of orphans */
			for (DBdata dd : rowsFromDB) {
				int spIdx = dd.getSpIdx(0);
				counterInc(proj2orphs, spIdx, 1);
			}
		/* create summary */
			
			statsPanel.add(Box.createVerticalStrut(5));
			String type = (qPanel.isSingleOrphan()) ? "Orphans" : "Genes"; // CAS514
			
			for (int spIdx : proj2orphs.keySet() )
			{
				String pname = spPanel.getSpNameFromSpIdx(spIdx);
				int o = proj2orphs.get(spIdx);
				String chrStr = chrStrMap.containsKey(pname) ? chrStrMap.get(pname) : "Unk";
				
				statsPanel.add(Box.createVerticalStrut(2));
				String label = String.format("%-13s    %s: %,7d     Chr: %s",pname, type, o, chrStr);
				
				JLabel theLabel = new JLabel(label);
				
				theLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
				statsPanel.add(theLabel);
			}
		}
		catch (Exception e) {ErrorReport.print(e, "Create orphan summary");}
	}
	private void counterInc(HashMap<Integer,Integer> ctr, Integer key, int inc  )
    {
    	if (inc == 0) return;
		if (!ctr.containsKey(key))	ctr.put(key, inc);
		else						ctr.put(key, ctr.get(key)+inc);
    }
	
}
