package backend;

// Stores the query/target side of hit data
public class SubHit 
{
	// IF CHANGED, ADD TO COPY CONSTRUCTOR
	public int start, end, grpIdx = 0;		
	public String name; 		
	public int[] blocks; // array of sub-block start/end coordinates
	public HitStatus status = HitStatus.Undecided;
	
	public SubHit(SubHit h)
	{
		end =      h.end;
		start =    h.start;
		grpIdx =   h.grpIdx;
		name = 	   new String(h.name);
		status =   h.status;
		blocks =   new int[h.blocks.length];
		for (int i = 0; i < blocks.length; i++)
		{
			blocks[i] = h.blocks[i];
		}
	}
	
	public SubHit(int numBlocks) {
		blocks = new int[numBlocks];
	}
	
	public String toString() {
		String s = "";
		for (int i = 0;  i < blocks.length - 1;  i+=2)
			s += blocks[i] + ":" + blocks[i+1] + ",";
		return s;
	}
	
	public int length() { return (Math.abs(end-start)+1); }
	
	public boolean isOverlapping(SubHit h) {
		return Utils.intervalsTouch(this.start, this.end, h.start, h.end);
	}
}
