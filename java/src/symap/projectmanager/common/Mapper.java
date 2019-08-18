package symap.projectmanager.common;

import java.util.Observable;
import java.util.TreeSet;
import java.util.Vector;

public class Mapper extends Observable {
	public static final float MIN_CYLINDER_HEIGHT = 0.02f;
	public static final float CYLINDER_WIDTH = 0.04f;
	public static final float DISPLAY_RADIUS = 0.4f;
	
	// Fudge factor to make the blocks slightly overlap the cylinders to
	// prevent spaces from jaggy effect.
	public static final float JAGGY_FUDGE = 0.95f;
	
	//private SyMAPFrame3D frame; // mdb removed 2/1/10
	
	protected Project[] projects;	
	public TrackCom[] tracks;
	protected Block[] blocks;
	public TrackCom reference;
		
	protected float maxBpPerUnit = 0;
	
	public static final int PICK_DO_NOTHING = 0;
	public static final int PICK_DELETE     = 1;
//	private int nPickFunction = PICK_DO_NOTHING;
	
	public static final int NAVIGATION_NONE      = 0;
	public static final int NAVIGATION_ROTATE    = 1;
	public static final int NAVIGATION_TRANSLATE = 2;
	public static final int NAVIGATION_ZOOM      = 3;
	private int nNavigationFunction = NAVIGATION_ROTATE;
	
	private boolean bChanged = false;
	
	public Mapper() { 
		reference = null;
	}
	
// mdb removed 2/1/10	
//	public void setFrame(SyMAPFrame3D frame) {
//		this.frame = frame;
//	}
	
	public void setProjects(Project[] projects) {
		this.projects = projects;
	}
	
	public void setTracks(TrackCom[] tracks) {
		this.tracks = tracks;
		reference = tracks[0];
		
		for (TrackCom t : tracks)
			maxBpPerUnit = Math.max(maxBpPerUnit, t.getSizeBP());
	}
	
	public TrackCom[] getTracks(int nProjID) {
		Vector<TrackCom> out = new Vector<TrackCom>();
		
		for (TrackCom t : tracks)
			if (t.getProjIdx() == nProjID)
				out.add(t);
		
		return out.toArray(new TrackCom[0]);
	}
	public TrackCom[] getTracks(int nProjID,TreeSet<Integer> grpIdxWithSynteny) {
		Vector<TrackCom> out = new Vector<TrackCom>();
		
		for (TrackCom t : tracks)
		{
			if (t.getProjIdx() == nProjID)
			{
				if (grpIdxWithSynteny.contains(t.getGroupIdx()))
				{
					out.add(t);
				}
			}
		}
		
		return out.toArray(new TrackCom[0]);
	}	
	public void setBlocks(Block[] blocks) {
		this.blocks = blocks;
	}
	
	public Block[] getBlocksForProject(int nProjID) {
		Vector<Block> out = new Vector<Block>();
		
		for (Block b : blocks)
			if (b.getProj1Idx() == nProjID)
				out.add(b);
			else if (b.getProj2Idx() == nProjID)
				out.add(b.swap());
		
		return out.toArray(new Block[0]);
	}
	
	public Block[] getBlocksForTracks(int nGroup1Idx, int nGroup2Idx) {
		Vector<Block> out = new Vector<Block>();
		
		for (Block b : blocks)
			if (b.getGroup1Idx() == nGroup1Idx && b.getGroup2Idx() == nGroup2Idx)
				out.add(b);
			else if (b.getGroup2Idx() == nGroup1Idx && b.getGroup1Idx() == nGroup1Idx)
				out.add(b.swap());
		
		return out.toArray(new Block[0]);
	}
	
	static public String getContigListForBlocks( Block[] blockArray ) {
		String out = "";
		
		for (int i = 0;  i < blockArray.length;  i++)
			out += blockArray[i].getCtgs1() + ",";
		
		return out;
	}
	
	public Project[] getProjects() { return projects; }
	public int getNumProjects() { return projects.length; }
	
	public int[] getVisibleProjectIDs() { // returns reference as first in list
		Vector<Integer> out = new Vector<Integer>();
		out.add( reference.getProjIdx() );
		for (Project p : projects) {
			if (/*p.getID() != reference.getProjIdx() && */getNumVisibleTracks(p.getID()) > 0)
				out.add(p.getID());
		}
		
		int[] out2 = new int[out.size()];
		for (int i = 0;  i < out2.length;  i++)
			out2[i] = out.get(i);
		
		return out2;
	}
	
	public int[] getVisibleGroupIDs() { // returns reference as first in list
		int[] out = new int[getNumVisibleTracks()+1];
		out[0] = reference.getGroupIdx();
		int i = 1;
		for (TrackCom t : getVisibleTracks())
			out[i++] = t.getGroupIdx();
		return out;
	}
	
	public void setReferenceTrack(TrackCom reference) { 
		this.reference = reference;
		bChanged = true;
	}
	
	public TrackCom getReferenceTrack() { return reference; }
	public boolean isReference(TrackCom t) { return t == reference; }
	public int getNumTracks() { return tracks.length; }
	
	public int getNumVisibleTracks() { // excluding the reference track
		int n = 0;
		for (TrackCom t : tracks)
			if (t.isVisible() && t != reference) n++;
		return n;
	}
	
	public int getNumVisibleTracks(int nProjID) {
		int n = 0;
		for (TrackCom t : tracks)
			if (t.isVisible() && t.getProjIdx() == nProjID) n++;
		return n;
	}
	
	public TrackCom[] getVisibleTracks() { // excluding the reference track
		Vector<TrackCom> visible = new Vector<TrackCom>();
		for (TrackCom t : tracks)
			if (t.isVisible() && t != reference) visible.add(t);
		return visible.toArray(new TrackCom[0]);
	}
	
	public TrackCom[] getVisibleTracks(int nProjID) {
		Vector<TrackCom> visible = new Vector<TrackCom>();
		for (TrackCom t : tracks)
			if (t.isVisible() && t.getProjIdx() == nProjID) visible.add(t);
		return visible.toArray(new TrackCom[0]);
	}
	
	public void hideVisibleTracks() { // excluding the reference
		for (TrackCom t : tracks)
			if (t != reference) t.setVisible(false);
		bChanged = true;
	}
	
	public void setTrackVisible(TrackCom t, boolean visible) {
		t.setVisible(visible);
		bChanged = true;
	}
	
	public float getMaxBpPerUnit() { return maxBpPerUnit; }
		
	public boolean hasChanged() { return bChanged; }
	
	public int getNavigationFunction() { return nNavigationFunction; }
	


}
