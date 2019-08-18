package backend;

class AnnotElem implements Comparable
{
	int start, end, idx;
	//String type;
	GeneType mGT = GeneType.NA;
	static int NEXTID = 0;
	int mID;
	
	AnnotElem(int start, int end,GeneType gt, int idx)
	{
		this.start = start;
		this.end   = end;
		//this.type  = type;
		this.idx   = idx;
		mGT = gt;
		mID = NEXTID;
		NEXTID++;
	}
	
	// mdb added 7/31/09 #167 - for debug
	public String toString() {
		return "idx=" + idx + " start=" + start + " end=" + end + " length=" + (end-start+1) + " type=" + mGT.toString();
	}
	
	// mdb added 8/14/09
	public int getLength() { return Math.abs(end-start)+1; }
	
	public boolean isGene()
	{
		return (mGT == GeneType.Gene);	
	}
	public int compareTo(Object _a2)
	{
		AnnotElem a2 = (AnnotElem)_a2;
		if (mID < a2.mID) return -1;
		if (mID > a2.mID) return 1;
		return 0;
	}
}