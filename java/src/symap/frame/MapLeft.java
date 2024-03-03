package symap.frame;

/**************************************************************
 * Contains data for the ChromosomeExplorer icons
 */
import java.util.TreeSet;
import java.util.Vector;

import symap.manager.Mproject;

public class MapLeft  { // CAS521 removed 'extend Observer' - wasn't even used
	private Mproject[] projects;	
	private ChrInfo[] allChrs;
	private Block[] blocks;
	private ChrInfo reference;
		
	private float maxBpPerUnit = 0;
	private boolean bChanged = false;
	
	// Created in frame.ChrExpInit
	protected MapLeft() { reference = null;}
	
	protected void setProjects(Mproject[] projects) {this.projects = projects;}
	
	protected void setChrs(ChrInfo[] tracks) {
		this.allChrs = tracks;
		reference = tracks[0];
		
		for (ChrInfo t : tracks)
			maxBpPerUnit = Math.max(maxBpPerUnit, t.getSizeBP());
	}
	
	protected void setBlocks(Block[] blocks) {this.blocks = blocks;}
		
	// symap.frame.ChrExpFrame
	protected ChrInfo[] getChrs(int nProjID) {
		Vector<ChrInfo> out = new Vector<ChrInfo>();
		
		for (ChrInfo t : allChrs)
			if (t.getProjIdx() == nProjID)
				out.add(t);
		
		return out.toArray(new ChrInfo[0]);
	}
	// symap.frame.ProjIcons
	protected ChrInfo[] getChrs(int nProjID,TreeSet<Integer> grpIdxWithSynteny) {
		Vector<ChrInfo> out = new Vector<ChrInfo>();
		
		for (ChrInfo t : allChrs){
			if (t.getProjIdx() == nProjID){
				if (grpIdxWithSynteny.contains(t.getGroupIdx())){
					out.add(t);
				}
			}
		}
		return out.toArray(new ChrInfo[0]);
	}	
	
	protected Block[] getBlocksForProject(int nProjID) {
		Vector<Block> out = new Vector<Block>();
		
		for (Block b : blocks)
			if (b.getProj1Idx() == nProjID)
				out.add(b);
			else if (b.getProj2Idx() == nProjID)
				out.add(b.swap());
		
		return out.toArray(new Block[0]);
	}
	
	protected Mproject[] getProjects() { return projects; }
	
	protected int[] getVisibleProjectIDs() { // returns reference as first in list
		Vector<Integer> out = new Vector<Integer>();
		out.add( reference.getProjIdx() );
		for (Mproject p : projects) {
			if (getNumVisibleChrs(p.getID()) > 0)
				out.add(p.getID());
		}
		
		int[] out2 = new int[out.size()];
		for (int i = 0;  i < out2.length;  i++)
			out2[i] = out.get(i);
		
		return out2;
	}
	
	protected int[] getVisibleGroupIDs() { // returns reference as first in list
		int[] out = new int[getNumVisibleChrs()+1];
		out[0] = reference.getGroupIdx();
		int i = 1;
		for (ChrInfo t : getVisibleChrs())
			out[i++] = t.getGroupIdx();
		return out;
	}
	
	protected void setRefChr(ChrInfo reference) { 
		this.reference = reference;
		bChanged = true;
	}
	
	protected ChrInfo getRefChr() { return reference; }
	protected boolean isRefChr(ChrInfo t) { return t == reference; }
	
	protected int getNumVisibleChrs() { // excluding the reference track
		int n = 0;
		for (ChrInfo t : allChrs)
			if (t.isVisible() && t != reference) n++;
		return n;
	}
	
	private int getNumVisibleChrs(int nProjID) {
		int n = 0;
		for (ChrInfo t : allChrs)
			if (t.isVisible() && t.getProjIdx() == nProjID) n++;
		return n;
	}
	
	protected ChrInfo[] getVisibleChrs() { // excluding the reference track
		Vector<ChrInfo> visible = new Vector<ChrInfo>();
		for (ChrInfo t : allChrs)
			if (t.isVisible() && t != reference) visible.add(t);
		return visible.toArray(new ChrInfo[0]);
	}
	
	protected void hideVisibleChrs() { // excluding the reference
		for (ChrInfo t : allChrs)
			if (t != reference) t.setVisible(false);
		bChanged = true;
	}
	
	protected void setVisibleChr(ChrInfo t, boolean visible) {
		t.setVisible(visible);
		bChanged = true;
	}
	
	protected float getMaxBpPerUnit() { return maxBpPerUnit; }
		
	protected boolean hasChanged() { return bChanged; }
}
