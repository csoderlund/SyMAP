package backend;

/*****************************************
 * Either gene, nongene or n/a
 * Created in Group, accessed in AnchorMain as mostOverlappingAnnot
 */
public class AnnotElem implements Comparable <AnnotElem> // CAS500 added <type>; comparable so can be used in TreeMap
{
	public int start, end, idx;
	public GeneType mGT = GeneType.NA;
	public int mID;
	
	private static int NEXTID = 0;
	
	AnnotElem(int start, int end,  GeneType gt, int idx){
		this.start = start;
		this.end   = end;
		this.idx   = idx;
		mGT = gt;
		mID = NEXTID;
		NEXTID++;
	}
	
	public String toString() {
		return "idx=" + idx + " start=" + start + " end=" + end + " length=" + (end-start+1) + " type=" + mGT.toString();
	}
	
	public int getLength() { return Math.abs(end-start)+1; }
	
	public boolean isGene(){
		return (mGT == GeneType.Gene);	
	}
	public int compareTo(AnnotElem _a2) {
		AnnotElem a2 = (AnnotElem)_a2;
		if (mID < a2.mID) return -1;
		if (mID > a2.mID) return 1;
		return 0;
	}
}