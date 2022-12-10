package dotplot;

import java.util.HashMap;

public class Group implements Comparable<Group> {
	private int id;
	private int sortOrder;
	private String name;
	private int size;
	private long offset;
	private float scaleFactor = 1;
	private int projID;
	private HashMap<Group,Boolean> hasBlocks;	
	private boolean isVisible = true;  			

	public Group(int id, int sortOrder, String name, int size, int projID) {
		this.id = id;
		this.sortOrder = sortOrder;
		this.name = name == null ? "" : name;
		this.size = size;
		this.projID = projID;
		hasBlocks = new HashMap<Group,Boolean>(); 
	}

	public int getProjID() {
		return projID;
	}

	public long getOffset() { return offset; }
	public int getID() { return id; }
	public int getSortOrder() { return sortOrder; }
	public String getName() { return name; }
	public int getSize() { return size; }
	public float getScaleFactor() { return scaleFactor; }
	public int getEffectiveSize() { return (int)(size*scaleFactor); }
	public String toString() { return String.format("%d",id); } // CAS520 Integer.toString

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
			groups[0].offset = 0;
			for (int i = 1; i < groups.length; i++)
			{
				groups[i].offset = groups[i-1].offset + (int)(((float)groups[i-1].size)*groups[i-1].scaleFactor);
			}
		}
	}
	
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
	
	public void setVisible(boolean b) { isVisible = b; }
	public boolean isVisible() { return isVisible; }
}

