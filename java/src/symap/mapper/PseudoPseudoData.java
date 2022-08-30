package symap.mapper;

import java.util.Collection;
import java.util.Iterator;

import symap.SyMAPConstants;

public class PseudoPseudoData extends AbstractHitData implements SyMAPConstants {	
	private PseudoHitData[] phd;

	public PseudoPseudoData(int project1ID, int group1, int project2ID, int group2, boolean swap) {
		super(project1ID, group1, project2ID, group2, Mapper.PSEUDO2PSEUDO, swap);
	}

	public PseudoPseudoData(int project1ID, int group1, int project2ID, 
			int group2, int type, Collection hitData, boolean swap) 
	{
		this(project1ID,group1,project2ID,group2,swap);
		addHitData(type,hitData);
	}

	public PseudoHitData[] getPseudoHitData() {
		return phd;
	}

	public PseudoHitData[] getHitData() {
		PseudoHitData ret[] = new PseudoHitData[phd.length];
		for (int i = 0; i < phd.length; i++) ret[i] = phd[i];
		return ret;
	}

	private void setHitData(Collection data) {
		phd = (PseudoHitData[])data.toArray(new PseudoHitData[data.size()]);
	}

	public boolean setHitData(PseudoPseudoHits h) {
		phd = h.getPseudoHitData();
		return true;
	}

	// Copy hitData into phd list.
	public boolean addHitData(int newHitContent, Collection hitData) {
		if (phd == null) setHitData(hitData);
		else {
			PseudoHitData tbd[] = new PseudoHitData[phd.length+hitData.size()];
			System.arraycopy(phd,0,tbd,0,phd.length);
			int i = phd.length;
			phd = tbd;
			for (Iterator iter = hitData.iterator(); i < phd.length; i++)
				phd[i] = (PseudoHitData)iter.next();              
		}
		return true;
	}
    // Set in MapperPool.setPseudoPseudoData
	static public HitData getHitData(long id, String name,
			String strand, int repetitive, int block, double evalue,
			double pctid, int start1, int end1, int start2, int end2,
			int overlap, 
			String query_seq, String target_seq)
	{
		return new PseudoHitData(id, name, strand, 
				repetitive == 0 ? false : true, block,
				evalue, pctid, start1, end1, start2, end2, overlap,
				query_seq, target_seq);
	}

	// wrapper class is required because HitData is abstract
	public static class PseudoHitData extends HitData {
		public PseudoHitData(long id, String name,
				String strand, boolean repetitive, int block,
				double evalue, double pctid, 
				int start1, int end1, 
				int start2, int end2, 
				int overlap,	
				String query_seq, String target_seq)
		{
			super(id, name, strand, repetitive, block, evalue, pctid, 
					start1, end1, start2, end2, overlap,
					query_seq, target_seq);
		}

		public boolean equals(Object obj) {
			return obj instanceof PseudoHitData 
				&& ((PseudoHitData)obj).getID() == getID();
		}

		public String toString() { // CAS501 Pseudo->Seq; called by SequenceFilter; shown on CloseUp
			return "Seq Hit Data "
				+ (getName() != null ? getName()+" " : "")
				+ ("#"+getID()); // CAS504 added # 
		}
	}
}
