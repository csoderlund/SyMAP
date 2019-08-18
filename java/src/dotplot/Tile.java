package dotplot;

import java.util.List;
import java.util.Vector;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Collections;

import util.Utilities;

public class Tile implements DotPlotConstants {
	private static final int EQUAL_ABLOCKS_PAD = 0;

	public static final int FULLY_LOADED = 2;
	public static final int SOME_LOADED  = 1;

	private Group group[]; // x and y
	private List iblocks;  // synteny blocks
	private AltBlocksRun altRuns[];
	private LoadObject loadObject;

	private Tile() {
		group      = new Group[2];
		iblocks    = new Vector(1,1);
		loadObject = new LoadObject();
		altRuns    = new AltBlocksRun[DotPlot.TOT_RUNS];
		for (int i = 0; i < altRuns.length; i++) altRuns[i] = null;
	}

	public Tile(Group grpX, Group grpY) {
		this();
		group[X] = grpX;
		group[Y] = grpY;
	}

	public String toString() {
		return "Tile "+group[Y].getName()+","+group[X].getName();
	}

	public boolean equals(Object obj) {
		if (obj instanceof Tile) {
			Tile b = (Tile)obj;
			return group[X].equals(b.group[X]) && group[Y].equals(b.group[Y]);
		}
		return false;
	}
	
	public int getProjID(int axis) {
		return group[axis].getProjID();
	}

	public AltBlocksRun getAltBlocksRun(int altNum) {
		return altRuns[altNum];
	}

	public void setAltBlocksRun(int altNum, AltBlocksRun altRun) {
		altRuns[altNum] = altRun;
		if (altRun == null || altRun.getABlocks().length == 0) {
			Hit[] hits = getHits();
			for (int i = 0; i < hits.length; i++) hits[i].setAltBlock(altNum,false);
		}
	}

	public ABlock[] getABlocks(int altNum) {
		if (altNum == 0) {
			synchronized (iblocks) {
				return (ABlock[])iblocks.toArray(new ABlock[0]);
			}
		}
		return altRuns[altNum] == null ? new ABlock[0] : altRuns[altNum].getABlocks();
	}

	public int getAltRID(int altNum) {
		return altRuns[altNum] == null ? -1 : altRuns[altNum].getRID();
	}

	public void setIBlocks(InnerBlock[] iblocks) {
		synchronized (iblocks) {
			this.iblocks.clear();
			if (iblocks != null)
				for (int i = 0; i < iblocks.length; ++i)
					this.iblocks.add(iblocks[i]);
			Collections.sort(this.iblocks);
		}
	}

	public void addIBlock(int number, int sX, int eX, int[] ctgX, int sY, int eY, int[] ctgY) {		
		synchronized (iblocks) {
			iblocks.add(new IBlock(number,sX,eX,group[X].getContigs(ctgX),sY,eY,group[Y].getContigs(ctgY)));
			Collections.sort(iblocks);
		}
	}

	public void clearIBlocks() {
		synchronized (iblocks) {
			iblocks.clear();
		}
	}

	public InnerBlock getIBlock(int number) {
		synchronized (iblocks) {
			return (IBlock)iblocks.get(number-1);
		}
	}
	
	public int getNumIBlocks() {
		synchronized (iblocks) {
			return iblocks.size();
		}
	}
	
	public boolean hasMatchingBlock(int altNum, ABlock ablock) {
		ABlock[] ablocks = getABlocks(altNum);
		for (int i = 0; i < ablocks.length; i++)
			if (ablocks[i].equalRectangles(ablock,EQUAL_ABLOCKS_PAD)) return true;
		return false;
	}

	public ABlock getABlock(int altNum, double xUnits, double yUnits) {
		if (altNum == 0) return (ABlock)getIBlock(xUnits,yUnits);
		ABlock[] ablocks = getABlocks(altNum);
		ABlock b = null;
		ABlock smallest = null;
		for (int i = 0; i < ablocks.length; i++) {
			b = ablocks[i];
			if (b.contains(xUnits,yUnits)) {
				if (smallest == null || Utilities.getSmallestBoundingArea(smallest,b) == b)
					smallest = b;	
			}
		}
		return smallest;
	}

	public InnerBlock getIBlock(double xUnits, double yUnits) {
		synchronized (iblocks) {
			IBlock b = null;
			IBlock smallest = null;
			Iterator iter = iblocks.iterator();
			while (iter.hasNext()) {
				b = (IBlock)iter.next();
				if (b.contains(xUnits,yUnits)) {
					if (smallest == null || Utilities.getSmallestBoundingArea(smallest,b) == b)
						smallest = b;
				}
			}
			return smallest;
		}	
	}

	public boolean setHits(Hit[] hits, int loadLevel) { return loadObject.setLoaded(hits,loadLevel); }
	public Hit[] getHits()     { return loadObject.getHits(); }
	public Hit[] getHitsCopy() { return loadObject.getHitsCopy(); }
	public int getNumHits()    { return loadObject.getNumHits(); }
	public Group getGroup(int axis) { return group[axis]; }
	public boolean isSomeLoaded()   { return loadObject.isLoaded(SOME_LOADED); }
	public boolean isLoaded() { return loadObject.isLoaded(FULLY_LOADED); }
	public boolean isLoaded(int loadLevel) { return loadObject.isLoaded(loadLevel); }
	
// mdb removed 6/29/07 #118
//	private void clearLoaded() {
//		loadObject.clearLoaded();
//	}

	public static Tile getTile(Tile[] tiles, Group grpX, Group grpY) {
		if (tiles != null)
			for (Tile t : tiles)
				if (t.group[X] == grpX && t.group[Y] == grpY) 
					return t;
		return null;
	}
	
	public static Tile getTile(List<Tile> tiles, Group grpX, Group grpY) {
		return getTile(tiles.toArray(new Tile[0]), grpX, grpY);
	}

	public static InnerBlock getIBlock(Tile[] blocks, Group grpX, Group grpY, int number) {
		Tile block = getTile(blocks,grpX,grpY);
		InnerBlock b = null;
		if (block != null) b = block.getIBlock(number);
		return b;
	}

	public static InnerBlock getIBlock(Tile[] tiles, Group grpX, Group grpY, double xUnits, double yUnits) {
		Tile t = getTile(tiles,grpX,grpY);
		InnerBlock b = null;
		if (t != null) b = t.getIBlock(xUnits,yUnits);
		return b;
	}

	public static ABlock getABlock(Tile[] tiles, Group grpX, Group grpY, int altNum, double xUnits, double yUnits) {
		if (altNum == 0) return (ABlock)getIBlock(tiles,grpX,grpY,xUnits,yUnits);
		Tile t = getTile(tiles,grpX,grpY);
		ABlock b = null;
		if (t != null) b = t.getABlock(altNum,xUnits,yUnits);
		return b;
	}

	private class IBlock extends ABlock implements Comparable<ABlock>, InnerBlock {
		private Contig[][] contigs = new Contig[2][];

		private IBlock(int num, int sX, int eX, Contig[] ctgX, int sY, int eY, Contig[] ctgY) {
			super(Tile.this,num,sX,eX,sY,eY);
			this.contigs[X] = ctgX;
			this.contigs[Y] = ctgY;	    
		}

		public int[] getContigNumbers(int axis) {
			int[] ret = new int[contigs[axis] == null ? 0 : contigs[axis].length];
			for (int j = 0; j < ret.length; ++j)
				ret[j] = contigs[axis][j].getNumber();
			return ret;
		}

		public Hit[] getHits(boolean includeRepetitive, boolean onlyBlock) {
			Hit[] h = getParent().getHits();
			List<Hit> hits = new ArrayList<Hit>(h.length / getNumIBlocks());
			for (int i = 0; i < h.length; i++) {
				if (contains(h[i]) && (!onlyBlock || h[i].isBlock()) && (includeRepetitive || !h[i].isRepetitive())) {
					hits.add(h[i]);
				}
			}

			return (Hit[])hits.toArray(new Hit[0]);
		}

		 public String toString() {
			 return new Integer(getNumber()).toString();
		 }
	}

	private static class LoadObject {
		private Hit hits[];
		private int loaded;

		public LoadObject() {
			loaded = 0;
			hits = null;
		}

		public synchronized boolean setLoaded(Hit[] hits, int loadLevel) {
			if (loaded < loadLevel) {
				this.hits = hits;
				loaded = loadLevel;
				return true;
			}
			return false;
		}

		public synchronized void clearLoaded() {
			hits = null;
			loaded = 0;
		}

		public synchronized boolean isLoaded(int loadLevel) {
			return loaded >= loadLevel;
		}

		public synchronized Hit[] getHits() {
			return hits == null ? new Hit[0] : hits;
		}

		public synchronized Hit[] getHitsCopy() {
			if (hits == null || hits.length == 0) return new Hit[0];
			Hit[] rHits = new Hit[hits.length];
			System.arraycopy(hits,0,rHits,0,hits.length);	    
			return rHits;
		}

		public synchronized int getNumHits() {
			return hits == null ? 0 : hits.length;
		}
	}
}
