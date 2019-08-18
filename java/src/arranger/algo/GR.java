package arranger.algo;

/* This class is the main class of the project. It will be used by GR_applet 
class to simulate the running of the algorithm.
*/
public class GR {
	
	final static int HURDLES_SUSPECIOUS = 1;
	final static int NO_HURDLES = 2;
	
	OVGraph m_graph;
	Permutation m_permutation;
	int m_status;
	
	// constructors
	public GR(Permutation permutation) throws Exception {
		m_permutation = permutation;
		m_graph = new OVGraph(m_permutation);
		m_status = HURDLES_SUSPECIOUS;
	}
//	public GR(Permutation permutation) throws Exception {
//		m_permutation = permutation;
//		m_graph = new OVGraph(m_permutation);
//	}
	void setMode(int mode) {
//		m_mode = mode;
	}
	
	public Reversal runStep() throws Exception {
		Reversal result;
		if(m_status == HURDLES_SUSPECIOUS) {
			result = m_graph.clearOneHurdle();
			if(result == null) { // No more hurdles
				m_status = NO_HURDLES;
				return runStep();
			}
			return result;
		} 
		// else (NO_HURDLES status)
		List happy_clique = m_graph.findHappyClique();
		if(happy_clique == null) // no oriented vertices, i.e., no vertices (since there are no hurdles)
			return null;
		int edge_index = m_graph.findVertexWithMaxUnorientedDegree(happy_clique);
		result = m_graph.findReversalByOrientedEdge(edge_index);
		m_graph.reversal(result);
		return result;
	}
			
			
								  
		
	// Main method. Runs the algorithm
	public Reversal[] run() throws Exception {
		int c = m_permutation.getCyclesNumber();
		int b = m_permutation.getBreakpointsNumber();
		int h = m_graph.getHurdlesNumber();
		int d = b - c + h;
		if(m_graph.isFortress()) d++;
		Reversal[] result = new Reversal[d];
		int reversals_num = 0;
		List happy_clique;
		
		Reversal[] hurdles_reversals = m_graph.clearHurdles();
		for(int i=0; i<hurdles_reversals.length; i++)
			result[reversals_num++] = hurdles_reversals[i];
		// Work on the oriented graph		
		while(true) {
			// Check weather a new hurdle had appeared			
//			og = new OVGraph(og.getPermutation());
//			h = og.getHurdlesNumber();
//			if(h > 0)
//				h++; // Error
			happy_clique = m_graph.findHappyClique();
			if(happy_clique == null) // no oriented vertices, i.e., no vertices (since there are no hurdles)
				break;
			int edge_index = m_graph.findVertexWithMaxUnorientedDegree(happy_clique);
			result[reversals_num] = m_graph.findReversalByOrientedEdge(edge_index);
			m_graph.reversal(result[reversals_num]);
			reversals_num++;
		}
		if(reversals_num != d)
			throw new Exception("Algorithm failed");
		return result;
	}
	public void makeReversal(Reversal reversal) throws Exception { 
		m_graph.reversal(reversal);
	}
	
	public Permutation getPermutation() { return m_permutation; }
	public OVGraph getGraph() { return this.m_graph; }
}






