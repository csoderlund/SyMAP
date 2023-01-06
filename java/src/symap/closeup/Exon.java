package symap.closeup;

/*******************************************************************
 * Holds the exon coordinates for the gene drawn in GeneAlignment
 */
public class Exon implements Comparable<Exon> {
	private int start;
	private int end;

	public Exon(int start, int end) {
		if (start > end) {
			int temp = start;
			start = end;
			end = temp;
		}
		
		this.start = start;
		this.end   = end;
	}

	public int getStart() {
		return start;
	}

	public int getEnd() {
		return end;
	}

	public double getStart(int startBP, double bpPerPixel) {
		if (start <= startBP) return 0;
		return (start - startBP)/bpPerPixel;
	}

	public double getWidth(int startBP, int endBP, double bpPerPixel) {
		int x = (end > endBP) ? endBP : end;
		int y = (start < startBP) ? startBP : start;
		return Math.max((x-y)/bpPerPixel, 0);
	}

	public double getEnd(int startBP, int endBP, double bpPerPixel) {
		if (end < startBP) return 0;
		int x = (end > endBP) ? endBP : end;
		return (x - startBP)/bpPerPixel;
	}

	public int compareTo(Exon e) {
		return start - e.start;
	}

	public boolean equals(Object obj) {
		if (obj instanceof Exon) {
			Exon e = (Exon)obj;
			return e.start == start && e.end == end;
		}
		return false;
	}

	public String toString() {
		return "Exon "+start+"-"+end;
	}

	public Exon translate(int s, int e) {
		if (s <= e) return this;
		return new Exon(s-end+e,s-start+e);
	}
}
