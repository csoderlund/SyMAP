package symap.closeup.alignment;

import java.awt.BasicStroke;
import java.awt.FontMetrics;
import java.awt.Dimension;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.Point2D;
import symap.mapper.HitData;
import symap.closeup.components.CloseUpComponent;
import util.Arrow;
import util.ArrowLine;

public class HitAlignment implements Comparable<HitAlignment> { // mdb removed abstract 7/25/07 #121
	private HitData hd;
	private String projDispName;
	private int content;
	private String strand;
	private String queryName; // mdb added 1/9/09 for pseudo-pseudo closeup
	private int qStart, qEnd, tStart, tEnd, fullTargetWidth;
	private /*TargetSequence*/AbstractSequence target; 	// mdb re-classed 7/26/07 #134
	private /*QuerySequence*/AbstractSequence query; 	// mdb re-classed 7/26/07 #134
	private double[] misses;
	private double[] inserts;
	private double[] deletes;
	private boolean selected;
	private ArrowLine hitLine;
	private Rectangle2D.Double bounds;
	private int layer;

	public HitAlignment(HitData hd, String projDispName, 
			int content, // mdb renamed from "contig" 12/31/08 
			String strand, 
			String queryName, // mdb added 1/9/09 for pseudo-pseudo closeup
			/*TargetSequence*/AbstractSequence target, // mdb re-classed 7/26/07 #134
			/*QuerySequence*/AbstractSequence query,   // mdb re-classed 7/26/07 #134
			int queryStart, int queryEnd, 
			int targetStart, int targetEnd, int fullTargetWidth) 
			//int uniqueId) // mdb removed 1/7/09
	{
		this.hd = hd;
		this.projDispName = projDispName;
		this.content = content;
		this.strand = strand;
		this.queryName = queryName;
		this.qStart = queryStart;
		this.qEnd = queryEnd;
		this.tStart = targetStart;
		this.tEnd = targetEnd;
		this.fullTargetWidth = fullTargetWidth;
		//this.uniqueId = uniqueId; // mdb removed 1/7/09
		this.target = target;
		this.query = query;
		this.layer = 1;
		this.misses = AbstractSequence.getQueryMisses(query, target);
		this.inserts = AbstractSequence.getQueryInserts(query, target);
		this.deletes = AbstractSequence.getQueryDeletes(query, target);
		this.selected = false;
	}

	public int compareTo(HitAlignment ha) {
		return getStart() - ha.getStart();
	}

	public String alignStr(AbstractSequence q, AbstractSequence t) {
		char ret[] = new char[q.length()];
		
		for (int i = 0;  i < q.length();  i++) {
			if (q.charAt(i) == t.charAt(i)) ret[i] = '|';
			else ret[i] = ' ';
		}
		
		return new String(ret);
	}
	
	public String toString() {
		String title = (projDispName != null ? projDispName+" " : "")+(content > 0 ? "Contig "+content+" " : "")+hd;
		return title // 1jun10 CAS commented out
				//+ "\nE-value:  "+ getEvalue()
				//+ "\nIdentity: "+ ((int)Math.round(getPctid()))+"%"
				//+ "\nStrand:   "+ strand
				+ "\nQuery:    "+ qStart+"-"+qEnd
				+ "\nTarget:   "+ tStart+"-"+tEnd
				+ "\n\n" 		+ query.toString()
				+ "\n" 			+ alignStr(query, target)
				+ "\n" 			+ target.toString() + " \n"; 
	}

	public void setBounds(int startBP, double pixelStart, double bpPerPixel, double y) {
		setBounds( getX(startBP, pixelStart, bpPerPixel), y, bpPerPixel );
	}

	public void setBounds(double x, double y, double bpPerPixel) {
		if (bounds == null) {
			hitLine = new ArrowLine();
			bounds  = new Rectangle2D.Double();
			bounds.height = CloseUpComponent.HIT_HEIGHT;
		}
		bounds.y = y;
		bounds.x = x;
		//bounds.width = getWidth(bpPerPixel);
		bounds.width = hitWidth() / bpPerPixel; // ASD Changed to be just the hit itself
		hitLine.setBounds(bounds, CloseUpComponent.LINE_HEIGHT, CloseUpComponent.ARROW_DIMENSION,
				pointLeft() ? ArrowLine.LEFT_ARROW : ArrowLine.RIGHT_ARROW);
	}

	public Rectangle2D getBounds() {
		if (bounds == null) return new Rectangle2D.Double();
		return bounds;
	}

	// Get starting x of the arrowed line.
	public double getX(int startBP, double pixelStart, double bpPerPixel) {
		return (getMinTarget()-startBP) / bpPerPixel + pixelStart; 
	}

	public void paintLine (Graphics2D g2, double bpPerPixel, int startBP, int pixelStart) {
		double x1 = getX(startBP, pixelStart, bpPerPixel);
		double x2 = fullTargetWidth/bpPerPixel;
		Rectangle2D dotLine = new Rectangle2D.Double(x1, bounds.y, x2, 1);
		g2.setColor(Color.gray);
		BasicStroke saveStroke = (BasicStroke) g2.getStroke();
		g2.setStroke( new BasicStroke( 1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[] {2f}, 0f) );
		g2.draw(dotLine);
		g2.setStroke(saveStroke);
	}

	public void paintName(Graphics2D g2, FontMetrics fm, Color fontColor, 
			double vertSpace, double bpPerPixel) 
	{
		String n = queryName;//hd.getName(); // mdb changed 1/9/09 for pseudo-pseudo closeup
		double w = fullTargetWidth/bpPerPixel; 
		double x = bounds.x + (w - fm.stringWidth(n)) / 2.0;
		double y = bounds.y - vertSpace;
		g2.setPaint(fontColor);
		g2.setFont(fm.getFont());
		g2.drawString(n,(float)x,(float)y);
	}

	public void paint(Graphics2D g2) {
		if (!selected) 
			g2.setPaint(lineColor());
		else
			g2.setPaint(/*Color.gray*/lineColor().brighter());	// mdb changed 6/26/09
		g2.draw(hitLine);
		g2.fill(hitLine);
		
		double startOffsetWidth = 0;
		double endOffsetWidth = 0;
		Rectangle2D hb = hitLine.getBounds();
		double hbWidth = hb.getWidth() - startOffsetWidth - endOffsetWidth;
		double hbX = hb.getX() + startOffsetWidth;
		Line2D.Double line = new Line2D.Double();
		line.y1 = hb.getY()+ ( (hb.getHeight() - CloseUpComponent.MISS_HEIGHT) / 2.0 );
		line.y2 = line.y1 + CloseUpComponent.MISS_HEIGHT;
		
		if (!selected) 
			g2.setPaint(CloseUpComponent.missColor.darker());
		else // mdb added 6/26/09
			g2.setPaint(CloseUpComponent.missColor);
		for (int i = 0; i < misses.length; i++) {
			line.x1 = line.x2 = misses[i] * hbWidth + hbX;
			g2.draw(line);
		}

		line.y1 = hb.getY() + ( (hb.getHeight() - CloseUpComponent.DELETE_HEIGHT) / 2.0 );
		line.y2 = line.y1 + CloseUpComponent.DELETE_HEIGHT;
		g2.setPaint(CloseUpComponent.deleteColor);
		for (int i = 0; i < deletes.length; i++) {
			line.x1 = line.x2 = deletes[i] * hbWidth + hbX;
			g2.draw(line);
		}

		double xOffset = hbX - CloseUpComponent.INSERT_DIMENSION.width/2.0;
		Arrow arrow = new Arrow(CloseUpComponent.INSERT_DIMENSION);
		line.y1 = hb.getY() + (hb.getHeight()/2.0) - CloseUpComponent.INSERT_OFFSET - CloseUpComponent.INSERT_DIMENSION.height;
		g2.setPaint(CloseUpComponent.insertColor);
		for (int i = 0; i < inserts.length; i++) {
			line.x1 = inserts[i] * hbWidth + xOffset;
			arrow.paint(g2,line.x1,line.y1,Arrow.POINT_DOWN);
		}
	}

	//protected abstract Color lineColor(); // mdb removed 7/25/07 #121
    protected Color lineColor() { // mdb added 7/25/07 #121
    	return CloseUpComponent.markerColor;
    	//return CloseUpComponent.besColor;
    }

	public Dimension getSize(FontMetrics fm) { return new Dimension(query.getWidth(fm),fm.getHeight()*13); }
	public long getID() { return hd.getID(); }
	public int getMinTarget () { return tStart < tEnd ? tStart : tEnd; }
	public int getMaxTarget () { return tStart > tEnd ? tStart : tEnd; }
	public int getWidthTarget () { return fullTargetWidth; }
	private int hitWidth() { return qEnd - qStart; }
	public void setLayer (int layer) { this.layer = layer + 1; }
	public int getLayer () { return this.layer; }
	public void setSelected (boolean status) { this.selected = status; }
	private boolean pointLeft() { return query.isPlus() != target.isPlus(); }
	public double getEvalue() { return hd.getEvalue(); }
	public double getPctid() { return hd.getPctid(); }
	public boolean contains(Point2D p) { return bounds.contains(p); }
	public int getStart() { return tStart; }
	public int getEnd() { return tEnd; }

	public boolean equals(Object obj) {
		return obj instanceof HitAlignment && ((HitAlignment)obj).getID() == getID();
	}
}
