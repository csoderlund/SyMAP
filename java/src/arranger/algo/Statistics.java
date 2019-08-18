package arranger.algo;


public class Statistics {
	// Static values for computing averages
	int m_instances_number;
	int m_summed_breakpoints_number;
	int m_summed_cycles_number;
	int m_summed_hurdles_number;
	int m_summed_super_hurdles_number;
	int m_summed_fortress;
	int m_summed_reversal_distance;
	int m_permutation_size;
	
	public Statistics() {
		//int m_instances_number = 0; 				// mdb removed 6/29/07 #118
		//int m_summed_breakpoints_number = 0; 		// mdb removed 6/29/07 #118
		//int m_summed_cycles_number = 0; 			// mdb removed 6/29/07 #118
		//int m_summed_hurdles_number = 0; 			// mdb removed 6/29/07 #118
		//int m_summed_super_hurdles_number = 0; 	// mdb removed 6/29/07 #118
		//int m_summed_fortress = 0;			 	// mdb removed 6/29/07 #118
		//int m_summed_reversal_distance = 0; 		// mdb removed 6/29/07 #118
	}
	
	public String getGraphStatistics(OVGraph graph) throws Exception {
		m_permutation_size = graph.getPermutationSize();
		m_instances_number++;
		//int index = m_instances_number; // mdb removed 6/29/07 #118
		int breakpoints_number = graph.getBreakpointsNumber();
		int cycles_number = graph.getCyclesNumber();
		int hurdles_number = graph.getHurdlesNumber();
		int super_hurdles_number = graph.getSuperHurdlesNumber();
		int fortress_number;
		if(graph.isFortress()) fortress_number = 1;
		else fortress_number = 0;
		int reversal_distance = breakpoints_number - cycles_number + hurdles_number + fortress_number;
		
		m_summed_breakpoints_number += breakpoints_number;
		m_summed_cycles_number += cycles_number;
		m_summed_hurdles_number += hurdles_number;
		m_summed_super_hurdles_number += super_hurdles_number;
		m_summed_fortress += fortress_number;
		m_summed_reversal_distance += reversal_distance;
		
		return new String("permutation " + m_instances_number +
						  " b=" + breakpoints_number +
						  " c=" + cycles_number +
						  " h=" + hurdles_number +
						  " sh=" + super_hurdles_number +
						  " f=" + fortress_number +
						  " d=" + reversal_distance
						  );
	}
	
	public String getSummary() {
		return new String("" + m_instances_number + " permutations" +
						  "\naverage breakpoints number is " + ((double)m_summed_breakpoints_number/m_instances_number) + " (from possible " + (m_permutation_size/2 + 1) + ")" +
						  "\naverage cycles number is " + ((double)m_summed_cycles_number/m_instances_number) +
						  "\naverage hurdles number is " + ((double)m_summed_hurdles_number/m_instances_number) +
						  "\naverage super hurdles number is " + ((double)m_summed_super_hurdles_number/m_instances_number) +
						  "\n" + (((double)m_summed_fortress/m_instances_number)*100) + "% of the permutations where fortress" +
						  "\naverage reversal distance is " + ((double)m_summed_reversal_distance/m_instances_number));
	}
}
