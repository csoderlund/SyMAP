package symap.mapper;

import java.util.Arrays;

import symap.track.Track;
import symap.sequence.Sequence;

public class MapInfo {
	public static final int NOT_SET       = 0;

	// Hit Content Types
	public static final int ONLY_BLOCK_HITS    = 1; // aka synteny hits
	public static final int ONLY_FILTERED_HITS = 2;
	public static final int ALL_HITS           = 3;

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
		return "[MapInfo hitContent = "+hitContent+"]";
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
		p1 = p2 = hitContent = NOT_SET;
		contents = new int[2][];
		contents[0] = new int[0];
		contents[1] = new int[0];
	}

	/**
	 * Method equalIfUpgradeHitContent returns true if the MapInfo 
	 * would be equal to mi if it (the calling object) where to upgrade it's 
	 * hit content to mi's hit content (mi.hasHitContent(getHitContent())).
	 */
	public boolean equalIfUpgradeHitContent(MapInfo mi) {
		if (p1 == mi.p1 && p2 == mi.p2 && mi.hasHitContent(hitContent)) 
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
			if (p1 == mi.p1 && p2 == mi.p2 && hitContent == mi.hitContent) {
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

		Sequence st1 = (Sequence)t1;
		Sequence st2 = (Sequence)t2;
		contents[0] = new int[1];
		contents[1] = new int[1];
		contents[0][0] = st1.getGroup();
		contents[1][0] = st2.getGroup();
		
		Arrays.sort(contents[0]);
		Arrays.sort(contents[1]);
	}
}    
