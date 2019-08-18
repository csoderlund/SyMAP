package arranger;

import java.util.*;
import util.PropertiesReader;
import dotplot.*;
import arranger.algo.Reversal;
import util.Coordinate2D;

public class SyArranger extends Arranger implements DotPlotConstants {
	private static final int DEFAULT_NEEDED;   /* needed hits */
	private static final int MAX_DIFF;         /* max hits able to jump */
	private static final int MAX_COMPACT_DIFF; /* max number to jump to merge sub-chains with the same orientation */
	private static final int BASE_AXIS;        /* e.g. if y is duplicated, better results if use y */
	private static final boolean ADD_ALL;      /* add left over anchors as sub-chains */
	static {
		PropertiesReader props = new PropertiesReader(SyArranger.class.getResource("/properties/syarranger.properties"));
		DEFAULT_NEEDED   = props.getInt("defaultNeeded");
		MAX_DIFF         = props.getInt("maxDiff");
		MAX_COMPACT_DIFF = props.getInt("maxCompactDiff");
		BASE_AXIS        = props.getInt("baseAxis");
		ADD_ALL          = props.getBoolean("addAll");
	}

	public SyArranger() {
		super(new PropertiesReader(SyArranger.class.getResource("/properties/syarranger.properties")));
	}

	protected SubBlock[] findSubBlocks(ABlock block) {
		SubBlock[] sbs = doFind(block,BASE_AXIS);

		return sbs;
	}

	protected SubBlock[] doFind(ABlock block, int axis) {

		Hit[] mhits = block.getHits(false,true);      /* get and sort the hits along X axis     */
		Arrays.sort(mhits,Hit.getComparator(axis));
		mhits = removeDuplicates(mhits);              /* remove any duplicates */

		Hit[] shits = (Hit[])mhits.clone();           /* same hits in new array sorted by Y axis */
		Comparator<Coordinate2D> comp = Hit.getComparator(axis == X ? Y : X);
		Arrays.sort(shits,comp);

		int bp[] = new int[mhits.length];             /* create array where the index goes into shits and the value */
		for (int i = 0; i < mhits.length; i++)        /* is the index into mhits                                    */
			bp[i] = Arrays.binarySearch(shits,mhits[i],comp);

		BPRange[] ranges = getRanges(block,bp,mhits,DEFAULT_NEEDED,MAX_DIFF);
		ranges = compactRanges(ranges,bp,MAX_COMPACT_DIFF);
		if (ADD_ALL)
			ranges = addSubRanges(ranges,block,bp,mhits);

		//for (int i = 0; i < ranges.length; i++) System.out.println(ranges[i]);

		//printInts(ranges);

		/*
	  int nv[] = getIntArray(ranges);
	  GR gr;
	  Reversal rev[] = new Reversal[0];
	  try {
	  gr = new GR(new Permutation(nv));
	  rev = gr.run();
	  } catch (Exception e) {
	  e.printStackTrace();
	  }

	  printReversals(rev);
		 */

		SubBlock[] sbs = createSubBlocks(block,ranges,mhits);

		return sbs;
	}

	protected void printReversals(Reversal[] rev) {
		System.out.println("Reversals ->");
		for (int i = 0; i < rev.length; i++) {
			System.out.println("Reversal "+(i+1)+" "+rev[i]);
		}
		System.out.println("<- Reversals");
	}

	protected SubBlock[] createSubBlocks(ABlock ib, BPRange[] ranges, Hit[] hits) {
		SubBlock[] sbs = new SubBlock[ranges.length];
		for (int i = 0; i < sbs.length; i++) {
			sbs[i] = new SubBlock(i+1,ib,ranges[i].orientation());
			for (Iterator<Integer> iter = ranges[i].getIndxs().iterator(); iter.hasNext(); )
				sbs[i].addHit(hits[(iter.next()).intValue()]);
		}
		return sbs;
	}

	protected int[] getIntArray(BPRange[] ranges) {
		int ret[] = new int[ranges.length];
		for (int i = 0; i < ret.length; i++)
			ret[i] = ranges[i].value();
		return ret;
	}

	protected void printInts(BPRange[] ranges) {
		System.out.print("[");
		for (int i = 0; i < ranges.length; i++)
			System.out.print(new Integer(ranges[i].value()).toString()+" ");
		System.out.println("]\n");
	}

	protected Hit[] removeDuplicates(Hit[] hits) {
		if (hits.length == 0) return hits;
		Hit[] nHits = new Hit[hits.length];
		int n = 0;
		nHits[0] = hits[0];
		for (int i = 1; i < hits.length; i++) {
			if (!hits[i].equals(nHits[n])) {
				nHits[++n] = hits[i];
				if (n == i) i++;
			}
			//else System.out.println("Removing "+hits[i]+" because equal to "+nHits[n]);
		}
		if (++n != hits.length) {
			Hit[] th = new Hit[n];
			System.arraycopy(nHits,0,th,0,n);
			nHits = th;
		}
		return nHits;
	}    

	protected BPRange[] getRanges(ABlock ib, int[] bp, Hit[] hits, int needed, int maxDiff) {
		List<BPRange> ranges = new ArrayList<BPRange>();
		BPRange range = null;

		for (int i = 0; i < bp.length; i++) {
			if (range != null && !range.add(i,bp[i],maxDiff)) {
				if (range.complete(hits))
					ranges.add(range);
				range = null;
			}
			if (range == null) {
				range = new BPRange(needed);
				range.begin(i,bp[i]);
			}
		}
		if (range != null && range.complete(hits))
			ranges.add(range);

		return setValues((BPRange[])ranges.toArray(new BPRange[0]));
	}

	protected BPRange[] compactRanges(BPRange[] ranges, int bp[], int maxDiff) {
		if (ranges.length == 0) return ranges;

		List<BPRange> list = new ArrayList<BPRange>(ranges.length);
		BPRange range = ranges[0];

		if (range.startInd <= maxDiff)
			range.expand(0,bp[0]);

		for (int i = 1; i < ranges.length; i++) {
			if (!range.add(ranges[i],maxDiff)) {
				list.add(range);
				range = ranges[i];
			}
		}

		if (bp.length - 1 - range.endInd <= maxDiff) 
			range.expand(bp.length-1,bp[bp.length-1]);

		list.add(range);

		return (BPRange[])list.toArray(new BPRange[0]);	
	}

	protected BPRange[] addSubRanges(BPRange[] ranges, ABlock ib, int[] bp, Hit[] hits) {
		List<BPRange> list = new ArrayList<BPRange>();
		BPRange range = null;
		int next = 0;

		for (int j = 0; j < ranges.length; j++) {
			if (ranges[j].startInd > next) {
				range = new BPRange(next,bp[next],ranges[j].startInd - 1,bp[ranges[j].startInd - 1]);
				range.complete(hits);
				list.add(range);
			}
			list.add(ranges[j]);
			next = ranges[j].endInd + 1;
		}

		if (next < bp.length) {
			range = new BPRange(next,bp[next],bp.length-1,bp[bp.length-1]);
			range.complete(hits);
			list.add(range); 
		}

		return setValues((BPRange[])list.toArray(new BPRange[0]));
	}

	protected BPRange[] setValues(BPRange[] ranges) {
		BPRange[] sranges = (BPRange[])ranges.clone();
		Arrays.sort(sranges); /* should do different sort? */

		for (int i = 0; i < ranges.length; i++) /* regular compareTo doesn't do anything with value, so safe */
			ranges[i].setValue(Arrays.binarySearch(sranges,ranges[i])+1);

		return ranges;
	}

	protected static class BPRange implements Comparable<BPRange> {
		private List<Integer> indxs;

		private int needed;

		private int value = 0;

		private int startInd, startValue;
		private int endInd,   endValue;
		//private boolean orientation; // mdb removed 6/29/07 #118

		public BPRange(int needed) {
			this.needed = needed;

			indxs = new ArrayList<Integer>();

			startInd = -1;
			endInd   = -1;
		}

		public BPRange(int startInd, int startValue, int endInd, int endValue) {
			this(0);
			begin(startInd,startValue);
			expand(endInd,endValue);
		}

		public int area(Hit[] hits) {
			return Math.abs(hits[endInd].getX() - hits[startInd].getX()) * Math.abs(hits[endInd].getY() - hits[startInd].getY());
		}

		public void begin(int ind, int value) {
			startInd   = ind;
			startValue = value;
			endInd     = ind;
			endValue   = value;
			indxs.clear();
			indxs.add(new Integer(ind));
		}

		public void expand(int ind, int value) {
			if (ind < startInd) {
				startInd   = ind;
				startValue = value;
				indxs.add(0,new Integer(ind));
			}
			else if (ind > endInd) {
				endInd   = ind;
				endValue = value;
				indxs.add(new Integer(ind));
			}
		}

		public boolean add(int ind, int value, int maxDiff) {
			if (diff(ind,value) > maxDiff) {
				if (isComplete())
					return false;
				begin(ind,value);
				return true;
			}

			if (ind < startInd) {
				startInd   = ind;
				startValue = value;
				indxs.add(0,new Integer(ind));
			}
			else {
				endInd   = ind;
				endValue = value;
				indxs.add(new Integer(ind));
			}

			return true;
		}

		public boolean add(BPRange range, int maxDiff) {
			if (orientation() != range.orientation() || diff(range) > maxDiff) 
				return false;
			indxs.addAll(range.indxs);
			if (startInd <  range.startInd) {
				endInd   = range.endInd;
				endValue = range.endValue;
			}
			else {
				startInd   = range.startInd;
				startValue = range.startValue;
			}
			return true;
		}

		public boolean complete(Hit[] hits) {
			if (isComplete()) {
				//orientation = endValue > startValue;//getPearsonOrientation(hits); // mdb removed 6/29/07 #118
				return true;
			}
			return false;
		}

		public boolean orientation() {
			//return orientation;
			return endValue > startValue;
		}

		public void setValue(int value) {
			if (orientation()) this.value = value;
			else               this.value = -value;
		}

		public int value() {
			return value;
		}

		public List<Integer> getIndxs() {
			return indxs;//getIntList(startInd,endInd);
		}

		public String toString() {
			return "[BPRange "+(orientation() ? "+" : "-")+
			" ("+startInd+","+endInd+") values ["+startValue+","+endValue+"] avg="+avg()+" value = "+value+"]";
		}

		public int compareTo(BPRange r) {
			double a1, a2;
			a1 = avg();
			a2 = r.avg();
			if      (a1 < a2) return -1;
			else if (a1 > a2) return 1;
			else              return startInd - r.startInd;
		}

		public boolean equals(Object obj) {
			if (obj instanceof BPRange) {
				BPRange r = (BPRange)obj;
				return startInd == r.startInd && avg() == r.avg();
			}
			return false;
		}

// mdb removed 6/29/07 #118
//		private boolean getPearsonOrientation(Hit[] hits) {
//			return Hit.pearson(getIndxs(),hits) >= 0;
//		}

		private int diff(int ind, int value) {
			if (startInd < ind)
				return Math.max(ind - endInd,Math.abs(value - endValue));
			else
				return Math.max(startInd - ind,Math.abs(startValue - value));	    
		}

		private int diff(BPRange range) {
			if (startInd < range.startInd)
				return Math.max(range.startInd - endInd,Math.abs(range.startValue - endValue));
			else
				return Math.max(startInd - range.endInd,Math.abs(startValue - range.endValue));
		}

		private double avg() {
			return (startValue + endValue) / 2.0;
		}

		public boolean isComplete(int needed) {
			return indxs.size() >= needed;
		}

		public boolean isComplete() {
			return indxs.size() >= needed; /*endInd - startInd + 1 >= needed;*/
		}

// mdb removed 6/29/07 #118
//		private static List getIntList(int sInd, int eInd) {
//			List list = new ArrayList(eInd - sInd + 1);
//			for (int i = sInd; i <= eInd; i++)
//				list.add(new Integer(i));
//			return list;
//		}
	}

}
