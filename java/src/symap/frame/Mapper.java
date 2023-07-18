package symap.frame;

import java.util.TreeSet;
import java.util.Vector;

import symap.manager.Mproject;

public class Mapper  { // CAS521 removed 'extend Observer' - wasn't even used
	public static final float MIN_CYLINDER_HEIGHT = 0.02f;
	public static final float CYLINDER_WIDTH = 0.04f;
	public static final float DISPLAY_RADIUS = 0.4f;
	
	// Fudge factor to make the blocks slightly overlap the cylinders to prevent spaces from jaggy effect.
	public static final float JAGGY_FUDGE = 0.95f;
	
	protected Mproject[] projects;	
	public TrackCom[] tracks;
	protected Block[] blocks;
	public TrackCom reference;
		
	protected float maxBpPerUnit = 0;
	
	private boolean bChanged = false;
	
	// Created in frame.ChrExpInit
	public Mapper() { reference = null;}
	
	public void setProjects(Mproject[] projects) {
		this.projects = projects;
	}
	
	public void setTracks(TrackCom[] tracks) {
		this.tracks = tracks;
		reference = tracks[0];
		
		for (TrackCom t : tracks)
			maxBpPerUnit = Math.max(maxBpPerUnit, t.getSizeBP());
	}
	
	public void setBlocks(Block[] blocks) {this.blocks = blocks;}
		
	// symap.frame.ChrExpFrame
	public TrackCom[] getTracks(int nProjID) {
		Vector<TrackCom> out = new Vector<TrackCom>();
		
		for (TrackCom t : tracks)
			if (t.getProjIdx() == nProjID)
				out.add(t);
		
		return out.toArray(new TrackCom[0]);
	}
	// symap.frame.ProjIcons
	public TrackCom[] getTracks(int nProjID,TreeSet<Integer> grpIdxWithSynteny) {
		Vector<TrackCom> out = new Vector<TrackCom>();
		
		for (TrackCom t : tracks){
			if (t.getProjIdx() == nProjID){
				if (grpIdxWithSynteny.contains(t.getGroupIdx())){
					out.add(t);
				}
			}
		}
		return out.toArray(new TrackCom[0]);
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
	
	public Mproject[] getProjects() { return projects; }
	
	public int[] getVisibleProjectIDs() { // returns reference as first in list
		Vector<Integer> out = new Vector<Integer>();
		out.add( reference.getProjIdx() );
		for (Mproject p : projects) {
			if (getNumVisibleTracks(p.getID()) > 0)
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
	
	private int getNumVisibleTracks(int nProjID) {
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
}
