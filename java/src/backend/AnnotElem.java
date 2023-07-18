package backend;

import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

/*****************************************
 * This is part of finding Cluster Hits (not part of loading annotation from AnnotLoadMain)
 * Created in Group, accessed in AnchorMain as bestOlapAnno
 */
public class AnnotElem implements Comparable <AnnotElem> {// CAS500 added <type>; comparable so can be used in TreeMap
	public int start, end, len, idx, genenum;
	public boolean isRev;
	public GeneType mGT = GeneType.NA; // {Gene, NonGene, NA}
	public int mID;
	
	private static int NEXTID = 0;
	
	AnnotElem( int idx, int start, int end, boolean isRev, int genenum, GeneType gt){
		this.idx   = idx;
		this.start = start;
		this.end   = end;
		len = Math.abs(end-start)+1;
		this.isRev = isRev;
		this.genenum =genenum;
		this.mGT = gt;
		mID = NEXTID;
		NEXTID++;
	}
	
	public String getInfo() {
		return String.format("%5d idx=%-5d gn=%5d (%,10d %,10d l=%,5d %5s)  %s", mID, idx, genenum, start, end, (end-start+1), isRev, mGT.toString());
	}
	
	public int getLength() { return len; }
	
	public boolean isGene(){
		return (mGT == GeneType.Gene);	
	}
	public int compareTo(AnnotElem _a2) { // needed for HashSet in AnchorMain
		AnnotElem a2 = (AnnotElem)_a2;
		if (mID < a2.mID) return -1;
		if (mID > a2.mID) return 1;
		return 0;
	}
	public static void sortByStart(Vector<AnnotElem> ae) { 
		Collections.sort(ae, 
			new Comparator<AnnotElem>() {
				public int compare(AnnotElem h1, AnnotElem h2) {
					return h1.start - h2.start;
				}
			}
		);
	}
}