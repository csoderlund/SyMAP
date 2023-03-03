package backend;

/****************************************************
 *  AnchorMain: Stores the query/target side of hit data
 */
public class SubHit  {
	public int start, end, grpIdx = 0;		
	public String name; 		
	public int[] subHits; // array of sub-hits start/end coordinates; CAS535 [blocks]
	public HitStatus status = HitStatus.Undecided; // AnchorsMain {In, Out, Undecided };
	
	public SubHit(SubHit h) {
		end =      h.end;
		start =    h.start;
		grpIdx =   h.grpIdx;
		name = 	   new String(h.name);
		status =   h.status;
		subHits =   new int[h.subHits.length];
		for (int i = 0; i < subHits.length; i++) {
			subHits[i] = h.subHits[i];
		}
	}
	
	public SubHit(int numBlocks) {
		subHits = new int[numBlocks];
	}
	
	public String toString() {
		String s = "";
		for (int i = 0;  i < subHits.length - 1;  i+=2)
			s += subHits[i] + ":" + subHits[i+1] + ",";
		return s;
	}
	
	public int length() { return (Math.abs(end-start)+1); }
	
	public boolean isOverlapping(SubHit h) {
		return Utils.intervalsTouch(this.start, this.end, h.start, h.end);
	}
}
