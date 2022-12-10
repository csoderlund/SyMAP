package symap.mapper;

import java.util.List;
import java.awt.event.MouseEvent;

/**
 * Class AbstractHitData
 * CAS521 removed unused splitOnHitContentAndClear, hasHitContent, upgradeHitContent
 */
public abstract class AbstractHitData {
	private int hitContent;
	private int project1, project2; // project IDs
	private int group1, group2; // content IDs: contig or chromosome 
	private int mapType; 
	private boolean swap; 
	protected boolean bHighlight;
	
	public AbstractHitData(int project1, int content1, int project2, 
			int content2, int mapType, 
			boolean swap) 
	{
		this.project1 = project1;
		this.project2 = project2;
		this.group1 = content1; 
		this.group2 = content2; 
		this.mapType  = mapType;
		this.hitContent = MapInfo.NOT_SET;
		this.swap = swap; 
		this.bHighlight = false; 
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
				&& d.group1 == group1 && d.group2 == group2; 
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
	
	public int getContent1() { return group1; }
	public int getContent2() { return group2; }
	public int getGroup() { return group2; }
	public int getGroup1() { return group1; }
	public int getGroup2() { return group2; }
	
	// empty here, to be defined by inheritor (for pseudo-pseudo closeup)
	public void getHitsInRange(List<AbstractHitData> hits, int start, int end, boolean swap) { }
	public HitData[] getHitData() { return null; }
	public boolean doPopupDesc(MouseEvent e) {return false;}
	
	public void getMinMax(int[] minMax, int start, int end, boolean swap) { }
	
	public int getMapType() { return mapType; }
	public boolean isSwapped() { return swap; }
}
