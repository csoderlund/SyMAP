package dotplot;

import java.util.Vector;
import database.DBconn2;
import props.PropsDB;
import symap.manager.Mproject;

/***************************************************************
 * Called in Data for each selected project
 * This uses the more global Mproject, but adds a little data and interface
 */
public class Project {
	private int id;
	private String displayName;
	private String grpPrefix;
	private Group[] groups;

	protected Project(int id, DBconn2 dbc2) { 
		Mproject tProj = new Mproject(dbc2, id, "", "");
		tProj.loadParamsFromDB();
		
		this.id          = id;
		this.displayName = tProj.getDisplayName();
		this.grpPrefix   = tProj.getGrpPrefix();
	}
	// Data.DBload reads all data
	protected void setGroups(Group[] grps) {
		if (grps != null) Group.setOffsets(grps);
		groups = grps;
	}

	protected Group getGroupByOrder(int sortOrder) {
		return groups == null ? null : groups[sortOrder-1];
	}

	protected Group getGroupByID(int id) {
		if (groups != null)
			for (Group g : groups)
				if (g.getID() == id) return g;
		return null;	
	}

	protected Group getGroupByOffset(double offset) {
		if (groups != null)
			for (int i = groups.length-1; i >= 0; i--)
				if (groups[i].getOffset() < offset) return groups[i];
		return null;
	}
	
	protected int getNumGroups() {
		return groups == null ? 0 : groups.length;
	}
	
	protected int getNumVisibleGroups() {
		int count = 0;
		for (Group g : groups)
			if (g.isVisible()) count++;
		return count;
	}

	protected int getID() { return id; }
	protected String getGrpPrefix() { return grpPrefix; }
	protected String getDisplayName() { return displayName; }
	public String toString() { return displayName; } 

	public boolean equals(Object obj) {
		return obj instanceof Project && ((Project)obj).id == id;
	}
	/*****************************************************
	 * Called in data.initialize to set up tiles
	 */
	protected static Tile[] createTiles(Project[] projects, Tile[] tiles, PropsDB pp) {
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
}
