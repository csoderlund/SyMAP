package symap.track;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.Point;

import javax.swing.AbstractButton;
import javax.swing.JPanel;

import symap.mapper.Mapper;

public class TrackLayout implements LayoutManager {

	private TrackHolder[] trackHolders;
	private Mapper[] mappers;
	private JPanel buttonPanel;
	private static final int buttonPadding = 1;

	public TrackLayout(TrackHolder[] trackHolders, Mapper[] mappers,
			JPanel buttonPanel) 
	{
		buttonPanel.setLayout(null);
		this.buttonPanel = buttonPanel;
		this.mappers = mappers;
		this.trackHolders = trackHolders;
	}

	public void addLayoutComponent(String name, Component comp) { }

	public void removeLayoutComponent(Component comp) { }

	public Dimension preferredLayoutSize(Container target) {
		if (!trackHolders[0].isVisible()) return target.getSize();

		Dimension dim = new Dimension(0,0);
		Dimension d;
		Point moveOffset;
		for (int i = 0; i < trackHolders.length; i++) {
			if (!trackHolders[i].isVisible()) break; // no breaks in maps allowed
			if (i > 0) dim.width += trackHolders[i-1].getTrackPadding();
			d = trackHolders[i].getPreferredSize();
			moveOffset = trackHolders[i].getOffset();
			dim.height = Math.max(dim.height,d.height);
			dim.width += d.width + moveOffset.x;
			if (i != 0) dim.width += trackHolders[i].getTrackPadding();
		}
		
		return dim;
	}

	public Dimension minimumLayoutSize(Container target) {
		return preferredLayoutSize(target);
	}

	public void layoutContainer(Container target) {
		Point p1, p2;
		Dimension dim1, dim2, dim, d;
		Point moveOffset;
		int x = 0, i;

		AbstractButton button;
		Dimension bpDim = new Dimension(0,0);
		Dimension bDim;

		if (buttonPanel != null) buttonPanel.removeAll();
		for (i = 0; i < trackHolders.length; i++) {
			if (!trackHolders[i].isVisible()) break;

			d = trackHolders[i].getPreferredSize();
			trackHolders[i].setSize(d.width, d.height);
			moveOffset = trackHolders[i].getOffset();
			x += moveOffset.x;
			if (i != 0) x += trackHolders[i].getTrackPadding();

			trackHolders[i].setLocation(x,moveOffset.y);
			if (buttonPanel != null) {
				button = trackHolders[i].getFilterButton();
				buttonPanel.add(button);
				bDim = button.getPreferredSize();
				button.setSize(bDim);
				button.setLocation(getMidTrack(trackHolders[i].getTrack(),x,bDim.width),buttonPadding);
				bpDim.height = Math.max(bpDim.height,button.getHeight());
			}
			x += d.width;

			bpDim.width = x;

			x += trackHolders[i].getTrackPadding();
		}

		if (buttonPanel != null) buttonPanel.setSize(bpDim.width,buttonPanel.getHeight());

		dim2 = trackHolders[0].getSize();
		p2 = trackHolders[0].getLocation();
		for (i = 0; i < mappers.length; i++) {
			if (!mappers[i].isVisible()) break;
			p1 = p2;
			dim1 = dim2;
			p2 = trackHolders[i+1].getLocation();
			dim2 = trackHolders[i+1].getSize();

			dim = new Dimension(
					Math.max(dim1.width + p1.x, dim2.width + p2.x), 	
					Math.max(dim1.height + p1.y, dim2.height + p2.y));	
			mappers[i].setSize(dim);

			if (buttonPanel != null) {
				button = mappers[i].getFilterButton();
				buttonPanel.add(button);
				bDim = button.getPreferredSize();
				button.setSize(bDim);
				button.setLocation(getMidMap(trackHolders[i].getFilterButton(),
						trackHolders[i+1].getFilterButton(),bDim.width),
						buttonPadding);
				bpDim.height = Math.max(bpDim.height,button.getHeight());
			}
		}

		if (buttonPanel != null) {
			bpDim.height += (buttonPadding * 2);
			if (bpDim.width > 0) {
				buttonPanel.setPreferredSize(bpDim);
				buttonPanel.setSize(bpDim);
			}
		}
	}

	private int getMidTrack(Track track, double x, double bw) {
		return (int)Math.round(x+track.getMidX() - (bw/2.0));
	}

	private int getMidMap(AbstractButton b1, AbstractButton b2, int bw) {
		return (int)Math.round( (b1.getX() + b1.getWidth() + b2.getX() - bw) / 2.0);
	}

	//private int getMidMap(double b1x, double b2x, double b1w, double bw) {
	//return (int)Math.round((b1x + b1w + b2x) / 2.0 - (bw / 2.0));
	//}
}
