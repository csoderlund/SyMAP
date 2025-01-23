package symap.sequence;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Stroke;
import java.awt.geom.Rectangle2D;
import java.util.Vector;

import props.PropertiesReader;

import java.util.Comparator;
import java.util.TreeMap;

import symap.Globals;
import symap.mapper.Mapper;
import symap.closeup.TextShowInfo;
import symap.closeup.SeqDataInfo;
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
	private String hitListStr1=null;  			// CAS517 add to popup; computed in sequence.setExonList using coords 
	private String hitListStr2=null;			// CAS543 add HitList for other side to popup
	private double lastY=0.0, lastX=0.0;		// CAS551 if Last gene starts at same Y, then stagger Gene#
	
	private boolean bGeneLineOpt=false; 		// Show line on all genes; CAS520 add
	private boolean bHighPopup=false;			// Highlight gene if popup; CAS544 add
	private boolean bShowGeneNum=true;			// show text genenum; CAS551 add
	private boolean isPopup=false; 				// Gene has popup - highlight if bHighPopup; CAS544 add
	private boolean isHitPopup=false;			// Hit Popup highlights gene too, though gene Popup has precedence
	
	private boolean isSelectedGene=false; 		// Gene is filtered; CAS545 add
	private boolean isHitg2=false;			    // Show all conserved genes; CAS545 add
	
	/**
	 * Creates a new Annotation instance setting the description values, color, and draw method based on type.
	 * SeqPool.setSequence
	 */
	protected Annotation(Sequence seqObj, String desc, int itype, int start, int end, String strand, 
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
	
		// see backend.AnnotLoadPost.computeTags for formatting; changed tag CAS512, 515, 517, 518, 534
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
	protected void addExon(Annotation aObj) {exonVec.add(aObj);} // CAS545 determined during SeqPool load of data
	
	public Vector <Annotation> getExonVec() { return exonVec;} // CAS560 for closeup.SeqDataInfo hit popup
	/**
	 * DRAW sets up the rectangle; called in Sequence.build(); CAS515 ordered lines to be more logical
	 */
	protected void setRectangle(
			Rectangle2D boundry,      // center of chromosome rectangle (rect.x+1,rect.y,rect.width-2,rect.height)
			int startBP, int endBP, // display start and end of chromosome 
			double bpPerPixel, double dwidth, double hoverWidth, boolean flip, int offset, 
			boolean bGeneLineOpt, boolean bShowGeneNum, boolean bHighPopup) // CAS517 offset; CAS551 showGeneNum
	{
		this.bGeneLineOpt=bGeneLineOpt; // used these 3 in paintComponent
		this.bShowGeneNum=bShowGeneNum; 
		this.bHighPopup=bHighPopup;
		
		double x, y, height;
		double chrX=boundry.getX(), upChrY=boundry.getY(), chrHeight=boundry.getHeight(), chrWidth=boundry.getWidth();
		double lowChrY = upChrY + chrHeight; // lowest chromosome edge
		
		int ts, te;
		 
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
	
	public void paintComponent(Graphics2D g2) { // called from Sequence.paintComponent
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
				
				if (itype==GENE_INT) {// Gene Delimiter; CAS520 add, CAS544 highlight.. 
					if (bGeneLineOpt || isPopup || isSelectedGene) {
						Stroke oldstroke = g2.getStroke();
						g2.setStroke(new BasicStroke(2)); 
						g2.drawLine((int)rect.x-10, (int)rect.y, ((int)rect.x + 13), (int)rect.y);
						g2.setStroke(oldstroke);
					}	
				}
			}
			else 	// Else draw as line 
				g2.drawLine((int)rect.x, (int)rect.y, (int)rect.x + (int)rect.width, (int)rect.y); 
			
			if (itype==GENE_INT && bShowGeneNum) { // CAS551 (Yellow box in Sequence.build)
				 g2.setPaint(Color.black);
				 g2.setFont(Globals.textFont);
				 
				 int x, w=0;
				 if (seqObj.isRef()) x = (int)rect.x+15;
				 else {
					 FontMetrics metrics = g2.getFontMetrics(Globals.textFont);
					 w = metrics.stringWidth(strGeneNum) + 5; // tested stringWidth of 'a' is 7
					 x = (int)rect.x-w;
				 }
				 int y = (int)rect.y +  (Globals.textHeight/2);
				 if (Math.abs(lastY-rect.y)<6) {
					 y+=10; // move it down
					 if (seqObj.isRef()) x = (int)lastX+15;
					 else x = (int)lastX-w;
				 }
				 
				 g2.drawString(strGeneNum, x, y);
			}
		}
	}
	private Color getColor() {
		if (itype == EXON_INT) {
			if (bHighPopup) {
				if (isPopup)    return geneHighColor; // CAS545 only exon highlighted when Gene Filter
				if (isHitPopup) return Mapper.pseudoLineGroupColor; // CAS545; CAS555 change to Group
			}
			if (isSelectedGene) return geneHighColor; // CAS546 add here too
			
			if (isHitg2)    	return geneHighColor; 
			
			if (bStrandPos)     return exonColorP;	
			else                return exonColorN;
		}
		
		if (itype == GENE_INT)		{
			if (bHighPopup) {
				if (isPopup)  	return geneHighColor;
				if (isHitPopup) return Mapper.pseudoLineGroupColor; // CAS545, CAS555 change to group 
			}	
			if (isSelectedGene) return geneHighColor;
			
			return geneColor;
		}
		if (itype == GAP_INT)			return gapColor;
		if (itype == CENTROMERE_INT)	return centromereColor;

		return Color.black; 
	}
	/////////////////////////////////////////////////////////////////////////
	protected void clear() {// clears the rectangle coordinates so that the annotation will not be painted 
		rect.setRect(0, 0, 0, 0);
		hoverGeneRect.setRect(0, 0, 0, 0);
	}
	
	protected double getY1() {return rect.getY();} // same as rect.y
	
	protected void setLastY(Annotation last) {lastY=last.rect.y; lastX=last.rect.x;}; 
	
	protected boolean isVisible() {// true if after calling setRectangle 
		return rect.getWidth() > 0 && rect.getHeight() > 0;
	}
	
	protected void setOffset(double x, double y) { // Offset the whole Annotation if it's visible. 
		if (isVisible()) {
			rect.x -= x;
			rect.y -= y;
			
			if (hoverGeneRect.getX()!=0 && hoverGeneRect.getY() !=0) {
				hoverGeneRect.x -= x;
				hoverGeneRect.y -= y;
			}
		}
	}

	public int getStart() {return start;}
	public int getEnd() {return end;}
	public int getGeneIdx() { return gene_idx;} 			// CAS517 for Sequence to know if overlap exon
	public int getAnnoIdx() { return annot_idx;}			// ditto
	public String getTag() {return tag;} 					// CAS512 add for HelpBox; CAS543 called for Exons
	public boolean isStrandPos() {return bStrandPos;}		// for seq-seq closeup
	protected Color getBorderColor() { return (bStrandPos) ? exonColorP : exonColorN;} // CAS554 for Annotation Boxes
	
	protected int getType() {return itype;}
	protected boolean isGene() 	  { return itype == GENE_INT; }
	protected boolean isExon() 	  { return itype == EXON_INT; }   
	protected boolean isGap() 		  { return itype == GAP_INT; }
	protected boolean isCentromere() { return itype == CENTROMERE_INT; }
	protected boolean isHitg2() {return isHitg2;} // CAS545,555 is g2, hit and gene highlighted
	
	protected int getGeneLen()	{ return Math.abs(end-start)+1;} // ditto
	protected int getGeneNum() { return genenum;}				// CAS517 for sorting in SeqPool
	public String getFullGeneNum() {return strGeneNum;}      // CAS545 has suffix
	
	/*******************************************
	 * XXX hover and box and closeup info
	 */
	
	protected boolean hasDesc() {	// Sequence.build() 
		return description != null && description.length() > 0;
	}
	public String getCloseUpDesc() {// For CloseUpDialog - shown over the gene graphic; CAS548 add geneNum, use DescOnly
		return "Gene #" + strGeneNum + "   " + getIdOnly() + "  " + getDescOnly();
	} 
	
	protected Vector<String> getYellowBoxDesc() { // Shown when the Annotation option is on; CAS548 removed ID=, etc
		Vector<String> out = new Vector<String>();
		out.add(String.format("Gene #%s ",strGeneNum) + SeqData.coordsStrKb(bStrandPos, start, end));
		out.add(getIdOnly());
		out.add(getDescOnly()); // TextBox expect 3 entries
		return out;
	}
	private String getIdOnly() {
		for (String keyVal : description.split(";")) {
			if (keyVal.contains("=")) {
				String [] tok = keyVal.split("=");
				String key = tok[0].toLowerCase();
				if (key.startsWith("id")) {
					if (tok.length==2) return tok[1];
					else return "";
				}
			}
		}
		return "";
	}
	private String getDescOnly() {
		for (String keyVal : description.split(";")) { // NCBI or Ensembl
			if (keyVal.contains("=")) {
				String [] tok = keyVal.split("=");
				String key = tok[0].toLowerCase();
				if (key.startsWith("product") || key.startsWith("desc")) {
					if (tok.length==2) return tok[1];
					else return "";
				}
			}
		}
		for (String keyVal : description.split(";")) { // unknown, take a guess
			if (keyVal.contains("=")) {
				String [] tok = keyVal.split("=");
				String key = tok[0].toLowerCase();
				if (!key.startsWith("id") && !key.startsWith("name") && !key.startsWith("protein")) {
					if (tok.length==2) return tok[1];
					else return "";
				}
			}
		}
		return description;
	}

	private Vector<String> getPopUpDesc() {	// Shown when click on anno; show full descr
		Vector<String> out = new Vector<String>();
		out.add(fullTag);				// CAS512 add tag	
		out.add(getLocLong());	// CAS548 remove loc
		for (String token : description.split(";")) {
			out.add( token.trim() ); // CAS501 added trim
		}
		return out;
	}
	protected String getHoverDesc() {// Shown in info text box when mouse over object; show full desc 
		String longDes;
		
		if (itype == GENE_INT) {
			String xDesc = description.replaceAll(";", "\n");   	 // CAS503
			longDes = fullTag +  "\n" + getLocLong() + "\n" + xDesc;	 // CAS512 add tag	
		}
		else if (itype == EXON_INT)			longDes = tag + " " +getLocLong(); // CAS517 tag +  "\n" + getLocLong("\n") + "\n" + xDesc;
		else if (itype == GAP_INT) 			longDes = "Gap\n" + getLocLong(); // CAS504 add getLoc
		else if (itype == CENTROMERE_INT) 	longDes = "Centromere\n" + getLocLong();
		else								longDes = "Name " + description;
		
		return longDes;
	}
	private String getLocLong() { // CAS504; used in above two
		return SeqData.coordsStr(bStrandPos, start, end); // CAS512 update
	}
	
	protected boolean hasHitList() 			{return hitListStr1!=null; } //  CAS517 added this
	protected void setHitList(String hList) {hitListStr1=hList;}  // Calls 1st time popup
	protected boolean hasHitList2() 		{return hitListStr2!=null; } //  CAS543 added this
	protected void setHitList2(String hList){hitListStr2=hList;} // hits on other side if tracks on both sides 
	
	// for popup
	protected void setExonList() { // CAS512 add ExonList to popup; CAS548 was sending full exonVec when it was here
		if (exonList!=null) return;
		try {
			String list=null;
			
			TreeMap <Integer, String> stMap = new TreeMap <Integer, String> (); // to sort on start
			for (Annotation ex : exonVec) { 
				String x = ex.tag + ":" + ex.start+ ":" + ex.end; ; // accessing exon annotation object
				stMap.put(ex.start, x);
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
	protected boolean contains(Point p) {
		if (itype == GENE_INT) {
			return hoverGeneRect.contains(p.getX(), p.getY());
		}
		return rect.contains(p.getX(), p.getY());
	}
	/*
	 * popup from clicking gene; CAS503, CAS516 add, CAS517 add hitList; CAS543 use Utilities for tag
	 * called from Sequence.popupDesc, 
	 * 		if hitListStr and exonList, not set, they are now and reused
	 * 		calls SeqPool to get list of hits for the gene/chr and scores
	 * 		calls HitData to format hit
	 */
	protected void popupDesc(Component parentFrame, String name, String chr) { 
	try {
		if (!tag.contains("(")) return;
		setIsPopup(true);
		
		String [] tok = Utilities.getGeneExonFromTag(tag); // tok[0] Gene #X  tok[1] #Exon=n nnnnbp
		
		String msg = null;
		for (String x : getPopUpDesc()) {
			if (msg==null) msg = tok[0] + " " + chr + "\n";
			else msg += x + "\n";
		}
		msg += "\n";
		
		if (hitListStr1!=null && hitListStr1.length()>1) {		
			String [] hitWires = hitListStr1.split(";"); // Computed in SeqPool.getGeneHits
			for (String h : hitWires) msg += h + "\n";	 // CAS548 was list of hits; now boundaries only
		}
		if (hitListStr2!=null && hitListStr2.length()>1) {
			String [] hitWires = hitListStr2.split(";");
			for (String h : hitWires) msg += h + "\n";
		}
		
		if (exonList!=null) {
			msg += tok[1] + "\n" + SeqDataInfo.formatExon(exonList); // CAS522 remove last \n
		}
		
		if (Globals.INFO) msg += "\nIdx=" + annot_idx + "\n"; // CAS540
		
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
	
	protected int isMatchGeneN(String gene) {// CAS545 add for Sequence Filter Gene# search
		if  (!strGeneNum.equals(gene)) return -1;
		return start + ((end-start)/2);
	}
		
	public void setIsPopup(boolean b) { // Called for gene only on popup; Annotation=true; Sequence when -tt; TextShowInfo=false
		isPopup=b; 
		if (exonVec!=null) 
			for (Annotation ad : exonVec) ad.isPopup=b; // CAS545
	}
	protected void setIsHitPopup(boolean b) { // Called for gene when searched on (not turned off)
		isHitPopup=b; 
		if (exonVec!=null) 
			for (Annotation ad : exonVec) ad.isHitPopup=b; // CAS545
	}
	protected void setIsSelectedGene(boolean b) { // Called for filtered gene (never turned off)
		isSelectedGene=b; 
		if (exonVec!=null) 
			for (Annotation ad : exonVec) ad.isSelectedGene=b; // CAS545
	}
	protected void setIsConserved(boolean b) { // Called for conserved turned on/off
		isHitg2=b; 
		if (exonVec!=null) 
			for (Annotation ad : exonVec) ad.isHitg2=b; // CAS545
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
	
	protected static final int GENE_INT 		= 0;
	protected static final int EXON_INT 		= 1; 
	protected static final int GAP_INT 		= 2;
	protected static final int CENTROMERE_INT 	= 3;
	protected static final int numTypes 		= 4; 
	
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
