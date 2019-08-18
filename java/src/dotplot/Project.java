package dotplot;

import java.util.List;
import java.util.LinkedList;
import java.util.Vector;

import java.awt.Color;

import symap.pool.ProjectProperties;

public class Project {
	private int id;
	private String displayName;
	private String name;
	private String grpPrefix;
	private String grpType;
	private boolean isPseudo;
	private Group[] groups;
	private Color color = null; 
	private int scale = 1; 
	private int minGrpSize = 0;

	
	public Project(int id, ProjectProperties pp) {
		this(id, pp.getName(id), 
				 pp.getDisplayName(id), 
				 pp.getType(id) == "pseudo",
				 pp.getStringProperty(id,"grp_prefix",""), 
				 pp.getStringProperty(id,"grp_type",""),
				 pp.getIntProperty(id,"cbsize",1),
				 pp.getIntProperty(id, "min_display_size_bp",0)
			);
	}

	public Project(int id, String name, String displayName, boolean isPseudo, 
			String grpPrefix, String grpType,
			int scale, int minGrpSize) 
	{
		this.id          = id;
		this.name        = name;
		this.displayName = displayName == null ? "" : displayName;
		this.grpPrefix   = grpPrefix   == null ? "" : grpPrefix;
		this.grpType     = grpType     == null ? "" : grpType;
		this.isPseudo    = isPseudo;
		this.scale       = scale; 
		this.minGrpSize  = minGrpSize;
	}
	
	public Color getColor() { return color; }
	public void setColor(Color c) { this.color = c; }
	public int getScale() { return scale; }

	public void setGroups(Group[] grps) {
		if (grps != null) {
			Group.setScaleFactors(grps, minGrpSize);
			Group.setOffsets(grps);
		}
		else if (groups != null) {
			//throw new RuntimeException("Groups are already set!");
			for (Group g : groups) g.getContigList().clear();
		}
		groups = grps;
	}

	public boolean isGroupsSet() {
		return groups != null;
	}
	
	public Group[] getGroups() {
		return groups == null ? new Group[0] : groups;
	}

	public Group getGroup(String name) {
		if (groups != null)
			for (Group g : groups)
				if (g.getName().equals(name)) return g;
		return null;
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
	
	public void addContig(int gid, int cid, int number, int ccb, int size) {
		Group g = getGroupByID(gid);
		g.addContig(new Contig(g,cid,number,ccb,size));
	}

	public Contig[] getContigs() {
		if (getNumGroups() <= 0) return new Contig[0];
		List<Contig> l = new LinkedList<Contig>();
		for (int i = 0; i < groups.length; ++i)
			l.addAll(groups[i].getContigList());
		return (Contig[])l.toArray(new Contig[l.size()]);
	}

	public int getNumGroups() {
		return groups == null ? 0 : groups.length;
	}
	
	// mdb added 1/14/10 #203
	public int getNumVisibleGroups() {
		int count = 0;
		for (Group g : groups)
			if (g.isVisible()) count++;
		return count;
	}

// mdb removed 12/15/09 #203		
//	public long getMaxGroup() {
//		if (groups == null || groups.length == 0) return 0;
//		return groups[groups.length-1].getOffset()+groups[groups.length-1].getSize();
//	}

	public int getID() { return id; }
	public String getGrpPrefix() { return grpPrefix; }
	public String getGrpType() { return grpType; }
	public String getDisplayName() { return displayName; }
	public String getName() { return name; }
	public boolean isPseudo() { return isPseudo; }
	public boolean isFPC() { return !isPseudo; }
	public String toString() { return displayName; /*new Integer(id).toString();*/ } // mdb changed 2/1/10 #210

	public boolean equals(Object obj) {
		return obj instanceof Project && ((Project)obj).id == id;
	}

	public static Tile[] getGroupPairs(Project[] projects, Tile[] tiles, ProjectProperties pp) {
		Vector<Tile> out = new Vector<Tile>(tiles.length);
		
		for (int i = 1;  i < projects.length;  i++) {
			Project pY = projects[i];
			for (Group gX : projects[0].groups) {
				for (Group gY : pY.groups) {
					// mdb rewritten 1/15/10 #207 - reuse existing tiles
					Tile t = Tile.getTile(tiles, gX, gY);
					if (t == null)
						t = new Tile(gX, gY);
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
