package arranger.algo;

public class OVGraph {

	// Data members
	private Permutation m_permutation;
	private boolean m_all_components_oriented;
	private int[] m_components_indexes;
	private C_component[] m_components;
	private int[] m_CR;
	private int m_hurdles_num;

	//private Permutation original_permutation; // mdb removed 6/29/07 #118

	// constructor
	public OVGraph(Permutation permutation) throws Exception {
		m_permutation = permutation;
		makeComponents();
	}


	/* **************** GENERAL AUXILARY METHODS ************* */

	public int getPosition(int index) throws Exception { return m_permutation.getPosition(index); }
	public int getIndex(int position) throws Exception { return m_permutation.getIndex(position); }
	private int getNeighbourIndex(int index) { 
		if((index % 2) == 0)
			return (index + 1);
		else
			return (index - 1);
	}
	// Find the index of the edge by one of its endpoints indexes
	public int getVertexIndexByIndex(int index) {
		return (index / 2);
	}
	// Find the index of the edge by the position of one of its endpoints
	public int getVertexIndexByPosition(int position) throws Exception {
		return getVertexIndexByIndex(this.getIndex(position));
	}
	public int getLeftEndpoint(int vertex_index) throws Exception {
		int even = 2 * vertex_index;
		int odd = 2 * vertex_index + 1;
		int[] range = m_permutation.getPositions(even, odd);
		return range[0];
	}
	public int getRightEndpoint(int vertex_index) throws Exception {
		int even = 2 * vertex_index;
		int odd = 2 * vertex_index + 1;
		int[] range = m_permutation.getPositions(even, odd);
		return range[1];
	}

	// Get the neighbour (by black edge)
// mdb removed 6/29/07 #118
//	private int getBlackNeighbour(int index) throws Exception { 
//		int position = getPosition(index);
//		if((position % 2) == 0)
//			return getIndex(position + 1);
//		else
//			return getIndex(position - 1);
//	}

	// Check if a vertex (BP graph edge) is oriented
	public boolean isOriented(int vertex_index) throws Exception {
		if(isBreakpoint(vertex_index) == false)
			return false;
		int even = 2 * vertex_index;
		int odd = 2 * vertex_index + 1;
		int temp = getPosition(even) - getPosition(odd);
		return((temp % 2) == 0);
	}

	// Check if a vertex (BP graph edge) is not oriented
	private boolean isUnoriented(int vertex_index) throws Exception {
		return (isBreakpoint(vertex_index) && !isOriented(vertex_index));
	}

	// Check weather a pair of vertices is a breakpoint
	public boolean isBreakpoint(int vertex_index) throws Exception {
		int even = 2 * vertex_index;
		int odd = 2 * vertex_index + 1;
		int temp = getPosition(odd) - getPosition(even);
		return(java.lang.Math.abs(temp) != 1); // in a non-breakpoint this number will be 1 since even,odd appear consequently in the permutation
	}

	// Return the number of vertices
	public int getNumOfVertices() {
		return this.m_permutation.getSize() / 2;
	}

	public void reversal(Reversal reversal) throws Exception {
		m_permutation.reversal(reversal);
		m_components = null;
	}
	public Permutation getPermutation() { return m_permutation; }

	public C_component[] getComponents() throws Exception { 
		if(m_components == null)
			this.makeComponents();
		return this.m_components;
	}

	// Get an array of the oriented vertices (by indexes)
	private int[] getOrientedVerticesByLeftOrder() throws Exception {
		int oriented_counter = 0;
		int vertices_num = this.getNumOfVertices();
		int permutation_size = m_permutation.getSize();
		boolean[] orientations = new boolean[vertices_num];

		// Check the orientation of all the vertices from left to right
		int vertex_index;
		for(vertex_index=0; vertex_index<orientations.length; vertex_index++) {
			orientations[vertex_index] = isOriented(vertex_index);
			if(orientations[vertex_index])
				oriented_counter++;
		}

		// Prepare the result array
		boolean[] check_array = new boolean[vertices_num];
		int[] result = new int[oriented_counter];
		int next_index = 0;

		// Fill the result. We pass on the array by left endpoints
		for(int position=0; position<permutation_size; position++) {
			vertex_index = this.getVertexIndexByPosition(position);
			if(check_array[vertex_index]) // We passed this vertex
				continue; // right endpoint
			// Left endpoint
			check_array[vertex_index] = true; 
			if(orientations[vertex_index])
				result[next_index++] = vertex_index;
			if(next_index == result.length) // result array is full
				break;
		}

		return result;
	}


	// Find the vertices that correspont to the edge that the vertex with id represents
	public Reversal findReversalByOrientedEdge(int vertex_index) throws Exception {
		int left = getLeftEndpoint(vertex_index);
		int right = getRightEndpoint(vertex_index);
		// left must be odd and right must be even
		if((left % 2) == 0)
			left++;
		if((right % 2) == 1)
			right--;
		return new Reversal(left, right);
	}

	/* Notice that source and target are the indexes of the vertices. For
       example 3 stands for 6,7 vertex (the 6, 7 vertyices in the breakpoints graph.
	 */
	public boolean isEdge(int source, int target) throws Exception {
		int left_source = getLeftEndpoint(source);
		int right_source = getRightEndpoint(source);
		int left_target = getLeftEndpoint(target);
		int right_target = getRightEndpoint(target);
		if(right_source < left_target)
			return false; // no collision
		if(right_target < left_source)
			return false; // no collision
		// If we reached this line we have a collision
		if(left_source < left_target)
			return (right_source < right_target);
		else
			return (right_target < right_source);
	}

	/* This method gets the id of a vertex in the overlap graph (corresponds
       to an edge in the breakpoints graph which corresponds to 2 numbers in 
       the permutation), locate the edges of the reversal and commit the reversal.
       TBD */
	//  public boolean safeReversal(int id) {
	//      return true;
	//  }

	private List getEdges(int vertex_index) throws Exception {
		int left = getLeftEndpoint(vertex_index);
		int right = getRightEndpoint(vertex_index);
		List edges = new List();
		for(int position=left+1; position<right; position++) {
			int new_vertex_index = getVertexIndexByPosition(position);
			if(isEdge(vertex_index, new_vertex_index))
				edges.add(new_vertex_index);
		}
		return edges;
	}

	/* Pseudo code for getting components:
       1. int[] temp_components = Generate an array where for each vertex the value is the lowest index of another vertex in its component
       2. int[] comp_indexes = Map the indexes to continuous indexes
       3. m_components_indexes = Set the array to low indexes
       4. int[] components_sizes = Get a sizes array
       5. m_components = Make components with vertices, m_oriented, m_breakpoint_comp
       6. m_CR = Generate CR
       7. int[] CR_components_indexes = Generate a components mapping of CR
       8. Scan CR and fetch the following data (for each unoriented component):
       a. m_number_of_blocks in CR
       b. m_hurdle (m_number_of_blocks == 1)
       9. Scan CR and fetch the following data (for each hurdle):
       m_super_hurdle by checking the neighbours ID's in CR and if there is one neighbour - check its number of blocks (2 makes us a super hurdle)
	 */

	/* This method initializes the following data members:
       m_components_indexes
       m_components
       m_CR 
       m_hurdles_number
       m_all_components_oriented
	 */
	private void makeComponents() throws Exception {

		m_all_components_oriented = false;
		m_components_indexes = null;
		m_components = null;
		m_CR = null;
		m_hurdles_num = 0;

		int vertices_num = this.getNumOfVertices();
		int[] temp_components = new int[vertices_num];
		temp_components = DFS();

		/* This method initializes m_components. The target of this 
	   action is to return also the components number (that is computed
	   in the method) */
		int[] comp_indexes = mapToContinuous(temp_components);

		// We have the components number in m_components.length

		// Update the components links to the new ones (indexes)
		m_components_indexes = new int[vertices_num];
		for(int i=0; i<vertices_num; i++)
			m_components_indexes[i] = comp_indexes[temp_components[i]];

		makeComponentsGivenIndexes();
	}

	private void makeComponentsGivenIndexes() throws Exception {
		int[] components_sizes = getComponentsSizes(m_components_indexes, m_components.length);

		// Generate the components with size value
		for(int i=0; i<m_components.length; i++) {
			m_components[i] = new C_component();
			m_components[i].m_vertices = new int[2 * components_sizes[i]];
		}

		// Set vertices, m_oriented, m_breakpoints_comp
		fillComponents_1(m_components, m_components_indexes, components_sizes);

		m_CR = getCR(m_components);

		m_all_components_oriented = (m_CR.length == 0);
		if(m_all_components_oriented)
			return;

		// Get a mapping of CR vertices to their components
		int[] CR_components_indexes = new int[m_CR.length];
		for(int i=0; i<m_CR.length; i++)
			CR_components_indexes[i] = m_components_indexes[getVertexIndexByIndex(m_CR[i])];

		// Assign values to blocks_number fields
		fillComponentsBlocksNumbers(m_components, m_CR, CR_components_indexes);
		fillComponentsHurdilities(m_components);
		fillComponentsSuperHurdilities(m_components);
	}

	// Compute the number of components given a components indexes array
	int getNumberOfComponents(int[] components_indexes) {
		int max = 0;
		for(int i=0; i<components_indexes.length; i++)
			if(max < components_indexes[i])
				max = components_indexes[i];
		return (max + 1);
	}


	/* This method use DFS search to assign for each vertex - the lowest index of a
       vertex in its component */
	private int[] DFS() throws Exception {
		int vertices_num = this.getNumOfVertices();
		int[] comp_lowest = new int[vertices_num];
		boolean[] checked = new boolean[vertices_num];
		// initialization. Each vertex is in itself component
		for(int i=0; i<vertices_num; i++) {
			comp_lowest[i] = i;
			checked[i] = false;
		}

		for(int i=0; i<vertices_num; i++)
			if(checked[i] == false) {
				checked[i] = true;
				workOnEdges(i, comp_lowest, checked);
			}
		return comp_lowest;
	}

	/* This method scan all the unchecked neighbours of index, try to improve their component
       index and set checked value to true. */
	void workOnEdges(int vertex_index, int[] components, boolean[] checked) throws Exception {
		List neighbours = this.getEdges(vertex_index);
		int neighbour_vertex_index;
		for(ListElem neighbour=neighbours.getElements(); neighbour!=null; neighbour=neighbour.getNext()) {
			neighbour_vertex_index = neighbour.getValue();
			if(checked[neighbour_vertex_index])
				continue;
			if(components[vertex_index] >= components[neighbour_vertex_index])
				throw new Exception("OVGraph::workOnEdges - unexpected event, DFS failed");

			components[neighbour_vertex_index] = components[vertex_index];
			checked[neighbour_vertex_index] = true;
			workOnEdges(neighbour_vertex_index, components, checked);
		}
	}

	/* Find the components number and map the current components indexes 
       to better ones (for example from 0, 2, 3, 6 - to 0,1,2,3). 
       This method initializes m_components. The target of this 
       action is to return also the components number (that is computed
       in the method) */
	int[] mapToContinuous(int[] lowest_indexes) {
		// The result array
		int[] comp_indexes = new int[lowest_indexes.length];
		// Initialization
		for(int i=0; i<lowest_indexes.length; i++)
			comp_indexes[i] = -1;

		int comp_num = 0;
		int highest = -1; // The first will be higher
		for(int i=0; i<lowest_indexes.length; i++) {
			if(highest < lowest_indexes[i]) { 
				// Found a new component
				comp_indexes[i] = comp_num;
				highest = lowest_indexes[i];
				comp_num++;
			}
		}
		// Got number of components and mapped them
		m_components = new C_component[comp_num];

		return comp_indexes;
	}

	// Pass on the array and count the appearances of each index
	int[] getComponentsSizes(int[] components_indexes, int components_num) {
		int[] sizes = new int[components_num]; // Initialized to 0
		for(int i=0; i<components_indexes.length; i++)
			sizes[components_indexes[i]]++;
		return sizes;
	}

	// Set vertices, m_oriented, m_breakpoints_comp
	void fillComponents_1(C_component[] components, int[] components_indexes, int[] components_sizes) throws Exception {
		int vertex_index, index;
		C_component comp;
		int permutation_size = m_permutation.getSize();
		for(int position=0; position<permutation_size; position++) {
			// Auxilary values
			index = getIndex(position);
			vertex_index = this.getVertexIndexByIndex(index);
			comp = components[components_indexes[vertex_index]];

			// Update the component
			comp.m_vertices[comp.m_size] = index;
			comp.m_size++;
			if(comp.m_breakpoints_comp == false)
				comp.m_breakpoints_comp = isBreakpoint(vertex_index);
			if(comp.m_breakpoints_comp) {
				if(comp.m_oriented == false)
					comp.m_oriented = this.isOriented(vertex_index);
			}
		}
	}

	/* Get the CR circle of the graph.*/
	int[] getCR(C_component[] components) throws Exception {
		int size = m_permutation.getSize();
		boolean[] all = new boolean[size]; // Initialized to false
		int cr_size = 0;

		for(int i=0; i<components.length; i++) {
			if(components[i].isUnoriented()) {
				// An unoriented component
				for(int j=0; j<components[i].m_vertices.length; j++) {
					int position = getPosition(components[i].m_vertices[j]);
					all[position] = true;
					cr_size++;
				}
			}
		}
		int[] result = new int[cr_size];
		int next = 0;
		for(int i=0; i<size; i++) {
			if(all[i]) {
				if(next >= cr_size)
					throw new Exception("OVGraph::getCR - Error in passing all array to result array");
				else
					result[next++] = getIndex(i);
			}
		}
		return result;
	}

	/* Get the blocks number of each component in CR. Find for each component 
       two different neighnbours (if it has) */
	void fillComponentsBlocksNumbers(C_component[] components, int[] CR, int[] CR_components_indexes) throws Exception {
		if(CR.length == 0)
			throw new Exception("CR is empty");
		// An arry for counting the blocks of each component in CR
		//int value; // mdb removed 6/29/07 #118
		int comp_index;
		int previous_index = CR_components_indexes[CR.length-1]; // last index
		// Scan CR
		for(int position=0; position<CR.length; position++) {
			comp_index = CR_components_indexes[position];
			if(comp_index != previous_index) {
				// A new block of comp_index component
				components[comp_index].m_blocks_number++;
				// Add the components as neighbours to each other
				components[comp_index].addNeighbour(previous_index);
				components[previous_index].addNeighbour(comp_index);
				previous_index = comp_index;
			}
		}
		// Check the pathological case when there is only one component.
		if(components[CR_components_indexes[0]].m_blocks_number == 0)
			components[CR_components_indexes[0]].m_blocks_number++;
	}

	void fillComponentsHurdilities(C_component[] components) {
		m_hurdles_num = 0;
		for(int i=0; i<components.length; i++) {
			if(components[i].isUnoriented()) {
				components[i].m_hurdle = (components[i].m_blocks_number == 1);
				if(components[i].m_hurdle)
					m_hurdles_num++;
			}
		}
	}

	void fillComponentsSuperHurdilities(C_component[] components) {
		for(int i=0; i<components.length; i++) {
			if(components[i].isHurdle() == false)
				continue;
			// components[i] is a hurdle
			if(components[i].countNeighbours() == 1) { 
				// One neighbour (from both sides). This neighbour might make the component a super hurdle
				int comp_index = components[i].m_two_neighbours[0];
				if(components[comp_index].isUnoriented() &&
						(components[comp_index].m_blocks_number == 2))
					components[i].m_super_hurdle = true;
				else
					components[i].m_super_hurdle = false;
			}
		}
	}

	/* *********** Getting information about the graph *********** */

	public int getPermutationSize() { return m_permutation.getSize(); }
	public int getBreakpointsNumber() { return this.m_permutation.getBreakpointsNumber(); }
	public int getCyclesNumber() throws Exception { return this.m_permutation.getCyclesNumber(); }
	public int getHurdlesNumber() throws Exception { 
		if(this.m_components == null)
			this.makeComponents();
		return m_hurdles_num; 
	}

	public int getSuperHurdlesNumber() throws Exception {
		if(m_components == null)
			this.makeComponents();
		int number = 0;
		for(int i=0; i<m_components.length; i++) {
			if(m_components[i].isSuperHurdle())
				number++;
		}
		return number;
	}

	public boolean isFortress() throws Exception {
		if(this.m_components == null)
			this.makeComponents();
		if(m_hurdles_num == 0)
			return false;
		boolean[] hurdilities = getHurdlesList(m_components);
		return (findSimpleHurdle(hurdilities) == -1);
	}





	/* ******************* CLEARING THE HURDLES METHODS ***************** */
	/* One public method and a million private */


	/* Find a set of reversals to clear the hurdles from the graph */
	public Reversal[] clearHurdles() throws Exception {
		// Get the reversals list
		Reversal[] reversals = getHurdlesReversals();

		// updating the permutation
		for(int i=0; i<reversals.length; i++)
			this.reversal(reversals[i]);

		m_hurdles_num = 0;

		// Update the other data members
		m_components = null;
		makeComponents();

		return reversals;
	}

	public Reversal clearOneHurdle() throws Exception {
		// Get the reversals list
		Reversal[] reversals = getHurdlesReversals();
		if(reversals.length == 0) // No reversals
			return null;
		// We have a real reversal
		// Make the reversal
		this.reversal(reversals[0]);
		return reversals[0];
	}

	public Reversal[] getHurdlesReversals() throws Exception {
		if(m_components == null)
			makeComponents();
		if(this.m_all_components_oriented)
			return new Reversal[0];
		//int count = 0; // mdb removed 6/29/07 #118

		boolean[] hurdilities = getHurdlesList(m_components);

		// k for 2k hurdles, k+1 for 2k+1 hurdles
		int reversals_num_for_clearing_the_hurdles = (m_hurdles_num + 1) / 2;
		Reversal[] reversals = new Reversal[reversals_num_for_clearing_the_hurdles];

		if((m_hurdles_num % 2) == 0)
			getHurdlesReversalsEven(hurdilities, reversals, 0, reversals_num_for_clearing_the_hurdles - 1);
		else
			getHurdlesReversalsOdd(hurdilities, reversals, 0, reversals_num_for_clearing_the_hurdles - 1);

		return reversals;
	}

// mdb removed 6/29/07 #118	
//	private int getComponentIndexByIndex(int index) { return this.m_components_indexes[index]; }
//	private int getComponentIndexByPosition(int position) throws Exception { 
//		return this.m_components_indexes[getIndex(position)]; 
//	}

	// The result is a boolean array with true in the indexes of the hurdles
	private boolean[] getHurdlesList(C_component[] components) {
		boolean[] hurdilities = new boolean[components.length]; // initialized to false
		//int next_index = 0; // mdb removed 6/29/07 #118
		// Check which components are  hurdles
		for(int i=0; i<components.length; i++)
			hurdilities[i] = components[i].isHurdle();

		return hurdilities;
	}

	/* The method for clearing these hurdles is by repeating the action of
       merging two non-consecutive hurdles until there are only two hurdles
       and then merge these two. The order we will make the merges is always merge the
       first hurdle and the third one:
       (0, 1, 2, 3, 4, 5, 6, 7, 8, 9)--> merge <0,2>
       (1, 3, 4, 5, 6, 7, 8, 9)--> merge <1,4>
       (3, 5, 6, 7, 8 ,9)--> merge <3,6>
       (5, 7, 8, 9)--> merge <5,8>
       (7, 9)--> merge <7,9>
       ()
	 */
	private void getHurdlesReversalsEven(boolean[] hurdilities, Reversal[] reversals_array, int from_index, int to_index) throws Exception {
		// These variables represents the indexes of the components that are hurdles
		int first = 0;
		int second, third;
		int start, end;
		int merges_num = to_index + 1 - from_index;

		if(merges_num == 0) // No hurdles
			return;

		int next_reversal_index = from_index;

		// Make all the merges
		for(int i=1; i<=merges_num; i++) {
			// Find the next first
			while(hurdilities[first] == false) 
				first++;
			start = first;
			// Find the next second
			second = first + 1;
			while(hurdilities[second] == false) 
				second++;
			if(i < merges_num) { // Not last iteration
				third = second + 1;
				// Find the next third
				while(hurdilities[third] == false) 
					third++;
				end = third;
			} else
				end = second;
			mergeHurdles(start, end, reversals_array, next_reversal_index);
			next_reversal_index++;
			// Update the hurdles array
			hurdilities[start] = false;
			hurdilities[end] = false;
		}
	}

	// Add to the reversals array the reversal that merges hurdles with the given components id's
	private void mergeHurdles(int component1, int component2, Reversal[] reversals, int next_reversal) throws Exception {
		/* The endpoints group of a componnt must start with a even position
	   and end with odd position (for example start with 0 and end in 7). 
	   Furthermore, the second position must be the first position + 1 since 
	   two endpoints with black edge stick together when dividing to components */
		if(m_components == null)
			this.makeComponents();

		// This one must be odd
		int start_reversal = getPosition(m_components[component1].m_vertices[1]);
		// This one must be even
		int end_reversal = getPosition(m_components[component2].m_vertices[0]);
		reversals[next_reversal] = new Reversal(start_reversal, end_reversal);
	}   


	/* This method compute the reversals for clearing the hurdles. The result is
       inserted to the array in thre specified indexes */
	private void getHurdlesReversalsOdd(boolean[] hurdilities, Reversal[] reversals_array, int from_index, int to_index) throws Exception {

		int simple_hurdle = findSimpleHurdle(hurdilities);
		if(simple_hurdle == -1) { 
			clearFortress(hurdilities, reversals_array, from_index, to_index);
			return;
		}

		// The graph isn't a fortress

		// We get an edge endpoints as the reversal endpoints
		int start_position = getPosition(m_components[simple_hurdle].m_vertices[0]) + 1;
		int start_index = getIndex(start_position);
		int end_index = getNeighbourIndex(start_index);
		int end_position = getPosition(end_index);

		// Generate the first reversal
		reversals_array[from_index] = new Reversal(start_position, end_position);

		// Update the hurdles array that this hurdle is terminated
		hurdilities[simple_hurdle] = false;
		// Take care of the rest
		getHurdlesReversalsEven(hurdilities, reversals_array, from_index+1, to_index);
	}

	// Return the index of the first simple hurdle or -1 if it can't find one
	private int findSimpleHurdle(boolean[] hurdilities) throws Exception {
		if(m_components == null)
			makeComponents();		
		for(int i=0; i<m_components.length; i++)
			if(m_components[i].isSimpleHurdle())
				return i;

		return -1;
	}

	/* The method for clearing a fortress is by repeating the action of
       merging two non-consecutive hurdles until there are only three hurdles
       and then merge both original hurdles (consecutive) and the last merge 
       is of the last two hurdles. The order we will make the merges is always merge the
       first hurdle and the third one:
       (0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)--> merge <0,2>
       (1, 3, 4, 5, 6, 7, 8, 9, 10)--> merge <1,4>
       (3, 5, 6, 7, 8 ,9, 10)--> merge <3,6>
       (5, 7, 8, 9, 10)--> merge <5,8>
       (7, 9, 10)--> merge <7,9>
       (79, 10)--> merge <79,10>
       ()
	 */
	private void clearFortress(boolean[] hurdilities, Reversal[] reversals_array, int from_index, int to_index) throws Exception {
		int first = 0;
		int second, third, start, end;
		int merges_num = to_index + 1 - from_index;

		int next_reversal_index = from_index;

		// Make all the merges but the last one
		for(int i=1; i<=merges_num-1; i++) {
			// Find the start hurdle
			while(hurdilities[first] == false) 
				first++;
			start = first;
			// Find the end hurdle
			second = first + 1;
			while(hurdilities[second] == false) 
				second++;
			third = second + 1;
			// Find the next third
			while(hurdilities[third] == false) 
				third++;
			end = third;

			mergeHurdles(start, end, reversals_array, next_reversal_index);
			next_reversal_index++;
			// Update the hurdles array
			hurdilities[start] = false;
			hurdilities[end] = false;
		}
		/* Done merges_num - 1 merges. We have now 2 hurdles to merge (79, 10 
	   in the example). We can reach the merged new hurdle through index of
	   one of its previous two hurdles (which are now in the start and end 
	   variables) and the second hurdle is the last one of the originals */
		start = first;

		// Find the last true hurdle
		while(hurdilities[first] == false) first++;		

		end = first;
		mergeHurdles(start, end, reversals_array, next_reversal_index);
	}



	/* ***************** HAPPY CLIQUE METHODS ***************** */

	// One public method and some private auxilary methods
	/* Find a happy clique in the graph. Pseudo code for the algorithm for 
       finding the clique :
       variables:
       1. C is the clique
       2. T is a vertex that contains
       3. Ej is the biggest (by left endpoint and as a result of the cliqueness
       by right endpoint too) vertex of C
       4. E1 is the smallest vertex of C
       Flow:
       1. Make a list of the oriented vertices {e1, e2, e3, e4, ...}
       2. Initialize C as {e1} and T as undefined
       3. Pass on the list of oriented vertices and update C and T according to the following rules:
       foreach vertex Ei
       if R(Ej) < L(Ei) 
       break   
       // R(Ej) > L(Ei)
       if((T is defined) && (R(T)<R(Ei)) // Case 1
       don't change C and T
       continue
       if(R(Ei) < R(Ej)) // Case 2c (Ej contains Ei+1)
       C = {Ei} , T = Ej
       continue
       // R(Ej) > L(Ei) , R(Ei) > R(Ej)
       if(L(Ei) < R(E1)) // Case 2a
       T unchanged , C = union(C, {Ei})
       continue
       else // Case 2b
       T unchanged, C = {Ei}
       4. return C 
	 */
	public List findHappyClique() throws Exception { 
		int[] oriented_vertices = getOrientedVerticesByLeftOrder();
		if(oriented_vertices.length == 0)
			return null;

		// Initialize E1 and Ej
		int first_in_happy_clique = oriented_vertices[0];
		int last_in_happy_clique = first_in_happy_clique;

		// Initialize C as {e1} and T as undefined
		List happy_clique = new List(); // C
		happy_clique.add(first_in_happy_clique);
		int container_vertex_index = -1; // T (undefined)

		int left_ei, right_ej, right_ei;

		// Pass on the list of oriented vertices and update C and T
		for(int i=1; i<oriented_vertices.length; i++) {
			left_ei = getLeftEndpoint(oriented_vertices[i]);
			right_ej = getRightEndpoint(last_in_happy_clique);
			right_ei = getRightEndpoint(oriented_vertices[i]);
			if(right_ej < left_ei)
				break; // Got a happy clique

			// R(Ej) > L(Ei)
			if( (container_vertex_index != -1) &&
					(getRightEndpoint(container_vertex_index) < right_ei)) // Case 1
				continue;
			// else
			if(right_ei < right_ej) { // Case 2c (Ej contains Ei+1)
				happy_clique = new List();
				happy_clique.add(oriented_vertices[i]);
				container_vertex_index = last_in_happy_clique;
				first_in_happy_clique = oriented_vertices[i];
				last_in_happy_clique = oriented_vertices[i];
				continue;
			}
			// R(Ej) > L(Ei) , R(Ei) > R(Ej)
			if(left_ei < getRightEndpoint(first_in_happy_clique)) { // Case 2a
				happy_clique.add(oriented_vertices[i]);
				last_in_happy_clique = oriented_vertices[i];
				continue;
			}
			else { // Case 2b
				happy_clique = new List();
				happy_clique.add(oriented_vertices[i]);
				first_in_happy_clique = oriented_vertices[i];
				last_in_happy_clique = oriented_vertices[i];
				continue;
			}
		}
		return happy_clique;
	}   


	// TBD
	public int findVertexWithMaxUnorientedDegree(List happy_clique) throws Exception {
		// Hold the vertices in the clique ordered by endpoints
		int[] vertices = happy_clique.getElementsInArrayByInsertionOrder();

		// Partition the real line to 2j+1 bins. [0] is the min and [1] - the max
		int bins_number = 2 * vertices.length + 1;
		int[][] bins = new int[bins_number][2];

		bins[0][0] = -50000;
		// Fill the bins
		int left, right;
		for(int i=0; i<vertices.length; i++) {
			left = getLeftEndpoint(vertices[i]);
			bins[i][1] = left;
			bins[i+1][0] = left;

			right = getRightEndpoint(vertices[i]);
			bins[vertices.length+i][1] = right;
			bins[vertices.length+i+1][0] = right;
		}
		bins[bins.length-1][1] = 50000;

		int[] u_vertices_by_left_endpoint = getUnorientedVerticesByLeftEndpoints();
		int[] u_vertices_by_right_endpoint = getUnorientedVerticesByRightEndpoints();
		int[] right_to_left = map(u_vertices_by_right_endpoint, u_vertices_by_left_endpoint);
		// u_vertices_by_left_endpoint[right_to_left[i]] = u_vertices_by_right_endpoint[i]

		// We hold here the interval where the left and right endpoints of each vertex fall
		int[][] unoriented_vertices_bins = new int[u_vertices_by_left_endpoint.length][2];
		int left_index = 0;
		int right_index = 0;

		// Pass on the bins and map the vertices to the bins
		for(int i=0; i<bins.length; i++) {
			while((left_index < u_vertices_by_left_endpoint.length) && 
					(getLeftEndpoint(u_vertices_by_left_endpoint[left_index]) < bins[i][1]))
				unoriented_vertices_bins[left_index++][0] = i;
			while((right_index < u_vertices_by_right_endpoint.length) && 
					(getRightEndpoint(u_vertices_by_right_endpoint[right_index]) < bins[i][1]))
				unoriented_vertices_bins[right_to_left[right_index++]][1] = i;
		}   
		// Now we have a O(1) access to the bin of left/right endpoint of every unoriented vertex

		/* Each entry in sum_array will hold the difference between the number 
	   of unoriented neighbours of the vertex and this number in the previous
	   vertex. For compatibleness with the article we add an extra allocated
	   space to avoid confusing with the indexes */
		int[] sum_array = new int[vertices.length+1];

		/* Assume the vertices of the clique are E1, E2, E3, .., Ej
	   their endpoints will be L(E1), L(E2), L(E3), .., L(Ej), R(E1), .., R(Ej)
		 */

		// Pass on all the unoriented vertices of the graph and fill sum_array
		for(int i=0; i<u_vertices_by_left_endpoint.length; i++) {
			int left_bin = unoriented_vertices_bins[i][0];
			int right_bin = unoriented_vertices_bins[i][1];

			if(left_bin == right_bin)
				continue;

			if(right_bin <= vertices.length) { // Case 1
				// All the vertices rom El+1 to Er are adjacent to E
				sum_array[left_bin+1]++;
				if(right_bin < vertices.length)
					sum_array[right_bin+1]--;
				continue;
			}
			if(vertices.length <= left_bin) { // Case 2
				// All the vertices rom El+1 to Er are adjacent to E
				sum_array[left_bin - vertices.length + 1]++;
				if((right_bin - vertices.length + 1) <= vertices.length)
					sum_array[right_bin - vertices.length + 1]--;
				continue;
			}
			if((left_bin < vertices.length) && (vertices.length < right_bin)) { // Case 3
				int m, M;
				if(left_bin < (right_bin - vertices.length)) 
					m = left_bin;
				else 
					m = right_bin - vertices.length;
				if(m > 0) {
					sum_array[1]++;
					sum_array[m+1]--;
				}
				M = left_bin + right_bin - vertices.length - m;
				if(M < vertices.length)
					sum_array[M + 1]++;
				continue;
			}
			throw new Exception("OVGraph::findVertexWithMaxUnorientedDegree - unexpected error");
		}
		// sum_array is now full
		int max_degree = -1;
		int max_index = -1;
		int sum = 0;
		for(int i=1; i<sum_array.length; i++) {
			sum += sum_array[i];
			if(sum > max_degree) {
				max_degree = sum;
				max_index = i;
			}
		}
		return vertices[max_index-1]; // the -1 is for the difference in the indexing
	}

	// Get 2 arrays (that has he same values) and map one to the other
	// TBD - decide if it is more efficient to use quadratic algorithm on the permutation size 
	private int[] map(int[] source, int[] target) throws Exception {
		if(target.length != source.length)
			throw new Exception("OVGraph::map - invoked with arrays with different sizes");
		int[] result = new int[source.length];
		int[] reverse_target = new int[this.getNumOfVertices()];
		// Initialize the array
		for(int i=0; i<reverse_target.length; i++)
			reverse_target[i] = -1;
		// Reverse the target
		for(int i=0; i<target.length; i++)
			reverse_target[target[i]] = i;
		// use the reversed target to map 
		for(int i=0; i<source.length; i++) {
			if(reverse_target[source[i]] == -1)
				throw new Exception("OVGraph::map - invoked with arrays with different values");
			result[i] = reverse_target[source[i]];
		}
		// target[result[i]] = source[i]
		return result;
	}









	// Find the unoriented vertices of the graph from left to right
	private int[] getUnorientedVerticesByLeftEndpoints() throws Exception {
		List temp_result = new List();
		int vertices_num = getNumOfVertices();
		boolean[] check_array = new boolean[vertices_num];
		int permutation_size = m_permutation.getSize();

		// Pass on the positions from left to right and find the unoriented vetices by this order
		int found_left = 0; // counter (0..vertices_num)
		int vertex_index;
		for(int position=0; position<permutation_size; position++) {
			vertex_index = getVertexIndexByPosition(position);
			if(check_array[vertex_index]) // already found the left endpoint
				continue; /// right endpoint
			// else
			check_array[vertex_index] = true;
			if(isUnoriented(vertex_index))
				temp_result.add(vertex_index);
			// Check if we already found all the left endpoints (and we can quit)
			found_left++;
			if(found_left == vertices_num)
				break;
		}
		int[] result = temp_result.getElementsInArrayByInsertionOrder();
		return result;
	}

	// Find the unoriented vertices of the graph from right to left
	private int[] getUnorientedVerticesByRightEndpoints() throws Exception {
		List temp_result = new List();
		int vertices_num = getNumOfVertices();
		boolean[] check_array = new boolean[vertices_num];
		int permutation_size = m_permutation.getSize();

		// Pass on the positions from right to left and find the unoriented vetices by this order
		int found_right = 0; // counter (0..vertices_num)
		int vertex_index;
		for(int position=permutation_size-1; position>=0; position--) {
			vertex_index = getVertexIndexByPosition(position);
			if(check_array[vertex_index]) // already found the right endpoint
				continue; // left endpoint
			// else
			check_array[vertex_index] = true;
			if(isUnoriented(vertex_index))
				temp_result.add(vertex_index);
			// Check if we already found all the left endpoints (and we can quit)
			found_right++;
			if(found_right == vertices_num)
				break;
		}
		int[] result = temp_result.getElementsInArrayNotByInsertionOrder();
		return result;
	}

	public boolean isLeftEndpoint(int index) throws Exception {
		int vertex_index = getVertexIndexByIndex(index);
		int left = getPosition(getLeftEndpoint(vertex_index));
		return (index == left); 
	}

	public void permutationChanged() throws Exception { 
		this.m_components = null;
		this.makeComponents();
	}

	public int[] getHurdlesIndexes() throws Exception {
		if(m_components == null)
			makeComponents();
		List hurdlesList = new List();
		for(int i=0; i<m_components.length; i++)
			if(m_components[i].isHurdle())
				hurdlesList.add(i);
		return hurdlesList.getElementsInArrayByInsertionOrder();
	}

	public int[] getSuperHurdlesIndexes() throws Exception {
		if(m_components == null)
			makeComponents();
		List super_hurdlesList = new List();
		for(int i=0; i<m_components.length; i++)
			if(m_components[i].isSuperHurdle())
				super_hurdlesList.add(i);
		return super_hurdlesList.getElementsInArrayByInsertionOrder();
	}

	public int[] getUnorientedIndexes() throws Exception {
		if(m_components == null)
			makeComponents();
		List unorientedList = new List();
		for(int i=0; i<m_components.length; i++)
			if(m_components[i].isUnoriented())
				unorientedList.add(i);
		return unorientedList.getElementsInArrayByInsertionOrder();
	}
}
