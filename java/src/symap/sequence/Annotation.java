package symap.sequence;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Stroke;
import java.awt.geom.Rectangle2D;
import java.util.Vector;

import props.PropertiesReader;

import java.util.Comparator;
import java.util.TreeMap;

import symap.Globals;
import symap.closeup.TextShowInfo;
import symap.mapper.Mapper;
import symap.closeup.SeqData;
import util.ErrorReport;
import util.Utilities;

/**
 * Annotation is used for storing and painting an annotation graphics to the screen.
 * An annotation can be a Gene, Exon, Gap, Centromere
 * Drawing the annotation requires calling setRectangle() first.
 */
public class Annotation {
	private Sequence seqObj;
	private int itype;
	private int start, end;						
	private String description;
	private String strGeneNum, tag, fullTag; 
	private boolean bStrandPos;
	private int gene_idx=0;  	// If this is an exon, it is the gene_idx (pseudo_annot.idx) that it belongs to
	private int annot_idx=0; 	// pseudo_annot.idx
	private int genenum=0;	 	// CAS517 for sorting in PseudoData
	
	private Rectangle2D.Double rect;
	private Rectangle2D.Double hoverGeneRect; 	// CAS515 so hover cover for gene covers full width of exon
	
	private Vector <Annotation> exonVec = null; // CAS545 determine during SeqPool load
	private String exonList=null; 				// CAS512 build first time of popup
	private String hitListStr1=null;  			// CAS517 add HitList to popup
	private String hitListStr2=null;			// CAS543 add HitList for other side to popup
	
	private boolean bGeneLineOpt=false; 		// Show line on all genes; CAS520 add
	
	private boolean bHighPopup=false;			// Highlight gene if popup; CAS544 add
	private boolean isPopup=false; 				// Gene has popup - highlight if bHighPopup; CAS544 add
	private boolean isHitPopup=false;			// Hit Popup highlights gene too, though gene Popup has precedence
	
	private boolean isSelectedGene=false; 		// Gene is filtered; CAS545 add
	private boolean isConserved=false;			// Show all conserved genes; CAS545 add
	
	/**
	 * Creates a new Annotation instance setting the description values, color, and draw method based on type.
	 * SeqPool.setSequence
	 */
	public Annotation(Sequence seqObj, String desc, int itype, int start, int end, String strand, 
			String dbtag, int gene_idx, int idx, int genenum, int numhits) {
		this.seqObj = seqObj;
		this.itype = itype;
		this.start = start;
		this.end = end;
		this.bStrandPos = (strand == null || !strand.equals("-"));
		this.description = desc;  
		this.gene_idx = gene_idx;
		this.annot_idx = idx;
		this.genenum = genenum;
	
		// see backend.AnnotLoadPost.computeTags for formatting
		// CAS512 add pseudo_annot.gene_idx so exon is mapped to the gene; need reload annot
		// CAS515 merge tag and genenum in a more readable format; CAS517 add genenum and suffix to tag in AnnotPost; 
		// CAS518 add total exon length (no change to parsing); CAS520 add h numhits, then removed because hard to see in display.
		// CAS534/4 changed tag again
		
		if (genenum==0) { 
			if (!dbtag.startsWith("Exon")) this.fullTag = this.tag = Globals.exonTag + dbtag; // new
			else 						   this.fullTag = this.tag = dbtag;	// old start with Exon
		}
		else {
			tag =  		 Utilities.convertTag(dbtag); 			// coverts old to new DB: geneNum (n exon-len)
			strGeneNum = Utilities.getGenenumFromDBtag(tag);	// extracts geneNum.suffix only
			fullTag =    Utilities.createFullTagFromDBtag(tag); // create: 'Gene' geneTag ('#Exons' n exon-len)
			exonVec = new Vector <Annotation> ();
		}
		
		rect = new Rectangle2D.Double();
		hoverGeneRect = new Rectangle2D.Double();
	}
	public void addExon(Annotation aObj) {exonVec.add(aObj);} // CAS545 determined during SeqPool load of data
	/**
	 * DRAW sets up the rectangle; called in Sequence.build(); CAS515 ordered lines to be more logical
	 */
	public void setRectangle(
			Rectangle2D boundry,      // center of chromosome rectangle (rect.x+1,rect.y,rect.width-2,rect.height)
			long startBP, long endBP, // display start and end of chromosome 
			double bpPerPixel, double dwidth, double hoverWidth, boolean flip, 
			int offset, boolean bGeneLineOpt, boolean bHighPopup) // CAS517 offset 
	{
		this.bGeneLineOpt=bGeneLineOpt;
		this.bHighPopup=bHighPopup;
		
		double x, y, height;
		double chrX=boundry.getX(), upChrY=boundry.getY(), chrHeight=boundry.getHeight(), chrWidth=boundry.getWidth();
		double lowChrY = upChrY + chrHeight; // lowest chromosome edge
		
		long ts, te;
		 
		if (start > end) {
			ts = end;
			te = start;
		} else {
			ts = start;
			te = end;
		}
		if (ts < startBP)	ts = startBP;	
		if (te > endBP)		te = endBP;		
		
		if (!flip) 	y = (ts - startBP) / bpPerPixel + upChrY; 
		else 		y = (endBP - ts)   / bpPerPixel + upChrY;		
		
		height = (te - ts) / bpPerPixel;
		if (flip) y -= height; 
		
		x = chrX + (chrWidth - dwidth)/2; // set x before modify width 
		if (offset!=0) x = x-offset;	 // CAS517 for overlapping genes, 0 for not
		
		if (chrWidth < dwidth) dwidth = chrWidth;
		
		double lowY = y + height;
		if (y < upChrY)      y = upChrY;
		if (lowY > lowChrY)  height = lowChrY - y;
		
		if (lowY >= upChrY && y <= lowChrY) rect.setRect(x, y, dwidth, height);
		else 								rect.setRect(0, 0, 0, 0);
		
		if (hoverWidth!=0) { // gene
			double xx = chrX + (chrWidth-hoverWidth)/2; // set x before modify width
			if (offset!=0) xx= xx-offset;				// CAS517 for overlapping genes
			
			if (chrWidth < hoverWidth) hoverWidth = chrWidth;
		
			if (lowY >= upChrY && y <= lowChrY) hoverGeneRect.setRect(xx, y, hoverWidth, height);
			else 								hoverGeneRect.setRect(0, 0, 0, 0); 
		}									
	}
	
	public void clear() {// clears the rectangle coordinates so that the annotation will not be painted 
		rect.setRect(0, 0, 0, 0);
		hoverGeneRect.setRect(0, 0, 0, 0);
	}
	public double getY() { // returns the midpoint of the mark; called after calling setRectangle 
		return rect.getY() + (rect.getHeight() / 2.0);
	}
	public double getY1() {return rect.getY();}

	public double getY2() {return rect.getY() + rect.getHeight() - 1;} // bottom y coord
	 
	public boolean isVisible() {// true if after calling setRectangle 
		return rect.getWidth() > 0 && rect.getHeight() > 0;
	}
	
	public void setOffset(double x, double y) { // Offset the whole Annotation if it's visible. 
		if (isVisible()) {
			rect.x -= x;
			rect.y -= y;
			
			if (hoverGeneRect.getX()!=0 && hoverGeneRect.getY() !=0) {
				hoverGeneRect.x -= x;
				hoverGeneRect.y -= y;
			}
		}
	}

	public int getType() {return itype;}
	public boolean isGene() 	  { return itype == GENE_INT; }
	public boolean isExon() 	  { return itype == EXON_INT; }   
	public boolean isGap() 		  { return itype == GAP_INT; }
	public boolean isCentromere() { return itype == CENTROMERE_INT; }
	public boolean isConserved() {return isConserved;}
	
	public int getStart() {return start;}
	public int getEnd() {return end;}
	public int getGeneIdx() { return gene_idx;} 			// CAS517 for Sequence to know if overlap exon
	public int getAnnoIdx() { return annot_idx;}			// ditto
	public int getGeneLen()	{ return Math.abs(end-start)+1;} // ditto
	public int getGeneNum() { return genenum;}				// CAS517 for sorting in PseudoData
	public String getFullGeneNum() {return strGeneNum;}     // CAS545 add
	public String getTag() {return tag;} 					// CAS512 add for HelpBox; CAS543 called for Exons
	public boolean isStrandPos() {return bStrandPos;}		// for seq-seq closeup
	
	public void paintComponent(Graphics2D g2) {
		if (itype >= numTypes) return;
		
		g2.setPaint(getColor());
		
		if (itype == CENTROMERE_INT) { 	
			Stroke oldstroke = g2.getStroke();
			g2.setStroke(new BasicStroke(crossWidth)/*crossStroke*/); 
			
			g2.drawLine((int)rect.x, (int)rect.y, (int)rect.x + (int)rect.width, (int)rect.y + (int)rect.height);
			g2.drawLine((int)rect.x, (int)rect.y + (int)rect.height, (int)rect.x + (int)rect.width, (int)rect.y);
			
			g2.setStroke(oldstroke);
		} 
		else { // (1) Gene, Exon, Gap (2) TICK or RECT 
			if (rect.height >= 2) { // Only draw full rectangle if it is large enough to see.
				g2.fillRect((int)rect.x, (int)rect.y, (int)rect.width, (int)rect.height);
				
				// black line on top of gene to distinguish closely placed genes; CAS520 add, CAS544 highlight.. 
				if (itype==GENE_INT) {
					if (bGeneLineOpt || isPopup || isSelectedGene) {
						Stroke oldstroke = g2.getStroke();
						g2.setStroke(new BasicStroke(2)); 
						
						int w = 10; // 15=width of exon from sequence.properties
						g2.drawLine((int)rect.x-w, (int)rect.y, ((int)rect.x + w+3), (int)rect.y);
						g2.setStroke(oldstroke);
					}
				}
			}
			else 	// Else draw as line 
				g2.drawLine((int)rect.x, (int)rect.y, (int)rect.x + (int)rect.width, (int)rect.y); 
		}
	}
	private Color getColor() {
		if (itype == EXON_INT) {
			if (bHighPopup) {
				if (isPopup)    return geneHighColor; // CAS545 only exon highlighted when Gene Filter
				if (isHitPopup) return Mapper.pseudoLineHoverColor; // CAS545 
			}
			if (isSelectedGene) return geneHighColor; // CAS546 add here too
			
			if (isConserved)    return geneHighColor; 
			
			if (bStrandPos)     return exonColorP;	
			else                return exonColorN;
		}
		
		if (itype == GENE_INT)		{
			if (bHighPopup) {
				if (isPopup)  	return geneHighColor;
				if (isHitPopup) return Mapper.pseudoLineHoverColor; // CAS545 
			}	
			if (isSelectedGene) return geneHighColor;
			
			return geneColor;
		}
		if (itype == GAP_INT)			return gapColor;
		if (itype == CENTROMERE_INT)	return centromereColor;

		return Color.black; 
	}
	
	/*******************************************
	 * XXX hover and box and closeup info
	 */
	public String getShortDescription() {return description;} // For CloseUpDialog - what is in the name field 
	
	public boolean hasShortDescription() {	// Sequence.build()
		return description != null && description.length() > 0;
	}
	
	// Display in box beside genes when Show Annotation Description 
	public Vector<String> getVectorDescription() {
		Vector<String> out = new Vector<String>();
		out.add(fullTag);				// CAS512 add tag	
		out.add(getLocLong(" "));
		for (String token : description.split(";")) 
				out.add( token.trim() ); // CAS501 added trim
		return out;
	}
	public String getLongDescription() {// Shown in info text box when mouse over object 
		String longDes;
		
		if (itype == GENE_INT) {
			String xDesc = description.replaceAll(";", "\n");   	 // CAS503
			longDes = fullTag +  "\n" + getLocLong("\n") + "\n" + xDesc;	 // CAS512 add tag	
		}
		else if (itype == EXON_INT)			longDes = tag + " " +getLocLong("\n"); // CAS517 tag +  "\n" + getLocLong("\n") + "\n" + xDesc;
		else if (itype == GAP_INT) 			longDes = "Gap\n" + getLocLong("\n"); // CAS504 add getLoc
		else if (itype == CENTROMERE_INT) 	longDes = "Centromere\n" + getLocLong("\n");
		else								longDes = "Name " + description;
		
		return longDes;
	}
	private String getLocLong(String delim) { // CAS504; used in above two
		return SeqData.coordsStr(bStrandPos, start, end); // CAS512 update
	}
	
	public boolean hasHitList() {return hitListStr1!=null; } //  CAS517 added this
	public void setHitList(String hList) {hitListStr1=hList;} 
	public boolean hasHitList2() {return hitListStr2!=null; } //  CAS543 added this
	public void setHitList2(String hList) {hitListStr2=hList;} 
	
	// for popup
	public String strExon(int parent_idx) {
		if (isExon() && this.gene_idx==parent_idx) {
			return tag + ":" + start+ ":" + end; 
		}
		return null;
	}
	public void setExonList(Vector <Annotation> annoVec) { // CAS512 add ExonList to popup
		if (exonList!=null) return;
		try {
			String list=null;
			
			TreeMap <Integer, String> stMap = new TreeMap <Integer, String> ();
			for (Annotation ad : annoVec) {
				String x = ad.strExon(annot_idx); // method of "ad" annotation
				if (x!=null) stMap.put(ad.start, x);
			}
			for (String val : stMap.values()) {
				if (list==null) list = val;
				else  list += "," + val;
			}
			exonList = list;
		}
		catch (Exception e) {ErrorReport.print(e, "Get exon list for " + gene_idx);}
	}
	
	// determines if the rectangle of this annotation contains the point p. 
	public boolean contains(Point p) {
		if (itype == GENE_INT) {
			return hoverGeneRect.contains(p.getX(), p.getY());
		}
		return rect.contains(p.getX(), p.getY());
	}
	// popup from clicking gene; CAS503, CAS516 add, CAS517 add hitList; CAS543 use Utilities for tag
	public void popupDesc(Component parentFrame, String name, String chr) { 
	try {
		if (!tag.contains("(")) return;
		setIsPopup(true);
		
		String [] tok = Utilities.getGeneExonFromTag(tag); // see backend.AnnotLoadPost.computeTags
		
		String msg = null;
		for (String x : getVectorDescription()) {
			if (msg==null) msg = tok[0] + " " + chr + "\n";
			else msg += x + "\n";
		}
		
		if (exonList!=null) {
			msg += "\n" + tok[1] + "\n" + SeqData.formatExon(exonList) + "\n";
		}
		if (hitListStr1!=null) {
			String [] hitWires = hitListStr1.split(";");
			for (String h : hitWires) msg += SeqData.formatHit(h);
		}
		if (hitListStr2!=null) {
			String [] hitWires = hitListStr2.split(";");
			for (String h : hitWires) msg += SeqData.formatHit(h);
		}
		if (Globals.TRACE) msg += "\nIdx=" + annot_idx + "\n"; // CAS540
		
		String title =  name + "; " + tok[0];
		
		new TextShowInfo(parentFrame, title, msg, this);
		// CAS544 obsolete else descBox.popupDesc(title, msg); // aligns it with yellow box
	} 
	catch (Exception e) {ErrorReport.print(e, "Creating genepopup");}
	}
	
	public String toString() {
		String x = String.format("%-12s ", seqObj.getFullName());
		x += String.format("%-8s [Rect %.2f,%.2f,%.2f,%.2f] [Seq: %d,%d]", fullTag,
				rect.x,rect.y,rect.width, rect.height, start, end);
		if (itype==GENE_INT) 
			x += String.format(" [HRect %.2f,%.2f,%.2f,%.2f]",
					hoverGeneRect.x,hoverGeneRect.y,hoverGeneRect.width, hoverGeneRect.height);
		return x;
	}
	
	/******************************************************************
	 * XXX settings
	 */
	
	public int isMatchGeneN(String gene) {// CAS545 add for Sequence Filter Gene# search
		if  (!strGeneNum.equals(gene)) return -1;
		return start + ((end-start)/2);
	}
		
	public void setIsPopup(boolean b) { // Called for gene only on popup; Annotation=true; Sequence when -tt; TextShowInfo=false
		isPopup=b; 
		if (exonVec!=null) 
			for (Annotation ad : exonVec) ad.isPopup=b; // CAS545
	}
	public void setIsHitPopup(boolean b) { // Called for gene when searched on (not turned off)
		isHitPopup=b; 
		if (exonVec!=null) 
			for (Annotation ad : exonVec) ad.isHitPopup=b; // CAS545
	}
	public void setIsSelectedGene(boolean b) { // Called for filtered gene (never turned off)
		isSelectedGene=b; 
		if (exonVec!=null) 
			for (Annotation ad : exonVec) ad.isSelectedGene=b; // CAS545
	}
	public void setIsConserved(boolean b) { // Called for conserved turned on/off
		isConserved=b; 
		if (exonVec!=null) 
			for (Annotation ad : exonVec) ad.isConserved=b; // CAS545
	}
	
	// CAS531 add for TextShowSeq to sort exons
	public static Comparator<Annotation> getExonStartComparator() {
		return new Comparator<Annotation>() {
			public int compare(Annotation a1, Annotation a2) {
				return a2.start - a1.start; 
			}
		};
	}
	/***********************************************************************
	 * Values used in the database to represent annotation types.
	 * CAS543 remove framemarker, hit and sygene
	 */
	
	private static final float crossWidth= (float) 2.0; // the width of the line in the cross
	
	public static final int GENE_INT 		= 0;
	public static final int EXON_INT 		= 1; 
	public static final int GAP_INT 		= 2;
	public static final int CENTROMERE_INT 	= 3;
	public static final int numTypes 		= 4; 
	
	// accessed and changed by ColorDialog - do not change
	public static Color geneColor;
	public static Color gapColor;
	public static Color centromereColor;
	public static Color exonColorP; 				// CAS517 add P/N
	public static Color exonColorN;
	public static Color geneHighColor;				// CAS544 add
	
	static {
		PropertiesReader props = new PropertiesReader(Globals.class.getResource("/properties/annotation.properties"));
		geneColor = props.getColor("geneColor");
		gapColor = props.getColor("gapColor");
		centromereColor = props.getColor("centromereColor");
		exonColorP = props.getColor("exonColorP"); 
		exonColorN = props.getColor("exonColorN"); 
		geneHighColor = props.getColor("geneHighColor"); 
	}
}
