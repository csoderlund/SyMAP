package symap.mapper;

import java.util.List;
import java.util.RandomAccess;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Class <code>AbstractHitData</code>
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 */
public abstract class AbstractHitData {
	private int hitContent;
	private int project1, project2; // project IDs
	private int group1, group2; // content IDs: contig or chromosome // mdb added 7/25/07 #134
	private int mapType; // mdb added 7/25/07 #134
	private boolean swap; // mdb added 12/30/08 for pseudo-pseudo closeup - indicates whether hit coords are swapped
	protected boolean bHighlight;
	
	public AbstractHitData(int project1, int content1, int project2, 
			int content2, int mapType, // mdb added content1/content2/mapType 7/25/07 #134
			boolean swap) // mdb added swap 12/30/08
	{
		this.project1 = project1;
		this.project2 = project2;
		this.group1 = content1; // mdb added 7/25/07 #134
		this.group2 = content2; // mdb added 7/25/07 #134
		this.mapType  = mapType;// mdb added 7/25/07 #134
		this.hitContent = MapInfo.NOT_SET;
		this.swap = swap; // mdb added 12/30/08 for pseudo-pseudo closeup
		this.bHighlight = false; 
	}

	public boolean hasHitContent(int hc) {
		return MapInfo.hasHitContent(hitContent,hc);
	}

	protected boolean upgradeHitContent(int type) {
		int hc = MapInfo.upgradeHitContent(hitContent,type);
		if (hitContent != hc) {
			hitContent = hc;
			return true;
		}
		return false;
	}

	public boolean equals(Object obj) {
		if (obj instanceof AbstractHitData) {
			AbstractHitData d = (AbstractHitData)obj;
			return d.project1 == project1 && d.project2 == project2
				&& d.group1 == group1 && d.group2 == group2; // mdb added 7/25/07 #134
		}
		return false;
	}

	public String toString() {
		return "[HitData: project1="+project1+" content1="+group1+
			   " project2="+project2+" content2="+group2+"]";
	}

	public int getHitContent() { return hitContent; }
	public int getProject1() { return project1; }
	public int getProject2() { return project2; }
	public boolean isHighlight() { return bHighlight; }
	
	// mdb added 7/25/07 #134
	public int getContent1() { return group1; }
	public int getContent2() { return group2; }
	public int getContig() { return group1; }
	public int getGroup() { return group2; }
	public int getContig1() { return group1; }
	public int getContig2() { return group2; }
	public int getGroup1() { return group1; }
	public int getGroup2() { return group2; }
	
	// mdb added 12/11/08 - empty here, to be defined by inheritor (for pseudo-pseudo closeup)
	public void getHitsInRange(List<AbstractHitData> hits, int start, int end, boolean swap) { }
	public HitData[] getHitData() { return null; }
	
	// mdb added 4/23/09 #161
	public void getMinMax(int[] minMax, int start, int end, boolean swap) { }
	
	// mdb added 12/31/08 for pseudo-pseudo closeup
	public int getMapType() { return mapType; }
	public boolean isSwapped() { return swap; }
	
	public static List[] splitOnHitContent(List<AbstractHitData> c) {
		List[] lists = new List[MapInfo.ALL_HITS+1];
		for (int i = 0; i < lists.length; i++) lists[i] = new LinkedList();

		for (AbstractHitData d : c) {
			try {
				lists[d.getHitContent()].add(d);
			} catch (ArrayIndexOutOfBoundsException e) {
				if (d.getHitContent() < 0) throw e;
				List[] tl = new List[d.getHitContent()+1];
				System.arraycopy(lists,0,tl,0,lists.length);
				for (int i = lists.length; i < tl.length; i++) tl[i] = new LinkedList();
				lists = tl;
				lists[d.getHitContent()].add(d);
			}
		}
		return lists;
	}

	public static List[] splitOnHitContentAndClear(List<AbstractHitData> c) {
		if (c instanceof RandomAccess) {
			List[] l = splitOnHitContent(c);
			c.clear();
			return l;
		}

		List[] lists = new List[MapInfo.ALL_HITS+1];
		for (int i = 0; i < lists.length; i++) lists[i] = new LinkedList();

		Iterator<?> iter = c.iterator();
		AbstractHitData d;
		while (iter.hasNext()) {
			d = (AbstractHitData)iter.next();
			try {
				lists[d.getHitContent()].add(d);
			} catch (ArrayIndexOutOfBoundsException e) {
				if (d.getHitContent() < 0) throw e;
				List[] tl = new List[d.getHitContent()+1];
				System.arraycopy(lists,0,tl,0,lists.length);
				for (int i = lists.length; i < tl.length; i++) tl[i] = new LinkedList();
				lists = tl;
				lists[d.getHitContent()].add(d);
			}
			iter.remove();
		}
		return lists;
	}
}
