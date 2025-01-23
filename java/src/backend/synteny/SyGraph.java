package backend.synteny;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.Stack;

// Implements a directed graph and transitive closure algorithm

public class SyGraph {
	int mN;
	TreeMap<Integer,TreeSet<Integer>> mNodes;
	
	protected SyGraph(int nnodes) {
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
	protected HashSet<TreeSet<Integer>> transitiveClosure()
	{
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
	
//	private void printGraph()
//	{
//		for (Integer i : mNodes.keySet())
//			for (Integer j : mNodes.get(i))
//				System.out.println("NODE:" + i + "," + j);
//	}
}
