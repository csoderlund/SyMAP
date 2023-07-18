package symap.closeup;

import java.awt.Color;
import java.awt.Font;
import java.awt.Point;
import java.awt.Graphics;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.FontMetrics;
import java.awt.geom.Dimension2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;

import props.PropertiesReader;
import symap.drawingpanel.SyMAP2d;

import java.util.Vector;
import java.util.Arrays;
import java.util.TreeMap;

/***************************************************************
 * Displays the graphical portion of the Closeup Align
 */

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class CloseUpComponent extends JComponent {
	// colors and finals are at the bottom
	private HitAlignment[] hitAlign;
	private GeneAlignment[] geneAlign;
	private int startGr, endGr;			// start and end of graphics
	private Ruler ruler;
	private Vector<CloseUpDialog> listeners;

	/******************************************************
	 * XXX 
	 */
	public CloseUpComponent() {
		super();
		setBackground(backgroundColor);
		listeners = new Vector<CloseUpDialog>();
		addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				notifyListenersOfClick(getHitAlignment(e.getPoint()));
			}
		});
		ruler = new Ruler(RULER_ARROW_DIM, RULER_ARROW_DIM, RULER_TICK_DIM,
				true, RULER_TICK_SPACE, RULER_LINE_THICKNESS);
	}

	public void addCloseUpListener(CloseUpDialog cl) {if (!listeners.contains(cl))listeners.add(cl);}
	public void removeCloseUpListener(CloseUpDialog cl) {listeners.remove(cl);}

	// start = min(start_exon, start_subhit), end=max(end_exon, end_subhit)
	public void setData(int startG, int endG, GeneAlignment[] ga, HitAlignment[] ha) {
		this.startGr = startG;
		this.endGr = endG;
		this.geneAlign = ga;
		this.hitAlign = ha;
		
		if (this.geneAlign != null) Arrays.sort(this.geneAlign);
		if (this.hitAlign  != null) Arrays.sort(this.hitAlign);
		
		setPreferredSize(getWidth());
	}

	public int getNumberOfHits() { return (hitAlign == null ? 0 : hitAlign.length); }
	
	private int getLengthG() { return Math.abs(endGr - startGr) + 1; }

	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		if (geneAlign == null || hitAlign == null) return; // will have 0 length if no genes

		double bpPerPixel = 1; 

		Graphics2D g2 = (Graphics2D) g;
		ruler.paint(g2, rulerColor, rulerFontColor, rulerFont);
		FontMetrics fm = getFontMetrics(hitFont);
 
		// Paint the hit segments
		for (int i = 0; i < hitAlign.length; ++i) {
			hitAlign[i].paintName(g2, fm, hitFontColor, FONT_SPACE, bpPerPixel);
			hitAlign[i].paintLine(g2, bpPerPixel, startGr, HORZ_BORDER);
			hitAlign[i].paint(g2);
		}
		
		// Paint the genes
		fm = getFontMetrics(geneFont);
		for (int i = 0; i < geneAlign.length; ++i) 
			geneAlign[i].paint(g2, fm, geneFontColor, FONT_SPACE);
	}
	/****************************************************************************/
	public void setPreferredSize(Dimension d) {
		setPreferredSize(d.width);
	}

	private void setPreferredSize(double width) {
		// CAS534 would not have done hit overlaps if no genes; if (hitAlign != null && geneAlign != null) {
		
		double grLen = getLengthG();
		
		double lenBP = grLen/(width-2 * HORZ_BORDER);
		
		double bpPerPixel = Math.max(1, lenBP);
		
		ruler.setBounds(startGr, endGr, HORZ_BORDER, VERT_BORDER, bpPerPixel, getFontMetrics(rulerFont));
		
		// height
		double ht = ruler.getBounds().getX() + ruler.getBounds().getHeight()+ RULER_OFFSET;
		ht = buildHitLayers(ht, bpPerPixel);
		if (geneAlign!=null) 
			ht = buildGeneLayers(ht, bpPerPixel);
		
		Dimension d = new Dimension();
		d.width  = (int) Math.ceil(grLen/bpPerPixel + 2*HORZ_BORDER);
		d.height = (int) Math.ceil(ht + VERT_BORDER);
		
		super.setPreferredSize(d);
	}

	private double buildGeneLayers(double y, double bpPerPixel) {
		int [] layers = assignGeneLayers(bpPerPixel);
		
		double layerSize = GENE_HEIGHT + VERT_GENE_SPACE + getFontHeight(geneFont) + FONT_SPACE;
		int max = -1;
		
		for (int i = 0; i < geneAlign.length; ++i) {
			if (layers[i] > max) max = layers[i];
			
			geneAlign[i].setBounds(startGr, endGr, HORZ_BORDER, bpPerPixel, layers[i] * layerSize + y);
		}
		return ((max + 1) * layerSize) + y;
	}

	private int[] assignGeneLayers(double bpPerPixel) {
		if (geneAlign == null || geneAlign.length == 0) return new int[0];

		int[] layers  = new int[geneAlign.length];
		double[] ends = new double[geneAlign.length];
		Arrays.fill(layers, -1);
		Arrays.fill(ends, Double.NEGATIVE_INFINITY);
		
		for (int i = 0; i < geneAlign.length; ++i) {
			double x = geneAlign[i].getX(startGr, 0, bpPerPixel);
			for (int j = 0; j < ends.length; ++j) {
				if (ends[j] <= x || ends[j] < 0) {
					layers[i] = j;
					break;
				}
			}
			ends[layers[i]] = x + geneAlign[i].getWidth(bpPerPixel, startGr, endGr) + HORZ_GENE_SPACE;
		}
		return layers;
	}

	private double buildHitLayers(double y, double bpPerPixel) {
		final double layerHeight = HIT_HEIGHT + VERT_HIT_SPACE + getFontHeight(hitFont) + FONT_SPACE;
		
		int numLayers = assignHitLayers(); 
		
		for (HitAlignment h : hitAlign)
			h.setBounds(startGr, HORZ_BORDER, bpPerPixel, h.getLayer() * layerHeight + y);

		return ((numLayers + 1) * layerHeight) + y;
	}
	// CAS531 previous, a different hit was on a different layer; now, overlapping hits go on top layer of 2
	// layer 1 is the lower layer, and layer 0 is upper; has to have 2 layers
	private int assignHitLayers() {
		if (hitAlign == null || hitAlign.length == 0) return 0;

		int layerNum=1, lastEnd=0, lastLayer=0, maxLayer=2;
		
		// sort on start
		TreeMap <Integer, HitAlignment> hitOrd = new TreeMap <Integer, HitAlignment> ();
		for (HitAlignment haObj : hitAlign) {
			hitOrd.put(haObj.getSstart(), haObj);
		}
		for (int start : hitOrd.keySet()) {
			HitAlignment haObj = hitOrd.get(start);
			int end =   haObj.getSend();
			end   = start + haObj.getWidthAlign();

			if (start>lastEnd) 	  layerNum=1;
			else {
				if (lastLayer==1) layerNum=0; 
				else              layerNum=1;
			}
			lastEnd = end+10;
			lastLayer = layerNum;
		
			haObj.setLayer(layerNum);
		}
		return maxLayer;
	}
	/*******************************************************************/
	private HitAlignment getHitAlignment(Point p) {
		if (hitAlign != null) {
			for (HitAlignment h : hitAlign)
				if (h.contains(p))
					return h;
		}
		return null;
	}

	private void notifyListenersOfClick(HitAlignment h) {
		if (h != null)
			for (int i = 0; i < listeners.size(); ++i)
				((CloseUpDialog) listeners.get(i)).hitClicked(h);
	}

	private double getFontHeight(Font font) {
		return (double) getFontMetrics(font).getHeight() + FONT_SPACE;
	}

	/********************************************************************************/
	private static Color rulerFontColor= new Color(0,0,0);
	private static Color hitFontColor = Color.black, geneFontColor=Color.black;
	
	private static Font rulerFont =  new Font("Courier",0,14);
	private static Font hitFont =    new Font("Arial",0,12); // CAS541 Ariel->Arial
	private static Font geneFont =   new Font("Arial",0,12);
	private static final double VERT_HIT_SPACE=2; // CAS531 3->2, no diff
	private static final double VERT_GENE_SPACE=5, HORZ_GENE_SPACE=8;
	private static final int 	VERT_BORDER=12, HORZ_BORDER=15; // CAS531 VERT 15->12
	private static final double RULER_TICK_SPACE=60, RULER_LINE_THICKNESS=3, RULER_OFFSET=12; // CAS531 30->12 - no diff
	private static final double FONT_SPACE = 3;
	
	// HitAlignment
	public static final double LINE_HEIGHT=2;	
	public static final double MISS_HEIGHT=9, DELETE_HEIGHT=9;
	public static final DoubleDimension INSERT_DIM = new DoubleDimension(6,6);
	public static final double INSERT_OFFSET=5;
	public static final DoubleDimension ARROW_DIM = new DoubleDimension(9,15);
	public static final double HIT_HEIGHT = 15; // CAS531 was a complicated equation that always gave 18
	
	// Gene Alignment
	public static final DoubleDimension GARROW_DIM = new DoubleDimension(10,15); //w,h
	public static final double EXON_HEIGHT=12, INTRON_HEIGHT=2;
	private static final double GENE_HEIGHT = Math.max(EXON_HEIGHT, INTRON_HEIGHT);

	private static final Dimension2D RULER_ARROW_DIM = new Dimension(10,15); // w,h
	private static final Dimension2D RULER_TICK_DIM = new Dimension(2,9);

	// Set by user in color wheel; defaults in closeup.properties
	public static Color backgroundColor, rulerColor, hitColor;
	public static Color missColor, deleteColor, insertColor;
	public static Color intronColor, exonColorP, exonColorN; // CAS535 split exon into p/n
	
	static {
		PropertiesReader props = new PropertiesReader(SyMAP2d.class.getResource("/properties/closeup.properties"));
		backgroundColor = props.getColor("backgroundColor");
		rulerColor = props.getColor("rulerColor");
		hitColor = props.getColor("hitColor");
		missColor = props.getColor("missColor");
		deleteColor = props.getColor("deleteColor");
		insertColor = props.getColor("insertColor");
		exonColorP = props.getColor("exonColorP");
		exonColorN = props.getColor("exonColorN");
		intronColor = props.getColor("intronColor");
		
		// CAS531 removed all the constants from closeup.properties, e.g. below, except those that user can change
		//ARROW_DIM = props.getDoubleDimension("arrowDimension");
		//DELETE_HEIGHT = props.getDouble("deleteHeight");
		//geneFont = props.getFont("geneFont");
	}
}
