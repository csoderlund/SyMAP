package symap.sequence;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.Point;

import javax.swing.JButton;
import javax.swing.JPanel;

import symap.drawingpanel.DrawingPanel;
import symap.mapper.Mapper;

/***************************************************
 * Lays out tracks; called in DrawingPanels
 */
public class TrackLayout implements LayoutManager {
	private DrawingPanel dp; 
	private TrackHolder[] trackHolders; // N sequence
	private Mapper[] mappers;			// N-1 hits joining 2 sequence
	private JPanel buttonPanel;			// to contain Filter buttons
	
	private final int MAX_SPACE = 180; // maximum space for a track with added ruler and anno
	
	public TrackLayout(DrawingPanel dp, TrackHolder[] trackHolders, Mapper[] mappers, JPanel buttonPanel) {
		buttonPanel.setLayout(null);
		this.dp = dp;
		this.buttonPanel = buttonPanel;
		this.mappers = mappers;
		this.trackHolders = trackHolders;
	}
	
	public void layoutContainer(Container target) {
		if (!trackHolders[0].isVisible()) return;
		if (!mappers[0].isVisible()) return;
		
		if (buttonPanel == null) {
			symap.Globals.eprt("Null button panel in Track Layout");
			return;
		}
		buttonPanel.removeAll(); 
		
		int nTracks = trackHolders.length;
		int accWid = 5; 								// accumulated width 
		Dimension filterDim = new Dimension(0,0); 	    // filter buttons dimension
		
		// Set track dimensions and location and calculate button panel size
		for (int i = 0; i < nTracks; i++) { 
			Dimension dimTrack = trackHolders[i].getPreferredSize();	
			trackHolders[i].setSize(dimTrack.width, dimTrack.height);
		
			if (i > 0) accWid += dp.trackDistance;
			trackHolders[i].setLocation(accWid, 0);
			
			// set button dims
			JButton ithButton = trackHolders[i].getFilterButton();
			buttonPanel.add(ithButton);
			
			Dimension dimButton = ithButton.getPreferredSize();
			ithButton.setSize(dimButton);
			
			int mid = (int) Math.round(trackHolders[i].getTrack().getMidX() + accWid - (dimButton.width/2.0));
			ithButton.setLocation(mid,1);
			
			// update dims
			if (i>0 && nTracks>2) 	accWid += Math.min(dimTrack.width, MAX_SPACE); 
			else 					accWid += dimTrack.width;
			
			filterDim.height = Math.max(filterDim.height, ithButton.getHeight());
			filterDim.width = accWid;

			accWid += dp.trackDistance;
		}
		buttonPanel.setSize(filterDim.width, buttonPanel.getHeight());
		
		// Set mappers dims and Hit Filter dims and loc
		Point pt1, pt2       = trackHolders[0].getLocation();
		Dimension dim1, dim2 = trackHolders[0].getSize();
		
		for (int i = 0; i < mappers.length; i++) {
			pt1 = pt2;
			dim1 = dim2;
			pt2 =  trackHolders[i+1].getLocation();
			dim2 = trackHolders[i+1].getSize();

			Dimension dim = new Dimension(Math.max(dim1.width  + pt1.x, dim2.width  + pt2.x), 	
								          Math.max(dim1.height + pt1.y, dim2.height + pt2.y));	
			mappers[i].setSize(dim);

			// Set Hit Filter dimensions and locations
			JButton ithButton = mappers[i].getFilterButton();
			buttonPanel.add(ithButton);
			
			Dimension bDim = ithButton.getPreferredSize();
			ithButton.setSize(bDim);
			
			JButton b1 = trackHolders[i].getFilterButton(), b2 = trackHolders[i+1].getFilterButton();
			int mid = (int) Math.round((b1.getX() + b1.getWidth() + b2.getX() - bDim.width) / 2.0);
			ithButton.setLocation(mid, 1);
			
			filterDim.height = Math.max(filterDim.height,ithButton.getHeight());
		}

		// Finish buttonPanel
		filterDim.height += 2;
		if (filterDim.width > 0) {
			buttonPanel.setPreferredSize(filterDim);
			buttonPanel.setSize(filterDim);
		}	
	}
	public Dimension preferredLayoutSize(Container target) { // necessary for scroll bar
		if (!trackHolders[0].isVisible()) return target.getSize();

		Dimension dim = new Dimension(0,0);
		
		for (int i = 0; i < trackHolders.length; i++) {
			if (i > 0) dim.width += dp.trackDistance;
			Dimension d = trackHolders[i].getPreferredSize();
			dim.height = Math.max(dim.height,d.height);
			dim.width += d.width; 
			if (i > 0) dim.width += dp.trackDistance;
		}
		return dim;
	}
	
	public Dimension minimumLayoutSize(Container target) {return preferredLayoutSize(target);}
	public void addLayoutComponent(String name, Component comp) { }
	public void removeLayoutComponent(Component comp) { }
}
