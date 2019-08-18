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

/**
 * Class <code>Annotation</code> is used for storing and painting an
 * annotation to the screeen.
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
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 */
public class Annotation {
	/**
	 * Values representing annotation shapes.
	 */
// mdb removed 3/31/08
//	public static final int TICK = 1;
//	public static final int RECT = 2;
//	public static final int CROSS = 3;

	/**
	 * Values used in the database to represent annotation types.
	 */
	public static final String GENE 		= "gene";
	public static final String FRAMEWORK 	= "frame";
	public static final String GAP 			= "gap";
	public static final String CENTROMERE 	= "centromere";
	public static final String EXON 		= "exon";   // mdb added 3/7/07  #102
	public static final String SYGENE       = "sygene"; // mdb added 7/31/09 #167
	public static final String HIT      	= "hit"; // mdb added 7/31/09 #167
	
	public static final int GENE_INT 		= 0;
	public static final int FRAMEWORK_INT 	= 1;
	public static final int GAP_INT 		= 2;
	public static final int CENTROMERE_INT 	= 3;
	public static final int EXON_INT 		= 4; // mdb added 3/7/07  #102
	public static final int SYGENE_INT     	= 5; // mdb added 7/31/09 #167
	public static final int HIT_INT     	= 6; // mdb added 7/31/09 #167
	public static final int numTypes 		= 7; // mdb added 3/8/07  #102
	public static Vector<String> types = new Vector<String>(numTypes, 1); // mdb added "numTypes" 3/8/07 #102
	static {
		types.add(GENE);
		types.add(FRAMEWORK);
		types.add(GAP);
		types.add(CENTROMERE);
		types.add(EXON);   // mdb added 3/7/07  #102
		types.add(SYGENE); // mdb added 7/31/09 #167
	}

// mdb removed 3/31/08 #156
//	private static HashMap defaults = new HashMap(numTypes); // mdb added "numTypes" 3/8/07 #102
//	static {
//		defaults.put(GENE, 		 new Integer(TICK));
//		defaults.put(FRAMEWORK,  new Integer(TICK));
//		defaults.put(GAP, 		 new Integer(RECT));
//		defaults.put(CENTROMERE, new Integer(CROSS));
//		defaults.put(EXON, 		 new Integer(RECT)); // mdb added 3/7/07
//	}

	public static Color geneColor;
	public static Color frameColor;
	public static Color gapColor;
	public static Color centromereColor;
	public static Color exonColor;   // mdb added 3/7/07 #102
	public static Color sygeneColor = Color.yellow; // mdb added 7/31/09 #167
	//private static final double markHeight; // mdb unused 7/7/09
	private static final float crossWidth; // the width of the line in the cross
	static {
		PropertiesReader props = new PropertiesReader(SyMAP.class.getResource("/properties/annotation.properties"));
		//markHeight = props.getDouble("markHeight"); // mdb unused 7/7/09
		crossWidth = (float) props.getDouble("crossWidth");
		geneColor = props.getColor("geneColor");
		frameColor = props.getColor("frameColor");
		gapColor = props.getColor("gapColor");
		centromereColor = props.getColor("centromereColor");
		exonColor = props.getColor("exonColor"); // mdb added 3/7/07 #102
	}

	// private String name;
	private int type;
	private /*long*/int start, end;		// mdb changed 1/8/09 - changed type to int to match database
	//private String text; 				// mdb removed 3/31/08 #156
	private String /*shortDes*/description;	// mdb renamed 7/14/09
	//private String longDes; 			// mdb removed 3/31/08 #156
	//private Rule rule;				// mdb removed 7/15/09 #166
	//private boolean paintRule; 		// mdb removed 3/31/08 #156
	//private Color color = null; 		// mdb removed 3/31/08 #156
	private Rectangle2D.Double rect;
	//private int draw; 				// mdb removed 3/31/08 #156
	//private Line2D.Double line;		// mdb removed 1/8/09
	//private Stroke crossStroke;		// mdb removed 1/8/09
	private boolean strand;				// mdb added 1/8/09 for pseudo-pseudo closeup - true=plus, false=minus

	/**
	 * Creates a new <code>Annotation</code> instance setting the description
	 * values, color, and draw method based on type.
	 * 
	 * @param name			a <code>String</code> value
	 * @param annot_type
	 *            a <code>String</code> value of the type, can be one of the
	 *            defined ones, or a different one
	 * @param start			an <code>long</code> value in bp
	 * @param end			an <code>long</code> value in bp
	 * @param text
	 *            a <code>String</code> value of database description of the
	 *            Annotation
	 */
	public Annotation(String name, String annot_type, /*long*/int start, /*long*/int end, String strand) {
		// this.name = name;
		this.type = getType(annot_type);
		this.start = start;
		this.end = end;
		this.strand = (strand == null || !strand.equals("-"));
		
		// mdb added 4/8/08 #156
		/*if (type != EXON_INT)*/ // mdb removed condition 11/5/08
			description = name;  
		
// mdb removed 4/8/08 #156
//		this.text = text; 
//		draw = getDefaultDraw(annot_type); 
//		// Set shortDes
//		if (type == FRAMEWORK_INT)
//			this.shortDes = name + " - " + text;
//		else
//			this.shortDes = text;
//		// Set longDes
//		if (type == GAP_INT)
//			this.longDes = "Gap: " + (new Long(start).toString()) + "-"
//					+ (new Long(end).toString());
//		else if (type == CENTROMERE_INT)
//			this.longDes = "Centromere: " + (new Long(start).toString()) + "-"
//					+ (new Long(end).toString());
//		else if (type == GENE_INT) // mdb added 3/28/07
//			this.longDes = "Gene: " + name;
//		else if (type == EXON_INT) // mdb added 3/28/07
//			this.longDes = "Exon: " + name;
//		else if (type == FRAMEWORK_INT) // mdb added 3/28/07
//			this.longDes = "Framework Marker: " + name;
//		else
//			this.longDes = "Name: " + name;
//
//		if (text != null && text.length() != 0)
//			this.longDes += " Description: " + text;

		//if (draw == CROSS) { // mdb removed 3/31/08 #156
// mdb removed 1/8/09			
//		if (type == CENTROMERE_INT) { // mdb added 3/31/08 #156
//			crossStroke = new BasicStroke(crossWidth);
//			line = new Line2D.Double();
//		}

		rect = new Rectangle2D.Double();

		//paintRule = true; // mdb removed 3/31/08 #156
	}

	/**
	 * Method <code>setRule</code> sets a rule associated with this
	 * annotation.
	 * 
	 * @param r
	 *            a <code>Rule</code> value
	 */
// mdb removed 7/15/09 #166
//	public void setRule(Rule r) {
//		rule = r;
//	}

	/**
	 * Method <code>getRule</code> returns the associated rule or null if
	 * there is none.
	 * 
	 * @return a <code>Rule</code> value
	 */
// mdb unused 3/31/08 #156
//	public Rule getRule() {
//		return rule;
//	}

	/**
	 * Method <code>clear</code> clears the rectangle coordinates so that the
	 * annotation will not be painted and sets the associated rule to null.
	 * 
	 */
	public void clear() {
		rect.setRect(0, 0, 0, 0);
		//rule = null; // mdb removed 7/15/09 #166
	}

	/**
	 * Method <code>setRectangle</code> sets up the rectangle based on the
	 * values given adjusting the rectangle as needed.
	 * 
	 * @param boundry
	 *            a <code>Rectagle2D</code> value of the overall rectangle
	 *            that this Annotation must stay inside
	 * @param startBP
	 *            a <code>long</code> value of the start base pair of the
	 *            overall rectangle that this annotation is in
	 * @param bpPerPixel
	 *            a <code>double</code> value of the base pair per pixel used
	 * @param width
	 *            a <code>double</code> value of the desired width of the
	 *            annotation in pixels
	 */
	public void setRectangle(
			Rectangle2D boundry, 
			long startBP,
			long endBP, // mdb added endBP 3/6/07 #103
			double bpPerPixel, 
			double width, 
			boolean flip) // mdb added flip 8/3/07 #132
	{
		double y, height, oy;
		long ts, te;
		double x = boundry.getX()+(boundry.getWidth()-width)/2; // mdb added 3/31/08

		if (start > end) {
			ts = end;
			te = start;
		} else {
			ts = start;
			te = end;
		}

		if (ts < startBP)	ts = startBP;	// mdb added 3/6/07 #103
		if (te > endBP)		te = endBP;		// mdb added 3/6/07 #103
			
		if (boundry.getWidth() < width)
			width = boundry.getWidth();

		oy = boundry.getY() + boundry.getHeight();

		if (!flip) y = (ts - startBP) / bpPerPixel + boundry.getY(); // mdb added flip 8/3/07 #132
		else y = (endBP - ts) / bpPerPixel + boundry.getY();		 // mdb added flip 8/3/07 #132
		height = (te - ts) / bpPerPixel;

// mdb removed 3/31/08 #156 - TICK type is never used
//		if (draw != CROSS && draw != RECT) {
//			y = y + (height / 2.0);
//			height = markHeight;
//		}
		
		if (flip) y -= height; // mdb added 8/6/07 #132

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
	
	// mdb added 1/8/09 - get top y coord
	public double getY1() {
		return rect.getY();
	}
	
	// mdb added 1/8/09 - get bottom y coord
	public double getY2() {
		return rect.getY() + rect.getHeight() - 1;
	}

	/**
	 * Method <code>isVisible</code> returns true if after calling
	 * setRectangle, the rectangle has a width and a height greater than zero.
	 * 
	 * @return a <code>boolean</code> value
	 */
	public boolean isVisible() {
		return rect.getWidth() > 0 && rect.getHeight() > 0;
	}
	
	/**
	 * Offset the whole Annotation if it's visible.
	 * 
	 * @param x
	 *            amount to subtract from the x coordinates
	 * @param y
	 *            amount to subtract from the y coordinates
	 */
	public void setOffset(double x, double y) {
		if (isVisible()) {
			rect.x -= x;
			rect.y -= y;
// mdb removed 7/15/09 #166			
//			if (rule != null)
//				rule.setOffset(x, y);
		}
	}

	/**
	 * Method <code>setColor</code> sets the color of the rectangle
	 * 
	 * @param color
	 *            a <code>Color</code> value of color of the rectangle or null
	 *            to not set the paint
	 */
// mdb unused 3/31/08 #156
//	public void setColor(Color color) {
//		this.color = color;
//	}

	/**
	 * Method <code>getColor</code> returns this annotations associated color.
	 * 
	 * @return a <code>Color</code> value
	 */
	public Color getColor() {
		//if (color == null) { // mdb removed 3/31/08 #156
			if (type == EXON_INT)	// mdb added 3/8/07 #102
				return exonColor;	// mdb added 3/8/07 #102
			if (type == GENE_INT)
				return geneColor;
			if (type == SYGENE_INT) // mdb added 7/31/09 #167
				return sygeneColor; // mdb added 7/31/09 #167
			if (type == GAP_INT)
				return gapColor;
			if (type == FRAMEWORK_INT)
				return frameColor;
			if (type == CENTROMERE_INT)
				return centromereColor;
//		}
//		return color; // mdb removed 3/31/08 #156
		return Color.black; // mdb added 3/31/08 #156
	}

	/**
	 * Method <code>getType</code>
	 * 
	 * @return a <code>String</code> value of the type of Annotation
	 */
//	public String getType() {
//		return (String) types.get(type);
//	}
	public int getType() {
		return type;
	}

	public boolean isGene() 	  { return type == GENE_INT; }
	public boolean isFramework()  { return type == FRAMEWORK_INT; }
	public boolean isGap() 		  { return type == GAP_INT; }
	public boolean isCentromere() { return type == CENTROMERE_INT; }
	public boolean isExon() 	  { return type == EXON_INT; }   // mdb added 3/8/07 #102
	public boolean isSyGene() 	  { return type == SYGENE_INT; } // mdb added 7/31/09 #167

	/**
	 * Method <code>setDraw</code> sets the method of drawing, overriding the
	 * default method for the given type for this instance. If d is not one of
	 * the defined drawing methods (TICK, RECT, or CROSS), than TICK is used.
	 * 
	 * @param d
	 *            an <code>int</code> value
	 */
// mdb removed 3/31/08 #156
//	public void setDraw(int d) {
//		if (d != RECT && d != CROSS)
//			draw = TICK;
//		else
//			draw = d;
//		if (draw == CROSS) {
//			if (crossStroke == null)
//				crossStroke = new BasicStroke(crossWidth);
//			if (line == null)
//				line = new Line2D.Double();
//		}
//	}

	/**
	 * Method <code>getDraw</code> gets the drawing method for this annotation
	 * 
	 * @return an <code>int</code> value of drawing method which will be TICK,
	 *         RECT, or CROSS.
	 */
// mdb removed 3/31/08 #156
//	public int getDraw() {
//		return draw;
//	}
	
	// mdb added 3/8/07 #103
	public /*long*/int getStart() {
		return start;
	}
	
	// mdb added 3/8/07 #103
	public /*long*/int getEnd() {
		return end;
	}
	
	// mdb added 1/8/09 for pseudo-pseudo closeup
	public boolean getStrand() {
		return strand;
	}
	
	/**
	 * Method <code>contains</code> determines if the rectangle of this
	 * annotation contains the point p.
	 * 
	 * @param p
	 *            a <code>Point</code> value of the point
	 * @return a <code>boolean</code> value of true if the rectangle contains
	 *         p, and false otherwise.
	 */
	public boolean contains(Point p) {
		return rect.contains(p.getX(), p.getY());
	}

	/**
	 * Method <code>getText</code> returns the text description set during
	 * creation.
	 * 
	 * @return a <code>String</code> value
	 */
// mdb unused 3/31/08 #156
//	public String getText() {
//		return text;
//	}

	/**
	 * Method <code>setShortDescription</code> sets the short description for
	 * this annotation
	 * 
	 * @param des
	 *            a <code>String</code> value
	 */
// mdb unused 3/31/08 #156
//	public void setShortDescription(String des) {
//		this.shortDes = des;
//	}

	/**
	 * Method <code>setLongDescription</code> sets the short description for
	 * this annotation
	 * 
	 * @param des
	 *            a <code>String</code> value
	 */
// mdb unused 3/31/08 #156
//	public void setLongDescription(String des) {
//		this.longDes = des;
//	}

	/**
	 * Method <code>getShortDescription</code> returns the long description
	 * set during the creation of the object by type or by a call to
	 * setShortDescription(String).
	 * 
	 * @return a <code>String</code> value
	 */
	public String getShortDescription() {
		return description;
	}
	
	// mdb added 7/15/09 #166
	public Vector<String> getVectorDescription() {
		Vector<String> out = new Vector<String>();
		out.add("Location=" + start + ":" + end + "   Length=" + (end-start+1));
		for (String token : description.split(";"))
			out.add( token );
		return out;
	}

	/**
	 * Method <code>getLongDescription</code> returns the long description set
	 * during the creation of the object by type or by a call to
	 * setLongDescription(String).
	 * 
	 * @return a <code>String</code> value
	 */
	public String getLongDescription() {
		// mdb added 3/31/08 #156
		String longDes;
		if (type == GAP_INT)
			longDes = "Gap: " + (new Long(start).toString()) + "-" + (new Long(end).toString());
		else if (type == CENTROMERE_INT)
			longDes = "Centromere: " + (new Long(start).toString()) + "-" + (new Long(end).toString());
		else if (type == GENE_INT) // mdb added 3/28/07
			longDes = "Gene: " + description;
		else if (type == EXON_INT) // mdb added 3/28/07
			longDes = "Exon: " + (new Long(start).toString()) + "-" + (new Long(end).toString());
		else if (type == FRAMEWORK_INT) // mdb added 3/28/07
			longDes = "Framework Marker: " + description;
		else
			longDes = "Name: " + description;
		
		return longDes;
	}

	/**
	 * Method <code>hasLongDescription</code> returns true if the long
	 * description is not null and not an empty string.
	 * 
	 * @return a <code>boolean</code> value
	 */
// mdb unused 3/31/08 #156
//	public boolean hasLongDescription() {
//		return longDes != null && longDes.length() > 0;
//	}

	/**
	 * Method <code>hasShortDescription</code> returns true if the long
	 * description is not null and not an empty string.
	 * 
	 * @return a <code>boolean</code> value
	 */
	public boolean hasShortDescription() {
		return description != null && description.length() > 0;
	}

	/**
	 * Method <code>showAnnot</code> sets whether to show the annotation
	 * description or not if it is set.
	 * 
	 * @param show
	 *            a <code>boolean</code> value
	 */
// mdb removed 3/31/08 #156
//	public void showAnnot(boolean show) {
//		paintRule = show;
//	}

	/**
	 * Method <code>paintComponent</code> paints this annotation to the
	 * graphics object g2.
	 * 
	 * @param g2
	 *            a <code>Graphics2D</code> value of the graphics object to
	 *            paint to
	 */
	public void paintComponent(Graphics2D g2) {
		if (type >= numTypes)
			return;
		
		Color c = getColor();
		if (c != null)
			g2.setPaint(c);
		//if (draw == CROSS) { 			// mdb removed 3/31/08 #156
		if (type == CENTROMERE_INT) { 	// mdb added 3/31/08 #156
			Stroke oldstroke = g2.getStroke();
			g2.setStroke(new BasicStroke(crossWidth)/*crossStroke*/); // mdb changed 1/8/09
			
			// mdb removed 1/8/09
			//line.setLine(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height);
			//g2.draw(line);
			//line.setLine(rect.x, rect.y + rect.height, rect.x + rect.width, rect.y);
			//g2.draw(line);
			
			// mdb added 1/8/09 - faster than code above
			g2.drawLine((int)rect.x, (int)rect.y, (int)rect.x + (int)rect.width, (int)rect.y + (int)rect.height);
			g2.drawLine((int)rect.x, (int)rect.y + (int)rect.height, (int)rect.x + (int)rect.width, (int)rect.y);
			
			g2.setStroke(oldstroke);
		} 
		else { // TICK or RECT
			// Only draw full rectangle if it is large enough to see.
			if (rect.height >= 2) { // mdb added condition 8/23/07 #145
				// mdb removed 1/8/09
				//g2.fill(rect);
				//g2.draw(rect);
				
				// mdb added 1/8/09 - should be faster than above
				g2.fillRect((int)rect.x, (int)rect.y, (int)rect.width, (int)rect.height);
			}
			else // Else draw as line (much faster) // mdb added 8/23/07 #145
				//g2.draw(new Line2D.Double(rect.x, rect.y, rect.x + rect.width, rect.y)); // mdb removed 1/8/09
				g2.drawLine((int)rect.x, (int)rect.y, (int)rect.x + (int)rect.width, (int)rect.y); // mdb added 1/8/09 - 2x speed improvement over line above
		}
		
// mdb removed 7/15/09 #166
//		if (rule != null /*&& paintRule*/) // mdb removed paintRule 3/31/08 #156
//			rule.paintComponent(g2);
	}
	
	/**
	 * Method <code>toString</code> returns the string representation of this
	 * object.
	 * 
	 * @return a <code>String</code> value
	 */
	public String toString() {
		return "[Annotation: {Rect: " + rect + "}]";// {Color: " + color + "}]"; // mdb changed 3/31/08 #156
	}

	private static int getType(String/*Object*/ type) {
		int i = (byte)types.indexOf(type);
		if (i < 0) {
			i = (byte)types.size();
			types.add(type);
		}
		return i;
	}

	/**
	 * Method <code>getDefaultDraw</code> gets the default drawing method for
	 * the given type of Annotation
	 * 
	 * If the drawing method isn't explicitly defined, TICK is used.
	 * 
	 * @param type
	 *            a <code>String</code> value of the type of annotation
	 * @return an <code>int</code> value of how its drawn (TICK,RECT,CROSS)
	 */
// mdb removed 3/31/08 #156
//	public static int getDefaultDraw(String type) {
//		Integer iObj = (Integer) defaults.get(type);
//		if (iObj == null)
//			return TICK;
//		else
//			return iObj.intValue();
//	}

	/**
	 * Method <code>setDefaultDraw</code> sets the default drawing method for
	 * the given type of Annotation so that the next annotation created will use
	 * this new value. Previously created Annotations are unchanged.
	 * 
	 * @param type
	 *            a <code>String</code> value of the type of Annotation
	 * @param d
	 *            an <code>int</code> value of the drawing method
	 */
// mdb removed 3/31/08 #156
//	public static void setDefaultDraw(String type, int d) {
//		defaults.put(type, new Integer(d));
//	}
}
