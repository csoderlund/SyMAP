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
import java.awt.event.MouseWheelEvent;	// mdb added 2/28/08 #153
import java.awt.event.MouseListener; 	// mdb added 3/1/07 #100
import java.awt.event.MouseWheelListener; 	// mdb added 2/28/08 #153
import javax.swing.JComponent;
import javax.swing.AbstractButton;
import symap.filter.Filtered;
import symap.filter.FilterHandler;
import symap.SyMAP;
import symap.SyMAPConstants;
import symap.drawingpanel.DrawingPanel;
import symap.track.*;
import symap.mapper.PseudoPseudoData.PseudoHitData;
import symap.sequence.Sequence;
import symap.frame.HelpBar; 		// mdb added 3/21/07 #104
import symap.frame.HelpListener; 	// mdb added 3/21/07 #104
import symapQuery.ListDataPanel;
import util.PropertiesReader;
import util.ClearList;

/**
 * The Mapper that holds two tracks (overlaying them when drawn) and all of the
 * hits.
 * 
 * @author Austin Shoemaker
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class Mapper 
	extends JComponent 
	implements Filtered, HitFilter.HitFilterListener, SyMAPConstants, 
		MouseMotionListener,
		MouseListener, 		// mdb added 3/1/07 #100
		MouseWheelListener, // mdb added 2/28/08 #153
		HelpListener 		// mdb added 3/21/07 #104
{
	//private static final boolean TIME_TRACE = false;

	// Constants used for signifying the type of map.
	public static final int FPC2PSEUDO 	  = 1;
	public static final int FPC2FPC 	  = 2;
	public static final int PSEUDO2PSEUDO = 3; // mdb added 7/11/07 #121
	
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
	public static Color pseudoLineColor; 			// mdb added 7/12/07 #121
	public static Color pseudoLineHighlightColor;	// mdb added 7/12/07 #121
	public static Color hitRibbonBackgroundColor;	// mdb added 8/22/07 #126
	public static int 	hitRibbonWidth; 			// mdb added 8/29/07 #126
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
		pseudoLineColor = props.getColor("pseudoLineColor"); // mdb added 7/12/07 #121
		pseudoLineHighlightColor = props.getColor("pseudoLineHighlightColor"); // mdb added 7/12/07 #121
		hitRibbonBackgroundColor = props.getColor("hitRibbonBackgroundColor"); // mdb added 8/22/07 #126
		hitRibbonWidth = props.getInt("hitRibbonWidth"); // mdb added 8/29/07 #126
	}

	private DrawingPanel drawingPanel; // mdb added 3/1/07
	private TrackHolder trackHolders[];
	private FilterHandler fh;
	private ListDataPanel theParentPanel;
	private List<AbstractHitData> hits;
	private MapperPool pool;
	private HitFilter hitfilter;
	private MapInfo mapinfo;
	private volatile boolean initing;
	private String helpText; // mdb added 1/31/08 

	public Mapper(DrawingPanel drawingPanel, 
			TrackHolder th1, TrackHolder th2,
			FilterHandler fh, MapperPool pool, 
			HelpBar hb, ListDataPanel listPanel) // mdb added 3/21/07 #104
	{
		super();
		this.pool = pool;
		this.drawingPanel = drawingPanel; // mdb added 3/1/07 #100
		this.fh = fh;
		initing = true;
		hitfilter = new HitFilter(this);
		theParentPanel = listPanel;
		mapinfo = new MapInfo();
		fh.set(this);
		trackHolders = new TrackHolder[2];
		trackHolders[0] = th1;
		trackHolders[1] = th2;

		setOpaque(false);
		setVisible(false);
		addMouseListener(this); 		// mdb added 3/1/07 #100
		addMouseWheelListener(this);	// mdb added 2/28/08 #153
		addMouseMotionListener(this);
		hits = new ClearList(10, 50);
		
		if (hb != null) hb.addHelpListener(this,this); // mdb added 3/21/07 #104
	}
	
	public long getSelectedSeq1Start() {
		if(theParentPanel != null)
			return theParentPanel.getSelectedSeq1Start();
		return -1;
	}

	public long getSelectedSeq2Start() {
		if(theParentPanel != null)
			return theParentPanel.getSelectedSeq2Start();
		return -1;
	}

	public long getSelectedSeq1End() {
		if(theParentPanel != null)
			return theParentPanel.getSelectedSeq1End();
		return -1;
	}

	public long getSelectedSeq2End() {
		if(theParentPanel != null)
			return theParentPanel.getSelectedSeq2End();
		return -1;
	}

	public void clearData() {
		hits = new ClearList(10, 50);//clear();	// mdb changed 2/3/10
		mapinfo = new MapInfo();//.clear();		// mdb changed 2/3/10
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
	public String getHelpText(MouseEvent e) { // mdb added 3/21/07 #104
		if (helpText == null)
			return "Hits:  Right-click for menu.";
		else
			return helpText; // mdb added 1/31/08
	}
	
	// mdb added 1/31/08
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
			return PSEUDO2PSEUDO; // mdb added 7/11/07 #121
		if (t1 == null || t2 == null || t1 instanceof Sequence || t2 instanceof Sequence)
			return FPC2PSEUDO;
		return FPC2FPC;
	}

	public DrawingPanel getDrawingPanel() { // mdb added 3/1/07 #100
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
		//long cStart = System.currentTimeMillis();
		
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
		if (SyMAP.DEBUG) System.out.println("Leaving Mapper Init - changed = " + change);
		//if (TIME_TRACE) System.out.println("Mapper: myInit() time = "+(System.currentTimeMillis()-cStart)+" ms");
		
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

	// mdb added 4/23/09 #161
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
	
	// mdb added 4/2/09 #160
	public boolean isSwapped(Track src) {
		Track t1 = getTrack1(); // left
		Track t2 = getTrack2(); // right
		return (t1 == src && !pool.hasPair(t2, t1)) || (t2 == src && !pool.hasPair(t1, t2))
				|| (isSelf() && src == t1); // mdb added condition 12/11/09 - fix self-alignment closeup
	}
	
	// mdb added 9/8/09
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
		//long cStart = System.currentTimeMillis();
		//super.paintComponent(g); // mdb removed 7/16/09 - not necessary
		
		Graphics2D g2 = (Graphics2D) g;
		if (!initing) {
			for (AbstractHitData h : hits)
				((Hits) h).paintComponent(g2);
		}
		
		//if (TIME_TRACE) System.out.println("Mapper: paint time = "+(System.currentTimeMillis()-cStart)+" ms");
	}

	public void mouseMoved(MouseEvent e) {
		if (!initing)
			for (Iterator iter = hits.iterator(); iter.hasNext(); ((Hits) iter.next()).mouseMoved(e));
	}
	
	public void mouseDragged(MouseEvent e) { }
	
	// mdb added 3/1/07 #100
	public void mouseClicked(MouseEvent e) { }
	public void mouseEntered(MouseEvent e) { }
	
	// mdb added 3/1/07 #100
	public void mouseExited(MouseEvent e) {
		if (!initing)
			for (Iterator iter = hits.iterator(); 
				iter.hasNext(); 
				((Hits) iter.next()).mouseExited(e));
	}
	
	// mdb added 3/1/07 #100
	public void mousePressed(MouseEvent e) {
		// mdb added 3/13/07 #104
		if (e.isPopupTrigger()) 
			fh.showPopup(e);
	} 
	
	// mdb added 3/1/07 #100
	public void mouseReleased(MouseEvent e) { }
	
	// mdb added 2/28/08 #153
	public void mouseWheelMoved(MouseWheelEvent e) {
		long length = trackHolders[0].getTrack().getTrackSize();
		for (int i = 0;  i < trackHolders.length;  i++)
			length = Math.min(length,trackHolders[i].getTrack().getEnd()-trackHolders[i].getTrack().getStart()+1);
		for (int i = 0;  i < trackHolders.length;  i++)
			trackHolders[i].getTrack().mouseWheelMoved(e, length);
	}
}
