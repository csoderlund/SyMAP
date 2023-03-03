package symap.track;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseEvent;

import javax.swing.JButton;
import javax.swing.AbstractButton;
import javax.swing.JComponent;

import symap.drawingpanel.DrawingPanel;
import symap.filter.FilterHandler;
import symap.frame.HelpBar;
import util.ErrorReport;

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class TrackHolder extends JComponent  {	
	private DrawingPanel dp;
	private HelpBar hb;
	private int orientation;
	private Track track;
	private FilterHandler fh;
	private int trackNum;	// CAS517 add for Sequence track 

	public TrackHolder(DrawingPanel dp, HelpBar hb, int trackNum) { // Called by DrawingPanel
		super();
		this.dp = dp;
		this.hb = hb;
		this.trackNum = trackNum;
		
		fh = new FilterHandler(dp,hb);
		track = null;
		
		setOpaque(false);
		setVisible(false);
	}

	public void setOrientation(int orient) { orientation = orient;}// Called by DrawingPanel

	public Track getTrack() {return track;}// Called by DrawingPanel, Mapper
	
	public int getTrackNum() {return trackNum;}
	
	public void setTrack(Track t) { // Called by DrawingPanel and TrackHolder
		if (t == track) return ;

		if (track != null) {
			removeAll();
			removeMouseListener(track);
			removeMouseMotionListener(track);
			removeMouseWheelListener(track); 	
			removeKeyListener(track); 			
			if (hb != null) hb.removeHelpListener(this);	    
			track.clear();
		}
		track = t;
		fh.set(track);
		if (track != null) {
			track.setHeld();
			addMouseListener(track);
			addMouseMotionListener(track);
			addMouseWheelListener(track); 	
			addKeyListener(track); 			
			if (hb != null) hb.addHelpListener(this,track);
			track.setOrientation(orientation);
		}
	}

	public TrackData getTrackData() { // called by DrawingPanelData
		if (track != null) return track.getData();
		else return null;
	}

	public void setTrackData(TrackData td) { // called by DrawingPanel
		if (td == null) setTrack(null);
		else {
			if (track == null || td.getTrackClass() != track.getClass()) {
				Track t = null;
				try { // CAS512 this was one big statement
					Class <?> [] x = new Class[]{dp.getClass(),getClass()};
					Object [] y = new Object[]{dp,this};
					t = (Track) td.getTrackClass().getConstructor(x).newInstance(y);
				} catch(Exception e) {
					ErrorReport.print(e, "setTrackData");
				}
				setTrack(t);
			}
			track.setup(td);
		}
	}
	public int getTrackPadding() {
		if (track != null) return (int)track.getPadding();
		else return 0;
	}
	public Point getOffset() {
		if (track != null) return track.getMoveOffset();
		else return new Point();
	}
	public Dimension getPreferredSize() {
		if (track != null) return track.getDimension();
		else return new Dimension(0,0);
	}
	public void setVisible(boolean visible) {
		if (track != null && fh.getFilterButton() != null) fh.getFilterButton().setEnabled(visible);
		super.setVisible(visible);
	}
	public AbstractButton getFilterButton() {
		if (track != null) return fh.getFilterButton();
		else return new JButton();
	}
	public void closeFilter() {
		fh.closeFilter();
	}
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		if (track != null) track.paintComponent(g);
	}
	public void showPopupFilter(MouseEvent e) {
		fh.showPopup(e);
	}
	public String toString() {
		String x = String.format("%2d", orientation);
		return "TH " + x + " #" + trackNum;
	}
}
