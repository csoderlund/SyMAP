package backend.synteny;

import java.util.TreeSet;
import java.util.Vector;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.Stack;

import util.ErrorReport;

/******************************************************
 * Used for merge blocks
 **********************************************************/

public class Merge {
	protected final static int CONTAINED=1, CLOSE=2, OLAP=3;
	private int rel = 1;					
	private boolean bOrient;			// User set variable
	protected int cntCoset=0;

	private Vector <SyBlock> blockVec;	// input all blocks, return merge blocks
	
	private int gap1, gap2, mindots;
	
	private Graph graph;
	
	protected Merge(Vector <SyBlock> blockVec, int rel, boolean bOrient) {
		this.blockVec = blockVec;
		this.rel = rel;
		this.bOrient = bOrient;
		
		for (SyBlock blk : blockVec) blk.hasChg=false;
	}
	/***************************************************
	 * Merge blocks; Contained - gap isn't used; Overlap - gap is 0; Close - gap is >0
	 */
	protected Vector<SyBlock> mergeBlocks(int gap1, int gap2, int mindots) { 
		this.gap1 = gap1; this.gap2 = gap2; 
		this.mindots = mindots;			
		
		int nprev = blockVec.size() + 1;

		while (nprev > blockVec.size()){
			nprev = blockVec.size();
			blockVec = mergeBlocksSingleFixed();   
		}	
		return blockVec;
	}
	// Used with bStrict and/or bOrient
	private Vector<SyBlock> mergeBlocksSingleFixed() { 
	try {
		if (blockVec.size() <= 1) return blockVec;
		
		graph = new Graph(blockVec.size());
	
		for (int i = 0; i < blockVec.size(); i++) { 
			SyBlock bi = blockVec.get(i);
			
			for (int j = i + 1; j < blockVec.size(); j++){
				SyBlock bj = blockVec.get(j);
				
				if (bOrient && !bi.orient.equals(bj.orient))continue;
				if (bi.n < mindots && bj.n < mindots) continue; // to merge two coset blocks need some analysis
				
				if (rel==CONTAINED) {// only merge if contained on both sides
					if (isContained(bi.mS1,bi.mE1,bj.mS1,bj.mE1) && 
						isContained(bi.mS2,bi.mE2,bj.mS2,bj.mE2))
					{
						graph.addNode(i,j);
					}	
				}
				else if (rel==OLAP) { // gap=0 for overlap only, or >0 for close or overlap
					if (isOverlap(bi.mS1,bi.mE1,bj.mS1,bj.mE1, gap1) && 
						isOverlap(bi.mS2,bi.mE2,bj.mS2,bj.mE2, gap2))
					{
						graph.addNode(i,j);
					}
				}
				else if (rel==CLOSE){ // close only, and works in conjunction with bOrient=T and mindots=0
					if (isClose(bi.mS1,bi.mE1,bj.mS1,bj.mE1, gap1) && 
						isClose(bi.mS2,bi.mE2,bj.mS2,bj.mE2, gap2))
					{
						graph.addNode(i,j);
					}			
				}
			}
		}
		return processGraph();
	} 
	catch (Exception e) {ErrorReport.print(e, "Merge blocks"); return null; }
	}

	/**************************************************************************/
	// parameters overlap (gap=0) or close (gap>0)
	private boolean isOverlap(int s1,int e1, int s2, int e2, int max_gap) {
		int gap = Math.max(s1,s2) - Math.min(e1,e2);
		return (gap <= max_gap);
	}
	// if bOrient or bStrict (gap>0)
	private boolean isClose(int s1,int e1, int s2, int e2, int max_gap) {
		int gap = Math.max(s1,s2) - Math.min(e1,e2);
		return (gap>0 && gap <= max_gap);
	}
	// always run
	private boolean isContained(int s1,int e1, int s2, int e2){
		return ((s1 >= s2 && e1 <= e2) || (s2 >= s1 && e2 <= e1));
	}
	/***************************************************************
	// Implements a directed graph and transitive closure algorithm
	// This does not look at blk.n, and can merge a big n into a very small n
	**************************************************************/
	private Vector<SyBlock> processGraph() {
		HashSet<TreeSet<Integer>> blockSets = graph.transitiveClosure();
		Vector<SyBlock> mergedBlocks = new Vector<SyBlock>();
		
		for (TreeSet<Integer> s : blockSets) {
			SyBlock bnew = null;
			for (Integer i : s) {
				if (bnew == null) 
					bnew = blockVec.get(i);
				else {
					if (SyntenyMain.bTrace) {
						if (bnew.mCase=="N" || blockVec.get(i).mCase=="N") cntCoset++;
						if (rel==CLOSE) {
							bnew.tprt("Merge1");
							blockVec.get(i).tprt("Merge2");
						}
					}
					bnew.mergeWith(blockVec.get(i));
				}
			}
			mergedBlocks.add(bnew);
		}
		return mergedBlocks;
	}
	/**************************************************************************/
	private class Graph {
		private int mN;
		private TreeMap<Integer,TreeSet<Integer>> mNodes;
		
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
