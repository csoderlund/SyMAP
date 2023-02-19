package symap.frame;

import java.util.Vector;

import symap.manager.Mproject;

import java.awt.Color;

public class TrackCom {
	protected Mproject project;
	private String strGroupName;
	private int nGroupIdx;
	private long sizeBP;
	private float posArg0, posArg1, posArg2;
	private int bpPerUnit; 			// for fpc 
	boolean bVisible = false;

	public TrackCom(Mproject project, String strGroupName, int nGroupIdx) {
		this.project = project;
		this.strGroupName = strGroupName;
		this.nGroupIdx = nGroupIdx;
		this.bpPerUnit = 1;
	}
	
	public String toString() { return strGroupName; }
	public String getFullName() { return project.getDisplayName() + " (" + strGroupName + ")"; }
	public String getGroupName() { return strGroupName; }
	
	public boolean isVisible() { return bVisible; }
	public void setVisible( boolean bVisible ) { this.bVisible = bVisible; }
	
	public int getProjIdx() { return project.getID(); }
	public int getGroupIdx() { return nGroupIdx; }
	
	public int getBpPerUnit() { return bpPerUnit; }
	public void setBpPerUnit( int n ) { bpPerUnit = n; }
	
	public long getSizeBP() { return sizeBP; }
	public void setSizeBP( long sizeBP ) { this.sizeBP = sizeBP; }
		
	public Color getColor() { return project.getColor(); }

	public float getPositionArg0() { 
		return posArg0; 
	}
	
	public float getPositionArg1() { 
		return posArg1; 
	}
	
	public float getPositionArg2() { 
		return posArg2; 
	}
	
	public void setPosition( float arg0, float arg1, float arg2 ) {
		posArg0 = arg0;
		posArg1 = arg1;
		posArg2 = arg2;
	}
	
	public static TrackCom getTrackByGroupIdx(Vector<TrackCom> tracks, int grpIdx) {
		for (TrackCom t : tracks) {
			if (t.getGroupIdx() == grpIdx)
				return t;
		}
		return null;
	}
}
