package symap.frame;

import java.util.Vector;

import symap.manager.Mproject;

import java.awt.Color;

/***********************************************
 * Created in ChrExpInit; used by Mapper, ChrExpInit, ChrExpFrame and ProjIcons 
 */
public class TrackCom {
	protected Mproject project;
	private String strGroupName;
	private int nGroupIdx;
	private long sizeBP;
	boolean bVisible = false;

	public TrackCom(Mproject project, String strGroupName, int nGroupIdx) {
		this.project = project;
		this.strGroupName = strGroupName;
		this.nGroupIdx = nGroupIdx;
	}
	
	public String toString() { return strGroupName; }
	public String getFullName() { return project.getDisplayName() + " (" + strGroupName + ")"; }
	public String getGroupName() { return strGroupName; } 					// frame.ProjIcons
	
	public boolean isVisible() { return bVisible; } 						// Mapper
	public void setVisible( boolean bVisible ) { this.bVisible = bVisible; } // Mapper
	
	public int getProjIdx() { return project.getID(); } 					// Mapper, ChrExpInit, ChrExpFrame
	public int getGroupIdx() { return nGroupIdx; } 							// Mapper, ChrExpInit, ChrExpFrame, ProjIcons
	
	public long getSizeBP() { return sizeBP; } // ProjIcons, Mapper
	public void setSizeBP( long sizeBP ) { this.sizeBP = sizeBP; } 			// ChrExpInit; length of chromosome
		
	public Color getColor() { return project.getColor(); } 					// ChrExpFrame.show2DView

	// Static
	public static TrackCom getTrackByGroupIdx(Vector<TrackCom> tracks, int grpIdx) { // ChrExpInit.loadAllBlocks
		for (TrackCom t : tracks) {
			if (t.getGroupIdx() == grpIdx)
				return t;
		}
		return null;
	}
}
