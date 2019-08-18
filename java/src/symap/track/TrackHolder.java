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
import symap.filter.Filtered;
import symap.frame.HelpBar;
import symap.SyMAP;

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class TrackHolder extends JComponent implements Filtered {	
	private static final boolean METHOD_TRACE = false;
	
	private DrawingPanel dp;
	private HelpBar hb;
	private int orientation;
	private Track track;
	private FilterHandler fh;

	public TrackHolder(DrawingPanel dp, HelpBar hb) {
		super();
		this.dp = dp;
		this.hb = hb;
		fh = new FilterHandler(dp,hb);
		track = null;
		setOpaque(false);
		setVisible(false);
	}

	public void setOrientation(int orient) {
		orientation = orient;
	}

	public Track getTrack() {
		return track;
	}

	public HelpBar getHelpBar() {
		return hb;
	}

	public void setTrack(Track t) {
		if (METHOD_TRACE) System.out.println("TrackHolder.setTrack: " + t);
		
		if (t == track) return ;

		if (track != null) {
			removeAll();
			removeMouseListener(track);
			removeMouseMotionListener(track);
			removeMouseWheelListener(track); 	// mdb added 3/1/07 #106
			removeKeyListener(track); 			// mdb added 3/2/07 #106
			if (hb != null) hb.removeHelpListener(this);	    
			track.clear();
		}
		track = t;
		fh.set(track);
		if (track != null) {
			track.setHeld();
			addMouseListener(track);
			addMouseMotionListener(track);
			addMouseWheelListener(track); 	// mdb added 3/1/07 #106
			addKeyListener(track); 			// mdb added 3/2/07 #106
			if (hb != null) hb.addHelpListener(this,track);
			track.setOrientation(orientation);
		}
	}

	public TrackData getTrackData() {
		if (track != null) return track.getData();
		else return null;
	}

	public void setTrackData(TrackData td) {
		if (td == null) setTrack(null);
		else {
			if (track == null || td.getTrackClass() != track.getClass()) {
				if (SyMAP.DEBUG) System.out.println("Creating a new Track");
				Track t = null;
				try {
					t = (Track)td.getTrackClass().getConstructor(new Class[]{dp.getClass(),getClass()})
					.newInstance(new Object[]{dp,this});
				} catch(Exception e) {
					e.printStackTrace();
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
	
	// mdb added 3/12/07 #104
	public void showPopupFilter(MouseEvent e) {
		fh.showPopup(e);
	}
}
