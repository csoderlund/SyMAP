package symap.frame;

/*********************************************************
 * Represents blocks for the ProjIcons on the left side of the explorer
 */
public class Block {
	private int nBlockIdx;
	private int nProj1Idx, nProj2Idx;
	private int nGroup1Idx, nGroup2Idx;
	private int start1, end1;
	private int start2, end2;
	private float corr;
	
	protected Block(int nBlockIdx, int nProj1Idx, int nProj2Idx, int nGroup1Idx, int nGroup2Idx, 
			int start1, int end1, int start2, int end2,  float corr)
	{
		this.nBlockIdx = nBlockIdx;
		this.nProj1Idx = nProj1Idx;
		this.nProj2Idx = nProj2Idx;
		this.nGroup1Idx = nGroup1Idx;
		this.nGroup2Idx = nGroup2Idx;
		this.start1 = start1;
		this.end1 = end1;
		this.start2 = start2;
		this.end2 = end2;
		this.corr = corr;
	}
	
	public boolean equals(Object obj) { // override Object method
		return ( nBlockIdx == ((Block)obj).nBlockIdx );
	}
	
	protected boolean isTarget(int nProjIdx) { return nProj2Idx == nProjIdx; } // projIdxon
	
	protected Block swap() { 
		return new Block(nBlockIdx, nProj2Idx, nProj1Idx, nGroup2Idx, nGroup1Idx, start2, end2, start1, end1, corr);
	}
	
	protected int getStart1() { return start1; }
	protected int getEnd1() { return end1; }
	protected int getStart2() { return start2; }
	protected int getEnd2() { return end2; }
	protected int getProj1Idx() { return nProj1Idx; }
	protected int getProj2Idx() { return nProj2Idx; }
	protected int getGroup1Idx() { return nGroup1Idx; }
	protected int getGroup2Idx() { return nGroup2Idx; }
}
