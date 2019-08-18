package arranger.algo;

// This class is a public one (used like C structure)
public class C_component {
	public int m_size;
	public int[] m_vertices;
	public boolean m_oriented;
	public boolean m_breakpoints_comp;
	public int m_blocks_number; // in CR
	public int[] m_two_neighbours; // in CR
	public boolean m_hurdle;
	public boolean m_super_hurdle;

	public C_component() {
		m_size = 0;
		m_vertices = null;
		m_oriented = false;
		m_breakpoints_comp = false;
		m_blocks_number = 0;
		m_two_neighbours = new int[2];
		m_two_neighbours[0] = -1;
		m_two_neighbours[1] = -1;
		m_hurdle = false;
		m_super_hurdle = false;
	}
	
	public boolean isBreakpoint() { return m_breakpoints_comp; }
	public boolean isAdjancy() { return (m_breakpoints_comp == false); }
	public boolean isUnoriented() { return (m_breakpoints_comp && !m_oriented); }
	public boolean isOriented() { return (m_breakpoints_comp && m_oriented); }
	public boolean isHurdle() { return (isUnoriented() && (m_blocks_number == 1)); }
	public boolean isSuperHurdle() { return (isHurdle() && m_super_hurdle); }
	public boolean isSimpleHurdle() { return (isHurdle() && !m_super_hurdle); }

	public void addNeighbour(int id) {
		if(m_two_neighbours[1] != -1)
			// We already have two different neighbours
			return;
		if(m_two_neighbours[0] == -1)
			// First neighbour
			m_two_neighbours[0] = id;
		else { // We have exactly one neighbour
			if(m_two_neighbours[0] == id)
				return; // Nothing to update
			m_two_neighbours[1] = id;
		}
	}		
		
	public int countNeighbours() { 
		if(m_two_neighbours[0] == -1)
			return 0;
		// At least one neighbour
		if(m_two_neighbours[1] == -1)
			return 1;
		return 2; // Or more
	}

	public int[] getVertices() { return m_vertices; }
	
	/* Get the vertices indexes of the component (for example if the component
	  indexes are 6,7,8,9,2,3,4,5 (not necessarily in this order) we should return
	  3, 4, 1, 2
	 */
	public int[] getVerticesIndexes() throws Exception {
		int max_index = -1;
		for(int i=0; i<m_vertices.length; i++)
			if(max_index < m_vertices[i]) max_index = m_vertices[i];
		boolean[] belonging = new boolean[max_index+1]; // Initialized to false
		for(int i=0; i<m_vertices.length; i++)
			belonging[m_vertices[i]] = true;
		// We have a bits vector that represents all the indexes of vertices in the array
		int[] result = new int[m_vertices.length / 2];
		int next = 0;
		for(int i=0; i<(belonging.length/2); i++) { // Pass on possible vertices indexes
			if(belonging[2*i]) {
				if(belonging[2*i+1]) // these two vertices belong
					result[next++] = i; // Add a new index
				else
					throw new Exception("C_component::getVerticesIndexes - invalid component");
			}
		}
		if(next != result.length) // The indexes number must fit the vertices number
			throw new Exception("C_component::getVerticesIndexes - invalid component");
		return result;
	}	
}
