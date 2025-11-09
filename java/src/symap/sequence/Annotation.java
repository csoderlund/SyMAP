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
	private int genenum=0;	 	
	
	private Rectangle2D.Double rect;			// Gene rectangle
	private Rectangle2D.Double hoverGeneRect; 	// so hover cover for gene covers full width of exon
	private Rectangle2D.Double lastR=null;		// if Last gene starts at same Y, then stagger Gene#
	
	private Vector <Annotation> exonVec = null; // determine during SeqPool load 
	private String exonList=null; 				// build first time of popup
	private String hitListStr1=null;  			// add to popup; computed in sequence.setForGenePopup using coords 
	private String hitListStr2=null;			// add HitList for other side to popup
	private int [] hitIdxArr1=null, hitIdxArr2=null;  // set value when hitListStr is set
	private boolean bHasHits=false;				// On creation, T if any hits; on display GeneNum, changed to T if hit for this pair
	
	private boolean bGeneLineOpt=false; 		// Show line on all genes
	private boolean bHighPopup=false;			// Highlight gene if popup
	private boolean bShowGeneNum=false;			// Show text genenum
	private boolean bShowGeneNumHit=false;		// Show text genenum
	private boolean isPopup=false; 				// Gene has popup - highlight if bHighPopup
	private boolean isHitPopup=false;			// Hit Popup highlights gene too, though gene Popup has precedence
	
	private boolean isSelectedGene=false; 		// Gene is filtered
	private boolean isG2xN=false;			    // Highlight exons of G2xN genes
	
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
		bHasHits = (numhits>0); // this could be from any synteny pair 
	
		// see backend.AnnotLoadPost.computeTags for formatting
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
	protected void addExon(Annotation aObj) {exonVec.add(aObj);} // determined during SeqPool load of data
	
	public Vector <Annotation> getExonVec() { return exonVec;} // for closeup.SeqDataInfo hit popup
	/**
	 * DRAW sets up the rectangle; called in Sequence.buildGraphics(); 
	 */
	protected void setRectangle(
			Rectangle2D boundry,      // center of chromosome rectangle (rect.x+1,rect.y,rect.width-2,rect.height)
			int startBP, int endBP,   // display start and end of chromosome 
			double bpPerPixel, double dwidth, double hoverWidth, boolean flip, int offset, 
			boolean bHighPopup, boolean bGeneLineOpt, boolean bShowGeneNum, boolean bShowGeneNumHit, int nG2xN) 
	{
		this.bGeneLineOpt=bGeneLineOpt; // used these 3 in paintComponent
		this.bHighPopup=bHighPopup;
		this.bShowGeneNum=bShowGeneNum;  this.bShowGeneNumHit=bShowGeneNumHit;
		if (bShowGeneNum && nG2xN!=0 && !isG2xN)  this.bShowGeneNum=false; 

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
		if (offset!=0) x = x-offset;	 // for overlapping genes, 0 for not
		
		if (chrWidth < dwidth) dwidth = chrWidth;
		
		double lowY = y + height;
		if (y < upChrY)      y = upChrY;
		if (lowY > lowChrY)  height = lowChrY - y;
		
		if (lowY >= upChrY && y <= lowChrY) rect.setRect(x, y, dwidth, height);
		else 								rect.setRect(0, 0, 0, 0);
		
		if (hoverWidth!=0) { // gene
			double xx = chrX + (chrWidth-hoverWidth)/2; // set x before modify width
			if (offset!=0) xx= xx-offset;				// for overlapping genes
			
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
				
				if (itype==GENE_INT) {// Gene Delimiter
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
			
			// XXX Gene#      LastR is Rect, not text (Gray box drawn in Sequence.buildGraphics)
			boolean doGeneNum = (bShowGeneNum && itype==GENE_INT) || (bShowGeneNumHit && showGeneHasHit());
			if (doGeneNum) { 
				 g2.setPaint(Color.black);
				 g2.setFont(Globals.textFont);
				 
				 int x, w=0;
				 if (seqObj.isRef()) { 	// right side
					 if (lastR!=null && lastR.x>rect.x && lastR.y+lastR.height>rect.y) 
						   x = (int)lastR.x + (int) Sequence.EXON_WIDTH-2;
					 else  x = (int)rect.x  + (int) Sequence.EXON_WIDTH-2;	// paint on right
				 }
				 else { 				// left side
					 FontMetrics metrics = g2.getFontMetrics(Globals.textFont);
					 w = metrics.stringWidth(strGeneNum) + 6; // tested stringWidth of 'a' is 7
					 x = (int) rect.x-w;
				 }
				 int y = (int)rect.y + (Globals.textHeight/2);
				 if (lastR!=null && Math.abs(lastR.y-rect.y)<Globals.textHeight) y+=Globals.textHeight-2;	// move it down
					 
				 g2.drawString(strGeneNum, x, y);
			}
		}
	}
	private Color getColor() {
		if (itype == EXON_INT) {
			if (bHighPopup) {
				if (isPopup)    return geneHighColor; // only exon highlighted when Gene Filter
				if (isHitPopup) return Mapper.pseudoLineGroupColor; 
			}
			if (isSelectedGene) return geneHighColor; 
			
			if (isG2xN)    		return geneHighColor; 
			
			if (bStrandPos)     return exonColorP;	
			else                return exonColorN;
		}
		if (itype == GENE_INT)		{
			if (bHighPopup) {
				if (isPopup)  	return geneHighColor;
				if (isHitPopup) return Mapper.pseudoLineGroupColor; 
			}	
			if (isSelectedGene) return geneHighColor;
			
			return geneColor;
		}
		if (itype == GAP_INT)			return gapColor;
		if (itype == CENTROMERE_INT)	return centromereColor;

		return Color.black; 
	}
	protected boolean showGeneHasHit() { // also used in Sequence.buildGraphics to show gray anno box
		if (itype!=GENE_INT || !bHasHits) return false;
		
		if (!hasHitList()) {
			bHasHits = seqObj.hasAnnoHit(1,this); // this sets hitListStr and hitIdxArr
		}		 
		if (bHasHits && seqObj.hasVisHit(1, hitIdxArr1)) return true;
		
		if (seqObj.hitsObj2==null) return false;
		
		if (!hasHitList2()) {
			boolean b = seqObj.hasAnnoHit(2, this); // this sets hitList
			if (b) bHasHits = true;				// may be T for hitList1, but the hit1 not visible
		}
		if (bHasHits && seqObj.hasVisHit(2, hitIdxArr2)) return true;
		return false;
	}
	/////////////////////////////////////////////////////////////////////////
	protected void clear() {// clears the rectangle coordinates so that the annotation will not be painted 
		rect.setRect(0, 0, 0, 0);
		hoverGeneRect.setRect(0, 0, 0, 0);
	}
	
	protected double getY1() {return rect.getY();} // same as rect.y
	
	protected void setLastY(Annotation last) {lastR=last.rect;}; 
	
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
	public int getGeneIdx() { return gene_idx;} 			// For Sequence to know if overlap exon
	public int getAnnoIdx() { return annot_idx;}			// ditto
	public String getTag() {return tag;} 					// For HelpBox; called for Exons
	public boolean isStrandPos() {return bStrandPos;}		// for seq-seq closeup
	protected Color getBorderColor() { return (bStrandPos) ? exonColorP : exonColorN;} // for Annotation Boxes
	
	protected int getType() {return itype;}
	protected boolean isGene() 	  { return itype == GENE_INT; }
	protected boolean isExon() 	  { return itype == EXON_INT; }   
	protected boolean isGap() 	  { return itype == GAP_INT; }
	protected boolean isCentromere() { return itype == CENTROMERE_INT; }
	
	protected int getGeneLen()	{ return Math.abs(end-start)+1;} // ditto
	protected int getGeneNum() { return genenum;}				 // for sorting in SeqPool
	public String getFullGeneNum() {return strGeneNum;}          // has suffix
	
	/*******************************************
	 * XXX hover and box and closeup info
	 */
	protected boolean hasDesc() {	// Sequence.build() 
		return description != null && description.length() > 0;
	}
	public String getCloseUpDesc() {// For CloseUpDialog - shown over the gene graphic; use DescOnly
		return "Gene #" + strGeneNum + "   " + getIdOnly() + "  " + getDescOnly();
	} 
	protected Vector<String> getGeneBoxDesc() { // Shown when the Annotation option is on; 
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
		out.add(fullTag);				
		out.add(getLocLong());	
		for (String token : description.split(";")) {
			out.add( token.trim() ); 
		}
		return out;
	}
	protected String getHoverDesc() {// Shown in info text box when mouse over object; show full desc 
		String longDes;
		
		if (itype == GENE_INT) {
			String xDesc = description.replaceAll(";", "\n");   	 
			longDes = fullTag +  "\n" + getLocLong() + "\n" + xDesc;	
		}
		else if (itype == EXON_INT)			longDes = tag + " " +getLocLong(); 
		else if (itype == GAP_INT) 			longDes = "Gap\n" + getLocLong(); 
		else if (itype == CENTROMERE_INT) 	longDes = "Centromere\n" + getLocLong();
		else								longDes = "Name " + description;
		
		return longDes;
	}
	private String getLocLong() { // used in above two
		return SeqData.coordsStr(bStrandPos, start, end); 
	}
	
	protected boolean hasHitList() 			{return hitListStr1!=null; } // means it has been set, but may not have hits
	protected boolean hasHits() 			{return hitIdxArr1!=null;} // set and has hits
	protected void setHitList(String hList, TreeMap <Integer, String> scoreMap) { // Calls 1st time popup or show GeneNum
		hitListStr1=hList;
		if (scoreMap.size() > 0) {
			hitIdxArr1 = new int [scoreMap.size()];
			int i=0;
			for (int idx : scoreMap.keySet()) hitIdxArr1[i++] = idx;
		}
	} 
	protected boolean hasHitList2() 		{return hitListStr2!=null; } // hits on other side if tracks on both sides 
	protected void setHitList2(String hList, TreeMap <Integer, String> scoreMap){
		hitListStr2=hList;
		if (scoreMap.size() > 0) {
			hitIdxArr2 = new int [scoreMap.size()];
			int i=0;
			for (int idx : scoreMap.keySet()) hitIdxArr2[i++] = idx;
		}
	} 
	
	// for popup
	protected void setExonList() { 
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
	 * popup from clicking gene; use Utilities for tag
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
			for (String h : hitWires) msg += h + "\n";	 // boundaries only
		}
		if (hitListStr2!=null && hitListStr2.length()>1) {
			String [] hitWires = hitListStr2.split(";");
			for (String h : hitWires) msg += h + "\n";
		}
		
		if (exonList!=null) {
			msg += tok[1] + "\n" + SeqDataInfo.formatExon(exonList); 
		}
		
		if (Globals.INFO) msg += "\nIdx=" + annot_idx + "\n"; 
		
		String title =  name + "; " + tok[0];
		
		new TextShowInfo(parentFrame, title, msg, this);
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
	protected int isMatchGeneN(String gene) {// for Sequence Filter Gene# search
		if  (!strGeneNum.equals(gene)) return -1;
		return start + ((end-start)/2);
	}
		
	public void setIsPopup(boolean b) { // Called for gene only on popup; Annotation=true; TextShowInfo=false
		isPopup=b; 
		if (exonVec!=null) 
			for (Annotation ad : exonVec) ad.isPopup=b; 
	}
	protected void setIsHitPopup(boolean b) { // Called for gene when searched on (not turned off)
		isHitPopup=b; 
		if (exonVec!=null) 
			for (Annotation ad : exonVec) ad.isHitPopup=b; 
	}
	protected void setIsSelectedGene(boolean b) { // Called for filtered gene (never turned off)
		isSelectedGene=b; 
		if (exonVec!=null) 
			for (Annotation ad : exonVec) ad.isSelectedGene=b; 
	}
	
	/* g2xN methods */
	protected boolean isHighG2xN() {return isG2xN;} // hit and gene highlighted
	
	protected void setIsG2xN(boolean b) { // Sequence.setAnnoG2xN 
		isG2xN = b; // sets for Gene object
		if (exonVec!=null) 
			for (Annotation ad : exonVec) ad.isG2xN = b; // sets exons object
	}
	
	////////////////////////////////////////////////////////
	// for TextShowSeq to sort exons
	public static Comparator<Annotation> getExonStartComparator() {
		return new Comparator<Annotation>() {
			public int compare(Annotation a1, Annotation a2) {
				return a2.start - a1.start; 
			}
		};
	}
	/***********************************************************************
	 * Values used in the database to represent annotation types.
	 */
	private static final float crossWidth= (float) 2.0; // the width of the line in the cross
	
	protected static final int GENE_INT 		= 0;
	protected static final int EXON_INT 		= 1; 
	protected static final int GAP_INT 			= 2;
	protected static final int CENTROMERE_INT 	= 3;
	protected static final int numTypes 		= 4; 
	
	// accessed and changed by ColorDialog - do not change
	public static Color geneColor;
	public static Color gapColor;
	public static Color centromereColor;
	public static Color exonColorP; 				
	public static Color exonColorN;
	public static Color geneHighColor;				
	
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
