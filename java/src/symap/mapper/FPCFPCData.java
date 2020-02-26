package symap.mapper;

import java.util.Collection;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedList;
import java.util.Iterator;

public class FPCFPCData extends AbstractHitData { 
	private FPHitData fphd[];
	private String markerBlockHits[];

	public FPCFPCData(int p1, int c1, int p2, int c2) {
		super(p1,c1,p2,c2,Mapper.FPC2FPC,false); 
		fphd = null;
		markerBlockHits = null;
	}

	public boolean setHitData(FPCFPCHits h) {
		if (upgradeHitContent(h.getHitContent())) {
			fphd = h.getFPHitData();
			return true;
		}
		return false;
	}

	public boolean addHitData(int newHitContent, Collection<FPHitData> fpHitData) {
		if (upgradeHitContent(newHitContent)) {
			if (fpHitData != null && !fpHitData.isEmpty()) {
				int i = 0;
				if (fphd == null)
					fphd = new FPHitData[fpHitData.size()];
				else {
					FPHitData td[] = new FPHitData[fphd.length+fpHitData.size()];
					System.arraycopy(fphd,0,td,0,fphd.length);
					i = fphd.length;
					fphd = td;
				}
				for (Iterator<FPHitData> iter = fpHitData.iterator(); i < fphd.length; ++i)
					fphd[i] = iter.next();
			}
			return true;
		}
		return false;
	}

	public FPHitData[] getFPHitData() {
		if (fphd == null) return new FPHitData[0];
		return fphd;
	}

	public void setSharedMarkerBlockHits(Collection<String> blockHitMarkers) {
		if (blockHitMarkers != null) {
			markerBlockHits = new String[blockHitMarkers.size()];
			Iterator<String> iter = blockHitMarkers.iterator();
			for (int i = 0; i < markerBlockHits.length; i++)
				markerBlockHits[i] = (iter.next());
			Arrays.sort(markerBlockHits);
		}
		else markerBlockHits = new String[0];
	}

	public boolean isBlockHitMarker(String marker) {
		if (markerBlockHits == null) return false;
		return Arrays.binarySearch(markerBlockHits,marker) >= 0;
	}

	public boolean isSharedMarkerBlockHitsSet() {
		return markerBlockHits != null;
	}

	public static List<FPCFPCData> getNeededBlockDataList(List dataList) {
		List<FPCFPCData> list = new LinkedList<FPCFPCData>();
		if (dataList == null) return list;
		FPCFPCData data;
		for (Iterator<FPCFPCData> iter = dataList.iterator(); iter.hasNext();) {
			data = (FPCFPCData)iter.next();
			if (data.markerBlockHits == null) list.add(data);
		}
		return list;
	}

	public static FPHitData getFPHitData(String clone1, String clone2, int pos1, int pos2, double score, int repetitive, int block) {
		return new FPHitData(clone1,clone2,pos1,pos2,score,repetitive != 0,block > 0);
	}

	public static class FPHitData {
		private String clone1, clone2;
		private int pos1, pos2;
		private double score;
		private byte repetitive;
		private byte block;

		public FPHitData(String clone1, String clone2, int pos1, int pos2, double score, boolean repetitive, boolean block) {
			this.clone1 = clone1;
			this.clone2 = clone2;
			this.pos1 = pos1;
			this.pos2 = pos2;
			this.score = score;
			this.repetitive = repetitive ? (byte)1 : (byte)0;
			this.block = block ? (byte)1 : (byte)0;
		}

		public String getClone1() {
			return clone1;
		}

		public String getClone2() {
			return clone2;
		}

		public boolean isRepetitiveHit() {
			return repetitive != 0;
		}

		public boolean isBlockHit() {
			return block != 0;
		}

		public double getScore() {
			return score;
		}

		public int getPos1() {
			return pos1;
		}

		public int getPos2() {
			return pos2;
		}

		public boolean equals(Object obj) {
			if (obj instanceof FPHitData) {
				FPHitData h = (FPHitData)obj;
				return clone1 == h.clone1 && clone2 == h.clone2;
			}
			return false;
		}
	}
}

