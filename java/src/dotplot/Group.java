package dotplot;

import java.util.HashMap;

/**************************************************
 * Represents a chromosome (group); contains its blocks
 */
public class Group implements Comparable<Group> {
	private int id, projID;
	private String name, fullname; 
	private int cLenBP, sortOrder;
	private long offset;
	private HashMap<Group,Boolean> hasBlocks;	
	private boolean isVisible = true;  			

	// sortOrder is order of chrs based on sorted names, name is chrName, chromosome length
	protected Group(int id, int sortOrder, String name, String fullname, int size, int projID) {
		this.id = id;
		this.sortOrder = sortOrder;
		this.name = (name == null) ? "" : name;
		this.fullname = (fullname == null) ? "" : fullname;
		this.cLenBP = size;		// chromosome length
		this.projID = projID;
		hasBlocks = new HashMap<Group,Boolean>(); 
	}

	protected int getProjID() {return projID;}
	protected long getOffset() { return offset; }
	protected int getID() { return id; }
	protected int getSortOrder() { return sortOrder; }
	protected String getName() { return name; }
	protected String getFullName() { return fullname; }
	protected int getGrpLenBP() { return cLenBP; }
	
	protected void setHasBlocks(Group g) { 
		if (!hasBlocks.containsKey(g)) hasBlocks.put(g,true); 
	}
	protected boolean hasBlocks() { return hasBlocks.keySet().size() > 0; }
	protected boolean hasBlocks(Group[] groups) {
		for (Group g : groups) {
			if (hasBlocks.containsKey(g) && hasBlocks.get(g))
				return true;
		}
		return false;
	}
	protected void setVisible(boolean b) { isVisible = b; }
	protected boolean isVisible() { return isVisible; }
	
	public String toString() { return String.format("%d",id); } 

	public boolean equals(Object obj) { 
		return obj instanceof Group && ((Group)obj).id == id;
	}
	public int compareTo(Group g) {
		return sortOrder - g.sortOrder;
	}
	protected static void setOffsets(Group[] groups) {
		if (groups != null && groups.length > 0) {
			groups[0].offset = 0;
			for (int i = 1; i < groups.length; i++) {
				groups[i].offset = groups[i-1].offset + groups[i-1].cLenBP; 
			}
		}
	}
}

