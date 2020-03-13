package symap.sequence;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Stroke;
import java.awt.geom.Rectangle2D;
import java.util.Vector;

import symap.SyMAP;
import util.PropertiesReader;
import util.TextBox;

/**
 * Class <code>Annotation</code> is used for storing and painting an
 * annotation graphics to the screen.
 * 
 * An annotation consists of a name, type, start, end, and text value. An
 * annotations also has a long description and a short description which are set
 * to default values based on type during construction, but can be changed
 * through setLongDescription(String) and setShortDescription(String).
 * 
 * Drawing the annotation requires calling setRectangle() first.
 * 
 * The color is set based on the annotation.properties file, but the color can
 * be changed by calling setColor(Color).
 * 
 * The draw method is set during the constructor by type, undefined types being
 * set to be drawn by the TICK method. setDraw(int) can be called to change this
 * value.
 * 
 */
public class Annotation {
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
	private String description;			
	private Rectangle2D.Double rect;
	private boolean strand;	
	private TextBox descBox=null; // CAS503

	/**
	 * Creates a new <code>Annotation</code> instance setting the description
	 * values, color, and draw method based on type.
	 */
	public Annotation(String name, String annot_type, /*long*/int start, /*long*/int end, String strand) {
		this.type = getType(annot_type);
		this.start = start;
		this.end = end;
		this.strand = (strand == null || !strand.equals("-"));
		description = name;  
		
		rect = new Rectangle2D.Double();
	}

	/**
	 * Method <code>clear</code> clears the rectangle coordinates so that the
	 * annotation will not be painted and sets the associated rule to null.
	 */
	public void clear() {
		rect.setRect(0, 0, 0, 0);
	}

	/**
	 * Method <code>setRectangle</code> sets up the rectangle based on the
	 * values given adjusting the rectangle as needed.
	 */
	public void setRectangle(
			Rectangle2D boundry, 
			long startBP,		
			long endBP, 
			double bpPerPixel, 
			double width, 
			boolean flip) 
	{
		double y, height, oy;
		long ts, te;
		double x = boundry.getX()+(boundry.getWidth()-width)/2; 

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

		if (!flip) y = (ts - startBP) / bpPerPixel + boundry.getY(); 
		else y = (endBP - ts) / bpPerPixel + boundry.getY();		 
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

	/**
	 * Method <code>getY</code> returns the midpoint of the mark. This should
	 * be called after calling setRectangle.
	 * 
	 * @return a <code>double</code> value
	 */
	public double getY() {
		return rect.getY() + (rect.getHeight() / 2.0);
	}
	
	// get top y coord
	public double getY1() {
		return rect.getY();
	}
	
	// get bottom y coord
	public double getY2() {
		return rect.getY() + rect.getHeight() - 1;
	}

	/**
	 * Method <code>isVisible</code> returns true if after calling
	 * setRectangle, the rectangle has a width and a height greater than zero.
	 */
	public boolean isVisible() {
		return rect.getWidth() > 0 && rect.getHeight() > 0;
	}
	
	/**
	 * Offset the whole Annotation if it's visible.
	 */
	public void setOffset(double x, double y) {
		if (isVisible()) {
			rect.x -= x;
			rect.y -= y;
		}
	}

	/**
	 * Method <code>getColor</code> returns this annotations associated color.
	 */
	private Color getColor() {
		
		if (type == EXON_INT)		return exonColor;	
		if (type == GENE_INT)		return geneColor;
		if (type == SYGENE_INT) 		return sygeneColor; 
		if (type == GAP_INT)			return gapColor;
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

	public /*long*/int getStart() {return start;}
	public /*long*/int getEnd() {return end;}
	
	// for pseudo-pseudo closeup
	public boolean getStrand() {return strand;}
	
	/**
	 * Method <code>contains</code> determines if the rectangle of this
	 * annotation contains the point p.
	 */
	public boolean contains(Point p) {
		return rect.contains(p.getX(), p.getY());
	}

	/**
	 * Method <code>getShortDescription</code> returns the long description
	 * set during the creation of the object by type or by a call to
	 * setShortDescription(String).
	 */
	public String getShortDescription() {
		return description;
	}
	
	// Display in box beside genes when Show Annotation Description
	public Vector<String> getVectorDescription() {
		Vector<String> out = new Vector<String>();
		out.add("Location=" + start + ":" + end + "   Length=" + (end-start+1));
		for (String token : description.split(";")) out.add( token.trim() ); // CAS501 added trim
		return out;
	}

	/**
	 * Method <code>getLongDescription</code> returns the long description set
	 * during the creation of the object by type or by a call to setLongDescription(String). 
	 * Shown in info text box when mouse over object
	 */
	public String getLongDescription() {
		String longDes;
		if (type == GAP_INT)
			longDes = "Gap: " + (new Long(start).toString()) + "-" + (new Long(end).toString());
		else if (type == CENTROMERE_INT)
			longDes = "Centromere: " + (new Long(start).toString()) + "-" + (new Long(end).toString());
		else if (type == GENE_INT) {
			String x = description.replaceAll(";", "\n"); // CAS503 
			longDes = "Gene: " + x;
		}
		else if (type == EXON_INT) 
			longDes = "Exon: " + (new Long(start).toString()) + "-" + (new Long(end).toString());
		else if (type == FRAMEWORK_INT) 
			longDes = "Framework Marker: " + description;
		else
			longDes = "Name: " + description;
		
		return longDes;
	}

	/**
	 * Method <code>hasShortDescription</code> returns true if the long
	 * description is not null and not an empty string.
	 */
	public boolean hasShortDescription() {
		return description != null && description.length() > 0;
	}

	/**
	 * Method <code>paintComponent</code> paints this annotation to the
	 * graphics object g2.
	 */
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
			// Only draw full rectangle if it is large enough to see.
			if (rect.height >= 2) { 
				g2.fillRect((int)rect.x, (int)rect.y, (int)rect.width, (int)rect.height);
			}
			else // Else draw as line 
				g2.drawLine((int)rect.x, (int)rect.y, (int)rect.x + (int)rect.width, (int)rect.y); 
		}
	}
	
	/**
	 * Method <code>toString</code> returns the string representation of this
	 * object
	 */
	public String toString() {
		return "[Annotation: {Rect: " + rect + "}]";
	}

	private static int getType(String/*Object*/ type) {
		int i = (byte)types.indexOf(type);
		if (i < 0) {
			i = (byte)types.size();
			types.add(type);
		}
		return i;
	}
	
	/*******************************************
	 * CAS503 added so can display popup of description
	 */
	public void setTextBox(TextBox tb) {descBox = tb;}
	public boolean boxContains(Point p) {
		if (descBox==null) return false;
		
		return descBox.containsP(p);
	}
	public boolean popupDesc(Point p) {
		if (descBox!=null && descBox.containsP(p)) {
			descBox.popupDesc();
			return true;
		}	
		return false;
	}
}
