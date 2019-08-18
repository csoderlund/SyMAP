package symap.mapper;

import java.awt.geom.Point2D;
import java.awt.Color;
import java.awt.Point;

import symap.contig.Clone;
import symap.contig.Contig;
import symap.block.Block;
import symap.marker.MarkerTrack;

class BESHit {
	private Clone clone;

	public BESHit() { }

	public void clear(Hit parent) {
		if (clone != null) clone.removeHit(parent);
	}

	public void set(Hit parent, MarkerTrack mt, String name, int contig) {
		if (clone != null) clone.removeHit(parent);
		if (mt instanceof Contig) clone = ((Contig)mt).getClone(name);
		else clone = null;
		if (clone != null) clone.addHit(parent);
	}

	public boolean isFiltered(MarkerTrack track, HitFilter filter, int contig) {
		if (track instanceof Block)
			return !((Block)track).isContigVisible(contig);
		return clone == null || !clone.isInRange();
	}

	public boolean isVisible(MarkerTrack track, int contig) {
		if (track instanceof Block) return ((Block)track).isContigVisible(contig);
		return clone != null && clone.isVisible();
	}

	public Point2D getCPoint(MarkerTrack track, int orientation, Point loc, int contig, int pos, byte bes) {
		Point2D p;
		Point2D cpoint = new Point2D.Double();
		if (track instanceof Block) {
			p = ((Block)track).getPoint(pos,orientation,contig);
			cpoint.setLocation(p.getX()+loc.getX(),p.getY()+loc.getY());
		}
		else {
			p = clone.getEndPoint(bes);
			cpoint.setLocation(p.getX()+loc.getX(),p.getY()+loc.getY());
		} 
		return cpoint;
	}

	public boolean isHighlighted() {
		return (clone != null && clone.isHover());
	}

	public Color getCColor(boolean colorByStrand, boolean orientation) {
		if (clone != null && clone.isHover()) return Mapper.besLineHighlightColor;
		if (colorByStrand) return orientation ? Mapper.posOrientLineColor : Mapper.negOrientLineColor;
		return Mapper.besLineColor;
	}
}
