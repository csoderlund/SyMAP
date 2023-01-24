package dotplot;

import java.util.Vector;

import symap.pool.ProjectProperties;

public class Project {
	private int id;
	private String displayName;
	private String name;
	private String grpPrefix;
	private String grpType;
	private Group[] groups;
	private int minGrpSize = 0;

	public Project(int id, ProjectProperties pp) {
		this(id, pp.getName(id), 
				 pp.getDisplayName(id), 
				 pp.getStringProperty(id,"grp_prefix",""), 
				 pp.getStringProperty(id,"grp_type",""),
				 pp.getIntProperty(id, "min_display_size_bp",0)
			);
	}

	private Project(int id, String name, String displayName, 
			String grpPrefix, String grpType, int minGrpSize) 
	{
		this.id          = id;
		this.name        = name;
		this.displayName = displayName == null ? "" : displayName;
		this.grpPrefix   = grpPrefix   == null ? "" : grpPrefix;
		this.grpType     = grpType     == null ? "" : grpType;
		this.minGrpSize  = minGrpSize;
	}
	
	public void setGroups(Group[] grps) {
		if (grps != null) {
			Group.setScaleFactors(grps, minGrpSize);
			Group.setOffsets(grps);
		}
		groups = grps;
	}

	public Group getGroupByOrder(int sortOrder) {
		return groups == null ? null : groups[sortOrder-1];
	}

	public Group getGroupByID(int id) {
		if (groups != null)
			for (Group g : groups)
				if (g.getID() == id) return g;
		return null;	
	}

	public Group getGroupByOffset(double offset) {
		if (groups != null)
			for (int i = groups.length-1; i >= 0; i--)
				if (groups[i].getOffset() < offset) return groups[i];
		return null;
	}
	
	public int getNumGroups() {
		return groups == null ? 0 : groups.length;
	}
	
	public int getNumVisibleGroups() {
		int count = 0;
		for (Group g : groups)
			if (g.isVisible()) count++;
		return count;
	}

	public int getID() { return id; }
	public String getGrpPrefix() { return grpPrefix; }
	public String getGrpType() { return grpType; }
	public String getDisplayName() { return displayName; }
	public String getName() { return name; }
	public String toString() { return displayName; } 

	public boolean equals(Object obj) {
		return obj instanceof Project && ((Project)obj).id == id;
	}
	/*****************************************************
	 * Called in data.initialize to set up tiles
	 */
	public static Tile[] createTiles(Project[] projects, Tile[] tiles, ProjectProperties pp) {
		Vector<Tile> out = new Vector<Tile>(tiles.length);
		Project pX = projects[0];
		for (int i = 1;  i < projects.length;  i++) {
			Project pY = projects[i];
			for (Group gX : pX.groups) {
				for (Group gY : pY.groups) {
					Tile t = Tile.getTile(tiles, gX, gY);
					if (t == null) t = new Tile(pX, pY, gX, gY);
					out.add(t);
				}
			}
		}
		return out.toArray(new Tile[0]);
	}
	
	public static Project getProject(Project[] projects, int startIndex, int id) {
		if (projects != null)
			for (int i = startIndex;  i < projects.length;  i++)
				if (projects[i].getID() == id)
					return projects[i];
		return null;
	}
}
