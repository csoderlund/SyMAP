package symap.sequence;

/*************************************************************
 * Holds a sequence track
 * CAS551 removed all checks for track==null; remove getOffset() { return track.getMoveOffset();}
 */
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseEvent;

import javax.swing.JButton;
import javax.swing.JComponent;

import symap.drawingpanel.DrawingPanel;
import symap.drawingpanel.FilterHandler;
import symap.frame.HelpBar;

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class TrackHolder extends JComponent  {	
	private HelpBar hb;
	private int orientation; // left-right
	private Sequence track=null;	// CAS550 track->sequence
	private FilterHandler fh;
	private int trackNum;	// CAS517 add for Sequence track 

	private void dprt(String msg) {symap.Globals.dprt("TH " + msg);}
	
	public TrackHolder(DrawingPanel dp, HelpBar hb, int trackNum, int side) { // Called by DrawingPanel; created on startup
		super();
		this.hb = hb;
		this.trackNum = trackNum;
		this.orientation = side;
		
		fh = new FilterHandler(dp);
		track = null;
		
		setOpaque(false);
		setVisible(false);
	}

	public Sequence getTrack() {return track;}// Called by DrawingPanel, Mapper - CAS550 return Sequence
	
	public int getTrackNum() {return trackNum;}
	
	public void setTrack(Sequence t) { // Called by DrawingPanel.setSequenceTrack 
		if (t == track && t!=null) dprt("Has track " + track.toString());
		if (t == null) dprt("null seq track ");
		// CAS551 rm code to remove mouse listeners if track!=null
		track = t;
		fh.setSfilter(track);
		addMouseListener(track);
		addMouseMotionListener(track);
		addMouseWheelListener(track); 	
		addKeyListener(track); 			
		if (hb != null) hb.addHelpListener(this,track);
		track.setOrientation(orientation);
	}
	public void setTrackData(TrackData td) { // called by DrawingPanel.setMaps for History
		track.setup(td); 					 // CAS551 rm code to set up new track
	}
	public TrackData getTrackData() { return track.getData();} // called by DrawingPanelData for History; 
		
	protected void showPopupFilter(MouseEvent e) { fh.showPopup(e);} // Sequence.mousePressed
		
	protected JButton getFilterButton() { return fh.getFilterButton(); }// TrackLayout
		
	public void closeFilter() { fh.closeFilter();}// DrawingPanel
	
	public Dimension getPreferredSize() {return track.getDimension();}
	
	public void setVisible(boolean visible) { 
		if (fh.getFilterButton() != null) fh.getFilterButton().setEnabled(visible);
		super.setVisible(visible);
	}
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		track.paintComponent(g);
	}
	public String toString() {
		String seq = (track==null) ? "null" : track.toString();
		return String.format("TrackHolder side=%2d #%d %s", orientation, trackNum, seq);
	}
}
