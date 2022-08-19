package symap.sequence;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Stroke;
import java.awt.geom.Rectangle2D;
import java.util.Vector;

import symap.SyMAP;
import util.ErrorReport;
import util.PropertiesReader;
import util.TextBox;
import util.Utilities;

/**
 * Class Annotation is used for storing and painting an annotation graphics to the screen.
 * An annotation can be a Gene, Exon, Gap, Centromere
 * Drawing the annotation requires calling setRectangle() first.
 */
public class Annotation {
	private static boolean TRACE = symap.projectmanager.common.ProjectManagerFrameCommon.TEST_TRACE;
	
	/**
	 * Values used in the database to represent annotation types.
	 */
	public static final String GENE 		= "gene";
	public static final String FRAMEWORK 	= "frame";
	public static final String GAP 			= "gap";
	public static final String CENTROMERE 	= "centromere";
	public static final String EXON 		= "exon";  
	public static final String SYGENE       = "sygene"; 
	public static final String HIT      	= "hit"; 
	
	public static final int GENE_INT 		= 0;
	public static final int FRAMEWORK_INT 	= 1;
	public static final int GAP_INT 		= 2;
	public static final int CENTROMERE_INT 	= 3;
	public static final int EXON_INT 		= 4; 
	public static final int SYGENE_INT     	= 5; 
	public static final int HIT_INT     	= 6; 
	public static final int numTypes 		= 7; 
	public static Vector<String> types = new Vector<String>(numTypes, 1); 
	static {
		types.add(GENE);
		types.add(FRAMEWORK);
		types.add(GAP);
		types.add(CENTROMERE);
		types.add(EXON);   
		types.add(SYGENE); 
	}

	public static Color geneColor;
	public static Color frameColor;
	public static Color gapColor;
	public static Color centromereColor;
	public static Color exonColor;   
	public static Color sygeneColor = Color.yellow; // CAS503 sygene obsolete?
	
	private static final float crossWidth; // the width of the line in the cross
	static {
		PropertiesReader props = new PropertiesReader(SyMAP.class.getResource("/properties/annotation.properties"));
		
		crossWidth = (float) props.getDouble("crossWidth");
		geneColor = props.getColor("geneColor");
		frameColor = props.getColor("frameColor");
		gapColor = props.getColor("gapColor");
		centromereColor = props.getColor("centromereColor");
		exonColor = props.getColor("exonColor"); 
	}

	// private String name;
	private int type;
	private int start, end;						
	private String description, tag;	
	private boolean strand;
	private int gene_idx=0;  // If this is an exon, it is the gene_idx that it belongs to
	private int annot_idx=0; 
	private int genenum=0;
	private String exonList=null;
	
	private Rectangle2D.Double rect;
	private TextBox descBox=null; // CAS503

	/**
	 * Creates a new Annotation instance setting the description values, color, and draw method based on type.
	 */
	public Annotation(String name, String annot_type, int start, int end, String strand, 
			String tag, int gene_idx, int idx, int genenum) {
		this.type = getType(annot_type);
		this.start = start;
		this.end = end;
		this.strand = (strand == null || !strand.equals("-"));
		description = name;  
		this.tag = tag;
		this.gene_idx = gene_idx;
		this.annot_idx = idx;
		this.genenum = genenum;
		
		rect = new Rectangle2D.Double();
	}

	/**
	 * DRAW sets up the rectangle; called in Sequence.build()
	 */
	public void setRectangle(
			Rectangle2D boundry, 
			long startBP, long endBP, // display start and end of chromosome 
			double bpPerPixel, double width, boolean flip) 
	{
		double y, height, oy;
		long ts, te;
		double x = boundry.getX() + (boundry.getWidth()-width)/2; 

		if (start > end) {
			ts = end;
			te = start;
		} else {
			ts = start;
			te = end;
		}

		if (ts < startBP)	ts = startBP;	
		if (te > endBP)		te = endBP;		
			
		if (boundry.getWidth() < width)
			width = boundry.getWidth();

		oy = boundry.getY() + boundry.getHeight();

		if (!flip) 	y = (ts - startBP) / bpPerPixel + boundry.getY(); 
		else 		y = (endBP - ts) / bpPerPixel + boundry.getY();		 
		
		height = (te - ts) / bpPerPixel;
		if (flip) y -= height; 

		if (y + height >= boundry.getY() && y <= oy) {
			if (y < boundry.getY())
				y = boundry.getY();
			if (y + height > oy)
				height = oy - y;
			rect.setRect(x, y, width, height);
		} else {
			rect.setRect(0, 0, 0, 0);
		}
	}
	/** clears the rectangle coordinates so that the annotation will not be painted **/
	public void clear() {
		rect.setRect(0, 0, 0, 0);
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
		}
	}

	private Color getColor() {
		if (type == EXON_INT)		return exonColor;	
		if (type == GENE_INT)		return geneColor;
		if (type == SYGENE_INT) 	return sygeneColor; 
		if (type == GAP_INT)		return gapColor;
		if (type == FRAMEWORK_INT)	return frameColor;
		if (type == CENTROMERE_INT)	return centromereColor;

		return Color.black; 
	}

	public int getType() {return type;}

	public boolean isGene() 	  { return type == GENE_INT; }
	public boolean isFramework()  { return type == FRAMEWORK_INT; }
	public boolean isGap() 		  { return type == GAP_INT; }
	public boolean isCentromere() { return type == CENTROMERE_INT; }
	public boolean isExon() 	  { return type == EXON_INT; }   
	public boolean isSyGene() 	  { return type == SYGENE_INT; } 

	public int getStart() {return start;}
	public int getEnd() {return end;}
	
	// for seq-seq closeup
	public boolean getStrand() {return strand;}
	
	/** determines if the rectangle of this annotation contains the point p. */
	public boolean contains(Point p) {
		return rect.contains(p.getX(), p.getY());
	}
	/*** For CloseUpDialog - what is in the name field */
	public String getShortDescription() {
		return description;
	}
	/** Display in box beside genes when Show Annotation Description */
	public Vector<String> getVectorDescription() {
		Vector<String> out = new Vector<String>();
		String gn = (genenum>0) ? " #"+ genenum : " ";
		out.add(tag + gn);				// CAS512 add tag, genenum	
		out.add(getLocLong());
		for (String token : description.split(";")) 
				out.add( token.trim() ); // CAS501 added trim
		return out;
	}
	
	private String getLocLong() { // CAS504
		return Utilities.coordsStr(strand, start, end); // CAS512 update
	}
	/** Shown in info text box when mouse over object */
	public String getLongDescription() {
		String longDes;
		
		if (type == GENE_INT || type == EXON_INT) {
			String xDesc = description.replaceAll(";", "\n"); 	 // CAS503
			String gn = (genenum>0) ? " #"+ genenum : " ";
			longDes = tag + gn +  "\n" + getLocLong() + "\n" + xDesc;	 // CAS512 add tag, genenum	
		}
		else if (type == GAP_INT) 			longDes = "Gap\n" + getLocLong(); // CAS504 add getLoc
		else if (type == CENTROMERE_INT) 	longDes = "Centromere\n" + getLocLong();
		else if (type == FRAMEWORK_INT) 	longDes = "Framework Marker\n" + description;
		else								longDes = "Name " + description;
		
		return longDes;
	}
	public String getTag() {return tag;} // CAS512 add for HelpBox 
	
	public boolean hasShortDescription() {
		if (type==EXON_INT && !TRACE) return false; // CAS512 do not want exons on "Show Annotations"

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
			}
			else 								// Else draw as line 
				g2.drawLine((int)rect.x, (int)rect.y, (int)rect.x + (int)rect.width, (int)rect.y); 
		}
	}
	
	public String toString() {
		return "[Annotation: {Rect: " + rect + "}]";
	}

	public static int getType(String type) {
		int i = (byte)types.indexOf(type);
		if (i < 0) {
			i = (byte)types.size();
			types.add(type);
		}
		return i;
	}
	
	/*******************************************
	 * CAS503 added so can display popup of description
	 * Right-click on annotation
	 */
	
	public void setExonList(Vector <Annotation> annoVec) { // CAS512 add ExonList to popup
		if (exonList!=null) return;
		try {
			String list="";
			
			for (Annotation ad : annoVec) {
				String x = ad.getExon(annot_idx); // method of "ad" annotation
				if (x!=null) list += "\n" + x;
			}
			exonList = list;
		}
		catch (Exception e) {ErrorReport.print(e, "Get exon list for " + gene_idx);}
		
	}
	public String getExon(int parent_idx) { // called on "another" annotation object
		if (isExon() && this.gene_idx==parent_idx) {
			String x = Utilities.coordsStr(strand, start, end);
			return String.format("%10s %s", tag, x);
		}
		return null;
	}
	
	public void setTextBox(TextBox tb) {
		descBox = tb;
	}
	public boolean boxContains(Point p) {
		if (descBox==null) return false;
		
		return descBox.containsP(p);
	}
	public boolean popupDesc(Point p) { // Called on mouse event in Sequence
		if (descBox==null || !descBox.containsP(p)) return false;
		 
		descBox.popupDesc(exonList); // Popup from TextBox method - it has description
		return true;
	}
}
