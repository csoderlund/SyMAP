package symap.mapper;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import symap.track.Track;
import symap.contig.Contig;
import symap.sequence.Sequence;
import symap.marker.MarkerTrack;

public class MapInfo {
	public static final int NOT_SET       = 0;

	// Track Types
	public static final int BLOCK_TYPE    =  1;
	public static final int CONTIG_TYPE   =  2;
	public static final int SEQUENCE_TYPE =  3;

	// Hit Content Types
	public static final int ONLY_BLOCK_HITS    = 1; // aka synteny hits
	public static final int ONLY_FILTERED_HITS = 2;
	public static final int ALL_HITS           = 3;

	private int t1Type,t2Type; // Track types
	private int hitContent;
	private int p1, p2; // Project IDs
	private int[][] contents;

	public MapInfo() { 
		clear();
	}

	public MapInfo(MapInfo mi) {
		set(mi);
	}

	public MapInfo(Track t1, Track t2, boolean showOnlyBlock, boolean showOnlyFiltered) {
		this(t1,t2,getHitContent(showOnlyBlock,showOnlyFiltered));
	}

	public MapInfo(Track t1, Track t2, int hitContent) {
		set(t1,t2,hitContent);
	}

	public String toString() {
		return "[MapInfo hitContent = "+hitContent+" t1Type = "+t1Type+" t2Type = "+t2Type+"]";
	}

	public int getProject1() {
		return p1;
	}

	public int getProject2() {
		return p2;
	}

	public int getHitContent() {
		return hitContent;
	}

	public void upgradeHitContent(int nHitContent) {
		hitContent = upgradeHitContent(hitContent,nHitContent);
	}

	public static int upgradeHitContent(int hitContent, int nHitContent) {
		if (nHitContent == ALL_HITS) hitContent = ALL_HITS;
		else if (nHitContent == ONLY_FILTERED_HITS) {
			if (hitContent != ALL_HITS) hitContent = ONLY_FILTERED_HITS;
		}
		else if (nHitContent == ONLY_BLOCK_HITS) {
			if (hitContent == NOT_SET) hitContent = ONLY_BLOCK_HITS;
		}
		return hitContent;
	}

	public static int getHitContent(boolean showOnlyBlock, boolean showOnlyFiltered) {
		if (showOnlyFiltered) return ONLY_FILTERED_HITS;
		if (showOnlyBlock)    return ONLY_BLOCK_HITS;
		return ALL_HITS;
	}

	public boolean hasHitContent(boolean showOnlyBlock, boolean showOnlyFiltered) {
		return hasHitContent(hitContent,showOnlyBlock,showOnlyFiltered);
	}

	public static boolean hasHitContent(int hc, boolean showOnlyBlock, boolean showOnlyFiltered) {
		if (hc == NOT_SET)            return false;
		if (hc == ONLY_FILTERED_HITS) return showOnlyFiltered || showOnlyBlock;
		if (hc == ONLY_BLOCK_HITS)    return showOnlyBlock;
		else                          return true;
	}

	public boolean hasHitContent(int otherHitContent) {
		return hasHitContent(hitContent,otherHitContent);
	}

	public static boolean hasHitContent(int src, int other) {
		return src >= other;
	}

	public boolean equalProjects(MapInfo info) {
		return (info.p1 == p1 && info.p2 == p2) || (info.p1 == p2 && info.p2 == p1);
	}

	public void clear() {
		p1 = p2 = t1Type = t2Type = hitContent = NOT_SET;
		contents = new int[2][];
		contents[0] = new int[0];
		contents[1] = new int[0];
	}

	/**
	 * Method <code>equalIfUpgradeHitContent</code> returns true if the MapInfo 
	 * would be equal to mi if it (the calling object) where to upgrade it's 
	 * hit content to mi's hit content (mi.hasHitContent(getHitContent())).
	 *
	 * @param mi a <code>MapInfo</code> value
	 * @return a <code>boolean</code> value
	 */
	public boolean equalIfUpgradeHitContent(MapInfo mi) {
		if (t1Type == mi.t1Type && t2Type == mi.t2Type 
				&& p1 == mi.p1 && p2 == mi.p2 && mi.hasHitContent(hitContent)) 
		{
			if (mi.contents[0].length != contents[0].length 
					|| mi.contents[1].length != contents[1].length) return false;
			for (int j = 0; j < 2; j++)
				for (int i = 0; i < contents[j].length; i++)
					if (mi.contents[j][i] != contents[j][i]) return false;
			return true;
		}	
		return false;
	}

	public boolean equals(Object obj) {
		if (obj instanceof MapInfo) {
			MapInfo mi = (MapInfo)obj;
			if (t1Type == mi.t1Type && t2Type == mi.t2Type 
					&& p1 == mi.p1 && p2 == mi.p2 && hitContent == mi.hitContent) {
				if (mi.contents[0].length != contents[0].length 
						|| mi.contents[1].length != contents[1].length) return false;
				for (int j = 0; j < 2; j++)
					for (int i = 0; i < contents[j].length; i++)
						if (mi.contents[j][i] != contents[j][i]) return false;
				return true;
			}
		}
		return false;
	}

	public MapInfo copy() {
		return new MapInfo(this);
	}

	public void set(MapInfo mi) {
		t1Type = mi.t1Type;
		t2Type = mi.t2Type;
		p1 = mi.p1;
		p2 = mi.p2;
		contents = (int[][])mi.contents.clone();
		hitContent = mi.hitContent;
	}

	public void setHitContent(int hc) {
		hitContent = hc;
	}

	public void set(Track t1, Track t2, int hitContent) {
		this.hitContent = hitContent;

		contents = new int[2][];

		p1 = t1.getProject();
		p2 = t2.getProject();

		if      (t1 instanceof Sequence)    t1Type = SEQUENCE_TYPE;
		else if (t1 instanceof Contig)      t1Type = CONTIG_TYPE;
		else                                t1Type = BLOCK_TYPE;
		if      (t2 instanceof Sequence)    t2Type = SEQUENCE_TYPE;
		else if (t2 instanceof Contig)      t2Type = CONTIG_TYPE;
		else                                t2Type = BLOCK_TYPE;

		 // mdb added pseudo-to-pseudo 7/11/07 #121
		if (t1Type == SEQUENCE_TYPE && t2Type == SEQUENCE_TYPE) { // PSEUDO to PSEUDO
			Sequence st1 = (Sequence)t1;
			Sequence st2 = (Sequence)t2;
			contents[0] = new int[1];
			contents[1] = new int[1];
			contents[0][0] = st1.getGroup();
			contents[1][0] = st2.getGroup();
		}
		else if (t1Type == SEQUENCE_TYPE || t2Type == SEQUENCE_TYPE) { // FPC to PSEUDO
			Sequence st;
			MarkerTrack mt;
			int si;
			if (t1Type == SEQUENCE_TYPE) {
				si = 0;
				st = (Sequence)t1;
				mt = (MarkerTrack)t2;
			}
			else {
				si = 1;
				st = (Sequence)t2;
				mt = (MarkerTrack)t1;
			}
			contents[si] = new int[1];
			contents[si][0] = st.getGroup();
			contents[(si+1)%2] = mt.getContigs();
		}
		else { // FPC to FPC
			contents[0] = ((MarkerTrack)t1).getContigs();
			contents[1] = ((MarkerTrack)t2).getContigs();
		}
		Arrays.sort(contents[0]);
		Arrays.sort(contents[1]);
	}

	public int getMapperType() {
		//return t1Type == SEQUENCE_TYPE || t2Type == SEQUENCE_TYPE ? Mapper.FPC2PSEUDO : Mapper.FPC2FPC; // mdb removed 7/11/07 #121
		if (t1Type == SEQUENCE_TYPE && t2Type == SEQUENCE_TYPE) return Mapper.PSEUDO2PSEUDO; // mdb added 7/11/07 #121
		if (t1Type == SEQUENCE_TYPE || t2Type == SEQUENCE_TYPE) return Mapper.FPC2PSEUDO; // mdb added 7/11/07 #121
		return Mapper.FPC2FPC; // mdb added 7/11/07 #121
	}

	public int getGroup() {
		if (t1Type == SEQUENCE_TYPE) return contents[0][0];
		if (t2Type == SEQUENCE_TYPE) return contents[1][0];
		return -1;
	}

	public int[] getContigs() {
		if (t1Type == SEQUENCE_TYPE) return (int[])contents[1].clone();
		if (t2Type == SEQUENCE_TYPE) return (int[])contents[0].clone();
		return null;
	}

	public List<Integer> getContigList() {
		if (t2Type == SEQUENCE_TYPE) return myGetList(contents[0]);
		if (t1Type == SEQUENCE_TYPE) return myGetList(contents[1]);
		return null;
	}

	private List<Integer> myGetList(int[] contigs) {
		if (contigs == null) return null;
		List<Integer> list = new ArrayList<Integer>(contigs.length);
		for (int i = 0; i < contigs.length; i++) list.add(new Integer(contigs[i]));
		return list;
	}
}    
