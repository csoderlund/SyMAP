package arranger;

import java.util.*;
import dotplot.*;

public class SubBlock extends ABlock implements InnerBlock {

	private ABlock ib;
	private List<Hit> hits;
	private boolean orient;

	public SubBlock(int n, ABlock iblock, boolean orientation) {
		super();
		setNumber(n);
		this.ib = iblock;
		this.orient = orientation;
		hits = new ArrayList<Hit>();
	}

	public void setBlockHits() {
		for (Hit h : hits)
			h.setAltBlock(DotPlot.SUB_CHAIN_RUN, true);
	}

	public boolean getOrientation() {
		return orient;
	}

	public void setOrientation(boolean orientation) {
		this.orient = orientation;
	}

	public String toString() {
		return "SubBlock " + getName() + (orient ? " + " : " - ") + getBounds()
				+ " [Hits: " + hits.size() + "]";
	}

	public String getSummary() {
		java.awt.Rectangle r = getBounds();
		return "SubBlock " + getName() + (orient ? " + " : " - ") + "[(" + r.x
				+ "," + r.y + ") - (" + (r.x + r.width) + ","
				+ (r.y + r.height) + ")], " + hits.size() + " hits.\n";
	}

	public int[] getContigNumbers(int axis) {
		return new int[0];
	}

	public void addHit(Hit hit) {
		hits.add(hit);
		super.addHit(hit);
	}

	public String getName() {
		return ib.getName() + "." + getNumber();
	}

	public Tile getParent() {
		return ib.getParent();
	}

	public Group getGroup(int axis) {
		return ib.getGroup(axis);
	}

	public Hit[] getHits(boolean includeRepetitive, boolean onlyBlock) {
// mdb unused 2/20/08
//		Hit h;
//		List rhits = new ArrayList(hits.size());
//		for (Iterator iter = hits.iterator(); iter.hasNext(); ) {
//		    h = (Hit)iter.next();
//		    if ((!onlyBlock || h.isBlock()) && (includeRepetitive || h.isRepetitive()))
//			rhits.add(h);
//		}
		return (Hit[])hits.toArray(new Hit[0]);
    }
}
