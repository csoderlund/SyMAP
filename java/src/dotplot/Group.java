package dotplot;

import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.Iterator;

public class Group implements Comparable<Group> {
	private int id;
	private int sortOrder;
	private String name;
	private int size;
	private long offset;
	private float scaleFactor = 1;
	private List<Contig> contigs = new LinkedList<Contig>();
	private int projID;
	private HashMap<Group,Boolean> hasBlocks;	// mdb added 12/14/09 #203
	private boolean isVisible = true;  			// mdb added 12/21/09 #207

	public Group(int id, int sortOrder, String name, int size, int projID) {
		this.id = id;
		this.sortOrder = sortOrder;
		this.name = name == null ? "" : name;
		this.size = size;
		this.projID = projID;
		hasBlocks = new HashMap<Group,Boolean>(); // mdb added 12/14/09 #203
	}

	public int getProjID() {
		return projID;
	}

	public void addContig(Contig contig) {
		contigs.add(contig);
	}

	public Contig[] getContigs() { 
		return (Contig[])contigs.toArray(new Contig[0]);
	}

	public List<Contig> getContigList() {
		return contigs;
	}

	public Contig[] getContigs(int[] contigIDs) {
		if (contigIDs == null || contigIDs.length == 0) return new Contig[0];
		Contig ret[] = new Contig[contigIDs.length];
		for (int i = 0; i < contigIDs.length; ++i)
			ret[i] = Contig.getContig(contigIDs[i],contigs);
		return ret;
	}

	public List<Contig> getContigs(int start, int end) {
		List<Contig> list = new ArrayList<Contig>(contigs.size());
		int ctgStart, ctgEnd;
		for (Contig c : contigs) {
			ctgStart = c.getCCB();
			ctgEnd   = c.getEnd();
			if ((ctgStart <= start && ctgEnd >= start) ||
					(ctgStart <= end && ctgEnd >= end) ||
					(ctgStart >= start && ctgEnd <= end)) 
			{
				list.add(c);
			}
		}
		return list;
	}

	public Contig getContig(int contigNumber) {
		Iterator<Contig> iter = contigs.iterator();
		Contig c;
		while (iter.hasNext()) {
			c = iter.next();
			if (c.getNumber() == contigNumber) return c;
		}
		return null;
	}

	public int getContigEnd(int contigNumber) {
		Contig c = getContig(contigNumber);
		if (c == null) return 0; 
		return c.getCCB()+c.getSize();
	}

	public int getContigPosition(int contigNumber) {
		Contig c = getContig(contigNumber);
		if (c == null) return 0; 
		return c.getCCB();
	}
	
	public long getOffset() { return offset; }
	public int getID() { return id; }
	public int getSortOrder() { return sortOrder; }
	public String getName() { return name; }
	public int getSize() { return size; }
	public float getScaleFactor() { return scaleFactor; }
	public int getEffectiveSize() { return (int)(size*scaleFactor); }
	public String toString() { return new Integer(id).toString(); }

	public boolean equals(Object obj) { 
		return obj instanceof Group && ((Group)obj).id == id;
	}

	public int compareTo(Group g) {
		return sortOrder - g.sortOrder;
	}
	public static void setScaleFactors(Group[] groups, int minSize)
	{
		if (minSize > 0)
		{
			for (int i = 0; i < groups.length; i++)
			{
				if (groups[i].size < minSize)
				{
					groups[i].scaleFactor = ((float)minSize)/((float)groups[i].size);	
				}
	
			}
		}			
	}
	public static void setOffsets(Group[] groups) {
		if (groups != null && groups.length > 0) {
			//Arrays.sort(groups); // mdb removed 12/31/09 #205
			groups[0].offset = 0;
			for (int i = 1; i < groups.length; i++)
			{
				groups[i].offset = groups[i-1].offset + (int)(((float)groups[i-1].size)*groups[i-1].scaleFactor);
			}
		}
	}
	
	// mdb added 12/14/09 #203
	public void setHasBlocks(Group g) { 
		if (!hasBlocks.containsKey(g)) hasBlocks.put(g,true); 
	}
	public boolean hasBlocks() { return hasBlocks.keySet().size() > 0; }
	public boolean hasBlocks(Group[] groups) {
		for (Group g : groups) {
			if (hasBlocks.containsKey(g) && hasBlocks.get(g))
				return true;
		}
		return false;
	}
	
	// mdb added 12/21/09 #207
	public void setVisible(boolean b) { isVisible = b; }
	public boolean isVisible() { return isVisible; }
}

