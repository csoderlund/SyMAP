package symap.mapper;

import java.util.List;
import java.util.LinkedList;
import java.util.Iterator;
import java.sql.SQLException;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;	
import java.awt.event.MouseListener; 	
import java.awt.event.MouseWheelListener; 	
import javax.swing.JComponent;
import javax.swing.AbstractButton;
import symap.filter.Filtered;
import symap.filter.FilterHandler;
import symap.SyMAP;
import symap.SyMAPConstants;
import symap.drawingpanel.DrawingPanel;
import symap.track.*;
import symap.sequence.Sequence;
import symap.frame.HelpBar; 		
import symap.frame.HelpListener; 	
import symapQuery.TableDataPanel;
import util.PropertiesReader;
import util.ClearList;

/**
 * The Mapper that holds two tracks (overlaying them when drawn) and all of the
 * hits.
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class Mapper 
	extends JComponent 
	implements Filtered, HitFilter.HitFilterListener, SyMAPConstants, 
		MouseMotionListener,
		MouseListener, 		
		MouseWheelListener, 
		HelpListener 		
{
	// Constants used for signifying the type of map.
	public static final int FPC2PSEUDO 	  = 1;
	public static final int FPC2FPC 	  = 2;
	public static final int PSEUDO2PSEUDO = 3; 
	
	public static Color cloneLineColor;
	public static Color cloneLineHighlightColor;
	public static Color besLineColor;
	public static Color besLineHighlightColor;
	public static Color markerJoinDotColor;
	public static Color markerLineColor;
	public static Color sharedMarkerLineColor;
	public static Color markerLineHighlightColor;
	public static Color negOrientLineColor;
	public static Color posOrientLineColor;
	public static double markerJoinDotRadius;
	public static Color pseudoLineColor; 			
	public static Color pseudoLineHighlightColor;	
	public static Color hitRibbonBackgroundColor;	
	public static int 	hitRibbonWidth; 			
	static {
		PropertiesReader props = new PropertiesReader(SyMAP.class.getResource("/properties/mapper.properties"));
		cloneLineColor = props.getColor("cloneLineColor");
		cloneLineHighlightColor = props.getColor("cloneLineHighlightColor");
		besLineColor = props.getColor("besLineColor");
		besLineHighlightColor = props.getColor("besLineHighlightColor");
		markerJoinDotColor = props.getColor("markerJoinDotColor");
		markerJoinDotRadius = props.getDouble("markerJoinDotRadius");
		markerLineColor = props.getColor("markerLineColor");
		sharedMarkerLineColor = props.getColor("sharedMarkerLineColor");
		markerLineHighlightColor = props.getColor("markerLineHighlightColor");
		posOrientLineColor = props.getColor("posOrientLineColor");
		negOrientLineColor = props.getColor("negOrientLineColor");
		pseudoLineColor = props.getColor("pseudoLineColor"); 
		pseudoLineHighlightColor = props.getColor("pseudoLineHighlightColor"); 
		hitRibbonBackgroundColor = props.getColor("hitRibbonBackgroundColor"); 
		hitRibbonWidth = props.getInt("hitRibbonWidth"); 
	}

	private DrawingPanel drawingPanel; 
	private TrackHolder trackHolders[];
	private FilterHandler fh;
	private TableDataPanel theTablePanel;
	private List<AbstractHitData> hits;
	private MapperPool pool;
	private HitFilter hitfilter;
	private MapInfo mapinfo;
	private volatile boolean initing;
	private String helpText; 

	public Mapper(DrawingPanel drawingPanel, 
			TrackHolder th1, TrackHolder th2,
			FilterHandler fh, MapperPool pool, 
			HelpBar hb, TableDataPanel listPanel) 
	{
		super();
		this.pool = pool;
		this.drawingPanel = drawingPanel; 
		this.fh = fh;
		initing = true;
		hitfilter = new HitFilter(this);
		theTablePanel = listPanel;
		mapinfo = new MapInfo();
		fh.set(this);
		trackHolders = new TrackHolder[2];
		trackHolders[0] = th1;
		trackHolders[1] = th2;

		setOpaque(false);
		setVisible(false);
		addMouseListener(this); 		
		addMouseWheelListener(this);	
		addMouseMotionListener(this);
		hits = new ClearList(10, 50);
		
		if (hb != null) hb.addHelpListener(this,this); 
	}
	// CAS516 change to one call instead of 4
	public boolean isHitSelected(int s1, int e1, int s2, int e2) {
		if(theTablePanel != null)
			return theTablePanel.isHitSelected(s1, e1, s2, e2);
		return false;
	}
	
	public void clearData() {
		hits = new ClearList(10, 50);
		mapinfo = new MapInfo();
	}

	public void update(HitFilter hf) { // HitFilterListener interface
		if (!mapinfo.hasHitContent(hf.getBlock(), hf.getNonRepetitive()))
			init();
	}

	public MapperData getMapperData() {
		return new MapperData(this);
	}

	public void setMapperData(MapperData md) {
		if (md != null) {
			md.setMapper(this);
			init();
		}
	}

	public HitFilter getHitFilter() {
		return hitfilter;
	}

	/**
	 * Method <code>closeFilter</code> closes this mappers filter window
	 * 
	 * @see FilterHandler#hide()
	 */
	public void closeFilter() {
		fh.hide();
	}

	public String toString() {
		return "[Mapper (Track1: " + trackHolders[0].getTrack() + ") (Track2: "
				+ trackHolders[1].getTrack() + ")]";
	}
	
	/**
	 * Method <code>getHelpText</code> returns the desired text for when the mouse is
	 * over a certain point.
	 *
	 * @param event a <code>MouseEvent</code> value
	 * @return a <code>String</code> value
	 */
	public String getHelpText(MouseEvent e) { 
		if (helpText == null)
			return "Hits:  Right-click for menu.";
		else
			return helpText; 
	}
	
	public void setHelpText(String text) {
		if (text == null)
			helpText = "Hits:  Right-click for menu.";
		else
			helpText = text;
	}
	
	/**
	 * Method <code>getMapType</code> returns the type of Map (<code>FPC2PSEUDO</code>
	 * or <code>FPC2FPC</code>) which is determine by what class the tracks
	 * are in.
	 * 
	 * If either track is not set (equal to null), than <code>FPC2PSEUDO</code>
	 * is returned.
	 * 
	 * @return an <code>int</code> value of <code>FPC2PSEUDO</code> or
	 *         <code>FPC2FPC</code>
	 */
	public int getMapType() {
		Track t1 = trackHolders[0].getTrack(), t2 = trackHolders[1].getTrack();
		if (t1 instanceof Sequence && t2 instanceof Sequence)
			return PSEUDO2PSEUDO; 
		if (t1 == null || t2 == null || t1 instanceof Sequence || t2 instanceof Sequence)
			return FPC2PSEUDO;
		return FPC2FPC;
	}

	public DrawingPanel getDrawingPanel() { 
		return drawingPanel;
	}

	public void setVisible(boolean visible) {
		fh.getFilterButton().setEnabled(visible);
		super.setVisible(visible);
	}

	public AbstractButton getFilterButton() {
		return fh.getFilterButton();
	}

	/**
	 * Method <code>getTrack1</code>
	 * 
	 * @return the track in the first position
	 */
	public Track getTrack1() {
		return trackHolders[0].getTrack();
	}

	/**
	 * Method <code>getTrack1</code>
	 * 
	 * @return the track in the second position
	 */
	public Track getTrack2() {
		return trackHolders[1].getTrack();
	}

	public void clearTrackBuild() {
		if (trackHolders[0].getTrack() != null)
			trackHolders[0].getTrack().clearTrackBuild();
		if (trackHolders[1].getTrack() != null)
			trackHolders[1].getTrack().clearTrackBuild();
	}

	/**
	 * Method <code>initAllHits</code> initializes the mapper downloading all
	 * hits.
	 * 
	 * @return a <code>boolean</code> value
	 */
	public boolean initAllHits() {
		HitFilter hf = hitfilter.copy();
		hf.setNonRepetitive(false);
		hf.setBlock(false);
		return myInit(hf);
	}

	/**
	 * Method <code>init</code> gathers the needed data from the database and
	 * creates the those hit objects, clearing the hits that are not needed. If
	 * there is filter data to commit, it is committed for this Mapper and for
	 * the associated tracks.
	 * 
	 * @see MapperPool#setData
	 */
	public boolean init() {
		return myInit(hitfilter);
	}

	private boolean myInit(HitFilter hf) {
		
		Track t1 = trackHolders[0].getTrack();
		Track t2 = trackHolders[1].getTrack();
		if (t1 == null || t2 == null)
			return false;

		initing = true;

		if (SyMAP.DEBUG) System.out.println("In mapper init");
		boolean change = false;
		synchronized (hits) {
			try {
				change = pool.setData(this, t1, t2, mapinfo, hf, hits);
			} catch (SQLException s1) {
				s1.printStackTrace();
				System.err.println("First attempt at initializing Mapper failed.");
				try {
					pool.close();
					change = pool.setData(this, t1, t2, mapinfo, hf, hits);
				} catch (SQLException s2) {
					s2.printStackTrace();
					System.err.println("Second attempt at initializing Mapper failed. Giving up.");
					return false;
				}
			}
			if (change) {
				Iterator<AbstractHitData> iter = hits.iterator();
				while (iter.hasNext()) {
					((Hits) iter.next()).setMinMax(hitfilter);
				}				
			}
		}

		initing = false;
		
		return true;
	}

	/**
	 * Method <code>getSequenceMinMax</code> returns the minimum and maximum
	 * values of the visible sequence hits and cMinMax[0] and cMinMax[1].
	 * 
	 * @param minMax
	 *            a <code>int[]</code> value of current min and max such that
	 *            the values after the method call wont be greater than or less
	 *            than the initial value.
	 */
	public void getSequenceMinMax(int[] minMax) {
		if (getMapType() == FPC2PSEUDO) {
			synchronized (hits) {
				for (AbstractHitData h : hits)
					((FPCPseudoHits) h).getSequenceMinMax(minMax);
			}
		}
	}

	/**
	 * Method <code>getHitData</code> returns a list of FPCPseudoData objects
	 * with marker and bes hits that are visible, not filtered, and in the range
	 * on the pseudomolecule side of start and end.
	 * 
	 * @param start		an <code>int</code> value
	 * @param end		an <code>int</code> value
	 * @return a <code>List</code> value
	 * @see FPCPseudoHits#getHitsInRange(List,int,int)
	 */
	public List<AbstractHitData> getHitsInRange(Track src, int start, int end) {
		List<AbstractHitData> retHits = new LinkedList<AbstractHitData>();
		
		synchronized (hits) {
			for (AbstractHitData h : hits)
				h.getHitsInRange(retHits, start, end, isSwapped(src));
		}
		return retHits;
	}

	public int[] getMinMax(Track src, int start, int end) {
		int[] minMax = new int[] { Integer.MAX_VALUE, Integer.MIN_VALUE };
		
		synchronized (hits) {
			for (AbstractHitData h : hits)
				h.getMinMax(minMax, start, end, isSwapped(src));
		}
		
		if (minMax[0] == Integer.MAX_VALUE || minMax[1] == Integer.MIN_VALUE)
			return null; // no hits within range
		
		return minMax;
	}
	
	public boolean isSwapped(Track src) {
		Track t1 = getTrack1(); // left
		Track t2 = getTrack2(); // right
		return (t1 == src && !pool.hasPair(t2, t1)) || (t2 == src && !pool.hasPair(t1, t2))
				|| (isSelf() && src == t1); 
	}
	
	public boolean isSelf() {
		Track t1 = getTrack1(); // left
		Track t2 = getTrack2(); // right
		return (t1.getProject() == t2.getProject());
	}
	
	public Track getTrack(int trackNum) {
		return trackHolders[trackNum].getTrack();
	}

	protected int getOrientation(Track t) {
		if (trackHolders[0].getTrack() == t)
			return LEFT_ORIENT;
		else
			return RIGHT_ORIENT;
	}
	
	public void paintComponent(Graphics g) {
		
		Graphics2D g2 = (Graphics2D) g;
		if (!initing) {
			for (AbstractHitData h : hits)
				((Hits) h).paintComponent(g2);
		}
	}

	public void mouseMoved(MouseEvent e) {
		if (!initing)
			for (Iterator iter = hits.iterator(); iter.hasNext(); ((Hits) iter.next()).mouseMoved(e));
	}
	
	public void mouseDragged(MouseEvent e) { }
	
	public void mouseClicked(MouseEvent e) { }
	public void mouseEntered(MouseEvent e) { }
	
	public void mouseExited(MouseEvent e) {
		if (!initing)
			for (Iterator iter = hits.iterator(); 
				iter.hasNext(); 
				((Hits) iter.next()).mouseExited(e));
	}
	
	public void mousePressed(MouseEvent e) {
		if (e.isPopupTrigger()) 
			fh.showPopup(e);
	} 
	
	public void mouseReleased(MouseEvent e) { }
	
	public void mouseWheelMoved(MouseWheelEvent e) {
		long length = trackHolders[0].getTrack().getTrackSize();
		for (int i = 0;  i < trackHolders.length;  i++)
			length = Math.min(length,trackHolders[i].getTrack().getEnd()-trackHolders[i].getTrack().getStart()+1);
		for (int i = 0;  i < trackHolders.length;  i++)
			trackHolders[i].getTrack().mouseWheelMoved(e, length);
	}
}
