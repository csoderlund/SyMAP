package arranger.algo;

public class Reversal {
    // Data members
    private int m_start;
    private int m_end;
	
    public Reversal(int start, int end) {
	m_start = start;
	m_end = end;
    }
    public String toString(Permutation p, boolean reversal_done) {
	try {
	    int from, to;
	    if(reversal_done) {
		from = p.getIndex(m_end);
		to = p.getIndex(m_start);
	    } else {
		from = p.getIndex(m_start);
		to = p.getIndex(m_end);
	    }
	    return new String(from + " to " + to); 
	} catch(Exception e) { return ""; }
    }

    // Return the reversal by position
    public String toString() {
	return new String("from position " + m_start + " to position " + m_end); 
    }
    public int getStart() { return m_start; }
    public int getEnd() { return m_end; }	
}
