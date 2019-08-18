package dotplot;

import java.util.Collection;
import java.util.List;
import java.util.Iterator;

public class Contig {
	private Group group;
	private int id;
	private int number;
	private int size;
	private int ccb;

	private Contig(int id) {
		this.id = id;
	}

	public Contig(Group grp, int id, int number, int ccb, int size) {
		this.group = grp;
		this.id = id;
		this.number = number;
		this.ccb = ccb;
		this.size = size;
	}

	public Group getGroup() {
		return group;
	}

	public boolean equals(Object obj) {
		return obj instanceof Contig && ((Contig)obj).id == id;
	}

	public String toString() {
		return new Integer(id).toString();
	}

	public int getID() { return id; }
	public int getNumber() { return number; }
	public int getSize() { return size; }
	public int getCCB() { return ccb; }
	public int getEnd() { return size+ccb; }

	public static Contig getContig(int cid, List<Contig> contigs) {
		if (contigs == null) return null;
		int i = contigs.indexOf(new Contig(cid));
		return i < 0 ? null : (Contig)contigs.get(i);
	}

	public static int[] getContigNumbers(Collection<Contig> contigs) {
		if (contigs == null) return new int[0];
		int[] ret = new int[contigs.size()];
		Iterator<Contig> iter = contigs.iterator();
		for (int i = 0; i < ret.length; ++i)
			ret[i] = (iter.next()).number;
		return ret;
	}
}
