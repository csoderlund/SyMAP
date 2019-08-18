package finder;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Collections;
import java.util.Comparator;
import java.awt.Rectangle;
import dotplot.ABlock;
import dotplot.Tile;
import dotplot.Hit;

public class FBlock extends ABlock {
	private int rid;
	private double score;
	private List<AltHit> altHits;
	private Rectangle altRect;

	public FBlock(Tile parent, int rid, int blockNum, 
			int sX, int eX, int[] contigsX, int sY, int eY, 
			int[] contigsY, double score) 
	{
		this(parent,rid,blockNum,sX,eX,sY,eY,score);
	}

	public FBlock(Tile parent, int rid, int blockNum, int sX, int eX, int sY, int eY, double score) {
		super(parent,blockNum,sX,eX,sY,eY);
		this.rid = rid;
		this.score = score;

		altHits = new ArrayList<AltHit>();
	}

	public void setBlockHits(int altNum) {
		for (Hit hit : altHits)
			hit.setAltBlock(altNum,true);
	}

	public AltHit getFirstAltHit() {
		return altHits.isEmpty() ? null : (AltHit)altHits.get(0);
	}

	public AltHit getLastAltHit() {
		return altHits.isEmpty() ? null : (AltHit)altHits.get(altHits.size()-1);
	}

	public double altDiagonal() {
		return util.Utilities.diagonal(altRect);
	}	

	public double altDistance(FBlock fblock) {
		return util.Utilities.distance(altRect,fblock.altRect);
	}

	public void setAltYFlipped(int maxY) {
		altRect = null;
		for (AltHit h : altHits) {
			h.setAltYFlipped(maxY);
			if (altRect == null)
				altRect = new Rectangle(h.getAltX(),h.getAltY(),0,0);
			else
				altRect.add(h.getAltX(),h.getAltY());
		}
	}

	public void resetAltLocation() {
		altRect = null;
		for (AltHit h : altHits) {
			if (altRect == null)
				altRect = new Rectangle(h.getAltX(),h.getAltY(),0,0);
			else
				altRect.add(h.getAltX(),h.getAltY());
		}
	}

	public void addHit(Hit hit) {
		AltHit h = (AltHit)hit;
		int i;
		for (i = altHits.size()-1; i >= 0; --i) {
			if (h.compareTo(altHits.get(i)) >= 0) {
				altHits.add(i+1,h);
				break;
			}
		}
		if (i < 0) altHits.add(0,h);

		if (altRect == null) {
			altRect = new Rectangle();
			altRect.setLocation(h.getAltX(),h.getAltY());
		}
		else {
			altRect.add(h.getAltX(),h.getAltY());
		}

		super.addHit(hit);
	}

	public AltHit[] getAltHits() {
		return (AltHit[])altHits.toArray(new AltHit[0]);
	}

	public Hit[] getHits(boolean includeRepetitive, boolean onlyBlock) {
		if (onlyBlock) return (Hit[])altHits.toArray(new Hit[0]);

		Hit[]   h = (Hit[])getParent().getHits();
		List<Hit> hits = new ArrayList<Hit>(h.length / 3);

		for (int i = 0; i < h.length; i++) {
			if (contains(h[i]) && (includeRepetitive || !h[i].isRepetitive()))
				hits.add(h[i]);
		}

		return (Hit[])hits.toArray(new Hit[0]);
	}

	public int getAltStart(int axis) {
		if (altRect == null) return getStart(axis);
		return axis == X ? altRect.x : altRect.y;
	}

	public int getAltEnd(int axis) {
		if (altRect == null) return getEnd(axis);
		return axis == X ? altRect.x+altRect.width : altRect.y+altRect.height;
	}

	public void merge(ABlock block) {
		if (block instanceof FBlock) {
			FBlock b = (FBlock)block;
			score += b.score;
			altHits.addAll(b.altHits);
			Collections.sort(altHits);
			if (altRect == null && b.altRect != null) {
				altRect = new Rectangle(b.altRect);
			}
			else if (b.altRect != null) {
				altRect.add(b.altRect);
			}
		}
		super.merge(block);
	}

	public int getNumAltHits() {
		return altHits.size();
	}

	public double averageSeparation(int axis) {
		if (axis == X) return altRect.width  / (double)altHits.size();
		else           return altRect.height / (double)altHits.size();
	}

	public double pearson_exact() {
		return AltHit.pearson_exact(altHits);
	}

	public double pearson() {
		return AltHit.pearson(altHits);
	}

	public double getScore() {
		return score;
	}

	public int getRunID() {
		return rid;
	}

	public String toString() {
		return "[Block #"+getNumber()+" ("+getStart(X)+","+getStart(Y)+") - ("+getEnd(X)+","+getEnd(Y)+
				") hits = "+altHits.size()+" score = "+score+"]";
	}

	public int[] getContigNumbers(int axis) {
		return null;
	}

	public int getAltX() {
		return altRect == null ? getX() : altRect.x;
	}

	public int getAltY() {
		return altRect == null ? getY() : altRect.y;
	}

	public int getAltHeight() {
		return altRect == null ? getHeight() : altRect.height;
	}

	public int getAltWidth() {
		return altRect == null ? getWidth() : altRect.width;
	}

	public Object clone() {
		FBlock b = (FBlock)super.clone();
		b.altRect = altRect == null ? null : (Rectangle)altRect.clone();
		b.altHits = (List<AltHit>)((ArrayList<AltHit>)altHits).clone();
		return b;
	}

	public void clearAltLocation() {
		altRect = null;
	}

	protected void clear() {
		altHits.clear();
		altRect = null;
		super.clear();
	}

	public static FBlock getFBlock(Tile parent, int rid, int num, double score, List indexs, AltHit[] hits) {
		//System.out.println((indexs == null ? "0" : new Integer(indexs.size()).toString())+" hits in block");
		FBlock block = new FBlock(parent,rid,num,0,0,0,0,score);
		block.setPosition(indexs,hits);
		return block;
	}

	public static FBlock getFBlock(List<Number> indexs, FBlock[] blocks) {
		if (indexs.isEmpty()) return null;
		Iterator<Number> iter = indexs.iterator();
		FBlock b = blocks[iter.next().intValue()];
		while (iter.hasNext())
			b.merge(blocks[iter.next().intValue()]);

		return b;
	}

	public static Comparator<FBlock> altLocationComparator() {
		return new AltLocComparator();
	}

	private static class AltLocComparator implements Comparator<FBlock> {
		private AltLocComparator() { }

		public int compare(FBlock a1, FBlock a2) {
			if (a1.getAltX() != a2.getAltX()) return a1.getAltX() - a2.getAltX();
			return a1.getAltY() - a2.getAltY();
		}

		public boolean equals(Object obj) {
			return obj instanceof AltLocComparator;
		}
	}
}
