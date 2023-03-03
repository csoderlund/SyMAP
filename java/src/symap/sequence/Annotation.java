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
import symap.closeup.SeqData;
import util.ErrorReport;
import util.TextBox;

/**
 * Class Annotation is used for storing and painting an annotation graphics to the screen.
 * An annotation can be a Gene, Exon, Gap, Centromere
 * Drawing the annotation requires calling setRectangle() first.
 */
public class Annotation {
	private int type;
	private int start, end;						
	private String description, tag, tagGeneN;	
	private boolean bStrandPos;
	private int gene_idx=0;  	// If this is an exon, it is the gene_idx (pseudo_annot.idx) that it belongs to
	private int annot_idx=0; 	// pseudo_annot.idx
	private int genenum=0;	 	// CAS517 for sorting in PseudoData
	
	private Rectangle2D.Double rect;
	private Rectangle2D.Double hoverGeneRect; 	// CAS515 so hover cover for gene covers full width of exon
	private TextBox descBox=null; 				// CAS503
	private String exonList=null; 				// CAS512 add ExonList to popup
	private String hitList=null;  				// CAS517 add HitList to popup
	private boolean bGeneLineOpt=false; 		// CAS520 show line at start of gene option
	
	/**
	 * Creates a new Annotation instance setting the description values, color, and draw method based on type.
	 * Read from database in PseudoPool.setSequence 
	 * Creates AnnotationData object array
	 * Then goes to PseudoData for sorting
	 */
	public Annotation(String name, String annot_type, int start, int end, String strand, 
			String tag, int gene_idx, int idx, int genenum, int numhits) {
		this.type = getType(annot_type);
		this.start = start;
		this.end = end;
		this.bStrandPos = (strand == null || !strand.equals("-"));
		this.description = name;  
		this.gene_idx = gene_idx;
		this.annot_idx = idx;
		this.genenum = genenum;
	
		// see backend.AnnotLoadPost.computeTags for formatting; e.g Gene #500a (N Mbp) where N=#exons and M=exon length
		// CAS512 add pseudo_annot.gene_idx so exon is mapped to the gene; need reload annot
		// CAS515 merge tag and genenum in a more readable format; 
		// CAS517 add genenum and suffix to tag in AnnotPost; 
		// CAS518 add total exon length (no change to parsing)
		// CAS520 add h numhits, then removed because hard to see in display.
		tagGeneN="";
		if (genenum>0) { 
			String [] tok = tag.split("\\(");	
			if (tok.length==2) {
				tagGeneN = (tok[0].contains("#")) ? tok[0] : "Gene #" + genenum; 
				this.tag = tagGeneN;	 			   
				this.tag += " (#Exons=" + tok[1];
			}
			else   {
				this.tag = tagGeneN = "Gene #" + genenum;
			}
		}
		else this.tag = tag;
		
		rect = new Rectangle2D.Double();
		hoverGeneRect = new Rectangle2D.Double();
	}
	/**
	 * DRAW sets up the rectangle; called in Sequence.build(); CAS515 ordered lines to be more logical
	 */
	public void setRectangle(
			Rectangle2D boundry,      // center of chromosome rectangle (rect.x+1,rect.y,rect.width-2,rect.height)
			long startBP, long endBP, // display start and end of chromosome 
			double bpPerPixel, double dwidth, double hoverWidth, boolean flip, 
			int offset, boolean bGeneLineOpt) // CAS517 offset 
	{
		this.bGeneLineOpt=bGeneLineOpt;
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
	/** clears the rectangle coordinates so that the annotation will not be painted **/
	public void clear() {
		rect.setRect(0, 0, 0, 0);
		hoverGeneRect.setRect(0, 0, 0, 0);
	}
	 /** returns the midpoint of the mark; called after calling setRectangle **/
	public double getY() {
		return rect.getY() + (rect.getHeight() / 2.0);
	}
	/** get top y coord **/
	public double getY1() {
		return rect.getY();
	}
	/** get bottom y coord **/
	public double getY2() {
		return rect.getY() + rect.getHeight() - 1;
	}
	 /** true if after calling setRectangle **/
	public boolean isVisible() {
		return rect.getWidth() > 0 && rect.getHeight() > 0;
	}
	 /** Offset the whole Annotation if it's visible. **/
	public void setOffset(double x, double y) {
		if (isVisible()) {
			rect.x -= x;
			rect.y -= y;
			
			if (hoverGeneRect.getX()!=0 && hoverGeneRect.getY() !=0) {
				hoverGeneRect.x -= x;
				hoverGeneRect.y -= y;
			}
		}
	}

	private Color getColor() {
		if (type == EXON_INT) {
			if (bStrandPos) return exonColorP;	
			else return exonColorN;
		}
		if (type == GENE_INT)		return geneColor;
		if (type == SYGENE_INT) 	return sygeneColor; 
		if (type == GAP_INT)		return gapColor;
		if (type == CENTROMERE_INT)	return centromereColor;

		return Color.black; 
	}

	public int getType() {return type;}

	public boolean isGene() 	  { return type == GENE_INT; }
	public boolean isGap() 		  { return type == GAP_INT; }
	public boolean isCentromere() { return type == CENTROMERE_INT; }
	public boolean isExon() 	  { return type == EXON_INT; }   
	public boolean isSyGene() 	  { return type == SYGENE_INT; } 

	public int getStart() {return start;}
	public int getEnd() {return end;}
	public int getGeneIdx() { return gene_idx;} 			// CAS517 for Sequence to know if overlap exon
	public int getAnnoIdx() { return annot_idx;}			// ditto
	public int getGeneLen()	{ return Math.abs(end-start)+1;} // ditto
	public int getGeneNum() { return genenum;}				// CAS517 for sorting in PseudoData
	public String getTag() {return tag;} 					// CAS512 add for HelpBox
	public String getGeneNumStr() {return tagGeneN;}		// CAS518 for...
	public boolean isStrandPos() {return bStrandPos;}	// for seq-seq closeup
	
	/** XXX determines if the rectangle of this annotation contains the point p. */
	public boolean contains(Point p) {
		if (type == GENE_INT) {
			return hoverGeneRect.contains(p.getX(), p.getY());
		}
		return rect.contains(p.getX(), p.getY());
	}
	/*** For CloseUpDialog - what is in the name field */
	public String getShortDescription() {
		return description;
	}
	/** Display in box beside genes when Show Annotation Description */
	public Vector<String> getVectorDescription() {
		Vector<String> out = new Vector<String>();
		out.add(tag);				// CAS512 add tag	
		out.add(getLocLong(" "));
		for (String token : description.split(";")) 
				out.add( token.trim() ); // CAS501 added trim
		return out;
	}
	
	private String getLocLong(String delim) { // CAS504
		return SeqData.coordsStr(bStrandPos, start, end); // CAS512 update
	}
	/** Shown in info text box when mouse over object */
	public String getLongDescription() {
		String longDes;
		
		if (type == GENE_INT) {
			String xDesc = description.replaceAll(";", "\n");   	 // CAS503
			longDes = tag +  "\n" + getLocLong("\n") + "\n" + xDesc;	 // CAS512 add tag	
		}
		else if (type == EXON_INT)			longDes = tag + " " +getLocLong("\n"); // CAS517 tag +  "\n" + getLocLong("\n") + "\n" + xDesc;
		else if (type == GAP_INT) 			longDes = "Gap\n" + getLocLong("\n"); // CAS504 add getLoc
		else if (type == CENTROMERE_INT) 	longDes = "Centromere\n" + getLocLong("\n");
		else if (type == FRAMEWORK_INT) 	longDes = "Framework Marker\n" + description;
		else								longDes = "Name " + description;
		
		return longDes;
	}
	
	public boolean hasShortDescription() {
		return description != null && description.length() > 0;
	}

	public void paintComponent(Graphics2D g2) {
		if (type >= numTypes)
			return;
		
		Color c = getColor();
		if (c != null)
			g2.setPaint(c);
		
		if (type == CENTROMERE_INT) { 	
			Stroke oldstroke = g2.getStroke();
			g2.setStroke(new BasicStroke(crossWidth)/*crossStroke*/); 
			
			g2.drawLine((int)rect.x, (int)rect.y, (int)rect.x + (int)rect.width, (int)rect.y + (int)rect.height);
			g2.drawLine((int)rect.x, (int)rect.y + (int)rect.height, (int)rect.x + (int)rect.width, (int)rect.y);
			
			g2.setStroke(oldstroke);
		} 
		else { // TICK or RECT 
			if (rect.height >= 2) { 			// Only draw full rectangle if it is large enough to see.
				g2.fillRect((int)rect.x, (int)rect.y, (int)rect.width, (int)rect.height);
				
				if (type==GENE_INT && bGeneLineOpt) {// CAS520 add black line on top of gene to distinguish closely placed genes
					g2.setColor(Color.BLACK);
					Stroke oldstroke = g2.getStroke();
					g2.setStroke(new BasicStroke(2)); 
					
					int w = 10; // 15=width of exon from sequence.properties
					g2.drawLine((int)rect.x-w, (int)rect.y, ((int)rect.x + w+3), (int)rect.y);
					g2.setStroke(oldstroke);
				}
		
			}
			else 								// Else draw as line 
				g2.drawLine((int)rect.x, (int)rect.y, (int)rect.x + (int)rect.width, (int)rect.y); 
		}
	}
	public String toString() {
		String x = String.format("%-8s [Rect %.2f,%.2f,%.2f,%.2f] [Seq: %d,%d]", tag,
				rect.x,rect.y,rect.width, rect.height, start, end);
		if (type==GENE_INT) 
			x += String.format(" [HRect %.2f,%.2f,%.2f,%.2f]",
					hoverGeneRect.x,hoverGeneRect.y,hoverGeneRect.width, hoverGeneRect.height);
		return x;
	}
	public static int getType(String type) {
		int i = (byte)typeVec.indexOf(type);
		if (i < 0) {
			i = (byte)typeVec.size();
			typeVec.add(type);
		}
		return i;
	}
	
	/*******************************************
	 * CAS503 added so can display popup of description
	 * Right-click on annotation
	 */
	public boolean hasHitList() {return hitList!=null; } //  CAS517 added this
	public void setHitList(String hList) {hitList=hList;} 
	
	// for hover
	public String getExon(int parent_idx) { // called on "exon" annotation object
		if (isExon() && this.gene_idx==parent_idx) {
			String x = SeqData.coordsStr(bStrandPos, start, end);
			return String.format("%-8s %s", tag, x); // Exon #xx
		}
		return null;
	}
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
	// the following 3 methods are for popup from yellow box 
	public void setTextBox(TextBox tb) {
		descBox = tb;
	}
	public boolean boxContains(Point p) {
		if (descBox==null) return false;
		return descBox.containsP(p);
	}
	
	// CAS516 popup from clicking gene or yellow box, CAS517 add hitList
	public void popupDesc(Component parentFrame, String name, String chr) { 
	try {
		if (!tag.contains("(")) return;
		
		String [] tok = tag.split("\\("); // see backend.AnnotLoadPost.computeTags
		
		String msg = null;
		for (String x : getVectorDescription()) {
			if (msg==null) msg = tok[0] + " " + chr + "\n";
			else msg += x + "\n";
		}
		
		if (exonList!=null) {
			String exon = tok[1].replace("(",""); // CAS518 move Exon N len to here
			exon = tok[1].replace(")","");
			msg += "\n" + exon + "\n" + SeqData.formatExon(exonList) + "\n";
		}
		
		if (hitList!=null) {
			String [] hitWires = hitList.split(";");
			for (String h : hitWires) msg += SeqData.formatHit(h);
		}
		String title =  name + "; " + tagGeneN;
		
		if (parentFrame!=null) {
			new TextShowInfo(parentFrame, title, msg);
		}
		else descBox.popupDesc(title, msg); // aligns it with yellow box
	} catch (Exception e) {ErrorReport.print(e, "Creating genepopup");}
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
	 */
	private static final float crossWidth= (float) 2.0; // the width of the line in the cross
	
	public static final String GENE 		= "gene";
	public static final String FRAMEWORK 	= "frame"; // dead fpc
	public static final String GAP 			= "gap";
	public static final String CENTROMERE 	= "centromere";
	public static final String EXON 		= "exon";  
	public static final String SYGENE       = "sygene"; // not used
	public static final String HIT      	= "hit"; 
	
	public static final int GENE_INT 		= 0;
	public static final int FRAMEWORK_INT 	= 1; // dead
	public static final int GAP_INT 		= 2;
	public static final int CENTROMERE_INT 	= 3;
	public static final int EXON_INT 		= 4; 
	public static final int SYGENE_INT     	= 5; 
	public static final int HIT_INT     	= 6; 
	public static final int numTypes 		= 7; 
	public static Vector<String> typeVec = new Vector<String>(numTypes, 1); 
	static {
		typeVec.add(GENE);
		typeVec.add(FRAMEWORK);
		typeVec.add(GAP);
		typeVec.add(CENTROMERE);
		typeVec.add(EXON);   
		typeVec.add(SYGENE); 
	}
	// accessed and changed by ColorDialog - do not change
	public static Color geneColor;
	public static Color gapColor;
	public static Color centromereColor;
	public static Color exonColorP; 				// CAS517 add P/N
	public static Color exonColorN;
	public static Color sygeneColor = Color.yellow; // CAS503 not used
	
	static {
		PropertiesReader props = new PropertiesReader(Globals.class.getResource("/properties/annotation.properties"));
		geneColor = props.getColor("geneColor");
		gapColor = props.getColor("gapColor");
		centromereColor = props.getColor("centromereColor");
		exonColorP = props.getColor("exonColorP"); 
		exonColorN = props.getColor("exonColorN"); 
	}
}
