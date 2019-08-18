package symap.closeup.components;

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

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.Arrays;

import util.PropertiesReader;
import util.DoubleDimension;
import util.Ruler;
import symap.SyMAP;
import symap.closeup.alignment.HitAlignment;
import symap.closeup.alignment.GeneAlignment;

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class CloseUpComponent extends JComponent {
	public static Color backgroundColor;
	public static Color rulerColor;
	public static Color rulerFontColor;
	public static Color markerColor;
	public static Color besColor;
	public static Color outsideColor;
	public static Color missColor;
	public static Color deleteColor;
	public static Color insertColor;
	public static Color exonColor;
	public static Color intronColor;

	private static Font rulerFont;
	private static Font hitFont, geneFont;

	public static Color hitFontColor, geneFontColor;

	public static final double LINE_HEIGHT;
	public static final DoubleDimension ARROW_DIMENSION;
	public static final double MISS_HEIGHT;
	public static final double DELETE_HEIGHT;
	public static final DoubleDimension INSERT_DIMENSION;
	public static final double INSERT_OFFSET;
	public static final double VERTICAL_HIT_SPACE, HORIZONTAL_HIT_SPACE;
	public static final double VERTICAL_GENE_SPACE, HORIZONTAL_GENE_SPACE;
	private static final int VERTICAL_BORDER, HORIZONTAL_BORDER;
	public static final double EXON_HEIGHT;
	public static final double INTRON_HEIGHT;
	public static final double EXON_ARROW_WIDTH;
	public static final double MIN_BP_PER_PIXEL;

	private static final Dimension2D RULER_ARROW_DIM;
	private static final Dimension2D RULER_TICK_DIM;

	private static final double RULER_TICK_SPACE;
	private static final double RULER_LINE_THICKNESS;
	private static final double RULER_OFFSET;
	static {
		PropertiesReader props = new PropertiesReader(SyMAP.class.getResource("/properties/closeup.properties"));
		backgroundColor = props.getColor("backgroundColor");
		rulerColor = props.getColor("rulerColor");
		rulerFontColor = props.getColor("rulerFontColor");
		rulerFont = props.getFont("rulerFont");
		markerColor = props.getColor("markerColor");
		besColor = props.getColor("besColor");
		outsideColor = props.getColor("outsideColor");
		missColor = props.getColor("missColor");
		deleteColor = props.getColor("deleteColor");
		insertColor = props.getColor("insertColor");
		LINE_HEIGHT = props.getDouble("lineHeight");
		ARROW_DIMENSION = props.getDoubleDimension("arrowDimension");
		DELETE_HEIGHT = props.getDouble("deleteHeight");
		INSERT_DIMENSION = props.getDoubleDimension("insertDimension");
		INSERT_OFFSET = props.getDouble("insertOffset");
		MISS_HEIGHT = props.getDouble("missHeight");
		VERTICAL_HIT_SPACE = props.getDouble("verticalHitSpace");
		HORIZONTAL_HIT_SPACE = props.getDouble("horizontalHitSpace");
		VERTICAL_GENE_SPACE = props.getDouble("verticalGeneSpace");
		HORIZONTAL_GENE_SPACE = props.getDouble("horizontalGeneSpace");
		HORIZONTAL_BORDER = props.getInt("horizontalBorder");
		VERTICAL_BORDER = props.getInt("verticalBorder");

		exonColor = props.getColor("exonColor");
		intronColor = props.getColor("intronColor");
		EXON_HEIGHT = props.getDouble("exonHeight");
		INTRON_HEIGHT = props.getDouble("intronHeight");
		EXON_ARROW_WIDTH = props.getDouble("exonArrowWidth");
		MIN_BP_PER_PIXEL = props.getDouble("minBpPerPixel");

		RULER_ARROW_DIM = props.getDoubleDimension("rulerArrowDimension");
		RULER_TICK_DIM = props.getDoubleDimension("rulerTickDimension");
		RULER_TICK_SPACE = props.getDouble("rulerTickSpace");
		RULER_LINE_THICKNESS = props.getDouble("rulerLineThickness");
		RULER_OFFSET = props.getDouble("rulerOffset");

		hitFont = props.getFont("hitFont");
		geneFont = props.getFont("geneFont");
		hitFontColor = props.getColor("hitFontColor");
		geneFontColor = props.getColor("geneFontColor");
	}

	public static final double HIT_HEIGHT;
	static {
		double part = Math.max(Math.max(Math.max(MISS_HEIGHT, DELETE_HEIGHT),
				ARROW_DIMENSION.height), LINE_HEIGHT);
		HIT_HEIGHT = Math.max(part, INSERT_DIMENSION.height + INSERT_OFFSET
				+ (part / 2.0));
	}

	public static final double GENE_HEIGHT = Math.max(EXON_HEIGHT, INTRON_HEIGHT);
	private static final double FONT_SPACE = 3;

	private HitAlignment[] ha;
	private GeneAlignment[] ga;
	private int start, end;
	private Ruler ruler;
	private Vector<CloseUpListener> listeners;

	public CloseUpComponent() {
		super();
		setBackground(backgroundColor);
		listeners = new Vector<CloseUpListener>();
		addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				notifyListenersOfClick(getHitAlignment(e.getPoint()));
			}
		});
		ruler = new Ruler(RULER_ARROW_DIM, RULER_ARROW_DIM, RULER_TICK_DIM,
				true, RULER_TICK_SPACE, RULER_LINE_THICKNESS);
	}

	public void addCloseUpListener(CloseUpListener cl) {
		if (!listeners.contains(cl))
			listeners.add(cl);
	}

	public void removeCloseUpListener(CloseUpListener cl) {
		listeners.remove(cl);
	}

	public void set(int start, int end, GeneAlignment[] ga, HitAlignment[] ha) {
		this.start = start;
		this.end = end;
		this.ga = ga;
		this.ha = ha;
		if (this.ga != null)
			Arrays.sort(this.ga);
		if (this.ha != null)
			Arrays.sort(this.ha);
		setPreferredSize(getWidth());
	}

	public int getNumberOfHits() { return (ha == null ? 0 : ha.length); }
	public int getStart() { return start; }
	public int getEnd() { return end; }
	public int getLength() { return Math.abs(end - start) + 1; }

	public BlastComponent[] getBlastComponents() {
		BlastComponent[] bc = new BlastComponent[ha == null ? 0 : ha.length];
		for (int i = 0; i < bc.length; i++)
			bc[i] = new BlastComponent(ha[i]);
		return bc;
	}

	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		if (ga == null || ha == null)
			return;

// mdb removed 12/29/08 for pseudo-pseudo closeup - incorrectly drawing dotted line to end of window		
//		double bpPerPixel = getBpPerPixel();
//		if (bpPerPixel <= 0)
//			return;
		double bpPerPixel = 1; // mdb added 12/29/08 for pseudo-pseudo closeup

		Graphics2D g2 = (Graphics2D) g;
		ruler.paint(g2, rulerColor, rulerFontColor, rulerFont);
		FontMetrics fm = getFontMetrics(hitFont);

		Vector<Long> uniqueClones = new Vector<Long>(); // ASD: kludge for printing the name and dotted line only once
		for (int i = 0; i < ha.length; ++i) {
			long id = ha[i].getID();
			if (!uniqueClones.contains(id)) {
 				ha[i].paintName(g2, fm, hitFontColor, FONT_SPACE, bpPerPixel);
				ha[i].paintLine(g2, bpPerPixel, getStart(), HORIZONTAL_BORDER);
				uniqueClones.add(id);
			}
		}
		uniqueClones = null;

		// Paint the hit segments
		for (int i = 0; i < ha.length; ++i)
			ha[i].paint(g2);
		
		// Paint the genes
		fm = getFontMetrics(geneFont);
		for (int i = 0; i < ga.length; ++i) 
			ga[i].paint(g2, fm, geneFontColor, FONT_SPACE);
	}

// mdb removed 12/29/08 for pseudo-pseudo closeup	
//	private double getBpPerPixel() {
//		if (ha == null || ga == null)
//			return 0;
//		double pixels = getWidth() - 2 * HORIZONTAL_BORDER;
//		if (pixels <= 0)
//			return 0;
//		return getLength() / pixels;
//	}

	public void setPreferredSize(Dimension d) {
		setPreferredSize(d.width);
	}

	public void setPreferredSize(double width) {
		Dimension d = new Dimension();
		if (ha != null && ga != null) {
			double bpPerPixel = Math.max(MIN_BP_PER_PIXEL, getLength()
					/ (width - 2 * HORIZONTAL_BORDER));
			ruler.setBounds(getStart(), getEnd(), HORIZONTAL_BORDER,
					VERTICAL_BORDER, bpPerPixel, getFontMetrics(rulerFont));
			double h = ruler.getBounds().getX() + ruler.getBounds().getHeight()
					+ RULER_OFFSET;

			d.width = (int) Math.ceil(getLength() / bpPerPixel + 2
					* HORIZONTAL_BORDER);
			h = setHitLayers(h, bpPerPixel);
			h = setGeneLayers(h, bpPerPixel);
			d.height = (int) Math.ceil(h + VERTICAL_BORDER);
		}
		super.setPreferredSize(d);
	}

	private double setGeneLayers(double y, double bpPerPixel) {
		int[] layers = getGeneLayers(bpPerPixel);
		double layerSize = GENE_HEIGHT + VERTICAL_GENE_SPACE
				+ getFontHeight(geneFont) + FONT_SPACE;
		int max = -1;
		int startBP = getStart();
		for (int i = 0; i < ga.length; ++i) {
			if (layers[i] > max)
				max = layers[i];
			ga[i].setBounds(startBP, HORIZONTAL_BORDER, bpPerPixel, layers[i] * layerSize + y, start, end);
		}
		return ((max + 1) * layerSize) + y;
	}

	private int[] getGeneLayers(double bpPerPixel) {
		if (ga == null || ga.length == 0)
			return new int[0];

		int[] layers = new int[ga.length];
		double[] ends = new double[ga.length];
		Arrays.fill(layers, -1);
		Arrays.fill(ends, Double.NEGATIVE_INFINITY);
		int startBP = getStart();
		for (int i = 0; i < ga.length; ++i) {
			double x = ga[i].getX(startBP, 0, bpPerPixel, start);
			for (int j = 0; j < ends.length; ++j) {
				if (ends[j] <= x || ends[j] < 0) {
					layers[i] = j;
					break;
				}
			}
			ends[layers[i]] = x + ga[i].getWidth(bpPerPixel, start, end) + HORIZONTAL_GENE_SPACE;
		}
		return layers;
	}

	private double setHitLayers(double y, double bpPerPixel) {
		final double layerHeight = HIT_HEIGHT + VERTICAL_HIT_SPACE + getFontHeight(hitFont) + FONT_SPACE;
		int numLayers = makeHitLayers();
		
		for (HitAlignment h : ha)
			h.setBounds(getStart(), HORIZONTAL_BORDER, bpPerPixel, h.getLayer() * layerHeight + y);

		return ((numLayers + 1) * layerHeight) + y;
	}

	// mdb rewritten 1/6/09 for pseudo-pseudo closeup
	private int makeHitLayers() {
		if (ha == null || ha.length == 0 || ga == null)
			return 0;

		Vector<Layer> layers = new Vector<Layer>();
		Map<Long,Integer> map = new HashMap<Long,Integer>(); // < hit_id, layer_num >
		int layerNum;
		
		for (HitAlignment h : ha) {	
			int start = h.getMinTarget();
			int end = start + h.getWidthTarget() - 1;

			// First check to see if part of previously layered clone
			long id = h.getID();
			if (map.containsKey(id)) {
				layerNum = map.get(id).intValue();
				h.setLayer(layerNum);
				layers.get(layerNum).extend(start, end);
				continue;
			}
			
			// Otherwise, new clone - see where it fits
			for (layerNum = 0;  layerNum < layers.size() && layers.get(layerNum).contains(start, end);  layerNum++);
			
			// It fit in an existing layer
			if (layerNum < layers.size()) {
				layers.get(layerNum).extend(start, end);
				map.put(id, new Integer(layerNum));
				h.setLayer(layerNum);
				continue;
			}
			
			// Otherwise, make new layer
			layers.add(new Layer(start, end));
			map.put(id, new Integer(layerNum));
			h.setLayer(layerNum);
		}
		
		return layers.size();
	}

	private HitAlignment getHitAlignment(Point p) {
		if (ha != null) {
			for (HitAlignment h : ha)
				if (h.contains(p))
					return h;
		}
		return null;
	}

	private void notifyListenersOfClick(HitAlignment h) {
		if (h != null)
			for (int i = 0; i < listeners.size(); ++i)
				((CloseUpListener) listeners.get(i)).hitClicked(h);
	}

	private double getFontHeight(Font font) {
		return (double) getFontMetrics(font).getHeight() + FONT_SPACE;
	}

	// ASD added helper class (defines a structure) for change to single layer 5/14/2006
	private class Layer {
		private int start, end;

		public Layer(int start, int end) {
			this.start = start;
			this.end   = end;
		}

		public void extend(int start, int end) {
			if (start < this.start)
				this.start = start;
			if (end > this.end)
				this.end = end;
		}

		public boolean contains(int start, int end) {
			if ((start >= this.start && start <= this.end)
				|| (end >= this.start && end <= this.end))
				return true;
			
			return false;
		}
	}
}
