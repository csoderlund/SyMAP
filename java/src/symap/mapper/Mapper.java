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
import util.ErrorReport;

/**
 * The Mapper that holds two tracks (overlaying them when drawn) and all of the hits.
 * Called from DrawingPanel
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class Mapper extends JComponent 
	implements  HitFilter.HitFilterListener, SyMAPConstants, // CAS521 remove Filtered Interface
		MouseMotionListener, MouseListener, MouseWheelListener, HelpListener 		
{
	// Constants used for signifying the type of map.
	public static final int FPC2PSEUDO 	  = 1;
	public static final int FPC2FPC 	  = 2;
	public static final int PSEUDO2PSEUDO = 3; 
	
	// drawing methods directly access these, and ColorDialog can change them
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
	public static Color pseudoLineColorPP; 
	public static Color pseudoLineColorPN;
	public static Color pseudoLineColorNP;
	public static Color pseudoLineColorNN;
	public static Color pseudoLineHoverColor;		// CAS520 renamed to Hover and use Highlight for Highlight	
	public static Color pseudoLineHighlightColor1;
	public static Color pseudoLineHighlightColor2;
	public static Color hitRibbonBackgroundColor;	
	public static int 	hitRibbonWidth; 			
	static {
		PropertiesReader props = new PropertiesReader(SyMAP.class.getResource("/properties/mapper.properties"));
		cloneLineColor = 			props.getColor("cloneLineColor");
		cloneLineHighlightColor = 	props.getColor("cloneLineHighlightColor");
		besLineColor = 				props.getColor("besLineColor");
		besLineHighlightColor = 	props.getColor("besLineHighlightColor");
		markerJoinDotColor = 		props.getColor("markerJoinDotColor");
		markerJoinDotRadius = 		props.getDouble("markerJoinDotRadius");
		markerLineColor = 			props.getColor("markerLineColor");
		sharedMarkerLineColor = 	props.getColor("sharedMarkerLineColor");
		markerLineHighlightColor = 	props.getColor("markerLineHighlightColor");
		posOrientLineColor = 		props.getColor("posOrientLineColor");
		negOrientLineColor = 		props.getColor("negOrientLineColor");
		pseudoLineColorPP = 		props.getColor("pseudoLineColorPP"); 
		pseudoLineColorPN = 		props.getColor("pseudoLineColorPN"); 
		pseudoLineColorNP = 		props.getColor("pseudoLineColorNP"); 
		pseudoLineColorNN = 		props.getColor("pseudoLineColorNN"); 
		pseudoLineHoverColor = 		props.getColor("pseudoLineHoverColor");
		pseudoLineHighlightColor1 = 	props.getColor("pseudoLineHighlightColor1"); 
		pseudoLineHighlightColor2 = 	props.getColor("pseudoLineHighlightColor2"); 
		hitRibbonBackgroundColor = 	props.getColor("hitRibbonBackgroundColor"); 
		hitRibbonWidth = 			props.getInt("hitRibbonWidth"); 
	}

	private DrawingPanel drawingPanel; 
	private TrackHolder trackHolders[];
	private FilterHandler fh;
	private TableDataPanel theTablePanel;
	private List<AbstractHitData> hitList;
	private MapperPool pool;
	private HitFilter hitfilter;
	private MapInfo mapinfo;
	private volatile boolean initing;
	private String helpText;
	private String hoverText=""; // CAS520 add
	
	private static final String HOVER_MESSAGE = 
			"\nHit-wire information: Hover on hit-wire or " 
			+ "right-click on hit-wire for popup of full information.\n";
			
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
		hoverText = hitfilter.getHover(); // CAS520 add
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
		hitList = new ClearList (10, 50);
		
		if (hb != null) hb.addHelpListener(this,this); 
	}
	
	
	public void clearData() {
		hitList = new ClearList(10, 50);
		mapinfo = new MapInfo();
	}

	public void update(HitFilter hf) { // HitFilterListener interface
		if (!mapinfo.hasHitContent(hf.isBlock(), hf.getNonRepetitive()))
			init();
		hoverText = hf.getHover(); // CAS520 add
	}

	/** Method closeFilter closes this mappers filter window @see FilterHandler#hide() */
	public void closeFilter() {fh.hide();}
	
	public void clearTrackBuild() {
		if (trackHolders[0].getTrack() != null)
			trackHolders[0].getTrack().clearTrackBuild();
		if (trackHolders[1].getTrack() != null)
			trackHolders[1].getTrack().clearTrackBuild();
	}

	/** Method initAllHits initializes the mapper downloading all hits. */
	public boolean initAllHits() {
		HitFilter hf = hitfilter.copy();
		hf.setNonRepetitive(false);
		hf.setBlock(false);
		return myInit(hf);
	}

	/**
	 * Method init gathers the needed data from the database and
	 * creates the those hit objects, clearing the hits that are not needed. If
	 * there is filter data to commit, it is committed for this Mapper and for the associated tracks.
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
		synchronized (hitList) {
			try {
				change = pool.setData(this, t1, t2, mapinfo, hf, hitList);
			} catch (SQLException s1) {
				ErrorReport.print(s1, "First attempt at initializing Mapper failed.");
				try {
					pool.close();
					change = pool.setData(this, t1, t2, mapinfo, hf, hitList);
				} catch (SQLException s2) {
					ErrorReport.print(s2, "Second attempt at initializing Mapper failed. Giving up.");
					return false;
				}
			}
			if (change) {
				Iterator<AbstractHitData> iter = hitList.iterator();
				while (iter.hasNext()) {
					((Hits) iter.next()).setMinMax(hitfilter);
				}				
			}
		}
		initing = false;
		
		return true;
	}
	public void setMapperData(MapperData md) {
		if (md != null) {
			md.setMapper(this);
			init();
		}
	}
	public void setVisible(boolean visible) {
		fh.getFilterButton().setEnabled(visible);
		super.setVisible(visible);
	}
	/******************************************************************/
	public void getSequenceMinMax(int[] minMax) {
		if (getMapType() == FPC2PSEUDO) {
			synchronized (hitList) {
				for (AbstractHitData h : hitList)
					((FPCPseudoHits) h).getSequenceMinMax(minMax);
			}
		}
	}

	public MapperData getMapperData() {return new MapperData(this);}
	
	public DrawingPanel getDrawingPanel() { return drawingPanel;}
	
	public HitFilter getHitFilter() {return hitfilter;}
	
	public AbstractButton getFilterButton() {return fh.getFilterButton();}
	
	public Track getTrack1() {return trackHolders[0].getTrack();}
	
	public Track getTrack2() {return trackHolders[1].getTrack();}
	
	public Track getTrack(int trackNum) {return trackHolders[trackNum].getTrack();}

	public int getMapType() {
		Track t1 = trackHolders[0].getTrack(), t2 = trackHolders[1].getTrack();
		if (t1 instanceof Sequence && t2 instanceof Sequence) return PSEUDO2PSEUDO; 
		if (t1 == null || t2 == null || t1 instanceof Sequence || t2 instanceof Sequence) return FPC2PSEUDO;
		return FPC2FPC;
	}
	
	protected int getTrackPosition(Track t) {
		if (trackHolders[0].getTrack() == t)
			return LEFT_ORIENT;
		else
			return RIGHT_ORIENT;
	}
	
	/*** @see FPCPseudoHits#getHitsInRange(List,int,int) */
	public List<AbstractHitData> getHitsInRange(Track src, int start, int end) {
		List<AbstractHitData> retHits = new LinkedList<AbstractHitData>();
		
		synchronized (hitList) {
			for (AbstractHitData h : hitList)
				h.getHitsInRange(retHits, start, end, isQuery(src));
		}
		return retHits;
	}

	public int[] getMinMax(Track src, int start, int end) {
		int[] minMax = new int[] { Integer.MAX_VALUE, Integer.MIN_VALUE };
		
		synchronized (hitList) {
			for (AbstractHitData h : hitList)
				h.getMinMax(minMax, start, end, isQuery(src));
		}
		
		if (minMax[0] == Integer.MAX_VALUE || minMax[1] == Integer.MIN_VALUE)
			return null; // no hits within range
		
		return minMax;
	}
	
	/**********************************************
	 * hasPair is always (query,target);  
	 * CAS517 rearranged and renamed from isSwapped 
	 * return (t1 == src && !pool.hasPair(t2, t1)) || (t2 == src && !pool.hasPair(t1, t2))|| (isSelf() && src == t1); 
	 */
	public boolean isQuery(Track src) {
		Track t1 = getTrack1(); 
		Track t2 = getTrack2();
		if (isSelf()) {
			if (t1==src && getTrackPosition(t1)==LEFT_ORIENT) return true;
			else return false;
		}
		if (t1 == src && pool.hasPair(t1, t2)) return true; 
		if (t2 == src && pool.hasPair(t2, t1)) return true;
		
		return false;
	}
	
	public boolean isSelf() {
		Track t1 = getTrack1(); 
		Track t2 = getTrack2(); 
		return (t1.getProject() == t2.getProject());
	}
	
	// CAS516 change to one call instead of 4
	public boolean isHitSelected(int s1, int e1, int s2, int e2) {
		if(theTablePanel != null)
			return theTablePanel.isHitSelected(s1, e1, s2, e2);
		return false;
	}
	/********************************************************************/
	public void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g;
		if (!initing) {
			for (AbstractHitData h : hitList)
				((Hits) h).paintComponent(g2);
		}
	}
	public void mouseMoved(MouseEvent e) {
		if (!initing)
			for (Iterator iter = hitList.iterator(); iter.hasNext(); ((Hits) iter.next()).mouseMoved(e));
	}
	
	public void mouseDragged(MouseEvent e) { }
	
	public void mouseClicked(MouseEvent e) { }
	public void mouseEntered(MouseEvent e) { }
	
	public void mouseExited(MouseEvent e) {
		if (!initing)
			for (Iterator iter = hitList.iterator(); iter.hasNext(); ((Hits) iter.next()).mouseExited(e));
	}
	public void mouseReleased(MouseEvent e) { }
	
	public void mouseWheelMoved(MouseWheelEvent e) {
		long length = trackHolders[0].getTrack().getTrackSize();
		for (int i = 0;  i < trackHolders.length;  i++)
			length = Math.min(length,trackHolders[i].getTrack().getEnd()-trackHolders[i].getTrack().getStart()+1);
		for (int i = 0;  i < trackHolders.length;  i++)
			trackHolders[i].getTrack().mouseWheelMoved(e, length);
	}
	
	// CAS517 add for popup hit wire description; see PseudoPseudoHits.PseudoHits.doPopupDesc
	public void mousePressed(MouseEvent e) {
		if (e.isPopupTrigger()) {
			for (AbstractHitData h : hitList) {
				if (h.doPopupDesc(e)) return;
			}
			fh.showPopup(e);
		}
	} 
	/******************************************************************/
	public String toString() {
		return "[Mapper (Track1: " + trackHolders[0].getTrack() + ") (Track2: " + trackHolders[1].getTrack() + ")]";
	}
	// called by HelpBar
	public String getHelpText(MouseEvent e) { 
		if (helpText == null)	return hoverText + HOVER_MESSAGE;
		else					return helpText; 
	}
	// set in PseudoPseudoHits.PseudoHits
	public void setHelpText(String text) {
		if (text == null)		helpText = hoverText + HOVER_MESSAGE  ;
		else					helpText = text;
	}
}
