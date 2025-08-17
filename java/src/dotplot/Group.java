package dotplot;

import java.util.HashMap;

/**************************************************
 * Represents a chromosome (group); contains its blocks
 */
public class Group implements Comparable<Group> {
	private int id, projID;
	private String name, fullname; // CAS571 add fullname
	private int cLenBP, sortOrder;
	private long offset;
	private float scaleFactor = 1;
	private HashMap<Group,Boolean> hasBlocks;	
	private boolean isVisible = true;  			

	// sortOrder is order of chrs based on sorted names, name is chrName, chromosome length
	public Group(int id, int sortOrder, String name, String fullname, int size, int projID) {
		this.id = id;
		this.sortOrder = sortOrder;
		this.name = (name == null) ? "" : name;
		this.fullname = (fullname == null) ? "" : fullname;
		this.cLenBP = size;
		this.projID = projID;
		hasBlocks = new HashMap<Group,Boolean>(); 
	}

	public int getProjID() {return projID;}
	public long getOffset() { return offset; }
	public int getID() { return id; }
	public int getSortOrder() { return sortOrder; }
	public String getName() { return name; }
	public String getFullName() { return fullname; }
	public int getGrpLenBP() { return cLenBP; }
	public float getScale() { return scaleFactor; }
	public int getEffectiveSize() { return (int)(cLenBP*scaleFactor); }
	public String toString() { return String.format("%d",id); } // CAS520 Integer.toString

	public boolean equals(Object obj) { 
		return obj instanceof Group && ((Group)obj).id == id;
	}

	public int compareTo(Group g) {
		return sortOrder - g.sortOrder;
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
	
	/********************************************************************/
	public static void setScaleFactors(Group[] groups, int minSize) {
		if (minSize <=0 ) return;
		
		for (int i = 0; i < groups.length; i++){
			if (groups[i].cLenBP < minSize){
				groups[i].scaleFactor = ((float)minSize)/((float)groups[i].cLenBP);	
			}
		}		
	}
	public static void setOffsets(Group[] groups) {
		if (groups != null && groups.length > 0) {
			groups[0].offset = 0;
			for (int i = 1; i < groups.length; i++) {
				groups[i].offset = groups[i-1].offset 
						+ (int)(((float)groups[i-1].cLenBP) * groups[i-1].scaleFactor);
			}
		}
	}
}

