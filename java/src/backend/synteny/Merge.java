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
	protected final static int CONTAINED=1, OLAP=2, COSET=3; 
	private int action = 1;					
	private boolean bOrient;			// User set variable; 
	protected int cntCoset=0, cntSkip=0;

	private Vector <SyBlock> blockVec;	// input all blocks, return merge blocks
	
	private int gap1, gap2, mindots, fullGap2;
	
	private Graph graph;
	
	protected Merge( int action, Vector <SyBlock> blockVec, boolean bOrient, int mindots, int fullGap2) {
		this.action = action;
		this.blockVec = blockVec;
		this.bOrient = bOrient;
		this.mindots = mindots;	
		this.fullGap2 = fullGap2;
		
		for (SyBlock blk : blockVec) blk.hasChg=false;
	}
	/***************************************************
	 * Merge blocks; Contained - gap isn't used; Overlap - gap is 0; Close - gap is >0
	 */
	protected Vector<SyBlock> mergeBlocks(int gap1, int gap2) { 
		this.gap1 = gap1; this.gap2 = gap2; 
		
		int loop=0;
		int prevSz = blockVec.size() + 1;
		while (prevSz > blockVec.size()){
			prevSz = blockVec.size();
			blockVec = mergeBlocksSingleFixed(loop++);   
		}
		return blockVec;
	}
	
	private Vector<SyBlock> mergeBlocksSingleFixed(int loop) { 
	try {
		if (blockVec.size() <= 1) return blockVec;
		graph = new Graph(blockVec.size());
	
		for (int i = 0; i < blockVec.size(); i++) { 
			SyBlock bA = blockVec.get(i);
			
			for (int j = i + 1; j < blockVec.size(); j++){
				SyBlock bZ = blockVec.get(j);
		
				if (bOrient && !bA.orient.equals(bZ.orient)) continue;
				
				if (action!=COSET && (bA.nHits < mindots && bZ.nHits < mindots)) continue; // Strict merges cosets
				
				if (action==CONTAINED) { // only merge if contained on both sides
					if (isContained(bA.mS1,bA.mE1,bZ.mS1,bZ.mE1) && 
						isContained(bA.mS2,bA.mE2,bZ.mS2,bZ.mE2))
					{
						boolean isGood = true;
						if (bA.nHits<mindots || bZ.nHits<mindots) { // Strict cosets has <mindots; merged cosets can be >mindots
							isGood = (bA.nHits<bZ.nHits) ? bZ.fitStrict(bA, fullGap2, loop==0) : bA.fitStrict(bZ, fullGap2, loop==0);
							if (!isGood) cntSkip++; else cntCoset++;
						}		
						if (isGood) graph.addNode(i,j);
					}	
				}
				else if (action>=OLAP) { // gap=0 for overlap only, or >0 for close or overlap
					if (action!=COSET && (bA.nHits<mindots || bZ.nHits<mindots)) continue; // fitStrict only checks for contained
					
					if (isOverlap(bA.mS1,bA.mE1,bZ.mS1,bZ.mE1, gap1) && 
						isOverlap(bA.mS2,bA.mE2,bZ.mS2,bZ.mE2, gap2))
					{
						graph.addNode(i,j);
					}
				}
			}
		}
		// Return blockVec with merged blocks
		return mergeGraph();
	} 
	catch (Exception e) {ErrorReport.print(e, "Merge blocks"); return null; }
	}

	/**************************************************************************/
	// parameters overlap (gap=0) or close (gap>0)
	private boolean isOverlap(int s1,int e1, int s2, int e2, int max_gap) {
		int gap = Math.max(s1,s2) - Math.min(e1,e2);
		return (gap <= max_gap);
	}
	// always run
	private boolean isContained(int s1,int e1, int s2, int e2){
		return ((s1 >= s2 && e1 <= e2) || (s2 >= s1 && e2 <= e1));
	}
	/***************************************************************
	// Implements a directed graph and transitive closure algorithm
	// This does not look at blk.n, and can merge a big n into a very small n
	**************************************************************/
	private Vector<SyBlock> mergeGraph() {
		HashSet<TreeSet<Integer>> blockSets = graph.transitiveClosure();
		Vector<SyBlock> mergedBlocks = new Vector<SyBlock>();
		
		for (TreeSet<Integer> s : blockSets) {
			SyBlock bnew = null;
			for (Integer i : s) {
				if (bnew == null) 
					bnew = blockVec.get(i);
				else {
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
