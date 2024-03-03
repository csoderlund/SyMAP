package symap.frame;

import java.util.Vector;

import symap.manager.Mproject;

import java.awt.Color;

/***********************************************
 * Created in ChrExpInit; used by Mapper, ChrExpInit, ChrExpFrame and ProjIcons 
 */
public class ChrInfo {
	protected Mproject project;
	private String strGroupName;
	private int nGroupIdx;
	private long sizeBP;
	private boolean bVisible = false;

	protected ChrInfo(Mproject project, String strGroupName, int nGroupIdx) {
		this.project = project;
		this.strGroupName = strGroupName;
		this.nGroupIdx = nGroupIdx;
	}
	
	public String toString() { return strGroupName; }
	protected String getGroupName() { return strGroupName; } 				// frame.ProjIcons
	
	protected boolean isVisible() { return bVisible; } 						// Mapper
	protected void setVisible( boolean bVisible ) { this.bVisible = bVisible; } // Mapper
	
	protected int getProjIdx() { return project.getID(); } 					// Mapper, ChrExpInit, ChrExpFrame
	protected int getGroupIdx() { return nGroupIdx; } 							// Mapper, ChrExpInit, ChrExpFrame, ProjIcons
	
	protected long getSizeBP() { return sizeBP; } // ProjIcons, Mapper
	protected void setSizeBP( long sizeBP ) { this.sizeBP = sizeBP; } 			// ChrExpInit; length of chromosome
		
	protected Color getColor() { return project.getColor(); } 					// ChrExpFrame.show2DView

	// Static
	protected static ChrInfo getChrByGroupIdx(Vector<ChrInfo> tracks, int grpIdx) { // ChrExpInit.loadAllBlocks
		for (ChrInfo t : tracks) {
			if (t.getGroupIdx() == grpIdx)
				return t;
		}
		return null;
	}
}
