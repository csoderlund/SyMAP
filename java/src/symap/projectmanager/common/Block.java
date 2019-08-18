package symap.projectmanager.common;

public class Block {
	//private int nBlockNum;
	private int nBlockIdx;
	private int nProj1Idx, nProj2Idx;
	private int nGroup1Idx, nGroup2Idx;
	private long start1, end1;
	private long start2, end2;
	private String strCtgs1, strCtgs2;
	private float corr;
	
	public Block(int nBlockIdx, int nProj1Idx, int nProj2Idx, int nGroup1Idx, int nGroup2Idx, 
			long start1, long end1, long start2, long end2, String ctgs1, String ctgs2, float corr)
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
		this.strCtgs1 = ctgs1;
		this.strCtgs2 = ctgs2;
		this.corr = corr;
	}
	
	public boolean equals(Object obj) { // override Object method
		return ( nBlockIdx == ((Block)obj).nBlockIdx );
	}
	
	public boolean isTarget(int nProjIdx) { return nProj2Idx == nProjIdx; }
	
	public Block swap() {
		return new Block(nBlockIdx, nProj2Idx, nProj1Idx, nGroup2Idx, nGroup1Idx, start2, end2, start1, end1, strCtgs2, strCtgs1,corr);
	}
	
	public long getStart1() { return start1; }
	public long getEnd1() { return end1; }
	public long getStart2() { return start2; }
	public long getEnd2() { return end2; }
	public int getProj1Idx() { return nProj1Idx; }
	public int getProj2Idx() { return nProj2Idx; }
	public int getGroup1Idx() { return nGroup1Idx; }
	public int getGroup2Idx() { return nGroup2Idx; }
	public String getCtgs1() { return strCtgs1; }
	public String getCtgs2() { return strCtgs2; }
	public float getCorr() { return corr; }
	public boolean inverted() { return (corr < 0);}
	
//	public String toString() {
//		return nProj1Idx + "." + nProj2Idx + "." + nGroup1Idx;
//	}
}
