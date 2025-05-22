package backend.synteny;

import java.util.TreeSet;
import java.util.Vector;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.Stack;

import symap.Globals;
import util.ErrorReport;

/******************************************************
 * Used for merge blocks
 * Implements a directed graph and transitive closure algorithm
 * CAS567 moved the mergeBlocks to here; add orient; renamed from SyGraph
 **********************************************************/

public class Merge {
	private boolean bTrace = SyntenyMain.bTrace;
	private boolean doMerge=false;			// User set variable
	private boolean bOrient=false;		// ditto
	private Vector <SyBlock> blockVec;		// input all blocks, return merge blocks
	
	private int mN;
	private TreeMap<Integer,TreeSet<Integer>> mNodes;

	private int cntMerge=0;
	
	protected Merge(Vector <SyBlock> blockVec, boolean bOrient, boolean doMerge) {
		this.blockVec = blockVec;
		this.bOrient = bOrient;
		this.doMerge = doMerge;
	}
	/***************************************************
	 * Merge blocks
	 */
	protected Vector<SyBlock> mergeBlocks() {
		int nprev = blockVec.size() + 1;

		while (nprev > blockVec.size()){
			nprev = blockVec.size();
			blockVec = mergeBlocksSingleRound();
		}
		if (bTrace && cntMerge>0) Globals.prt("   " + cntMerge + " Merge");	
		return blockVec;
	}
	
	private Vector<SyBlock> mergeBlocksSingleRound() {
	try {
		if (blockVec.size() <= 1) return blockVec;
		
		int maxjoin1 = 0, maxjoin2 = 0;
		float joinfact = 0;
		
		if (doMerge) {		
			maxjoin1 = 1000000000;
			maxjoin2 = 1000000000;
			joinfact = 0.25f;
		}
		
		Graph graph = new Graph(blockVec.size());
		
		for (int i = 0; i < blockVec.size(); i++) { 
			SyBlock b1 = blockVec.get(i);
			
			int w11 = b1.mE1 - b1.mS1;
			int w12 = b1.mE2 - b1.mS2;				
			
			for (int j = i + 1; j < blockVec.size(); j++){
				SyBlock b2 = blockVec.get(j);
				
				if (bOrient && !b1.orient.equals(b2.orient))  continue;
				
				if (doMerge) {
					int w21 = b2.mE1 - b2.mS1;
					int w22 = b2.mE2 - b2.mS2;

					int gap1 = Math.min(maxjoin1,(int)(joinfact*Math.max(w11,w21)));
					int gap2 = Math.min(maxjoin2,(int)(joinfact*Math.max(w12,w22)));
					
					if (intervalsOverlap(b1.mS1,b1.mE1,b2.mS1,b2.mE1,gap1) && 
						intervalsOverlap(b1.mS2,b1.mE2,b2.mS2,b2.mE2,gap2) )
					{
						graph.addNode(i,j);
						cntMerge++;
					}
				}
				else {// only merge if contained	
					if (intervalContained(b1.mS1,b1.mE1,b2.mS1,b2.mE1) && 
						intervalContained(b1.mS2,b1.mE2,b2.mS2,b2.mE2))
					{
						graph.addNode(i,j);
						cntMerge++;
					}					
				}
			}
		}
		
		HashSet<TreeSet<Integer>> blockSets = graph.transitiveClosure();
		
		Vector<SyBlock> mergedBlocks = new Vector<SyBlock>();
		
		for (TreeSet<Integer> s : blockSets) {
			SyBlock bnew = null;
			for (Integer i : s) {
				if (bnew == null) 
					bnew = blockVec.get(i);
				else
					bnew.mergeWith(blockVec.get(i));
			}
			mergedBlocks.add(bnew);
		}
		return mergedBlocks;
	} catch (Exception e) {ErrorReport.print(e, "Merge blocks"); return null; }
	}
	
	/*****************************************************************/
	private boolean intervalsOverlap(int s1,int e1, int s2, int e2, int max_gap) {
		int gap = Math.max(s1,s2) - Math.min(e1,e2);
		return (gap <= max_gap);
	}
	private boolean intervalContained(int s1,int e1, int s2, int e2){
		return ((s1 >= s2 && e1 <= e2) || (s2 >= s1 && e2 <= e1));
	}
	
	/**************************************************************************/
	private class Graph {
		private Graph(int nnodes) {
			mN = nnodes;
			mNodes = new TreeMap<Integer,TreeSet<Integer>>();
			for (int i = 0; i < mN; i++) {
				mNodes.put(i, new TreeSet<Integer>());
			}
		}
		protected void addNode(int i, int j){
			mNodes.get(i).add(j);
			mNodes.get(j).add(i);
		}
	
		// Simple transitive closure using stack and marked nodes.
		protected HashSet<TreeSet<Integer>> transitiveClosure(){
			HashSet<TreeSet<Integer>> retSets = new HashSet<TreeSet<Integer>>();
			
			TreeSet<Integer> usedNodes = new TreeSet<Integer>();
			Stack<Integer> curNodes = new Stack<Integer>();		
			TreeSet<Integer> curSet = new TreeSet<Integer>();
			
			for (Integer i : mNodes.keySet()) {
				if (usedNodes.contains(i)) continue;
				
				assert(curNodes.empty());
				curNodes.push(i);
				
				while(!curNodes.empty()) {
					int j = curNodes.pop();
					
					for (int k : mNodes.get(j)) {
						if (usedNodes.contains(k)) continue;
						usedNodes.add(k);
						curNodes.push(k);
					}
					curSet.add(j);
				}
				retSets.add(curSet);
				curSet = new TreeSet<Integer>();
			}
			return retSets;
		}
	}
}
