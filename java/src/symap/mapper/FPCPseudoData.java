package symap.mapper;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

import symap.SyMAPConstants;

//public class FPCPseudoData extends AbstractFPCPseudoHits implements SyMAPConstants { // mdb removed 7/25/07 #134
public class FPCPseudoData extends AbstractHitData implements SyMAPConstants { // mdb added 7/25/07 #134
	private PseudoMarkerData[] mhd;
	private PseudoBESData[]    bhd;

	public FPCPseudoData(int project1ID, int contig, int project2ID, int group) {
		super(project1ID, contig, project2ID, group, Mapper.FPC2PSEUDO, false);
	}

	public FPCPseudoData(int project1ID, int contig, int project2ID, 
			int group, int type, Collection markerData, Collection besData) 
	{
		this(project1ID,contig,project2ID,group);
		addHitData(type,markerData,besData);
	}

	public PseudoMarkerData[] getPseudoMarkerData() {
		return mhd;
	}

	public PseudoBESData[] getPseudoBESData() {
		return bhd;
	}

	public HitData[] getHitData() {
		HitData ret[] = new HitData[mhd.length+bhd.length];
		int i;
		for (i = 0; i < mhd.length; i++) ret[i] = mhd[i];
		int j = i;
		for (i = 0; i < bhd.length; i++) ret[j+i] = bhd[i];
		return ret;
	}

	private void setMarkerData(Collection data) {
		mhd = (PseudoMarkerData[])data.toArray(new PseudoMarkerData[data.size()]);
	}

	private void setBESData(Collection data) {
		bhd = (PseudoBESData[])data.toArray(new PseudoBESData[data.size()]);
	}

	public boolean setHitData(FPCPseudoHits h) {
		if (upgradeHitContent(h.getHitContent())) {
			mhd = h.getPseudoMarkerHitData();
			bhd = h.getPseudoBESHitData();
			return true;
		}
		return false;
	}

	public boolean addHitData(int newHitContent, Collection markerData, Collection besData) {
		if (upgradeHitContent(newHitContent)) {
			if (mhd == null) setMarkerData(markerData);
			else {
				PseudoMarkerData tmd[] = new PseudoMarkerData[mhd.length+markerData.size()];
				System.arraycopy(mhd,0,tmd,0,mhd.length);
				int i = mhd.length;
				mhd = tmd;
				for (Iterator iter = markerData.iterator(); i < mhd.length; i++)
					mhd[i] = (PseudoMarkerData)iter.next();
			}
			if (bhd == null) setBESData(besData);
			else {
				PseudoBESData tbd[] = new PseudoBESData[bhd.length+besData.size()];
				System.arraycopy(bhd,0,tbd,0,bhd.length);
				int i = bhd.length;
				bhd = tbd;
				for (Iterator iter = besData.iterator(); i < bhd.length; i++)
					bhd[i] = (PseudoBESData)iter.next();              
			}
			return true;
		}
		return false;
	}

	public static List getFPCPseudoData(int p1, int p2, int group, Collection contigList) {
		List<FPCPseudoData> list = new ArrayList<FPCPseudoData>(contigList == null ? 0 : contigList.size());
		if (contigList == null) return list;
		Iterator iter = contigList.iterator();
		while (iter.hasNext())
			list.add(new FPCPseudoData(p1,((Integer)iter.next()).intValue(),p2,group));
		return list;
	}

	public PseudoMarkerData getMarkerData(long id, String name, String strand,
			int repetitive, RepetitiveMarkerFilterData rmfd, long block,
			double evalue, double pctid, int start2, int end2,
			String query_seq, String target_seq, int gene_olap) 
	{
		return new PseudoMarkerData(id, name, strand, repetitive != 0
				|| rmfd.isRepetitive(name), (block > 0), evalue, pctid, start2,
				end2, query_seq, target_seq, gene_olap);
	}

/*	public PseudoMarkerData getMarkerData(long id, String name, String strand,
			int repetitive, long block, double evalue, double pctid,
			int start2, int end2, String query_seq, String target_seq)  
	{
		return new PseudoMarkerData(id, name, strand, repetitive != 0,
				(block > 0), evalue, pctid, start2, end2, query_seq, target_seq); 
	}
*/
	public PseudoBESData getBESData(long id, String name, byte bes,
			String strand, int repetitive, int block, double evalue,
			double pctid, int start2, int end2, int cb1, int cb2, byte bes1,
			byte bes2, String query_seq, String target_seq, int gene_olap) 
	{
		return new PseudoBESData(id, name, (bes == bes1 ? cb1 : cb2), bes,
				strand, repetitive == 0 ? false : true, (block > 0),
				evalue, pctid, start2, end2, query_seq, target_seq, gene_olap);
	}

	public static class PseudoMarkerData extends HitData {

		public PseudoMarkerData(long id, String name, String strand,
				boolean repetitive, boolean block, double evalue, double pctid,
				int start2, int end2, String query_seq, String target_seq, int gene_olap) 
		{
			super(id,name,strand,repetitive,block,evalue,pctid,start2,end2,query_seq,target_seq,gene_olap);
		}

		public boolean equals(Object obj) {
			return obj instanceof PseudoMarkerData && ((PseudoMarkerData)obj).getID() == getID();
		}

		public String toString() {
			return "Marker "+getName();
		}
	}

	public static class PseudoBESData extends HitData {		
		private int pos;
		private byte bes;

		public PseudoBESData(long id, String name, int pos, byte bes,
				String strand, boolean repetitive, boolean block,
				double evalue, double pctid, int start2, int end2,
				String query_seq, String target_seq, int gene_olap) // mdb added query_seq/target_seq 8/22/07 #126
		{
			super(id, name, strand, repetitive, block, evalue, pctid, start2, end2, query_seq, target_seq, gene_olap);
			this.pos = pos;
			this.bes = bes;
		}

		public int getPos() {
			return pos;
		}

		public byte getBES() {
			return bes;
		}

		public String getBESString() {
			switch (bes) {
			case R_VALUE: 
				return R_VALUE_STR;
			case F_VALUE: 
				return F_VALUE_STR;
			}
			return NORF_VALUE_STR;
		}

		public boolean equals(Object obj) {
			return obj instanceof PseudoBESData && ((PseudoBESData)obj).getID() == getID();
		}

		public String toString() {
			return "Clone "+getName()+getBESString();
		}
	}
}
