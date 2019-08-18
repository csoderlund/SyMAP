package symap.mapper;

import java.util.Collection;
import java.util.Iterator;

import symap.SyMAPConstants;

// mdb added class 7/11/07 #121
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
		//if (upgradeHitContent(h.getHitContent())) {
			phd = h.getPseudoHitData();
			return true;
		//}
		//return false;
	}

	// Copy hitData into phd list.
	public boolean addHitData(int newHitContent, Collection hitData) {
		//if (upgradeHitContent(newHitContent)) {
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
		//}
		//return false;
	}

//	public static List getPseudoPseudoData(int p1, int p2, int group1, int group2) {
//		List list = new ArrayList(contigList == null ? 0 : contigList.size());
//		if (contigList == null) return list;
//		Iterator iter = contigList.iterator();
//		while (iter.hasNext())
//			list.add(new PseudoPseudoData(p1,((Integer)iter.next()).intValue(),p2,group));
//		return list;
//	}

	static public HitData getHitData(long id, String name,
			String strand, int repetitive, int block, double evalue,
			double pctid, int start1, int end1, int start2, int end2,
			int overlap, // mdb added overlap 2/19/08 #150
			String query_seq, String target_seq)// mdb added query_seq/target_seq 4/17/09 #126
	{
		return new PseudoHitData(id, name, strand, 
				repetitive == 0 ? false : true, (block > 0),
				evalue, pctid, start1, end1, start2, end2, overlap,
				query_seq, target_seq);
	}

	// mdb: this wrapper class is required because HitData is abstract
	public static class PseudoHitData extends HitData {
		public PseudoHitData(long id, String name,
				String strand, boolean repetitive, boolean block,
				double evalue, double pctid, 
				int start1, int end1, 
				int start2, int end2, 
				int overlap,	// mdb added overlap 2/18/08
				String query_seq, String target_seq)// mdb added query_seq/target_seq 4/17/09 #126
		{
			super(id, name, strand, repetitive, block, evalue, pctid, 
					start1, end1, start2, end2, overlap,
					query_seq, target_seq);
		}

		public boolean equals(Object obj) {
			return obj instanceof PseudoHitData 
				&& ((PseudoHitData)obj).getID() == getID();
		}

		public String toString() {
			return "Pseudo Hit Data "
				+ (getName() != null ? getName()+" " : "")
				+ getID(); // mdb changed 1/9/09 for pseudo-pseudo closeup
		}
	}
}
