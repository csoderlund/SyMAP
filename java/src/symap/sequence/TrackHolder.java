package symap.sequence;

/*************************************************************
 * Holds a sequence track
 */
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseEvent;

import javax.swing.JButton;
import javax.swing.JComponent;

import symap.drawingpanel.DrawingPanel;
import symap.drawingpanel.FilterHandler;
import symap.frame.HelpBar;
import util.ErrorReport;

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class TrackHolder extends JComponent  {	
	private DrawingPanel dp;
	private HelpBar hb;
	private int orientation;
	private Sequence track;	// CAS550 track->sequence
	private FilterHandler fh;
	private int trackNum;	// CAS517 add for Sequence track 

	public TrackHolder(DrawingPanel dp, HelpBar hb, int trackNum, int side) { // Called by DrawingPanel; created on startup
		super();
		this.dp = dp;
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
	
	public void setTrack(Sequence t) { // Called by DrawingPanel.setMaps, setTracks and TrackHolder 
		if (t == track) return ;

		if (track != null) {
			removeAll();
			removeMouseListener(track);
			removeMouseMotionListener(track);
			removeMouseWheelListener(track); 	
			removeKeyListener(track); 			
			if (hb != null) hb.removeHelpListener(this);	    
			track.clearSeq();
		}
		track = t;
		fh.setSfilter(track);
		
		if (track != null) {
			addMouseListener(track);
			addMouseMotionListener(track);
			addMouseWheelListener(track); 	
			addKeyListener(track); 			
			if (hb != null) hb.addHelpListener(this,track);
			track.setOrientation(orientation);
		}
	}
	public void setTrackData(TrackData td) { // called by DrawingPanel.setMaps for History
		if (td == null) setTrack(null);
		else {
			if (track == null) {
				Sequence t = null;
				try { // CAS512 this was one big statement
					Class <?> [] x  = new Class[]{dp.getClass(),getClass()};
					Object [] 	 y  = new Object[]{dp,this};
					Class <?>    tc = td.getTrackClass();
					t = (Sequence)  tc.getConstructor(x).newInstance(y);
				} 
				catch(Exception e) {ErrorReport.print(e, "setTrackData");}
				
				setTrack(t);
			}
			track.setup(td);
		}
	}
	public TrackData getTrackData() { // called by DrawingPanelData for History; 
		if (track != null) return track.getData(); // copy of TrackData filter and graphic settings
		else return null;
	}

	
	protected void showPopupFilter(MouseEvent e) { // Sequence.mousePressed
		fh.showPopup(e);
	}
	protected Point getOffset() { // TrackLayout
		if (track != null) return track.getMoveOffset();
		else return new Point();
	}
	protected JButton getFilterButton() { // TrackLayout
		if (track != null) return fh.getFilterButton();
		else return new JButton();
	}
	public void closeFilter() { // DrawingPanel
		fh.closeFilter();
	}
	
	public Dimension getPreferredSize() {
		if (track != null) return track.getDimension();
		else return new Dimension(0,0);
	}
	public void setVisible(boolean visible) { 
		if (track != null && fh.getFilterButton() != null) fh.getFilterButton().setEnabled(visible);
		super.setVisible(visible);
	}
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		if (track != null) track.paintComponent(g);
	}
	public String toString() {
		String seq = (track==null) ? "null" : track.toString();
		return String.format("TrackHolder side=%2d #%d %s", orientation, trackNum, seq);
	}
}
