package symap.mapper;

import java.awt.Color;
import java.awt.Point;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.event.MouseEvent;
import java.util.Vector;

import symap.marker.Marker;
import symap.marker.MarkerTrack;
import symap.block.Block;
import symap.contig.Clone;
import symap.SyMAPConstants;
import util.Circle;

class MarkerHit implements SyMAPConstants {
	private Marker marker;
	private Point2D.Double cpoint;
	private Circle circle;

	public MarkerHit() {
		this.cpoint = new Point2D.Double();
		circle = new Circle(Mapper.markerJoinDotRadius);
	}

	public Marker getMarker() {
		return marker;
	}

	public void set(Hit parent, Marker mrk) {
		if (marker != null) marker.removeHit(parent);
		marker = mrk;
		if (marker != null) marker.addHit(parent);
	}

	public void set(Hit parent, MarkerTrack mt, String markerName, int contig) {
		if (marker != null) marker.removeHit(parent);
		marker = mt.getMarker(markerName,contig);
		if (marker != null) marker.addHit(parent);
	}

	public void clear(Hit parent) {
		if (marker != null) {
			marker.removeHit(parent);
		}
	}

	public boolean isFiltered(MarkerTrack mt, boolean onlyShared) {
		return marker == null || (onlyShared && !marker.isShared()) ||
		!(mt instanceof Block || (marker.isInRange() && marker.hasInRangeClone()));
	}

	public boolean isVisible(MarkerTrack mt) {
		return marker != null && marker.isVisible() && (mt instanceof Block || marker.hasVisibleClone());
	}

	public Point2D getCPoint(MarkerTrack mt, int orientation, Point loc,
			boolean showJoinDot) {
		if (mt instanceof Block) {
			Point2D p = marker.getPoint();
			cpoint.setLocation(((Block)mt).getXPoint(orientation)+loc.getX(), p.getY()+loc.getY());
		}
		else if (showJoinDot) {
			Point2D p = marker.getPoint();
			double x = loc.getX();
			if (orientation == LEFT_ORIENT)
				x += (mt.getDimension().getWidth() + (mt.getPadding() / 2.0));
			else
				x -= (mt.getPadding() / 2.0);
			cpoint.setLocation(x, p.getY() + loc.getY());
			circle.setLocation(cpoint);
		}
		else return null;
		return cpoint;
	}

	public boolean isHighlighted() {
		return marker != null && (marker.isHover() || marker.isCloneHover() || marker.isClickHighlighted());
	}

	public Color getCColor(boolean colorByStrand, boolean orientation) {
		if (isHighlighted()) return Mapper.markerLineHighlightColor;
		else if (colorByStrand) return orientation ? Mapper.posOrientLineColor : Mapper.negOrientLineColor;
		else if (marker != null && marker.isShared()) return Mapper.sharedMarkerLineColor;
		else return Mapper.markerLineColor;
	}

	public void paintComponent(Graphics2D g2, MarkerTrack mt, Point loc,
			Point2D opoint, boolean showJoinDot, boolean colorByStrand,
			boolean orient) {
		if ( !(mt instanceof Block) ) {
			Color c = Mapper.markerLineColor;
			if (marker.isHover() || marker.isClickHighlighted()) c = Mapper.markerLineHighlightColor;
			else if (marker.isShared()) c = Mapper.sharedMarkerLineColor;
			else if (colorByStrand) c = orient ? Mapper.posOrientLineColor : Mapper.negOrientLineColor;
			if (!showJoinDot) cpoint.setLocation(opoint);

			Line2D.Double line = new Line2D.Double();
			Vector<Line2D> highlightedLines = new Vector<Line2D>();
			Point2D p;
			g2.setPaint(c);
			for (Clone clone : marker.getClones()) {
				if (clone.isVisible()) {
					p = clone.getMidPoint();
					line.setLine(p.getX()+loc.getX(),p.getY()+loc.getY(), cpoint.x, cpoint.y);
					if ( c != Mapper.markerLineHighlightColor && clone.isHover() ) 
						highlightedLines.add(new Line2D.Double(line.x1,line.y1,line.x2,line.y2));
					else g2.draw(line);
				}
			}
			if (!highlightedLines.isEmpty()) {
				g2.setPaint(Mapper.markerLineHighlightColor);
				for (Line2D l : highlightedLines)
					g2.draw(l);
			}
			if (showJoinDot) circle.paint(g2,Mapper.markerJoinDotColor);
		}
	}

	public void mouseMoved(MouseEvent e) {
		marker.setHighlighted(circle.contains(e.getPoint()));
	}
}
