package backend;

// Stores the query/target side of hit data
public class SubHit 
{
	// IF CHANGED, ADD TO COPY CONSTRUCTOR
	public int start;
	public int end;
	public int grpIdx = 0;		// FIXME inefficient use of memory
	public String name; 		// FIXME inefficient use of memory
	public int[] blocks; // array of sub-block start/end coordinates
	public HitStatus status = HitStatus.Undecided;
	public FileType fileType; 	// FIXME inefficient use of memory
	
	public SubHit() {
			
		blocks = new int[0]; // prevent null pointer exception
	}
	public SubHit(SubHit h)
	{
		end = h.end;
		start = h.start;
		fileType = h.fileType;
		grpIdx = h.grpIdx;
		name = new String(h.name);
		status = h.status;
		blocks = new int[h.blocks.length];
		for (int i = 0; i < blocks.length; i++)
		{
			blocks[i] = h.blocks[i];
		}
	}
	// mdb added 8/5/09 #167
	public SubHit(int numBlocks) {
		blocks = new int[numBlocks];
	}
	
	public String toString() {
		String s = "";
		for (int i = 0;  i < blocks.length - 1;  i+=2)
			s += blocks[i] + ":" + blocks[i+1] + ",";
		return s;
	}
	
	// mdb added 8/4/09
	public int length() { return (Math.abs(end-start)+1); }
	
	// mdb added 8/3/09 #167
	public boolean isOverlapping(SubHit h) {
		return Utils.intervalsTouch(this.start, this.end, h.start, h.end);
	}
}
