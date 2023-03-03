package symap.closeup;

import java.awt.BasicStroke;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Dimension2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import javax.swing.Icon;
import java.awt.geom.Point2D;

import symap.mapper.HitData;

/****************************************************
 * Display Hit Alignments; created in AlignPool; drawn from CloseUpComponent
 */
public class HitAlignment implements Comparable<HitAlignment> { 
	public static final String tokS="S",     tokO="N";
	private final       String tokpS="(S) ", tokpO="(N) ";
	private final boolean drawLine=false;
	
	private HitData hitDataObj;
	private String proj1, proj2; 
	private int sStart, sEnd, oStart, oEnd; // non-aligned ends
	private int alignWidth, startOffset;
	private SeqData otherData, selectData, matchData; 	 // aligned sequences
	private String scoreStr, numSubHit="";
	
	private double[] misses, inserts, deletes;
	private boolean selected;
	private ArrowLine hitLine;
	private Rectangle2D.Double bounds;
	private int layer;
	
	public HitAlignment(HitData hd, String proj1, String proj2,
			SeqData selectAlign, SeqData otherAlign, SeqData match,  
			int selectStart, int selectEnd, int otherStart, int otherEnd, 
			int fullAlignWidth, int offset, String scoreStr, String numSubHit) 
	{
		this.hitDataObj = hd;	
		this.proj1 = proj1;
		this.proj2 = proj2;
		this.sStart = selectStart;
		this.sEnd = selectEnd;
		this.oStart = otherStart;
		this.oEnd = otherEnd;
		this.alignWidth = fullAlignWidth;
		this.selectData = selectAlign;
		this.otherData = otherAlign;
		this.matchData = match;
		this.startOffset = offset;
		this.scoreStr = scoreStr;
		this.numSubHit = numSubHit;
		layer = 1;
		
		// these calculate where to put marks along the hit line of graphics
		misses =  SeqData.getQueryMisses(selectAlign, otherAlign);
		inserts = SeqData.getQueryInserts(selectAlign, otherAlign);
		deletes = SeqData.getQueryDeletes(selectAlign, otherAlign);
		selected = false;
	}
	// for reverse text alignments
	public HitAlignment(HitAlignment ha,
			SeqData selectAlign, SeqData otherAlign, SeqData match,  
			int selectStart, int selectEnd, int otherStart, int otherEnd, 
			int fullAlignWidth, int offset, String scoreStr) 
	{
		this.hitDataObj = ha.hitDataObj;	
		this.proj1 = ha.proj1;
		this.proj2 = ha.proj2;
		this.sStart = selectStart;
		this.sEnd = selectEnd;
		this.oStart = otherStart;
		this.oEnd = otherEnd;
		this.alignWidth = fullAlignWidth;
		this.selectData = selectAlign;
		this.otherData = otherAlign;
		this.matchData = match;
		this.startOffset = offset;
		this.scoreStr = scoreStr;
		this.numSubHit = ha.numSubHit;
		layer = 1;
	}
	public String getHitName() {return hitDataObj.getName();}
	public String toString() {return toText(true, proj1, proj2);  }

	// for TextPopup and closeup
	public String toText(boolean isClose, String p1, String p2) {
		String sSeq = selectData.toString(), nSeq = otherData.toString();
		String mSeq = matchData.toString();
		
		String sCoord = SeqData.coordsStr(selectData.getStrand(), sStart, sEnd);// CAS504 format 
		String nCoord = SeqData.coordsStr(otherData.getStrand(), oStart, oEnd);
		String hitN = hitDataObj.getName() + "." + numSubHit;
		
		if (isClose) {
			int max = hitN.length();
			max = Math.max(max, (p1 + tokpS).length());
			max = Math.max(max, (p2 + tokpO).length());
			max += 1;
			String format =  "%-" + max + "s "; 
			
			String desc = String.format(format, hitN) + scoreStr;
			String sName = String.format(format, p1 + tokpS);
			String nName = String.format(format, p2 + tokpO);
			
			return "\n " + desc  + 
				   "\n " + sName + sCoord  + 
				   "\n " + nName + nCoord + 
				   "\n" +
				   "\n " + tokS + " " + sSeq + 
				   "\n   " + mSeq + 
				   "\n " + tokO + " " + nSeq + " \n"; 
		}
		else { // the text formatter puts the S and N before each align name
			String sName = String.format("%s", p1 + tokpS);
			String nName = String.format("%s", p2 + tokpO);
			return  (hitN + "  " + scoreStr) + "\t" + sName + sCoord + "  " + nName + nCoord + 
					"\t" 	+ sSeq + "\t" + mSeq + "\t" + nSeq;
		}
	}
	
	public String toTextAA(String p1, String p2) {
		String qd = selectData.toString(), td = otherData.toString();
		String mk = matchData.toString();
		
		String qn = p1 + tokpS;
		String tn = p2 + tokpO;
		
		String qc = SeqData.coordsStr(sStart, sEnd);// CAS504 format
		String tc = SeqData.coordsStr(oStart, oEnd);
		
		String desc = hitDataObj.getName() + "." + numSubHit;
		
		return desc  + "\t" + qn + qc + "   "+ tn + tc + "\t" 	+ qd + "\t" + mk + "\t" + td;
	}
	// for TextShowInfo
	public int [] getCoords( ) {
		int [] coords = new int[4];
		coords[0]=sStart+startOffset;
		coords[1]=sEnd;
		coords[2]=oStart+startOffset;
		coords[3]=oEnd;
		return coords;
	}
	/*******************************************************************/
	public void setBounds(int startBP, double pixelStart, double bpPerPixel, double y) {
		setBounds( getX(startBP, pixelStart, bpPerPixel), y, bpPerPixel );
	}

	private void setBounds(double x, double y, double bpPerPixel) {
		if (bounds == null) {
			hitLine = new ArrowLine();
			bounds  = new Rectangle2D.Double();
			bounds.height = CloseUpComponent.HIT_HEIGHT;
		}
		bounds.y = y;
		bounds.x = x;
		bounds.width = alignWidth / bpPerPixel; // CAS531 was (sEnd - sStart) which was non-aligned
	
		hitLine.setBounds(bounds, CloseUpComponent.LINE_HEIGHT, CloseUpComponent.ARROW_DIM,
				ArrowLine.NO_ARROW); // CAS531 needs clarification pointLeft() ? ArrowLine.LEFT_ARROW : ArrowLine.RIGHT_ARROW);
	}

	public Rectangle2D getBounds() {
		if (bounds == null) return new Rectangle2D.Double();
		return bounds;
	}
	// CAS531 this does not add anything; bpPerPixel=1, startBP=start of graphics, pixelStart=HORZ_BORDER
	public void paintLine (Graphics2D g2, double bpPerPixel, int startBP, int pixelStart) {
		if (drawLine) {
			double x1 = getX(startBP, pixelStart, bpPerPixel);
			double x2 = alignWidth/bpPerPixel;
			Rectangle2D dotLine = new Rectangle2D.Double(x1, bounds.y, x2, 1);
			BasicStroke saveStroke = (BasicStroke) g2.getStroke();
			
			g2.setColor(Color.gray);
			g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[] {2f}, 0f));
			g2.draw(dotLine);
			g2.setStroke(saveStroke);
		}
	}

	public void paintName(Graphics2D g2, FontMetrics fm, Color fontColor, double vertSpace, double bpPerPixel) {
		String m = hitDataObj.getName().replace("Hit ", "");
		String n = m + "." + numSubHit.substring(0, numSubHit.indexOf("/")); // CAS531 was otherName
		double w = alignWidth/bpPerPixel; 
		double x = bounds.x + (w - fm.stringWidth(n)) / 2.0;
		double y = bounds.y - vertSpace;
		g2.setPaint(fontColor);
		g2.setFont(fm.getFont());
		g2.drawString(n,(float)x,(float)y);
	}

	public void paint(Graphics2D g2) {
		Color c = CloseUpComponent.hitColor;
		if (!selected) 	g2.setPaint(c);
		else			g2.setPaint(c.brighter());	
		
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
		if (!selected) 	g2.setPaint(CloseUpComponent.missColor.darker());
		else 			g2.setPaint(CloseUpComponent.missColor);
		for (int i = 0; i < misses.length; i++) {
			line.x1 = line.x2 = misses[i] * hbWidth + hbX;
			g2.draw(line);
		}

		line.y1 = hb.getY() + ( (hb.getHeight() - CloseUpComponent.DELETE_HEIGHT) / 2.0 );
		line.y2 = line.y1 + CloseUpComponent.DELETE_HEIGHT;
		if (!selected) 	g2.setPaint(CloseUpComponent.deleteColor.darker());
		else 			g2.setPaint(CloseUpComponent.deleteColor);
		for (int i = 0; i < deletes.length; i++) {
			line.x1 = line.x2 = deletes[i] * hbWidth + hbX;
			g2.draw(line);
		}

		double xOffset = hbX - CloseUpComponent.INSERT_DIM.width/2.0;
		InsIcon arrow = new InsIcon(CloseUpComponent.INSERT_DIM);
		line.y1 = hb.getY() + (hb.getHeight()/2.0) - CloseUpComponent.INSERT_OFFSET - CloseUpComponent.INSERT_DIM.height;
		if (!selected) 	g2.setPaint(CloseUpComponent.insertColor.darker());
		else 			g2.setPaint(CloseUpComponent.insertColor);
		for (int i = 0; i < inserts.length; i++) {
			line.x1 = inserts[i] * hbWidth + xOffset;
			arrow.paint(g2,line.x1,line.y1,InsIcon.POINT_DOWN);
		}
	}
	// Get starting x of the arrowed line.
	private double getX(int startBP, double pixelStart, double bpPerPixel) {
		return (getMinSelectCoord()-startBP) / bpPerPixel + pixelStart; 
	}
	//private boolean pointLeft() { return selectData.isPlus() != otherData.isPlus(); }
	
	private long getID() 				{return hitDataObj.getID(); }
	private int getMinSelectCoord () 	{return (sStart < sEnd) ? sStart+startOffset : sEnd+startOffset; } // CAS531 add offset
	
	// CloseUpComponetn
	public int getWidthAlign () 		{return alignWidth; }
	public void setLayer (int layer) 	{this.layer = layer + 1; }
	public int getLayer () 			 	{return this.layer; }
	public boolean contains(Point2D p) 	{return bounds.contains(p); }
	
	// CloseUpDialog
	public void setSelected (boolean status) {this.selected = status; }
	public int getAend()   				{return sStart + alignWidth; }
	
	// AlignPool.alignSubHitRev, CloseUpComponent.assignHitLayers, CloseUpDialog.setvView, HitAlignment.compareTo
	public int getSstart() 				{return sStart; }
	public int getSend()   				{return sEnd; }
	
	// AlignPool.alignSubHitRev
	public int getOstart() 				{return oStart; }
	public int getOend()   				{return oEnd; }
	public String getSelectSeq() 		{return selectData.getSeq();}
	public String getOtherSeq() 		{return otherData.getSeq();}
	public char getSelectStrand() 		{return selectData.getStrand();}
	public char getOtherStrand() 		{return otherData.getStrand();}
	public String getSubHitName()		{return getHitName() + "." + numSubHit;}
	
	public boolean equals(Object obj) {
		return obj instanceof HitAlignment && ((HitAlignment)obj).getID() == getID();
	}
	public int compareTo(HitAlignment ha) {
		return getSstart() - ha.getSend();
	}
	/****************************************************************
	 * Insert Icon; Arrow down, CAS533 was a separate file in util
	 */
	public class InsIcon implements Icon {
	    public static final double POINT_DOWN  = 0;
	    private Dimension2D dim;
	    private Line2D line = new Line2D.Double();

	    public InsIcon(Dimension2D dim) {this.dim = dim;}
	    
	    public int getIconHeight() {return (int)dim.getHeight();}

	    public int getIconWidth() {return (int)dim.getWidth();}
	    
	    public void paintIcon(Component c, Graphics g, int x, int y) {
	    	paint((Graphics2D)g,(double)x,(double)y, POINT_DOWN);
	    }
	    public void paint(Graphics2D g2, double x, double y, double direction) {
			AffineTransform saveAt = g2.getTransform();
		
			g2.rotate(direction,x+dim.getWidth()/2.0,y+dim.getHeight()/2.0);
		
			line.setLine(x,y,x + dim.getWidth()/2.0,y+dim.getHeight());
			g2.draw(line);
			line.setLine(line.getX2(),line.getY2(),x+dim.getWidth(),y);
			g2.draw(line);
		
			g2.setTransform(saveAt);
	    }
	}
}
